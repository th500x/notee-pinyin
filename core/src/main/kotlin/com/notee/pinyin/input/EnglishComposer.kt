package com.notee.pinyin.input

import com.notee.pinyin.engine.EnglishCase
import com.notee.pinyin.engine.EnglishFormat
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

    /**
     * Longer words only. The letters already typed are in the editor, so they are not offered again.
     * A place name keeps the capital in [EnglishPlaces] when the table stored it in lowercase.
     */
    fun completions(): List<String> {
        val typed = raw()
        if (typed.isEmpty()) return emptyList()
        val out = ArrayList<String>(EnglishFormat.COMPLETIONS)
        for (word in lexicon.completions(typed)) {
            if (out.size == EnglishFormat.COMPLETIONS) break
            val shown = EnglishCase.apply(typed, word)
            if (shown.equals(typed, ignoreCase = true)) continue
            if (out.none { it.equals(shown, ignoreCase = true) }) out += shown
        }
        for (place in EnglishPlaces.longerThan(typed, EnglishFormat.COMPLETIONS)) {
            val shown = EnglishCase.apply(typed, place)
            if (shown.equals(typed, ignoreCase = true)) continue
            val existing = out.indexOfFirst { it.equals(shown, ignoreCase = true) }
            if (existing >= 0) {
                if (typed.none { it.isUpperCase() } && out[existing] != shown) out[existing] = shown
            } else if (out.size < EnglishFormat.COMPLETIONS) {
                out += shown
            }
        }
        return out
    }

    fun completion(index: Int): String? = completions().getOrNull(index)

    /**
     * The candidate at [index]. [Selection.replace] is set when the letters already typed
     * do not match the start of [Selection.word], so the capital has to be written into the editor.
     */
    fun selection(index: Int): Selection? {
        val word = completion(index) ?: return null
        val typed = raw()
        if (typed.isEmpty() || word.length <= typed.length) return null
        if (!word.startsWith(typed, ignoreCase = true)) return null
        return Selection(word, replace = !word.startsWith(typed))
    }

    /** Title case of a finished place name typed in lowercase, or null to keep the typed letters. */
    fun placeSpelling(): String? = EnglishPlaces.rewrite(raw())

    data class Selection(val word: String, val replace: Boolean)

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
