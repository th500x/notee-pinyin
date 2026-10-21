package com.notee.pinyin.input

import com.notee.pinyin.TestData
import com.notee.pinyin.engine.CandidateItem
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyComposerTest {
    private val user = UserDictionary()
    private val composer = TestData.composers(user).qwerty

    private fun type(letters: String) = letters.forEach(composer::type)

    private fun pick(text: String): String? {
        val candidate = composer.candidates.items
            .filterIsInstance<CandidateItem.Word>()
            .first { it.candidate.text == text }
            .candidate
        return composer.select(candidate)
    }

    @Test
    fun oneSyllableShowsPinsThenBar() {
        type("de")
        assertEquals(listOf("的", "地", "得", "|"), composer.candidates.texts().take(4))
    }

    @Test
    fun webUsagePinsWin() {
        type("jian")
        assertEquals(listOf("见", "间", "件", "|"), composer.candidates.texts().take(4))
    }

    @Test
    fun wholeSyllableIsNeverSplitIntoAPartial() {
        type("xian")
        val texts = composer.candidates.texts()
        assertTrue(texts.indexOf("|") in 1..3)
        assertTrue("西安" in texts)
    }

    @Test
    fun phraseComesFirstForSeveralSyllables() {
        type("nihao")
        assertEquals("你好", composer.candidates.first?.text)
        assertTrue("|" !in composer.candidates.texts())
    }

    @Test
    fun unfinishedLastSyllableIsCompleted() {
        type("zhongg")
        assertTrue("中国" in composer.candidates.texts().take(5))
    }

    @Test
    fun wordsCanCoverAPinnedSyllable() {
        type("mudi")
        assertTrue("目的" in composer.candidates.texts().take(5))
    }

    @Test
    fun sentenceIsBuiltFromWords() {
        type("womenyiqiqu")
        assertEquals("我们一起去", composer.candidates.first?.text)
    }

    @Test
    fun sentencePrefersWordsOverLoneCharacters() {
        type("xianzaishijianshiduoshao")
        assertEquals("现在时间是多少", composer.candidates.first?.text)
    }

    @Test
    fun pickingPartsCommitsTheWholeAndLearnsIt() {
        type("nihao")
        assertNull(pick("你"))
        assertEquals("你", composer.preedit.chosen)
        assertEquals("hao", composer.preedit.pinyin)
        assertEquals("你号", pick("号"))
        assertEquals(1, user.count("ni hao", "你号"))
        assertEquals(1, user.count("hao", "号"))
        assertTrue(!composer.isComposing)
    }

    @Test
    fun learnedCharacterLeadsAfterTheBarButNeverPassesThePins() {
        type("de")
        pick("德")
        type("de")
        assertEquals(listOf("的", "地", "得", "|", "德"), composer.candidates.texts().take(5))
    }

    @Test
    fun deletingIntoAChoiceReopensIt() {
        type("nihao")
        pick("你")
        repeat(3) { composer.deleteBackward() }
        assertEquals("", composer.preedit.chosen)
        assertEquals("ni", composer.preedit.pinyin)
    }

    @Test
    fun delimiterForcesTheSplit() {
        type("xi'an")
        assertEquals("西安", composer.candidates.first?.text)
    }

    @Test
    fun spaceOnGarbageCommitsTheLetters() {
        type("vvv")
        assertTrue(composer.candidates.isEmpty)
        assertEquals("vvv", composer.selectFirst())
    }

    @Test
    fun commitAllTakesFirstCandidatesToTheEnd() {
        type("nihao")
        assertEquals("你好", composer.commitAll())
        assertTrue(!composer.isComposing)
    }

    @Test
    fun transposedLettersOfferTheWordAndLearnItsSpelling() {
        type("niaho")
        val first = composer.candidates.first!!
        assertEquals("你好", first.text)
        assertTrue(first.corrected)
        assertEquals("niaho", composer.preedit.pinyin)
        assertEquals("你好", composer.selectFirst())
        assertEquals(1, user.count("ni hao", "你好"))
        assertTrue(!composer.isComposing)
    }

    @Test
    fun otherAdjacentSwapsOfNihao() {
        type("inhao")
        assertEquals("你好", composer.candidates.first?.text)
        composer.clear()
        type("nihoa")
        assertEquals("你好", composer.candidates.first?.text)
        assertEquals("nihoa", composer.preedit.pinyin)
    }

    @Test
    fun apostropheStaysPutWhileTheLettersSwap() {
        type("ix'an")
        val first = composer.candidates.first!!
        assertEquals("西安", first.text)
        assertTrue(first.corrected)
        assertEquals("ix'an", composer.preedit.pinyin)
    }

    @Test
    fun aPrefixOfALongerWordIsNotCorrected() {
        type("jius")
        assertTrue("就是" in composer.candidates.texts())
        assertFalse("激素" in composer.candidates.texts())
        assertTrue(!composer.candidates.first!!.corrected)
        composer.clear()
        type("jieg")
        assertTrue(!composer.candidates.first!!.corrected)
        assertFalse("几个" in composer.candidates.texts())
    }

    @Test
    fun anExistingWordIsNotRewrittenAsItsNeighbor() {
        type("xiaoshou")
        assertTrue("销售" in composer.candidates.texts().take(3))
        assertTrue(!composer.candidates.first!!.corrected)
        assertFalse("小说" in composer.candidates.texts())
    }

    @Test
    fun aLearnedWordIsOfferedForTheSameTransposition() {
        user.record("ni hao", "拟好呀")
        type("niaho")
        assertTrue("拟好呀" in composer.candidates.texts())
    }
}
