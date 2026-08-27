package com.simona.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Asistente de IA "Simona" (chat flotante del dashboard). Usa la API de
 * Gemini de Google, llamada DIRECTO desde la app (sin servidor
 * intermedio) — decisión tomada porque el proyecto prioriza simplicidad
 * y el uso es personal/casero (ver charla del asistente en el chat).
 *
 * Punto clave de arquitectura: el WebView del dashboard está atado por
 * completo a la red de SIMONA (WifiConnectionManager.connect() hace
 * bindProcessToNetwork()), así que una llamada de red "normal" hecha
 * acá intentaría salir por esa red y fallaría (SIMONA no tiene
 * internet). Por eso esta clase pide, APARTE, la red de internet del
 * celular (WiFi de casa o datos móviles) vía
 * ConnectivityManager.requestNetwork() y fuerza la conexión HTTPS a
 * usar ESA red específica con Network.openConnection(url) — eso
 * ignora el binding de proceso solo para esta llamada puntual, sin
 * tocar ni afectar la conexión a SIMONA que sigue viva en paralelo.
 */
class AsistenteGemini(private val context: Context) {

    /**
     * Hace la pregunta a Gemini con el contexto de la huerta ya armado
     * como texto plano. Corre la llamada de red en un hilo aparte;
     * onExito/onError se disparan siempre en el hilo principal.
     */
    fun preguntar(
        pregunta: String,
        contextoHuerta: String,
        onExito: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // OJO: NET_CAPABILITY_VALIDATED es una capability "mutable" (puede
        // cambiar en cualquier momento de la vida de la red) y Android NO
        // permite pedirla con requestNetwork(request, callback) sin timeout:
        // tira IllegalArgumentException de forma SINCRÓNICA, ahí mismo en el
        // hilo principal, antes de que se intente ninguna conexión. Como esa
        // excepción salta fuera del Thread/try-catch de ejecutarLlamada(),
        // no hay forma de atajarla ahí adentro y termina crasheando la app
        // en cada mensaje. Alcanza con pedir INTERNET; si la red no tiene
        // internet de verdad, la conexión HTTPS de más abajo va a fallar
        // igual con una IOException normal, que sí está contemplada.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var resuelto = false
        val timeoutHandler = Handler(Looper.getMainLooper())
        lateinit var callback: ConnectivityManager.NetworkCallback

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (resuelto) return
                resuelto = true
                timeoutHandler.removeCallbacksAndMessages(null)
                cm.unregisterNetworkCallback(this)
                ejecutarLlamada(network, pregunta, contextoHuerta, mainHandler, onExito, onError)
            }

