package com.notee.pinyin.engine

import com.notee.pinyin.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociationsTest {
    private val associations = Associations(TestData.lexicon, UserDictionary())

    @Test
    fun chiOffersWordsBeforeCharacters() {
        val list = associations.suggest("吃")
        assertTrue(list.indexOf("东西") in 0 until list.indexOf("饭"))
        val firstChar = list.indexOfFirst { it.length == 1 }
        val lastWord = list.indexOfLast { it.length >= 2 }
        assertTrue(firstChar > lastWord)
    }

    @Test
    fun shangAndHeFollowTheDictionary() {
        val shang = associations.suggest("上")
        assertTrue("班" in shang && "学" in shang && "课" in shang)
        val he = associations.suggest("喝")
        assertTrue("水" in he && "茶" in he && "酒" in he)
        assertTrue(he.indexOfLast { it.length >= 2 } < he.indexOf("水"))
    }

    @Test
    fun aWordStemUsesTheWholeWordThenItsLastCharacter() {
        val beijing = associations.suggest("北京")
        assertTrue("市" in beijing)
        assertEquals("吃饭", Associations.stemOf("吃饭。"))
        assertNull(Associations.stemOf("12+7"))
    }

    @Test
    fun pickedWordsMoveUpInsideTheirOwnGroup() {
        val user = UserDictionary()
        user.record("chi dong xi", "吃东西")
        repeat(3) { user.record("chi wan fan", "吃完饭") }
        val list = Associations(TestData.lexicon, user).suggest("吃")
        assertTrue(list.indexOf("完饭") < list.indexOf("东西"))
        assertTrue(list.indexOf("东西") < list.indexOf("饭"))
    }

    @Test
    fun aCommonCharacterStaysQuick() {
        val started = System.nanoTime()
        repeat(20) { associations.suggest("的") }
        val millis = (System.nanoTime() - started) / 1_000_000
        println("association 的 × 20: $millis ms")
        assertTrue(millis < 2_000)
    }
}
