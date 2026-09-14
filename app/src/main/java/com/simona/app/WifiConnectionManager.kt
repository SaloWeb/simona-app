package com.simona.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Maneja la conexión WiFi hacia la red local del ESP32 (SSID "SIMONA"),
 * acotada exclusivamente a esta app mediante ConnectivityManager.requestNetwork()
 * + WifiNetworkSpecifier, sin promover esa red a "red por defecto" del celular.
 *
 * Ver Plan de Desarrollo — Aplicación Android, sección 5.
 *
 * Nota: este archivo sigue el fragmento de referencia conceptual del documento
 * (sección 5.5), ajustado a un ciclo de vida real de Activity.
 *
 * Cambio (sección 14, múltiples huertas): la contraseña ya no es fija por
 * instancia — cada huerta puede tener la suya propia (mismo SSID "SIMONA"
 * para todas, ver 14.4). Por eso password ahora es parámetro de connect(),
 * no del constructor: la misma instancia compartida vía SimonaApp puede
 * conectarse a huertas distintas sin necesidad de recrearse.
 */
class WifiConnectionManager(
    private val context: Context,
    private val ssid: String = "SIMONA"
) {

    companion object {
        private const val TAG = "WifiConnectionManager"
        // Tiempo máximo de espera antes de considerar la solicitud fallida.
        // onUnavailable() del sistema puede tardar; este timeout es un respaldo
        // adicional para no dejar la UI colgada indefinidamente.
        private const val TIMEOUT_MS = 15_000L

        // Fase 6.3 — cantidad de niveles para bucketear el RSSI (0=Débil ..
        // 3=Excelente), mismo criterio que usa el propio Android para el
        // ícono de señal del sistema.
        private const val NIVELES_SENAL = 4
    }

    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var network: Network? = null

    // Fase 0.1 — id de la huerta a la que está atado el proceso ACTUALMENTE
    // (o null si no hay ninguna). Existe para que el atajo de reconexión
    // (TutorialConexionActivity.onStart) pueda distinguir "ya estoy en la
    // huerta que quiero abrir" de "estoy atado a OTRA huerta" — antes de
    // esto, isConnected() solo veía "hay red", sin importar de cuál huerta,
    // así que el atajo podía abrir el dashboard equivocado.
    var huertaIdConectada: String? = null
        private set

    /**
     * Solicita la conexión a la red SIMONA. Los callbacks se disparan en el
     * hilo principal.
     *
     * huertaId: id de la huerta a la que se quiere conectar (sección 14.4),
     *           se guarda junto con la red para poder responder
     *           estaConectadoA() más adelante.
     * password: contraseña de la huerta específica a la que se quiere
     *           conectar (sección 14.4 — cada huerta tiene la suya).
     * onConnected: la red está disponible y el proceso ya quedó atado a ella
     *              (bindProcess). A partir de acá se puede abrir
     *              DetalleHuertaActivity en modo conectado.
     * onError: falló la conexión (timeout, red no encontrada, contraseña
     *          incorrecta — Android no distingue estos dos últimos casos).
     * onLost: la conexión se perdió en pleno uso (el usuario se alejó del ESP32).
     */
    fun connect(
        huertaId: String,
        password: String,
        onConnected: (Network) -> Unit,
        onError: (String) -> Unit,
        onLost: () -> Unit
    ) {
        // Fase 0.5 — si el proceso ya está atado a OTRA huerta, hay que
        // soltarla primero: si no, quedarían dos NetworkCallback vivos (el
        // viejo nunca se desregistra) y el proceso terminaría bindeado a la
        // red que gane la carrera, no necesariamente la nueva.
        if (network != null && huertaIdConectada != huertaId) {
            disconnect()
        }

        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(net: Network) {
                cancelTimeout()
                network = net
                huertaIdConectada = huertaId
                // Ata TODO el proceso a la red de SIMONA (incluidas las
                // peticiones HTTP nativas de DetalleHuertaActivity —
                // polling a /data, /riego_manual, /refill_tanque), sin
                // tocar la red por defecto del sistema. Ver sección 5.2.
                cm?.bindProcessToNetwork(net)
                Log.d(TAG, "Conectado a $ssid (huerta $huertaId)")
                onConnected(net)
            }

            override fun onUnavailable() {
                cancelTimeout()
                Log.w(TAG, "No se pudo conectar a $ssid (timeout o rechazo)")
                onError(context.getString(R.string.error_timeout))
            }

            override fun onLost(net: Network) {
                cancelTimeout()
                cm?.bindProcessToNetwork(null)
                network = null
                huertaIdConectada = null
                Log.w(TAG, "Conexión a $ssid perdida")
                onLost()
            }
        }

        cm?.requestNetwork(request, callback!!, timeoutHandler)

        // Respaldo: si Android tarda demasiado en resolver la solicitud sin
        // llamar ni a onAvailable ni a onUnavailable, no dejamos la UI colgada.
        timeoutRunnable = Runnable {
            Log.w(TAG, "Timeout manual esperando conexión a $ssid")
            onError(context.getString(R.string.error_timeout))
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
    }

    /** Indica si actualmente hay una red activa (solicitada por esta instancia). */
    fun isConnected(): Boolean = network != null

    /**
     * Fase 0.3 — a diferencia de isConnected(), no solo dice si hay una red
     * viva: dice si esa red es la de ESTA huerta puntual. Es lo que debe
     * usar cualquier atajo de "ya estás conectado, saltá directo al
     * dashboard" (ver TutorialConexionActivity.onStart), para no confundir
     * "conectado a algo" con "conectado a lo que corresponde".
     */
    fun estaConectadoA(huertaId: String): Boolean =
        isConnected() && huertaIdConectada == huertaId

    /**
     * Fase 6.3 — RSSI (dBm, típicamente entre -30 y -90) de la red SIMONA
     * actualmente conectada. Se lee desde NetworkCapabilities.transportInfo
     * (no desde WifiManager.connectionInfo global) porque esta red no es la
     * "red por defecto" del sistema — está pedida puntualmente vía
     * WifiNetworkSpecifier y nunca se promueve a default (ver connect()),
     * así que WifiManager.connectionInfo podría devolver datos de otra red
     * (o ninguno) sin relación con SIMONA.
     *
     * Al ser una red que la propia app solicitó vía WifiNetworkSpecifier,
     * Android expone su WifiInfo (incluido el RSSI) sin necesitar permiso
     * de ubicación — mismo motivo por el que WifiNetworkSpecifier no
     * requiere ACCESS_FINE_LOCATION desde Android 10 (ver comentario de
     * minSdk en build.gradle.kts).
     *
     * Devuelve null si todavía no hay red conectada.
     */
    fun obtenerRssi(): Int? {
        val net = network ?: return null
        val info = cm?.getNetworkCapabilities(net)?.transportInfo as? WifiInfo ?: return null
        return info.rssi
    }

    /**
     * Fase 6.3 — convierte obtenerRssi() a un nivel discreto 0 (Débil) a
     * NIVELES_SENAL-1 (Excelente), para no mostrarle un número en dBm crudo
     * al usuario (poco intuitivo) sino una etiqueta tipo "Buena señal".
     * calculateSignalLevel() está deprecado a favor de una versión de
     * instancia agregada en API 30, pero sigue siendo válido en cualquier
     * versión soportada (minSdk 29) — se mantiene la versión estática para
     * no tener que ramificar por SDK_INT solo para esto.
     */
    @Suppress("DEPRECATION")
    fun obtenerNivelSenal(): Int? {
        val rssi = obtenerRssi() ?: return null
        return WifiManager.calculateSignalLevel(rssi, NIVELES_SENAL)
    }

    /**
     * Resuelve la IP del dispositivo que sirve el dashboard, leyendo el
     * gateway de la red actualmente conectada — en modo Access Point
     * (ESP32, hotspot de notebook o de celular), el gateway siempre es el
     * propio dispositivo que hace de servidor, sin importar qué IP le haya
     * tocado ese día: 192.168.4.1 en el ESP32 real, 10.42.0.1 en un hotspot
     * de notebook con nmcli, otra distinta en un hotspot de Windows o en el
     * hotspot nativo de un celular (varía según fabricante).
     *
     * Resolviendo esto en tiempo de ejecución, la app deja de depender de
     * una IP fija hardcodeada que hay que editar y recompilar cada vez que
     * cambia el entorno de prueba (ver sección 10.2/10.4 — problema real
     * detectado probando con notebook Windows y con dos celulares).
     *
     * Devuelve null si todavía no hay red conectada o si por algún motivo
     * Android no expone el gateway (poco común).
     */
    fun obtenerIpGateway(): String? {
        val net = network ?: return null
        val linkProperties = cm?.getLinkProperties(net) ?: return null
        return linkProperties.routes
            .firstOrNull { it.isDefaultRoute }
            ?.gateway
            ?.hostAddress
    }

    /**
     * Fase 4.1 — antes vivía duplicado (con variaciones menores) como
     * resolverUrlBase() privado en TutorialConexionActivity y en la ya
     * eliminada DashboardActivity; hoy el mismo duplicado existe entre
     * TutorialConexionActivity y DetalleHuertaActivity. Se centraliza acá
     * porque la lógica depende pura y exclusivamente del estado de ESTE
     * manager (obtenerIpGateway()), no de nada propio de ninguna de las
     * Activities — es lógica de conexión, no de UI.
     *
     * Toma una URL ya configurada (típicamente R.string.dashboard_url, que
     * define esquema y puerto — ver README "Modo demo vs. modo hardware
     * real") y le reemplaza el host por el gateway real de la red conectada
     * (ver obtenerIpGateway()). Si todavía no hay red, o la URL configurada
     * no se puede parsear, se devuelve la URL configurada tal cual, sin
     * romper el flujo.
     */
    fun resolverUrl(urlConfigurada: String): String {
        val ipGateway = obtenerIpGateway() ?: return urlConfigurada.trimEnd('/')

        return try {
            val uri = android.net.Uri.parse(urlConfigurada)
            val autoridad = if (uri.port != -1) "$ipGateway:${uri.port}" else ipGateway
            uri.buildUpon().authority(autoridad).build().toString().trimEnd('/')
        } catch (e: Exception) {
            // Si algo sale mal parseando la URL configurada, no rompemos
            // el flujo: caemos a la URL fija de siempre.
            urlConfigurada.trimEnd('/')
        }
    }

    /**
     * Restaura el binding del proceso a la red del ESP32 si sigue conectada.
     * Se llama después de hacer peticiones de red externas (como la IA
     * Gemini, que necesita su propia red de internet real en paralelo).
     */
    fun rebindProcess() {
        network?.let { cm?.bindProcessToNetwork(it) }
    }

    /**
     * Libera la conexión y desata el proceso de la red de SIMONA
     * (sección 5.2). Llamar en onStop()/onDestroy() de la Activity.
     */
    fun disconnect() {
        cancelTimeout()
        callback?.let { cm?.unregisterNetworkCallback(it) }
        cm?.bindProcessToNetwork(null)
        callback = null
        network = null
        huertaIdConectada = null
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }
}
