package com.notee.pinyin.engine

/**
 * The toneless syllables that occur in the bundled character table, sorted alphabetically.
 * A syllable id is its index. ü is spelled `v`.
 */
class SyllableTable(spellings: List<String>, weights: IntArray) {
    private val spellings: Array<String> = spellings.toTypedArray()
    private val weights: IntArray = weights.copyOf()
    private val ids: Map<String, Int> = spellings.withIndex().associate { it.value to it.index }

    init {
        require(spellings.size == weights.size) { "spellings and weights differ in size" }
        require(spellings.zipWithNext().all { (a, b) -> a < b }) { "spellings must be sorted and unique" }
    }

    val size: Int get() = spellings.size

    val maxLength: Int = spellings.maxOfOrNull { it.length } ?: 0

    fun id(spelling: CharSequence): Int = ids[spelling.toString()] ?: NONE

    fun spelling(id: Int): String = spellings[id]

    /** Sum of the character weights read with this syllable. Orders several syllables matched by one key run. */
    fun weight(id: Int): Int = weights[id]

    /** Ids whose spelling starts with [prefix]. Empty when no syllable does. */
    fun completions(prefix: CharSequence): IntRange {
        val p = prefix.toString()
        val from = lowerBound(p)
        var to = from
        while (to < spellings.size && spellings[to].startsWith(p)) to++
        return from until to
    }

    /** `ni hao` style code, the same form the rime dictionaries and the user dictionary use. */
    fun code(syllables: IntArray, length: Int = syllables.size): String =
        buildString {
            for (i in 0 until length) {
                if (i > 0) append(' ')
                append(spellings[syllables[i]])
            }
        }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = spellings.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (spellings[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        const val NONE = -1
    }
}
