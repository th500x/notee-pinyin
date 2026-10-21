package com.notee.pinyin.engine

import com.notee.pinyin.TestData
import com.notee.pinyin.rank.PinTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconTest {
    private val lexicon = TestData.lexicon
    private val syllables = lexicon.syllables

    private fun ids(code: String) = code.split(' ').map(syllables::id).toIntArray()

    @Test
    fun syllableSetComesFromTheCharacterTable() {
        assertEquals(414, syllables.size)
        assertTrue(syllables.id("lv") != SyllableTable.NONE)
        assertEquals(SyllableTable.NONE, syllables.id("ng"))
    }

    @Test
    fun pinTableMatchesTheDoc() {
        val pins = PinTable.parse(lexicon.pinText, syllables)
        assertEquals(401, pins.syllableCount)
        assertEquals(listOf("的", "地", "得"), pins.pins(syllables.id("de")))
        assertEquals(listOf("见", "间", "件"), pins.pins(syllables.id("jian")))
    }

    @Test
    fun lookupFindsWordsAndKnowsWhatContinues() {
        val nihao = lexicon.lookup(ids("ni hao"))
        assertTrue(nihao.entries.any { it.text == "你好" })
        assertTrue(lexicon.lookup(ids("ni")).continues)
        val chars = lexicon.lookup(ids("de")).entries
        assertTrue(chars.zipWithNext().all { (a, b) -> a.weight >= b.weight })
        assertFalse(lexicon.lookup(ids("de de de de de de de de")).continues)
    }

    @Test
    fun scriptConversionUsesPhrasesFirst() {
        assertEquals("簡體", TestData.script.toTraditional("简体"))
        assertEquals("頭髮", TestData.script.toTraditional("头发"))
        assertEquals("的", TestData.script.toTraditional("的"))
        assertEquals("abc，12", TestData.script.toTraditional("abc，12"))
    }
}
