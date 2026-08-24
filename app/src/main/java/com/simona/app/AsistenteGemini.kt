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

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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

                val url = URL("$ENDPOINT?key=$apiKey")
                // network.openConnection() (no URL.openConnection()) es lo que
                // fuerza a que ESTA conexión puntual use la red de internet
                // del celular, sin importar a qué red esté atado el proceso.
                conexion = (network.openConnection(url) as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
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

                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }))
                }

                conexion.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val codigo = conexion.responseCode
                val stream = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                val texto = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                if (codigo !in 200..299) {
                    Log.w(TAG, "Gemini respondió $codigo: $texto")
                    mainHandler.post { onError(context.getString(R.string.asistente_error_generico)) }
                    return@Thread
                }

                val respuesta = JSONObject(texto)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                mainHandler.post { onExito(respuesta) }
            } catch (e: Exception) {
                Log.w(TAG, "Error llamando a Gemini: ${e.message}")
                mainHandler.post { onError(context.getString(R.string.asistente_error_generico)) }
            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    companion object {
        private const val TAG = "AsistenteGemini"

        // gemini-2.0-flash: modelo estable dentro del nivel gratis. Si más
        // adelante querés probar un modelo más nuevo, es el único valor
        // que hay que tocar acá.
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

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