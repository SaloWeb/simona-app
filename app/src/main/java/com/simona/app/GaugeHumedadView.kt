package com.simona.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Gauge circular nativo de humedad — Fase 5.5 del plan de unificación
 * visual (portar la estética del simulador HTML a la app nativa), marcada
 * en su momento como "opcional, alto esfuerzo" y dejada pendiente cuando
 * se hizo la Fase 5.4 (MiniMapaCapasView). Vive en DetalleHuertaActivity,
 * arriba del mini-mapa multicapa, como pieza central de esa pantalla.
 *
 * Dibuja un arco de 270° (deja un hueco de 90° abajo, patrón clásico de
 * gauge) dividido en 3 bandas de color según el mismo criterio que ya usa
 * MiniMapaCapasView.pesosBandas() — seco/óptimo/húmedo según el rango
 * humedadMin/humedadMax configurado de la huerta sobre el eje fijo 0-100 —
 * más un marcador circular en la posición del valor actual y el
 * porcentaje grande en el centro.
 *
 * Se recalcula todo en cada setValor(); no guarda estado de animación
 * entre lecturas (la app ya asume refrescos completos cada pocos segundos
 * vía polling, igual que MiniMapaCapasView y renderizarTarjetas()).
 */
class GaugeHumedadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var valorActual = 0f
    private var humedadMin = 30f
    private var humedadMax = 70f

    private val grosorTrazo = 18.dp.toFloat()
    private val radioMarcador = 11.dp.toFloat()

    private val colorSeco = ContextCompat.getColor(context, R.color.simona_estado_seco)
    private val colorOptimo = ContextCompat.getColor(context, R.color.simona_estado_optimo)
    private val colorHumedo = ContextCompat.getColor(context, R.color.simona_estado_humedo)
    private val colorPista = ContextCompat.getColor(context, R.color.simona_linea)
    private val colorTexto = ContextCompat.getColor(context, R.color.simona_tinta)
    private val colorTextoSuave = ContextCompat.getColor(context, R.color.simona_tinta_suave)
    private val colorFondoMarcador = ContextCompat.getColor(context, R.color.simona_card_bg)

    private val paintPista = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = grosorTrazo
        strokeCap = Paint.Cap.ROUND
        color = colorPista
    }
    private val paintBanda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = grosorTrazo
        strokeCap = Paint.Cap.BUTT
    }
    private val paintMarcadorRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorFondoMarcador
    }
    private val paintMarcadorBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.dp.toFloat()
    }
    private val paintValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = colorTexto
    }
    private val paintEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorTextoSuave
    }

    private val rectArco = RectF()

    companion object {
        private const val ANGULO_INICIO = 135f
        private const val ANGULO_BARRIDO = 270f
        private const val EJE_MIN = 0f
        private const val EJE_MAX = 100f
    }

    /** Actualiza el valor mostrado y el rango configurado de la huerta, y
     * fuerza un redibujo. Pensado para llamarse junto con setCapas() del
     * mini-mapa, desde el mismo par (huerta, lectura). */
    fun setValor(valor: Float, min: Float, max: Float) {
        valorActual = valor.coerceIn(EJE_MIN, EJE_MAX)
        humedadMin = min
        humedadMax = max
        // PLAN_MEJORAS_20.md, punto 19: es un View de Canvas puro — sin esto,
        // TalkBack no anuncia nada al enfocarlo (el porcentaje es solo
        // píxeles dibujados, no texto real).
        contentDescription = context.getString(
            R.string.gauge_humedad_descripcion,
            valorActual.toInt(),
            estadoDescripcionAccesible()
        )
        invalidate()
    }

    private fun estadoDescripcionAccesible(): String = context.getString(
        when {
            valorActual <= humedadMin -> R.string.home_estado_seco
            valorActual >= humedadMax -> R.string.home_estado_humedo
            else -> R.string.home_estado_optimo
        }
    )

    private fun estadoColor(): Int = when {
        valorActual <= humedadMin -> colorSeco
        valorActual >= humedadMax -> colorHumedo
        else -> colorOptimo
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Cuadrado, del lado que menos espacio tenga (normalmente el ancho).
        val lado = min(measuredWidth, if (measuredHeight > 0) measuredHeight else measuredWidth)
        setMeasuredDimension(measuredWidth, lado)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        // Margen extra para que el marcador (que sobresale del trazo) no se
        // corte contra el borde del View.
        val radio = (min(width, height) / 2f) - grosorTrazo / 2f - radioMarcador
        rectArco.set(cx - radio, cy - radio, cx + radio, cy + radio)

        // Pista de fondo completa (gris suave), después las 3 bandas de
        // color encima según el rango configurado.
        canvas.drawArc(rectArco, ANGULO_INICIO, ANGULO_BARRIDO, false, paintPista)

        val rango = (humedadMax - humedadMin).coerceAtLeast(0.001f)
        val pesoBajo = ((humedadMin - EJE_MIN) / (EJE_MAX - EJE_MIN)).coerceIn(0.02f, 1f)
        val pesoAlto = ((EJE_MAX - humedadMax) / (EJE_MAX - EJE_MIN)).coerceIn(0.02f, 1f)
        val pesoOptimo = (1f - pesoBajo - pesoAlto).coerceAtLeast(0.02f)

        var anguloActual = ANGULO_INICIO
        paintBanda.color = colorSeco
        canvas.drawArc(rectArco, anguloActual, ANGULO_BARRIDO * pesoBajo, false, paintBanda)
        anguloActual += ANGULO_BARRIDO * pesoBajo

        paintBanda.color = colorOptimo
        canvas.drawArc(rectArco, anguloActual, ANGULO_BARRIDO * pesoOptimo, false, paintBanda)
        anguloActual += ANGULO_BARRIDO * pesoOptimo

        paintBanda.color = colorHumedo
        canvas.drawArc(rectArco, anguloActual, ANGULO_BARRIDO * pesoAlto, false, paintBanda)

        // Marcador circular en la posición del valor actual.
        val porcentaje = (valorActual - EJE_MIN) / (EJE_MAX - EJE_MIN)
        val anguloValorRad = Math.toRadians((ANGULO_INICIO + ANGULO_BARRIDO * porcentaje).toDouble())
        val mx = cx + radio * cos(anguloValorRad).toFloat()
        val my = cy + radio * sin(anguloValorRad).toFloat()
        val colorEstado = estadoColor()
        canvas.drawCircle(mx, my, radioMarcador, paintMarcadorRelleno)
        paintMarcadorBorde.color = colorEstado
        canvas.drawCircle(mx, my, radioMarcador - paintMarcadorBorde.strokeWidth / 2f, paintMarcadorBorde)

        // Texto central: porcentaje grande + etiqueta "Humedad" debajo.
        paintValor.textSize = radio * 0.62f
        paintValor.color = colorEstado
        val textoValor = "${valorActual.roundToInt()}%"
        val offsetValor = (paintValor.descent() + paintValor.ascent()) / 2f
        canvas.drawText(textoValor, cx, cy - offsetValor - radio * 0.12f, paintValor)

        paintEtiqueta.textSize = radio * 0.16f
        canvas.drawText("Humedad", cx, cy + radio * 0.30f, paintEtiqueta)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
