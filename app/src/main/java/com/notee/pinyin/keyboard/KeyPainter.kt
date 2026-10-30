package com.notee.pinyin.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    val boardColor = context.getColor(R.color.keyboard_background)
    private val switchOnColor = context.getColor(R.color.switch_on)
    private val switchOffColor = context.getColor(R.color.switch_off)
    private val switchThumbColor = context.getColor(R.color.switch_thumb)

    private val gap = resources.getDimension(R.dimen.key_gap)
    private val radius = resources.getDimension(R.dimen.key_radius)
    private val hintSize = resources.getDimension(R.dimen.text_key_hint)
    private val glyphSize = resources.getDimension(R.dimen.text_key)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val triangle = Path()
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val inset = RectF()
    private val imageRect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun dimension(id: Int): Float = resources.getDimension(id)

    /** Letters, symbols and emoji get the big key size; words like 回车 or ABC keep [wordSize]. */
    fun labelSize(label: String, wordSize: Float): Float = if (isSingleGlyph(label)) glyphSize else wordSize

    /** Rounded cap inside [cell], leaving half a gap on every side. */
    fun cap(canvas: Canvas, cell: RectF, color: Int) {
        inset.set(cell.left + gap / 2, cell.top + gap / 2, cell.right - gap / 2, cell.bottom - gap / 2)
        fill.color = color
        canvas.drawRoundRect(inset, radius, radius, fill)
    }

    /** [label] centered in [cell], or starting at [at] when that edge is set. Cut with … at [cut] when wider than the cell. */
    fun label(
        canvas: Canvas,
        cell: RectF,
        label: String,
        size: Float,
        color: Int,
        cut: TextUtils.TruncateAt = TextUtils.TruncateAt.END,
        at: Float? = null,
    ) {
        text.color = color
        val room = if (at != null) (cell.right - at - gap).coerceAtLeast(0f) else cell.width() - 2 * gap
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
        if (at == null) {
            canvas.drawText(shown, cell.centerX(), baseline, text)
        } else {
            val saved = text.textAlign
            text.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText(shown, at, baseline, text)
            text.textAlign = saved
        }
    }

    /**
     * [label] and a slider. On is a red track with the thumb at the right.
     * Off is a gray track with the thumb at the left (docs/01-1 §1).
     * [at] is the label's left edge; without it the pair is centered in [cell].
     */
    fun labelWithSwitch(
        canvas: Canvas,
        cell: RectF,
        label: String,
        size: Float,
        color: Int,
        on: Boolean,
        at: Float? = null,
    ) {
        val trackW = resources.getDimension(R.dimen.switch_width)
        val trackH = resources.getDimension(R.dimen.switch_height)
        val gap = resources.getDimension(R.dimen.switch_gap)
        text.color = color
        text.textSize = size
        var labelWidth = text.measureText(label)
        var drawSize = size
        var drawTrackW = trackW
        var drawTrackH = trackH
        var drawGap = gap
        val room = if (at != null) (cell.right - at - this.gap).coerceAtLeast(0f) else cell.width() - 2 * this.gap
        val total = labelWidth + gap + trackW
        if (room > 0f && total > room) {
            val scale = room / total
            drawSize *= scale
            labelWidth *= scale
            drawTrackW *= scale
            drawTrackH *= scale
            drawGap *= scale
        }
        text.textSize = drawSize
        val baseline = cell.centerY() - (text.descent() + text.ascent()) / 2
        val left = at ?: (cell.centerX() - (labelWidth + drawGap + drawTrackW) / 2)
        val saved = text.textAlign
        text.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(label, left, baseline, text)
        text.textAlign = saved

        val trackLeft = left + labelWidth + drawGap
        val trackTop = cell.centerY() - drawTrackH / 2f
        inset.set(trackLeft, trackTop, trackLeft + drawTrackW, trackTop + drawTrackH)
        fill.color = if (on) switchOnColor else switchOffColor
        canvas.drawRoundRect(inset, drawTrackH / 2f, drawTrackH / 2f, fill)
        val pad = drawTrackH * 0.12f
        val diameter = drawTrackH - 2f * pad
        val thumbLeft = if (on) inset.right - pad - diameter else inset.left + pad
        val thumbTop = inset.top + pad
        inset.set(thumbLeft, thumbTop, thumbLeft + diameter, thumbTop + diameter)
        fill.color = switchThumbColor
        canvas.drawRoundRect(inset, diameter / 2f, diameter / 2f, fill)
    }

    /**
     * [label] and [detail] at the same size, centered as a pair.
     * The gap is a little wider than one ideographic space.
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

    /** [label] at [size], then [mark] smaller. [at] is the left edge; without it the pair is centered. */
    fun labelWithMark(
        canvas: Canvas,
        cell: RectF,
        label: String,
        mark: String,
        size: Float,
        color: Int,
        at: Float? = null,
    ) {
        text.color = color
        var drawSize = size
        var markSize = size * MARK_SCALE
        text.textSize = drawSize
        var labelWidth = text.measureText(label)
        text.textSize = markSize
        var markWidth = text.measureText(mark)
        var space = drawSize * 0.08f
        val room = if (at != null) (cell.right - at - gap).coerceAtLeast(0f) else cell.width() - 2 * gap
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
        val left = at ?: (cell.centerX() - (labelWidth + space + markWidth) / 2)
        val saved = text.textAlign
        text.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(label, left, baseline, text)
        text.textSize = markSize
        canvas.drawText(mark, left + labelWidth + space, baseline, text)
        text.textAlign = saved
    }

    /**
     * [bitmap] centered at [height], the same em the letter keys use. [color] tints it, so the
     * 9-key mark can sit in the key gray instead of the brand blue.
     */
    fun image(canvas: Canvas, cell: RectF, bitmap: Bitmap, height: Float, color: Int) {
        if (bitmap.height <= 0 || height <= 0f) return
        val width = height * bitmap.width / bitmap.height
        val left = cell.centerX() - width / 2f
        val top = cell.centerY() - height / 2f
        imageRect.set(left, top, left + width, top + height)
        imagePaint.colorFilter = android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
    }

    /** Small text in the upper right corner: what a long press gives. */
    fun hint(canvas: Canvas, cell: RectF, hint: String) {
        text.textSize = hintSize
        text.color = hintColor
        val x = cell.right - gap - text.measureText(hint) / 2 - gap
        canvas.drawText(hint, x, cell.top + gap + hintSize, text)
    }

    /** A solid rectangle. The candidate arrow uses it so the hide glyph underneath does not show through. */
    fun fillRect(canvas: Canvas, cell: RectF, color: Int) {
        fill.color = color
        canvas.drawRect(cell, fill)
    }

    /**
     * A down-pointing triangle drawn as a stroke, not the filled ▼ of the candidate row.
     * The toolbar's hide control uses it so the two stay easy to tell apart.
     */
    fun outlineTriangle(canvas: Canvas, cell: RectF, color: Int) {
        val cx = cell.centerX()
        val cy = cell.centerY()
        val halfH = minOf(cell.height() * 0.2f, cell.width() * 0.18f).coerceAtLeast(1f)
        val halfW = halfH * 1.2f
        triangle.rewind()
        triangle.moveTo(cx - halfW, cy - halfH * 0.55f)
        triangle.lineTo(cx + halfW, cy - halfH * 0.55f)
        triangle.lineTo(cx, cy + halfH * 0.85f)
        triangle.close()
        stroke.color = color
        stroke.strokeWidth = resources.displayMetrics.density * 1.5f
        canvas.drawPath(triangle, stroke)
    }

    /** A horizontal rule across [cell], less a gap at each end, through its middle. */
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

        /** Space between a label and its detail, in ems. A little more than one ideographic space. */
        const val DETAIL_GAP = 1.6f

        fun isSingleGlyph(label: String): Boolean {
            val graphemes = BreakIterator.getCharacterInstance()
            graphemes.setText(label)
            graphemes.first()
            return graphemes.next() == label.length
        }
    }
}
