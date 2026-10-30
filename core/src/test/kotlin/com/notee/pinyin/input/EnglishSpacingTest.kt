package com.notee.pinyin.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishSpacingTest {
    @Test
    fun aSpaceBeforeSentenceMarksIsDropped() {
        for (mark in listOf(".", ",", "!", "?", ";", ":", "...", "'", ")")) {
            assertTrue(mark, EnglishSpacing.dropSpaceBefore("hello ", mark))
        }
    }

    @Test
    fun lettersDigitsAndOpeningMarksKeepTheSpace() {
        assertFalse(EnglishSpacing.dropSpaceBefore("hello ", "a"))
        assertFalse(EnglishSpacing.dropSpaceBefore("hello ", "1"))
        assertFalse(EnglishSpacing.dropSpaceBefore("hello ", "("))
        assertFalse(EnglishSpacing.dropSpaceBefore("hello ", "+"))
        assertFalse(EnglishSpacing.dropSpaceBefore("hello ", "@"))
    }

    @Test
    fun onlyASingleSpaceAfterAWord() {
        assertFalse(EnglishSpacing.dropSpaceBefore("hello", "."))
        assertFalse(EnglishSpacing.dropSpaceBefore("hello  ", "."))
        assertFalse(EnglishSpacing.dropSpaceBefore("hello\n", "."))
        assertFalse(EnglishSpacing.dropSpaceBefore(" ", "."))
        assertFalse(EnglishSpacing.dropSpaceBefore(null, "."))
        assertTrue(EnglishSpacing.dropSpaceBefore("a ", "."))
    }
}
