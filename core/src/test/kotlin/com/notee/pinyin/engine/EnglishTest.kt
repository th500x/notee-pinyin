package com.notee.pinyin.engine

import com.notee.pinyin.TestData
import com.notee.pinyin.input.EnglishComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishTest {
    private val english = TestData.english

    @Test
    fun helContainsHello() {
        assertTrue("hello" in english.completions("hel"))
    }

    @Test
    fun fullyTypedHelloIsFirst() {
        assertEquals("hello", english.completions("hello").first())
        val composer = EnglishComposer(english)
        "hello".forEach(composer::type)
        assertTrue(composer.completions().none { it.equals("hello", ignoreCase = true) })
        assertEquals("hello", composer.commitFirst())
    }

    @Test
    fun helIsTitleCased() {
        val composer = EnglishComposer(english)
        "Hel".forEach(composer::type)
        val words = composer.completions()
        assertTrue("Hello" in words)
        assertTrue(words.all { it.isNotEmpty() && it[0].isUpperCase() })
    }

    @Test
    fun shortWordsAreFirstWhenFullyTyped() {
        for (typed in listOf("a", "an", "i")) {
            assertEquals(typed, english.completions(typed).first().lowercase())
        }
    }

    @Test
    fun eatListsLunchBeforeDinner() {
        val next = english.following("eat")
        val lunch = next.indexOf("lunch")
        val dinner = next.indexOf("dinner")
        assertTrue(lunch >= 0)
        assertTrue(dinner > lunch)
    }

    @Test
    fun thankStartsWithYou() {
        assertTrue(english.following("thank").first().equals("you", ignoreCase = true))
    }

    @Test
    fun aShorterTypingOffersTheRestOfTheWord() {
        val composer = EnglishComposer(english)
        "hel".forEach(composer::type)
        val hello = composer.completions().indexOf("hello")
        assertTrue(hello >= 0)
        assertEquals("lo", composer.suffix(hello))
        composer.clear()
        "hello".forEach(composer::type)
        assertTrue(composer.completions().none { it.equals("hello", ignoreCase = true) })
    }

    @Test
    fun blankAndUnknownPrefixesAreEmpty() {
        assertTrue(english.completions("").isEmpty())
        assertTrue(english.completions("zzzqqq").isEmpty())
    }
}
