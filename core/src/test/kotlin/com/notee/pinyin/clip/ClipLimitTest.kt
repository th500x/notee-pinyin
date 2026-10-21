package com.notee.pinyin.clip

import com.notee.pinyin.engine.UserDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipLimitTest {
    @Test
    fun fiveHundredChineseCharactersFit() {
        assertTrue(ClipLimit.accepts("字".repeat(500)))
        assertFalse(ClipLimit.accepts("字".repeat(501)))
    }

    @Test
    fun oneThousandEnglishLettersFit() {
        assertTrue(ClipLimit.accepts("a".repeat(1000)))
        assertFalse(ClipLimit.accepts("a".repeat(1001)))
    }

    @Test
    fun mixedTextUsesTheSameBudget() {
        assertTrue(ClipLimit.accepts("字".repeat(250) + "a".repeat(500)))
        assertFalse(ClipLimit.accepts("字".repeat(250) + "a".repeat(501)))
    }

    @Test
    fun anEmojiCountsAsOne() {
        assertEquals(1, ClipLimit.units("😀"))
    }
}

class UserDictionaryLearningTest {
    @Test
    fun aFieldThatAsksNotToLearnIsNotRecorded() {
        val user = UserDictionary()
        var writes = 0
        user.onRecorded = { _, _ -> writes++ }
        user.learning = false
        user.record("ni", "你")
        assertEquals(0, user.count("ni", "你"))
        assertEquals(0, writes)
    }
}
