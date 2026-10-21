package com.notee.pinyin.dict

import com.notee.pinyin.engine.ScriptFormat
import java.io.File

/**
 * Builds `s2t.bin` from OpenCC `key<TAB>value value…` tables. A key keeps the first value of the
 * last table that lists it, so pass characters before phrases.
 */
object ScriptTableCompiler {

    fun compile(tables: List<File>, output: File): Int {
        val pairs = HashMap<String, String>()
        for (table in tables) {
            table.forEachLine(Charsets.UTF_8) { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val columns = line.split('\t')
                if (columns.size < 2) return@forEachLine
                val value = columns[1].trim().split(' ').firstOrNull { it.isNotEmpty() } ?: return@forEachLine
                if (columns[0].isNotEmpty()) pairs[columns[0]] = value
            }
        }
        val keys = pairs.keys.sorted()

        val out = LittleEndianBuffer()
        repeat(ScriptFormat.HEADER_SIZE / 4) { out.u32(0) }
        out.setU32(ScriptFormat.AT_MAGIC, ScriptFormat.MAGIC)
        out.setU32(ScriptFormat.AT_VERSION, ScriptFormat.VERSION)
        out.setU32(ScriptFormat.AT_COUNT, keys.size)
        out.setU32(ScriptFormat.AT_MAX_KEY_LENGTH, keys.maxOfOrNull { it.length } ?: 0)

        out.setU32(ScriptFormat.AT_KEY_STARTS, out.size)
        writeStarts(out, keys)
        val values = keys.map { pairs.getValue(it) }
        out.setU32(ScriptFormat.AT_VALUE_STARTS, out.size)
        writeStarts(out, values)

        out.setU32(ScriptFormat.AT_KEY_POOL, out.size)
        keys.forEach(out::chars)
        out.align4()
        out.setU32(ScriptFormat.AT_VALUE_POOL, out.size)
        values.forEach(out::chars)

        out.writeTo(output)
        return keys.size
    }

    private fun writeStarts(out: LittleEndianBuffer, texts: List<String>) {
        var start = 0
        for (text in texts) {
            out.u32(start)
            start += text.length
        }
        out.u32(start)
    }
}
