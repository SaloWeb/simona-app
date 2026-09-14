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
 * Gemini de Google, llamada DIRECTO desde la app.
 */
class AsistenteGemini(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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
                try {
                    cm.unregisterNetworkCallback(this)
                } catch (e: Exception) {
                    // Ignorar si ya estaba liberado
                }
                ejecutarLlamada(network, pregunta, contextoHuerta, mainHandler, onExito, onError)
            }

            override fun onUnavailable() {
                if (resuelto) return
                resuelto = true
                mainHandler.post { onError(context.getString(R.string.asistente_sin_internet)) }
            }
        }

        cm.requestNetwork(request, callback)

        timeoutHandler.postDelayed({
            if (!resuelto) {
                resuelto = true
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (e: IllegalArgumentException) {
                    // Ya se había liberado: ignorar.
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
                // Liberar el proceso del Wi-Fi local para permitir la ruta pública
                cm.bindProcessToNetwork(null)

                val apiKey = context.getString(R.string.gemini_api_key)
                if (apiKey.isBlank() || apiKey == "TU_API_KEY_ACA") {
                    mainHandler.post { onError(context.getString(R.string.asistente_sin_api_key)) }
                    return@Thread
                }

                val url = URL(ENDPOINT)
                conexion = (network.openConnection(url) as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", apiKey)
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }

                val prompt = buildString {
                    append(PROMPT_SISTEMA.trimIndent())
                    append("\n\nContexto actual de la huerta:\n")
                    append(contextoHuerta)
                    append("\n\nPregunta del usuario: ")
                    append(pregunta)
                }

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
                    mainHandler.post {
                        onError("${context.getString(R.string.asistente_error_generico)} [debug: HTTP $codigo — $texto]")
                    }
                    return@Thread
                }

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
                mainHandler.post {
                    onError("${context.getString(R.string.asistente_error_generico)} [debug: ${e.javaClass.simpleName} — ${e.message}]")
                }
            } finally {
                conexion?.disconnect()
                // Volver a atar el proceso a la red del ESP32 si la app sigue conectada
                (context.applicationContext as? SimonaApp)?.wifiConnectionManager?.rebindProcess()
            }
        }.start()
    }

    companion object {
        private const val TAG = "AsistenteGemini"

        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/interactions"

        private const val MODELO = "gemini-3.6-flash"

        private const val TIMEOUT_RED_MS = 15_000L

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
