package com.notee.pinyin.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            "notee-pinyin-words 2\nni hao\t你好\t4\nwo\t我\t1\n",
            text,
        )
        val data = WordFile.decode(text)
        assertEquals(
            listOf(LearnedWord("ni hao", "你好", 4), LearnedWord("wo", "我", 1)),
            data?.words,
        )
        assertTrue(data!!.syllables.isEmpty())
    }

    @Test
    fun syllablePicksTravelWithTheWords() {
        val text = WordFile.encode(
            listOf(LearnedWord("ni hao", "你好", 2)),
            listOf(LearnedSyllable("9426", "xian", 4), LearnedSyllable("9426", "xiao", 1)),
        )
        assertEquals(
            "notee-pinyin-words 2\n9426\txian\t4\n9426\txiao\t1\nni hao\t你好\t2\n",
            text,
        )
        val data = WordFile.decode(text)
        assertEquals(listOf(LearnedSyllable("9426", "xian", 4), LearnedSyllable("9426", "xiao", 1)), data?.syllables)
        assertEquals(listOf(LearnedWord("ni hao", "你好", 2)), data?.words)
    }

    @Test
    fun versionOneStillLoads() {
        val data = WordFile.decode("notee-pinyin-words 1\nwo\t我\t1\n")
        assertEquals(listOf(LearnedWord("wo", "我", 1)), data?.words)
        assertEquals(emptyList<LearnedSyllable>(), data?.syllables)
    }

    @Test
    fun aReadingMustBeTypedWithThoseKeys() {
        assertNull(WordFile.decode("notee-pinyin-words 2\n22\txiao\t1\n"))
        assertNull(WordFile.decode("notee-pinyin-words 2\n9426\tXian\t1\n"))
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
        assertEquals(listOf(LearnedWord("ni hao", "你好", 5)), WordFile.decode(text)?.words)
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
