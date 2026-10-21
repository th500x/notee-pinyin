package com.notee.pinyin.input

import com.notee.pinyin.engine.Candidate
import com.notee.pinyin.engine.Lexicon
import com.notee.pinyin.engine.SyllableGraph
import com.notee.pinyin.engine.SyllableTable
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.engine.WordSearch
import com.notee.pinyin.rank.T9Keys
import com.notee.pinyin.rank.T9Matcher

/**
 * One adjacent transposition (docs/01-0 §7).
 *
 * 26-key compares letters. A longer dictionary or user word of any weight means the
 * letters are still a prefix, so nothing is offered. 9-key compares keys. Only a common
 * word (weight at least [COMMON_WORD_WEIGHT]) or a learned word of two or more syllables
 * counts as "still typing"; a rare extension such as 米虫 must not hide 你好.
 *
 * Candidates are stamped [Candidate.corrected] and cover the whole open input, so picking
 * one commits the repaired word and learns its real spelling.
 */
class Transposition(
    private val lexicon: Lexicon,
    private val user: UserDictionary,
    private val search: WordSearch,
    private val matcher: T9Matcher,
    private val syllables: SyllableTable = lexicon.syllables,
) {
    private val commonKeyPrefixes = CommonKeyPrefixes(lexicon, syllables)
    private var userRevision = -1
    private val userKeyPrefixes = HashSet<String>()

    /** [open] is the uncommitted 26-key text, including `'`. [end] is where it stops in the full input. */
    fun letters(
        open: String,
        end: Int,
        graph: SyllableGraph,
        words: List<Candidate>,
        sentence: Candidate?,
    ): List<Candidate> {
        val typed = lettersOf(open)
        if (typed.length < MIN_LENGTH) return emptyList()
        if (hasExactLetters(words, graph, typed)) return emptyList()
        if (graph.isWhole && sentence != null) return emptyList()
        if (hasLongerLetterWord(graph)) return emptyList()
        return swapped(open, end) { raw ->
            val repaired = SyllableGraph.ofLetters(raw, 0, syllables)
            exactCovers(repaired) { spellingOf(it.syllables) == lettersOf(raw) }
        }
    }

    /**
     * [open] is the uncommitted 9-key digits. [pinned] means a syllable was fixed inside
     * this stretch; that stretch is left alone.
     */
    fun keys(
        open: String,
        end: Int,
        pinned: Boolean,
        graph: SyllableGraph,
        words: List<Candidate>,
        sentence: Candidate?,
        longestPiece: Int,
    ): List<Candidate> {
        if (pinned || open.length < MIN_LENGTH) return emptyList()
        if (hasExactKeys(words, graph)) return emptyList()
        if (commonKeyPrefixes.continues(open) || userStillTyping(open)) return emptyList()
        val found = swapped(open, end) { raw ->
            val repaired = SyllableGraph.ofKeys(raw, 0, syllables.maxLength) { from, to ->
                matcher.syllables(raw.substring(from, to))
            }
            exactCovers(repaired) { true }
        }
        if (found.isEmpty()) return emptyList()
        // A real multi-word phrase stays. A light piece, such as 你爱 inside 你爱哦, does not hide 你好.
        val learned = found.any { user.count(syllables.code(it.syllables), it.text) > 0 }
        val best = found.maxOf { it.weight }
        if (!learned && graph.isWhole && sentence != null && longestPiece >= best) return emptyList()
        return found
    }

    private fun swapped(open: String, end: Int, exact: (String) -> List<Candidate>): List<Candidate> {
        val best = HashMap<String, Candidate>()
        val chars = open.toCharArray()
        for (i in 0 until chars.size - 1) {
            if (chars[i] == chars[i + 1] || chars[i] == APOSTROPHE || chars[i + 1] == APOSTROPHE) continue
            val neighbor = chars[i + 1]
            chars[i + 1] = chars[i]
            chars[i] = neighbor
            for (word in exact(String(chars))) remember(best, word, end)
            val back = chars[i + 1]
            chars[i + 1] = chars[i]
            chars[i] = back
        }
        if (best.isEmpty()) return emptyList()
        return best.values.sortedWith(
            compareByDescending<Candidate> { user.count(syllables.code(it.syllables), it.text) }
                .thenByDescending { it.weight },
        )
    }

    private fun exactCovers(graph: SyllableGraph, spellingMatches: (Candidate) -> Boolean): List<Candidate> {
        if (graph.isEmpty || !graph.isWhole) return emptyList()
        val found = ArrayList<Candidate>()
        for (word in search.wordsFromStart(graph)) {
            if (word.syllables.size >= 2 && word.end == graph.end && spellingMatches(word)) found += word
        }
        return found
    }

    private fun remember(best: HashMap<String, Candidate>, word: Candidate, end: Int) {
        val candidate = Candidate(word.text, word.syllables.copyOf(), end, word.weight, corrected = true)
        val old = best[candidate.text]
        if (old == null || prefer(candidate, old)) best[candidate.text] = candidate
    }

    private fun prefer(candidate: Candidate, old: Candidate): Boolean {
        val count = user.count(syllables.code(candidate.syllables), candidate.text)
        val oldCount = user.count(syllables.code(old.syllables), old.text)
        if (count != oldCount) return count > oldCount
        return candidate.weight > old.weight
    }

    private fun hasExactLetters(words: List<Candidate>, graph: SyllableGraph, letters: String): Boolean {
        for (word in words) {
            if (word.syllables.size >= 2 && word.end == graph.end && spellingOf(word.syllables) == letters) return true
        }
        return false
    }

    private fun hasExactKeys(words: List<Candidate>, graph: SyllableGraph): Boolean {
        for (word in words) {
            if (word.syllables.size >= 2 && word.end == graph.end) return true
        }
        return false
    }

    /**
     * Some dictionary or user word's letters continue past [graph].
     * A finished syllable blocks when its code continues; a partial tail blocks when the
     * completed syllable is itself a word or continues into one.
     */
    private fun hasLongerLetterWord(graph: SyllableGraph): Boolean {
        if (graph.isEmpty) return false
        val ids = IntArray(WordSearch.MAX_SYLLABLES)
        fun code(depth: Int) = syllables.code(ids, depth)
        fun continues(depth: Int): Boolean {
            if (lexicon.lookup(ids, depth).continues) return true
            return user.hasLonger(code(depth))
        }
        fun wordOrLonger(depth: Int): Boolean {
            val hit = lexicon.lookup(ids, depth)
            if (hit.entries.isNotEmpty() || hit.continues) return true
            val code = code(depth)
            return user.words(code).isNotEmpty() || user.hasLonger(code)
        }
        fun walk(node: Int, depth: Int): Boolean {
            for (edge in graph.edgesFrom(node)) {
                if (depth >= ids.size) continue
                ids[depth] = edge.syllable
                val next = depth + 1
                val longer = if (edge.partial) {
                    wordOrLonger(next)
                } else if (edge.to == graph.end) {
                    continues(next)
                } else {
                    walk(edge.to, next)
                }
                if (longer) return true
            }
            return false
        }
        return walk(graph.start, 0)
    }

    private fun spellingOf(ids: IntArray): String = buildString {
        for (id in ids) append(syllables.spelling(id))
    }

    private fun userStillTyping(keys: String): Boolean {
        if (user.revision != userRevision) {
            userKeyPrefixes.clear()
            user.forEachCode { code ->
                if (' ' !in code) return@forEachCode
                val digits = StringBuilder()
                for (syllable in code.split(' ')) digits.append(T9Keys.digitsOf(syllable))
                val spelled = digits.toString()
                for (length in 1 until spelled.length) userKeyPrefixes += spelled.substring(0, length)
            }
            userRevision = user.revision
        }
        return keys in userKeyPrefixes
    }

    /** Proper prefixes of common multi-syllable key sequences. Built once from [lexicon]. */
    private class CommonKeyPrefixes(lexicon: Lexicon, syllables: SyllableTable) {
        private val prefixes = HashSet<String>()

        init {
            lexicon.forEachCode { ids, length, topWeight ->
                if (length < 2 || topWeight < COMMON_WORD_WEIGHT) return@forEachCode
                val digits = StringBuilder()
                for (i in 0 until length) digits.append(T9Keys.digitsOf(syllables.spelling(ids[i])))
                val keys = digits.toString()
                for (prefix in 1 until keys.length) prefixes += keys.substring(0, prefix)
            }
        }

        fun continues(keys: String): Boolean = keys in prefixes
    }

    private companion object {
        const val MIN_LENGTH = 4

        /** Highest lexicon weight of a reading. Below this, a longer 9-key word does not count as "still typing". */
        const val COMMON_WORD_WEIGHT = 100_000

        const val APOSTROPHE = '\''

        fun lettersOf(raw: String): String = buildString(raw.length) {
            for (ch in raw) if (ch != APOSTROPHE) append(ch)
        }
    }
}
