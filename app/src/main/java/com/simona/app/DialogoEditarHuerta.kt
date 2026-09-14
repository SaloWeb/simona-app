package com.simona.app

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import com.simona.app.databinding.DialogEditarHuertaBinding

/**
 * Diálogo de "Editar huerta" de MapaHuertasActivity, extraído a su propia
 * clase (PLAN_MEJORAS_20.md, punto 7) para que la Activity no cargue con
 * toda esta lógica. El picker de fotos sigue viviendo en la Activity —
 * ActivityResultContracts exige registrarse antes de STARTED, así que no
 * se puede mover acá — por eso esta clase expone
 * actualizarFotoSeleccionada() para que la Activity le pase el resultado
 * cuando llega, y recibe lanzarSelectorFoto()/onDismiss() como callbacks.
 */
class DialogoEditarHuerta(
    private val activity: AppCompatActivity,
    private val huerta: Huerta,
    private val repository: HuertaRepository,
    private val lanzarSelectorFoto: () -> Unit,
    private val onGuardado: () -> Unit,
    private val onDismiss: () -> Unit
) {
    private val binding = DialogEditarHuertaBinding.inflate(LayoutInflater.from(activity))
    private var fotoUriEnEdicion: Uri? = huerta.fotoUri?.let { Uri.parse(it) }
    private val dialog = MaterialAlertDialogBuilder(activity)
        .setView(binding.root)
        .setCancelable(true)
        .create()

    fun mostrar() {
        with(binding) {
            tvEditarTitulo.text = activity.getString(R.string.editar_huerta_titulo, huerta.nombre)
            etEditarNombre.setText(huerta.nombre)
            etEditarPassword.setText(huerta.passwordRed)

            val fotoActual = fotoUriEnEdicion
            if (fotoActual != null) {
                mostrarPreviewFoto(fotoActual)
                btnEditarElegirFoto.setText(R.string.btn_cambiar_foto)
            } else {
                ivEditarFotoPreview.visibility = View.GONE
                btnEditarQuitarFoto.visibility = View.GONE
                btnEditarElegirFoto.setText(R.string.btn_elegir_foto)
            }
            btnEditarElegirFoto.setOnClickListener { lanzarSelectorFoto() }
            btnEditarQuitarFoto.setOnClickListener {
                fotoUriEnEdicion = null
                ivEditarFotoPreview.setImageDrawable(null)
                ivEditarFotoPreview.visibility = View.GONE
                btnEditarQuitarFoto.visibility = View.GONE
                btnEditarElegirFoto.setText(R.string.btn_elegir_foto)
            }

            configurarEditorRango(sliderEditarHumedad, etEditarHumedadMin, etEditarHumedadMax, huerta.humedadMin, huerta.humedadMax, 0, tvEditarHumedadAviso)
            configurarEditorRango(sliderEditarPh, etEditarPhMin, etEditarPhMax, huerta.phMin, huerta.phMax, 1)
            configurarEditorRango(sliderEditarLuz, etEditarLuzMin, etEditarLuzMax, huerta.luzMin.toFloat(), huerta.luzMax.toFloat(), 0)
            configurarEditorRango(sliderEditarTemp, etEditarTempMin, etEditarTempMax, huerta.tempMin, huerta.tempMax, 1)

            btnEditarCancelar.setOnClickListener { dialog.dismiss() }
            btnEditarGuardar.setOnClickListener { guardar() }
        }
        dialog.setOnDismissListener { onDismiss() }
        // PLAN_MEJORAS_UX_20.md, punto 6: antes el ScrollView tenía una
        // altura fija de 360dp, que en pantallas chicas (o con teclado
        // abierto) podía recortar contenido, y en pantallas grandes dejaba
        // espacio vacío de más. Ahora arranca en wrap_content y solo se
        // acota (a ~55% de la altura de pantalla) si el contenido medido
        // realmente no entra — se mide después de mostrarse porque antes
        // de eso binding.scrollEditarContenido.height todavía es 0.
        dialog.setOnShowListener {
            val alturaPantalla = activity.resources.displayMetrics.heightPixels
            val alturaMaxima = (alturaPantalla * 0.55f).toInt()
            val scroll = binding.scrollEditarContenido
            if (scroll.height > alturaMaxima) {
                scroll.layoutParams = scroll.layoutParams.apply { height = alturaMaxima }
                scroll.requestLayout()
            }
        }
        dialog.show()
    }

    /** Llamado por la Activity cuando el picker de fotos (registrado a
     * nivel Activity) devuelve un resultado mientras este diálogo está
     * abierto. */
    fun actualizarFotoSeleccionada(uri: Uri) {
        fotoUriEnEdicion = uri
        mostrarPreviewFoto(uri)
    }

    private fun mostrarPreviewFoto(uri: Uri) {
        val destinoPx = (56 * activity.resources.displayMetrics.density).toInt()
        val bitmap = FotoHuertaUtil.decodificarSampleado(activity, uri, destinoPx, destinoPx)
        if (bitmap == null) {
            fotoUriEnEdicion = null
            return
        }
        FotoHuertaUtil.aplicarConEsquinasRedondeadas(activity, binding.ivEditarFotoPreview, bitmap)
        binding.ivEditarFotoPreview.visibility = View.VISIBLE
        binding.btnEditarQuitarFoto.visibility = View.VISIBLE
    }

    private fun guardar() = with(binding) {
        val nuevoNombre = etEditarNombre.text?.toString()?.trim().orEmpty()
        val nuevoPassword = etEditarPassword.text?.toString().orEmpty()

        var valido = true
        if (nuevoNombre.isEmpty()) {
            tvErrorEditarNombre.visibility = View.VISIBLE
            valido = false
        } else {
            tvErrorEditarNombre.visibility = View.GONE
        }

        if (nuevoPassword.length < 8) {
            tvErrorEditarPassword.visibility = View.VISIBLE
            valido = false
        } else {
            tvErrorEditarPassword.visibility = View.GONE
        }

        val valHumedad = sliderEditarHumedad.values
        if (valHumedad[1] - valHumedad[0] < 10f) {
            tvEditarHumedadAviso.visibility = View.VISIBLE
            valido = false
        }

        if (!valido) return@with

        val valPh = sliderEditarPh.values
        val valLuz = sliderEditarLuz.values
        val valTemp = sliderEditarTemp.values

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
        Toast.makeText(activity, R.string.huerta_actualizada, Toast.LENGTH_SHORT).show()
        dialog.dismiss()
        onGuardado()
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

        // PLAN_MEJORAS_VISUAL_2.md, punto 8: el slider de pH pasó de
        // 0–14 a 4.0–9.0. Una huerta guardada con una versión anterior de
        // la app (slider viejo) podría tener un pH fuera de ese rango
        // nuevo — sin este clamp, slider.values tira
        // IllegalArgumentException al abrir "Editar" sobre esa huerta.
        // No afecta a huertas normales (siempre dentro del rango nuevo).
        val valorMinAcotado = valorMin.coerceIn(slider.valueFrom, slider.valueTo)
        val valorMaxAcotado = valorMax.coerceIn(slider.valueFrom, slider.valueTo)

        slider.values = listOf(valorMinAcotado, valorMaxAcotado)
        etMin.setText(formatearValor(valorMinAcotado, decimales))
        etMax.setText(formatearValor(valorMaxAcotado, decimales))

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
}
