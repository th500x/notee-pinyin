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

    /** A 9-key reading the user picked for the keys that spell it. Not a chosen word. */
    var onSyllableRecorded: ((digits: String, spelling: String) -> Unit)? = null

    private val syllablePicks = HashMap<String, HashMap<String, Int>>()

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

    /** How often [spelling] was picked for the 9-key run [digits]. */
    fun syllableCount(digits: String, spelling: String): Int = syllablePicks[digits]?.get(spelling) ?: 0

    /** Every stored reading, for an export file. */
    fun learnedSyllables(): List<LearnedSyllable> {
        val out = ArrayList<LearnedSyllable>()
        for ((digits, group) in syllablePicks) {
            for ((spelling, count) in group) {
                if (count > 0) out += LearnedSyllable(digits, spelling, count)
            }
        }
        return out
    }

    /**
     * Raises a stored reading up to [count] when the file has more. A smaller incoming count
     * leaves the local one alone. This is not a pick, so [onSyllableRecorded] is not called.
     */
    fun raiseSyllable(digits: String, spelling: String, count: Int) {
        if (count > syllableCount(digits, spelling)) putSyllable(digits, spelling, count)
    }

    /** One tap on a 9-key reading. Ignored when [learning] is off; deleting the keys does not undo it. */
    fun recordSyllable(digits: String, spelling: String) {
        if (!learning) return
        putSyllable(digits, spelling, syllableCount(digits, spelling) + 1)
        onSyllableRecorded?.invoke(digits, spelling)
    }

    /** Adds a stored reading without reporting it back. Adding keeps a tap made while rows were loading. */
    fun loadSyllable(digits: String, spelling: String, count: Int) {
        if (count > 0) putSyllable(digits, spelling, syllableCount(digits, spelling) + count)
    }

    private fun putSyllable(digits: String, spelling: String, count: Int) {
        syllablePicks.getOrPut(digits) { HashMap(2) }[spelling] = count
    }

    /**
     * Adds a stored row without reporting it back. Adding, not replacing, keeps picks made
     * while the rows were still loading.
     */
    fun load(code: String, text: String, count: Int) {
        if (count > 0) put(code, text, count(code, text) + count)
    }

    /**
     * Drops one learned word. A word that also lives in the main dictionary can still be
     * suggested; it only loses this personal count. Returns false when it was not stored.
     */
    fun forget(code: String, text: String): Boolean {
        val group = words[code] ?: return false
        if (group.remove(text) == null) return false
        if (group.isEmpty()) words.remove(code)
        prefixes.clear()
        longer.clear()
        for (stored in words.keys) remember(stored)
        revision++
        return true
    }

    private fun put(code: String, text: String, count: Int) {
        words.getOrPut(code) { HashMap(2) }[text] = count
        remember(code)
        revision++
    }

    /** [code] and each shorter `ni` inside `ni hao`, so a longer word can still be found. */
    private fun remember(code: String) {
        var space = code.indexOf(' ')
        while (space >= 0) {
            val prefix = code.substring(0, space)
            prefixes += prefix
            longer += prefix
            space = code.indexOf(' ', space + 1)
        }
        prefixes += code
    }
}
