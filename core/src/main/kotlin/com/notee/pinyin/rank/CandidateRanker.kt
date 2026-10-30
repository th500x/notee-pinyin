package com.notee.pinyin.rank

import com.notee.pinyin.engine.Candidate
import com.notee.pinyin.engine.CandidateItem
import com.notee.pinyin.engine.CandidateList
import com.notee.pinyin.engine.SyllableEdge
import com.notee.pinyin.engine.SyllableGraph
import com.notee.pinyin.engine.SyllableTable
import com.notee.pinyin.engine.UserDictionary

/**
 * Orders the words found for a graph (docs/01-0).
 *
 * One syllable: its pins in table order, one `|`, then everything else by pick count and weight.
 * [OrderAdjustments] replaces that leading run for the few rows listed there.
 * Several syllables: the sentence, whole-input words, words covering a leading part (longest first),
 * then characters of the first syllable with their pins in front. Each text appears once.
 */
class CandidateRanker(
    private val syllables: SyllableTable,
    private val pins: PinTable,
    private val user: UserDictionary,
) {

    fun arrange(
        graph: SyllableGraph,
        words: List<Candidate>,
        sentence: Candidate?,
        corrections: List<Candidate> = emptyList(),
    ): CandidateList {
        if (corrections.isEmpty()) {
            if (words.isEmpty() && sentence == null) return CandidateList.EMPTY
            val items = if (graph.isSingleSyllable) oneSyllable(graph, words) else severalSyllables(graph, words, sentence)
            return CandidateList(items)
        }
        val items = Distinct()
        corrections.forEach(items::add)
        if (words.isNotEmpty() || sentence != null) {
            val rest = if (graph.isSingleSyllable) oneSyllable(graph, words) else severalSyllables(graph, words, sentence)
            for (item in rest) {
                when (item) {
                    is CandidateItem.Word -> items.add(item.candidate)
                    CandidateItem.Bar -> items.bar()
                }
            }
        }
        return CandidateList(items.result)
    }

    private fun oneSyllable(graph: SyllableGraph, words: List<Candidate>): List<CandidateItem> {
        val whole = words.filter { it.end == graph.end }
        val heads = graph.edgesFrom(graph.start)
            .filter { it.to == graph.end && !it.partial }
            .map { it.syllable }
            .distinct()
            .sortedByDescending(syllables::weight)
        val pinned = pinnedCharacters(heads, whole, group = true)
        val items = Distinct()
        pinned.forEach(items::add)
        if (pinned.isNotEmpty()) items.bar()
        byUse(whole).forEach(items::add)
        return items.result
    }

    private fun severalSyllables(graph: SyllableGraph, words: List<Candidate>, sentence: Candidate?): List<CandidateItem> {
        val items = Distinct()
        sentence?.let(items::add)
        byUse(words.filter { it.end == graph.end }).forEach(items::add)

        val leading = words.filter { it.end < graph.end && it.syllables.size >= 2 }
        leading.groupBy { it.end }.toSortedMap(reverseOrder()).values.forEach { group -> byUse(group).forEach(items::add) }

        val characters = words.filter { it.syllables.size == 1 }
        val heads = graph.edgesFrom(graph.start)
            .sortedWith(compareByDescending<SyllableEdge> { it.to }.thenByDescending { syllables.weight(it.syllable) })
            .map { it.syllable }
            .distinct()
        pinnedCharacters(heads, characters, group = false).forEach(items::add)
        byUse(characters).forEach(items::add)
        return phraseOrder(items.result)
    }

    /** Moves each phrase's earlier word to just before the later one (docs/03-0 §2). */
    private fun phraseOrder(items: List<CandidateItem>): List<CandidateItem> {
        val out = items.toMutableList()
        for (phrase in OrderAdjustments.phrases) {
            val atEarlier = out.indexOfFirst { (it as? CandidateItem.Word)?.candidate?.text == phrase.earlier }
            val atLater = out.indexOfFirst { (it as? CandidateItem.Word)?.candidate?.text == phrase.later }
            if (atEarlier < 0 || atLater < 0 || atEarlier < atLater) continue
            val word = out.removeAt(atEarlier)
            out.add(out.indexOfFirst { (it as? CandidateItem.Word)?.candidate?.text == phrase.later }, word)
        }
        return out
    }

    /**
     * Pins of each head syllable in turn, as long as the dictionary offers them for this input.
     * [group] is the single full-cover case: a matching [OrderAdjustments] row leads, by spelling
     * rather than by syllable weight.
     */
    private fun pinnedCharacters(heads: List<Int>, pool: List<Candidate>, group: Boolean): List<Candidate> {
        val single = pool.filter { it.syllables.size == 1 }
        if (group) {
            val leading = OrderAdjustments.together(heads.map { syllables.spelling(it) }.toSet())
            if (leading != null) {
                val seen = leading.toSet()
                return inTextOrder(leading, single) + syllablePins(heads, single).filter { it.text !in seen }
            }
        }
        return syllablePins(heads, single)
    }

    private fun syllablePins(heads: List<Int>, single: List<Candidate>): List<Candidate> {
        val pinned = ArrayList<Candidate>()
        for (syllable in heads) {
            val order = OrderAdjustments.pinOrder(syllables.spelling(syllable), pins.pins(syllable))
            for (char in order) {
                single.firstOrNull { it.syllables[0] == syllable && it.text == char }?.let(pinned::add)
            }
        }
        return pinned
    }

    private fun inTextOrder(chars: List<String>, single: List<Candidate>): List<Candidate> {
        val pinned = ArrayList<Candidate>()
        for (char in chars) single.firstOrNull { it.text == char }?.let(pinned::add)
        return pinned
    }

    /** Most picked first; equal counts keep dictionary weight order. */
    private fun byUse(words: List<Candidate>): List<Candidate> {
        if (words.size < 2) return words
        val counts = words.associateWith { user.count(syllables.code(it.syllables), it.text) }
        return words.sortedWith(compareByDescending<Candidate> { counts.getValue(it) }.thenByDescending { it.weight })
    }

    /** Keeps the first position of each text and stops at [MAX_CANDIDATES]. */
    private class Distinct {
        private val seen = HashSet<String>()
        val result = ArrayList<CandidateItem>()

        fun add(candidate: Candidate) {
            if (seen.size < MAX_CANDIDATES && seen.add(candidate.text)) result += CandidateItem.Word(candidate)
        }

        fun bar() {
            result += CandidateItem.Bar
        }
    }

    companion object {
        /** About ten full grid pages. Deeper words are faster to reach by typing more pinyin. */
        const val MAX_CANDIDATES = 400
    }
}
