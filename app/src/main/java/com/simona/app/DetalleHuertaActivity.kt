package com.simona.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.simona.app.databinding.ActivityDetalleHuertaBinding
import kotlin.math.roundToInt

/**
 * Pantalla de detalle de UNA huerta (Fase 5.4 del plan de unificación
 * visual): a diferencia de DashboardActivity (WebView "en vivo" contra el
 * ESP32, con control operativo — riego manual, refill de tanque), esta
 * pantalla es analítica y funciona 100% offline: lee la última lectura
 * conocida desde HuertaRepository (la misma que persiste el polling de
 * DashboardActivity.onResume()/iniciarPollingLecturas()), sin necesitar
 * estar conectado al WiFi del ESP32 en ese momento.
 *
 * Se actualiza sola cada POLLING_INTERVAL_MS por si el dashboard en vivo
 * está corriendo en paralelo (o corrió recientemente) y sigue escribiendo
 * lecturas nuevas al repositorio.
 */
class DetalleHuertaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleHuertaBinding
    private lateinit var repository: HuertaRepository
    private var huertaId: String = ""

    private val refrescoHandler = Handler(Looper.getMainLooper())
    private var refrescoRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleHuertaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as SimonaApp).huertaRepository
        huertaId = intent.getStringExtra(EXTRA_HUERTA_ID) ?: ""

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnIrADashboard.setOnClickListener { abrirDashboardEnVivo() }

        cargarYMostrar()
    }

    private fun cargarYMostrar() {
        val huerta = repository.obtener(huertaId)
        if (huerta == null) {
            finish() // la huerta ya no existe (se borró desde MapaHuertasActivity)
            return
        }

        binding.txtNombreHuerta.text = huerta.nombre
        binding.txtCategoriaHuerta.text = huerta.categoria

        val lectura = huerta.ultimaLectura
        if (lectura == null) {
            binding.estadoSinLecturas.visibility = View.VISIBLE
            binding.contenidoConLecturas.visibility = View.GONE
            return
        }

        binding.estadoSinLecturas.visibility = View.GONE
        binding.contenidoConLecturas.visibility = View.VISIBLE

        binding.miniMapaCapas.setCapas(construirCapasDesdeHuerta(huerta, lectura))

        binding.txtTanqueAgua.text = "${lectura.tanqueAgua.roundToInt()}%"

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

    private fun abrirDashboardEnVivo() {
        startActivity(TutorialConexionActivity.crearIntent(this, huertaId))
    }

    /** Refresca la pantalla cada POLLING_INTERVAL_MS mientras está visible,
     * por si DashboardActivity sigue escribiendo lecturas nuevas al
     * repositorio en paralelo (misma huerta abierta en otra pestaña de la
     * navegación, o polling recién detenido hace instantes). */
    override fun onResume() {
        super.onResume()
        refrescoRunnable = object : Runnable {
            override fun run() {
                cargarYMostrar()
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

    companion object {
        private const val POLLING_INTERVAL_MS = 3000L
        private const val EXTRA_HUERTA_ID = "extra_huerta_id"

        fun crearIntent(context: Context, huertaId: String): Intent =
            Intent(context, DetalleHuertaActivity::class.java).apply {
                putExtra(EXTRA_HUERTA_ID, huertaId)
            }
    }
}
