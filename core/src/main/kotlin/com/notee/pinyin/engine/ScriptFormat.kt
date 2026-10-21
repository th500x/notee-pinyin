package com.notee.pinyin.engine

/**
 * Layout of `s2t.bin`, little-endian: Simplified keys sorted by UTF-16 order, each with the first
 * Traditional value OpenCC lists for it.
 */
internal object ScriptFormat {
    const val MAGIC = 0x3253504E // "NPS2"
    const val VERSION = 1
    const val HEADER_SIZE = 32

    const val AT_MAGIC = 0
    const val AT_VERSION = 4
    const val AT_COUNT = 8
    const val AT_MAX_KEY_LENGTH = 12
    const val AT_KEY_STARTS = 16 // u32[count + 1], index into the key pool
    const val AT_VALUE_STARTS = 20 // u32[count + 1], index into the value pool
    const val AT_KEY_POOL = 24 // UTF-16 code units
    const val AT_VALUE_POOL = 28 // UTF-16 code units
}
