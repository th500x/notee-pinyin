package com.notee.pinyin.keyboard

import kotlin.math.min

/**
 * How a candidate row uses a gap it cannot fill (docs/01-1 §2).
 *
 * Each word normally keeps [padding] on both sides. When the next word does not fit and the
 * unused width is at least [largeHole], that inset shrinks, down to [minPadding], just enough
 * to show the word. If the tightest inset still cannot, the text scale drops toward [minScale].
 * A change that would not reveal another word is left unused, so a full row of characters stays
 * at the usual inset and size.
 */
internal object CandidateRow {
    data class Fit(val padding: Float, val scale: Float)

    fun fit(
        cores: List<Float>,
        bars: List<Boolean>,
        room: Float,
        start: Float,
        padding: Float,
        minPadding: Float,
        largeHole: Float,
        minScale: Float,
    ): Fit {
        val usual = Fit(padding, 1f)
        if (cores.size < 2 || room <= start) return usual
        val shown = count(cores, bars, room, start, padding, 1f)
        if (shown >= cores.size) return usual
        if (room - end(cores, bars, room, start, padding, 1f, shown) < largeHole) return usual
        if (count(cores, bars, room, start, minPadding, 1f) > shown) {
            return Fit(widestPadding(cores, bars, room, start, padding, minPadding, shown), 1f)
        }
        val floor = min(minScale, 1f)
        if (floor >= 1f || count(cores, bars, room, start, minPadding, floor) <= shown) return usual
        return Fit(minPadding, largestScale(cores, bars, room, start, minPadding, floor, shown))
    }

    fun itemWidth(core: Float, bar: Boolean, padding: Float, scale: Float): Float =
        if (bar) core else core * scale + padding * 2

    fun count(
        cores: List<Float>,
        bars: List<Boolean>,
        room: Float,
        start: Float,
        padding: Float,
        scale: Float,
    ): Int {
        var x = start
        var shown = 0
        for (i in cores.indices) {
            val width = itemWidth(cores[i], bars[i], padding, scale)
            if (shown > 0 && x + width > room) break
            x = min(x + width, room)
            shown++
        }
        return shown
    }

    private fun end(
        cores: List<Float>,
        bars: List<Boolean>,
        room: Float,
        start: Float,
        padding: Float,
        scale: Float,
        shown: Int,
    ): Float {
        var x = start
        for (i in 0 until shown) {
            x = min(x + itemWidth(cores[i], bars[i], padding, scale), room)
        }
        return x
    }

    /** Largest inset in [minPadding, padding] that still shows more than [shown] words. */
    private fun widestPadding(
        cores: List<Float>,
        bars: List<Boolean>,
        room: Float,
        start: Float,
        padding: Float,
        minPadding: Float,
        shown: Int,
    ): Float {
        var low = minPadding
        var high = padding
        var best = minPadding
        repeat(20) {
            if (high - low < 0.25f) return best
            val mid = (low + high) / 2f
            if (count(cores, bars, room, start, mid, 1f) > shown) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    /** Largest scale in [minScale, 1] that still shows more than [shown] words at [padding]. */
    private fun largestScale(
        cores: List<Float>,
        bars: List<Boolean>,
        room: Float,
        start: Float,
        padding: Float,
        minScale: Float,
        shown: Int,
    ): Float {
        var low = minScale
        var high = 1f
        var best = minScale
        repeat(20) {
            if (high - low < 0.005f) return best
            val mid = (low + high) / 2f
            if (count(cores, bars, room, start, padding, mid) > shown) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }
}
