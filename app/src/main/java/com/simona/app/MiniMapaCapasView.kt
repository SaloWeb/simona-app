package com.simona.app

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Una "capa" del mini-mapa multicapa (Fase 5.4): una métrica de la huerta
 * (Humedad, Temperatura, pH o Luz) con su valor actual, unidad, el rango
 * configurado por el usuario (min/max — los mismos 8 rangos de
 * AjustarRangosActivity/PerfilCultivo) y el eje visual del mini-mapa
 * (ejeMin/ejeMax), que suele ser más ancho que el rango para dejar margen
 * a los lados de la franja.
 */
data class CapaMetrica(
    val id: String,
    val etiqueta: String,
    val icono: Int,
    val valorActual: Float,
    val unidad: String,
    val min: Float,
    val max: Float,
    val ejeMin: Float,
    val ejeMax: Float,
    val decimales: Int = 1
) {
    /** Réplica de estadoHumedad() del simulador, generalizada a las 4 capas:
     * mismas 3 franjas/colores (bajo=naranja, óptimo=verde, alto=azul),
     * calculadas sobre el rango configurado (min/max) de esta capa. */
    fun estado(): EstadoCapa = when {
        valorActual <= min -> EstadoCapa.BAJO
        valorActual >= max -> EstadoCapa.ALTO
        else -> EstadoCapa.OPTIMO
    }

    fun valorFormateado(): String {
        val numero = if (decimales == 0) {
            valorActual.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.${decimales}f", valorActual)
        }
        return if (unidad.isEmpty()) numero else "$numero $unidad"
    }
}

enum class EstadoCapa(val colorRes: Int, val pinDrawableRes: Int, val etiqueta: String) {
    BAJO(R.color.simona_estado_seco, R.drawable.bg_pin_seco, "Bajo"),
    OPTIMO(R.color.simona_estado_optimo, R.drawable.bg_pin_optimo, "Óptimo"),
    ALTO(R.color.simona_estado_humedo, R.drawable.bg_pin_humedo, "Alto")
}

/**
 * Mini-mapa multicapa (Fase 5.4 del plan de unificación visual): versión
 * nativa del panel de métricas del simulador, generalizada a Humedad/pH/
 * Temp/Luz con el mismo criterio de rango configurable que usa el gauge de
 * humedad del HTML (dibujarBandas), pero SIN el gauge circular con aguja
 * (eso es la Fase 5.5, marcada como alto esfuerzo/opcional en el plan).
 *
 * En vez del círculo con aguja, reutiliza el lenguaje visual de "mapa" que
 * ya tiene la app: un pin con halo de color posicionado por porcentaje
 * (mismo patrón que MapaHuertasActivity.renderizarPines(), con post{} para
 * esperar el layout antes de calcular translationX) sobre una franja que
 * representa el rango de la métrica, con badge de valor en vivo y una
 * leyenda de 3 franjas debajo. Chips arriba permiten elegir qué capa mirar.
 *
 * Se reconstruye por completo en cada setCapas() — mismo criterio que
 * renderizarPines()/renderizarTarjetas() en MapaHuertasActivity: la app ya
 * asume refrescos completos cada pocos segundos (polling cada 3s), no hace
 * diffing de vistas.
 */
class MiniMapaCapasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var capas: List<CapaMetrica> = emptyList()
    private var capaSeleccionadaId: String? = null
    private var onCapaSeleccionada: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
    }

    /**
     * Reemplaza el set de capas y redibuja todo. Si capaSeleccionadaId ya
     * apunta a una capa que sigue existiendo en la lista nueva, se conserva
     * la selección — así el polling de valores (cada 3s) no le pisa al
     * usuario la capa que está mirando.
     */
    fun setCapas(nuevasCapas: List<CapaMetrica>, listener: ((String) -> Unit)? = null) {
        capas = nuevasCapas
        if (listener != null) onCapaSeleccionada = listener
        if (capaSeleccionadaId == null || capas.none { it.id == capaSeleccionadaId }) {
            capaSeleccionadaId = capas.firstOrNull()?.id
        }
        redibujar()
    }

    fun seleccionarCapa(id: String) {
        if (capas.none { it.id == id }) return
        capaSeleccionadaId = id
        redibujar()
        onCapaSeleccionada?.invoke(id)
    }

    private fun redibujar() {
        removeAllViews()
        val capa = capas.firstOrNull { it.id == capaSeleccionadaId } ?: return

        addView(
            construirChips(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        addView(
            construirFranjaRango(capa),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 64.dp).apply {
                topMargin = 20.dp // deja lugar arriba para el badge + halo del pin
            }
        )
        addView(
            construirLeyenda(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp
            }
        )
    }

    // ---------- Chips de capa ----------

    private fun construirChips(): LinearLayout {
        val fila = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        capas.forEachIndexed { index, capa ->
            val seleccionada = capa.id == capaSeleccionadaId
            val chip = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(12.dp, 6.dp, 12.dp, 6.dp)
                background = ContextCompat.getDrawable(
                    context,
                    if (seleccionada) R.drawable.bg_chip_capa_seleccionado else R.drawable.bg_chip_capa
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { seleccionarCapa(capa.id) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = 6.dp
                }
            }
            val texto = TextView(context).apply {
                text = capa.etiqueta
                textSize = 12f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (seleccionada) R.color.simona_on_color else R.color.simona_tinta
                    )
                )
            }
            chip.addView(texto)
            fila.addView(chip)
        }
        return fila
    }

    // ---------- Franja de rango con pin ----------

    private fun construirFranjaRango(capa: CapaMetrica): FrameLayout {
        val franja = FrameLayout(context)

        val bandas = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        val (pesoBajo, pesoOptimo, pesoAlto) = pesosBandas(capa)
        bandas.addView(bandaColor(R.color.simona_estado_seco, pesoBajo))
        bandas.addView(bandaColor(R.color.simona_estado_optimo, pesoOptimo))
        bandas.addView(bandaColor(R.color.simona_estado_humedo, pesoAlto))
        franja.addView(
            bandas,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 8.dp).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )

        franja.post {
            val ancho = franja.width.toFloat()
            if (ancho <= 0f) return@post
            val estado = capa.estado()
            val porcentaje = ((capa.valorActual - capa.ejeMin) / (capa.ejeMax - capa.ejeMin))
                .coerceIn(0f, 1f)

            val pin = construirPin(capa, estado)
            franja.addView(pin)
            pin.post {
                pin.translationX = (porcentaje * ancho) - (pin.width / 2f)
                pin.translationY = -(pin.height.toFloat())
            }
        }

        return franja
    }

    /**
     * Ancho relativo (en peso de LinearLayout) de cada una de las 3 franjas,
     * proporcional a su tramo dentro del eje visual ejeMin..ejeMax — mismo
     * criterio que dibujarBandas(umbralBajo, umbralAlto) del simulador,
     * adaptado de ángulos de gauge a anchos de barra. coerceIn(0.02f, 1f)
     * evita que una franja quede en 0 y no se pueda ver/tocar si el rango
     * configurado coincide justo con el borde del eje.
     */
    private fun pesosBandas(capa: CapaMetrica): Triple<Float, Float, Float> {
        val rango = (capa.ejeMax - capa.ejeMin).coerceAtLeast(0.001f)
        val bajo = ((capa.min - capa.ejeMin) / rango).coerceIn(0.02f, 1f)
        val alto = ((capa.ejeMax - capa.max) / rango).coerceIn(0.02f, 1f)
        val optimo = (1f - bajo - alto).coerceAtLeast(0.02f)
        return Triple(bajo, optimo, alto)
    }

    private fun bandaColor(colorRes: Int, peso: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, peso)
        setBackgroundColor(ContextCompat.getColor(context, colorRes))
    }

    private fun construirPin(capa: CapaMetrica, estado: EstadoCapa): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )

            val badge = TextView(context).apply {
                text = capa.valorFormateado()
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.simona_on_color))
                background = ContextCompat.getDrawable(context, R.drawable.bg_pin_badge)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, estado.colorRes)
                )
                setPadding(8.dp, 3.dp, 8.dp, 3.dp)
            }
            addView(
                badge,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 2.dp
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )

            val haloYPin = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp)
            }
            val halo = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(28.dp, 28.dp)
                background = ContextCompat.getDrawable(context, R.drawable.bg_pin_halo)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, estado.colorRes)
                )
                alpha = 0.28f
            }
            haloYPin.addView(halo)
            val pin = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(16.dp, 16.dp).apply {
                    gravity = Gravity.CENTER
                }
                background = ContextCompat.getDrawable(context, estado.pinDrawableRes)
            }
            haloYPin.addView(pin)
            addView(haloYPin)
        }
    }

    // ---------- Leyenda ----------

    private fun construirLeyenda(): LinearLayout {
        val fila = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        EstadoCapa.values().forEachIndexed { index, estado ->
            val item = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                if (index > 0) setPadding(16.dp, 0, 0, 0)
            }
            val punto = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(8.dp, 8.dp)
                background = ContextCompat.getDrawable(context, estado.pinDrawableRes)
            }
            item.addView(punto)
            val texto = TextView(context).apply {
                text = estado.etiqueta
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.simona_tinta_suave))
                setPadding(4.dp, 0, 0, 0)
            }
            item.addView(texto)
            fila.addView(item)
        }
        return fila
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}

