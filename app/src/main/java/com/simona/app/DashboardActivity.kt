package com.simona.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simona.app.databinding.ActivityDashboardBinding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Muestra el dashboard HTML de SIMONA (http://192.168.4.1) en pantalla
 * completa, sin barra de navegador (Plan de Desarrollo, sección 4.1).
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var wifiManager: WifiConnectionManager

    // Reintento corto automático si el ESP32 recién está reiniciando (5.4)
    private var reintentosPendientes = 3

    // B.5 — Polling en Kotlin para persistir lecturas por huerta (independiente
    // del fetch('/data') que ya hace el JS del dashboard, ver nota de diseño
    // en el documento de plan sobre la duplicación aceptada de polling).
    private var huertaId: String? = null
    private val pollingHandler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = (application as SimonaApp).wifiConnectionManager
        huertaId = intent.getStringExtra(EXTRA_HUERTA_ID)

        setupWebView()
        setupBackNavigation()
        cargarDashboard()
    }

    /**
     * Botón atrás del sistema: por defecto Android simplemente cierra la
     * Activity (finish()), lo que dispara onStop() y libera la conexión de
     * inmediato. Acá primero intentamos retroceder dentro del historial del
     * propio WebView (si el dashboard llegara a tener más de una pantalla) y
     * solo si no hay nada más para atrás dejamos que se cierre normalmente.
     *
     * Fase 6.2 — antes de cerrar de verdad (el caso "no hay más historial
     * en el WebView"), se pide confirmación: salir de acá desconecta la red
     * de SIMONA, y sin aviso era fácil tocar atrás sin querer y perder la
     * conexión en medio de un riego manual o de estar mirando el gráfico en
     * vivo. Decisión de diseño tomada con Ben: la confirmación es SOLO para
     * este camino (atrás/gesto) — minimizar la app (onStop sin isFinishing)
     * sigue sin desconectar ni preguntar nada, como ya funcionaba antes.
     */
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    confirmarSalidaDashboard()
                }
            }
        })
    }

    /**
     * Fase 6.2 — diálogo de confirmación. Si el usuario confirma, se
     * desactiva este callback y se vuelve a disparar el back press: así el
     * sistema procesa el back "de verdad" (finish() de la Activity), en vez
     * de duplicar acá la lógica de cierre.
     */
    private fun confirmarSalidaDashboard() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_salir_dashboard_titulo)
            .setMessage(R.string.dialog_salir_dashboard_mensaje)
            .setPositiveButton(R.string.btn_salir) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true

        // Puente para el asistente de IA "Simona" (chat flotante del
        // dashboard). Ver PuenteAsistente y AsistenteGemini.kt.
        binding.webView.addJavascriptInterface(PuenteAsistente(), "SimonaAndroid")

        binding.webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.waitingOverlay.visibility = View.GONE
                reintentosPendientes = 3

                // Sincroniza el tema del dashboard con el de la app nativa
                // (bug UI/UX #4): sin esto, el WebView siempre arranca en
                // claro sin importar lo que tenga elegido ThemePrefs.
                val temaInicial = if (ThemePrefs.isDark(this@DashboardActivity)) "dark" else "light"
                view?.evaluateJavascript("setTemaInicial('$temaInicial')", null)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Solo interesa el error del frame principal, no de recursos
                // secundarios (íconos, fuentes, etc.)
                if (request?.isForMainFrame == true) {
                    mostrarEsperandoDispositivo()
                }
            }
        }
    }

    private fun cargarDashboard() {
        binding.webView.loadUrl(resolverDashboardUrl())
    }

    /**
     * Resuelve la URL del dashboard a partir del gateway real de la red
     * conectada, en vez de depender solo de la IP fija de dashboard_url
     * (sección 10.2/10.4). Se conserva el puerto/esquema ya definidos en
     * strings.xml (80 implícito para el ESP32 real, :8000 para el servidor
     * simulado) y se reemplaza únicamente el host por el gateway detectado
     * en tiempo de ejecución, así la misma app funciona sin recompilar
     * sin importar si el entorno es el ESP32 real, una notebook con
     * Linux/Windows, o el hotspot nativo de otro celular.
     */
    private fun resolverDashboardUrl(): String {
        val base = resolverUrlBase()
        val id = huertaId
        return if (!id.isNullOrEmpty()) {
            if (base.contains("?")) "$base&huerta=$id" else "$base?huerta=$id"
        } else {
            base
        }
    }

    /**
     * Fase 4.1 — delega en WifiConnectionManager.resolverUrl(), que centraliza
     * la lógica antes duplicada entre esta Activity y TutorialConexionActivity.
     * También la necesita el polling de B.5 (resolverUrlDatos()) para pegarle
     * a /data por su cuenta.
     */
    private fun resolverUrlBase(): String =
        wifiManager.resolverUrl(getString(R.string.dashboard_url))

    private fun resolverUrlDatos(): String = resolverUrlBase().trimEnd('/') + "/data"

    /**
     * El WebView tarda en cargar o la IP no responde aunque la red esté
     * conectada (ESP32 recién reiniciando). En vez de una pantalla en blanco,
     * se muestra un mensaje y se reintenta automáticamente unas pocas veces
     * (sección 5.4).
     */
    private fun mostrarEsperandoDispositivo() {
        binding.waitingOverlay.visibility = View.VISIBLE
        binding.waitingText.text = getString(R.string.error_device_not_responding)

        if (reintentosPendientes > 0) {
            reintentosPendientes--
            binding.webView.postDelayed({ cargarDashboard() }, 2000)
        } else {
            // Se agotaron los reintentos cortos: volvemos a la pantalla de
            // estado para que el usuario decida reintentar manualmente.
            volverAEstadoSinConexion()
        }
    }

    private fun volverAEstadoSinConexion() {
        finish()
    }

    /**
     * B.5 — Arranca el ciclo de polling cada POLLING_INTERVAL_MS. Solo tiene
     * sentido si esta pantalla se abrió con una huerta específica (D.4); en
     * el flujo viejo (MainActivity sin selección de huerta) huertaId es
     * null y no hay nada que persistir.
     */
    private fun iniciarPollingLecturas() {
        val id = huertaId ?: return
        detenerPollingLecturas() // evita duplicar el runnable si ya había uno

        pollingRunnable = object : Runnable {
            override fun run() {
                descargarYGuardarLectura(id)
                pollingHandler.postDelayed(this, POLLING_INTERVAL_MS)
            }
        }
        pollingHandler.postDelayed(pollingRunnable!!, POLLING_INTERVAL_MS)
    }

    private fun detenerPollingLecturas() {
        pollingRunnable?.let { pollingHandler.removeCallbacks(it) }
        pollingRunnable = null
    }

    /**
     * Corre en un hilo aparte para no bloquear el main thread con el GET.
     * Si falla (timeout, JSON inesperado, etc.) se loguea y se descarta ese
     * ciclo — el próximo intento, POLLING_INTERVAL_MS después, puede andar.
     */
    private fun descargarYGuardarLectura(idHuerta: String) {
        Thread {
            var conexion: HttpURLConnection? = null
            try {
                conexion = (URL(resolverUrlDatos()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                if (conexion.responseCode == 200) {
                    val texto = conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(texto)

                    val lectura = LecturaHuerta(
                        humedad = json.getDouble("humedad_suelo").toFloat(),
                        temperatura = json.getDouble("temperatura").toFloat(),
                        luz = json.getDouble("luminosidad").toInt(),
                        ph = json.getDouble("ph").toFloat(),
                        riegoActivo = json.getBoolean("riego_activo"),
                        tanqueAgua = json.getDouble("tanque_agua").toFloat()
                    )

                    (application as SimonaApp).huertaRepository.actualizarLectura(idHuerta, lectura)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Polling /data falló (se reintenta en ${POLLING_INTERVAL_MS}ms): ${e.message}")
            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    /**
     * Puente entre el JS del chat flotante del dashboard y el asistente de
     * IA nativo. Ver AsistenteGemini.kt para el porqué de la llamada de red
     * "por afuera" del binding de proceso a SIMONA: esta llamada necesita
     * la conexión de internet real del celular (WiFi de casa o datos
     * móviles), no la red de SIMONA a la que está atado el WebView.
     */
    private inner class PuenteAsistente {
        /**
         * Llamado desde toggleTheme() en el HTML del dashboard cuando el
         * usuario cambia el tema ahí adentro (bug UI/UX #4). Guarda la
         * preferencia en ThemePrefs para que quede sincronizada con el
         * toggle nativo del home.
         */
        @JavascriptInterface
        fun temaCambiado(tema: String) {
            // Los métodos @JavascriptInterface corren en un hilo de
            // background; ThemePrefs.setDark() termina llamando a
            // AppCompatDelegate.setDefaultNightMode(), que debe hacerse en
            // el hilo principal.
            runOnUiThread {
                ThemePrefs.setDark(this@DashboardActivity, tema == "dark")
            }
        }

        @JavascriptInterface
        fun preguntar(pregunta: String) {
            val id = huertaId
            val huerta = if (id != null) (application as SimonaApp).huertaRepository.obtener(id) else null

            if (huerta == null) {
                responderAlJs(exito = false, texto = "No encontré los datos de esta huerta.")
                return
            }

            AsistenteGemini(this@DashboardActivity).preguntar(
                pregunta = pregunta,
                contextoHuerta = armarContextoHuerta(huerta),
                onExito = { respuesta -> responderAlJs(exito = true, texto = respuesta) },
                onError = { mensaje -> responderAlJs(exito = false, texto = mensaje) }
            )
        }
    }

    /** Arma el contexto en texto plano que recibe Gemini junto a la pregunta. */
    private fun armarContextoHuerta(huerta: Huerta): String {
        val l = huerta.ultimaLectura
        return buildString {
            append("Nombre: ${huerta.nombre}. Categoría: ${huerta.categoria}. ")
            append("Rango de humedad configurado: ${huerta.humedadMin}%-${huerta.humedadMax}%. ")
            append("Rango de pH: ${huerta.phMin}-${huerta.phMax}. ")
            append("Rango de luz: ${huerta.luzMin}-${huerta.luzMax} lux. ")
            append("Rango de temperatura: ${huerta.tempMin}°C-${huerta.tempMax}°C. ")
            if (l != null) {
                append("Última lectura: humedad ${l.humedad}%, temperatura ${l.temperatura}°C, ")
                append("luz ${l.luz} lux, pH ${l.ph}, tanque de agua ${l.tanqueAgua}%, ")
                append("riego ${if (l.riegoActivo) "activo" else "apagado"}.")
            } else {
                append("Todavía no hay lecturas registradas de esta huerta.")
            }
        }
    }

    /** Devuelve la respuesta (o el error) del asistente al chat del WebView. */
    private fun responderAlJs(exito: Boolean, texto: String) {
        runOnUiThread {
            val payload = JSONObject().apply {
                put("ok", exito)
                put("texto", texto)
            }
            binding.webView.evaluateJavascript("recibirRespuestaAsistente($payload)", null)
        }
    }

    /**
     * Pausar/reanudar el WebView cuando la Activity deja de estar en
     * primer plano (multitarea, apagado de pantalla, etc.). Sin esto, al
     * volver desde "apps recientes" el WebView puede quedar con la
     * superficie gráfica sin redibujar (pantalla negra), aunque siga
     * funcionando por detrás — bug conocido de Android WebView (ver
     * pendientes, sección 12 del documento).
     *
     * También arranca/frena el polling de B.5 en el mismo ciclo de vida:
     * no tiene sentido seguir pidiendo /data si la Activity no está visible.
     */
    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        binding.webView.pauseTimers()
        detenerPollingLecturas()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.resumeTimers()
        binding.webView.onResume()
        iniciarPollingLecturas()
    }

    /**
     * Fix real del bug de pantalla negra (sección 12, "Bug nuevo detectado").
     *
     * Primer intento (solo en onResume + layerType SOFTWARE→HARDWARE) no
     * alcanzaba, por dos motivos:
     *
     * 1) Timing: onResume() se dispara antes de que la ventana esté
     *    realmente visible en pantalla — sobre todo volviendo desde "apps
     *    recientes", donde el sistema todavía está reconstruyendo la
     *    superficie gráfica. onWindowFocusChanged(true) es la señal
     *    confiable de que la ventana ya volvió a estar en foco y dibujada.
     *
     * 2) El toggle SOFTWARE→HARDWARE no fuerza a Android a destruir y
     *    recrear la superficie: sigue siendo un layer válido en todo
     *    momento. Pasar por LAYER_TYPE_NONE en el medio sí lo fuerza,
     *    porque ahí el WebView pierde su layer por completo y Android
     *    tiene que reconstruirlo desde cero al volver a HARDWARE.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            forzarRedibujadoWebView()
        }
    }

    private fun forzarRedibujadoWebView() {
        binding.webView.setLayerType(View.LAYER_TYPE_NONE, null)
        binding.webView.post {
            binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            binding.webView.requestLayout()
            binding.webView.invalidate()
        }
    }

    /**
     * Cambio respecto a la versión anterior: antes, wifiManager.disconnect()
     * se llamaba en TODO onStop(), lo cual incluye simplemente minimizar la
     * app (ir a "apps recientes", apretar Home, o que aparezca otra pantalla
     * encima) — no solo cerrar el dashboard. Eso desconectaba la red SIMONA
     * cada vez que la app dejaba de estar en primer plano, lo cual agravaba
     * el bug de la pantalla negra: al volver, ya no había red y nadie
     * disparaba una recarga.
     *
     * Ahora solo se libera la red cuando la Activity realmente se está
     * cerrando (isFinishing == true, por ejemplo al tocar atrás, confirmar
     * la salida en el diálogo de la Fase 6.2, y no haber historial en el
     * WebView). Si el usuario solo minimizó la app, la conexión sigue viva
     * y, al volver, MainActivity.onStart() / DashboardActivity.onResume()
     * la encuentran intacta.
     */
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            wifiManager.disconnect()
        }
    }

    companion object {
        private const val TAG = "DashboardActivity"
        private const val POLLING_INTERVAL_MS = 3000L
        private const val EXTRA_HUERTA_ID = "extra_huerta_id"

        /**
         * huertaId es opcional: MainActivity (flujo viejo, sin selección de
         * huerta) sigue abriendo el dashboard sin id. El polling de B.5
         * (iniciarPollingLecturas) revisa esto y no hace nada si es null.
         */
        fun crearIntent(context: Context, huertaId: String? = null): Intent =
            Intent(context, DashboardActivity::class.java).apply {
                huertaId?.let { putExtra(EXTRA_HUERTA_ID, it) }
            }
    }
}
