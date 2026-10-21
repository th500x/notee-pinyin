package com.notee.pinyin.engine

/**
 * Words the user picked on this device, with pick counts. Keys use the `ni hao` code form.
 * Lives in memory; [onRecorded] reports each pick so the keyboard can add one on disk too.
 */
class UserDictionary {
    private val words = HashMap<String, HashMap<String, Int>>()
    private val prefixes = HashSet<String>()

    /** Proper prefixes of stored codes: a longer word continues past this code. */
    private val longer = HashSet<String>()

    /** Bumps on every [put], so a key-prefix cache can rebuild after a load or a pick. */
    var revision: Int = 0
        private set

    var onRecorded: ((code: String, text: String) -> Unit)? = null

    /**
     * False in a field that asked the keyboard not to learn (incognito, some banking apps).
     * Picks then change nothing, in memory or on disk.
     */
    var learning = true

    fun count(code: String, text: String): Int = words[code]?.get(text) ?: 0

    fun words(code: String): Map<String, Int> = words[code].orEmpty()

    /** Some recorded code equals or continues [code]. */
    fun hasPrefix(code: String): Boolean = code in prefixes

    /** Some recorded code is longer than [code] and starts with it. */
    fun hasLonger(code: String): Boolean = code in longer

    fun forEachCode(action: (String) -> Unit) {
        for (code in words.keys) action(code)
    }

    /** Every stored word, for an export file. */
    fun learnedWords(): List<LearnedWord> {
        val out = ArrayList<LearnedWord>()
        for ((code, group) in words) {
            for ((text, count) in group) {
                if (count > 0) out += LearnedWord(code, text, count)
            }
        }
        return out
    }

    /**
     * Raises a stored count up to [count] when the file has more. A smaller incoming count
     * leaves the local one alone. This is not a pick, so [onRecorded] is not called.
     */
    fun raiseTo(code: String, text: String, count: Int) {
        if (count > this.count(code, text)) put(code, text, count)
    }

    /** Highest pick count of [text], whatever reading it was learned under. */
    fun countsByText(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (group in words.values) {
            for ((text, count) in group) {
                val old = counts[text]
                if (old == null || count > old) counts[text] = count
            }
        }
        return counts
    }

    fun record(code: String, text: String) {
        if (!learning) return
        put(code, text, count(code, text) + 1)
        onRecorded?.invoke(code, text)
    }

    /**
     * Adds a stored row without reporting it back. Adding, not replacing, keeps picks made
     * while the rows were still loading.
     */
    fun load(code: String, text: String, count: Int) {
        if (count > 0) put(code, text, count(code, text) + count)
    }

    private fun put(code: String, text: String, count: Int) {
        words.getOrPut(code) { HashMap(2) }[text] = count
        var space = code.indexOf(' ')
        while (space >= 0) {
            val prefix = code.substring(0, space)
            prefixes += prefix
            longer += prefix
            space = code.indexOf(' ', space + 1)
        }
        prefixes += code
        revision++
    }
}