            override fun onUnavailable() {
                if (resuelto) return
                resuelto = true
                mainHandler.post { onError(context.getString(R.string.asistente_sin_internet)) }
            }
        }

        cm.requestNetwork(request, callback)

        // Respaldo manual, mismo patrón que WifiConnectionManager.kt: si
        // Android tarda demasiado en resolver sin llamar a ninguno de los
        // dos callbacks, no dejamos el chat colgado esperando para siempre.
        timeoutHandler.postDelayed({
            if (!resuelto) {
                resuelto = true
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (e: IllegalArgumentException) {
                    // Ya se había liberado (carrera con onAvailable/onUnavailable): ignorar.
                }
                mainHandler.post { onError(context.getString(R.string.asistente_sin_internet)) }
            }
        }, TIMEOUT_RED_MS)
    }

    private fun ejecutarLlamada(
        network: Network,
        pregunta: String,
        contextoHuerta: String,
        mainHandler: Handler,
        onExito: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            var conexion: HttpsURLConnection? = null
            try {
                val apiKey = context.getString(R.string.gemini_api_key)
                if (apiKey.isBlank() || apiKey == "TU_API_KEY_ACA") {
                    mainHandler.post { onError(context.getString(R.string.asistente_sin_api_key)) }
                    return@Thread
                }

                val url = URL(ENDPOINT)
                // network.openConnection() (no URL.openConnection()) es lo que
                // fuerza a que ESTA conexión puntual use la red de internet
                // del celular, sin importar a qué red esté atado el proceso.
                conexion = (network.openConnection(url) as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    // Las keys nuevas formato "AQ." (Authentication Key, la
                    // única que emite AI Studio desde mediados de 2026) no
                    // andan contra el endpoint viejo generateContent — tiran
                    // 401 ACCESS_TOKEN_TYPE_UNSUPPORTED tanto por header como
                    // por query param (hilo oficial confirmado en el foro de
                    // Google AI). El endpoint nuevo (Interactions API) sí
                    // está pensado para este tipo de key.
                    setRequestProperty("x-goog-api-key", apiKey)
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }

                val prompt = buildString {
                    append(PROMPT_SISTEMA.trimIndent())
                    append("\n\nContexto actual de la huerta:\n")
                    append(contextoHuerta)
                    append("\n\nPregunta del usuario: ")
                    append(pregunta)
                }

                // Formato de la Interactions API: no lleva "contents"/"parts"
                // como el viejo generateContent, sino "model" + "input" con
                // el texto plano del prompt.
                val body = JSONObject().apply {
                    put("model", MODELO)
                    put("input", prompt)
                }

                conexion.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val codigo = conexion.responseCode
                val stream = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                val texto = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                if (codigo !in 200..299) {
                    Log.w(TAG, "Gemini respondió $codigo: $texto")
                    // DEBUG TEMPORAL: mostramos el código/cuerpo real de la
                    // respuesta para diagnosticar por qué falla. Sacar este
                    // detalle del mensaje al usuario una vez resuelto —
                    // dejarlo así en producción expondría de más.
                    mainHandler.post {
                        onError("${context.getString(R.string.asistente_error_generico)} [debug: HTTP $codigo — $texto]")
                    }
                    return@Thread
                }

                // Formato de respuesta de la Interactions API: un array
                // "steps" con distintos tipos de paso (thought, tool calls,
                // etc.) — el texto final está en el (o los) paso(s) de tipo
                // "model_output". Nos quedamos con el ÚLTIMO de esos pasos,
                // por si en el futuro se agregan tools y hay varios.
                val steps = JSONObject(texto).getJSONArray("steps")
                var respuesta: String? = null
                for (i in 0 until steps.length()) {
                    val paso = steps.getJSONObject(i)
                    if (paso.optString("type") == "model_output") {
                        val contenidos = paso.getJSONArray("content")
                        for (j in 0 until contenidos.length()) {
                            val bloque = contenidos.getJSONObject(j)
                            if (bloque.optString("type") == "text") {
                                respuesta = bloque.getString("text").trim()
                            }
                        }
                    }
                }

                if (respuesta == null) {
                    Log.w(TAG, "Gemini no devolvió ningún model_output: $texto")
                    mainHandler.post {
                        onError("${context.getString(R.string.asistente_error_generico)} [debug: sin model_output — $texto]")
                    }
                    return@Thread
                }

                mainHandler.post { onExito(respuesta) }
            } catch (e: Exception) {
                Log.w(TAG, "Error llamando a Gemini: ${e.message}")
                // DEBUG TEMPORAL: mismo motivo que arriba, sacar después.
                mainHandler.post {
                    onError("${context.getString(R.string.asistente_error_generico)} [debug: ${e.javaClass.simpleName} — ${e.message}]")
                }
            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    companion object {
        private const val TAG = "AsistenteGemini"

        // Interactions API (reemplazo de generateContent, único endpoint
        // que Google garantiza que funciona con las keys nuevas "AQ.").
        // Acá el modelo va DENTRO del body ("model"), no en la URL.
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/interactions"

        // gemini-2.5-flash sigue vigente dentro del nivel gratis. Si más
        // adelante querés probar un modelo más nuevo (p.ej. gemini-3.7-flash),
        // es el único valor que hay que tocar acá.
        private const val MODELO = "gemini-2.5-flash"

        private const val TIMEOUT_RED_MS = 8_000L

        private const val PROMPT_SISTEMA = """
            Sos "Simona", el asistente de una app de riego automático de huertas
            caseras. Respondé en español rioplatense, corto y claro (máximo 4-5
            oraciones), sin markdown ni asteriscos. Usá los datos reales de la
            huerta que te paso para responder preguntas sobre su estado actual
            (humedad, temperatura, pH, luz, tanque de agua, riego). También podés
            dar consejos generales de cultivo y jardinería. Si te preguntan algo
            que no tiene nada que ver con la huerta o el cultivo, respondé
            amablemente que no es tu tema.
        """
    }
}