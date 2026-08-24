
package com.simona.app

import android.app.AlertDialog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.RangeSlider

/**
 * Home nativo de la app SIMONA: funciona 100% offline (sin depender del ESP32).
 * Muestra el resumen de huertas, lista en tarjetas ricas, croquis general del
 * predio con canteros, gestión de huertas (editar/eliminar) y acceso al asistente
 * de IA Simona usando la conexión a internet normal del teléfono.
 *
 * Al seleccionar una huerta, deriva a TutorialConexionActivity para conectar
 * el WiFi al ESP32 y abrir el Dashboard en tiempo real.
 */
class MapaHuertasActivity : AppCompatActivity() {

    private lateinit var repository: HuertaRepository
    private lateinit var mapaContainer: FrameLayout
    private lateinit var listaHeatmapsContainer: LinearLayout
    private lateinit var estadoVacio: LinearLayout
    private lateinit var seccionLista: LinearLayout
    private lateinit var seccionMapa: LinearLayout
    private lateinit var resumenChipsContainer: LinearLayout
    private lateinit var tabsContainer: LinearLayout
    private lateinit var tabBtnLista: MaterialButton
    private lateinit var tabBtnMapa: MaterialButton
    private lateinit var tvChipTotal: TextView
    private lateinit var tvChipConSed: TextView
    private lateinit var tvChipOptimas: TextView
    private lateinit var tvContadorHuertas: TextView
    private lateinit var fabAiSimona: FloatingActionButton

    private var vistaActual = "lista" // "lista" o "mapa"

    // Fase 6.4 — foto opcional de la huerta que se está editando en ese
    // momento (dialog_editar_huerta). Como el diálogo se infla de nuevo
    // por cada huerta, el picker vive a nivel Activity (tiene que
    // registrarse antes de STARTED) y estas variables apuntan al diálogo
    // actualmente abierto para saber dónde volcar el resultado.
    private var fotoUriEnEdicion: Uri? = null
    private var ivFotoEnEdicion: ImageView? = null
    private var btnQuitarFotoEnEdicion: View? = null

    private val seleccionarFotoEditar = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            FotoHuertaUtil.persistirPermisoLectura(contentResolver, uri)
            fotoUriEnEdicion = uri
            mostrarPreviewFotoEditar(uri)
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
        setContentView(R.layout.activity_mapa_huertas)

        repository = (application as SimonaApp).huertaRepository

        mapaContainer = findViewById(R.id.mapaContainer)
        listaHeatmapsContainer = findViewById(R.id.listaHeatmapsContainer)
        estadoVacio = findViewById(R.id.estadoVacio)
        seccionLista = findViewById(R.id.seccionLista)
        seccionMapa = findViewById(R.id.seccionMapa)
        resumenChipsContainer = findViewById(R.id.resumenChipsContainer)
        tabsContainer = findViewById(R.id.tabsContainer)
        tabBtnLista = findViewById(R.id.tabBtnLista)
        tabBtnMapa = findViewById(R.id.tabBtnMapa)
        tvChipTotal = findViewById(R.id.tvChipTotal)
        tvChipConSed = findViewById(R.id.tvChipConSed)
        tvChipOptimas = findViewById(R.id.tvChipOptimas)
        tvContadorHuertas = findViewById(R.id.tvContadorHuertas)
        fabAiSimona = findViewById(R.id.fabAiSimona)

        findViewById<View>(R.id.btnNuevaHuerta).setOnClickListener {
            startActivity(SeleccionarPerfilActivity.crearIntent(this))
        }
        findViewById<View>(R.id.btnNuevaHuertaVacio).setOnClickListener {
            startActivity(SeleccionarPerfilActivity.crearIntent(this))
        }

        tabBtnLista.setOnClickListener { cambiarPestana("lista") }
        tabBtnMapa.setOnClickListener { cambiarPestana("mapa") }

        fabAiSimona.setOnClickListener { abrirChatAi() }

        pedirPermisoNotificacionesSiHaceFalta()

        cargarDatos()
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
        seccionLista.visibility = if (esLista) View.VISIBLE else View.GONE
        seccionMapa.visibility = if (esLista) View.GONE else View.VISIBLE

        tabBtnLista.backgroundTintList = ContextCompat.getColorStateList(
            this, if (esLista) R.color.simona_azul_suave else R.color.simona_card_bg
        )
        tabBtnLista.setTextColor(
            ContextCompat.getColor(this, if (esLista) R.color.simona_azul else R.color.simona_tinta_suave)
        )

