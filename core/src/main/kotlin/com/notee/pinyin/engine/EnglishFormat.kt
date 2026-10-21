package com.notee.pinyin.engine

/** Memory-mapped English completions and next words. Little-endian. */
object EnglishFormat {
    const val MAGIC = 0x5845504E
    const val VERSION = 1
    const val HEADER_SIZE = 32

    const val AT_MAGIC = 0
    const val AT_VERSION = 4
    const val AT_WORD_COUNT = 8
    const val AT_WORDS = 12
    const val AT_NEXT_COUNT = 16
    const val AT_NEXT = 20

    /** Candidate row while a word is still being typed. */
    const val COMPLETIONS = 24

    /** Words offered after one word has been committed. */
    const val NEXT_WORDS = 12
}
