package com.notee.pinyin.engine

/**
 * Layout of `lexicon.bin`, little-endian throughout.
 *
 * Codes are syllable-id sequences sorted by [compareCodes]. The entries of one code sit together,
 * heaviest first. Texts are UTF-16 so a Han character costs two bytes.
 */
internal object LexiconFormat {
    const val MAGIC = 0x584C504E // "NPLX"
    const val VERSION = 2
    const val HEADER_SIZE = 68

    const val AT_MAGIC = 0
    const val AT_VERSION = 4
    const val AT_SYLLABLE_COUNT = 8
    const val AT_CODE_COUNT = 12
    const val AT_ENTRY_COUNT = 16
    const val AT_SYLLABLE_SPELLINGS = 20 // per syllable: u8 length, ASCII
    const val AT_SYLLABLE_WEIGHTS = 24 // u32[syllableCount]
    const val AT_PINS = 28 // u32 byte length, UTF-8 pin table
    const val AT_CODE_STARTS = 32 // u32[codeCount + 1], index into the code pool
    const val AT_ENTRY_STARTS = 36 // u32[codeCount + 1], index into entries
    const val AT_TEXT_STARTS = 40 // u32[entryCount + 1], index into the text pool
    const val AT_ENTRY_WEIGHTS = 44 // u32[entryCount]
    const val AT_CODE_POOL = 48 // u16 syllable ids
    const val AT_TEXT_POOL = 52 // UTF-16 code units
    const val AT_TOTAL_WEIGHT = 56 // u64, sum of all entry weights
    const val AT_ASSOCIATION = 64 // u32 offset of the first-character index, version 2

    /** Shorter code first when one is a prefix of the other. */
    fun compareCodes(a: IntArray, aLength: Int, b: IntArray, bLength: Int): Int {
        val n = minOf(aLength, bLength)
        for (i in 0 until n) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return aLength.compareTo(bLength)
    }
}
