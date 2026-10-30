package com.notee.pinyin.input

/**
 * English marks that sit against the preceding word (docs/01-0 §6).
 * A single space between a word and one of these is removed. The space after a
 * completion is the usual case. Letters, digits, opening brackets and operators keep theirs.
 */
object EnglishSpacing {
    private val attaching = setOf(
        ".", ",", "!", "?", ";", ":", "...", "'", ")", "]", "}",
    )

    fun dropSpaceBefore(before: CharSequence?, symbol: String): Boolean {
        if (symbol !in attaching || before == null || before.length < 2) return false
        if (before[before.lastIndex] != ' ') return false
        return before[before.lastIndex - 1].isLetterOrDigit()
    }
}
