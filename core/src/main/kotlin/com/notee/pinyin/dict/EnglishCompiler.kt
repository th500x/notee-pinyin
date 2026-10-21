package com.notee.pinyin.dict

import com.notee.pinyin.engine.EnglishFormat
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Builds `english.bin`.
 *
 * Which words exist comes from the rime-ice English tables. Their order comes from AOSP unigram
 * `f`. The AOSP list's line shape has room for a second word, but this file has none, so the next
 * word is the Tatoeba counts in `en_next.tsv`, not an AOSP bigram.
 */
object EnglishCompiler {

    class Report(val words: Int, val nextHeads: Int)

    private class Frequency(val display: String, val frequency: Int, val blocked: Boolean)

    private class Word(val key: String, val display: String, val weight: Int)

    private class NextHead(val key: String, val words: List<String>)

    private class Ranked(val display: String, val score: Long, val order: Int)

    private val WORD = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

    /** Content words keep their count; these are ranked as a quarter of theirs. `you` is not here. */
    private val FUNCTION = setOf(
        "a", "an", "the", "of", "to", "in", "on", "at", "for", "with", "from", "by", "as",
        "and", "or", "but", "if", "is", "are", "was", "were", "be", "been", "being",
        "it", "this", "that", "these", "those", "not",
    )

    fun compile(wordFiles: List<File>, frequencyFile: File, nextFile: File, output: File): Report {
        val frequencies = readFrequencies(frequencyFile)
        val words = compileWords(wordFiles, frequencies)
        val heads = compileNext(nextFile, frequencies)
        write(output, words, heads)
        return Report(words.size, heads.size)
    }

