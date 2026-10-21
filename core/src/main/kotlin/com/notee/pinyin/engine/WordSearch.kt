package com.notee.pinyin.engine

import kotlin.math.ln

/** A sentence covering the graph, and the heaviest multi-syllable piece inside it. */
class SentencePath(val candidate: Candidate, val longestPiece: Int)

/** Finds dictionary and user words along a [SyllableGraph]. */
class WordSearch(private val lexicon: Lexicon, private val user: UserDictionary) {
    private val syllables = lexicon.syllables
    private val logTotal = ln(lexicon.totalWeight.coerceAtLeast(1L).toDouble())

    private fun interface Visitor {
        fun visit(ids: IntArray, length: Int, code: String, to: Int, hit: LexiconHit)
    }

    /** Every word, of any length, that begins at the graph start. */
    fun wordsFromStart(graph: SyllableGraph): List<Candidate> {
        val words = ArrayList<Candidate>()
        walk(graph, graph.start, IntArray(MAX_SYLLABLES), 0, "", withUser = true) { ids, length, code, to, hit ->
            val read = ids.copyOf(length)
            hit.entries.forEach { words += Candidate(it.text, read, to, it.weight.toLong()) }
            val learned = user.words(code)
            if (learned.isNotEmpty()) {
                val known = hit.entries.mapTo(HashSet()) { it.text }
                learned.keys.filter { it !in known }.forEach { words += Candidate(it, read, to, 0L) }
            }
        }
        return words
    }

    /**
     * The most probable chain of dictionary words over the whole graph, scoring each word by its
     * share of the total weight. Null unless the chain has two words or more; one word is already a candidate.
     */
    fun sentence(graph: SyllableGraph): Candidate? = sentencePath(graph)?.candidate

    /**
     * The best sentence, plus the heaviest dictionary piece in it that is two syllables or longer.
     * Zero when every piece is a single character. 9-key correction uses that weight so a light
     * phrase does not hide a much more common repaired word.
     */
    fun sentencePath(graph: SyllableGraph): SentencePath? {
        class Step(val score: Double, val words: Int, val text: String, val ids: IntArray, val longestPiece: Int)

        val best = arrayOfNulls<Step>(graph.end + 1)
        best[graph.start] = Step(0.0, 0, "", IntArray(0), 0)
        val scratch = IntArray(MAX_SYLLABLES)
        for (node in graph.start until graph.end) {
            val here = best[node] ?: continue
            walk(graph, node, scratch, 0, "", withUser = false) { ids, length, _, to, hit ->
                val top = hit.entries.firstOrNull() ?: return@walk
                val penalty = if (length == 1) CHARACTER_PENALTY else 0.0
                val score = here.score + ln(top.weight.coerceAtLeast(1).toDouble()) - logTotal - penalty
                val old = best[to]
                if (old == null || score > old.score) {
                    val piece = if (length >= 2) top.weight else 0
                    best[to] = Step(
                        score, here.words + 1, here.text + top.text, here.ids + ids.copyOf(length),
                        maxOf(here.longestPiece, piece),
                    )
                }
            }
        }
        val last = best[graph.end] ?: return null
        if (last.words < 2) return null
        return SentencePath(Candidate(last.text, last.ids, graph.end, 0L), last.longestPiece)
    }

    private fun walk(
        graph: SyllableGraph,
        node: Int,
        ids: IntArray,
        depth: Int,
        code: String,
        withUser: Boolean,
        visitor: Visitor,
    ) {
        for (edge in graph.edgesFrom(node)) {
            ids[depth] = edge.syllable
            val length = depth + 1
            val spelling = syllables.spelling(edge.syllable)
            val extended = if (withUser) (if (depth == 0) spelling else "$code $spelling") else ""
            val hit = lexicon.lookup(ids, length)
            val userGoesOn = withUser && user.hasPrefix(extended)
            if (hit.isDeadEnd && !userGoesOn) continue
            visitor.visit(ids, length, extended, edge.to, hit)
            if (length < MAX_SYLLABLES && (hit.continues || userGoesOn)) {
                walk(graph, edge.to, ids, length, extended, withUser, visitor)
            }
        }
    }

    companion object {
        /** Longer runs are left to sentence building; no dictionary word is that long in practice. */
        const val MAX_SYLLABLES = 12

        /**
         * Character weights come from a character-frequency table and run about ten times above word
         * weights for the same use, so `现在是 + 见` would beat `现在 + 时间` without this.
         */
        private val CHARACTER_PENALTY = ln(10.0)
    }
}
