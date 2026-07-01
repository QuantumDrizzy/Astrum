package com.astrum.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class MoonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var phase: Double = 0.5
        set(value) { field = value; invalidate() }

    var illumination: Double = 0.5
        set(value) { field = value; invalidate() }

    // At night the lit limb must be red, not gold — otherwise it leaks warm light past the filter.
    var nightMode: Boolean = false
        set(value) {
            field = value
            paintLit.color = Color.parseColor(if (value) "#ff4842" else "#c8b870")
            invalidate()
        }

    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1a2035")
    }
    private val paintLit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#c8b870")
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3a3020")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = (minOf(w, h) / 2f) - 2f

        // Dark disc
        canvas.drawCircle(cx, cy, r, paintShadow)

        // Lit part using phase
        val p = ((phase % 1.0) + 1.0) % 1.0
        val path = Path()

        if (p < 0.5) {
            val angle = p * 4 - 1
            val rx = (abs(angle) * r).toFloat()
            // Left half circle
            path.addArc(cx - r, cy - r, cx + r, cy + r, 90f, 180f)
            // Elliptical terminator
            val sweepDir = if (angle >= 0) -1f else 1f
            path.arcTo(cx - rx, cy - r, cx + rx, cy + r, 270f, sweepDir * 180f, false)
        } else {
            val angle = (p - 0.5) * 4 - 1
            val rx = (abs(angle) * r).toFloat()
            // Right half circle
            path.addArc(cx - r, cy - r, cx + r, cy + r, 270f, 180f)
            val sweepDir = if (angle >= 0) 1f else -1f
            path.arcTo(cx - rx, cy - r, cx + rx, cy + r, 90f, sweepDir * 180f, false)
        }
        path.close()

        // Clip to circle
        val clipPath = Path()
        clipPath.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPath(path, paintLit)
        canvas.restore()

        // Border
        canvas.drawCircle(cx, cy, r, paintBorder)
    }
}
