package com.notee.pinyin.festival

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.notee.pinyin.data.CardShareProvider
import com.notee.pinyin.ime.Editor
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Draws one greeting card and hands it to an editor that accepts images.
 * The frame already carries the year. This paints an optional signature,
 * the name, origin, today's dates, one duty line, one blessing, and the mark.
 * Each send picks one series that has a picture for this year. Text starts under the
 * border and the spare room is spread between the lines, so the ivory is not
 * left as one empty band.
 */
internal object BlessingCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val LOGO = 128

    /** Ivory on the oval frame. The cartouche ends near the top; the square starts near the bottom. */
    private val OVAL = FrameBox(top = 360, bottom = 1100, column = 760, logoX = 891, logoY = 1230)

    /** The blossom frame's paper is narrower, and its square sits higher than the oval's. */
    private val BRANCH = FrameBox(top = 280, bottom = 1040, column = 640, logoX = 903, logoY = 1238)

    /** Navy ground and gold plum. A branch hangs into the top-left, so the column stays inside it. */
    private val GOLD = FrameBox(top = 360, bottom = 1100, column = 700, logoX = 897, logoY = 1250)

    /** A full floral border. The inner rectangle is like the blossom frame, and the square starts higher. */
    private val GARLAND = FrameBox(top = 250, bottom = 1040, column = 680, logoX = 859, logoY = 1204)

    /** A thin gold line and a year plaque. The paper matches the oval; the square sits a little to the left. */
    private val NAVY = FrameBox(top = 360, bottom = 1100, column = 740, logoX = 871, logoY = 1237)

    private val FRAMES = listOf(
        "oval" to OVAL,
        "branch" to BRANCH,
        "gold" to GOLD,
        "garland" to GARLAND,
        "navy" to NAVY,
    )

    private val ink = 0xFF1A5558.toInt()
    private val rule = 0xFFA07C3E.toInt()
    private val quiet = 0xFF5C4C3C.toInt()
    private val metaColor = 0xFF6E5A3E.toInt()
    private val wishColor = 0xFF2F2822.toInt()

    private var sample: Bitmap? = null

    /** The small card shown on a festival row that will send a picture. */
    fun sample(context: Context): Bitmap? {
        val cached = sample
        if (cached != null && !cached.isRecycled) return cached
        return bitmap(context, "frames/sample.jpg")?.also { sample = it }
    }

    /**
     * True when the picture was accepted. False means the caller should send the emoji:
     * no row for this day, no frame for this year, or the editor will not take an image.
     * [fromLine] is already worded, or empty when the sender left the name blank.
     * [wishLine] replaces the random blessing when the sender wrote one.
     */
    fun send(
        context: Context,
        editor: Editor,
        festival: Festival,
        name: String,
        date: LocalDate,
        fromLine: String,
        wishLine: String,
        english: Boolean,
    ): Boolean {
        return try {
            val day = Almanac.on(date) ?: return false
            val jpeg = draw(context, festival, name, date, day, fromLine, wishLine, english) ?: return false
            val uri = CardShareProvider.publish(context, jpeg)
            editor.commitImage(uri, name)
        } catch (_: Exception) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    private fun draw(
        context: Context,
        festival: Festival,
        name: String,
        date: LocalDate,
        day: AlmanacDay,
        fromLine: String,
        wishLine: String,
        english: Boolean,
    ): ByteArray? {
        val picked = frame(context, date.year) ?: return null
        val frame = picked.bitmap
        val box = picked.box
        val canvasBitmap = if (frame.width == WIDTH && frame.height == HEIGHT && frame.isMutable) {
            frame
        } else {
            Bitmap.createScaledBitmap(frame, WIDTH, HEIGHT, true).also { if (it != frame) frame.recycle() }
        }
        val page = if (canvasBitmap.isMutable) canvasBitmap else {
            canvasBitmap.copy(Bitmap.Config.ARGB_8888, true).also { canvasBitmap.recycle() }
        }
        return try {
            val canvas = Canvas(page)
            paintCard(canvas, festival, name, date, day, fromLine, wishLine, english, box)
            bitmap(context, "frames/mark.png")?.let { mark ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val left = box.logoX - LOGO / 2
                val top = box.logoY - LOGO / 2
                canvas.drawBitmap(mark, null, Rect(left, top, left + LOGO, top + LOGO), paint)
                mark.recycle()
            }
            val bytes = ByteArrayOutputStream()
            page.compress(Bitmap.CompressFormat.JPEG, 92, bytes)
            bytes.toByteArray()
        } finally {
            page.recycle()
        }
    }

    /** Any series that has this year, in random order. None means the caller sends the emoji. */
    private fun frame(context: Context, year: Int): Picked? {
        for ((name, box) in FRAMES.shuffled()) {
            val bitmap = bitmap(context, "frames/$name/$year.jpg") ?: continue
            return Picked(bitmap, box)
        }
        return null
    }

    private fun paintCard(
        canvas: Canvas,
        festival: Festival,
        name: String,
        date: LocalDate,
        day: AlmanacDay,
        fromLine: String,
        wishLine: String,
        english: Boolean,
        box: FrameBox,
    ) {
        val face = if (english) Typeface.SERIF else Typeface.SANS_SERIF
        val pieces = ArrayList<Piece>(6)
        if (fromLine.isNotEmpty()) {
            val from = layout(fromLine, if (english) 36f else 42f, ink, face, 8f, english, box.column)
            pieces += Piece(from.height, gap = 40, weight = 1) { host, y -> drawLayout(host, from, y, box.column) }
        }
        val namePaint = textPaint(if (english) 68f else 96f, ink, face)
        namePaint.letterSpacing = when {
            english -> 0.03f
            name.length <= 2 -> 0.18f
            name.length == 3 -> 0.08f
            else -> 0.02f
        }
        fit(namePaint, name, 36f, box.column)
        val nameHeight = nameHeight(namePaint)
        pieces += Piece(nameHeight, gap = 20, weight = 1) { host, y -> drawLine(host, name, namePaint, y) }
        pieces += Piece(8, gap = 28, weight = 1) { host, y -> drawRule(host, y + 4f) }
        val origin = layout(BlessingCopy.origin(festival, english), if (english) 36f else 40f, quiet, face, 12f, english, box.column)
        pieces += Piece(origin.height, gap = 36, weight = 2) { host, y -> drawLayout(host, origin, y, box.column) }
        val meta = layout(metaText(date, day, english), if (english) 30f else 34f, metaColor, face, 8f, english, box.column)
        pieces += Piece(meta.height, gap = 48, weight = 3) { host, y -> drawLayout(host, meta, y, box.column) }
        val spoken = wishLine.ifEmpty { BlessingCopy.blessing(festival, english, Random.nextInt(BlessingCopy.COUNT)) }
        var wishSize = if (english) 42f else 52f
        var wish = layout(spoken, wishSize, wishColor, face, 14f, english, box.column)
        pieces += Piece(wish.height, gap = 0, weight = 0) { host, y -> drawLayout(host, wish, y, box.column) }
        val band = box.bottom - box.top
        while (pieces.sumOf { it.height + it.gap } > band && wishSize > 28f) {
            wishSize -= 2f
            wish = layout(spoken, wishSize, wishColor, face, 14f, english, box.column)
            pieces[pieces.lastIndex] = Piece(wish.height, gap = 0, weight = 0) { host, y -> drawLayout(host, wish, y, box.column) }
        }
        place(canvas, pieces, box)
    }

    /**
     * Pins the first line under the top border and gives the spare ivory to the gaps,
     * with more of it above the blessing. The last line keeps the remainder so the
     * block reaches the bottom of the band.
     */
    private fun place(canvas: Canvas, pieces: List<Piece>, box: FrameBox) {
        val content = pieces.sumOf { it.height + it.gap }
        val extra = (box.bottom - box.top - content).coerceAtLeast(0)
        val weights = pieces.sumOf { it.weight }
        var y = box.top.toFloat()
        var given = 0
        for (i in pieces.indices) {
            val piece = pieces[i]
            piece.draw(canvas, y)
            y += piece.height
            if (i == pieces.lastIndex) break
            val add = if (weights == 0) 0 else if (i == pieces.lastIndex - 1) extra - given else extra * piece.weight / weights
            given += add
            y += piece.gap + add
        }
    }

    private fun metaText(date: LocalDate, day: AlmanacDay, english: Boolean): String =
        "${DATE.format(date)}\n${day.lunarLine(english)}\n${day.dutyLine(english)}"

    private fun textPaint(size: Float, color: Int, family: Typeface): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(family, Typeface.NORMAL)
        isSubpixelText = true
    }

    private fun fit(paint: TextPaint, text: String, floor: Float, column: Int) {
        while (paint.measureText(text) > column && paint.textSize > floor) {
            paint.textSize -= 2f
        }
    }

    private fun layout(
        text: String,
        size: Float,
        color: Int,
        family: Typeface,
        extra: Float,
        english: Boolean,
        column: Int,
    ): StaticLayout {
        val paint = textPaint(size, color, family)
        if (!english) paint.letterSpacing = 0.04f
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, column)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .build()
    }

    private fun nameHeight(paint: TextPaint): Int {
        val metrics = paint.fontMetrics
        return (metrics.descent - metrics.ascent).toInt()
    }

    private fun drawLine(canvas: Canvas, text: String, paint: TextPaint, top: Float) {
        val x = (WIDTH - paint.measureText(text)) / 2f
        canvas.drawText(text, x, top - paint.fontMetrics.ascent, paint)
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, top: Float, column: Int) {
        canvas.save()
        canvas.translate((WIDTH - column) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawRule(canvas: Canvas, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rule
            strokeWidth = 2f
            style = Paint.Style.FILL_AND_STROKE
        }
        val center = WIDTH / 2f
        canvas.drawLine(center - 148f, y, center - 14f, y, paint)
        canvas.drawLine(center + 14f, y, center + 148f, y, paint)
        canvas.drawRect(center - 3.5f, y - 3.5f, center + 3.5f, y + 3.5f, paint)
    }

    private fun bitmap(context: Context, path: String): Bitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    private class Piece(
        val height: Int,
        val gap: Int,
        val weight: Int,
        val draw: (Canvas, Float) -> Unit,
    )

    private class FrameBox(val top: Int, val bottom: Int, val column: Int, val logoX: Int, val logoY: Int)

    private class Picked(val bitmap: Bitmap, val box: FrameBox)

    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
