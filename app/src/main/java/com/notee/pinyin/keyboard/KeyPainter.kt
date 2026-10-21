package com.notee.pinyin.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.icu.text.BreakIterator
import android.text.TextPaint
import android.text.TextUtils
import com.notee.pinyin.R

/**
 * Shared drawing for every keyboard surface: key caps, candidate cells, emoji cells, buttons.
 * Colors are read once; the views are rebuilt when the system switches light and dark.
 */
class KeyPainter(context: Context) {
    private val resources = context.resources

    val keyColor = context.getColor(R.color.key_background)
    val keyPressedColor = context.getColor(R.color.key_pressed)
    val functionColor = context.getColor(R.color.key_function)
    val functionPressedColor = context.getColor(R.color.key_function_pressed)
    val activeColor = context.getColor(R.color.key_active)
    val deleteColor = context.getColor(R.color.key_delete)
    val deletePressedColor = context.getColor(R.color.key_delete_pressed)
    val textColor = context.getColor(R.color.key_text)
    val hintColor = context.getColor(R.color.key_hint)
    val deleteTextColor = context.getColor(R.color.key_delete_text)
    val accentColor = context.getColor(R.color.preedit_text)
    val dividerColor = context.getColor(R.color.strip_divider)

    private val gap = resources.getDimension(R.dimen.key_gap)
    private val radius = resources.getDimension(R.dimen.key_radius)
    private val hintSize = resources.getDimension(R.dimen.text_key_hint)
    private val glyphSize = resources.getDimension(R.dimen.text_key)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val inset = RectF()

    fun dimension(id: Int): Float = resources.getDimension(id)

    /** Letters, symbols and emoji get the big key size; words like 回车 or ABC keep [wordSize]. */
    fun labelSize(label: String, wordSize: Float): Float = if (isSingleGlyph(label)) glyphSize else wordSize

    /** Rounded cap inside [cell], leaving half a gap on every side. */
    fun cap(canvas: Canvas, cell: RectF, color: Int) {
        inset.set(cell.left + gap / 2, cell.top + gap / 2, cell.right - gap / 2, cell.bottom - gap / 2)
        fill.color = color
        canvas.drawRoundRect(inset, radius, radius, fill)
    }

    /** [label] centered in [cell], cut with … at [cut] when wider than the cell. */
    fun label(
        canvas: Canvas,
        cell: RectF,
        label: String,
        size: Float,
        color: Int,
        cut: TextUtils.TruncateAt = TextUtils.TruncateAt.END,
    ) {
        text.color = color
        val room = cell.width() - 2 * gap
        // Six ASCII dots are the Chinese ellipsis. Shrinking keeps every dot; ellipsizing would
        // swap the tail for …, and this phone's font draws that character as two dots.
        var drawSize = size
        if (room > 0f && label.length > 1 && label.all { it == '.' }) {
            text.textSize = size
            val width = text.measureText(label)
            if (width > room) drawSize = size * room / width
        }
        text.textSize = drawSize
        val shown = if (text.measureText(label) <= room) label else TextUtils.ellipsize(label, text, room, cut).toString()
        val baseline = cell.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(shown, cell.centerX(), baseline, text)
    }

    /**
     * [label] and [detail] at the same size, centered as a pair.
     * The gap is a little wider than one ideographic space, which is what the sound switch uses.
     */
    fun labelWithDetail(canvas: Canvas, cell: RectF, label: String, detail: String, size: Float, color: Int) {
        text.color = color
        text.textSize = size
        var labelWidth = text.measureText(label)
        var detailWidth = text.measureText(detail)
        var gap = size * DETAIL_GAP
        val room = cell.width() - 2 * this.gap
        val total = labelWidth + gap + detailWidth
        var drawSize = size
        if (room > 0f && total > room) {
            val scale = room / total
            drawSize *= scale
            labelWidth *= scale
            detailWidth *= scale
            gap *= scale
        }
        text.textSize = drawSize
        val baseline = cell.centerY() - (text.descent() + text.ascent()) / 2
        val left = cell.centerX() - (labelWidth + gap + detailWidth) / 2
        val saved = text.textAlign
        text.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(label, left, baseline, text)
        canvas.drawText(detail, left + labelWidth + gap, baseline, text)
        text.textAlign = saved
    }

    /** [label] at [size], then [mark] smaller, the pair centered on the same baseline. */
    fun labelWithMark(canvas: Canvas, cell: RectF, label: String, mark: String, size: Float, color: Int) {
        text.color = color
        var drawSize = size
        var markSize = size * MARK_SCALE
        text.textSize = drawSize
        var labelWidth = text.measureText(label)
        text.textSize = markSize
        var markWidth = text.measureText(mark)
        var space = drawSize * 0.08f
        val room = cell.width() - 2 * gap
        val total = labelWidth + space + markWidth
        if (room > 0f && total > room) {
            val scale = room / total
            drawSize *= scale
            markSize *= scale
            labelWidth *= scale
            markWidth *= scale
            space *= scale
        }
        text.textSize = drawSize
        val baseline = cell.centerY() - (text.descent() + text.ascent()) / 2
        val left = cell.centerX() - (labelWidth + space + markWidth) / 2
        val saved = text.textAlign
        text.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(label, left, baseline, text)
        text.textSize = markSize
        canvas.drawText(mark, left + labelWidth + space, baseline, text)
        text.textAlign = saved
    }

    /** Small text in the upper right corner: what a long press gives. */
    fun hint(canvas: Canvas, cell: RectF, hint: String) {
        text.textSize = hintSize
        text.color = hintColor
        val x = cell.right - gap - text.measureText(hint) / 2 - gap
        canvas.drawText(hint, x, cell.top + gap + hintSize, text)
    }

    fun line(canvas: Canvas, x: Float, top: Float, bottom: Float) {
        fill.color = dividerColor
        canvas.drawRect(x, top, x + resources.displayMetrics.density, bottom, fill)
    }

    /** The horizontal [line]: across [cell] less a gap at each end, through its middle. */
    fun rule(canvas: Canvas, cell: RectF) {
        fill.color = dividerColor
        val y = cell.centerY()
        canvas.drawRect(cell.left + gap, y, cell.right - gap, y + resources.displayMetrics.density, fill)
    }

    fun measure(label: String, size: Float): Float {
        text.textSize = size
        return text.measureText(label)
    }

    fun capColor(style: KeyStyle, pressed: Boolean): Int = when (style) {
        KeyStyle.NORMAL, KeyStyle.PLACEHOLDER -> if (pressed) keyPressedColor else keyColor
        KeyStyle.FUNCTION, KeyStyle.DISABLED -> if (pressed) functionPressedColor else functionColor
        KeyStyle.ACTIVE -> if (pressed) functionPressedColor else activeColor
        KeyStyle.DELETE -> if (pressed) deletePressedColor else deleteColor
    }

    fun labelColor(style: KeyStyle): Int = when (style) {
        KeyStyle.DELETE -> deleteTextColor
        KeyStyle.PLACEHOLDER, KeyStyle.DISABLED -> hintColor
        else -> textColor
    }

    private companion object {
        /** Festival dates and the ✨ after 工具. Smaller than the label, same baseline. */
        const val MARK_SCALE = 0.62f

        /** Space between 按键音 and 开/关, in ems. A little more than one ideographic space. */
        const val DETAIL_GAP = 1.6f

        fun isSingleGlyph(label: String): Boolean {
            val graphemes = BreakIterator.getCharacterInstance()
            graphemes.setText(label)
            graphemes.first()
            return graphemes.next() == label.length
        }
    }
}
