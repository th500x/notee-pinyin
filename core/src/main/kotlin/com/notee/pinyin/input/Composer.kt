package com.notee.pinyin.input

import com.notee.pinyin.engine.Candidate
import com.notee.pinyin.engine.CandidateList
import com.notee.pinyin.engine.SyllableGraph
import com.notee.pinyin.engine.SyllableTable
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.engine.WordSearch
import com.notee.pinyin.rank.CandidateRanker

/** A syllable the user can tap in the 9-key preedit line to fix how the next keys read. */
class SyllableChoice(val syllable: Int, val spelling: String)

/** The preedit line: text already chosen, then the pinyin still open, then 9-key [choices]. */
class Preedit(val chosen: String, val pinyin: String, val choices: List<SyllableChoice>) {
    companion object {
        val EMPTY = Preedit("", "", emptyList())
    }
}

/**
 * One composition: raw input, the words chosen so far from its front, and the candidates for the rest.
 * Committing returns the finished text; the caller sends it to the editor.
 */
abstract class Composer internal constructor(
    protected val syllables: SyllableTable,
    private val search: WordSearch,
    private val ranker: CandidateRanker,
    protected val user: UserDictionary,
) {
    private class Selection(val text: String, val syllables: IntArray, val end: Int)

    private val selections = ArrayList<Selection>()
    private var cached: CandidateList? = null
    private var cachedRevision = -1

    abstract val isComposing: Boolean

    abstract val preedit: Preedit

    /** All typed pinyin as letters, for enter. */
    abstract fun rawText(): String

    abstract fun deleteBackward()

    protected abstract fun graph(): SyllableGraph

    /** Nothing typed is left without a chosen word. */
    protected abstract val isFullyChosen: Boolean

    /** Pinyin after the chosen words, for committing when nothing matches. */
    protected abstract fun openPinyin(): String

    protected abstract fun clearInput()

    /** Graph position where the open input starts. */
    protected val chosenEnd: Int get() = selections.lastOrNull()?.end ?: 0

    protected val chosenText: String get() = selections.joinToString("") { it.text }

    /** The syllables the chosen words were read with, in order. */
    protected val chosenSyllables: List<Int> get() = selections.flatMap { it.syllables.asList() }

    val candidates: CandidateList
        get() {
            val ready = cached
            if (ready != null && cachedRevision == user.revision) return ready
            return compute().also {
                cached = it
                cachedRevision = user.revision
            }
        }

    /** Takes [candidate]. Returns the text to commit once nothing is left open, else null. */
    fun select(candidate: Candidate): String? {
        selections += Selection(candidate.text, candidate.syllables.copyOf(), candidate.end)
        changed()
        if (!isFullyChosen) return null
        learn()
        return finish(chosenText)
    }

    /** Space: the first candidate, or the open pinyin itself when nothing matches. */
    fun selectFirst(): String? {
        val first = candidates.first ?: return finish(chosenText + openPinyin())
        return select(first)
    }

    /** Before a symbol or a page change: first candidates until the composition is done. */
    fun commitAll(): String {
        if (!isComposing) return ""
        while (true) {
            selectFirst()?.let { return it }
        }
    }

    fun clear() {
        selections.clear()
        clearInput()
        changed()
    }

    protected fun changed() {
        cached = null
    }

    /**
     * After a delete left the input ending at [end]: chosen words that reach past it are dropped,
     * and so is the last one when nothing open would be left after it.
     */
    protected fun reopenSelections(end: Int) {
        while (selections.isNotEmpty() && (selections.last().end > end || isFullyChosen)) {
            selections.removeAt(selections.lastIndex)
        }
    }

    /**
     * Words reached by one adjacent swap.
     * [longestPiece] is the heaviest multi-syllable word inside the sentence, or 0.
     */
    protected open fun corrections(
        graph: SyllableGraph,
        words: List<Candidate>,
        sentence: Candidate?,
        longestPiece: Int,
    ): List<Candidate> = emptyList()

    private fun compute(): CandidateList {
        val graph = graph()
        val words = if (graph.isEmpty) emptyList() else search.wordsFromStart(graph)
        val path = if (graph.isEmpty) null else search.sentencePath(graph)
        return ranker.arrange(graph, words, path?.candidate, corrections(graph, words, path?.candidate, path?.longestPiece ?: 0))
    }

    /** Each pick counts once; a phrase put together from several picks is learned as a word too. */
    private fun learn() {
        for (selection in selections) {
            if (selection.syllables.size <= MAX_LEARNED_SYLLABLES) {
                user.record(syllables.code(selection.syllables), selection.text)
            }
        }
        if (selections.size < 2) return
        val all = chosenSyllables.toIntArray()
        if (all.size <= MAX_LEARNED_SYLLABLES) user.record(syllables.code(all), chosenText)
    }

    private fun finish(text: String): String {
        clear()
        return text
    }

    companion object {
        /** Long sentences are not worth remembering as words. */
        const val MAX_LEARNED_SYLLABLES = 6
    }
}
