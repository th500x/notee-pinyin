package com.notee.pinyin.clip

/**
 * How long a copied text may be before it is kept (docs/02-0 §4).
 * A Chinese character counts as 2, an English letter or digit as 1, and the total may not pass [MAX_UNITS].
 * That is 500 Chinese characters, or 1000 English letters, or a mix of the two.
 */
object ClipLimit {
    const val MAX_UNITS = 1000

    fun accepts(text: String): Boolean = units(text) <= MAX_UNITS

    fun units(text: String): Int {
        var total = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            total += if (isWide(cp)) 2 else 1
            if (total > MAX_UNITS) return total
            i += Character.charCount(cp)
        }
        return total
    }

    /** Ideographs, CJK punctuation, and fullwidth forms. An emoji counts as 1. */
    private fun isWide(cp: Int): Boolean =
        Character.isIdeographic(cp) || cp in 0x3000..0x303F || cp in 0xFF00..0xFFEF
}