        tabBtnMapa.backgroundTintList = ContextCompat.getColorStateList(
            this, if (!esLista) R.color.simona_azul_suave else R.color.simona_card_bg
        )
        tabBtnMapa.setTextColor(
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

        estadoVacio.visibility = if (hayHuertas) View.GONE else View.VISIBLE
        resumenChipsContainer.visibility = if (hayHuertas) View.VISIBLE else View.GONE
        tabsContainer.visibility = if (hayHuertas) View.VISIBLE else View.GONE
        seccionLista.visibility = if (hayHuertas && vistaActual == "lista") View.VISIBLE else View.GONE
        seccionMapa.visibility = if (hayHuertas && vistaActual == "mapa") View.VISIBLE else View.GONE

        if (!hayHuertas) return

        val total = huertas.size
        val conSed = huertas.count { h ->
            val l = h.ultimaLectura
            l != null && l.humedad <= h.humedadMin
        }
        val optimas = total - conSed

        tvChipTotal.text = total.toString()
        tvChipConSed.text = conSed.toString()
        tvChipOptimas.text = optimas.toString()
        tvContadorHuertas.text = if (total == 1) {
            getString(R.string.home_contador_una)
        } else {
            getString(R.string.home_contador_varias, total)
        }

        renderizarTarjetas(huertas)
        renderizarPines(huertas)
    }

    private fun renderizarTarjetas(huertas: List<Huerta>) {
        listaHeatmapsContainer.removeAllViews()

        val colorTinta = ContextCompat.getColor(this, R.color.simona_tinta)
        val colorSuave = ContextCompat.getColor(this, R.color.simona_tinta_suave)
        val colorAzul = ContextCompat.getColor(this, R.color.simona_azul)
        val colorRojo = ContextCompat.getColor(this, R.color.simona_rojo)
        val colorVerde = ContextCompat.getColor(this, R.color.simona_verde)
        val colorMarron = ContextCompat.getColor(this, R.color.simona_marron)

        huertas.forEach { huerta ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@MapaHuertasActivity, R.drawable.bg_card_huerta)
                setPadding(16.dpToPx(), 14.dpToPx(), 16.dpToPx(), 14.dpToPx())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 10.dpToPx()
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener { abrirDashboardHuerta(huerta) }
                setOnLongClickListener {
                    mostrarOpcionesHuerta(huerta)
                    true
                }
            }