    private fun readFrequencies(file: File): Map<String, Frequency> {
        val out = HashMap<String, Frequency>(200_000)
        GZIPInputStream(file.inputStream().buffered()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val parsed = parseFrequency(line) ?: continue
                val key = parsed.display.lowercase()
                if (key.isEmpty() || key in out) continue
                out[key] = parsed
            }
        }
        return out
    }

    /** `word` is before `,f=`, `f` before `,flags=`, flags before `,originalFreq=`. */
    private fun parseFrequency(line: String): Frequency? {
        val wordAt = line.indexOf("word=")
        if (wordAt < 0) return null
        val freqAt = line.indexOf(",f=", wordAt + 5)
        if (freqAt < 0) return null
        val flagsAt = line.indexOf(",flags=", freqAt + 3)
        if (flagsAt < 0) return null
        val originalAt = line.indexOf(",originalFreq=", flagsAt + 7)
        if (originalAt < 0) return null
        val display = line.substring(wordAt + 5, freqAt)
        val frequency = line.substring(freqAt + 3, flagsAt).toIntOrNull() ?: return null
        if (display.isEmpty()) return null
        val flags = line.substring(flagsAt + 7, originalAt)
        val blocked = frequency == 0 || flags.split(',', ':').any {
            it == "offensive" || it == "nonword" || it == "drugs"
        }
        return Frequency(display, frequency, blocked)
    }

    private fun compileWords(files: List<File>, frequencies: Map<String, Frequency>): List<Word> {
        val byKey = LinkedHashMap<String, Word>()
        for (file in files) {
            for (entry in RimeDict.read(file)) {
                val spelling = entry.text
                if (!WORD.matches(spelling)) continue
                val key = spelling.lowercase()
                if (key in byKey || !fits(key)) continue
                val known = frequencies[key]
                if (known != null && known.blocked) continue
                val display = when {
                    spelling.any { it.isUpperCase() } -> spelling
                    known != null -> known.display
                    else -> spelling
                }
                if (!fits(display)) continue
                byKey[key] = Word(key, display, (known?.frequency ?: 1).coerceIn(1, 0xFFFF))
            }
        }
        // rime comments out a, an, and i. Without the exact word, space on "an" would commit "and".
        for ((key, known) in frequencies) {
            if (key in byKey || known.blocked || known.frequency < 70 || known.display.length > 2) continue
            if (!WORD.matches(known.display) || !fits(key) || !fits(known.display)) continue
            byKey[key] = Word(key, known.display, known.frequency.coerceIn(1, 0xFFFF))
        }
        return byKey.values.sortedBy { it.key }
    }

    private fun compileNext(file: File, frequencies: Map<String, Frequency>): List<NextHead> {
        val heads = LinkedHashMap<String, List<String>>()
        file.forEachLine(Charsets.UTF_8) { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val columns = line.split('\t')
            val head = columns[0].trim().lowercase()
            if (head.isEmpty() || head in heads || !fitsKey(head)) return@forEachLine
            val ranked = ArrayList<Ranked>()
            val seen = HashSet<String>()
            for ((order, column) in columns.drop(1).withIndex()) {
                val token = column.trim()
                val colon = token.lastIndexOf(':')
                if (colon <= 0) continue
                val count = token.substring(colon + 1).trim().toIntOrNull() ?: continue
                if (count <= 0) continue
                val key = token.substring(0, colon).trim().lowercase()
                if (key.isEmpty() || key == head || key in seen || !WORD.matches(key)) continue
                val known = frequencies[key]
                if (known != null && known.blocked) continue
                val display = known?.display ?: key
                if (!fitsKey(display)) continue
                seen += key
                val score = count.toLong() * if (key in FUNCTION) 1 else 4
                ranked += Ranked(display, score, order)
            }
            ranked.sortWith(compareByDescending<Ranked> { it.score }.thenBy { it.order })
            val words = ranked.take(EnglishFormat.NEXT_WORDS).map { it.display }
            if (words.isNotEmpty()) heads[head] = words
        }
        return heads.map { (key, words) -> NextHead(key, words) }.sortedBy { it.key }
    }

    private fun fits(text: String): Boolean = text.toByteArray(Charsets.UTF_8).size in 1..64

    /** A length stored in one byte. Completions are tighter; next-word strings only have to fit. */
    private fun fitsKey(text: String): Boolean = text.toByteArray(Charsets.UTF_8).size in 1..255

    private fun write(output: File, words: List<Word>, heads: List<NextHead>) {
        val out = LittleEndianBuffer()
        repeat(EnglishFormat.HEADER_SIZE / 4) { out.u32(0) }
        out.setU32(EnglishFormat.AT_MAGIC, EnglishFormat.MAGIC)
        out.setU32(EnglishFormat.AT_VERSION, EnglishFormat.VERSION)
        out.setU32(EnglishFormat.AT_WORD_COUNT, words.size)
        out.setU32(EnglishFormat.AT_NEXT_COUNT, heads.size)

        out.setU32(EnglishFormat.AT_WORDS, out.size)
        writeSection(out, words.size) { index ->
            val word = words[index]
            val key = word.key.toByteArray(Charsets.UTF_8)
            val display = word.display.toByteArray(Charsets.UTF_8)
            out.u8(key.size)
            out.bytes(key)
            out.u8(display.size)
            out.bytes(display)
            out.u16(word.weight)
        }

        out.setU32(EnglishFormat.AT_NEXT, out.size)
        writeSection(out, heads.size) { index ->
            val head = heads[index]
            val key = head.key.toByteArray(Charsets.UTF_8)
            out.u8(key.size)
            out.bytes(key)
            out.u8(head.words.size)
            for (word in head.words) {
                val bytes = word.toByteArray(Charsets.UTF_8)
                out.u8(bytes.size)
                out.bytes(bytes)
            }
        }
        out.writeTo(output)
    }

    /** Count, then u32 offsets relative to the bytes that start immediately after that table. */
    private fun writeSection(out: LittleEndianBuffer, count: Int, writeOne: (Int) -> Unit) {
        out.u32(count)
        val table = out.size
        repeat(count) { out.u32(0) }
        var cursor = 0
        for (index in 0 until count) {
            out.setU32(table + 4 * index, cursor)
            val before = out.size
            writeOne(index)
            cursor += out.size - before
        }
    }
}
