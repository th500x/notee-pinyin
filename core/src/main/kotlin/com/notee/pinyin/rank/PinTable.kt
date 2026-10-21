package com.notee.pinyin.rank

import com.notee.pinyin.engine.SyllableTable

/** Fixed first characters per syllable (docs/03-0). They never move, whatever the user types. */
class PinTable private constructor(private val bySyllable: Map<Int, List<String>>) {

    fun pins(syllable: Int): List<String> = bySyllable[syllable].orEmpty()

    val syllableCount: Int get() = bySyllable.size

    companion object {
        const val MAX_PINS = 3

        /** Parses `syllable<TAB>char char char` lines. Fails on anything 03-0 would not allow. */
        fun parse(text: String, syllables: SyllableTable): PinTable {
            val bySyllable = HashMap<Int, List<String>>()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    val columns = line.split('\t')
                    require(columns.size == 2) { "pin line needs two columns: $line" }
                    val id = syllables.id(columns[0])
                    require(id != SyllableTable.NONE) { "pin syllable not in the table: ${columns[0]}" }
                    val chars = columns[1].split(' ').filter { it.isNotEmpty() }
                    require(chars.size in 1..MAX_PINS) { "pin line needs 1 to $MAX_PINS characters: $line" }
                    require(chars.distinct().size == chars.size) { "duplicate pin: $line" }
                    require(bySyllable.put(id, chars) == null) { "syllable pinned twice: ${columns[0]}" }
                }
            return PinTable(bySyllable)
        }
    }
}
