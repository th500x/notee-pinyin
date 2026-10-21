package com.notee.pinyin.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Completions are sorted by lowercase spelling. Next-word heads are too.
 * A prefix scan is one ordered walk; twenty thousand words need no extra index.
 */
class EnglishLexicon internal constructor(buffer: ByteBuffer) {
    private val data: ByteBuffer = buffer.duplicate().apply { order(ByteOrder.LITTLE_ENDIAN) }

    init {
        require(data.getInt(EnglishFormat.AT_MAGIC) == EnglishFormat.MAGIC) { "Not an English table" }
        require(data.getInt(EnglishFormat.AT_VERSION) == EnglishFormat.VERSION) { "Unsupported English table" }
    }

    private val wordCount = data.getInt(EnglishFormat.AT_WORD_COUNT)
    private val wordsAt = data.getInt(EnglishFormat.AT_WORDS)
    private val nextCount = data.getInt(EnglishFormat.AT_NEXT_COUNT)
    private val nextAt = data.getInt(EnglishFormat.AT_NEXT)

    /** Dictionary spellings whose lowercase form starts with [typed], exact match first. */
    fun completions(typed: String): List<String> {
        val prefix = typed.lowercase()
        if (prefix.isEmpty() || wordCount == 0) return emptyList()
        var index = lowerBound(wordsAt, wordCount, prefix)
        var exact: String? = null
        val rest = ArrayList<Scored>(EnglishFormat.COMPLETIONS)
        while (index < wordCount) {
            val word = readWord(wordsAt, index)
            if (!word.key.startsWith(prefix)) break
            if (word.key == prefix) exact = word.display
            else rest += word
            index++
        }
        rest.sortByDescending { it.weight }
        val out = ArrayList<String>(EnglishFormat.COMPLETIONS)
        if (exact != null) out += exact
        for (word in rest) {
            if (out.size == EnglishFormat.COMPLETIONS) break
            out += word.display
        }
        return out
    }

    /** Words that follow [word], already ordered for display. Empty when unknown. */
    fun following(word: String): List<String> {
        val key = word.trim().lowercase()
        if (key.isEmpty() || nextCount == 0) return emptyList()
        val index = lowerBound(nextAt, nextCount, key)
        if (index >= nextCount) return emptyList()
        val head = readNext(index)
        return if (head.key == key) head.words else emptyList()
    }

    private fun lowerBound(section: Int, count: Int, key: String): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) ushr 1
            if (keyAt(section, mid) < key) low = mid + 1 else high = mid
        }
        return low
    }

    private fun keyAt(section: Int, index: Int): String {
        val at = recordAt(section, index)
        return text(at + 1, u8(at))
    }

    private fun readWord(section: Int, index: Int): Scored {
        val at = recordAt(section, index)
        val keyLen = u8(at)
        val key = text(at + 1, keyLen)
        val displayLen = u8(at + 1 + keyLen)
        val displayAt = at + 2 + keyLen
        val display = text(displayAt, displayLen)
        val weight = data.getShort(displayAt + displayLen).toInt() and 0xFFFF
        return Scored(key, display, weight)
    }

    private fun readNext(index: Int): Next {
        val at = recordAt(nextAt, index)
        val keyLen = u8(at)
        val key = text(at + 1, keyLen)
        val count = u8(at + 1 + keyLen)
        var cursor = at + 2 + keyLen
        val words = ArrayList<String>(count)
        repeat(count) {
            val length = u8(cursor)
            words += text(cursor + 1, length)
            cursor += 1 + length
        }
        return Next(key, words)
    }

    private fun recordAt(section: Int, index: Int): Int {
        val offsets = section + 4
        return offsets + 4 * data.getInt(section) + data.getInt(offsets + 4 * index)
    }

    private fun u8(index: Int): Int = data.get(index).toInt() and 0xFF

    private fun text(index: Int, length: Int): String {
        val bytes = ByteArray(length)
        for (i in 0 until length) bytes[i] = data.get(index + i)
        return String(bytes, Charsets.UTF_8)
    }

    private class Scored(val key: String, val display: String, val weight: Int)
    private class Next(val key: String, val words: List<String>)

    companion object {
        fun from(buffer: ByteBuffer): EnglishLexicon = EnglishLexicon(buffer)

        fun open(file: File): EnglishLexicon =
            RandomAccessFile(file, "r").use { file ->
                EnglishLexicon(file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()))
            }
    }
}
