package com.simona.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.Fade
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simona.app.databinding.ActivityMapaHuertasBinding

/**
 * Home nativo de la app SIMONA: funciona 100% offline (sin depender del ESP32).
 * Muestra el resumen de huertas, lista en tarjetas ricas (RecyclerView, ver
 * HuertaCardAdapter), croquis general del predio con canteros, gestión de
 * huertas (editar/eliminar, ver DialogoEditarHuerta) y acceso al asistente
 * de IA Simona (ver DialogoChatAi) usando la conexión a internet normal
 * del teléfono.
 *
 * Al seleccionar una huerta, deriva a TutorialConexionActivity para conectar
 * el WiFi al ESP32 y abrir el Dashboard en tiempo real.
 *
 * PLAN_MEJORAS_20.md, puntos 7 y 10: esta Activity tenía ~800 líneas,
 * armaba las tarjetas a mano con Views por código en vez de RecyclerView,
 * usaba findViewById manual en vez de ViewBinding, y mezclaba
 * AlertDialog.Builder con el resto del proyecto (que ya usaba
 * MaterialAlertDialogBuilder). Se migró a ViewBinding + RecyclerView +
 * MaterialAlertDialogBuilder de una sola vez, y los diálogos de "editar
 * huerta" y "chat IA" se extrajeron a sus propias clases
 * (DialogoEditarHuerta.kt, DialogoChatAi.kt) para que esta Activity quede
 * solo orquestando. El croquis/mapa con pines (renderizarPines) no se tocó:
 * no es una lista (no hay DiffUtil/adapter que aplicarle) y queda fuera del
 * alcance de este punto del plan.
 */
class MapaHuertasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapaHuertasBinding
    private lateinit var repository: HuertaRepository
    private lateinit var adapter: HuertaCardAdapter

    private var vistaActual = "lista" // "lista" o "mapa"

    // PLAN_MEJORAS_UX_20.md, punto 16: los chips de resumen eran solo
    // informativos (sin click listener) pero visualmente parecían
    // tarjetas tocables. Ahora filtran la lista: null = sin filtro (total).
    private var filtroActual: FiltroHuertas? = null

    private enum class FiltroHuertas { CON_SED, OPTIMAS }

    // Diálogo de "editar huerta" actualmente abierto (si hay uno), para
    // poder pasarle el resultado del picker de fotos cuando vuelve —
    // ActivityResultContracts exige registrarse a nivel Activity, así que
    // el picker en sí no puede vivir dentro de DialogoEditarHuerta.
    private var dialogoEditarActual: DialogoEditarHuerta? = null

    private val seleccionarFotoEditar = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            FotoHuertaUtil.persistirPermisoLectura(contentResolver, uri)
            dialogoEditarActual?.actualizarFotoSeleccionada(uri)
        }
    }

    // Fase 6.5: en Android 13+ hay que pedir este permiso en tiempo de
    // ejecución para que AlertaHuertasWorker pueda mostrar la notificación
    // de "huerta con sed". Si el usuario lo niega, el worker simplemente no
    // notifica (no se vuelve a insistir en esta pantalla).
    private val solicitarPermisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaHuertasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as SimonaApp).huertaRepository

        adapter = HuertaCardAdapter(
            onClick = { huerta -> abrirDashboardHuerta(huerta) },
            onLongClick = { huerta -> mostrarOpcionesHuerta(huerta) },
            onEstadoClick = { huerta -> startActivity(DetalleHuertaActivity.crearIntent(this, huerta.id)) },
            onOpcionesClick = { huerta -> mostrarOpcionesHuerta(huerta) }
        )
        binding.rvHuertas.layoutManager = LinearLayoutManager(this)
        binding.rvHuertas.adapter = adapter

        binding.btnNuevaHuerta.setOnClickListener {
            startActivity(SeleccionarPerfilActivity.crearIntent(this))
        }
        binding.btnNuevaHuertaVacio.setOnClickListener {
            startActivity(SeleccionarPerfilActivity.crearIntent(this))
        }

        // Setup theme toggle button (light / dark)
        binding.btnThemeToggle.setOnClickListener {
            val currentlyDark = ThemePrefs.isDark(this)
            ThemePrefs.setDark(this, !currentlyDark)
            // Update icon immediately and recreate to apply theme changes
            updateThemeIcon(!currentlyDark)
            recreate()
        }
        // Initialize icon based on current preference
        updateThemeIcon(ThemePrefs.isDark(this))

        binding.tabBtnLista.setOnClickListener { cambiarPestana("lista") }
        binding.tabBtnMapa.setOnClickListener { cambiarPestana("mapa") }

        // PLAN_MEJORAS_UX_20.md, punto 15: antes la única forma de
        // refrescar la lista era volver a entrar a la pantalla (onResume).
        // cargarDatos() ya relee HuertaRepository (100% local/sincrónico,
        // sin red), así que el gesto termina casi al instante — el
        // isRefreshing=false se hace en el mismo hilo, sin post() ni
        // delay artificial.
        binding.swipeRefreshHuertas.setColorSchemeResources(R.color.simona_azul)
        binding.swipeRefreshHuertas.setOnRefreshListener {
            cargarDatos()
            binding.swipeRefreshHuertas.isRefreshing = false
        }

        binding.chipTotal.setOnClickListener { aplicarFiltro(null) }
        binding.chipConSed.setOnClickListener { aplicarFiltro(FiltroHuertas.CON_SED) }
        binding.chipOptimas.setOnClickListener { aplicarFiltro(FiltroHuertas.OPTIMAS) }

        binding.fabAiSimona.setOnClickListener { DialogoChatAi.mostrar(this, repository) }

        pedirPermisoNotificacionesSiHaceFalta()

        // cargarDatos() es lo que dispara el primer repository.listar() de
        // esta sesión — chequear la bandera de corrupción DESPUÉS de esa
        // llamada, no antes, para no perderse la corrupción detectada en
        // esta misma carga.
        cargarDatos()

        if (repository.consumirAvisoDeCorrupcion()) {
            mostrarAvisoDatosCorruptos()
        }

        mostrarOnboardingAsistenteSiHaceFalta()
    }

    /**
     * PLAN_MEJORAS_UX_20.md, punto 19: es fácil que un usuario nuevo no
     * note el FAB del asistente de IA la primera vez que abre la app.
     * Muestra un tooltip apuntándolo UNA sola vez (persistido en
     * OnboardingPrefs, mismo patrón que ThemePrefs). El FAB vive fuera de
     * contenidoHome (siempre visible, incluso con la lista vacía), así
     * que no depende de si ya hay huertas cargadas.
     */
    private fun mostrarOnboardingAsistenteSiHaceFalta() {
        if (OnboardingPrefs.fabIaYaVisto(this)) return

        binding.fabAiSimona.post {
            if (isFinishing || isDestroyed) return@post

            val popupView = layoutInflater.inflate(R.layout.popup_onboarding_asistente, null)
            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            popupWindow.elevation = 8f

            fun cerrar() {
                OnboardingPrefs.marcarFabIaVisto(this)
                if (popupWindow.isShowing) popupWindow.dismiss()
            }

            popupView.findViewById<TextView>(R.id.btnCerrarOnboarding).setOnClickListener { cerrar() }
            popupWindow.setOnDismissListener { OnboardingPrefs.marcarFabIaVisto(this) }

            // Ancla el tooltip arriba del FAB, con su borde derecho
            // alineado al del FAB (el FAB es angosto — 56dp —, la tarjeta
            // del tooltip es más ancha, así que hay que compensar en X).
            val xOffset = binding.fabAiSimona.width - popupView.measuredWidth
            val yOffset = -(binding.fabAiSimona.height + popupView.measuredHeight + 12.dpToPx())
            popupWindow.showAsDropDown(binding.fabAiSimona, xOffset, yOffset, Gravity.NO_GRAVITY)
        }
    }

    /**
     * PLAN_MEJORAS_20.md, punto 12: si HuertaRepository detectó JSON
     * corrupto la última vez que se leyó (ver HuertaRepository.listar()),
     * avisar una única vez en vez de que la lista de huertas aparezca
     * vacía sin ninguna explicación.
     */
    private fun mostrarAvisoDatosCorruptos() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.aviso_datos_corruptos_titulo)
            .setMessage(R.string.aviso_datos_corruptos_mensaje)
            .setPositiveButton(R.string.btn_entendido, null)
            .setCancelable(true)
            .show()
    }

    private fun pedirPermisoNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            solicitarPermisoNotificaciones.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        cargarDatos()
    }

    private fun cambiarPestana(pestana: String) {
        vistaActual = pestana
        val esLista = pestana == "lista"
        binding.seccionLista.visibility = if (esLista) View.VISIBLE else View.GONE
        binding.seccionMapa.visibility = if (esLista) View.GONE else View.VISIBLE

        // PLAN_MEJORAS_VISUAL_2.md, punto 14: la pestaña inactiva usaba
        // simona_card_bg (blanco, IGUAL al fondo del propio contenedor
        // tabsContainer que también es bg_chip_resumen/simona_card_bg),
        // así que en la práctica solo se distinguía por el borde de 1dp —
        // contraste muy sutil. Se cambió a simona_superficie (el fondo
        // general de la pantalla, distinto de ambos: del contenedor
        // blanco y de la pestaña activa celeste), sin tocar el color de
        // la pestaña activa.
        binding.tabBtnLista.isSelected = esLista
        binding.tabBtnLista.backgroundTintList = ContextCompat.getColorStateList(
            this, if (esLista) R.color.simona_azul_suave else R.color.simona_superficie
        )
        binding.tabBtnLista.setTextColor(
            ContextCompat.getColor(this, if (esLista) R.color.simona_azul else R.color.simona_tinta_suave)
        )

        binding.tabBtnMapa.isSelected = !esLista
        binding.tabBtnMapa.backgroundTintList = ContextCompat.getColorStateList(
            this, if (!esLista) R.color.simona_azul_suave else R.color.simona_superficie
        )
        binding.tabBtnMapa.setTextColor(
            ContextCompat.getColor(this, if (!esLista) R.color.simona_azul else R.color.simona_tinta_suave)
        )

        if (!esLista) {
            val huertas = repository.listar()
            renderizarPines(huertas)
        }
    }

    private fun cargarDatos() {
        val huertas = repository.listar()
        val hayHuertas = huertas.isNotEmpty()

        // PLAN_MEJORAS_UX_20.md, punto 20: antes el paso de "vacío" a "con
        // huertas" (y viceversa, ej. al eliminar la última) cambiaba de
        // golpe; un Fade corto lo suaviza. Solo se dispara cuando ESE
        // estado puntual cambia (no en cada refresh/filtro), para no pisar
        // las animaciones propias del DiffUtil del RecyclerView.
        val estabaVacio = binding.estadoVacio.visibility == View.VISIBLE
        val estadoVacioCambio = estabaVacio == hayHuertas
        if (estadoVacioCambio) {
            TransitionManager.beginDelayedTransition(binding.contenidoHome, Fade().setDuration(180))
        }

        binding.estadoVacio.visibility = if (hayHuertas) View.GONE else View.VISIBLE
        binding.resumenChipsContainer.visibility = if (hayHuertas) View.VISIBLE else View.GONE
        binding.tabsContainer.visibility = if (hayHuertas) View.VISIBLE else View.GONE
        binding.seccionLista.visibility = if (hayHuertas && vistaActual == "lista") View.VISIBLE else View.GONE
        binding.seccionMapa.visibility = if (hayHuertas && vistaActual == "mapa") View.VISIBLE else View.GONE

        if (!hayHuertas) {
            adapter.submitList(emptyList())
            return
        }

        val total = huertas.size
        val conSed = huertas.count { h ->
            val l = h.ultimaLectura
            l != null && l.humedad <= h.humedadMin
        }
        val optimas = total - conSed

        // PLAN_MEJORAS_VISUAL_2.md, punto 3: tip visible solo con 1-2
        // huertas en TOTAL (sin filtrar) — si se basara en la lista
        // filtrada, aparecería/desaparecería de forma confusa al tocar
        // los chips de "Con sed"/"Óptimas".
        binding.tvTipPocasHuertas.visibility = if (total in 1..2) View.VISIBLE else View.GONE

        binding.tvChipTotal.text = total.toString()
        binding.tvChipConSed.text = conSed.toString()
        binding.tvChipConSed.setTextColor(
            ContextCompat.getColor(this, if (conSed > 0) R.color.simona_rojo else R.color.simona_tinta_suave)
        )
        binding.tvChipOptimas.text = optimas.toString()

        // PLAN_MEJORAS_UX_20.md, punto 16: la lista (RecyclerView y contador)
        // respeta el filtro activo; el croquis/mapa sigue mostrando todas
        // las huertas (filtrar pines no forma parte de este punto).
        val huertasFiltradas = when (filtroActual) {
            FiltroHuertas.CON_SED -> huertas.filter { h ->
                val l = h.ultimaLectura
                l != null && l.humedad <= h.humedadMin
            }
            FiltroHuertas.OPTIMAS -> huertas.filter { h ->
                val l = h.ultimaLectura
                l == null || l.humedad > h.humedadMin
            }
            null -> huertas
        }

        binding.tvContadorHuertas.text = if (huertasFiltradas.size == 1) {
            getString(R.string.home_contador_una)
        } else {
            getString(R.string.home_contador_varias, huertasFiltradas.size)
        }

        actualizarEstiloChips()
        adapter.submitList(huertasFiltradas)
        renderizarPines(huertas)
    }

    /** PLAN_MEJORAS_UX_20.md, punto 16: click en un chip de resumen filtra
     * la lista; tocar el mismo chip que ya está activo lo desactiva. */
    private fun aplicarFiltro(filtro: FiltroHuertas?) {
        filtroActual = if (filtroActual == filtro) null else filtro
        cargarDatos()
    }

    private fun actualizarEstiloChips() {
        binding.chipTotal.setBackgroundResource(
            if (filtroActual == null) R.drawable.bg_chip_resumen_seleccionado else R.drawable.bg_chip_resumen
        )
        binding.chipConSed.setBackgroundResource(
            if (filtroActual == FiltroHuertas.CON_SED) R.drawable.bg_chip_resumen_seleccionado else R.drawable.bg_chip_resumen
        )
        binding.chipOptimas.setBackgroundResource(
            if (filtroActual == FiltroHuertas.OPTIMAS) R.drawable.bg_chip_resumen_seleccionado else R.drawable.bg_chip_resumen
        )
    }

    private fun renderizarPines(huertas: List<Huerta>) {
        // PLAN_MEJORAS_VISUAL_2.md, punto 9: si hay huertas sin posición
        // asignada (posicionMapaX/Y null), avisar específicamente en vez
        // de dejar el hint genérico — esas huertas no van a tener pin en
        // el croquis y sin este aviso no hay ninguna pista de que existen.
        val sinUbicar = huertas.count { it.posicionMapaX == null || it.posicionMapaY == null }
        binding.tvMapaHint.text = when {
            sinUbicar == 1 -> getString(R.string.home_mapa_hint_sin_ubicar_una)
            sinUbicar > 1 -> getString(R.string.home_mapa_hint_sin_ubicar_varias, sinUbicar)
            else -> getString(R.string.home_mapa_hint)
        }

        binding.mapaContainer.removeAllViews()

        binding.mapaContainer.post {
            val ancho = binding.mapaContainer.width.toFloat()
            val alto = binding.mapaContainer.height.toFloat()
            if (ancho <= 0 || alto <= 0) return@post

            huertas.forEach { huerta ->
                val x = huerta.posicionMapaX ?: return@forEach
                val y = huerta.posicionMapaY ?: return@forEach

                // Fase 6.4: si la huerta tiene foto propia, reemplaza el
                // rectángulo de color del bancal por la foto (con el mismo
                // borde punteado encima, para mantener el lenguaje visual).
                val bitmapFoto = huerta.fotoUri?.let {
                    FotoHuertaUtil.decodificarSampleado(this, Uri.parse(it), 60.dpToPx(), 42.dpToPx())
                }

                val bancal: View = if (bitmapFoto != null) {
                    FrameLayout(this).apply {
                        layoutParams = FrameLayout.LayoutParams(60.dpToPx(), 42.dpToPx())
                        translationX = (x * ancho) - 30.dpToPx()
                        translationY = (y * alto) - 21.dpToPx()

                        val foto = ImageView(this@MapaHuertasActivity).apply {
                            layoutParams = FrameLayout.LayoutParams(60.dpToPx(), 42.dpToPx())
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            // PLAN_MEJORAS_20.md, punto 19: decorativo, el
                            // label de texto al lado del pin ya dice el nombre.
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }
                        FotoHuertaUtil.aplicarConEsquinasRedondeadas(this@MapaHuertasActivity, foto, bitmapFoto)
                        addView(foto)

                        val borde = View(this@MapaHuertasActivity).apply {
                            layoutParams = FrameLayout.LayoutParams(60.dpToPx(), 42.dpToPx())
                            background = ContextCompat.getDrawable(this@MapaHuertasActivity, R.drawable.bg_bancal)
                        }
                        addView(borde)
                    }
                } else {
                    View(this).apply {
                        layoutParams = FrameLayout.LayoutParams(60.dpToPx(), 42.dpToPx())
                        background = ContextCompat.getDrawable(this@MapaHuertasActivity, R.drawable.bg_bancal)
                        translationX = (x * ancho) - 30.dpToPx()
                        translationY = (y * alto) - 21.dpToPx()
                    }
                }
                binding.mapaContainer.addView(bancal)

                val pinContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )

                    val pinIcon = ImageView(this@MapaHuertasActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(22.dpToPx(), 22.dpToPx())
                        setImageResource(R.drawable.marker_huerta)
                        // PLAN_MEJORAS_20.md, punto 19: decorativo, el label
                        // de texto justo debajo (huerta.nombre) ya lo describe.
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    addView(pinIcon)

                    val label = TextView(this@MapaHuertasActivity).apply {
                        text = huerta.nombre
                        textSize = 9f
                        setTextColor(ContextCompat.getColor(this@MapaHuertasActivity, R.color.simona_on_color))
                        setBackgroundColor(ContextCompat.getColor(this@MapaHuertasActivity, R.color.simona_scrim))
                        setPadding(4.dpToPx(), 1.dpToPx(), 4.dpToPx(), 1.dpToPx())
                    }
                    addView(label)

                    translationX = (x * ancho) - 20.dpToPx()
                    translationY = (y * alto) - 24.dpToPx()

                    setOnClickListener {
                        abrirDashboardHuerta(huerta)
                    }
                    setOnLongClickListener {
                        mostrarOpcionesHuerta(huerta)
                        true
                    }
                }
                binding.mapaContainer.addView(pinContainer)
            }
        }
    }

    private fun mostrarOpcionesHuerta(huerta: Huerta) {
        val opciones = arrayOf(
            getString(R.string.opcion_conectar),
            getString(R.string.opcion_editar),
            getString(R.string.opcion_eliminar)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.opciones_huerta_titulo, huerta.nombre))
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> abrirDashboardHuerta(huerta)
                    1 -> mostrarDialogoEditar(huerta)
                    2 -> mostrarDialogoEliminar(huerta)
                }
            }
            .show()
    }

    private fun mostrarDialogoEliminar(huerta: Huerta) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eliminar_huerta_titulo)
            .setMessage(getString(R.string.eliminar_huerta_mensaje, huerta.nombre))
            .setPositiveButton(R.string.btn_eliminar) { _, _ ->
                repository.eliminar(huerta.id)
                Toast.makeText(this, R.string.huerta_eliminada, Toast.LENGTH_SHORT).show()
                cargarDatos()
            }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun mostrarDialogoEditar(huerta: Huerta) {
        val dialogo = DialogoEditarHuerta(
            activity = this,
            huerta = huerta,
            repository = repository,
            lanzarSelectorFoto = {
                seleccionarFotoEditar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onGuardado = { cargarDatos() },
            onDismiss = { dialogoEditarActual = null }
        )
        dialogoEditarActual = dialogo
        dialogo.mostrar()
    }

    private fun updateThemeIcon(dark: Boolean) {
        binding.btnThemeToggle.setImageResource(if (dark) R.drawable.ic_luna else R.drawable.ic_sol)
    }

    private fun abrirDashboardHuerta(huerta: Huerta) {
        startActivity(TutorialConexionActivity.crearIntent(this, huerta.id))
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
