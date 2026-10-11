package com.example.roguelikeai.ui
import android.content.Context; import android.graphics.Canvas; import android.graphics.Paint; import android.graphics.PointF; import android.util.AttributeSet; import android.view.View
class ChartView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var values = listOf<Double>(); set(v) { field = v; invalidate() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4DD0E1.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE }
    private val grid = Paint().apply { color = 0x33FFFFFF }
    override fun onDraw(cv: Canvas) { super.onDraw(cv); cv.drawColor(0xFF0B0F14.toInt()); if (values.size < 2) return
        val mn = values.min()!!; val mx = values.max()!!; val span = (mx - mn).coerceAtLeast(1.0); val w = width.toFloat(); val h = height.toFloat()
        for (i in 0..4) { val y = h * i / 4; cv.drawLine(0f, y, w, y, grid) }
        val pts = values.mapIndexed { i, v -> PointF(i / (values.size - 1f) * w, h - ((v - mn) / span * (h - 20) + 10).toFloat()) }
        for (i in 0 until pts.size - 1) cv.drawLine(pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y, line) }
}
