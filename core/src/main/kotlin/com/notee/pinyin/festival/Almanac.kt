package com.notee.pinyin.festival

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Lunar date and the day's 黄黑十二神 for 2026-01-01 through 2030-12-31.
 *
 * The phone only looks a day up. Outside this span [on] is null, and a formal festival day
 * falls back to the emoji (docs/02-0 §5). Leap months use the same month's 青龙起支 as the
 * month they repeat. The twelve gods, starting at 青龙 and stepping through the earthly
 * branches, are 青龙、明堂、天刑、朱雀、金匮、天德、白虎、玉堂、天牢、玄武、司命、勾陈.
 *
 * Packed as big-endian unsigned shorts: bits 0–3 the god, 4–8 the lunar day, 9–12 the lunar
 * month, bit 13 set when the month is a leap month.
 */
object Almanac {
    private val FIRST: LocalDate = LocalDate.of(2026, 1, 1)
    private const val DAYS = 1826

    private val packed: ShortArray = decode(DATA)

    fun on(date: LocalDate): AlmanacDay? {
        val index = ChronoUnit.DAYS.between(FIRST, date).toInt()
        if (index !in 0 until DAYS) return null
        val value = packed[index].toInt() and 0xFFFF
        val duty = Duty.entries[(value and 0xF)]
        val day = (value shr 4) and 0x1F
        val month = (value shr 9) and 0xF
        val leap = (value shr 13) and 1 == 1
        return AlmanacDay(month, day, leap, duty)
    }

    private fun decode(text: String): ShortArray {
        val bytes = Base64.getDecoder().decode(text)
        return ShortArray(bytes.size / 2) { index ->
            val high = bytes[index * 2].toInt() and 0xFF
            val low = bytes[index * 2 + 1].toInt() and 0xFF
            ((high shl 8) or low).toShort()
        }
    }
}

class AlmanacDay(
    val month: Int,
    val day: Int,
    val leap: Boolean,
    val duty: Duty,
) {
    /** 农历八月廿七, or 农历闰五月初一. English is `Lunar 8/27` or `Lunar leap 5/1`. */
    fun lunarLine(english: Boolean): String {
        if (english) {
            val leapWord = if (leap) "leap " else ""
            return "Lunar $leapWord$month/$day"
        }
        val leapWord = if (leap) "闰" else ""
        return "农历$leapWord${monthName(month)}月${dayName(day)}"
    }

    /** 黄道日 · 青龙, or `Inauspicious · White Tiger`. */
    fun dutyLine(english: Boolean): String {
        val kind = when {
            english && duty.auspicious -> "Auspicious"
            english -> "Inauspicious"
            duty.auspicious -> "黄道日"
            else -> "黑道日"
        }
        val name = if (english) duty.english else duty.chinese
        return "$kind · $name"
    }

    private fun monthName(month: Int): String = when (month) {
        10 -> "十"
        11 -> "冬"
        12 -> "腊"
        else -> "正二三四五六七八九"[month - 1].toString()
    }

    private fun dayName(day: Int): String = when (day) {
        in 1..10 -> "初${digit(day)}"
        in 11..19 -> "十${digit(day - 10)}"
        20 -> "二十"
        in 21..29 -> "廿${digit(day - 20)}"
        else -> "三十"
    }

    private fun digit(n: Int): String = "一二三四五六七八九十"[n - 1].toString()
}

enum class Duty(val auspicious: Boolean, val chinese: String, val english: String) {
    AZURE_DRAGON(true, "青龙", "Azure Dragon"),
    BRIGHT_HALL(true, "明堂", "Bright Hall"),
    HEAVENLY_PUNISHMENT(false, "天刑", "Heavenly Punishment"),
    VERMILION_BIRD(false, "朱雀", "Vermilion Bird"),
    GOLDEN_CASKET(true, "金匮", "Golden Casket"),
    HEAVENLY_VIRTUE(true, "天德", "Heavenly Virtue"),
    WHITE_TIGER(false, "白虎", "White Tiger"),
    JADE_HALL(true, "玉堂", "Jade Hall"),
    HEAVENLY_PRISON(false, "天牢", "Heavenly Prison"),
    BLACK_TORTOISE(false, "玄武", "Black Tortoise"),
    LIFE_STAR(true, "司命", "Life Star"),
    GOUCHEN(false, "勾陈", "Gouchen"),
}

private const val DATA = "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"
