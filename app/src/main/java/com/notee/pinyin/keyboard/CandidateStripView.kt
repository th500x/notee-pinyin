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
import android.view.ViewConfiguration
import com.notee.pinyin.R
import com.notee.pinyin.input.Preedit
import kotlin.math.hypot
import kotlin.math.min

/**
 * The composing strip: the preedit line (with tappable 9-key syllables) over one row of candidates,
 * ▼ at the right edge when they do not all fit. A null label is the `|` between pinned characters.
 *
 * Syllable buttons keep going while the line has room. What still does not fit gets a ▼ on the
 * preedit line, in the same column as the candidate ▼. An English suggestion row keeps an Emoji
 * button where the toolbar's Emoji button sits, to the left of that ▼. A wide gap that cannot
 * hold the next word tightens this row's inset, then its text size (docs/01-1 §2).
 *
 * As a header it draws the preedit and the arrows; the toolbar underneath shows through and gets
 * every touch this view does not take. The candidate arrow paints the keyboard background so it
 * covers the toolbar's hide glyph. As an answer it shows one arithmetic result, cut at the front
 * when too long so the value stays in view.
 */
class CandidateStripView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onCandidate(index: Int)

        /** A long press on a word the user has learned. The tap is not also reported. */
        fun onForgetCandidate(index: Int)

        fun onSyllableChoice(index: Int)

        fun onToggleGrid()

        /** The preedit line's ▼: every 9-key reading, not the word grid. */
        fun onToggleSyllables()

        fun onEmoji()
    }

    private enum class Mode { CANDIDATES, ANSWER, HEADER }

    var listener: Listener? = null

    private var preedit: Preedit? = null
    private var labels: List<String?> = emptyList()
    private var learned: List<Boolean> = emptyList()
    private var mode = Mode.CANDIDATES
    private var wordsExpanded = false
    private var syllablesExpanded = false
    private var englishEmoji = false

    private val painter = KeyPainter(context)
    private val candidateSize = painter.dimension(R.dimen.text_candidate)
    private val preeditSize = painter.dimension(R.dimen.text_preedit)
    private val buttonSize = painter.dimension(R.dimen.text_button)
    private val preeditHeight = painter.dimension(R.dimen.preedit_height)
    private val itemPadding = painter.dimension(R.dimen.strip_item_padding)
    private val minItemPadding = painter.dimension(R.dimen.strip_item_padding_min)
    private val largeHole = painter.dimension(R.dimen.strip_item_hole)
    private val minTextScale =
        (painter.dimension(R.dimen.text_candidate_min) / candidateSize).coerceIn(0.5f, 1f)
    private val arrowWidth = painter.dimension(R.dimen.strip_arrow_width)
    private val barWidth = painter.dimension(R.dimen.strip_bar_width)
    private val emojiLabel = context.getString(R.string.toolbar_emoji)
    private val density = resources.displayMetrics.density

    private val itemRects = ArrayList<RectF>()
    private val choiceRects = ArrayList<RectF>()
    private val arrowRect = RectF()
    private val barRect = RectF()
    private val syllableArrowRect = RectF()
    private val emojiRect = RectF()
    private var hasArrow = false
    private var hasSyllableArrow = false
    private var hasEmoji = false
    private var rowTextSize = candidateSize
    private var preeditX = 0f
    private var preeditEnd = 0f
    private var pressed: Target? = null
    private var downX = 0f
    private var downY = 0f
    private var forgot = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPress = Runnable {
        val candidate = pressed as? Target.Candidate ?: return@Runnable
        if (learned.getOrNull(candidate.index) != true) return@Runnable
        forgot = true
        pressed = null
        invalidate()
        listener?.onForgetCandidate(candidate.index)
    }

    private sealed interface Target {
        data class Candidate(val index: Int) : Target
        data class Choice(val index: Int) : Target
        data object Arrow : Target
        data object SyllableArrow : Target
        data object Emoji : Target
    }

    fun showCandidates(
        preedit: Preedit?,
        labels: List<String?>,
        syllablesExpanded: Boolean = false,
        emoji: Boolean = false,
        learned: List<Boolean> = emptyList(),
    ) = show(preedit, labels, Mode.CANDIDATES, wordsExpanded = false, syllablesExpanded, emoji, learned)

    fun showAnswer(answer: String) = show(null, listOf(answer), Mode.ANSWER, wordsExpanded = false, syllablesExpanded = false, emoji = false)

    /** ▲ when that grid is open, ▼ otherwise. */
    fun showHeader(preedit: Preedit, wordsExpanded: Boolean, syllablesExpanded: Boolean) =
        show(preedit, emptyList(), Mode.HEADER, wordsExpanded, syllablesExpanded, emoji = false)

    private fun show(
        preedit: Preedit?,
        labels: List<String?>,
        mode: Mode,
        wordsExpanded: Boolean,
        syllablesExpanded: Boolean,
        emoji: Boolean,
        learned: List<Boolean> = emptyList(),
    ) {
        this.preedit = preedit
        this.labels = labels
        this.learned = learned
        this.mode = mode
        this.wordsExpanded = wordsExpanded
        this.syllablesExpanded = syllablesExpanded
        this.englishEmoji = emoji
        removeCallbacks(longPress)
        pressed = null
        arrange()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        arrange()
    }

    private val rowTop: Float get() = if (preedit != null) preeditHeight else 0f

    private fun toolButtonWidth(): Float = (width - barWidth - arrowWidth).coerceAtLeast(0f) / TOOL_BUTTONS

    private fun toolButtonLeft(index: Int): Float = index * toolButtonWidth()

    private fun arrange() {
        itemRects.clear()
        hasEmoji = englishEmoji && mode == Mode.CANDIDATES
        rowTextSize = candidateSize
        val start = START_DP * density
        val emojiLeft = if (hasEmoji) toolButtonLeft(EMOJI_INDEX) else width.toFloat()
        val naturalRoom = if (hasEmoji) emojiLeft else width.toFloat()
        val cores = labels.map { label ->
            if (label == null) barWidth else painter.measure(label, candidateSize)
        }
        val bars = labels.map { it == null }
        val plan = if (mode == Mode.CANDIDATES && width > 0 && cores.size > 1) {
            CandidateRow.fit(
                cores, bars, naturalRoom, start,
                itemPadding, minItemPadding, largeHole, minTextScale,
            )
        } else {
            CandidateRow.Fit(itemPadding, 1f)
        }
        val overflows = mode == Mode.CANDIDATES && start + span(cores, bars, plan) > naturalRoom
        hasArrow = mode == Mode.HEADER || overflows
        val room = when {
            hasEmoji -> emojiLeft
            hasArrow -> width - barWidth - arrowWidth
            else -> width.toFloat()
        }
        val fitted = if (mode == Mode.CANDIDATES && hasArrow && !hasEmoji && cores.size > 1) {
            CandidateRow.fit(
                cores, bars, room, start,
                itemPadding, minItemPadding, largeHole, minTextScale,
            )
        } else {
            plan
        }
        rowTextSize = candidateSize * fitted.scale
        var x = start
        for (i in cores.indices) {
            val w = CandidateRow.itemWidth(cores[i], bars[i], fitted.padding, fitted.scale)
            if (i > 0 && x + w > room) break
            val right = minOf(x + w, room)
            itemRects += RectF(x, rowTop, right, height.toFloat())
            x = right
        }
        val top = rowTop
        val bottom = height.toFloat()
        arrowRect.set(width - arrowWidth, top, width.toFloat(), bottom)
        barRect.set(width - arrowWidth - barWidth, top, width - arrowWidth, bottom)
        if (hasEmoji) {
            val left = toolButtonLeft(EMOJI_INDEX)
            emojiRect.set(left, top, left + toolButtonWidth(), bottom)
        }
        arrangePreedit()
    }

    private fun span(cores: List<Float>, bars: List<Boolean>, fit: CandidateRow.Fit): Float {
        var sum = 0f
        for (i in cores.indices) sum += CandidateRow.itemWidth(cores[i], bars[i], fit.padding, fit.scale)
        return sum
    }

    /**
     * Choices follow the text's own width. A short pinyin no longer reserves a third of the line,
     * so every reading that fits is shown. Past the screen edge, the text keeps about three tenths
     * and a ▼ takes the arrow column.
     */
    private fun arrangePreedit() {
        choiceRects.clear()
        hasSyllableArrow = false
        val preedit = preedit ?: return
        val start = START_DP * density
        val gap = CHOICE_GAP_DP * density
        val natural = painter.measure(preedit.chosen + preedit.pinyin, preeditSize)
        val right = width - start
        val line = (right - start).coerceAtLeast(0f)
        val widths = preedit.choices.map { painter.measure(it.spelling, preeditSize) + 2 * CHOICE_PADDING_DP * density }

        fun place(textRoom: Float, limit: Float): Int {
            var cursor = start + textRoom
            var shown = 0
            for (w in widths) {
                val at = cursor + gap
                if (at + w > limit + 0.5f) break
                cursor = at + w
                shown++
            }
            return shown
        }

        var textRoom = min(natural, line)
        var shown = if (widths.isEmpty()) 0 else place(textRoom, right)
        if (widths.isNotEmpty() && shown < widths.size) {
            hasSyllableArrow = true
            val limit = width - arrowWidth
            val maxText = (limit - start - gap).coerceAtLeast(0f)
            textRoom = min(natural, min(line * MIN_TEXT_SHARE, maxText))
            shown = place(textRoom, limit)
        }
        preeditEnd = start + textRoom
        preeditX = preeditEnd - natural
        var cursor = preeditEnd + gap
        val limit = if (hasSyllableArrow) width - arrowWidth else right
        for (i in 0 until shown) {
            if (cursor + widths[i] > limit + 0.5f) break
            choiceRects += RectF(cursor, 0f, cursor + widths[i], preeditHeight)
            cursor += widths[i] + gap
        }
        syllableArrowRect.set(width - arrowWidth, 0f, width.toFloat(), preeditHeight)
    }

    override fun onDraw(canvas: Canvas) {
        drawPreedit(canvas)
        val cut = if (mode == Mode.ANSWER) TextUtils.TruncateAt.START else TextUtils.TruncateAt.END
        itemRects.forEachIndexed { i, rect ->
            val label = labels[i]
            val held = pressed == Target.Candidate(i)
            if (held || learned.getOrNull(i) == true) {
                painter.cap(canvas, rect, if (held) painter.keyPressedColor else painter.activeColor)
            }
            if (label == null) {
                painter.label(canvas, rect, "|", rowTextSize, painter.hintColor)
            } else {
                painter.label(canvas, rect, label, rowTextSize, painter.textColor, cut)
            }
        }
        if (hasEmoji) {
            if (pressed == Target.Emoji) painter.cap(canvas, emojiRect, painter.keyPressedColor)
            painter.label(canvas, emojiRect, emojiLabel, painter.labelSize(emojiLabel, buttonSize), painter.textColor)
        }
        if (hasArrow) {
            painter.fillRect(canvas, arrowRect, painter.boardColor)
            if (pressed == Target.Arrow) painter.cap(canvas, arrowRect, painter.keyPressedColor)
            if (mode != Mode.HEADER) painter.label(canvas, barRect, "|", rowTextSize, painter.hintColor)
            painter.label(canvas, arrowRect, if (wordsExpanded) "▲" else "▼", preeditSize, painter.textColor)
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
        if (hasSyllableArrow) {
            painter.fillRect(canvas, syllableArrowRect, painter.boardColor)
            if (pressed == Target.SyllableArrow) painter.cap(canvas, syllableArrowRect, painter.keyPressedColor)
            painter.label(
                canvas,
                syllableArrowRect,
                if (syllablesExpanded) "▲" else "▼",
                preeditSize,
                painter.textColor,
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPress)
                forgot = false
                downX = event.x
                downY = event.y
                pressed = targetAt(event.x, event.y) ?: return false
                val candidate = pressed as? Target.Candidate
                if (candidate != null && learned.getOrNull(candidate.index) == true) {
                    postDelayed(longPress, longPressTimeout)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - downX, event.y - downY) > touchSlop) removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val target = pressed.takeIf { it == targetAt(event.x, event.y) }
                val skip = forgot
                forgot = false
                pressed = null
                invalidate()
                if (!skip) when (target) {
                    is Target.Candidate -> listener?.onCandidate(target.index)
                    is Target.Choice -> listener?.onSyllableChoice(target.index)
                    Target.Arrow -> listener?.onToggleGrid()
                    Target.SyllableArrow -> listener?.onToggleSyllables()
                    Target.Emoji -> listener?.onEmoji()
                    null -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                forgot = false
                pressed = null
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    private fun targetAt(x: Float, y: Float): Target? {
        if (hasSyllableArrow && syllableArrowRect.contains(x, y)) return Target.SyllableArrow
        choiceRects.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { return Target.Choice(it) }
        if (y < rowTop) return null
        if (hasEmoji && emojiRect.contains(x, y)) return Target.Emoji
        if (hasArrow && arrowRect.contains(x, y)) return Target.Arrow
        if (hasArrow && mode != Mode.HEADER && barRect.contains(x, y)) return null
        val index = itemRects.indexOfFirst { x >= it.left && x < it.right }
        return if (index >= 0 && labels[index] != null) Target.Candidate(index) else null
    }

    private companion object {
        const val START_DP = 8f
        const val CHOICE_PADDING_DP = 8f
        const val CHOICE_GAP_DP = 6f
        const val TOOL_BUTTONS = 5
        const val EMOJI_INDEX = 4

        /** Part of the preedit line kept for the text when 9-key choices run past the screen. */
        const val MIN_TEXT_SHARE = 0.3f
    }
}
