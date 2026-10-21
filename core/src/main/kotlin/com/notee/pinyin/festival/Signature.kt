package com.notee.pinyin.festival

/**
 * How long the two lines on a blessing card may be.
 * A Chinese character counts as two, a Latin letter as one.
 * [LIMIT] is the signature: ten Chinese characters, twenty English letters, or a mix.
 * [WISH_LIMIT] is a custom blessing. Stock blessings run to about two lines
 * (20 Chinese characters, 71 English letters), so a custom one may be a little longer:
 * thirty Chinese characters, sixty English letters, or a mix.
 */
object Signature {
    const val LIMIT = 20
    const val WISH_LIMIT = 60

    fun units(text: String): Int {
        var total = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            total += if (wide(cp)) 2 else 1
            i += Character.charCount(cp)
        }
        return total
    }

    fun fits(current: String, more: String, limit: Int = LIMIT): Boolean = units(current) + units(more) <= limit

    /** A cursor index that does not split a character in two. */
    fun boundary(text: String, at: Int): Int {
        val clamped = at.coerceIn(0, text.length)
        if (clamped in 1 until text.length && Character.isLowSurrogate(text[clamped])) return clamped + 1
        return clamped
    }

    /** Inserts [more] at [at]. The returned index is just after the inserted text. */
    fun insert(current: String, at: Int, more: String): Pair<String, Int> {
        val index = boundary(current, at)
        return current.substring(0, index) + more + current.substring(index) to index + more.length
    }

    /** Deletes the code point before [at]. At the start, the text stays. */
    fun deleteBefore(current: String, at: Int): Pair<String, Int> {
        val index = boundary(current, at)
        if (index == 0) return current to 0
        val start = index - Character.charCount(current.codePointBefore(index))
        return current.substring(0, start) + current.substring(index) to start
    }

    /** Drops the last code point. Empty stays empty. */
    fun dropLast(text: String): String {
        if (text.isEmpty()) return text
        val count = Character.charCount(text.codePointBefore(text.length))
        return text.substring(0, text.length - count)
    }

    private fun wide(cp: Int): Boolean = when (cp) {
        in 0x1100..0x115F -> true
        in 0x2E80..0xA4CF -> true
        in 0xAC00..0xD7A3 -> true
        in 0xF900..0xFAFF -> true
        in 0xFE10..0xFE19 -> true
        in 0xFE30..0xFE6F -> true
        in 0xFF01..0xFF60 -> true
        in 0xFFE0..0xFFE6 -> true
        in 0x20000..0x3FFFD -> true
        else -> false
    }
}
