package com.notee.pinyin.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.notee.pinyin.R
import kotlin.math.ceil
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
    private var pressed: Target? = null

    /** Starts on the first screen. */
    fun show(labels: List<String?>) {
        this.labels = labels
        screen = 0
        pressed = null
        arrange()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        screen = 0
        arrange()
    }

    private fun isEnabled(target: Target): Boolean = when (target) {
        Target.Up -> screen > 0
        Target.Down -> screen < screens.lastIndex
        else -> true
    }

    private fun arrange() {
        val areaWidth = width - sideWidth
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
            if (label == null) {
                painter.label(canvas, rect, "|", textSize, painter.hintColor)
            } else {
                painter.cap(canvas, rect, if (pressed == Target.Cell(first + i)) painter.keyPressedColor else painter.keyColor)
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
                pressed = targetAt(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val target = pressed.takeIf { it == targetAt(event.x, event.y) }
                pressed = null
                when (target) {
                    is Target.Cell -> listener?.onGridCandidate(target.index)
                    Target.Clear -> listener?.onGridClear()
                    Target.Up -> screen--
                    Target.Down -> screen++
                    null -> Unit
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
            }
        }
        return true
    }

    private fun targetAt(x: Float, y: Float): Target? {
        val side = sideRects.indexOfFirst { it.contains(x, y) }
        if (side >= 0) return sideTargets[side].takeIf(::isEnabled)
        val cells = screens.getOrNull(screen) ?: return null
        val i = cells.indexOfFirst { it.contains(x, y) }
        if (i < 0) return null
        val index = firstIndexOf(screen) + i
        return if (labels[index] != null) Target.Cell(index) else null
    }
}
