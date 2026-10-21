package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.notee.pinyin.R

/** A row of equal-width text buttons inside the padding: the toolbar, the emoji tabs, the tool rows. */
class ButtonRowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** A [repeats] button taps on press and keeps tapping while held, like the ⌫ key. */
    data class Button(
        val label: String,
        val selected: Boolean = false,
        val enabled: Boolean = true,
        val repeats: Boolean = false,
        /** Drawn smaller, after [label]. The festival hint on 工具. */
        val mark: String? = null,
        /** Same size as [label], with a wider gap. The 开/关 after 按键音. */
        val detail: String? = null,
        /** Takes a slot and draws nothing, so the other button can sit in the left half. */
        val blank: Boolean = false,
        /** Overrides the row's text size. Festival rows use the emoji size. */
        val textSize: Float? = null,
        /** Drawn even when the button is resting. Festival names use a gray cap and stay disabled. */
        val background: Int? = null,
        /** A picture in place of [label]. Festival rows that send a card show a sample of it. */
        val image: Bitmap? = null,
    )

    /** An equal list keeps the button that is being held. */
    var buttons: List<Button> = emptyList()
        set(value) {
            if (value == field) return
            field = value
            labelSizes = value.map { it.textSize ?: painter.labelSize(it.label, textSize) }
            release()
        }

    var onTap: ((index: Int) -> Unit)? = null

    /** A finger landed on an enabled button; feedback goes here. */
    var onPress: ((index: Int) -> Unit)? = null

    private val painter = KeyPainter(context)
    private val textSize = painter.dimension(R.dimen.text_button)
    private var labelSizes: List<Float> = emptyList()
    private val cell = RectF()
    private val imageRect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var pressed = -1

    private val repeat = object : Runnable {
        override fun run() {
            if (pressed < 0) return
            onTap?.invoke(pressed)
            if (pressed >= 0) postDelayed(this, KeyHold.REPEAT_MS)
        }
    }

    override fun onDraw(canvas: Canvas) {
        buttons.forEachIndexed { i, button ->
            if (button.blank) return@forEachIndexed
            cellOf(i, cell)
            when {
                i == pressed -> painter.cap(canvas, cell, painter.keyPressedColor)
                button.background != null -> painter.cap(canvas, cell, button.background)
                button.selected -> painter.cap(canvas, cell, painter.activeColor)
            }
            val image = button.image
            if (image != null) {
                drawImage(canvas, cell, image)
                return@forEachIndexed
            }
            val color = if (button.enabled) painter.textColor else painter.hintColor
            val mark = button.mark
            val detail = button.detail
            when {
                mark != null -> painter.labelWithMark(canvas, cell, button.label, mark, labelSizes[i], color)
                detail != null -> painter.labelWithDetail(canvas, cell, button.label, detail, labelSizes[i], color)
                else -> painter.label(canvas, cell, button.label, labelSizes[i], color)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = indexAt(event.x, event.y)
                pressed = if (buttons.getOrNull(index)?.enabled == true) index else -1
                invalidate()
                if (pressed >= 0) {
                    onPress?.invoke(pressed)
                    if (buttons[pressed].repeats) {
                        onTap?.invoke(pressed)
                        postDelayed(repeat, KeyHold.LONG_PRESS_MS)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val tapped = pressed.takeIf { it >= 0 && !buttons[it].repeats && it == indexAt(event.x, event.y) }
                release()
                tapped?.let { onTap?.invoke(it) }
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun release() {
        removeCallbacks(repeat)
        pressed = -1
        invalidate()
    }

    private val cellWidth: Float get() = (width - paddingLeft - paddingRight).toFloat() / buttons.size

    private fun indexAt(x: Float, y: Float): Int {
        if (buttons.isEmpty() || y < paddingTop || y >= height - paddingBottom) return -1
        if (x < paddingLeft || x >= width - paddingRight) return -1
        val index = ((x - paddingLeft) / cellWidth).toInt().coerceIn(0, buttons.lastIndex)
        if (buttons[index].blank) return -1
        return index
    }

    /** A 3:4 sample, as tall as the cap allows, centered in the cell. */
    private fun drawImage(canvas: Canvas, cell: RectF, image: Bitmap) {
        val inset = cell.height() * 0.14f
        val h = cell.height() - 2 * inset
        val w = h * 3f / 4f
        imageRect.set(cell.centerX() - w / 2f, cell.centerY() - h / 2f, cell.centerX() + w / 2f, cell.centerY() + h / 2f)
        canvas.drawBitmap(image, null, imageRect, imagePaint)
    }

    private fun cellOf(index: Int, out: RectF) {
        val left = paddingLeft + index * cellWidth
        out.set(left, paddingTop.toFloat(), left + cellWidth, (height - paddingBottom).toFloat())
    }
}