            val filaTop = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val colInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            colInfo.addView(TextView(this).apply {
                text = huerta.nombre
                textSize = 16f
                setTextColor(colorTinta)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            colInfo.addView(TextView(this).apply {
                text = getString(
                    R.string.home_rango_ideal,
                    huerta.categoria,
                    huerta.humedadMin.toInt(),
                    huerta.humedadMax.toInt()
                )
                textSize = 12f
                setTextColor(colorSuave)
                setPadding(0, 3.dpToPx(), 0, 0)
            })
            filaTop.addView(colInfo)

            filaTop.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_more_vert)
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx())
                setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                imageTintList = ContextCompat.getColorStateList(this@MapaHuertasActivity, R.color.simona_tinta_suave)
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.opciones_huerta_cd, huerta.nombre)
                setOnClickListener { mostrarOpcionesHuerta(huerta) }
            })

            filaTop.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron_right)
                layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx())
                imageTintList = ContextCompat.getColorStateList(this@MapaHuertasActivity, R.color.simona_azul)
            })

            card.addView(filaTop)

            val l = huerta.ultimaLectura
            val filaEstado = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10.dpToPx(), 0, 0)
            }

            if (l != null) {
                val esSeco = l.humedad <= huerta.humedadMin
                val esHumedo = l.humedad >= huerta.humedadMax
                val colorEstado = when {
                    esSeco -> colorRojo
                    esHumedo -> colorAzul
                    else -> colorVerde
                }
                val textoEstado = getString(
                    when {
                        esSeco -> R.string.home_estado_seco
                        esHumedo -> R.string.home_estado_humedo
                        else -> R.string.home_estado_optimo
                    }
                )

                filaEstado.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_gota_full)
                    layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 16.dpToPx()).apply {
                        marginEnd = 6.dpToPx()
                    }
                    imageTintList = android.content.res.ColorStateList.valueOf(colorEstado)
                })

                filaEstado.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = getString(R.string.home_humedad_estado, l.humedad.toInt(), textoEstado)
                    textSize = 12f
                    setTextColor(colorEstado)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        startActivity(DetalleHuertaActivity.crearIntent(this@MapaHuertasActivity, huerta.id))
                    }
                })
            } else {
                filaEstado.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_brote)
                    layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 16.dpToPx()).apply {
                        marginEnd = 6.dpToPx()
                    }
                })
                filaEstado.addView(TextView(this).apply {
                    text = getString(R.string.home_sin_lecturas)
                    textSize = 12f
                    setTextColor(colorMarron)
                })
            }
            card.addView(filaEstado)

            listaHeatmapsContainer.addView(card)
        }
    }

    private fun renderizarPines(huertas: List<Huerta>) {
        mapaContainer.removeAllViews()

        mapaContainer.post {
            val ancho = mapaContainer.width.toFloat()
            val alto = mapaContainer.height.toFloat()
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
                        }
                        FotoHuertaUtil.aplicarConEsquinasRedondeadas(this@MapaHuertasActivity, foto, bitmapFoto)
                        addView(foto)

                        val borde = View(this@MapaHuertasActivity).apply {
                            layoutParams = FrameLayout.LayoutParams(60.dpToPx(), 42.dpToPx())
                            background = androidx.core.content.ContextCompat.getDrawable(this@MapaHuertasActivity, R.drawable.bg_bancal)
                        }
                        addView(borde)
                    }
                } else {
                    View(this).apply {
                        layoutParams = FrameLayout.LayoutParams(60.dpToPx(), 42.dpToPx())
                        background = androidx.core.content.ContextCompat.getDrawable(this@MapaHuertasActivity, R.drawable.bg_bancal)
                        translationX = (x * ancho) - 30.dpToPx()
                        translationY = (y * alto) - 21.dpToPx()
                    }
                }
                mapaContainer.addView(bancal)

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
                mapaContainer.addView(pinContainer)
            }
        }
    }

    private fun mostrarOpcionesHuerta(huerta: Huerta) {
        val opciones = arrayOf(
            getString(R.string.opcion_conectar),
            getString(R.string.opcion_editar),
            getString(R.string.opcion_eliminar)
        )

        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_editar_huerta, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvEditarTitulo)
        val etNombre = dialogView.findViewById<EditText>(R.id.etEditarNombre)
        val tvErrorNombre = dialogView.findViewById<TextView>(R.id.tvErrorEditarNombre)
        val etPassword = dialogView.findViewById<EditText>(R.id.etEditarPassword)
        val tvErrorPassword = dialogView.findViewById<TextView>(R.id.tvErrorEditarPassword)

        val ivFotoPreview = dialogView.findViewById<ImageView>(R.id.ivEditarFotoPreview)
        val btnElegirFoto = dialogView.findViewById<MaterialButton>(R.id.btnEditarElegirFoto)
        val btnQuitarFoto = dialogView.findViewById<MaterialButton>(R.id.btnEditarQuitarFoto)

        val etHumedadMin = dialogView.findViewById<EditText>(R.id.etEditarHumedadMin)
        val etHumedadMax = dialogView.findViewById<EditText>(R.id.etEditarHumedadMax)
        val sliderHumedad = dialogView.findViewById<RangeSlider>(R.id.sliderEditarHumedad)
        val tvHumedadAviso = dialogView.findViewById<TextView>(R.id.tvEditarHumedadAviso)

        val etPhMin = dialogView.findViewById<EditText>(R.id.etEditarPhMin)
        val etPhMax = dialogView.findViewById<EditText>(R.id.etEditarPhMax)
        val sliderPh = dialogView.findViewById<RangeSlider>(R.id.sliderEditarPh)

        val etLuzMin = dialogView.findViewById<EditText>(R.id.etEditarLuzMin)
        val etLuzMax = dialogView.findViewById<EditText>(R.id.etEditarLuzMax)
        val sliderLuz = dialogView.findViewById<RangeSlider>(R.id.sliderEditarLuz)

        val etTempMin = dialogView.findViewById<EditText>(R.id.etEditarTempMin)
        val etTempMax = dialogView.findViewById<EditText>(R.id.etEditarTempMax)
        val sliderTemp = dialogView.findViewById<RangeSlider>(R.id.sliderEditarTemp)

        val btnCancelar = dialogView.findViewById<Button>(R.id.btnEditarCancelar)
        val btnGuardar = dialogView.findViewById<Button>(R.id.btnEditarGuardar)

        tvTitulo.text = getString(R.string.editar_huerta_titulo, huerta.nombre)
        etNombre.setText(huerta.nombre)
        etPassword.setText(huerta.passwordRed)

        // Fase 6.4: precargar la foto ya guardada (si tiene) y dejar que
        // el picker registrado a nivel Activity sepa a qué ImageView/botón
        // de ESTE diálogo volcar el resultado.
        fotoUriEnEdicion = huerta.fotoUri?.let { Uri.parse(it) }
        ivFotoEnEdicion = ivFotoPreview
        btnQuitarFotoEnEdicion = btnQuitarFoto
        if (fotoUriEnEdicion != null) {
            mostrarPreviewFotoEditar(fotoUriEnEdicion!!)
            btnElegirFoto.setText(R.string.btn_cambiar_foto)
        } else {
            ivFotoPreview.visibility = View.GONE
            btnQuitarFoto.visibility = View.GONE
            btnElegirFoto.setText(R.string.btn_elegir_foto)
        }
        btnElegirFoto.setOnClickListener {
            seleccionarFotoEditar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnQuitarFoto.setOnClickListener {
            fotoUriEnEdicion = null
            ivFotoPreview.setImageDrawable(null)
            ivFotoPreview.visibility = View.GONE
            btnQuitarFoto.visibility = View.GONE
            btnElegirFoto.setText(R.string.btn_elegir_foto)
        }

        configurarEditorRango(sliderHumedad, etHumedadMin, etHumedadMax, huerta.humedadMin, huerta.humedadMax, 0, tvHumedadAviso)
        configurarEditorRango(sliderPh, etPhMin, etPhMax, huerta.phMin, huerta.phMax, 1)
        configurarEditorRango(sliderLuz, etLuzMin, etLuzMax, huerta.luzMin.toFloat(), huerta.luzMax.toFloat(), 0)
        configurarEditorRango(sliderTemp, etTempMin, etTempMax, huerta.tempMin, huerta.tempMax, 1)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val nuevoNombre = etNombre.text?.toString()?.trim().orEmpty()
            val nuevoPassword = etPassword.text?.toString().orEmpty()

            var valido = true
            if (nuevoNombre.isEmpty()) {
                tvErrorNombre.visibility = View.VISIBLE
                valido = false
            } else {
                tvErrorNombre.visibility = View.GONE
            }

            if (nuevoPassword.length < 8) {
                tvErrorPassword.visibility = View.VISIBLE
                valido = false
            } else {
                tvErrorPassword.visibility = View.GONE
            }

            val valHumedad = sliderHumedad.values
            if (valHumedad[1] - valHumedad[0] < 10f) {
                tvHumedadAviso.visibility = View.VISIBLE
                valido = false
            }

            if (!valido) return@setOnClickListener

            val valPh = sliderPh.values
            val valLuz = sliderLuz.values
            val valTemp = sliderTemp.values

            val huertaActualizada = huerta.copy(
                nombre = nuevoNombre,
                passwordRed = nuevoPassword,
                humedadMin = valHumedad[0],
                humedadMax = valHumedad[1],
                phMin = valPh[0],
                phMax = valPh[1],
                luzMin = valLuz[0].toInt(),
                luzMax = valLuz[1].toInt(),
                tempMin = valTemp[0],
                tempMax = valTemp[1],
                fotoUri = fotoUriEnEdicion?.toString()
            )

            repository.guardar(huertaActualizada)
            Toast.makeText(this, R.string.huerta_actualizada, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            cargarDatos()
        }

        dialog.setOnDismissListener { limpiarReferenciasFotoEdicion() }
        dialog.show()
    }

    private fun limpiarReferenciasFotoEdicion() {
        ivFotoEnEdicion = null
        btnQuitarFotoEnEdicion = null
    }

    private fun mostrarPreviewFotoEditar(uri: Uri) {
        val iv = ivFotoEnEdicion ?: return
        val destinoPx = 56.dpToPx()
        val bitmap = FotoHuertaUtil.decodificarSampleado(this, uri, destinoPx, destinoPx)
        if (bitmap == null) {
            fotoUriEnEdicion = null
            return
        }
        FotoHuertaUtil.aplicarConEsquinasRedondeadas(this, iv, bitmap)
        iv.visibility = View.VISIBLE
        btnQuitarFotoEnEdicion?.visibility = View.VISIBLE
    }

    private fun configurarEditorRango(
        slider: RangeSlider,
        etMin: EditText,
        etMax: EditText,
        valorMin: Float,
        valorMax: Float,
        decimales: Int,
        tvAvisoHumedad: TextView? = null
    ) {
        var sinc = false

        slider.values = listOf(valorMin, valorMax)
        etMin.setText(formatearValor(valorMin, decimales))
        etMax.setText(formatearValor(valorMax, decimales))

        slider.addOnChangeListener { s, _, fromUser ->
            if (fromUser && !sinc) {
                if (tvAvisoHumedad != null) {
                    val vals = s.values
                    val ancho = vals[1] - vals[0]
                    if (ancho < 10f) {
                        tvAvisoHumedad.visibility = View.VISIBLE
                        val nuevoMax = (vals[0] + 10f).coerceAtMost(s.valueTo)
                        val nuevoMin = if (nuevoMax - vals[0] < 10f) {
                            (nuevoMax - 10f).coerceAtLeast(s.valueFrom)
                        } else {
                            vals[0]
                        }
                        sinc = true
                        s.values = listOf(nuevoMin, nuevoMax)
                        sinc = false
                    } else {
                        tvAvisoHumedad.visibility = View.GONE
                    }
                }
                sinc = true
                etMin.setText(formatearValor(s.values[0], decimales))
                etMax.setText(formatearValor(s.values[1], decimales))
                sinc = false
            }
        }

        fun crearWatcher(esMin: Boolean): TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (sinc) return
                val txt = s?.toString()?.replace(',', '.') ?: return
                val v = txt.toFloatOrNull() ?: return
                val actuales = slider.values
                val nMin = if (esMin) v else actuales[0]
                val nMax = if (esMin) actuales[1] else v
                if (nMin < slider.valueFrom || nMax > slider.valueTo || nMin >= nMax) return

                sinc = true
                slider.values = listOf(nMin, nMax)
                sinc = false

                if (tvAvisoHumedad != null) {
                    tvAvisoHumedad.visibility = if (nMax - nMin < 10f) View.VISIBLE else View.GONE
                }
            }
        }

        etMin.addTextChangedListener(crearWatcher(true))
        etMax.addTextChangedListener(crearWatcher(false))
    }

    private fun formatearValor(v: Float, decimales: Int): String =
        if (decimales == 0) v.toInt().toString() else String.format(java.util.Locale.US, "%.${decimales}f", v)

    private fun abrirChatAi() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_ai, null)
        val tvHistorial = dialogView.findViewById<TextView>(R.id.tvHistorialChat)
        val etPregunta = dialogView.findViewById<EditText>(R.id.etPreguntaChat)
        val btnEnviar = dialogView.findViewById<Button>(R.id.btnEnviarChat)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBarChat)
        val scrollChat = dialogView.findViewById<ScrollView>(R.id.scrollChat)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val huertas = repository.listar()
        val contexto = if (huertas.isNotEmpty()) {
            buildString {
                append("El usuario está en el menú principal de SIMONA. Tiene ${huertas.size} huertas registradas: ")
                huertas.forEachIndexed { i, h ->
                    append("${i + 1}) ${h.nombre} (Cultivo: ${h.categoria}, humedad óptima: ${h.humedadMin.toInt()}%-${h.humedadMax.toInt()}%). ")
                    val l = h.ultimaLectura
                    if (l != null) {
                        append("Última lectura: humedad ${l.humedad}%, temp ${l.temperatura}°C, riego ${if (l.riegoActivo) "activo" else "inactivo"}. ")
                    }
                }
            }
        } else {
            "El usuario está en el menú principal de SIMONA. Aún no tiene huertas registradas."
        }

        btnEnviar.setOnClickListener {
            val pregunta = etPregunta.text.toString().trim()
            if (pregunta.isEmpty()) return@setOnClickListener

            val textoAnterior = tvHistorial.text.toString()
            val etiquetaTu = getString(R.string.asistente_tu)
            val etiquetaSimona = getString(R.string.asistente_simona)
            tvHistorial.text = "$textoAnterior\n\n$etiquetaTu: $pregunta\n\n${getString(R.string.asistente_pensando)}"
            etPregunta.setText("")
            progressBar.visibility = View.VISIBLE
            btnEnviar.isEnabled = false
            scrollChat.post { scrollChat.fullScroll(View.FOCUS_DOWN) }

            AsistenteGemini(this).preguntar(
                pregunta = pregunta,
                contextoHuerta = contexto,
                onExito = { respuesta ->
                    progressBar.visibility = View.GONE
                    btnEnviar.isEnabled = true
                    tvHistorial.text = "$textoAnterior\n\n$etiquetaTu: $pregunta\n\n$etiquetaSimona: $respuesta"
                    scrollChat.post { scrollChat.fullScroll(View.FOCUS_DOWN) }
                },
                onError = { error ->
                    progressBar.visibility = View.GONE
                    btnEnviar.isEnabled = true
                    tvHistorial.text = "$textoAnterior\n\n$etiquetaTu: $pregunta\n\n${getString(R.string.asistente_error_prefijo)}: $error"
                    scrollChat.post { scrollChat.fullScroll(View.FOCUS_DOWN) }
                }
            )
        }

        dialog.show()
    }

    private fun abrirDashboardHuerta(huerta: Huerta) {
        startActivity(TutorialConexionActivity.crearIntent(this, huerta.id))
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
