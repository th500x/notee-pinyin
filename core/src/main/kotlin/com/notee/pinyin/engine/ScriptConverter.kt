package com.notee.pinyin.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Simplified to Traditional by longest match over the OpenCC `s2t` phrase and character tables. */
class ScriptConverter private constructor(buffer: ByteBuffer) {
    private val data: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val count: Int
    private val maxKeyLength: Int
    private val atKeyStarts: Int
    private val atValueStarts: Int
    private val atKeyPool: Int
    private val atValuePool: Int

    init {
        check(data.getInt(ScriptFormat.AT_MAGIC) == ScriptFormat.MAGIC) { "not a script table" }
        check(data.getInt(ScriptFormat.AT_VERSION) == ScriptFormat.VERSION) { "script table version mismatch" }
        count = data.getInt(ScriptFormat.AT_COUNT)
        maxKeyLength = data.getInt(ScriptFormat.AT_MAX_KEY_LENGTH)
        atKeyStarts = data.getInt(ScriptFormat.AT_KEY_STARTS)
        atValueStarts = data.getInt(ScriptFormat.AT_VALUE_STARTS)
        atKeyPool = data.getInt(ScriptFormat.AT_KEY_POOL)
        atValuePool = data.getInt(ScriptFormat.AT_VALUE_POOL)
    }

    fun toTraditional(text: String): String {
        val out = StringBuilder(text.length)
        var at = 0
        while (at < text.length) {
            var matched = false
            var length = minOf(maxKeyLength, text.length - at)
            while (length > 0) {
                val index = find(text, at, length)
                if (index >= 0) {
                    appendValue(out, index)
                    at += length
                    matched = true
                    break
                }
                length--
            }
            if (!matched) out.append(text[at++])
        }
        return out.toString()
    }

    private fun find(text: String, from: Int, length: Int): Int {
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compareKey(mid, text, from, length)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun compareKey(index: Int, text: String, from: Int, length: Int): Int {
        val start = u32(atKeyStarts, index)
        val keyLength = u32(atKeyStarts, index + 1) - start
        val n = minOf(keyLength, length)
        for (i in 0 until n) {
            val c = data.getChar(atKeyPool + 2 * (start + i)).compareTo(text[from + i])
            if (c != 0) return c
        }
        return keyLength.compareTo(length)
    }

    private fun appendValue(out: StringBuilder, index: Int) {
        val from = u32(atValueStarts, index)
        val to = u32(atValueStarts, index + 1)
        for (i in from until to) out.append(data.getChar(atValuePool + 2 * i))
    }

    private fun u32(base: Int, index: Int): Int = data.getInt(base + 4 * index)

    companion object {
        fun from(buffer: ByteBuffer): ScriptConverter = ScriptConverter(buffer)

        fun open(file: File): ScriptConverter =
            RandomAccessFile(file, "r").use { raf ->
                ScriptConverter(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()))
            }
    }
}
