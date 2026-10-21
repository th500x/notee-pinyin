package com.notee.pinyin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordFileTest {
    @Test
    fun roundTripKeepsOnlyCodeTextAndCount() {
        val words = listOf(
            LearnedWord("wo", "我", 1),
            LearnedWord("ni hao", "你好", 4),
        )
        val text = WordFile.encode(words)
        assertEquals(
            "notee-pinyin-words 1\nni hao\t你好\t4\nwo\t我\t1\n",
            text,
        )
        assertEquals(
            listOf(LearnedWord("ni hao", "你好", 4), LearnedWord("wo", "我", 1)),
            WordFile.decode(text),
        )
    }

    @Test
    fun aForeignFileIsRejected() {
        assertNull(WordFile.decode("hello"))
        assertNull(WordFile.decode("notee-pinyin-words 1\nni hao 你好 4"))
        assertNull(WordFile.decode("notee-pinyin-words 1\nni hao\t你好\t0"))
        assertNull(WordFile.decode(""))
    }

    @Test
    fun duplicateRowsKeepTheLargerCount() {
        val text = "notee-pinyin-words 1\nni hao\t你好\t2\nni hao\t你好\t5\n"
        assertEquals(listOf(LearnedWord("ni hao", "你好", 5)), WordFile.decode(text))
    }

    @Test
    fun raiseToNeverLowersAndDoesNotCountAsAPick() {
        val user = UserDictionary()
        var picks = 0
        user.onRecorded = { _, _ -> picks++ }
        user.load("ni hao", "你好", 2)
        user.raiseTo("ni hao", "你好", 5)
        user.raiseTo("ni hao", "你好", 1)
        assertEquals(5, user.count("ni hao", "你好"))
        assertEquals(0, picks)
        assertEquals(listOf(LearnedWord("ni hao", "你好", 5)), user.learnedWords())
    }
}
