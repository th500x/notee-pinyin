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
    fun documeOffersDocumentBeforeDocumentary() {
        val words = english.completions("docume")
        val document = words.indexOf("document")
        val documentary = words.indexOf("documentary")
        assertEquals(0, document)
        assertTrue(documentary > document)
        val composer = EnglishComposer(english)
        "docume".forEach(composer::type)
        assertEquals("document", composer.completions().first())
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

    @Test
    fun placeNamesTakeACapitalWhenFinished() {
        assertEquals("Shanghai", english.completions("shanghai").first())
        assertEquals("Bangkok", english.completions("bangkok").first())
        val composer = EnglishComposer(english)
        for ((typed, spelled) in listOf("bangkok" to "Bangkok", "pattaya" to "Pattaya", "shanghai" to "Shanghai")) {
            composer.clear()
            typed.forEach(composer::type)
            assertEquals(spelled, composer.placeSpelling())
            assertTrue(composer.completions().none { it.equals(typed, ignoreCase = true) })
        }
        composer.clear()
        "Bangkok".forEach(composer::type)
        assertEquals(null, composer.placeSpelling())
        composer.clear()
        "march".forEach(composer::type)
        assertEquals(null, composer.placeSpelling())
        composer.clear()
        "shang".forEach(composer::type)
        val shanghai = composer.completions().indexOf("Shanghai")
        assertTrue(shanghai >= 0)
        assertEquals("Shanghai", composer.selection(shanghai)?.word)
        assertEquals(true, composer.selection(shanghai)?.replace)
        composer.clear()
        "patt".forEach(composer::type)
        val words = composer.completions()
        val pattern = words.indexOf("pattern")
        val pattaya = words.indexOf("Pattaya")
        assertTrue(pattern >= 0)
        assertTrue(pattaya > pattern)
        composer.clear()
        "hel".forEach(composer::type)
        val hello = composer.completions().indexOf("hello")
        assertTrue(hello >= 0)
        assertEquals(false, composer.selection(hello)?.replace)
    }
}
