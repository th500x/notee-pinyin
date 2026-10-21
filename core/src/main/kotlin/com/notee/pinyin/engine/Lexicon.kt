package com.notee.pinyin.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class LexiconEntry(val text: String, val weight: Int)

/** What one code holds and whether longer codes continue it. */
class LexiconHit(val entries: List<LexiconEntry>, val continues: Boolean) {
    val isDeadEnd: Boolean get() = entries.isEmpty() && !continues

    companion object {
        val NONE = LexiconHit(emptyList(), continues = false)
    }
}

/** Read-only view of `lexicon.bin`. The buffer is usually a memory map, so lookups cost no heap. */
class Lexicon private constructor(buffer: ByteBuffer) {
    private val data: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val codeCount: Int
    private val atCodeStarts: Int
    private val atEntryStarts: Int
    private val atTextStarts: Int
    private val atEntryWeights: Int
    private val atCodePool: Int
    private val atTextPool: Int
    private val associationChars: Int
    private val atAssociationChars: Int
    private val atAssociationStarts: Int
    private val atAssociationPool: Int

    val syllables: SyllableTable

    /** The pin table text as compiled in, `syllable<TAB>chars` per line. */
    val pinText: String

    /** Sum of every entry weight. Turns weights into probabilities when building sentences. */
    val totalWeight: Long

    init {
        check(data.getInt(LexiconFormat.AT_MAGIC) == LexiconFormat.MAGIC) { "not a lexicon" }
        check(data.getInt(LexiconFormat.AT_VERSION) == LexiconFormat.VERSION) { "lexicon version mismatch" }
        val syllableCount = data.getInt(LexiconFormat.AT_SYLLABLE_COUNT)
        codeCount = data.getInt(LexiconFormat.AT_CODE_COUNT)
        atCodeStarts = data.getInt(LexiconFormat.AT_CODE_STARTS)
        atEntryStarts = data.getInt(LexiconFormat.AT_ENTRY_STARTS)
        atTextStarts = data.getInt(LexiconFormat.AT_TEXT_STARTS)
        atEntryWeights = data.getInt(LexiconFormat.AT_ENTRY_WEIGHTS)
        atCodePool = data.getInt(LexiconFormat.AT_CODE_POOL)
        atTextPool = data.getInt(LexiconFormat.AT_TEXT_POOL)
        totalWeight = data.getLong(LexiconFormat.AT_TOTAL_WEIGHT)

        var at = data.getInt(LexiconFormat.AT_SYLLABLE_SPELLINGS)
        val spellings = ArrayList<String>(syllableCount)
        repeat(syllableCount) {
            val length = data.get(at).toInt() and 0xFF
            val bytes = ByteArray(length) { i -> data.get(at + 1 + i) }
            spellings += String(bytes, Charsets.US_ASCII)
            at += 1 + length
        }
        val atWeights = data.getInt(LexiconFormat.AT_SYLLABLE_WEIGHTS)
        syllables = SyllableTable(spellings, IntArray(syllableCount) { data.getInt(atWeights + 4 * it) })

        val atPins = data.getInt(LexiconFormat.AT_PINS)
        val pinBytes = ByteArray(data.getInt(atPins)) { data.get(atPins + 4 + it) }
        pinText = String(pinBytes, Charsets.UTF_8)

        val atAssociation = data.getInt(LexiconFormat.AT_ASSOCIATION)
        associationChars = data.getInt(atAssociation)
        atAssociationChars = atAssociation + 4
        atAssociationStarts = aligned(atAssociationChars + 2 * associationChars)
        atAssociationPool = atAssociationStarts + 4 * (associationChars + 1)
    }

    /**
     * Rest of the heaviest dictionary words that start with [stem]: longer remainders first, then
     * single characters. Within each, heaviest first. The same remainder is kept once.
     */
    fun suffixes(stem: String): List<LexiconEntry> {
        if (stem.isEmpty()) return emptyList()
        val group = associationGroup(stem[0])
        if (group < 0) return emptyList()
        val from = u32(atAssociationStarts, group)
        val to = minOf(u32(atAssociationStarts, group + 1), from + ASSOCIATION_SCAN)
        val seen = HashSet<String>()
        val words = ArrayList<LexiconEntry>(ASSOCIATION_WORDS)
        val chars = ArrayList<LexiconEntry>(ASSOCIATION_CHARS)
        for (i in from until to) {
            if (words.size >= ASSOCIATION_WORDS && chars.size >= ASSOCIATION_CHARS) break
            val entry = data.getInt(atAssociationPool + 4 * i)
            val full = text(entry)
            if (full.length <= stem.length || !full.startsWith(stem)) continue
            val suffix = full.substring(stem.length)
            if (!seen.add(suffix)) continue
            val hit = LexiconEntry(suffix, u32(atEntryWeights, entry))
            if (suffix.length >= 2) {
                if (words.size < ASSOCIATION_WORDS) words += hit
            } else if (chars.size < ASSOCIATION_CHARS) {
                chars += hit
            }
        }
        return words + chars
    }

