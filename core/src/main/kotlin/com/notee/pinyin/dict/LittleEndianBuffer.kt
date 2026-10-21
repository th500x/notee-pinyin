package com.notee.pinyin.dict

import java.io.ByteArrayOutputStream
import java.io.File

/** Append-only little-endian byte sink for the compiled tables. */
internal class LittleEndianBuffer {
    private val bytes = ByteArrayOutputStream()
    private val headerSlots = HashMap<Int, Int>()

    val size: Int get() = bytes.size()

    fun u8(value: Int) = bytes.write(value and 0xFF)

    fun u16(value: Int) {
        u8(value)
        u8(value ushr 8)
    }

    fun u32(value: Int) {
        u16(value)
        u16(value ushr 16)
    }

    fun u64(value: Long) {
        u32(value.toInt())
        u32((value ushr 32).toInt())
    }

    fun chars(text: CharSequence) {
        for (c in text) u16(c.code)
    }

    fun bytes(value: ByteArray) = bytes.write(value)

    fun align4() {
        while (size % 4 != 0) u8(0)
    }

    /** Fills a header u32 that was written as a placeholder, typically a section offset. */
    fun setU32(at: Int, value: Int) {
        headerSlots[at] = value
    }

    fun writeTo(file: File) {
        val out = bytes.toByteArray()
        for ((at, value) in headerSlots) {
            for (i in 0 until 4) out[at + i] = (value ushr (8 * i)).toByte()
        }
        file.parentFile?.mkdirs()
        file.writeBytes(out)
    }
}
