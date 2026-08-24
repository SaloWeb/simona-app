package com.simona.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.simona.app.databinding.ActivityTutorialConexionBinding
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * D.4 — Pantalla de conexión adaptada a UNA huerta específica (sección 14.5).
 * Reemplaza el flujo viejo de MainActivity (contraseña fija "simona123"):
 * acá se lee la huerta guardada en D.3 y se usa SU contraseña real al
 * llamar a WifiConnectionManager.connect() (sección 14.4/B.4).
 *
 * Cambio (cierre del hueco "Config push", sección 14 del Plan): una vez que
 * la conexión WiFi se confirma, se empuja la configuración de la huerta
 * (nombre + los 8 rangos) al dispositivo vía POST /config, ANTES de abrir
 * el dashboard. Sin esto, el dispositivo (real o simulado) no tiene forma
 * de saber qué huerta es — el simulador arranca cada vez con sus valores
 * de fábrica (sin persistencia en NVS, limitación aceptada) y el ESP32
 * real tampoco tendría de dónde sacar esos datos por su cuenta. La app es
 * la única que los tiene guardados (HuertaRepository), así que es la app
 * quien debe entregárselos al dispositivo en el momento de conectar.
 *
 * Fase 6.3 — mientras se espera (handshake WiFi ya resuelto, config
 * pusheándose), se muestra la fuerza de señal (RSSI) de la red SIMONA, para
 * que el usuario entienda por qué una conexión "exitosa" puede tardar o
 * fallar en cargar el dashboard si está muy lejos del dispositivo.
 */
class TutorialConexionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialConexionBinding
    private lateinit var wifiManager: WifiConnectionManager
    private lateinit var huerta: Huerta

    private val rssiHandler = Handler(Looper.getMainLooper())
    private var rssiRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialConexionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = (application as SimonaApp).wifiConnectionManager

        val huertaId = intent.getStringExtra(EXTRA_HUERTA_ID)
        val huertaGuardada = (application as SimonaApp).huertaRepository.obtener(huertaId ?: "")

        if (huertaGuardada == null) {
            // No debería pasar (siempre venimos de D.3 recién guardando),
            // pero si pasa no tiene sentido mostrar una pantalla de conexión
            // sin huerta: volvemos atrás en vez de crashear.
            finish()
            return
        }
        huerta = huertaGuardada

        binding.tvHuertaNombre.text = huerta.nombre
        binding.btnConectar.setOnClickListener { iniciarConexion() }

        renderState(ConnectionState.Desconectado)
    }

    override fun onStart() {
        super.onStart()
        // Fase 0.4 — el atajo solo aplica si el proceso está atado A ESTA
        // huerta puntual. Antes se usaba wifiManager.isConnected(), que solo
        // ve "hay red", sin importar de cuál huerta: si el usuario venía de
        // conectarse a la Huerta A y volvía a esta pantalla para la Huerta
        // B, saltaba directo al dashboard de A con el proceso todavía atado
        // a la red de A. Con estaConectadoA(huerta.id), si el proceso está
        // en otra huerta, no se salta — se fuerza iniciarConexion(), que a
        // su vez suelta la red vieja antes de pedir la nueva (ver 0.5 en
        // WifiConnectionManager.connect()).
        //
        // Nota: en el caso "ya estoy en ESTA huerta" NO se vuelve a empujar
        // la config (la conexión ya estaba viva de antes, se asume que el
        // dispositivo ya la tiene de cuando se conectó la primera vez en
        // esta sesión) — mismo criterio que antes.
        if (wifiManager.estaConectadoA(huerta.id)) {
            abrirDashboard()
        }
    }

    override fun onStop() {
        super.onStop()
        // Fase 6.3 — no seguir consultando RSSI si la pantalla no está
        // visible (evita el runnable colgado indefinidamente si el usuario
        // sale de la app justo durante el tramo "Configurando...").
        detenerPollingRssi()
    }

    private fun iniciarConexion() {
        renderState(ConnectionState.Conectando)
        wifiManager.connect(
            huertaId = huerta.id,
            password = huerta.passwordRed,
            onConnected = {
                // El handshake WiFi ya cerró: recién a partir de acá hay
                // RSSI real para mostrar (antes de esto no hay red asociada).
                iniciarPollingRssi()
                binding.tvEstadoSubtitulo.text = getString(R.string.tutorial_configurando)
                empujarConfiguracionAlDispositivo {
                    renderState(ConnectionState.Conectado)
                    abrirDashboard()
                }
            },
            onError = { mensaje ->
                detenerPollingRssi()
                renderState(ConnectionState.Error(mensaje))
            },
            onLost = {
                detenerPollingRssi()
                renderState(ConnectionState.Desconectado)
            }
        )
    }

    /**
     * Fase 6.3 — arranca el refresco periódico del indicador de señal.
     * Se llama detenerPollingRssi() primero por si veníamos de un intento
     * de conexión anterior (ej. usuario tocó "Reintentar") que dejó un
     * runnable corriendo.
     */
    private fun iniciarPollingRssi() {
        detenerPollingRssi()
        rssiRunnable = object : Runnable {
            override fun run() {
                actualizarIndicadorSenal()
                rssiHandler.postDelayed(this, RSSI_POLL_INTERVAL_MS)
            }
        }
        rssiHandler.post(rssiRunnable!!)
    }

    private fun detenerPollingRssi() {
        rssiRunnable?.let { rssiHandler.removeCallbacks(it) }
        rssiRunnable = null
        binding.filaSenalWifi.visibility = View.GONE
    }

    /**
     * Traduce WifiConnectionManager.obtenerNivelSenal() (0..3) a una
     * etiqueta + color, reutilizando la paleta de estados que ya usa el
     * resto de la app (verde=óptimo, naranja=alerta media, rojo=crítico —
     * misma familia de colores que EstadoCapa en MiniMapaCapasView.kt).
     */
    private fun actualizarIndicadorSenal() {
        val nivel = wifiManager.obtenerNivelSenal()
        if (nivel == null) {
            binding.filaSenalWifi.visibility = View.GONE
            return
        }

        val (textoRes, colorRes) = when (nivel) {
            0 -> R.string.senal_debil to R.color.simona_rojo
            1 -> R.string.senal_regular to R.color.simona_estado_seco
            2 -> R.string.senal_buena to R.color.simona_verde_claro
            else -> R.string.senal_excelente to R.color.simona_estado_optimo
        }

        val color = ContextCompat.getColor(this, colorRes)
        binding.filaSenalWifi.visibility = View.VISIBLE
        binding.tvSenalWifi.text = getString(textoRes)
        binding.tvSenalWifi.setTextColor(color)
        binding.imgSenalWifi.setColorFilter(color)
    }

    /**
     * POST /config al dispositivo recién conectado, con el nombre y los 8
     * rangos de ESTA huerta (mismos campos que valida _actualizar_config()
     * en servidor_simulado.py: nombre, categoria, humedadMin/Max, phMin/Max,
     * luzMin/Max, tempMin/Max).
     *
     * Fase 1.1 — mismo criterio de reintentos cortos que
     * DashboardActivity.mostrarEsperandoDispositivo(): hasta
     * MAX_REINTENTOS_CONFIG intentos, con REINTENTO_CONFIG_DELAY_MS entre
     * cada uno, antes de rendirse y avanzar igual al dashboard. Cubre el
     * caso típico de "el dispositivo todavía está terminando de levantar
     * su servidor" justo cuando la app ya se conectó al WiFi.
     *
     * Corre en un hilo aparte (no bloquea UI) con timeout corto por
     * intento. Si se agotan los reintentos, NO se traba el flujo: se
     * avisa al usuario con un Toast (1.2) y se continúa igual al
     * dashboard — es preferible mostrar datos desactualizados a dejar al
     * usuario colgado en la pantalla de conexión por un problema de config.
     *
     * onFinalizado siempre se dispara en el hilo principal, haya
     * funcionado el push o no.
     */
    private fun empujarConfiguracionAlDispositivo(
        intentosRestantes: Int = MAX_REINTENTOS_CONFIG,
        onFinalizado: () -> Unit
    ) {
        val urlBase = resolverUrlBase()
        Thread {
            var conexion: HttpURLConnection? = null
            var exito = false
            try {
                conexion = (URL("$urlBase/config").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                val body = JSONObject().apply {
                    put("nombre", huerta.nombre)
                    put("categoria", huerta.categoria)
                    put("humedadMin", huerta.humedadMin)
                    put("humedadMax", huerta.humedadMax)
                    put("phMin", huerta.phMin)
                    put("phMax", huerta.phMax)
                    put("luzMin", huerta.luzMin)
                    put("luzMax", huerta.luzMax)
                    put("tempMin", huerta.tempMin)
                    put("tempMax", huerta.tempMax)
                }

                conexion.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val codigo = conexion.responseCode
                if (codigo in 200..299) {
                    exito = true
                } else {
                    // Fase 1.3 — el simulador devuelve {"ok": false, "error": "..."}
                    // en _responder_error(); se parsea para loguear el motivo
                    // real (ej. "El nombre de la huerta es obligatorio") en vez
                    // de solo el código HTTP, que por sí solo no dice nada.
                    val mensajeError = leerMensajeError(conexion)
                    Log.w(TAG, "POST /config respondió $codigo: $mensajeError")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo empujar la configuración al dispositivo: ${e.message}")
            } finally {
                conexion?.disconnect()
            }

            if (exito) {
                runOnUiThread { onFinalizado() }
            } else if (intentosRestantes > 1) {
                Handler(Looper.getMainLooper()).postDelayed({
                    empujarConfiguracionAlDispositivo(intentosRestantes - 1, onFinalizado)
                }, REINTENTO_CONFIG_DELAY_MS)
            } else {
                // Fase 1.2 — se agotaron los reintentos: aviso visible (no
                // solo log) de por qué el dashboard puede mostrar datos
                // desactualizados, y se continúa igual.
                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.error_sincronizar_config,
                        Toast.LENGTH_SHORT
                    ).show()
                    onFinalizado()
                }
            }
        }.start()
    }

    /**
     * Lee el body de error de una respuesta HTTP no-2xx y extrae el campo
     * "error" del JSON que devuelve _responder_error() en el simulador
     * (y que debería devolver también el ESP32 real si implementa el mismo
     * contrato). Si el body no es JSON válido o no trae ese campo, se
     * devuelve tal cual llegó para no perder información en el log.
     */
    private fun leerMensajeError(conexion: HttpURLConnection): String {
        val stream: InputStream = conexion.errorStream ?: conexion.inputStream
        return try {
            val texto = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(texto).optString("error", texto)
        } catch (e: Exception) {
            "(sin detalle: ${e.message})"
        }
    }

    /**
     * Fase 4.1 — antes había una versión privada de esto acá, casi idéntica
     * a la de DashboardActivity.resolverUrlBase(). Ahora ambas Activities
     * delegan en WifiConnectionManager.resolverUrl(), que es donde
     * realmente vive el estado necesario (obtenerIpGateway()).
     */
    private fun resolverUrlBase(): String =
        wifiManager.resolverUrl(getString(R.string.dashboard_url))

    private fun abrirDashboard() {
        detenerPollingRssi()
        startActivity(DashboardActivity.crearIntent(this, huerta.id))
        finish()
    }

    private fun renderState(nuevoEstado: ConnectionState) {
        when (nuevoEstado) {
            is ConnectionState.Desconectado -> {
                binding.progressBar.visibility = View.GONE
                binding.btnConectar.visibility = View.VISIBLE
                binding.btnConectar.isEnabled = true
                binding.btnConectar.text = getString(R.string.btn_connect)
                binding.tvEstadoSubtitulo.text = getString(R.string.tutorial_subtitulo, huerta.nombre)
            }
            is ConnectionState.Conectando -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnConectar.isEnabled = false
                binding.tvEstadoSubtitulo.text = getString(R.string.status_connecting)
            }
            is ConnectionState.Conectado -> {
                binding.progressBar.visibility = View.GONE
            }
            is ConnectionState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.btnConectar.visibility = View.VISIBLE
                binding.btnConectar.isEnabled = true
                binding.btnConectar.text = getString(R.string.btn_retry)
                binding.tvEstadoSubtitulo.text = nuevoEstado.mensaje
            }
        }
    }

    companion object {
        private const val TAG = "TutorialConexionActivity"
        const val EXTRA_HUERTA_ID = "extra_huerta_id"

        // Fase 1.1 — hasta 3 intentos de POST /config con ~1.5s entre cada
        // uno, mismo orden de magnitud que el backoff de
        // DashboardActivity.mostrarEsperandoDispositivo() para el caso
        // equivalente (dispositivo que todavía no responde).
        private const val MAX_REINTENTOS_CONFIG = 3
        private const val REINTENTO_CONFIG_DELAY_MS = 1500L

        // Fase 6.3 — cada cuánto se refresca el indicador de señal. No hace
        // falta que sea tan frecuente como el polling de lecturas del
        // dashboard (3s): acá es solo para orientar al usuario durante un
        // tramo breve de la pantalla de conexión.
        private const val RSSI_POLL_INTERVAL_MS = 2000L

        fun crearIntent(context: Context, huertaId: String): Intent =
            Intent(context, TutorialConexionActivity::class.java)
                .putExtra(EXTRA_HUERTA_ID, huertaId)
    }
}
