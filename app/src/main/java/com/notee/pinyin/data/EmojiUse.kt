package com.notee.pinyin.data

/** How often each emoji was sent; drives the 常用 tab. Each send adds one on disk. */
class EmojiUse(private val storage: Storage) {
    private val counts = HashMap<String, Int>()

    val all: Map<String, Int> get() = counts

    /** Stored counts add to anything sent while they were loading. */
    fun restore(saved: Map<String, Int>) {
        saved.forEach { (emoji, count) -> counts.merge(emoji, count, Int::plus) }
    }

    fun add(emoji: String) {
        counts.merge(emoji, 1, Int::plus)
        storage.write { addEmojiUse(emoji) }
    }
}
