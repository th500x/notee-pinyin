package com.notee.pinyin.festival

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalsTest {
    @Test
    fun fixedWindowsIncludeTheDayBeforeAndAfter() {
        assertEquals(listOf(Festival.NEW_YEAR), Festivals.activeOn(LocalDate.of(2026, 12, 30)))
        assertEquals(listOf(Festival.NEW_YEAR), Festivals.activeOn(LocalDate.of(2027, 1, 2)))
        assertTrue(Festivals.activeOn(LocalDate.of(2026, 12, 29)).isEmpty())
        assertEquals(listOf(Festival.VALENTINE), Festivals.activeOn(LocalDate.of(2026, 2, 13)))
        assertEquals(listOf(Festival.INDEPENDENCE), Festivals.activeOn(LocalDate.of(2026, 7, 3)))
        assertEquals(listOf(Festival.HALLOWEEN), Festivals.activeOn(LocalDate.of(2026, 10, 30)))
        assertEquals(listOf(Festival.HALLOWEEN), Festivals.activeOn(LocalDate.of(2026, 11, 1)))
        assertEquals(listOf(Festival.BONFIRE), Festivals.activeOn(LocalDate.of(2026, 11, 4)))
        assertEquals(listOf(Festival.CHRISTMAS), Festivals.activeOn(LocalDate.of(2026, 12, 23)))
        assertEquals(listOf(Festival.CHRISTMAS), Festivals.activeOn(LocalDate.of(2026, 12, 24)))
        assertEquals(listOf(Festival.CHRISTMAS), Festivals.activeOn(LocalDate.of(2026, 12, 26)))
        assertTrue(Festivals.activeOn(LocalDate.of(2026, 12, 22)).isEmpty())
        assertTrue(Festivals.activeOn(LocalDate.of(2026, 12, 27)).isEmpty())
        assertEquals(listOf(Festival.SONGKRAN), Festivals.activeOn(LocalDate.of(2026, 4, 12)))
        assertEquals(listOf(Festival.SONGKRAN), Festivals.activeOn(LocalDate.of(2026, 4, 16)))
        assertFalse(Festival.SONGKRAN in Festivals.activeOn(LocalDate.of(2026, 4, 11)))
        assertFalse(Festival.SONGKRAN in Festivals.activeOn(LocalDate.of(2026, 4, 17)))
    }

    @Test
    fun springCoversNewYearsEveThroughTheFirstAndOneDayOut() {
        assertEquals(listOf(Festival.VALENTINE, Festival.SPRING), Festivals.activeOn(LocalDate.of(2026, 2, 15)))
        assertEquals(listOf(Festival.SPRING), Festivals.activeOn(LocalDate.of(2026, 2, 16)))
        assertEquals(listOf(Festival.SPRING), Festivals.activeOn(LocalDate.of(2026, 2, 18)))
        assertFalse(Festival.SPRING in Festivals.activeOn(LocalDate.of(2026, 2, 14)))
        assertFalse(Festival.SPRING in Festivals.activeOn(LocalDate.of(2026, 2, 19)))
        assertEquals("🧧🧨🎆", Festival.SPRING.emoji)
    }

    @Test
    fun lanternFestivalAndMakhaBuchaShareADayAsTwoRows() {
        val both = Festivals.activeOn(LocalDate.of(2026, 3, 3))
        assertEquals(listOf(Festival.LANTERN, Festival.MAKHA), both)
        assertEquals("🏮🌕", Festival.LANTERN.emoji)
        assertEquals("🪷🙏", Festival.MAKHA.emoji)
    }

    @Test
    fun movableCentersFrom2026Through2030() {
        val centers = listOf(
            "2026-04-05" to Festival.EASTER,
            "2027-03-28" to Festival.EASTER,
            "2028-04-16" to Festival.EASTER,
            "2029-04-01" to Festival.EASTER,
            "2030-04-21" to Festival.EASTER,
            "2026-11-26" to Festival.THANKSGIVING,
            "2027-11-25" to Festival.THANKSGIVING,
            "2028-11-23" to Festival.THANKSGIVING,
            "2029-11-22" to Festival.THANKSGIVING,
            "2030-11-28" to Festival.THANKSGIVING,
            "2026-02-17" to Festival.SPRING,
            "2027-02-06" to Festival.SPRING,
            "2028-01-26" to Festival.SPRING,
            "2029-02-13" to Festival.SPRING,
            "2030-02-03" to Festival.SPRING,
            "2026-03-03" to Festival.LANTERN,
            "2027-02-20" to Festival.LANTERN,
            "2030-02-17" to Festival.LANTERN,
            "2026-06-19" to Festival.DRAGON_BOAT,
            "2027-06-09" to Festival.DRAGON_BOAT,
            "2028-05-28" to Festival.DRAGON_BOAT,
            "2026-08-19" to Festival.QIXI,
            "2027-08-08" to Festival.QIXI,
            "2026-09-25" to Festival.MID_AUTUMN,
            "2027-09-15" to Festival.MID_AUTUMN,
            "2026-10-18" to Festival.DOUBLE_NINTH,
            "2027-10-08" to Festival.DOUBLE_NINTH,
            "2030-10-05" to Festival.DOUBLE_NINTH,
            "2026-03-03" to Festival.MAKHA,
            "2027-02-21" to Festival.MAKHA,
            "2028-02-10" to Festival.MAKHA,
            "2029-02-28" to Festival.MAKHA,
            "2030-02-18" to Festival.MAKHA,
            "2026-05-31" to Festival.VISAKHA,
            "2027-05-20" to Festival.VISAKHA,
            "2028-05-09" to Festival.VISAKHA,
            "2029-05-27" to Festival.VISAKHA,
            "2030-05-17" to Festival.VISAKHA,
            "2026-11-24" to Festival.LOY_KRATHONG,
            "2027-11-14" to Festival.LOY_KRATHONG,
            "2028-11-02" to Festival.LOY_KRATHONG,
            "2029-11-21" to Festival.LOY_KRATHONG,
            "2030-11-10" to Festival.LOY_KRATHONG,
        )
        for ((iso, festival) in centers) {
            assertTrue("$iso ${festival.name}", festival in Festivals.activeOn(LocalDate.parse(iso)))
        }
        assertEquals("🎄🎅🎁", Festival.CHRISTMAS.emoji)
        assertEquals("🎉🥂🎆", Festival.NEW_YEAR.emoji)
        assertEquals("💦🐘", Festival.SONGKRAN.emoji)
        assertEquals("🕯️🪷🌕", Festival.LOY_KRATHONG.emoji)
    }

    @Test
    fun loyKrathongAndThanksgivingShareNovemberTwentyFifth() {
        assertEquals(
            listOf(Festival.THANKSGIVING, Festival.LOY_KRATHONG),
            Festivals.activeOn(LocalDate.of(2026, 11, 25)),
        )
    }

    @Test
    fun easterAndSongkranShareTheEdgeOf2028() {
        assertEquals(
            listOf(Festival.EASTER, Festival.SONGKRAN),
            Festivals.activeOn(LocalDate.of(2028, 4, 16)),
        )
    }

    @Test
    fun movableFestivalsStayDarkOutside2026Through2030() {
        assertFalse(Festival.SPRING in Festivals.activeOn(LocalDate.of(2025, 1, 29)))
        assertFalse(Festival.EASTER in Festivals.activeOn(LocalDate.of(2025, 4, 20)))
        assertFalse(Festival.SPRING in Festivals.activeOn(LocalDate.of(2031, 1, 23)))
        assertFalse(Festival.EASTER in Festivals.activeOn(LocalDate.of(2031, 4, 13)))
        assertFalse(Festival.MAKHA in Festivals.activeOn(LocalDate.of(2031, 3, 9)))
        assertEquals(listOf(Festival.SONGKRAN), Festivals.activeOn(LocalDate.of(2031, 4, 13)))
        assertEquals(listOf(Festival.CHRISTMAS), Festivals.activeOn(LocalDate.of(2025, 12, 25)))
        assertEquals(listOf(Festival.NEW_YEAR), Festivals.activeOn(LocalDate.of(2031, 1, 1)))
    }

    @Test
    fun dateTextNamesTheFestivalDayInSmallParentheses() {
        assertEquals("（10/18）", Festivals.dateText(Festival.DOUBLE_NINTH, LocalDate.of(2026, 10, 17)))
        assertEquals("（10/18）", Festivals.dateText(Festival.DOUBLE_NINTH, LocalDate.of(2026, 10, 18)))
        assertEquals("（10/18）", Festivals.dateText(Festival.DOUBLE_NINTH, LocalDate.of(2026, 10, 19)))
        assertEquals("（10/8）", Festivals.dateText(Festival.DOUBLE_NINTH, LocalDate.of(2027, 10, 7)))
        assertEquals("（2/14）", Festivals.dateText(Festival.VALENTINE, LocalDate.of(2026, 2, 13)))
        assertEquals("（2/16-2/17）", Festivals.dateText(Festival.SPRING, LocalDate.of(2026, 2, 15)))
        assertEquals("（2/16-2/17）", Festivals.dateText(Festival.SPRING, LocalDate.of(2026, 2, 18)))
        assertEquals("（12/31-1/1）", Festivals.dateText(Festival.NEW_YEAR, LocalDate.of(2026, 12, 30)))
        assertEquals("（12/31-1/1）", Festivals.dateText(Festival.NEW_YEAR, LocalDate.of(2027, 1, 2)))
        assertEquals("（4/13-4/15）", Festivals.dateText(Festival.SONGKRAN, LocalDate.of(2026, 4, 16)))
        assertEquals("（4/16）", Festivals.dateText(Festival.EASTER, LocalDate.of(2028, 4, 16)))
        assertEquals("（12/24-12/25）", Festivals.dateText(Festival.CHRISTMAS, LocalDate.of(2026, 12, 23)))
        assertEquals("（10/18）", Festivals.spanText(Festival.DOUBLE_NINTH, 2026))
        assertEquals("（12/24-12/25）", Festivals.spanText(Festival.CHRISTMAS, 2026))
        assertEquals("", Festivals.spanText(Festival.SPRING, 2025))
        assertEquals("（12/24-12/25）", Festivals.dateText(Festival.CHRISTMAS, LocalDate.of(2026, 12, 26)))
        assertTrue(Festivals.isFormal(Festival.CHRISTMAS, LocalDate.of(2026, 12, 24)))
        assertTrue(Festivals.isFormal(Festival.CHRISTMAS, LocalDate.of(2026, 12, 25)))
        assertFalse(Festivals.isFormal(Festival.CHRISTMAS, LocalDate.of(2026, 12, 23)))
        assertFalse(Festivals.isFormal(Festival.CHRISTMAS, LocalDate.of(2026, 12, 26)))
        assertTrue(Festivals.isFormal(Festival.SPRING, LocalDate.of(2026, 2, 16)))
        assertTrue(Festivals.isFormal(Festival.SPRING, LocalDate.of(2026, 2, 17)))
        assertFalse(Festivals.isFormal(Festival.SPRING, LocalDate.of(2026, 2, 15)))
        assertFalse(Festivals.isFormal(Festival.SPRING, LocalDate.of(2026, 2, 18)))
        assertTrue(Festivals.isFormal(Festival.SONGKRAN, LocalDate.of(2026, 4, 14)))
        assertFalse(Festivals.isFormal(Festival.SONGKRAN, LocalDate.of(2026, 4, 12)))
        assertTrue(Festivals.isFormal(Festival.NEW_YEAR, LocalDate.of(2026, 12, 31)))
        assertFalse(Festivals.isFormal(Festival.NEW_YEAR, LocalDate.of(2026, 12, 30)))
        assertEquals("（4/13-4/15）", Festivals.dateText(Festival.SONGKRAN, LocalDate.of(2028, 4, 16)))
    }
}