    private fun associationGroup(char: Char): Int {
        var lo = 0
        var hi = associationChars
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val found = data.getChar(atAssociationChars + 2 * mid)
            if (found < char) lo = mid + 1 else hi = mid
        }
        if (lo >= associationChars || data.getChar(atAssociationChars + 2 * lo) != char) return -1
        return lo
    }

    /**
     * Every code, heaviest entry first in [topWeight]. [ids] is reused on the next call;
     * copy whatever must outlive this visit. Single-syllable codes are included.
     */
    fun forEachCode(action: (ids: IntArray, length: Int, topWeight: Int) -> Unit) {
        var widest = 1
        for (index in 0 until codeCount) widest = maxOf(widest, codeLength(index))
        val ids = IntArray(widest)
        for (index in 0 until codeCount) {
            val length = codeLength(index)
            for (i in 0 until length) ids[i] = syllableAt(index, i)
            val from = u32(atEntryStarts, index)
            val to = u32(atEntryStarts, index + 1)
            var top = 0
            for (entry in from until to) top = maxOf(top, u32(atEntryWeights, entry))
            action(ids, length, top)
        }
    }

    fun lookup(code: IntArray, length: Int = code.size): LexiconHit {
        val index = lowerBound(code, length)
        if (index >= codeCount) return LexiconHit.NONE
        val exact = codeLength(index) == length && startsWith(index, code, length)
        val next = if (exact) index + 1 else index
        val continues = next < codeCount && startsWith(next, code, length)
        if (!exact) return if (continues) LexiconHit(emptyList(), true) else LexiconHit.NONE
        return LexiconHit(entries(index), continues)
    }

    private fun entries(codeIndex: Int): List<LexiconEntry> {
        val from = u32(atEntryStarts, codeIndex)
        val to = u32(atEntryStarts, codeIndex + 1)
        return List(to - from) { i -> LexiconEntry(text(from + i), u32(atEntryWeights, from + i)) }
    }

    private fun text(entry: Int): String {
        val from = u32(atTextStarts, entry)
        val to = u32(atTextStarts, entry + 1)
        val chars = CharArray(to - from) { i -> data.getChar(atTextPool + 2 * (from + i)) }
        return String(chars)
    }

    private fun codeLength(index: Int): Int = u32(atCodeStarts, index + 1) - u32(atCodeStarts, index)

    private fun syllableAt(index: Int, position: Int): Int =
        data.getShort(atCodePool + 2 * (u32(atCodeStarts, index) + position)).toInt() and 0xFFFF

    private fun startsWith(index: Int, code: IntArray, length: Int): Boolean {
        if (codeLength(index) < length) return false
        for (i in 0 until length) {
            if (syllableAt(index, i) != code[i]) return false
        }
        return true
    }

    private fun compareAt(index: Int, code: IntArray, length: Int): Int {
        val recordLength = codeLength(index)
        val n = minOf(recordLength, length)
        for (i in 0 until n) {
            val c = syllableAt(index, i).compareTo(code[i])
            if (c != 0) return c
        }
        return recordLength.compareTo(length)
    }

    private fun lowerBound(code: IntArray, length: Int): Int {
        var lo = 0
        var hi = codeCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareAt(mid, code, length) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun u32(base: Int, index: Int): Int = data.getInt(base + 4 * index)

    companion object {
        /** Heavier matches than this are enough; the rest of a common character is noise. */
        private const val ASSOCIATION_SCAN = 8192

        /** Few enough that single characters still show up on the next grid page. */
        private const val ASSOCIATION_WORDS = 16
        private const val ASSOCIATION_CHARS = 32

        private fun aligned(offset: Int): Int = (offset + 3) and 3.inv()

        fun from(buffer: ByteBuffer): Lexicon = Lexicon(buffer)

        fun open(file: File): Lexicon =
            RandomAccessFile(file, "r").use { raf ->
                Lexicon(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()))
            }
    }
}
