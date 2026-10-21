package com.notee.pinyin.festival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    @Test
    fun tenChineseOrTwentyEnglish() {
        assertTrue(Signature.fits("", "字".repeat(10)))
        assertFalse(Signature.fits("", "字".repeat(11)))
        assertTrue(Signature.fits("", "a".repeat(20)))
        assertFalse(Signature.fits("", "a".repeat(21)))
        assertTrue(Signature.fits("字".repeat(5), "a".repeat(10)))
        assertFalse(Signature.fits("字".repeat(5), "a".repeat(11)))
        assertEquals(20, Signature.units("字".repeat(10)))
        assertEquals(1, Signature.units("😀"))
    }

    @Test
    fun dropLastKeepsEarlierCharacters() {
        assertEquals("", Signature.dropLast(""))
        assertEquals("字", Signature.dropLast("字a"))
        assertEquals("a", Signature.dropLast("a😀"))
    }

    @Test
    fun aCustomWishAllowsThirtyChineseOrSixtyEnglish() {
        assertTrue(Signature.fits("", "字".repeat(30), Signature.WISH_LIMIT))
        assertFalse(Signature.fits("", "字".repeat(31), Signature.WISH_LIMIT))
        assertTrue(Signature.fits("", "a".repeat(60), Signature.WISH_LIMIT))
        assertFalse(Signature.fits("", "a".repeat(61), Signature.WISH_LIMIT))
        assertTrue(Signature.fits("字".repeat(10), "a".repeat(40), Signature.WISH_LIMIT))
        assertFalse(Signature.fits("字".repeat(10), "a".repeat(41), Signature.WISH_LIMIT))
    }

    @Test
    fun cursorInsertsAndDeletesAtTheTappedPlace() {
        assertEquals("abc" to 2, Signature.insert("ac", 1, "b"))
        assertEquals("b你好" to 1, Signature.insert("你好", 0, "b"))
        assertEquals("你b好" to 2, Signature.insert("你好", 1, "b"))
        assertEquals("acd" to 1, Signature.deleteBefore("abcd", 2))
        assertEquals("好" to 0, Signature.deleteBefore("你好", 1))
        assertEquals("😀" to 0, Signature.deleteBefore("a😀", 1))
        val emoji = "😀"
        assertEquals("ab" to 1, Signature.deleteBefore("a" + emoji + "b", 1 + emoji.length))
        assertEquals("a" to 1, Signature.deleteBefore("a" + emoji, ("a" + emoji).length))
        assertEquals("ab" to 0, Signature.deleteBefore("ab", 0))
        assertEquals(emoji.length, Signature.boundary(emoji, 1))
    }
}
