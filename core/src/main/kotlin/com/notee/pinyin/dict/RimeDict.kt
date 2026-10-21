package com.notee.pinyin.dict

import java.io.File

/** One row of a rime `*.dict.yaml`: text, space-separated syllables, weight. */
class RimeEntry(val text: String, val code: String, val weight: Int)

/** Reads the rime-ice `cn_dicts` files. Only the `text<TAB>code<TAB>weight` columns are used. */
object RimeDict {
    private const val HEADER_END = "..."

    fun read(file: File): List<RimeEntry> {
        val entries = ArrayList<RimeEntry>()
        var inBody = false
        file.forEachLine(Charsets.UTF_8) { line ->
            when {
                !inBody -> inBody = line.trimEnd() == HEADER_END
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> parse(line)?.let(entries::add)
            }
        }
        return entries
    }

    private fun parse(line: String): RimeEntry? {
        val columns = line.split('\t')
        if (columns.size < 2) return null
        val code = columns[1].trim()
        if (code.isEmpty()) return null
        val weight = columns.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
        return RimeEntry(columns[0], code, weight.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    }
}
