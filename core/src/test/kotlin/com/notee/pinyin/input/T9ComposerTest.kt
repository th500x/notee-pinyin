package com.notee.pinyin.input

import com.notee.pinyin.TestData
import com.notee.pinyin.engine.CandidateItem
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.rank.PinTable
import com.notee.pinyin.rank.T9Keys
import com.notee.pinyin.texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ComposerTest {
    private val composers = TestData.composers()
    private val composer = composers.t9
    private val syllables = composers.syllables

    private fun press(keys: String) = keys.forEach(composer::press)

    private val letters = Regex("[a-z']*")

    @Test
    fun exactSyllableOnly() {
        press("33")
        val texts = composer.candidates.texts()
        assertEquals(listOf("的", "地", "得", "|"), texts.take(4))
        assertFalse("分" in texts)
    }

    @Test
    fun severalSyllablesPutEachOnesPinsBeforeOneBar() {
        press("7486")
        val pins = PinTable.parse(TestData.lexicon.pinText, syllables)
        val shuo = pins.pins(syllables.id("shuo"))
        val shun = pins.pins(syllables.id("shun"))
        val texts = composer.candidates.texts()
        assertEquals(shuo + shun + "|", texts.take(shuo.size + shun.size + 1))
        assertEquals(1, texts.count { it == "|" })
        assertEquals(listOf("shuo", "shun"), composer.preedit.choices.take(2).map { it.spelling })
    }

    @Test
    fun keysRunOnIntoWords() {
        press("9633")
        assertEquals("我的", composer.candidates.first?.text)
        assertEquals("wo'de", composer.preedit.pinyin)
        assertEquals("wode", composer.rawText())
    }

    @Test
    fun oneRunReadsEveryWay() {
        press("9474")
        val texts = composer.candidates.texts()
        assertTrue(texts.indexOf("一起") in 0 until 5)
        assertTrue(texts.indexOf("意思") in 0 until 5)
    }

    @Test
    fun wordsComeBeforeCharacters() {
        press("9474")
        val items = composer.candidates.items.filterIsInstance<CandidateItem.Word>()
        val firstCharacter = items.indexOfFirst { it.candidate.syllables.size == 1 }
        val lastWord = items.indexOfLast { it.candidate.syllables.size >= 2 }
        assertTrue(firstCharacter > lastWord)
    }

    @Test
    fun keysThatSpellNoSyllableGiveNothing() {
        press("5")
        assertTrue(composer.candidates.isEmpty)
        assertTrue(composer.preedit.pinyin.matches(letters))
    }

    @Test
    fun unfinishedKeysShowLettersNotDigits() {
        press("335")
        assertTrue("的" in composer.candidates.texts())
        val pinyin = composer.preedit.pinyin
        assertTrue(pinyin, pinyin.matches(letters))
        assertTrue(composer.rawText().matches(letters))
    }

    @Test
    fun choosingASyllableNarrowsTheCandidates() {
        press("7486")
        assertTrue(composer.choose(syllables.id("shun")))
        val texts = composer.candidates.texts()
        val shun = PinTable.parse(TestData.lexicon.pinText, syllables).pins(syllables.id("shun"))
        assertEquals(shun + "|", texts.take(shun.size + 1))
        assertEquals("shun", composer.preedit.pinyin)
    }

    @Test
    fun aReadingThatLeavesNoSyllableIsNotOffered() {
        press(T9Keys.digitsOf("xiang"))
        val choices = composer.preedit.choices.map { it.spelling }
        assertTrue(choices.contains("xiang"))
        assertTrue(choices.contains("zhang"))
        assertFalse(choices.contains("xiao"))
        assertFalse(choices.contains("xian"))
        assertFalse(choices.contains("zhao"))
        assertFalse(choices.contains("zhan"))
    }

    @Test
    fun choosingTheFirstSyllableKeepsTheRestOpen() {
        press("9474")
        assertTrue(composer.choose(syllables.id("yi")))
        val texts = composer.candidates.texts()
        assertTrue("一起" in texts && "意思" in texts)
        assertFalse("自私" in texts)
        assertTrue(composer.preedit.pinyin.startsWith("yi'"))
    }

    @Test
    fun continuousKeysBuildAPhrase() {
        press("64426")
        assertEquals("你好", composer.candidates.first?.text)
        assertTrue(!composer.candidates.first!!.corrected)
        assertEquals("ni'hao", composer.preedit.pinyin)
        assertEquals("ni", composer.preedit.choices.first().spelling)
        assertTrue(composer.preedit.choices.map { it.spelling }.containsAll(listOf("ni", "mi")))
        assertEquals("nihao", composer.rawText())
    }

    @Test
    fun deleteUndoesTheLastKey() {
        press("333")
        composer.deleteBackward()
        assertEquals("de", composer.preedit.pinyin)
    }

    @Test
    fun deleteIntoAPickReopensIt() {
        press("9633")
        val wo = composer.candidates.items.filterIsInstance<CandidateItem.Word>().first { it.candidate.text == "我" }
        assertEquals(null, composer.select(wo.candidate))
        composer.deleteBackward()
        composer.deleteBackward()
        assertEquals("", composer.preedit.chosen)
        assertTrue(composer.isComposing)
    }

    @Test
    fun pickingTheFirstSyllableKeepsTheRest() {
        press("64426")
        val ni = composer.candidates.items.filterIsInstance<CandidateItem.Word>().first { it.candidate.text == "你" }
        assertEquals(null, composer.select(ni.candidate))
        assertEquals("你", composer.preedit.chosen)
        assertEquals("好", composer.candidates.first?.text)
        assertEquals("你好", composer.selectFirst())
    }

    @Test
    fun aRealPhraseIsNotReplacedByASwap() {
        press(T9Keys.digitsOf("womenyiqiqu"))
        val first = composer.candidates.first!!
        assertEquals("我们一起去", first.text)
        assertTrue(!first.corrected)
    }

    @Test
    fun swappedKeysOfferTheWordWithoutRewritingThePreedit() {
        press("64246")
        val first = composer.candidates.first!!
        assertEquals("你好", first.text)
        assertTrue(first.corrected)
        assertTrue(composer.preedit.pinyin != "ni'hao")
        assertEquals("你好", composer.selectFirst())
    }

    @Test
    fun aCommonKeyPrefixIsNotCorrected() {
        press("6442")
        assertFalse("你好" in composer.candidates.texts())
        assertTrue(composer.candidates.items.none { (it as? CandidateItem.Word)?.candidate?.corrected == true })
    }

    @Test
    fun keysThatAlreadySpellAWordAreNotRewritten() {
        press("2435464")
        assertFalse("北京" in composer.candidates.texts())
        composer.clear()
        press("94664468")
        assertFalse("中国" in composer.candidates.texts())
    }

    @Test
    fun aFixedSyllableIsNotTransposed() {
        press("64246")
        val choice = composer.preedit.choices.firstOrNull() ?: return
        assertTrue(composer.choose(choice.syllable))
        assertTrue(composer.candidates.items.none { (it as? CandidateItem.Word)?.candidate?.corrected == true })
    }

    @Test
    fun aLearnedWordFollowsTheSwappedKeys() {
        val user = UserDictionary()
        val t9 = TestData.composers(user).t9
        user.record("ni hao", "拟好呀")
        "64246".forEach(t9::press)
        assertTrue("拟好呀" in t9.candidates.texts())
    }

    @Test
    fun selectingRangFromARunningPhraseKeepsTheTail() {
        press(T9Keys.digitsOf("rangtamen"))
        assertEquals("rang'ta'men", composer.preedit.pinyin)
        val rang = composer.candidates.items.filterIsInstance<CandidateItem.Word>()
            .first { it.candidate.text == "让" }
        assertTrue(rang.candidate.end > 0)
        assertFalse(rang.candidate.corrected)
        assertEquals(null, composer.select(rang.candidate))
        assertEquals("让", composer.preedit.chosen)
        assertTrue(composer.preedit.pinyin.isNotEmpty())
        assertTrue(composer.preedit.pinyin != "rang'ta'men")
        val committed = composer.commitAll()
        assertTrue(committed, committed.startsWith("让"))
    }

    /** Every shown word for these key runs must be selectable, and must move the composition. */
    @Test
    fun everyShownCandidateCanBeSelected() {
        val samples = listOf(
            "rangtamen", "nihao", "women", "xian", "xianguang", "ba", "shuo", "zhongguo",
            "jintian", "beijing", "shanghai", "womenyiqiqu", "xiang", "zhang",
            "xiao", "pengyou", "rang", "pang", "chi", "heshui", "wode",
        )
        for (spelling in samples) {
            val keys = T9Keys.digitsOf(spelling)
            val probe = TestData.composers().t9
            keys.forEach(probe::press)
            val items = probe.candidates.items
            for (index in items.indices) {
                val shown = items[index]
                if (shown !is CandidateItem.Word) continue
                val fresh = TestData.composers().t9
                keys.forEach(fresh::press)
                val item = fresh.candidates.items[index]
                check(item is CandidateItem.Word) { "$spelling #$index disappeared" }
                val candidate = item.candidate
                if (!candidate.corrected) {
                    val letters = candidate.syllables.sumOf { syllables.spelling(it).length }
                    check(letters == candidate.end) {
                        "$spelling ${candidate.text} spells $letters keys but ends at ${candidate.end}"
                    }
                }
                val committed = fresh.select(candidate)
                fresh.preedit
                fresh.candidates
                if (committed == null) {
                    check(fresh.preedit.chosen.endsWith(candidate.text)) {
                        "$spelling selecting ${candidate.text} left chosen='${fresh.preedit.chosen}'"
                    }
                }
            }
        }
    }

    @Test
    fun baPinsBaFirst() {
        press(T9Keys.digitsOf("ba"))
        assertEquals("吧", composer.candidates.first?.text)
    }

    @Test
    fun minAndNinLeadWithNinUntilAReadingIsChosen() {
        press(T9Keys.digitsOf("nin"))
        assertEquals(listOf("您", "民", "敏", "|"), composer.candidates.texts().take(4))
        val min = composer.preedit.choices.first { it.spelling == "min" }
        assertTrue(composer.choose(min.syllable))
        assertEquals(listOf("民", "敏"), composer.candidates.texts().take(2))
    }

    @Test
    fun zuLinLeadsWithRent() {
        press(T9Keys.digitsOf("zulin"))
        val texts = composer.candidates.texts()
        assertEquals("租金", texts.first())
        assertTrue(texts.indexOf("租金") < texts.indexOf("租赁"))
    }

    @Test
    fun keAndLeLeadWithLeUntilAReadingIsChosen() {
        press(T9Keys.digitsOf("ke"))
        assertEquals(listOf("了", "可", "刻", "克", "乐", "勒", "|"), composer.candidates.texts().take(7))
        val ke = composer.preedit.choices.first { it.spelling == "ke" }
        assertTrue(composer.choose(ke.syllable))
        assertEquals(listOf("可", "刻", "克"), composer.candidates.texts().take(3))
    }

    @Test
    fun choosingTianThenKongDropsTheOtherReadings() {
        press(T9Keys.digitsOf("tiankong"))
        val tian = composer.preedit.choices.first { it.spelling == "tian" }
        assertTrue(composer.choose(tian.syllable))
        val next = composer.preedit.choices.map { it.spelling }
        assertTrue(next.size > 1)
        assertTrue(next.contains("kong"))
        assertTrue(composer.choose(syllables.id("kong")))
        assertEquals("tian'kong", composer.preedit.pinyin)
        val words = composer.candidates.items.mapNotNull { (it as? CandidateItem.Word)?.candidate }
        assertTrue(words.any { !it.corrected && it.syllables.map(syllables::spelling) == listOf("tian", "kong") })
        assertTrue(words.none { word ->
            if (word.corrected) {
                false
            } else {
                val spells = word.syllables.map(syllables::spelling)
                spells.first() != "tian" || (spells.sumOf { it.length } == 8 && spells != listOf("tian", "kong"))
            }
        })
    }

    @Test
    fun aChosenReadingMovesToTheFrontNextTime() {
        val user = UserDictionary()
        val t9 = TestData.composers(user).t9
        val keys = T9Keys.digitsOf("xian")
        keys.forEach(t9::press)
        val choices = t9.preedit.choices
        assertTrue(choices.size > 1)
        val pick = choices.firstOrNull { it.spelling == "xian" && it != choices.first() } ?: choices.last()
        assertTrue(t9.choose(pick.syllable))
        assertEquals(1, user.syllableCount(T9Keys.digitsOf(pick.spelling), pick.spelling))
        t9.clear()
        keys.forEach(t9::press)
        assertEquals(pick.spelling, t9.preedit.choices.first().spelling)
    }

    @Test
    fun aPrivateFieldDoesNotRememberTheReading() {
        val user = UserDictionary()
        user.learning = false
        var recorded = 0
        user.onSyllableRecorded = { _, _ -> recorded++ }
        val t9 = TestData.composers(user).t9
        val keys = T9Keys.digitsOf("xian")
        keys.forEach(t9::press)
        val before = t9.preedit.choices.map { it.spelling }
        assertTrue(t9.choose(t9.preedit.choices.last().syllable))
        t9.clear()
        keys.forEach(t9::press)
        assertEquals(before, t9.preedit.choices.map { it.spelling })
        assertEquals(0, recorded)
    }

    @Test
    fun everySyllableCandidateAgreesWithItsKeys() {
        for (id in 0 until syllables.size) {
            val spelling = syllables.spelling(id)
            val t9 = TestData.composers().t9
            T9Keys.digitsOf(spelling).forEach(t9::press)
            for (item in t9.candidates.items) {
                if (item !is CandidateItem.Word) continue
                val candidate = item.candidate
                if (candidate.corrected) continue
                val letters = candidate.syllables.sumOf { syllables.spelling(it).length }
                check(letters == candidate.end) {
                    "$spelling ${candidate.text} spells $letters keys but ends at ${candidate.end}"
                }
            }
            val first = t9.candidates.first ?: continue
            t9.select(first)
            t9.preedit
            t9.candidates
        }
    }

    @Test
    fun longRunsStayQuick() {
        val keys = T9Keys.digitsOf("womenyiqiquxuexiaokanshu")
        val started = System.nanoTime()
        keys.forEach {
            composer.press(it)
            composer.candidates
            composer.preedit
        }
        val millis = (System.nanoTime() - started) / 1_000_000
        println("T9 ${keys.length} keys, every key: $millis ms")
        assertTrue(composer.candidates.first != null)
        assertTrue(millis < 5_000)
    }
}
