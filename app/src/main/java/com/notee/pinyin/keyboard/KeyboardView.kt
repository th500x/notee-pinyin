package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.notee.pinyin.R

/** How long a hold takes, for every surface with keys that repeat or long-press. */
internal object KeyHold {
    const val LONG_PRESS_MS = 400L
    const val REPEAT_MS = 50L
}

/**
 * Draws a [KeyGrid] and turns touches into [KeyAction]s.
 *
 * Taps act on release; a repeating key acts on press. Holding past [KeyHold.LONG_PRESS_MS] fires
 * the long-press action instead of the tap, or starts repeating every [KeyHold.REPEAT_MS]. A new
 * finger landing first releases the keys still held, so quick two-thumb typing keeps its order.
 */
class KeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        /** A finger landed on [key]; feedback goes here. */
        fun onKeyDown(key: Key)

        fun onKey(action: KeyAction, longPress: Boolean)
    }

    private class Press(val key: Key) {
        var consumed = false
    }

    var listener: Listener? = null

    /** Setting a different grid drops the keys held on the old one. */
    var grid: KeyGrid? = null
        set(value) {
            if (field === value) return
            field = value
            labelSizes = value?.keys?.map { painter.labelSize(it.label, wordSize) }?.toFloatArray() ?: FloatArray(0)
            cancelPresses()
        }

    private val painter = KeyPainter(context)
    private val wordSize = painter.dimension(R.dimen.text_key_word)
    private val images = HashMap<Int, Bitmap>()
    private var labelSizes = FloatArray(0)
    private val presses = HashMap<Int, Press>()
    private val pending = HashMap<Int, Runnable>()
    private val cell = RectF()

    override fun onDraw(canvas: Canvas) {
        val grid = grid ?: return
        val pressed = presses.values.mapTo(HashSet()) { it.key }
        grid.keys.forEachIndexed { i, key ->
            cellOf(grid, key, cell)
            painter.cap(canvas, cell, painter.capColor(key.style, key in pressed))
            val bitmap = if (key.image != 0) image(key.image) else null
            if (bitmap != null) painter.image(canvas, cell, bitmap, wordSize, painter.hintColor)
            else painter.label(canvas, cell, key.label, labelSizes[i], painter.labelColor(key.style))
            key.hint?.let { painter.hint(canvas, cell, it) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val pointer = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                releaseAll()
                keyAt(event.getX(index), event.getY(index))?.let { press(pointer, it) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(pointer)
            MotionEvent.ACTION_CANCEL -> cancelPresses()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        cancelPresses()
        super.onDetachedFromWindow()
    }

    private fun press(pointer: Int, key: Key) {
        val press = Press(key)
        presses[pointer] = press
        listener?.onKeyDown(key)
        if (key.repeats) listener?.onKey(key.action, longPress = false)
        if (key.repeats || key.longPress != null) {
            val hold = Runnable { onHeld(pointer, press) }
            pending[pointer] = hold
            postDelayed(hold, KeyHold.LONG_PRESS_MS)
        }
        invalidate()
    }

    private fun onHeld(pointer: Int, press: Press) {
        if (presses[pointer] !== press) return
        press.consumed = true
        val key = press.key
        if (key.repeats) {
            listener?.onKey(key.action, longPress = false)
            val repeat = Runnable { onHeld(pointer, press) }
            pending[pointer] = repeat
            postDelayed(repeat, KeyHold.REPEAT_MS)
        } else {
            key.longPress?.let { listener?.onKey(it, longPress = true) }
        }
    }

    private fun release(pointer: Int) {
        val press = presses.remove(pointer) ?: return
        pending.remove(pointer)?.let(::removeCallbacks)
        if (!press.consumed && !press.key.repeats) listener?.onKey(press.key.action, longPress = false)
        invalidate()
    }

    private fun releaseAll() {
        presses.keys.toList().forEach(::release)
    }

    private fun cancelPresses() {
        pending.values.forEach(::removeCallbacks)
        pending.clear()
        presses.clear()
        invalidate()
    }

    private fun keyAt(x: Float, y: Float): Key? {
        val grid = grid ?: return null
        return grid.keys.firstOrNull { key ->
            cellOf(grid, key, cell)
            cell.contains(x, y)
        }
    }

    private fun image(id: Int): Bitmap? {
        images[id]?.let { return it }
        val source = BitmapFactory.decodeResource(resources, id) ?: return null
        val mark = markOf(source)
        if (mark !== source) source.recycle()
        images[id] = mark
        return mark
    }

    /** The white calligraphy, without the blue plate. Soft edges stay. */
    private fun markOf(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val alpha = IntArray(pixels.size)
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        for (i in pixels.indices) {
            val color = pixels[i]
            if (Color.alpha(color) < 16) continue
            val lum = (Color.red(color) * 3 + Color.green(color) * 6 + Color.blue(color)) / 10
            val ink = ((lum - PLATE_LUM) * 255 / (255 - PLATE_LUM)).coerceIn(0, 255)
            if (ink == 0) continue
            alpha[i] = ink
            if (ink <= 32) continue
            val x = i % width
            val y = i / width
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        if (maxX < minX || maxY < minY) return source
        val outWidth = maxX - minX + 1
        val outHeight = maxY - minY + 1
        val out = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val from = (minY + y) * width + minX
            for (x in 0 until outWidth) {
                out[y * outWidth + x] = Color.argb(alpha[from + x], 255, 255, 255)
            }
        }
        return Bitmap.createBitmap(out, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }

    private fun cellOf(grid: KeyGrid, key: Key, out: RectF) {
        val unitX = width / grid.columns
        val unitY = height / grid.rows
        out.set(key.x * unitX, key.y * unitY, (key.x + key.width) * unitX, (key.y + key.height) * unitY)
    }

    private companion object {
        /** Luminance of the logo's blue plate. Brighter pixels are the mark. */
        const val PLATE_LUM = 140
    }
}
