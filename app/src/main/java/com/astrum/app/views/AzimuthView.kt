package com.astrum.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.astrum.app.NightModeManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact radar-style compass showing azimuth + altitude of a sky object.
 * Size: 120 × 120 dp. Call [setPosition] to update.
 */
class AzimuthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuth  = 0f
    private var altitude = 0f

    // ── Paints ────────────────────────────────────────────────────────────
    private val bgPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tickPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cardPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
    private val dashPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint   = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setPosition(az: Float, alt: Float) {
        azimuth = az; altitude = alt; invalidate()
    }

    override fun onMeasure(ws: Int, hs: Int) {
        val px = (120 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(px, px)
    }

    override fun onDraw(canvas: Canvas) {
        val d   = resources.displayMetrics.density
        val cx  = width  / 2f
        val cy  = height / 2f
        val r   = minOf(cx, cy) - 14 * d
        val isN = NightModeManager.isNightMode

        // ── Background radial gradient ───────────────────────────────────
        val centerCol = if (isN) Color.rgb(20, 0, 0) else Color.rgb(14, 28, 50)
        val edgeCol   = if (isN) Color.rgb(8,  0, 0) else Color.rgb(6,  12, 18)
        bgPaint.shader = RadialGradient(cx, cy, r, centerCol, edgeCol, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, bgPaint)

        // ── Altitude rings (horizon, 30°, 60°) ───────────────────────────
        val ringCol  = if (isN) Color.argb(120, 140, 0, 0) else Color.argb(120, 40, 80, 140)
        ringPaint.color       = ringCol
        ringPaint.strokeWidth = d * 0.8f
        canvas.drawCircle(cx, cy, r,            ringPaint)  // horizon (0°)
        canvas.drawCircle(cx, cy, r * 0.667f,   ringPaint)  // 30°
        canvas.drawCircle(cx, cy, r * 0.333f,   ringPaint)  // 60°

        // ── Azimuth tick marks at 45° intervals ──────────────────────────
        tickPaint.color       = ringCol
        tickPaint.strokeWidth = d * 0.7f
        for (step in 0 until 8) {
            val ang    = Math.toRadians((step * 45.0 - 90.0)).toFloat()
            val outer  = r
            val inner  = r - (if (step % 2 == 0) 5 * d else 3 * d)
            canvas.drawLine(
                cx + outer * cos(ang), cy + outer * sin(ang),
                cx + inner * cos(ang), cy + inner * sin(ang),
                tickPaint
            )
        }

        // ── Cardinal labels ──────────────────────────────────────────────
        val cardCol = if (isN) Color.rgb(200, 60, 60) else Color.WHITE
        cardPaint.color    = cardCol
        cardPaint.textSize = 8f * d
        val lo = r + 8f * d
        val ts = cardPaint.textSize
        canvas.drawText("N", cx,      cy - lo + ts * 0.9f, cardPaint)
        canvas.drawText("S", cx,      cy + lo,              cardPaint)
        canvas.drawText("E", cx + lo, cy + ts * 0.35f,     cardPaint)
        canvas.drawText("W", cx - lo, cy + ts * 0.35f,     cardPaint)

        // ── Object position ──────────────────────────────────────────────
        val altC   = altitude.coerceIn(-90f, 90f)
        val above  = altC >= 0f
        val dist   = r * (1f - altC.coerceAtLeast(0f) / 90f)
        val angRad = Math.toRadians((azimuth - 90.0)).toFloat()
        val dotX   = cx + dist * cos(angRad)
        val dotY   = cy + dist * sin(angRad)

        val dotCol = when {
            isN    -> Color.rgb(220, 50,  50)
            above  -> Color.rgb(0,   230, 118)
            else   -> Color.rgb(255, 82,  82)
        }

        // Dashed line from centre to object
        dashPaint.color       = (dotCol and 0x00FFFFFF) or 0x55000000.toInt() or (dotCol ushr 24 shl 24 and 0x55FFFFFF.toInt())
        dashPaint.color       = Color.argb(80,
            Color.red(dotCol), Color.green(dotCol), Color.blue(dotCol))
        dashPaint.strokeWidth = 1f * d
        dashPaint.pathEffect  = DashPathEffect(floatArrayOf(3 * d, 3 * d), 0f)
        canvas.drawLine(cx, cy, dotX, dotY, dashPaint)
        dashPaint.pathEffect = null

        // Glow halo
        glowPaint.color = Color.argb(60, Color.red(dotCol), Color.green(dotCol), Color.blue(dotCol))
        canvas.drawCircle(dotX, dotY, 9f * d, glowPaint)

        // Dot core
        dotPaint.color = dotCol
        canvas.drawCircle(dotX, dotY, 4.5f * d, dotPaint)

        // Tiny centre cross
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, Color.red(ringCol), Color.green(ringCol), Color.blue(ringCol))
            strokeWidth = d * 0.7f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(cx - 4 * d, cy, cx + 4 * d, cy, crossPaint)
        canvas.drawLine(cx, cy - 4 * d, cx, cy + 4 * d, crossPaint)
    }
}
