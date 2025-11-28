package com.example.silentzonefinder_android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.silentzonefinder_android.R
import kotlin.math.max
import kotlin.math.min

class NoiseTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.grey_light)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.primary_purple)
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary_purple)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary_purple_light)
        alpha = 50
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.grey_light)
    }

    private var dataPoints: List<Double> = emptyList()

    fun setData(values: List<Double>) {
        dataPoints = values.takeLast(MAX_POINTS)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        drawGrid(canvas, contentWidth, contentHeight)

        if (dataPoints.size < 2) {
            // draw straight placeholder line
            val midY = contentHeight * 0.6f
            canvas.drawLine(0f, midY, contentWidth.toFloat(), midY, placeholderPaint)
            canvas.restore()
            return
        }

        val minValue = min(dataPoints.minOrNull() ?: 0.0, DEFAULT_MIN_DB)
        val maxValue = max(dataPoints.maxOrNull() ?: 0.0, DEFAULT_MAX_DB)
        val range = if (maxValue - minValue == 0.0) 1.0 else (maxValue - minValue)

        val stepX = contentWidth.toFloat() / (dataPoints.size - 1)
        val path = Path()
        var isFirstPoint = true

        dataPoints.forEachIndexed { index, value ->
            val x = stepX * index
            val normalized = ((value - minValue) / range).toFloat()
            val y = contentHeight - normalized * contentHeight
            if (isFirstPoint) {
                path.moveTo(x, y)
                isFirstPoint = false
            } else {
                path.lineTo(x, y)
            }
        }

        // fill beneath the line
        val fillPath = Path(path).apply {
            lineTo(contentWidth.toFloat(), contentHeight.toFloat())
            lineTo(0f, contentHeight.toFloat())
            close()
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        val pointRadius = 4 * resources.displayMetrics.density
        dataPoints.forEachIndexed { index, value ->
            val x = stepX * index
            val normalized = ((value - minValue) / range).toFloat()
            val y = contentHeight - normalized * contentHeight
            canvas.drawCircle(x, y, pointRadius, pointPaint)
        }

        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas, width: Int, height: Int) {
        val rows = 3
        val rowHeight = height / rows.toFloat()
        for (i in 0..rows) {
            val y = rowHeight * i
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
    }

    companion object {
        private const val MAX_POINTS = 12
        private const val DEFAULT_MIN_DB = 30.0
        private const val DEFAULT_MAX_DB = 80.0
    }
}



























