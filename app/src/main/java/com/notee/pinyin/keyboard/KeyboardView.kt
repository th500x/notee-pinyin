package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
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
            painter.label(canvas, cell, key.label, labelSizes[i], painter.labelColor(key.style))
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

    private fun cellOf(grid: KeyGrid, key: Key, out: RectF) {
        val unitX = width / grid.columns
        val unitY = height / grid.rows
        out.set(key.x * unitX, key.y * unitY, (key.x + key.width) * unitX, (key.y + key.height) * unitY)
    }
}
