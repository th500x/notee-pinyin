package com.notee.pinyin.input

import com.notee.pinyin.engine.Candidate
import com.notee.pinyin.engine.SyllableGraph
import com.notee.pinyin.engine.SyllableTable
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.engine.WordSearch
import com.notee.pinyin.rank.CandidateRanker

/** 26-key composition: the letters are the pinyin, `'` marks a syllable boundary. */
class QwertyComposer internal constructor(
    syllables: SyllableTable,
    search: WordSearch,
    ranker: CandidateRanker,
    user: UserDictionary,
    private val transposition: Transposition,
) : Composer(syllables, search, ranker, user) {
    private val input = StringBuilder()

    override val isComposing: Boolean get() = input.isNotEmpty()

    override val preedit: Preedit get() = Preedit(chosenText, openPinyin(), emptyList())

    fun type(letter: Char) {
        if (input.length >= MAX_INPUT) return
        input.append(letter)
        changed()
    }

    fun separate() {
        if (input.isEmpty() || input.last() == SyllableGraph.DELIMITER || input.length >= MAX_INPUT) return
        input.append(SyllableGraph.DELIMITER)
        changed()
    }

    override fun deleteBackward() {
        if (input.isEmpty()) return
        input.setLength(input.length - 1)
        reopenSelections(input.length)
        changed()
    }

    override fun rawText(): String = input.toString()

    override fun graph(): SyllableGraph = SyllableGraph.ofLetters(input, chosenEnd, syllables)

    override val isFullyChosen: Boolean get() = SyllableGraph.skipDelimiters(input, chosenEnd) >= input.length

    override fun openPinyin(): String = input.substring(SyllableGraph.skipDelimiters(input, chosenEnd))

    override fun clearInput() = input.setLength(0)

    override fun corrections(
        graph: SyllableGraph,
        words: List<Candidate>,
        sentence: Candidate?,
        longestPiece: Int,
    ): List<Candidate> {
        val start = SyllableGraph.skipDelimiters(input, chosenEnd)
        if (start >= input.length) return emptyList()
        return transposition.letters(input.substring(start), input.length, graph, words, sentence)
    }

    companion object {
        const val MAX_INPUT = 64
    }
}
