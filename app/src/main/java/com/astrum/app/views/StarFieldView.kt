package com.astrum.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class StarFieldView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Star(
        val x: Float, val y: Float,
        val radius: Float, val baseAlpha: Float,
        val speed: Float, val phase: Float,
        val color: Int
    )

    data class Meteor(
        var x: Float, var y: Float,
        val vx: Float, val vy: Float,
        val length: Float,
        var life: Float, val maxLife: Float
    )

    private val stars = mutableListOf<Star>()
    private val meteors = mutableListOf<Meteor>()
    private var t = 0f
    private val random = Random(42)

    private val starColors = intArrayOf(
        Color.WHITE,
        Color.parseColor("#ffe8d4"),
        Color.parseColor("#d4e8ff"),
        Color.parseColor("#fff4d4"),
        Color.parseColor("#f0e8ff"),
    )

    private val paintStar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintMeteor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildStars(w, h)
    }

    private fun buildStars(w: Int, h: Int) {
        stars.clear()
        // Tiny dim stars
        repeat(120) {
            stars += Star(
                random.nextFloat() * w, random.nextFloat() * h,
                random.nextFloat() * 0.8f + 0.2f,
                random.nextFloat() * 0.2f + 0.05f,
                random.nextFloat() * 0.5f + 0.3f,
                random.nextFloat() * (2 * PI).toFloat(),
                Color.WHITE
            )
        }
        // Medium stars
        repeat(150) {
            stars += Star(
                random.nextFloat() * w, random.nextFloat() * h,
                random.nextFloat() * 1.2f + 0.5f,
                random.nextFloat() * 0.4f + 0.15f,
                random.nextFloat() * 0.8f + 0.5f,
                random.nextFloat() * (2 * PI).toFloat(),
                starColors[random.nextInt(starColors.size)]
            )
        }
        // Bright stars with glow
        repeat(50) {
            stars += Star(
                random.nextFloat() * w, random.nextFloat() * h,
                random.nextFloat() * 2f + 1f,
                random.nextFloat() * 0.5f + 0.3f,
                random.nextFloat() * 1.5f + 0.8f,
                random.nextFloat() * (2 * PI).toFloat(),
                starColors[random.nextInt(starColors.size)]
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        t += 0.02f

        // Milky Way gradient band
        val mw = LinearGradient(
            0f, height * 0.1f, width.toFloat(), height * 0.9f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(10, 80, 60, 140),
                Color.argb(16, 100, 80, 160),
                Color.argb(10, 80, 60, 140),
                Color.TRANSPARENT
            ),
            null, Shader.TileMode.CLAMP
        )
        paintStar.shader = mw
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintStar)
        paintStar.shader = null

        // Stars
        for (star in stars) {
            val alpha = (star.baseAlpha * (0.6f + 0.4f * sin(t * star.speed + star.phase).toFloat())).coerceIn(0f, 1f)
            paintStar.color = star.color
            paintStar.alpha = (alpha * 255).toInt()
            canvas.drawCircle(star.x, star.y, star.radius, paintStar)
            // Glow for bright ones
            if (star.radius > 1.2f && alpha > 0.4f) {
                paintStar.alpha = (alpha * 0.15f * 255).toInt()
                canvas.drawCircle(star.x, star.y, star.radius * 3f, paintStar)
            }
        }

        // Meteors
        if (random.nextFloat() < 0.005f && meteors.size < 3) {
            val sx = random.nextFloat() * width * 0.8f + width * 0.1f
            val sy = random.nextFloat() * height * 0.3f
            val ang = (random.nextFloat() * 30f + 20f) * (PI / 180f).toFloat()
            val speed = random.nextFloat() * 5f + 4f
            meteors += Meteor(sx, sy, cos(ang) * speed, sin(ang) * speed,
                random.nextFloat() * 80f + 60f, 0f, random.nextFloat() * 50f + 40f)
        }

        val iter = meteors.iterator()
        while (iter.hasNext()) {
            val m = iter.next()
            m.x += m.vx; m.y += m.vy; m.life++
            val prog = m.life / m.maxLife
            val alpha = when {
                prog < 0.2f -> prog / 0.2f
                else -> 1f - ((prog - 0.2f) / 0.8f)
            }
            if (alpha <= 0f || m.x > width || m.y > height) { iter.remove(); continue }

            val tailLen = m.length * min(1f, prog * 3f)
            val nx = -m.vx / sqrt(m.vx * m.vx + m.vy * m.vy)
            val ny = -m.vy / sqrt(m.vx * m.vx + m.vy * m.vy)

            val shader = LinearGradient(
                m.x + nx * tailLen, m.y + ny * tailLen, m.x, m.y,
                Color.TRANSPARENT, Color.argb((alpha * 220).toInt(), 255, 240, 220),
                Shader.TileMode.CLAMP
            )
            paintMeteor.shader = shader
            canvas.drawLine(m.x + nx * tailLen, m.y + ny * tailLen, m.x, m.y, paintMeteor)
            paintMeteor.shader = null

            // Head glow
            paintStar.color = Color.WHITE
            paintStar.alpha = (alpha * 200).toInt()
            canvas.drawCircle(m.x, m.y, 2.5f, paintStar)
        }

        postInvalidateOnAnimation()
    }
}
