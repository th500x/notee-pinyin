package com.notee.pinyin.engine

/** One learned word in an export file: the `ni hao` code, simplified text, and pick count. */
data class LearnedWord(val code: String, val text: String, val count: Int)

/**
 * The user's learned words as a text file. Nothing else is in the file: no clipboard,
 * no emoji counts, no pinned emoji. A file that is not ours is rejected.
 *
 * ```
 * notee-pinyin-words 1
 * ni hao	你好	3
 * ```
 */
object WordFile {
    const val HEADER = "notee-pinyin-words 1"

    fun encode(words: List<LearnedWord>): String {
        val lines = words
            .filter { it.count > 0 && it.code.isNotEmpty() && it.text.isNotEmpty() && clean(it.code) && clean(it.text) }
            .groupBy { it.code to it.text }
            .map { (key, rows) -> LearnedWord(key.first, key.second, rows.maxOf { it.count }) }
            .sortedWith(compareBy({ it.code }, { it.text }))
            .joinToString("\n") { "${it.code}\t${it.text}\t${it.count}" }
        return if (lines.isEmpty()) HEADER else "$HEADER\n$lines\n"
    }

    /** Words in the file, or null when [text] is not a word file we wrote. */
    fun decode(text: String): List<LearnedWord>? {
        val lines = text.removePrefix("\uFEFF").split('\n', '\r').map { it.trimEnd() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || lines[0] != HEADER) return null
        val best = LinkedHashMap<Pair<String, String>, Int>()
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            if (parts.size != 3) return null
            val code = parts[0]
            val word = parts[1]
            val count = parts[2].toIntOrNull() ?: return null
            if (code.isEmpty() || word.isEmpty() || count <= 0 || !clean(code) || !clean(word)) return null
            val key = code to word
            val old = best[key]
            if (old == null || count > old) best[key] = count
        }
        return best.map { (key, count) -> LearnedWord(key.first, key.second, count) }
    }

    private fun clean(value: String): Boolean = value.none { it == '\t' || it == '\n' || it == '\r' }
}
