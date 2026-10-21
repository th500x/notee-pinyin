package com.notee.pinyin.festival

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmanacTest {
    @Test
    fun publishedDaysKeepTheirLunarDateAndDuty() {
        val newYear = Almanac.on(LocalDate.of(2026, 2, 17))!!
        assertEquals(1, newYear.month)
        assertEquals(1, newYear.day)
        assertFalse(newYear.leap)
        assertEquals(Duty.LIFE_STAR, newYear.duty)
        assertEquals("农历正月初一", newYear.lunarLine(false))
        assertEquals("Lunar 1/1", newYear.lunarLine(true))
        assertEquals("黄道日 · 司命", newYear.dutyLine(false))
        assertEquals("Auspicious · Life Star", newYear.dutyLine(true))

        val lantern = Almanac.on(LocalDate.of(2026, 3, 3))!!
        assertEquals(1, lantern.month)
        assertEquals(15, lantern.day)
        assertEquals(Duty.AZURE_DRAGON, lantern.duty)

        val midAutumn = Almanac.on(LocalDate.of(2026, 9, 25))!!
        assertEquals(8, midAutumn.month)
        assertEquals(15, midAutumn.day)
        assertEquals(Duty.AZURE_DRAGON, midAutumn.duty)
        assertEquals("农历八月十五", midAutumn.lunarLine(false))

        val ninth = Almanac.on(LocalDate.of(2026, 10, 18))!!
        assertEquals(9, ninth.month)
        assertEquals(9, ninth.day)
        assertEquals(Duty.BLACK_TORTOISE, ninth.duty)
        assertEquals("农历九月初九", ninth.lunarLine(false))
        assertEquals("Lunar 9/9", ninth.lunarLine(true))
        assertEquals("黑道日 · 玄武", ninth.dutyLine(false))
        assertEquals("Inauspicious · Black Tortoise", ninth.dutyLine(true))

        val eve = Almanac.on(LocalDate.of(2026, 2, 16))!!
        assertEquals(12, eve.month)
        assertEquals(29, eve.day)
        assertEquals("农历腊月廿九", eve.lunarLine(false))

        val winter = Almanac.on(LocalDate.of(2026, 1, 1))!!
        assertEquals(11, winter.month)
        assertEquals(13, winter.day)
        assertEquals(Duty.VERMILION_BIRD, winter.duty)
        assertEquals("农历冬月十三", winter.lunarLine(false))
    }

    @Test
    fun chineseFestivalDatesMatchTheLunarFestivals() {
        val years = listOf(
            listOf("2026-02-16", "2026-02-17", "2026-03-03", "2026-06-19", "2026-08-19", "2026-09-25", "2026-10-18"),
            listOf("2027-02-05", "2027-02-06", "2027-02-20", "2027-06-09", "2027-08-08", "2027-09-15", "2027-10-08"),
            listOf("2028-01-25", "2028-01-26", "2028-02-09", "2028-05-28", "2028-08-26", "2028-10-03", "2028-10-26"),
            listOf("2029-02-12", "2029-02-13", "2029-02-27", "2029-06-16", "2029-08-16", "2029-09-22", "2029-10-16"),
            listOf("2030-02-02", "2030-02-03", "2030-02-17", "2030-06-05", "2030-08-05", "2030-09-12", "2030-10-05"),
        )
        val expect = listOf(12 to null, 1 to 1, 1 to 15, 5 to 5, 7 to 7, 8 to 15, 9 to 9)
        for (row in years) {
            row.zip(expect).forEach { (iso, wanted) ->
                val day = Almanac.on(LocalDate.parse(iso))!!
                assertFalse(day.leap)
                assertEquals(wanted.first, day.month)
                if (wanted.second != null) assertEquals(wanted.second, day.day)
            }
        }
    }

    @Test
    fun leapMonthKeepsTheMonthNumber() {
        val leap = Almanac.on(LocalDate.of(2028, 6, 23))!!
        assertTrue(leap.leap)
        assertEquals(5, leap.month)
        assertEquals(1, leap.day)
        assertEquals("农历闰五月初一", leap.lunarLine(false))
        assertEquals("Lunar leap 5/1", leap.lunarLine(true))
        assertFalse(Almanac.on(LocalDate.of(2028, 6, 22))!!.leap)
    }

    @Test
    fun outsideTheFiveYearsThereIsNoRow() {
        assertNull(Almanac.on(LocalDate.of(2025, 12, 31)))
        assertNull(Almanac.on(LocalDate.of(2031, 1, 1)))
    }
}
