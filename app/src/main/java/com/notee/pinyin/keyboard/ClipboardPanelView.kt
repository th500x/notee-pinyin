package com.notee.pinyin.keyboard

import android.content.Context
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.notee.pinyin.R
import com.notee.pinyin.data.ClipHistory

/** The last [ClipHistory.MAX] copied texts, newest on top. Empty history shows an empty panel (docs/02-0 §4). */
class ClipboardPanelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    var onPick: ((String) -> Unit)? = null

    private val painter = KeyPainter(context)
    private val gap = painter.dimension(R.dimen.key_gap).toInt()
    private val textSize = painter.dimension(R.dimen.text_button)

    init {
        orientation = VERTICAL
        setPadding(gap, gap, gap, gap)
    }

    fun show(texts: List<String>) {
        removeAllViews()
        val rows = ArrayList<TextView>(texts.size)
        for (text in texts) {
            val view = item(text)
            rows += view
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(gap, gap, gap, gap) })
        }
        repeat(ClipHistory.MAX - texts.size) {
            addView(Space(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Width is known only after layout. The click still sends the full text.
        post {
            val width = rows.firstOrNull()?.let { it.width - it.paddingLeft - it.paddingRight } ?: return@post
            if (width <= 0) return@post
            for (view in rows) {
                val full = view.tag as String
                view.text = twoLines(full, width)
            }
        }
    }

    private fun item(text: String) = TextView(context).apply {
        tag = text
        this.text = text
        maxLines = MAX_LINES
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(painter.textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        setBackgroundResource(R.drawable.clip_item_background)
        setPadding(gap * 4, 0, gap * 4, 0)
        setOnClickListener { onPick?.invoke(text) }
    }

    /** At most two lines. Anything longer keeps the start and ends with [ELLIPSIS]. */
    private fun twoLines(text: String, width: Int): String {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = this@ClipboardPanelView.textSize }
        if (fits(text, paint, width)) return text
        var low = 0
        var high = text.length
        var best = ELLIPSIS
        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = prefix(text, mid) + ELLIPSIS
            if (fits(candidate, paint, width)) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    /** [end] is a UTF-16 index. Step back when it would split an emoji in half. */
    private fun prefix(text: String, end: Int): String {
        var cut = end.coerceIn(0, text.length)
        if (cut in 1 until text.length && text[cut - 1].isHighSurrogate()) cut--
        return text.substring(0, cut).trimEnd()
    }

    private fun fits(text: String, paint: TextPaint, width: Int): Boolean =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setMaxLines(MAX_LINES + 1)
            .setIncludePad(true)
            .build()
            .lineCount <= MAX_LINES

    private companion object {
        const val MAX_LINES = 2

        /** Three ASCII dots. This phone's font draws the single ellipsis character as two dots. */
        const val ELLIPSIS = "..."
    }
}
