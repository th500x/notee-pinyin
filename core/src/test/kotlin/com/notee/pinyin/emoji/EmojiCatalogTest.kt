package com.notee.pinyin.emoji

import com.notee.pinyin.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    private val catalog = EmojiCatalog.parse(TestData.emojiFile.readText())

    @Test
    fun everyCategoryIsFilledAndNothingRepeats() {
        EmojiCategory.entries.forEach { assertTrue(it.name, catalog.emoji(it).isNotEmpty()) }
        val all = EmojiCategory.entries.flatMap(catalog::emoji)
        assertEquals(all.size, all.toSet().size)
        assertEquals("❤️", catalog.emoji(EmojiCategory.SYMBOL).first())
    }

    @Test
    fun frequentPageFollowsTheGrid() {
        val page = catalog.frequentPage(emptyMap())
        assertEquals(EmojiCatalog.COLUMNS * EmojiCatalog.ROWS, page.size)
        assertEquals(EmojiCell.Bar, page[3 * 7 + 3])
        assertEquals(EmojiCell.Bar, page[4 * 7 + 3])
        assertEquals(catalog.emoji(EmojiCategory.FACE).first(), (page[0] as EmojiCell.Emoji).text)
    }

    @Test
    fun frequentPagePutsAnimalsLeftOfPlants() {
        val page = catalog.frequentPage(emptyMap())
        assertEquals(catalog.emoji(EmojiCategory.ANIMAL).first(), (page[4 * 7] as EmojiCell.Emoji).text)
        assertEquals(catalog.emoji(EmojiCategory.PLANT).first(), (page[4 * 7 + 4] as EmojiCell.Emoji).text)
    }

    @Test
    fun natureTabRulesAnimalsOffFromPlants() {
        val animals = catalog.emoji(EmojiCategory.ANIMAL)
        val plants = catalog.emoji(EmojiCategory.PLANT)
        val page = catalog.page(EmojiTab.NATURE)
        assertEquals(animals.size + 1 + plants.size, page.size)
        assertEquals(EmojiCell.Rule, page[animals.size])
        assertEquals(animals.first(), (page[0] as EmojiCell.Emoji).text)
        assertEquals(plants.first(), (page[animals.size + 1] as EmojiCell.Emoji).text)
        assertEquals(catalog.emoji(EmojiCategory.FACE).size, catalog.page(EmojiTab.FACE).size)
    }

    @Test
    fun everyCategoryHasOneTab() {
        assertEquals(EmojiCategory.entries.toSet(), EmojiTab.entries.flatMap { it.categories }.toSet())
        assertEquals(EmojiCategory.entries.size, EmojiTab.entries.sumOf { it.categories.size })
    }

    @Test
    fun logoFacesAreRoundAndDoNotRepeatTheLast() {
        val faces = catalog.logoFaces()
        assertTrue("🤠" !in faces)
        assertTrue("🤡" !in faces)
        assertTrue("👻" !in faces)
        assertTrue("💀" !in faces)
        assertTrue("💩" !in catalog.emoji(EmojiCategory.FACE))
        assertTrue("😕" in faces)
        assertEquals(catalog.emoji(EmojiCategory.FACE).size - 4, faces.size)
        assertEquals("😁", EmojiCatalog.pickLogoFace(listOf("😀", "😁", "😂"), "😀", 0))
        assertEquals("😂", EmojiCatalog.pickLogoFace(listOf("😀", "😁", "😂"), "😀", 1))
        assertEquals("😀", EmojiCatalog.pickLogoFace(listOf("😀"), "😀", 3))
    }

    @Test
    fun useReordersWithinItsCategoryOnly() {
        val hand = catalog.emoji(EmojiCategory.HAND)
        val page = catalog.frequentPage(mapOf(hand.last() to 3))
        assertEquals(hand.last(), (page[7] as EmojiCell.Emoji).text)
        assertEquals(hand.first(), (page[8] as EmojiCell.Emoji).text)
    }
}
