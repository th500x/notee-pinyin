package com.notee.pinyin.dict

import com.notee.pinyin.engine.LexiconFormat
import com.notee.pinyin.engine.SyllableTable
import com.notee.pinyin.rank.PinTable
import java.io.File
import java.util.TreeMap

/**
 * Builds `lexicon.bin` from the rime-ice character table, the word tables and the pin table.
 *
 * The character table defines the syllable set. Rows are merged in file order and the first
 * row for a (text, code) pair wins. Rows that use a syllable outside the set are dropped.
 */
object LexiconCompiler {

    class Report(val syllables: Int, val codes: Int, val entries: Int, val dropped: Int)

    private class Code(val ids: IntArray, val entries: MutableList<RimeEntry>)

    fun compile(characters: File, words: List<File>, pins: File, output: File): Report {
        val characterRows = RimeDict.read(characters)
        val syllables = syllableTable(characterRows)

        val codes = HashMap<String, Code>()
        val seen = HashSet<String>()
        var dropped = 0
        fun add(rows: List<RimeEntry>) {
            for (row in rows) {
                if (!seen.add(row.text + '\t' + row.code)) continue
                val code = codes[row.code] ?: run {
                    val ids = row.code.split(' ').map(syllables::id).toIntArray()
                    if (ids.any { it == SyllableTable.NONE }) null else Code(ids, ArrayList(1)).also { codes[row.code] = it }
                }
                if (code == null) dropped++ else code.entries += row
            }
        }
        add(characterRows)
        words.forEach { add(RimeDict.read(it)) }

        val pinText = pins.readText(Charsets.UTF_8)
        checkPins(PinTable.parse(pinText, syllables), syllables, codes)

        val sorted = codes.values.sortedWith { a, b -> LexiconFormat.compareCodes(a.ids, a.ids.size, b.ids, b.ids.size) }
        sorted.forEach { code -> code.entries.sortByDescending { it.weight } }
        write(output, syllables, pinText, sorted)
        return Report(syllables.size, sorted.size, sorted.sumOf { it.entries.size }, dropped)
    }

    private fun syllableTable(characterRows: List<RimeEntry>): SyllableTable {
        val weights = TreeMap<String, Long>()
        for (row in characterRows) {
            if (' ' !in row.code) weights.merge(row.code, row.weight.toLong(), Long::plus)
        }
        check(weights.size < 0xFFFF) { "syllable ids must fit in u16" }
        return SyllableTable(
            weights.keys.toList(),
            weights.values.map { it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }.toIntArray(),
        )
    }

    /** Every pinned character must be in the dictionary under its syllable, or it could never show. */
    private fun checkPins(pins: PinTable, syllables: SyllableTable, codes: Map<String, Code>) {
        for (id in 0 until syllables.size) {
            val spelling = syllables.spelling(id)
            val texts = codes[spelling]?.entries?.mapTo(HashSet()) { it.text }.orEmpty()
            for (char in pins.pins(id)) {
                require(char in texts) { "pin $char is not read $spelling in the dictionary" }
            }
        }
    }

    private fun write(output: File, syllables: SyllableTable, pinText: String, codes: List<Code>) {
        val out = LittleEndianBuffer()
        repeat(LexiconFormat.HEADER_SIZE / 4) { out.u32(0) }
        val entryCount = codes.sumOf { it.entries.size }
        val totalWeight = codes.sumOf { code -> code.entries.sumOf { it.weight.toLong() } }
        out.setU32(LexiconFormat.AT_MAGIC, LexiconFormat.MAGIC)
        out.setU32(LexiconFormat.AT_VERSION, LexiconFormat.VERSION)
        out.setU32(LexiconFormat.AT_SYLLABLE_COUNT, syllables.size)
        out.setU32(LexiconFormat.AT_CODE_COUNT, codes.size)
        out.setU32(LexiconFormat.AT_ENTRY_COUNT, entryCount)
        out.setU32(LexiconFormat.AT_TOTAL_WEIGHT, totalWeight.toInt())
        out.setU32(LexiconFormat.AT_TOTAL_WEIGHT + 4, (totalWeight ushr 32).toInt())

        out.setU32(LexiconFormat.AT_SYLLABLE_SPELLINGS, out.size)
        for (id in 0 until syllables.size) {
            val ascii = syllables.spelling(id).toByteArray(Charsets.US_ASCII)
            out.u8(ascii.size)
            out.bytes(ascii)
        }
        out.align4()

        out.setU32(LexiconFormat.AT_SYLLABLE_WEIGHTS, out.size)
        for (id in 0 until syllables.size) out.u32(syllables.weight(id))

        out.setU32(LexiconFormat.AT_PINS, out.size)
        val pinBytes = pinText.toByteArray(Charsets.UTF_8)
        out.u32(pinBytes.size)
        out.bytes(pinBytes)
        out.align4()

        out.setU32(LexiconFormat.AT_CODE_STARTS, out.size)
        var codeStart = 0
        for (code in codes) {
            out.u32(codeStart)
            codeStart += code.ids.size
        }
        out.u32(codeStart)

        out.setU32(LexiconFormat.AT_ENTRY_STARTS, out.size)
        var entryStart = 0
        for (code in codes) {
            out.u32(entryStart)
            entryStart += code.entries.size
        }
        out.u32(entryStart)

        out.setU32(LexiconFormat.AT_TEXT_STARTS, out.size)
        var textStart = 0
        for (code in codes) {
            for (entry in code.entries) {
                out.u32(textStart)
                textStart += entry.text.length
            }
        }
        out.u32(textStart)

        out.setU32(LexiconFormat.AT_ENTRY_WEIGHTS, out.size)
        for (code in codes) code.entries.forEach { out.u32(it.weight) }

        out.setU32(LexiconFormat.AT_CODE_POOL, out.size)
        for (code in codes) code.ids.forEach(out::u16)
        out.align4()

        out.setU32(LexiconFormat.AT_TEXT_POOL, out.size)
        for (code in codes) code.entries.forEach { out.chars(it.text) }

        writeAssociations(out, codes, entryCount)
        out.writeTo(output)
    }

    /** Words of two characters or more, grouped by their first character, heaviest first in each group. */
    private fun writeAssociations(out: LittleEndianBuffer, codes: List<Code>, entryCount: Int) {
        val weightOf = IntArray(entryCount)
        val byChar = HashMap<Char, ArrayList<Int>>()
        var entry = 0
        for (code in codes) {
            for (row in code.entries) {
                weightOf[entry] = row.weight
                if (row.text.length >= 2) byChar.getOrPut(row.text[0]) { ArrayList(4) }.add(entry)
                entry++
            }
        }
        val chars = byChar.keys.sorted()
        for (list in byChar.values) list.sortByDescending { weightOf[it] }

        out.align4()
        out.setU32(LexiconFormat.AT_ASSOCIATION, out.size)
        out.u32(chars.size)
        chars.forEach { out.u16(it.code) }
        out.align4()
        var pool = 0
        for (char in chars) {
            out.u32(pool)
            pool += byChar.getValue(char).size
        }
        out.u32(pool)
        for (char in chars) byChar.getValue(char).forEach(out::u32)
    }
}
