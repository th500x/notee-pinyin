package com.notee.pinyin.data

import com.notee.pinyin.clip.ClipLimit

/** The last [MAX] copied texts, newest first; copying one again only moves it up (docs/02-0 §4). */
class ClipHistory(private val storage: Storage) {
    private val texts = ArrayList<String>(MAX + 1)

    val items: List<String> get() = texts

    /** Stored history goes after anything copied while it was loading. */
    fun restore(saved: List<String>) {
        val merged = (texts + saved).distinct().take(MAX)
        texts.clear()
        texts += merged
    }

    fun add(text: String) {
        if (!ClipLimit.accepts(text)) return
        if (texts.firstOrNull() == text) return
        texts.remove(text)
        texts.add(0, text)
        while (texts.size > MAX) texts.removeAt(texts.lastIndex)
        val copy = texts.toList()
        storage.write { putClips(copy) }
    }

    companion object {
        const val MAX = 5
    }
}
