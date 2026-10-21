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
import com.notee.pinyin.emoji.EmojiCatalog
import com.notee.pinyin.emoji.EmojiCell
import kotlin.math.abs

/**
 * One emoji page, [EmojiCatalog.COLUMNS] across. Rows are a fifth of the key area, so 常用 fills it
 * exactly and longer tabs scroll inside the parent ScrollView. A [EmojiCell.Rule] ends the row it
 * meets and draws its line in a thinner row of its own.
 */
class EmojiGridView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var onEmoji: ((String) -> Unit)? = null

    private var cells: List<EmojiCell> = emptyList()
    private var rowOf = IntArray(0)
    private var columnOf = IntArray(0)

    /** Top of each row, then the bottom of the last. */
    private var rowEdges = floatArrayOf(0f)

    private val painter = KeyPainter(context)
    private val emojiSize = painter.dimension(R.dimen.text_emoji)
    private val rowHeight = painter.dimension(R.dimen.keys_height) / EmojiCatalog.ROWS
    private val ruleHeight = rowHeight * RULE_ROW
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val cell = RectF()
    private var pressed = -1
    private var downX = 0f
    private var downY = 0f

    fun show(cells: List<EmojiCell>) {
        this.cells = cells
        layOut()
        pressed = -1
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), rowEdges.last().toInt())
    }

    override fun onDraw(canvas: Canvas) {
        cells.forEachIndexed { i, emojiCell ->
            cellOf(i, cell)
            when (emojiCell) {
                is EmojiCell.Emoji -> {
                    if (i == pressed) painter.cap(canvas, cell, painter.keyPressedColor)
                    painter.label(canvas, cell, emojiCell.text, emojiSize, painter.textColor)
                }
                EmojiCell.Bar -> painter.label(canvas, cell, "|", emojiSize, painter.hintColor)
                EmojiCell.Rule -> painter.rule(canvas, cell)
                EmojiCell.Empty -> Unit
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressed = indexAt(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (pressed >= 0 && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                pressed = -1
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val tapped = (cells.getOrNull(pressed) as? EmojiCell.Emoji)?.text
                pressed = -1
                invalidate()
                tapped?.let { onEmoji?.invoke(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
            }
        }
        return true
    }

    private fun layOut() {
        rowOf = IntArray(cells.size)
        columnOf = IntArray(cells.size)
        val edges = arrayListOf(0f)
        var row = 0
        var column = 0
        fun endRow(height: Float) {
            edges += edges.last() + height
            row++
            column = 0
        }
        cells.forEachIndexed { i, emojiCell ->
            if (emojiCell == EmojiCell.Rule && column > 0) endRow(rowHeight)
            rowOf[i] = row
            columnOf[i] = column
            when {
                emojiCell == EmojiCell.Rule -> endRow(ruleHeight)
                ++column == EmojiCatalog.COLUMNS -> endRow(rowHeight)
            }
        }
        if (column > 0) endRow(rowHeight)
        rowEdges = edges.toFloatArray()
    }

    private fun indexAt(x: Float, y: Float): Int = cells.indices.firstOrNull { i ->
        cells[i] is EmojiCell.Emoji && cellOf(i, cell).contains(x, y)
    } ?: -1

    private fun cellOf(index: Int, out: RectF): RectF {
        val top = rowEdges[rowOf[index]]
        val bottom = rowEdges[rowOf[index] + 1]
        if (cells[index] == EmojiCell.Rule) return out.apply { set(0f, top, width.toFloat(), bottom) }
        val w = width.toFloat() / EmojiCatalog.COLUMNS
        val column = columnOf[index]
        return out.apply { set(column * w, top, (column + 1) * w, bottom) }
    }

    private companion object {
        /** A rule row is this much of an emoji row. */
        const val RULE_ROW = 0.5f
    }
}