/**
 * Arma las 4 capas (Humedad/Temp/pH/Luz) a partir de una Huerta y su última
 * lectura conocida — pensado para alimentar directamente
 * MiniMapaCapasView.setCapas() desde donde se tenga el par (huerta, lectura),
 * por ejemplo HuertaRepository.obtener(id) junto con huerta.ultimaLectura.
 * La humedad usa un eje fijo 0-100 (ya es un porcentaje natural); las otras
 * 3 capas no tienen un eje "natural", así que se arma con un margen del 25%
 * a cada lado del rango configurado (min/max) de la huerta.
 */
fun construirCapasDesdeHuerta(huerta: Huerta, lectura: LecturaHuerta): List<CapaMetrica> {
    fun conMargen(min: Float, max: Float, proporcion: Float = 0.25f): Pair<Float, Float> {
        val ancho = (max - min).coerceAtLeast(0.001f)
        val extra = ancho * proporcion
        return (min - extra) to (max + extra)
    }

    val ejeTemp = conMargen(huerta.tempMin, huerta.tempMax)
    val ejePh = conMargen(huerta.phMin, huerta.phMax)
    val ejeLuz = conMargen(huerta.luzMin.toFloat(), huerta.luzMax.toFloat())

    return listOf(
        CapaMetrica(
            id = "humedad", etiqueta = "Humedad", icono = R.drawable.ic_gota_full,
            valorActual = lectura.humedad, unidad = "%",
            min = huerta.humedadMin, max = huerta.humedadMax,
            ejeMin = 0f, ejeMax = 100f, decimales = 0
        ),
        CapaMetrica(
            id = "temp", etiqueta = "Temp.", icono = R.drawable.ic_termometro,
            valorActual = lectura.temperatura, unidad = "°C",
            min = huerta.tempMin, max = huerta.tempMax,
            ejeMin = ejeTemp.first, ejeMax = ejeTemp.second, decimales = 1
        ),
        CapaMetrica(
            id = "ph", etiqueta = "pH", icono = R.drawable.ic_matraz,
            valorActual = lectura.ph, unidad = "",
            min = huerta.phMin, max = huerta.phMax,
            ejeMin = ejePh.first, ejeMax = ejePh.second, decimales = 2
        ),
        CapaMetrica(
            id = "luz", etiqueta = "Luz", icono = R.drawable.ic_sol,
            valorActual = lectura.luz.toFloat(), unidad = "lux",
            min = huerta.luzMin.toFloat(), max = huerta.luzMax.toFloat(),
            ejeMin = ejeLuz.first, ejeMax = ejeLuz.second, decimales = 0
        )
    )
}
