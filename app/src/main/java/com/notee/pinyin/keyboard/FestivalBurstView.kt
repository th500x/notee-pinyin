package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.notee.pinyin.festival.FestivalEffect
import kotlin.math.cos
import kotlin.math.sin

/**
 * A short festival picture drawn over the key area only (docs/02-0 §5). About a second and a half.
 * Touches land here while it is showing, so they do not hit the keys underneath.
 */
class FestivalBurstView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var effect: FestivalEffect? = null
    private var started = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val oval = RectF()

    fun play(effect: FestivalEffect) {
        this.effect = effect
        started = SystemClock.uptimeMillis()
        visibility = VISIBLE
        invalidate()
    }

    fun stop() {
        effect = null
        visibility = GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = effect != null

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val effect = effect ?: return
        val t = ((SystemClock.uptimeMillis() - started).toFloat() / DURATION).coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        wash(canvas, w, h, effect, t)
        when (effect) {
            FestivalEffect.FIREWORKS -> fireworks(canvas, w, h, t)
            FestivalEffect.HEARTS -> hearts(canvas, w, h, t)
            FestivalEffect.EGGS -> eggs(canvas, w, h, t)
            FestivalEffect.PUMPKIN -> pumpkin(canvas, w, h, t)
            FestivalEffect.LEAVES -> leaves(canvas, w, h, t)
            FestivalEffect.BONFIRE -> bonfire(canvas, w, h, t)
            FestivalEffect.TREE -> tree(canvas, w, h, t)
            FestivalEffect.LANTERNS -> lanterns(canvas, w, h, t)
            FestivalEffect.BOAT -> boat(canvas, w, h, t)
            FestivalEffect.STARS -> stars(canvas, w, h, t)
            FestivalEffect.MOON -> moon(canvas, w, h, t)
            FestivalEffect.FLOWER -> flower(canvas, w, h, t)
            FestivalEffect.LOTUS -> lotus(canvas, w, h, t)
            FestivalEffect.WATER -> water(canvas, w, h, t)
            FestivalEffect.KRATHONG -> krathong(canvas, w, h, t)
        }
        if (t < 1f) postInvalidateOnAnimation() else stop()
    }

    private fun wash(canvas: Canvas, w: Float, h: Float, effect: FestivalEffect, t: Float) {
        val quiet = effect == FestivalEffect.LOTUS || effect == FestivalEffect.MOON || effect == FestivalEffect.FLOWER
        val alpha = ((if (quiet) 0.45f else 0.72f) * (1f - (t - 0.8f).coerceAtLeast(0f) / 0.2f) * 255).toInt()
        fill.color = 0xFF101820.toInt()
        fill.alpha = alpha.coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, fill)
    }

    private fun fireworks(canvas: Canvas, w: Float, h: Float, t: Float) {
        val starts = floatArrayOf(0.06f, 0.28f, 0.46f, 0.62f)
        val colors = intArrayOf(0xFFFFD45A.toInt(), 0xFFFF5A4A.toInt(), 0xFFFFF1C2.toInt(), 0xFFFF8A3D.toInt())
        val cx = floatArrayOf(0.30f, 0.70f, 0.48f, 0.36f)
        val cy = floatArrayOf(0.34f, 0.30f, 0.58f, 0.68f)
        stroke.strokeWidth = density * 2.4f
        for (i in starts.indices) {
            val local = ((t - starts[i]) / 0.40f).takeIf { it in 0f..1f } ?: continue
            val radius = local * minOf(w, h) * 0.30f
            stroke.color = colors[i]
            stroke.alpha = ((1f - local) * 255).toInt()
            val x = cx[i] * w
            val y = cy[i] * h
            for (k in 0 until 14) {
                val angle = k * Math.PI * 2 / 14
                canvas.drawLine(x, y, x + cos(angle).toFloat() * radius, y + sin(angle).toFloat() * radius, stroke)
            }
        }
    }

    private fun hearts(canvas: Canvas, w: Float, h: Float, t: Float) {
        val colors = intArrayOf(0xFFFF5A7A.toInt(), 0xFFFF8AA0.toInt(), 0xFFFFD0DA.toInt())
        for (i in 0 until 7) {
            val local = ((t - i * 0.06f) / 0.85f).coerceIn(0f, 1f)
            val x = w * (0.15f + (i % 4) * 0.22f) + sin(local * 6 + i) * w * 0.03f
            val y = h * (1.05f - local * 1.2f)
            fill.color = colors[i % colors.size]
            fill.alpha = ((1f - local) * 255).toInt()
            heart(canvas, x, y, w * 0.045f)
        }
    }

    private fun heart(canvas: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y + r * 0.6f)
        path.cubicTo(x - r * 2f, y - r, x - r * 0.2f, y - r * 1.8f, x, y - r * 0.4f)
        path.cubicTo(x + r * 0.2f, y - r * 1.8f, x + r * 2f, y - r, x, y + r * 0.6f)
        canvas.drawPath(path, fill)
    }

    private fun eggs(canvas: Canvas, w: Float, h: Float, t: Float) {
        val grow = ease(t.coerceAtMost(0.45f) / 0.45f)
        val colors = intArrayOf(0xFFFFE08A.toInt(), 0xFFB7E4C7.toInt(), 0xFFF7C6D0.toInt(), 0xFFC9DDF7.toInt(), 0xFFFFC49A.toInt())
        for (i in colors.indices) {
            val x = w * (0.18f + i * 0.16f)
            val y = h * 0.62f
            oval.set(x - w * 0.05f * grow, y - h * 0.11f * grow, x + w * 0.05f * grow, y + h * 0.08f * grow)
            fill.color = colors[i]
            fill.alpha = 255
            canvas.drawOval(oval, fill)
        }
    }

    private fun pumpkin(canvas: Canvas, w: Float, h: Float, t: Float) {
        val grow = ease(t.coerceAtMost(0.5f) / 0.5f)
        val r = minOf(w, h) * 0.28f * grow
        val cx = w / 2
        val cy = h * 0.56f
        fill.color = 0xFFE8872E.toInt()
        fill.alpha = 255
        canvas.drawCircle(cx, cy, r, fill)
        fill.color = 0xFFC46A18.toInt()
        stroke.color = 0xFFC46A18.toInt()
        stroke.alpha = 255
        stroke.strokeWidth = density * 3f
        canvas.drawLine(cx - r * 0.45f, cy - r * 0.2f, cx - r * 0.45f, cy + r * 0.55f, stroke)
        canvas.drawLine(cx + r * 0.45f, cy - r * 0.2f, cx + r * 0.45f, cy + r * 0.55f, stroke)
        fill.color = 0xFF3E8F45.toInt()
        oval.set(cx - r * 0.12f, cy - r * 1.25f, cx + r * 0.28f, cy - r * 0.7f)
        canvas.drawOval(oval, fill)
    }

    private fun leaves(canvas: Canvas, w: Float, h: Float, t: Float) {
        val colors = intArrayOf(0xFFD98A2B.toInt(), 0xFFC4522A.toInt(), 0xFFE2B15A.toInt())
        for (i in 0 until 10) {
            val local = ((t + i * 0.07f) % 1f)
            val x = w * ((i * 0.097f) % 1f)
            val y = local * h * 1.1f - h * 0.05f
            fill.color = colors[i % colors.size]
            fill.alpha = 230
            canvas.save()
            canvas.rotate(local * 180f + i * 20f, x, y)
            oval.set(x, y, x + w * 0.045f, y + h * 0.03f)
            canvas.drawOval(oval, fill)
            canvas.restore()
        }
    }

    private fun bonfire(canvas: Canvas, w: Float, h: Float, t: Float) {
        val cx = w / 2
        val base = h * 0.82f
        fill.color = 0xFF5C3317.toInt()
        fill.alpha = 255
        canvas.drawRect(cx - w * 0.16f, base, cx + w * 0.16f, base + h * 0.04f, fill)
        val flicker = 0.85f + 0.15f * sin(t * 30)
        val colors = intArrayOf(0xFFFF5A1F.toInt(), 0xFFFFB020.toInt(), 0xFFFFF1A8.toInt())
        for (i in colors.indices) {
            val reach = h * (0.42f - i * 0.1f) * flicker * ease(t.coerceAtMost(0.3f) / 0.3f)
            path.reset()
            path.moveTo(cx - w * (0.10f - i * 0.02f), base)
            path.quadTo(cx - w * 0.04f, base - reach * 0.6f, cx, base - reach)
            path.quadTo(cx + w * 0.04f, base - reach * 0.6f, cx + w * (0.10f - i * 0.02f), base)
            path.close()
            fill.color = colors[i]
            canvas.drawPath(path, fill)
        }
    }

    private fun tree(canvas: Canvas, w: Float, h: Float, t: Float) {
        val grow = ease(t.coerceAtMost(0.55f) / 0.55f)
        val cx = w / 2
        val base = h * 0.9f
        val top = base - grow * h * 0.7f
        fill.color = 0xFF6B3E26.toInt()
        fill.alpha = 255
        val trunk = w * 0.045f
        canvas.drawRect(cx - trunk, base - h * 0.1f * grow, cx + trunk, base, fill)
        fill.color = 0xFF2F8F4E.toInt()
        for (i in 0 until 3) {
            val layerTop = top + i * (base - top) * 0.22f
            val layerBottom = layerTop + (base - top) * 0.42f
            val half = w * (0.12f + i * 0.07f)
            path.reset()
            path.moveTo(cx, layerTop)
            path.lineTo(cx + half, layerBottom)
            path.lineTo(cx - half, layerBottom)
            path.close()
            canvas.drawPath(path, fill)
        }
        fill.color = 0xFFFFD45A.toInt()
        canvas.drawCircle(cx, top, w * 0.028f, fill)
        val baubles = intArrayOf(0xFFE24B4B.toInt(), 0xFFFFD45A.toInt(), 0xFF7EB6FF.toInt())
        for (i in baubles.indices) {
            fill.color = baubles[i]
            canvas.drawCircle(cx + (i - 1) * w * 0.08f, top + h * (0.22f + i * 0.08f) * grow, w * 0.018f, fill)
        }
    }

    private fun lanterns(canvas: Canvas, w: Float, h: Float, t: Float) {
        val rise = ease(t)
        for (i in 0 until 4) {
            val x = w * (0.2f + i * 0.2f)
            val y = h * (0.85f - rise * (0.45f + (i % 2) * 0.15f))
            val rw = w * 0.07f
            val rh = h * 0.1f
            fill.color = 0xFFE24B4B.toInt()
            fill.alpha = 255
            oval.set(x - rw, y - rh, x + rw, y + rh)
            canvas.drawOval(oval, fill)
            fill.color = 0xFFFFD45A.toInt()
            canvas.drawRect(x - rw * 0.35f, y - rh * 1.15f, x + rw * 0.35f, y - rh * 0.85f, fill)
            stroke.color = 0xFFFFD45A.toInt()
            stroke.alpha = 255
            stroke.strokeWidth = density * 1.5f
            canvas.drawLine(x, y + rh, x, y + rh * 1.45f, stroke)
        }
    }

    private fun boat(canvas: Canvas, w: Float, h: Float, t: Float) {
        val x = w * (-0.2f + ease(t) * 1.15f)
        val y = h * 0.62f
        fill.color = 0xFF2F6FD0.toInt()
        fill.alpha = 180
        canvas.drawRect(0f, y + h * 0.06f, w, h, fill)
        fill.color = 0xFFC4522A.toInt()
        fill.alpha = 255
        path.reset()
        path.moveTo(x, y)
        path.lineTo(x + w * 0.28f, y)
        path.lineTo(x + w * 0.22f, y + h * 0.08f)
        path.lineTo(x + w * 0.06f, y + h * 0.08f)
        path.close()
        canvas.drawPath(path, fill)
        fill.color = 0xFF3E8F45.toInt()
        path.reset()
        path.moveTo(x + w * 0.04f, y)
        path.lineTo(x + w * 0.1f, y - h * 0.1f)
        path.lineTo(x + w * 0.16f, y)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun stars(canvas: Canvas, w: Float, h: Float, t: Float) {
        fill.color = 0xFFFFF1C2.toInt()
        for (i in 0 until 18) {
            val twinkle = 0.45f + 0.55f * ((sin(t * 16 + i) + 1) / 2)
            fill.alpha = (twinkle * 255).toInt()
            val x = w * ((i * 0.137f) % 1f)
            val y = h * ((i * 0.173f) % 0.8f + 0.08f)
            canvas.drawCircle(x, y, density * (1.4f + (i % 3)), fill)
        }
    }

    private fun moon(canvas: Canvas, w: Float, h: Float, t: Float) {
        val grow = ease(t.coerceAtMost(0.5f) / 0.5f)
        val r = minOf(w, h) * 0.32f * grow
        fill.color = 0xFFFFF1C2.toInt()
        fill.alpha = 255
        canvas.drawCircle(w * 0.5f, h * 0.48f, r, fill)
        fill.color = 0xFFE7D59A.toInt()
        canvas.drawCircle(w * 0.42f, h * 0.42f, r * 0.16f, fill)
        canvas.drawCircle(w * 0.58f, h * 0.55f, r * 0.1f, fill)
    }

    private fun flower(canvas: Canvas, w: Float, h: Float, t: Float) {
        val grow = ease(t.coerceAtMost(0.6f) / 0.6f)
        val cx = w / 2
        val cy = h * 0.5f
        val r = minOf(w, h) * 0.08f * grow
        fill.alpha = 255
        for (i in 0 until 12) {
            val angle = i * Math.PI * 2 / 12
            fill.color = if (i % 2 == 0) 0xFFFFD45A.toInt() else 0xFFE2B15A.toInt()
            canvas.drawCircle(
                cx + cos(angle).toFloat() * r * 2.2f,
                cy + sin(angle).toFloat() * r * 2.2f,
                r,
                fill,
            )
        }
        fill.color = 0xFFD98A2B.toInt()
        canvas.drawCircle(cx, cy, r * 0.9f, fill)
    }

    private fun lotus(canvas: Canvas, w: Float, h: Float, t: Float) {
        val grow = ease(t.coerceAtMost(0.75f) / 0.75f)
        val cx = w / 2
        val cy = h * 0.55f
        fill.alpha = (200 + 55 * grow).toInt()
        for (i in 0 until 8) {
            val angle = -Math.PI / 2 + (i - 3.5) * 0.38
            fill.color = if (i % 2 == 0) 0xFFF4B6C8.toInt() else 0xFFFFE4EE.toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(Math.toDegrees(angle).toFloat())
            oval.set(-w * 0.035f * grow, -h * 0.2f * grow, w * 0.035f * grow, 0f)
            canvas.drawOval(oval, fill)
            canvas.restore()
        }
    }

    private fun water(canvas: Canvas, w: Float, h: Float, t: Float) {
        fill.color = 0xFF7EB6FF.toInt()
        for (i in 0 until 14) {
            val local = ((t * 1.3f + i * 0.05f) % 1f)
            val x = w * ((i * 0.07f + 0.05f) % 1f)
            val y = local * h
            fill.alpha = ((1f - local) * 220).toInt()
            canvas.drawCircle(x, y, density * 3.5f, fill)
        }
    }

    private fun krathong(canvas: Canvas, w: Float, h: Float, t: Float) {
        val drift = sin(t * 8) * w * 0.02f
        val x = w * 0.5f + drift
        val y = h * (0.7f - ease(t.coerceAtMost(0.4f) / 0.4f) * 0.12f)
        fill.color = 0xFF2F6FD0.toInt()
        fill.alpha = 160
        canvas.drawRect(0f, y + h * 0.05f, w, h, fill)
        fill.color = 0xFF3E8F45.toInt()
        fill.alpha = 255
        oval.set(x - w * 0.12f, y, x + w * 0.12f, y + h * 0.06f)
        canvas.drawOval(oval, fill)
        fill.color = 0xFFF4B6C8.toInt()
        canvas.drawCircle(x, y, w * 0.035f, fill)
        fill.color = 0xFFFFB020.toInt()
        path.reset()
        path.moveTo(x, y - h * 0.08f)
        path.lineTo(x - w * 0.015f, y)
        path.lineTo(x + w * 0.015f, y)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    private val density: Float get() = resources.displayMetrics.density

    private companion object {
        const val DURATION = 1600L
    }
}
