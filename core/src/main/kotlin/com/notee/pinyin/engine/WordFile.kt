package com.notee.pinyin.engine

import com.notee.pinyin.rank.T9Keys

/** One learned word in an export file: the `ni hao` code, simplified text, and pick count. */
data class LearnedWord(val code: String, val text: String, val count: Int)

/** One 9-key reading the user picked: the digit run, the spelling, and how many times. */
data class LearnedSyllable(val digits: String, val spelling: String, val count: Int)

/** What a word file holds. Version 1 files have an empty [syllables] list. */
class WordFileData(val words: List<LearnedWord>, val syllables: List<LearnedSyllable>)

/**
 * The user's learned words and 9-key readings as a text file. Nothing else is in the file:
 * no clipboard, no emoji counts, no pinned emoji. A file that is not ours is rejected.
 *
 * A row whose code is only the digits `2`–`9` is a reading (`9426`, `xian`, `4`). Any other
 * code is a word. A reading is kept only when those digits are exactly how [spelling] is typed.
 *
 * ```
 * notee-pinyin-words 2
 * 9426	xian	4
 * ni hao	你好	3
 * ```
 *
 * Files written as version 1, words only, still load.
 */
object WordFile {
    const val HEADER = "notee-pinyin-words 2"
    private const val HEADER_V1 = "notee-pinyin-words 1"

    fun encode(words: List<LearnedWord>, syllables: List<LearnedSyllable> = emptyList()): String {
        val lines = ArrayList<Triple<String, String, Int>>()
        words
            .filter { it.count > 0 && it.code.isNotEmpty() && it.text.isNotEmpty() && clean(it.code) && clean(it.text) && !isSyllableCode(it.code) }
            .groupBy { it.code to it.text }
            .forEach { (key, rows) -> lines += Triple(key.first, key.second, rows.maxOf { it.count }) }
        syllables
            .filter { it.count > 0 && syllableOk(it.digits, it.spelling) }
            .groupBy { it.digits to it.spelling }
            .forEach { (key, rows) -> lines += Triple(key.first, key.second, rows.maxOf { it.count }) }
        lines.sortWith(compareBy({ it.first }, { it.second }))
        val body = lines.joinToString("\n") { "${it.first}\t${it.second}\t${it.third}" }
        return if (body.isEmpty()) HEADER else "$HEADER\n$body\n"
    }

    /** The rows in the file, or null when [text] is not a word file we wrote. */
    fun decode(text: String): WordFileData? {
        val lines = text.removePrefix("\uFEFF").split('\n', '\r').map { it.trimEnd() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || (lines[0] != HEADER && lines[0] != HEADER_V1)) return null
        val words = LinkedHashMap<Pair<String, String>, Int>()
        val syllables = LinkedHashMap<Pair<String, String>, Int>()
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            if (parts.size != 3) return null
            val code = parts[0]
            val value = parts[1]
            val count = parts[2].toIntOrNull() ?: return null
            if (code.isEmpty() || value.isEmpty() || count <= 0 || !clean(code) || !clean(value)) return null
            if (isSyllableCode(code)) {
                if (!syllableOk(code, value)) return null
                val key = code to value
                val old = syllables[key]
                if (old == null || count > old) syllables[key] = count
            } else {
                val key = code to value
                val old = words[key]
                if (old == null || count > old) words[key] = count
            }
        }
        return WordFileData(
            words.map { (key, count) -> LearnedWord(key.first, key.second, count) },
            syllables.map { (key, count) -> LearnedSyllable(key.first, key.second, count) },
        )
    }

    private fun isSyllableCode(code: String): Boolean = code.all { it in '2'..'9' }

    /** [digits] is the 9-key run for [spelling], and [spelling] is plain letters. */
    private fun syllableOk(digits: String, spelling: String): Boolean {
        if (spelling.any { it !in 'a'..'z' }) return false
        return T9Keys.digitsOf(spelling) == digits
    }

    private fun clean(value: String): Boolean = value.none { it == '\t' || it == '\n' || it == '\r' }
}
