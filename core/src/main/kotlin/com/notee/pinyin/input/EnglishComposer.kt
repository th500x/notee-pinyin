package com.notee.pinyin.input

import com.notee.pinyin.engine.EnglishCase
import com.notee.pinyin.engine.EnglishLexicon

/**
 * The English word being typed. Not a pinyin [Composer]: nothing here is a syllable,
 * and nothing is written into the user dictionary.
 */
class EnglishComposer(private val lexicon: EnglishLexicon) {
    private val buffer = StringBuilder()

    val isComposing: Boolean get() = buffer.isNotEmpty()

    /** Letters and `'`, and nothing else. Further input past [MAX_LENGTH] is ignored. */
    fun type(char: Char) {
        if (buffer.length >= MAX_LENGTH) return
        if (char.isLetter() || char == '\'') buffer.append(char)
    }

    fun deleteBackward() {
        if (buffer.isNotEmpty()) buffer.deleteAt(buffer.lastIndex)
    }

    fun clear() {
        buffer.setLength(0)
    }

    fun raw(): String = buffer.toString()

    /** Longer words only. The letters already typed are in the editor, so they are not offered again. */
    fun completions(): List<String> {
        val typed = raw()
        if (typed.isEmpty()) return emptyList()
        return lexicon.completions(typed)
            .map { EnglishCase.apply(typed, it) }
            .filter { !it.equals(typed, ignoreCase = true) }
    }

    fun completion(index: Int): String? = completions().getOrNull(index)

    /** The letters a tapped candidate still has to add, or null when it is not an extension. */
    fun suffix(index: Int): String? {
        val word = completion(index) ?: return null
        val typed = raw()
        if (typed.isEmpty() || !word.startsWith(typed, ignoreCase = true) || word.length == typed.length) return null
        return word.substring(typed.length)
    }

    /** The first completion, or the letters actually typed when nothing matches. Clears the buffer. */
    fun commitFirst(): String {
        val text = completions().firstOrNull() ?: raw()
        clear()
        return text
    }

    /**
     * Next words after a completion the user tapped. A space the user typed themselves
     * does not call this: that path leaves the toolbar alone (docs/01-0 英文).
     */
    fun following(word: String): List<String> = lexicon.following(word).map { EnglishCase.apply(word, it) }

    private companion object {
        const val MAX_LENGTH = 48
    }
}
