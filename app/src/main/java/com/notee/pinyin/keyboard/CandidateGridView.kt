package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.notee.pinyin.R
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * The expanded candidates (docs/01-1 §2): cells flowed into rows, one screen at a time, with
 * 清除 · 上翻 · 下翻 down the right side. A long word takes several cells; the `|` (null) takes one.
 * Screen size follows the view, so portrait and landscape page differently.
 */
class CandidateGridView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        /** [index] is into the labels given to [show]. */
        fun onGridCandidate(index: Int)

        /** A long press on a word the user has learned. The tap is not also reported. */
        fun onForgetCandidate(index: Int)

        fun onGridClear()
    }

    private sealed interface Target {
        data class Cell(val index: Int) : Target
        data object Clear : Target
        data object Up : Target
        data object Down : Target
    }

    var listener: Listener? = null

    private var labels: List<String?> = emptyList()
    private var learned: List<Boolean> = emptyList()
    private var screens: List<List<RectF>> = emptyList()
    private var screen = 0

    private val painter = KeyPainter(context)
    private val textSize = painter.dimension(R.dimen.text_candidate)
    private val buttonSize = painter.dimension(R.dimen.text_button)
    private val sideWidth = painter.dimension(R.dimen.grid_side_width)
    private val minCellWidth = painter.dimension(R.dimen.grid_cell_min_width)
    private val rowHeight = painter.dimension(R.dimen.grid_row_height)
    private val padding = painter.dimension(R.dimen.strip_item_padding)
    private val sideLabels = listOf(R.string.grid_clear, R.string.grid_page_up, R.string.grid_page_down).map(context::getString)
    private val sideTargets = listOf(Target.Clear, Target.Up, Target.Down)
    private val sideRects = List(3) { RectF() }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var contentRight = 0f
    private var downX = 0f
    private var downY = 0f
    private var pressed: Target? = null
    private var forgot = false
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPress = Runnable {
        val cell = pressed as? Target.Cell ?: return@Runnable
        if (learned.getOrNull(cell.index) != true) return@Runnable
        forgot = true
        pressed = null
        invalidate()
        listener?.onForgetCandidate(cell.index)
    }

    init {
        isClickable = true
    }

    /** Starts on the first screen, unless [resetPage] is false. */
    fun show(labels: List<String?>, learned: List<Boolean> = emptyList(), resetPage: Boolean = true) {
        this.labels = labels
        this.learned = learned
        if (resetPage) screen = 0
        removeCallbacks(longPress)
        pressed = null
        arrange()
        screen = screen.coerceAtMost(screens.lastIndex)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        screen = 0
        arrange()
        invalidate()
    }

    private fun isEnabled(target: Target): Boolean = when (target) {
        Target.Up -> screen > 0
        Target.Down -> screen < screens.lastIndex
        else -> true
    }

    private fun arrange() {
        if (width <= 0 || height <= 0) {
            screens = listOf(emptyList())
            contentRight = 0f
            return
        }
        val areaWidth = width - sideWidth
        contentRight = areaWidth
        val rows = max(1, (height / rowHeight).toInt())
        val columns = max(1, (areaWidth / minCellWidth).toInt())
        val cellWidth = areaWidth / columns
        val cellHeight = height.toFloat() / rows
        val all = ArrayList<List<RectF>>()
        var current = ArrayList<RectF>()
        var row = 0
        var column = 0
        for (label in labels) {
            val span = if (label == null) 1 else ceil((painter.measure(label, textSize) + 2 * padding) / cellWidth).toInt().coerceIn(1, columns)
            if (column + span > columns) {
                row++
                column = 0
            }
            if (row == rows) {
                all += current
                current = ArrayList()
                row = 0
            }
            current += RectF(column * cellWidth, row * cellHeight, (column + span) * cellWidth, (row + 1) * cellHeight)
            column += span
        }
        if (current.isNotEmpty() || all.isEmpty()) all += current
        screens = all
        screen = screen.coerceAtMost(screens.lastIndex)
        val third = height / 3f
        sideRects.forEachIndexed { i, rect -> rect.set(areaWidth, i * third, width.toFloat(), (i + 1) * third) }
    }

    private fun firstIndexOf(screen: Int): Int = screens.take(screen).sumOf { it.size }

    override fun onDraw(canvas: Canvas) {
        val cells = screens.getOrNull(screen) ?: return
        val first = firstIndexOf(screen)
        cells.forEachIndexed { i, rect ->
            val label = labels[first + i]
            val index = first + i
            if (label == null) {
                painter.label(canvas, rect, "|", textSize, painter.hintColor)
            } else {
                val held = pressed == Target.Cell(index)
                val color = when {
                    held -> painter.keyPressedColor
                    learned.getOrNull(index) == true -> painter.activeColor
                    else -> painter.keyColor
                }
                painter.cap(canvas, rect, color)
                painter.label(canvas, rect, label, textSize, painter.textColor)
            }
        }
        sideTargets.forEachIndexed { i, target ->
            val rect = sideRects[i]
            painter.cap(canvas, rect, if (pressed == target) painter.functionPressedColor else painter.functionColor)
            painter.label(canvas, rect, sideLabels[i], buttonSize, if (isEnabled(target)) painter.textColor else painter.hintColor)
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
                pressed = targetAt(event.x, event.y)
                val cell = pressed as? Target.Cell
                if (cell != null && learned.getOrNull(cell.index) == true) postDelayed(longPress, longPressTimeout)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - downX, event.y - downY) > touchSlop) removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val within = hypot(event.x - downX, event.y - downY) <= touchSlop
                val target = if (within) pressed else pressed?.takeIf { it == targetAt(event.x, event.y) }
                val skip = forgot
                forgot = false
                pressed = null
                if (!skip) when (target) {
                    is Target.Cell -> listener?.onGridCandidate(target.index)
                    Target.Clear -> listener?.onGridClear()
                    Target.Up -> if (screen > 0) screen--
                    Target.Down -> if (screen < screens.lastIndex) screen++
                    null -> Unit
                }
                invalidate()
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

    /**
     * Cells own everything left of the side column, so a side rect cannot swallow a character.
     * A point just outside a cell still counts, within the system's touch slop: a finger that
     * lands on the cap and shifts a little must still select that character.
     */
    private fun targetAt(x: Float, y: Float): Target? {
        if (x < contentRight) return cellAt(x, y)
        val side = sideRects.indexOfFirst { contains(it, x, y) }
        if (side >= 0) return sideTargets[side].takeIf(::isEnabled)
        return null
    }

    private fun cellAt(x: Float, y: Float): Target? {
        val cells = screens.getOrNull(screen) ?: return null
        var index = cells.indexOfFirst { contains(it, x, y) }
        if (index < 0) {
            var best = Float.MAX_VALUE
            cells.forEachIndexed { i, rect ->
                val dx = when {
                    x < rect.left -> rect.left - x
                    x > rect.right -> x - rect.right
                    else -> 0f
                }
                val dy = when {
                    y < rect.top -> rect.top - y
                    y > rect.bottom -> y - rect.bottom
                    else -> 0f
                }
                val dist = hypot(dx, dy)
                if (dist <= touchSlop && dist < best) {
                    best = dist
                    index = i
                }
            }
        }
        if (index < 0) return null
        val labelIndex = firstIndexOf(screen) + index
        return if (labels.getOrNull(labelIndex) != null) Target.Cell(labelIndex) else null
    }

    /** The view's right and bottom edges count as inside, unlike [RectF.contains]. */
    private fun contains(rect: RectF, x: Float, y: Float): Boolean {
        val right = if (rect.right >= width) width + 1f else rect.right
        val bottom = if (rect.bottom >= height) height + 1f else rect.bottom
        return x >= rect.left && x < right && y >= rect.top && y < bottom
    }
}
