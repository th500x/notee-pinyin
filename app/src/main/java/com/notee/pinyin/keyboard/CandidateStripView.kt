package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.notee.pinyin.R
import com.notee.pinyin.input.Preedit

/**
 * The composing strip: the preedit line (with tappable 9-key syllables) over one row of candidates,
 * ▼ at the right edge when they do not all fit. A null label is the `|`.
 *
 * As a header it draws only the preedit and the arrow; the toolbar underneath shows through and
 * gets every touch this view does not take. As an answer it shows one arithmetic result, cut at the
 * front when too long so the value stays in view.
 */
class CandidateStripView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onCandidate(index: Int)

        fun onSyllableChoice(index: Int)

        fun onToggleGrid()
    }

    private enum class Mode { CANDIDATES, ANSWER, HEADER }

    var listener: Listener? = null

    private var preedit: Preedit? = null
    private var labels: List<String?> = emptyList()
    private var mode = Mode.CANDIDATES
    private var expanded = false

    private val painter = KeyPainter(context)
    private val candidateSize = painter.dimension(R.dimen.text_candidate)
    private val preeditSize = painter.dimension(R.dimen.text_preedit)
    private val preeditHeight = painter.dimension(R.dimen.preedit_height)
    private val itemPadding = painter.dimension(R.dimen.strip_item_padding)
    private val arrowWidth = painter.dimension(R.dimen.strip_arrow_width)
    private val density = resources.displayMetrics.density

    private val itemRects = ArrayList<RectF>()
    private val choiceRects = ArrayList<RectF>()
    private val arrowRect = RectF()
    private var hasArrow = false
    private var preeditX = 0f
    private var preeditEnd = 0f
    private var pressed: Target? = null

    private sealed interface Target {
        data class Candidate(val index: Int) : Target
        data class Choice(val index: Int) : Target
        data object Arrow : Target
    }

    fun showCandidates(preedit: Preedit?, labels: List<String?>) = show(preedit, labels, Mode.CANDIDATES, expanded = false)

    fun showAnswer(answer: String) = show(null, listOf(answer), Mode.ANSWER, expanded = false)

    /** ▲ when the grid is open, ▼ otherwise. */
    fun showHeader(preedit: Preedit, expanded: Boolean) = show(preedit, emptyList(), Mode.HEADER, expanded)

    private fun show(preedit: Preedit?, labels: List<String?>, mode: Mode, expanded: Boolean) {
        this.preedit = preedit
        this.labels = labels
        this.mode = mode
        this.expanded = expanded
        pressed = null
        arrange()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        arrange()
    }

    private val rowTop: Float get() = if (preedit != null) preeditHeight else 0f

    private fun arrange() {
        itemRects.clear()
        val widths = labels.map { label -> if (label == null) BAR_WIDTH_DP * density else painter.measure(label, candidateSize) + 2 * itemPadding }
        val start = START_DP * density
        hasArrow = mode == Mode.HEADER || (mode == Mode.CANDIDATES && start + widths.sum() > width)
        val room = if (hasArrow) width - arrowWidth else width.toFloat()
        var x = start
        for ((i, w) in widths.withIndex()) {
            if (i > 0 && x + w > room) break
            val right = minOf(x + w, room)
            itemRects += RectF(x, rowTop, right, height.toFloat())
            x = right
        }
        arrowRect.set(width - arrowWidth, rowTop, width.toFloat(), height.toFloat())
        arrangePreedit()
    }

    /**
     * The text gets what the choices leave, and at least [MIN_TEXT_SHARE] of the line: choices that
     * do not fit are left off the end. Text longer than its room keeps its newest letters in view.
     */
    private fun arrangePreedit() {
        choiceRects.clear()
        val preedit = preedit ?: return
        val start = START_DP * density
        val gap = CHOICE_GAP_DP * density
        val widths = preedit.choices.map { painter.measure(it.spelling, preeditSize) + 2 * CHOICE_PADDING_DP * density }
        val line = width - 2 * start
        var shown = widths.size
        while (shown > 0 && widths.take(shown).sumOf { (it + gap).toDouble() } > line * (1 - MIN_TEXT_SHARE)) shown--
        val room = line - widths.take(shown).sumOf { (it + gap).toDouble() }.toFloat()
        val whole = painter.measure(preedit.chosen + preedit.pinyin, preeditSize)
        preeditEnd = start + minOf(whole, room)
        preeditX = preeditEnd - whole
        var x = preeditEnd + gap
        for (i in 0 until shown) {
            choiceRects += RectF(x, 0f, x + widths[i], preeditHeight)
            x += widths[i] + gap
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawPreedit(canvas)
        val cut = if (mode == Mode.ANSWER) TextUtils.TruncateAt.START else TextUtils.TruncateAt.END
        itemRects.forEachIndexed { i, rect ->
            val label = labels[i]
            if (pressed == Target.Candidate(i)) painter.cap(canvas, rect, painter.keyPressedColor)
            if (label == null) {
                painter.label(canvas, rect, "|", candidateSize, painter.hintColor)
            } else {
                painter.label(canvas, rect, label, candidateSize, painter.textColor, cut)
            }
        }
        if (hasArrow) {
            if (pressed == Target.Arrow) painter.cap(canvas, arrowRect, painter.keyPressedColor)
            painter.line(canvas, arrowRect.left, arrowRect.top + arrowRect.height() / 4, arrowRect.bottom - arrowRect.height() / 4)
            painter.label(canvas, arrowRect, if (expanded) "▲" else "▼", preeditSize, painter.textColor)
        }
    }

    private fun drawPreedit(canvas: Canvas) {
        val preedit = preedit ?: return
        val text = painter.text
        text.textSize = preeditSize
        text.textAlign = Paint.Align.LEFT
        val baseline = preeditHeight / 2 - (text.descent() + text.ascent()) / 2
        canvas.save()
        canvas.clipRect(0f, 0f, preeditEnd, preeditHeight)
        var x = preeditX
        text.color = painter.textColor
        canvas.drawText(preedit.chosen, x, baseline, text)
        x += text.measureText(preedit.chosen)
        text.color = painter.accentColor
        canvas.drawText(preedit.pinyin, x, baseline, text)
        canvas.restore()
        text.textAlign = Paint.Align.CENTER
        choiceRects.forEachIndexed { i, rect ->
            painter.cap(canvas, rect, if (pressed == Target.Choice(i)) painter.keyPressedColor else painter.activeColor)
            painter.label(canvas, rect, preedit.choices[i].spelling, preeditSize, painter.textColor)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = targetAt(event.x, event.y) ?: return false
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val target = pressed.takeIf { it == targetAt(event.x, event.y) }
                pressed = null
                invalidate()
                when (target) {
                    is Target.Candidate -> listener?.onCandidate(target.index)
                    is Target.Choice -> listener?.onSyllableChoice(target.index)
                    Target.Arrow -> listener?.onToggleGrid()
                    null -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
            }
        }
        return true
    }

    private fun targetAt(x: Float, y: Float): Target? {
        choiceRects.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { return Target.Choice(it) }
        if (y < rowTop) return null
        if (hasArrow && arrowRect.contains(x, y)) return Target.Arrow
        val index = itemRects.indexOfFirst { x >= it.left && x < it.right }
        return if (index >= 0 && labels[index] != null) Target.Candidate(index) else null
    }

    private companion object {
        const val START_DP = 8f
        const val BAR_WIDTH_DP = 18f
        const val CHOICE_PADDING_DP = 8f
        const val CHOICE_GAP_DP = 6f

        /** Part of the preedit line kept for the text when 9-key choices crowd it. */
        const val MIN_TEXT_SHARE = 0.3f
    }
}
