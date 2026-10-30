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
        /** Same size as [label], with a wider gap. */
        val detail: String? = null,
        /** A slider after [label]. Null means this button has no switch. */
        val switchOn: Boolean? = null,
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

    /**
     * The main toolbar reserves a `|` and a hide triangle at the right, in the same columns as
     * the candidate row's `|` and ▼. Tool rows and emoji tabs leave this off and stay equal-width.
     */
    var showsEnd: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The hide triangle. Not one of [buttons], and it does not play a key sound. */
    var onEnd: (() -> Unit)? = null

    /**
     * When set, every label in this row starts where a centered [alignToLabel] plus its slider
     * would start. Tool rows share the top row's left edge instead of centering on their own.
     */
    var alignToLabel: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val painter = KeyPainter(context)
    private val textSize = painter.dimension(R.dimen.text_button)
    private val barTextSize = painter.dimension(R.dimen.text_candidate)
    private val barWidth = painter.dimension(R.dimen.strip_bar_width)
    private val arrowWidth = painter.dimension(R.dimen.strip_arrow_width)
    private var labelSizes: List<Float> = emptyList()
    private val cell = RectF()
    private val endRect = RectF()
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
            val switchOn = button.switchOn
            val at = contentLeft(cell)
            when {
                switchOn != null -> painter.labelWithSwitch(canvas, cell, button.label, labelSizes[i], color, switchOn, at)
                mark != null -> painter.labelWithMark(canvas, cell, button.label, mark, labelSizes[i], color, at)
                detail != null -> painter.labelWithDetail(canvas, cell, button.label, detail, labelSizes[i], color)
                else -> painter.label(canvas, cell, button.label, labelSizes[i], color, at = at)
            }
        }
        if (!showsEnd) return
        endBounds(bar = true, endRect)
        painter.label(canvas, endRect, "|", barTextSize, painter.hintColor)
        endBounds(bar = false, endRect)
        if (pressed == END) painter.cap(canvas, endRect, painter.keyPressedColor)
        painter.outlineTriangle(canvas, endRect, painter.textColor)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = indexAt(event.x, event.y)
                pressed = when {
                    index == END -> END
                    buttons.getOrNull(index)?.enabled == true -> index
                    else -> -1
                }
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
                val index = indexAt(event.x, event.y)
                val tapped = pressed
                release()
                when {
                    tapped == END && index == END -> onEnd?.invoke()
                    tapped >= 0 && index == tapped && !buttons[tapped].repeats -> onTap?.invoke(tapped)
                }
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

    /** Buttons share what the `|` and the hide triangle leave. Without [showsEnd], the whole row. */
    private val buttonSpan: Float
        get() {
            val inner = (width - paddingLeft - paddingRight).toFloat()
            val usable = if (showsEnd) inner - barWidth - arrowWidth else inner
            return usable / buttons.size.coerceAtLeast(1)
        }

    private fun endBounds(bar: Boolean, out: RectF) {
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val right = (width - paddingRight).toFloat()
        val arrowLeft = right - arrowWidth
        if (bar) out.set(arrowLeft - barWidth, top, arrowLeft, bottom) else out.set(arrowLeft, top, right, bottom)
    }

    private fun indexAt(x: Float, y: Float): Int {
        if (y < paddingTop || y >= height - paddingBottom) return -1
        if (showsEnd) {
            endBounds(bar = false, cell)
            if (cell.contains(x, y)) return END
            endBounds(bar = true, cell)
            if (cell.contains(x, y)) return -1
        }
        if (buttons.isEmpty() || buttonSpan <= 0f) return -1
        if (x < paddingLeft || x >= width - paddingRight) return -1
        val index = ((x - paddingLeft) / buttonSpan).toInt()
        if (index !in buttons.indices || buttons[index].blank) return -1
        return index
    }

    /** A 3:4 sample, as tall as the cap allows. Centered, unless the row shares a left edge. */
    private fun drawImage(canvas: Canvas, cell: RectF, image: Bitmap) {
        val inset = cell.height() * 0.14f
        val h = cell.height() - 2 * inset
        val w = h * 3f / 4f
        val at = contentLeft(cell)
        val left = if (at != null) at.coerceAtMost(cell.right - w) else cell.centerX() - w / 2f
        imageRect.set(left, cell.centerY() - h / 2f, left + w, cell.centerY() + h / 2f)
        canvas.drawBitmap(image, null, imageRect, imagePaint)
    }

    /** Left edge shared with a centered [alignToLabel] and its slider. Null keeps the old centering. */
    private fun contentLeft(cell: RectF): Float? {
        val anchor = alignToLabel ?: return null
        if (cell.width() <= 0f) return null
        val textW = painter.measure(anchor, textSize)
        val switchW = painter.dimension(R.dimen.switch_width)
        val gap = painter.dimension(R.dimen.switch_gap)
        val pad = ((cell.width() - textW - gap - switchW) / 2f).coerceAtLeast(gap)
        return cell.left + pad
    }

    private fun cellOf(index: Int, out: RectF) {
        val left = paddingLeft + index * buttonSpan
        out.set(left, paddingTop.toFloat(), left + buttonSpan, (height - paddingBottom).toFloat())
    }

    private companion object {
        /** Not a [buttons] index. The hide triangle. */
        const val END = -2
    }
}
