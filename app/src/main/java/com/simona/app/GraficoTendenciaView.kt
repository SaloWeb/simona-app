package com.simona.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Componente gráfico nativo para mostrar la curva de evolución de
 * humedad y temperatura de una huerta a lo largo del tiempo.
 */
class GraficoTendenciaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var puntos: List<PuntoHistorial> = emptyList()
    private var modoHumedad: Boolean = true // true = Humedad (%), false = Temp (°C)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.simona_linea)
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.simona_tinta_suave)
    }

    fun setDatos(datos: List<PuntoHistorial>, esHumedad: Boolean) {
        this.puntos = datos
        this.modoHumedad = esHumedad
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (180 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 20f * resources.displayMetrics.density
        val paddingRight = 20f * resources.displayMetrics.density
        val paddingTop = 20f * resources.displayMetrics.density
        val paddingBottom = 24f * resources.displayMetrics.density

        if (puntos.size < 2) {
            canvas.drawText(
                "Acumulando telemetría para trazar la tendencia…",
                w / 2f,
                h / 2f,
                emptyTextPaint
            )
            return
        }

        val primaryColor = if (modoHumedad) {
            ContextCompat.getColor(context, R.color.simona_azul)
        } else {
            ContextCompat.getColor(context, R.color.simona_rojo)
        }

        linePaint.color = primaryColor
        pointPaint.color = primaryColor
        textPaint.color = ContextCompat.getColor(context, R.color.simona_tinta_suave)

        val valores = puntos.map { if (modoHumedad) it.humedad else it.temperatura }
        var minVal = (valores.minOrNull() ?: 0f)
        var maxVal = (valores.maxOrNull() ?: 100f)

        if (maxVal - minVal < 5f) {
            minVal = max(0f, minVal - 5f)
            maxVal += 5f
        }

        val plotWidth = w - paddingLeft - paddingRight
        val plotHeight = h - paddingTop - paddingBottom

        // Draw horizontal grid lines (top, middle, bottom)
        val yTop = paddingTop
        val yMid = paddingTop + plotHeight / 2f
        val yBottom = paddingTop + plotHeight

        canvas.drawLine(paddingLeft, yTop, w - paddingRight, yTop, gridPaint)
        canvas.drawLine(paddingLeft, yMid, w - paddingRight, yMid, gridPaint)
        canvas.drawLine(paddingLeft, yBottom, w - paddingRight, yBottom, gridPaint)

        val unidad = if (modoHumedad) "%" else "°C"
        canvas.drawText("${maxVal.toInt()}$unidad", paddingLeft, yTop - 4f, textPaint)
        canvas.drawText("${minVal.toInt()}$unidad", paddingLeft, yBottom + 16f, textPaint)

        val path = Path()
        val fillPath = Path()

        val stepX = plotWidth / (valores.size - 1)

        val coords = mutableListOf<PointF>()
        valores.forEachIndexed { i, valor ->
            val x = paddingLeft + (i * stepX)
            val ratio = ((valor - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val y = paddingTop + plotHeight * (1f - ratio)
            coords.add(PointF(x, y))
        }

        path.moveTo(coords[0].x, coords[0].y)
        fillPath.moveTo(coords[0].x, coords[0].y)

        for (i in 1 until coords.size) {
            val prev = coords[i - 1]
            val curr = coords[i]
            val midX = (prev.x + curr.x) / 2f
            path.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
        }

        fillPath.lineTo(coords.last().x, yBottom)
        fillPath.lineTo(coords.first().x, yBottom)
        fillPath.close()

        // Gradient fill
        fillPaint.shader = LinearGradient(
            0f, paddingTop, 0f, yBottom,
            Color.argb(70, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // Draw last point dot and label
        val lastPoint = coords.last()
        canvas.drawCircle(lastPoint.x, lastPoint.y, 6f * resources.displayMetrics.density, pointPaint)
        pointPaint.color = Color.WHITE
        canvas.drawCircle(lastPoint.x, lastPoint.y, 3f * resources.displayMetrics.density, pointPaint)

        val valorActual = valores.last()
        val labelActual = "${if (modoHumedad) "Humedad: " else "Temp: "}${valorActual.toInt()}$unidad"
        textPaint.color = primaryColor
        canvas.drawText(labelActual, w - paddingRight - 80f * resources.displayMetrics.density, paddingTop - 4f, textPaint)
    }
}
