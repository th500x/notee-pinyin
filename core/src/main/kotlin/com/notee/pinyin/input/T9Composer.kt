package com.notee.pinyin.input

import com.notee.pinyin.engine.Candidate
import com.notee.pinyin.engine.CandidateItem
import com.notee.pinyin.engine.SyllableGraph
import com.notee.pinyin.engine.SyllableTable
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.engine.WordSearch
import com.notee.pinyin.rank.CandidateRanker
import com.notee.pinyin.rank.T9Keys
import com.notee.pinyin.rank.T9Matcher

/**
 * 9-key composition. Keys split into whole syllables every way they can, like 26-key letters, but
 * never into a syllable they only begin (docs/01-0 §4). A tapped [Preedit.choices] syllable
 * fixes how the next keys read.
 */
class T9Composer internal constructor(
    syllables: SyllableTable,
    search: WordSearch,
    ranker: CandidateRanker,
    user: UserDictionary,
    private val matcher: T9Matcher,
    private val transposition: Transposition,
) : Composer(syllables, search, ranker, user) {
    /** Keys `start until end` read as [syllable] only. */
    private class Fixed(val start: Int, val end: Int, val syllable: Int)

    /** Digits `2`–`9`. */
    private val input = StringBuilder()
    private val fixed = ArrayList<Fixed>()

    override val isComposing: Boolean get() = input.isNotEmpty()

    override val preedit: Preedit
        get() = Preedit(chosenText, openReading().joinToString(SyllableGraph.DELIMITER.toString()), choices())

    fun press(digit: Char) {
        if (input.count { it != SyllableGraph.DELIMITER } >= MAX_KEYS) return
        input.append(digit)
        changed()
    }

    /**
     * Fixes the next open keys as [syllable], tapped in the preedit line.
     * A successful tap is remembered for this key run, the same way a chosen word is.
     */
    fun choose(syllable: Int): Boolean {
        val start = openStart()
        val graph = graph()
        if (graph.edgesFrom(start).none { it.syllable == syllable && graph.reachesEnd(it.to) }) return false
        val spelling = syllables.spelling(syllable)
        fixed += Fixed(start, start + spelling.length, syllable)
        user.recordSyllable(T9Keys.digitsOf(spelling), spelling)
        changed()
        return true
    }

    /** One key back. */
    override fun deleteBackward() {
        if (input.isEmpty()) return
        input.setLength(input.length - 1)
        fixed.removeAll { it.end > input.length }
        reopenSelections(input.length)
        changed()
    }

    override fun rawText(): String = (chosenSyllables.map(syllables::spelling) + openReading()).joinToString("")

    override fun graph(): SyllableGraph = SyllableGraph.ofKeys(input, chosenEnd, syllables.maxLength, ::read)

    override val isFullyChosen: Boolean get() = SyllableGraph.skipDelimiters(input, chosenEnd) >= input.length

    override fun openPinyin(): String = openReading().joinToString("")

    override fun clearInput() {
        input.setLength(0)
        fixed.clear()
    }

    override fun corrections(
        graph: SyllableGraph,
        words: List<Candidate>,
        sentence: Candidate?,
        longestPiece: Int,
    ): List<Candidate> {
        val start = SyllableGraph.skipDelimiters(input, chosenEnd)
        if (start >= input.length) return emptyList()
        val pinned = fixed.any { it.end > start }
        return transposition.keys(input.substring(start), input.length, pinned, graph, words, sentence, longestPiece)
    }

    /** The syllables keys `start until end` spell; a fixed part reads only as itself. */
    private fun read(start: Int, end: Int): IntArray {
        for (part in fixed) {
            if (part.start == start) return if (part.end == end) intArrayOf(part.syllable) else NONE
            if (start < part.end && part.start < end) return NONE
        }
        return matcher.syllables(input.subSequence(start, end))
    }

    /** Where keys stop being fixed: after the chosen words and the syllables tapped since. */
    private fun openStart(): Int {
        var at = SyllableGraph.skipDelimiters(input, chosenEnd)
        while (true) {
            val part = fixed.firstOrNull { it.start == at } ?: return at
            at = SyllableGraph.skipDelimiters(input, part.end)
        }
    }

    /**
     * Syllables the next open keys can start with, and only when the keys left after that
     * syllable still split into whole syllables. A tail such as a lone `g` has no character,
     * so that reading is left out (docs/01-0 §5). A reading this user already picked on these
     * keys comes first; the rest keep the old order: the first candidate's reading, then longest,
     * then heaviest. Shown only when there is a choice.
     */
    private fun choices(): List<SyllableChoice> {
        val start = openStart()
        val current = firstReadingAt(start)
        val graph = graph()
        val ranked = graph.edgesFrom(start)
            .filter { graph.reachesEnd(it.to) }
            .map { it.syllable }
            .distinct()
            .sortedWith(
                compareByDescending<Int> { remembered(it) }
                    .thenByDescending { it == current }
                    .thenByDescending { syllables.spelling(it).length }
                    .thenByDescending { syllables.weight(it) },
            )
        return if (ranked.size > 1) ranked.map { SyllableChoice(it, syllables.spelling(it)) } else emptyList()
    }

    /** How often this user picked [syllable] for the keys that spell it. */
    private fun remembered(syllable: Int): Int {
        val spelling = syllables.spelling(syllable)
        return user.syllableCount(T9Keys.digitsOf(spelling), spelling)
    }

    /**
     * The syllable the first uncorrected candidate reads at key [position].
     * A transposition must not rewrite the preedit as if those keys had been pressed.
     */
    private fun firstReadingAt(position: Int): Int? {
        var at = SyllableGraph.skipDelimiters(input, chosenEnd)
        for (syllable in reading()?.syllables ?: return null) {
            if (at == position) return syllable
            at = SyllableGraph.skipDelimiters(input, at + syllables.spelling(syllable).length)
        }
        return null
    }

    /** First candidate that was not found by swapping keys. */
    private fun reading(): Candidate? = candidates.items.firstNotNullOfOrNull { item ->
        (item as? CandidateItem.Word)?.candidate?.takeUnless { it.corrected }
    }

    /**
     * The open keys as letters, one string per syllable: the way the first uncorrected candidate reads them,
     * then the longest syllable that fits, then letters a syllable could start with. Never digits.
     */
    private fun openReading(): List<String> {
        val parts = ArrayList<String>()
        var at = SyllableGraph.skipDelimiters(input, chosenEnd)
        reading()?.syllables?.forEach {
            val spelling = syllables.spelling(it)
            parts += spelling
            at = SyllableGraph.skipDelimiters(input, at + spelling.length)
        }
        while (at < input.length) {
            val boundary = input.indexOf(SyllableGraph.DELIMITER, at).let { if (it < 0) input.length else it }
            val longest = minOf(boundary - at, syllables.maxLength)
            val whole = (longest downTo 1).firstNotNullOfOrNull { length ->
                read(at, at + length).firstOrNull()?.let { length to syllables.spelling(it) }
            }
            val (length, letters) = whole ?: (longest downTo 1).firstNotNullOfOrNull { length ->
                matcher.preview(input.subSequence(at, at + length))?.let { length to it }
            } ?: (1 to T9Keys.letters(input[at]).take(1))
            parts += letters
            at = SyllableGraph.skipDelimiters(input, at + length)
        }
        return parts
    }

    companion object {
        const val MAX_KEYS = 48

        private val NONE = IntArray(0)
    }
}
