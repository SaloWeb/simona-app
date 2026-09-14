package com.simona.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simona.app.databinding.ActivityDetalleHuertaBinding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Pantalla de detalle de UNA huerta. Cumple dos roles según cómo se llegue:
 *
 * - MODO OFFLINE (modoConectado = false, el caso normal, entrando desde
 *   "tocar la humedad" en el home): 100% analítica, lee la última lectura
 *   conocida desde HuertaRepository, sin necesitar WiFi al ESP32.
 * - MODO CONECTADO (modoConectado = true, cuando TutorialConexionActivity
 *   recién terminó de conectar y hacer POST /config): además de lo
 *   anterior, hace polling propio a GET /data del dispositivo cada
 *   POLLING_INTERVAL_MS mientras esta pantalla esté visible, y habilita
 *   los controles operativos (riego manual, rellenar tanque). Reemplaza
 *   a la vieja DashboardActivity (WebView contra el HTML del ESP32, ya
 *   eliminado del dispositivo — ver CONTEXTO_PROYECTO.md secciones 2/3):
 *   esta pantalla nativa hace lo mismo con las vistas propias de la app
 *   (GaugeHumedadView, MiniMapaCapasView, GraficoTendenciaView), pensada
 *   para verse igual o mejor que el gauge HTML que reemplaza.
 */
class DetalleHuertaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleHuertaBinding
    private lateinit var repository: HuertaRepository
    private lateinit var wifiManager: WifiConnectionManager
    private var huertaId: String = ""
    private var modoConectado: Boolean = false
    // Evita solapar un GET /data con el siguiente si el dispositivo tarda
    // en responder (mismo criterio que el poll de RSSI en
    // TutorialConexionActivity: nunca dos peticiones en simultáneo).
    private var fetchEnCurso: Boolean = false
    // PLAN_MEJORAS_20.md, punto 8: qué curva mostrar en GraficoTendenciaView.
    // Se conserva entre refrescos (no se resetea en cada cargarYMostrar())
    // para que el polling de 3s no le pise al usuario la métrica elegida.
    private var mostrandoHumedadEnTendencia: Boolean = true

    private val refrescoHandler = Handler(Looper.getMainLooper())
    private var refrescoRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleHuertaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as SimonaApp).huertaRepository
        wifiManager = (application as SimonaApp).wifiConnectionManager
        huertaId = intent.getStringExtra(EXTRA_HUERTA_ID) ?: ""
        modoConectado = intent.getBooleanExtra(EXTRA_MODO_CONECTADO, false)

        binding.btnVolver.setOnClickListener { confirmarVolverSiCorresponde() }
        binding.btnIrADashboard.setOnClickListener { abrirDashboardEnVivo() }
        binding.chipTendenciaHumedad.setOnClickListener { seleccionarMetricaTendencia(esHumedad = true) }
        binding.chipTendenciaTemp.setOnClickListener { seleccionarMetricaTendencia(esHumedad = false) }
        binding.btnDesconectar.setOnClickListener { confirmarVolverSiCorresponde() }
        binding.btnRiegoManual.setOnClickListener { alternarRiegoManual() }
        binding.btnRefillTanque.setOnClickListener { rellenarTanque() }

        setupBackNavigation()
        cargarYMostrar()

        // Modo conectado: no esperamos al primer ciclo de onResume() (hasta
        // POLLING_INTERVAL_MS de demora) para traer el primer dato — se
        // pide de una así la pantalla deja de mostrar "sin lecturas" lo
        // antes posible.
        if (modoConectado) {
            fetchDatosEnVivo()
        }
    }

    /**
     * Atrás/gesto y el botón "Desconectar" del banner comparten el mismo
     * criterio que tenía DashboardActivity.confirmarSalidaDashboard(): en
     * modo conectado, salir de acá corta la red de SIMONA, así que se
     * avisa antes en vez de desconectar sin querer en medio de un riego
     * manual. En modo offline no hay nada que desconectar: se sale directo.
     */
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmarVolverSiCorresponde()
            }
        })
    }

    private fun confirmarVolverSiCorresponde() {
        if (!modoConectado) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_salir_dashboard_titulo)
            .setMessage(R.string.dialog_salir_dashboard_mensaje)
            .setPositiveButton(R.string.btn_salir) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun cargarYMostrar() {
        val huerta = repository.obtener(huertaId)
        if (huerta == null) {
            finish() // la huerta ya no existe (se borró desde MapaHuertasActivity)
            return
        }

        binding.txtNombreHuerta.text = huerta.nombre
        binding.txtCategoriaHuerta.text = huerta.categoria
        binding.bannerConectado.visibility = if (modoConectado) View.VISIBLE else View.GONE

        val lectura = huerta.ultimaLectura
        if (lectura == null) {
            binding.estadoSinLecturas.visibility = View.VISIBLE
            binding.contenidoConLecturas.visibility = View.GONE
            // Modo conectado: ya estamos trayendo el primer dato solos
            // (fetchDatosEnVivo), no tiene sentido ofrecer el botón que
            // manda a reconectar — mostramos un spinner en su lugar.
            binding.btnIrADashboard.visibility = if (modoConectado) View.GONE else View.VISIBLE
            binding.progressEsperandoDatos.visibility = if (modoConectado) View.VISIBLE else View.GONE
            return
        }

        binding.estadoSinLecturas.visibility = View.GONE
        binding.contenidoConLecturas.visibility = View.VISIBLE
        binding.controlesEnVivo.visibility = if (modoConectado) View.VISIBLE else View.GONE
        binding.btnRiegoManual.setText(
            if (lectura.riegoActivo) R.string.detalle_riego_manual_detener else R.string.detalle_riego_manual_activar
        )

        binding.miniMapaCapas.setCapas(construirCapasDesdeHuerta(huerta, lectura))
        binding.gaugeHumedad.setValor(lectura.humedad, huerta.humedadMin, huerta.humedadMax)

        binding.txtTanqueAgua.text = "${lectura.tanqueAgua.roundToInt()}%"

        // PLAN_MEJORAS_20.md, punto 8: la tarjeta de tendencia solo tiene
        // sentido con al menos 2 puntos (GraficoTendenciaView ya maneja su
        // propio estado "acumulando datos", pero por debajo de 2 puntos ni
        // siquiera vale la pena mostrar la tarjeta vacía).
        if (huerta.historial.size >= 2) {
            binding.cardTendencia.visibility = View.VISIBLE
            binding.graficoTendencia.setDatos(huerta.historial, mostrandoHumedadEnTendencia)
        } else {
            binding.cardTendencia.visibility = View.GONE
        }

        if (lectura.riegoActivo) {
            binding.txtRiegoActivo.text = "Riego activo"
            binding.txtRiegoActivo.setTextColor(Color.parseColor("#1A6FA8"))
        } else {
            binding.txtRiegoActivo.text = "Riego apagado"
            binding.txtRiegoActivo.setTextColor(Color.parseColor("#6E685F"))
        }

        val actualizacion = huerta.ultimaActualizacion
        binding.txtUltimaActualizacion.text = if (actualizacion != null) {
            "Última lectura: ${formatearHaceCuanto(actualizacion)}"
        } else {
            ""
        }
    }

    private fun formatearHaceCuanto(timestampMs: Long): String {
        val segundos = ((System.currentTimeMillis() - timestampMs) / 1000).coerceAtLeast(0)
        return when {
            segundos < 60 -> "hace ${segundos}s"
            segundos < 3600 -> "hace ${segundos / 60}min"
            else -> "hace ${segundos / 3600}h"
        }
    }

    private fun seleccionarMetricaTendencia(esHumedad: Boolean) {
        if (mostrandoHumedadEnTendencia == esHumedad) return
        mostrandoHumedadEnTendencia = esHumedad
        binding.chipTendenciaHumedad.setBackgroundResource(
            if (esHumedad) R.drawable.bg_chip_capa_seleccionado else R.drawable.bg_chip_capa
        )
        binding.chipTendenciaHumedad.setTextColor(
            getColor(if (esHumedad) R.color.simona_on_color else R.color.simona_tinta)
        )
        binding.chipTendenciaTemp.setBackgroundResource(
            if (esHumedad) R.drawable.bg_chip_capa else R.drawable.bg_chip_capa_seleccionado
        )
        binding.chipTendenciaTemp.setTextColor(
            getColor(if (esHumedad) R.color.simona_tinta else R.color.simona_on_color)
        )
        cargarYMostrar()
    }

    private fun abrirDashboardEnVivo() {
        startActivity(TutorialConexionActivity.crearIntent(this, huertaId))
    }

    /**
     * Base de URL del dispositivo conectado (mismo criterio que
     * TutorialConexionActivity.resolverUrlBase(): WifiConnectionManager
     * resuelve el gateway real de la red en tiempo de ejecución).
     */
    private fun resolverUrlBase(): String =
        wifiManager.resolverUrl(getString(R.string.dashboard_url))

    /**
     * GET /data del dispositivo, en un hilo aparte, y si sale bien persiste
     * la lectura en HuertaRepository (mismo shape de campos que consumía
     * antes PuenteAsistente.datosActualizados() en la ya eliminada
     * DashboardActivity). cargarYMostrar() ya lee de ahí, así que alcanza
     * con volver a llamarla en el hilo principal al terminar.
     *
     * Solo tiene efecto en modo conectado; se llama tanto desde el primer
     * fetch de onCreate() como desde cada ciclo de refrescoRunnable.
     */
    private fun fetchDatosEnVivo() {
        if (!modoConectado || fetchEnCurso) return
        fetchEnCurso = true
        val urlBase = resolverUrlBase()
        Thread {
            var conexion: HttpURLConnection? = null
            try {
                conexion = (URL("$urlBase/data").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val codigo = conexion.responseCode
                if (codigo in 200..299) {
                    val texto = conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val d = JSONObject(texto)
                    val lectura = LecturaHuerta(
                        humedad = d.getDouble("humedad_suelo").toFloat(),
                        temperatura = d.getDouble("temperatura").toFloat(),
                        luz = d.getDouble("luminosidad").toInt(),
                        ph = d.getDouble("ph").toFloat(),
                        riegoActivo = d.getBoolean("riego_activo"),
                        tanqueAgua = d.getDouble("tanque_agua").toFloat()
                    )
                    repository.actualizarLectura(huertaId, lectura)
                } else {
                    Log.w(TAG, "GET /data respondió $codigo")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo traer /data del dispositivo: ${e.message}")
            } finally {
                conexion?.disconnect()
            }
            fetchEnCurso = false
            runOnUiThread { cargarYMostrar() }
        }.start()
    }

    /**
     * POST genérico a un endpoint de comando (/riego_manual,
     * /refill_tanque), sin body — mismo contrato que ya usan ambos en
     * servidor_simulado.py. onOk se dispara en el hilo principal solo si
     * la respuesta fue 2xx; si falla, se avisa con un Toast en vez de
     * quedar en silencio (mismo criterio de UX que el resto de la app).
     */
    private fun enviarComando(path: String, onOk: () -> Unit) {
        val urlBase = resolverUrlBase()
        Thread {
            var conexion: HttpURLConnection? = null
            var exito = false
            try {
                conexion = (URL("$urlBase$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                conexion.outputStream.use { it.write(ByteArray(0)) }
                exito = conexion.responseCode in 200..299
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar $path: ${e.message}")
            } finally {
                conexion?.disconnect()
            }
            runOnUiThread {
                if (exito) {
                    onOk()
                    fetchDatosEnVivo()
                } else {
                    Toast.makeText(this, R.string.detalle_error_comando_dispositivo, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun alternarRiegoManual() {
        val activo = repository.obtener(huertaId)?.ultimaLectura?.riegoActivo ?: false
        enviarComando("/riego_manual") {
            val mensaje = if (activo) R.string.detalle_toast_riego_detenido else R.string.detalle_toast_riego_activado
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    private fun rellenarTanque() {
        enviarComando("/refill_tanque") {
            Toast.makeText(this, R.string.detalle_toast_tanque_rellenado, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Refresca la pantalla cada POLLING_INTERVAL_MS mientras está visible.
     * En modo conectado, cada ciclo primero hace fetchDatosEnVivo() (que ya
     * llama a cargarYMostrar() al terminar); en modo offline directamente
     * relee el repositorio, por si el modo conectado de otra pestaña de la
     * navegación (o un polling recién detenido) sigue escribiendo lecturas
     * nuevas en paralelo.
     */
    override fun onResume() {
        super.onResume()
        refrescoRunnable = object : Runnable {
            override fun run() {
                if (modoConectado) fetchDatosEnVivo() else cargarYMostrar()
                refrescoHandler.postDelayed(this, POLLING_INTERVAL_MS)
            }
        }
        refrescoHandler.postDelayed(refrescoRunnable!!, POLLING_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        refrescoRunnable?.let { refrescoHandler.removeCallbacks(it) }
        refrescoRunnable = null
    }

    /**
     * Mismo criterio que tenía DashboardActivity.onStop(): solo se libera
     * la red de SIMONA cuando la Activity realmente se está cerrando
     * (isFinishing == true), no al simplemente minimizar la app — así el
     * usuario puede ir a "apps recientes" sin perder la conexión.
     */
    override fun onStop() {
        super.onStop()
        if (isFinishing && modoConectado) {
            wifiManager.disconnect()
        }
    }

    companion object {
        private const val TAG = "DetalleHuertaActivity"
        private const val POLLING_INTERVAL_MS = 3000L
        private const val EXTRA_HUERTA_ID = "extra_huerta_id"
        private const val EXTRA_MODO_CONECTADO = "extra_modo_conectado"

        /**
         * modoConectado = true cuando se llega recién desde
         * TutorialConexionActivity (el proceso ya está atado a la red del
         * dispositivo): habilita el polling en vivo y los controles
         * operativos. Con el valor por defecto (false) esta pantalla se
         * abre 100% offline, como toda la vida (ej. desde "tocar la
         * humedad" en el home).
         */
        fun crearIntent(context: Context, huertaId: String, modoConectado: Boolean = false): Intent =
            Intent(context, DetalleHuertaActivity::class.java).apply {
                putExtra(EXTRA_HUERTA_ID, huertaId)
                putExtra(EXTRA_MODO_CONECTADO, modoConectado)
            }
    }
}
