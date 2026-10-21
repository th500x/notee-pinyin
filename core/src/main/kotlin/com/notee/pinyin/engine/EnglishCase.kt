package com.notee.pinyin.engine

/**
 * Copies the case the user actually typed onto a dictionary word.
 * All lowercase keeps the dictionary's own spelling, so `aaron` can stay `Aaron`.
 */
object EnglishCase {
    fun apply(typed: String, word: String): String {
        if (typed.none { it.isUpperCase() }) return word
        if (typed.all { !it.isLetter() || it.isUpperCase() }) return word.uppercase()
        return word.replaceFirstChar { it.titlecase() }
    }
}
