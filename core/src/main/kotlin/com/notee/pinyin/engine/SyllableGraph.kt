package com.notee.pinyin.engine

/** One way to read a syllable between two input positions. A [partial] edge reads only its first letters. */
class SyllableEdge(val to: Int, val syllable: Int, val partial: Boolean)

/**
 * Every way the pending input splits into syllables. Nodes are input positions from [start] to
 * [end]; edges always point forward.
 */
class SyllableGraph(val start: Int, val end: Int, private val edges: Array<out List<SyllableEdge>>) {

    fun edgesFrom(node: Int): List<SyllableEdge> = edges.getOrNull(node).orEmpty()

    val isEmpty: Boolean get() = edgesFrom(start).isEmpty()

    /** Some edge leads straight from start to end: the whole input is one syllable. */
    val isSingleSyllable: Boolean get() = edgesFrom(start).any { it.to == end && !it.partial }

    /** Whole syllables, one after another, cover the input from start to end. */
    val isWhole: Boolean
        get() {
            if (start >= end) return false
            val reached = BooleanArray(end + 1)
            reached[start] = true
            for (node in start until end) {
                if (!reached[node]) continue
                edgesFrom(node).forEach { if (!it.partial) reached[it.to] = true }
            }
            return reached[end]
        }

    /** Complete edges from every node the start can reach; [read] names what `start until end` spells exactly. */
    private class Builder(val input: CharSequence, from: Int) {
        val start = skipDelimiters(input, from)
        val end = input.length
        val edges = Array(end + 1) { ArrayList<SyllableEdge>(2) }
        val reachable = BooleanArray(end + 1)

        fun addWhole(maxLength: Int, read: (start: Int, end: Int) -> IntArray) {
            reachable[start] = true
            for (i in start until end) {
                if (!reachable[i]) continue
                var to = i + 1
                while (to - i <= maxLength && to <= end && input[to - 1] != DELIMITER) {
                    val ids = read(i, to)
                    if (ids.isNotEmpty()) {
                        val next = skipDelimiters(input, to)
                        ids.forEach { edges[i] += SyllableEdge(next, it, partial = false) }
                        reachable[next] = true
                    }
                    to++
                }
            }
        }

        fun build() = SyllableGraph(start, end, edges)
    }

    companion object {
        val EMPTY = SyllableGraph(0, 0, emptyArray())

        private val NONE = IntArray(0)

        /**
         * 26-key letters. `'` forces a boundary. Partial edges are added at the tail only when
         * complete syllables cannot reach the end, so `zhongg` offers 中国 while `xian` is never read as `xia n…`.
         */
        fun ofLetters(input: CharSequence, from: Int, syllables: SyllableTable): SyllableGraph {
            val builder = Builder(input, from)
            if (builder.start >= builder.end) return EMPTY
            builder.addWhole(syllables.maxLength) { start, end ->
                val id = syllables.id(input.subSequence(start, end))
                if (id == SyllableTable.NONE) NONE else intArrayOf(id)
            }
            val n = builder.end
            if (!builder.reachable[n]) {
                for (i in builder.start until n) {
                    if (!builder.reachable[i] || n - i > syllables.maxLength) continue
                    val tail = input.subSequence(i, n)
                    if (tail.contains(DELIMITER)) continue
                    for (id in syllables.completions(tail)) builder.edges[i] += SyllableEdge(n, id, partial = true)
                }
            }
            return builder.build()
        }

        /**
         * 9-key keys, one key per letter. `'` forces a boundary; [read] gives every syllable that
         * keys `start until end` spell. No partial edges: keys that only begin a syllable are not one
         * (docs/01-0 §4).
         */
        fun ofKeys(input: CharSequence, from: Int, maxLength: Int, read: (start: Int, end: Int) -> IntArray): SyllableGraph {
            val builder = Builder(input, from)
            if (builder.start >= builder.end) return EMPTY
            builder.addWhole(maxLength, read)
            return builder.build()
        }

        const val DELIMITER = '\''

        fun skipDelimiters(input: CharSequence, from: Int): Int {
            var at = from
            while (at < input.length && input[at] == DELIMITER) at++
            return at
        }
    }
}
