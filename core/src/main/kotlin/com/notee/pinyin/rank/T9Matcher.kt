package com.notee.pinyin.rank

import com.notee.pinyin.engine.SyllableTable

/** Phone-pad letter groups, `2` = abc through `9` = wxyz (docs/01-0 §1). */
object T9Keys {
    val DIGITS: List<Char> = ('2'..'9').toList()

    private val GROUPS = listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")

    fun letters(digit: Char): String = GROUPS.getOrElse(digit - '2') { "" }

    fun digitOf(letter: Char): Char {
        val group = GROUPS.indexOfFirst { letter in it }
        require(group >= 0) { "no key for $letter" }
        return '2' + group
    }

    fun digitsOf(spelling: String): String = spelling.map(::digitOf).joinToString("")
}

/**
 * 9-key exact matching: a key run stands for the syllables whose letters it spells one key per
 * letter, never for syllables it only begins (docs/01-0 §4).
 */
class T9Matcher(private val syllables: SyllableTable) {
    private val exact: Map<String, IntArray>
    private val previews: Map<String, String>

    init {
        val digits = Array(syllables.size) { T9Keys.digitsOf(syllables.spelling(it)) }
        exact = (0 until syllables.size)
            .groupBy { digits[it] }
            .mapValues { (_, ids) -> ids.sortedByDescending(syllables::weight).toIntArray() }
        val heaviest = HashMap<String, Int>()
        for (id in 0 until syllables.size) {
            for (length in 1..digits[id].length) {
                val prefix = digits[id].substring(0, length)
                val current = heaviest[prefix]
                if (current == null || syllables.weight(id) > syllables.weight(current)) heaviest[prefix] = id
            }
        }
        previews = heaviest.mapValues { (prefix, id) -> syllables.spelling(id).substring(0, prefix.length) }
    }

    /** Syllables spelled exactly by [keys], heaviest first. Empty when there is none. */
    fun syllables(keys: CharSequence): IntArray = exact[keys.toString()] ?: NONE

    /** Letters to show while [keys] is still on its way to a syllable; null once no syllable starts this way. */
    fun preview(keys: CharSequence): String? = previews[keys.toString()]

    private companion object {
        val NONE = IntArray(0)
    }
}
