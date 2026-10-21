package com.notee.pinyin.engine

/**
 * A word or sentence that reads [syllables] from the graph start to node [end].
 * [weight] is the dictionary weight, 0 for words only the user dictionary knows.
 */
class Candidate(
    val text: String,
    val syllables: IntArray,
    val end: Int,
    val weight: Long,
    /** Found by swapping one adjacent pair. The preedit keeps the keys actually pressed. */
    val corrected: Boolean = false,
)

/** What the candidate row shows, in order. [Bar] is the `|` between pins and the rest. */
sealed interface CandidateItem {
    class Word(val candidate: Candidate) : CandidateItem

    data object Bar : CandidateItem
}

class CandidateList(val items: List<CandidateItem>) {
    val first: Candidate? = items.firstNotNullOfOrNull { (it as? CandidateItem.Word)?.candidate }

    val isEmpty: Boolean get() = first == null

    companion object {
        val EMPTY = CandidateList(emptyList())
    }
}
