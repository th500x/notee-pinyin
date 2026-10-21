package com.notee.pinyin.engine

/**
 * What can follow text just committed (docs/01-0 §6). A dictionary word that starts with the stem
 * offers its remainder: `吃` gives `东西` from `吃东西` and `饭` from `吃饭`. Words come before
 * single characters; within either, most picked first and then heaviest in the dictionary.
 */
class Associations(private val lexicon: Lexicon, private val user: UserDictionary) {

    fun suggest(stem: String): List<String> {
        if (stem.isEmpty()) return emptyList()
        val direct = ranked(stem)
        if (direct.isNotEmpty() || stem.length == 1) return direct
        return ranked(stem.takeLast(1))
    }

    private fun ranked(stem: String): List<String> {
        val counts = user.countsByText()
        val seen = HashSet<String>()
        val items = ArrayList<LexiconEntry>()
        for (entry in lexicon.suffixes(stem)) {
            if (seen.add(entry.text)) items += entry
        }
        for ((text, _) in counts) {
            if (text.length <= stem.length || !text.startsWith(stem)) continue
            val suffix = text.substring(stem.length)
            if (seen.add(suffix)) items += LexiconEntry(suffix, 0)
        }
        fun order(list: List<LexiconEntry>) = list.sortedWith(
            compareByDescending<LexiconEntry> { counts[stem + it.text] ?: 0 }.thenByDescending { it.weight },
        )
        return (order(items.filter { it.text.length >= 2 }) + order(items.filter { it.text.length == 1 })).map { it.text }
    }

    companion object {
        /** Longest committed run worth matching as a whole. Longer runs use their last characters. */
        const val MAX_STEM = 6

        /** The trailing Han run of [text], at most [MAX_STEM] characters. Null when it ends in no Han. */
        fun stemOf(text: String): String? {
            var end = text.length
            while (end > 0 && !text[end - 1].isHan()) end--
            if (end == 0) return null
            var start = end
            while (start > 0 && text[start - 1].isHan()) start--
            val run = text.substring(start, end)
            return if (run.length <= MAX_STEM) run else run.takeLast(MAX_STEM)
        }

        private fun Char.isHan(): Boolean = this in '\u4E00'..'\u9FFF'
    }
}
