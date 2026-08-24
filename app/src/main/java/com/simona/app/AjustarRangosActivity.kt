package com.simona.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.RangeSlider
import com.simona.app.databinding.ActivityAjustarRangosBinding

/**
 * Paso 2 del flujo de creación de huerta (Plan de Desarrollo, sección 14.1 y
 * 14.3). Recibe el perfil elegido en D.1 (SeleccionarPerfilActivity) y deja
 * que el usuario confirme los valores recomendados o los ajuste a mano,
 * tanto por arrastre (RangeSlider) como por texto (EditText), antes de
 * pasar al paso 3 (DatosHuertaActivity, D.3: nombre, contraseña y
 * ubicación de la huerta).
 */
class AjustarRangosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAjustarRangosBinding
    private lateinit var perfil: PerfilCultivo

    // Evita que el listener del EditText dispare una actualización del
    // slider mientras el propio slider está actualizando ese EditText (y
    // viceversa) — sin esto, cada arrastre generaría un loop de escrituras.
    private var sincronizando = false

    private lateinit var rangoHumedad: RangoUI
    private lateinit var rangoPh: RangoUI
    private lateinit var rangoLuz: RangoUI
    private lateinit var rangoTemp: RangoUI

    /** Agrupa las tres vistas de un rango (sección 14.3) para no repetir lógica. */
    private data class RangoUI(
        val slider: RangeSlider,
        val etMin: EditText,
        val etMax: EditText,
        val decimales: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAjustarRangosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val perfilId = intent.getStringExtra(EXTRA_PERFIL_ID)
        perfil = PerfilesCultivo.porId(perfilId ?: "") ?: PerfilesCultivo.porId("personalizado")!!

        binding.ivPerfilIcono.setImageResource(perfil.iconoRes)
        binding.tvPerfilNombre.text = perfil.nombre

        rangoHumedad = RangoUI(binding.sliderHumedad, binding.etHumedadMin, binding.etHumedadMax, decimales = 0)
        rangoPh = RangoUI(binding.sliderPh, binding.etPhMin, binding.etPhMax, decimales = 1)
        rangoLuz = RangoUI(binding.sliderLuz, binding.etLuzMin, binding.etLuzMax, decimales = 0)
        rangoTemp = RangoUI(binding.sliderTemp, binding.etTempMin, binding.etTempMax, decimales = 1)

        configurarRango(rangoHumedad, perfil.humedadMin, perfil.humedadMax)
        configurarRango(rangoPh, perfil.phMin, perfil.phMax)
        configurarRango(rangoLuz, perfil.luzMin.toFloat(), perfil.luzMax.toFloat())
        configurarRango(rangoTemp, perfil.tempMin, perfil.tempMax)

        // Validación de ancho mínimo (sección 14.3): solo aplica a humedad,
        // porque es la única variable con actuador físico (el riego real).
        rangoHumedad.slider.addOnChangeListener { slider, _, fromUser ->
            if (fromUser) aplicarAnchoMinimoHumedad(slider)
        }

        binding.btnUsarRecomendados.setOnClickListener { confirmarRangos() }
        binding.btnContinuar.setOnClickListener { confirmarRangos() }
        binding.linkAjustarManualmente.setOnClickListener { mostrarSlidersManualmente() }
    }

    /**
     * Conecta un RangeSlider con sus dos EditText en ambos sentidos:
     * arrastrar el slider actualiza el texto, y escribir en el texto
     * actualiza el slider — cumple el requisito de "editables por texto y
     * por arrastre" de la sección 14.3.
     */
    private fun configurarRango(rango: RangoUI, valorMin: Float, valorMax: Float) {
        rango.slider.values = listOf(valorMin, valorMax)
        actualizarTextos(rango)

        rango.slider.addOnChangeListener { slider, _, fromUser ->
            if (fromUser && !sincronizando) actualizarTextos(rango, desdeSlider = slider)
        }

        rango.etMin.addTextChangedListener(crearTextWatcher(rango, esMinimo = true))
        rango.etMax.addTextChangedListener(crearTextWatcher(rango, esMinimo = false))
    }

    private fun crearTextWatcher(rango: RangoUI, esMinimo: Boolean): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (sincronizando) return
                val texto = s?.toString()?.replace(',', '.') ?: return
                val valor = texto.toFloatOrNull() ?: return

                val actuales = rango.slider.values
                val nuevoMin = if (esMinimo) valor else actuales[0]
                val nuevoMax = if (esMinimo) actuales[1] else valor

                val minLimite = rango.slider.valueFrom
                val maxLimite = rango.slider.valueTo
                if (nuevoMin < minLimite || nuevoMax > maxLimite || nuevoMin >= nuevoMax) return

                sincronizando = true
                rango.slider.values = listOf(nuevoMin, nuevoMax)
                sincronizando = false

                if (rango === rangoHumedad) aplicarAnchoMinimoHumedad(rango.slider)
            }
        }

    private fun actualizarTextos(rango: RangoUI, desdeSlider: RangeSlider? = null) {
        val valores = (desdeSlider ?: rango.slider).values
        sincronizando = true
        rango.etMin.setText(formatearValor(valores[0], rango.decimales))
        rango.etMax.setText(formatearValor(valores[1], rango.decimales))
        sincronizando = false
    }

    private fun formatearValor(v: Float, decimales: Int): String =
        if (decimales == 0) v.toInt().toString() else String.format("%.${decimales}f", v)

    /**
     * Ancho mínimo de 10 puntos porcentuales entre umbral bajo y alto de
     * humedad (sección 14.3). Si el usuario lo achica de más, se corrige
     * automáticamente empujando el máximo hacia arriba (o el mínimo hacia
     * abajo, si ya está en el tope) y se muestra el aviso.
     */
    private fun aplicarAnchoMinimoHumedad(slider: RangeSlider) {
        val valores = slider.values
        val min = valores[0]
        val max = valores[1]
        val ancho = max - min

        if (ancho >= ANCHO_MINIMO_HUMEDAD) {
            binding.tvHumedadAviso.visibility = View.GONE
            return
        }

        binding.tvHumedadAviso.visibility = View.VISIBLE
        val nuevoMax = (min + ANCHO_MINIMO_HUMEDAD).coerceAtMost(slider.valueTo)
        val nuevoMin = if (nuevoMax - min < ANCHO_MINIMO_HUMEDAD) {
            (nuevoMax - ANCHO_MINIMO_HUMEDAD).coerceAtLeast(slider.valueFrom)
        } else {
            min
        }

        sincronizando = true
        slider.values = listOf(nuevoMin, nuevoMax)
        sincronizando = false
        actualizarTextos(rangoHumedad, desdeSlider = slider)
    }

    private fun mostrarSlidersManualmente() {
        binding.grupoManual.visibility = View.VISIBLE
        binding.linkAjustarManualmente.visibility = View.GONE
        binding.btnUsarRecomendados.visibility = View.GONE
        binding.btnContinuar.visibility = View.VISIBLE
    }

    private fun confirmarRangos() {
        val humedad = rangoHumedad.slider.values
        val ph = rangoPh.slider.values
        val luz = rangoLuz.slider.values
        val temp = rangoTemp.slider.values

        if (humedad[1] - humedad[0] < ANCHO_MINIMO_HUMEDAD) {
            Toast.makeText(this, R.string.aviso_ancho_minimo_humedad, Toast.LENGTH_SHORT).show()
            return
        }

        val datosParaSiguientePaso = Intent().apply {
            putExtra(EXTRA_PERFIL_ID, perfil.id)
            putExtra(EXTRA_HUMEDAD_MIN, humedad[0])
            putExtra(EXTRA_HUMEDAD_MAX, humedad[1])
            putExtra(EXTRA_PH_MIN, ph[0])
            putExtra(EXTRA_PH_MAX, ph[1])
            putExtra(EXTRA_LUZ_MIN, luz[0])
            putExtra(EXTRA_LUZ_MAX, luz[1])
            putExtra(EXTRA_TEMP_MIN, temp[0])
            putExtra(EXTRA_TEMP_MAX, temp[1])
        }

        // Paso 3 (D.3): nombre, contraseña de red y ubicación opcional.
        startActivity(DatosHuertaActivity.crearIntent(this, datosParaSiguientePaso))
        finish()
    }

    companion object {
        const val EXTRA_PERFIL_ID = "extra_perfil_id"
        const val EXTRA_HUMEDAD_MIN = "extra_humedad_min"
        const val EXTRA_HUMEDAD_MAX = "extra_humedad_max"
        const val EXTRA_PH_MIN = "extra_ph_min"
        const val EXTRA_PH_MAX = "extra_ph_max"
        const val EXTRA_LUZ_MIN = "extra_luz_min"
        const val EXTRA_LUZ_MAX = "extra_luz_max"
        const val EXTRA_TEMP_MIN = "extra_temp_min"
        const val EXTRA_TEMP_MAX = "extra_temp_max"

        private const val ANCHO_MINIMO_HUMEDAD = 10f

        fun crearIntent(context: Context, perfilId: String): Intent =
            Intent(context, AjustarRangosActivity::class.java)
                .putExtra(EXTRA_PERFIL_ID, perfilId)
    }
}