package com.notee.pinyin.festival

import java.time.LocalDate
import java.time.MonthDay

/**
 * Traditional festivals for Britain, the US, China and Thailand (docs/02-0 §5).
 *
 * Fixed civil dates are rules and apply in any year. Movable dates — Chinese lunar festivals,
 * Western Easter, US Thanksgiving, and the three Thai full-moon days — come from a table that
 * covers 2026-01-01 through 2030-12-31 only. Outside that span those festivals do not light;
 * nothing is guessed past the last tabulated year. The end year moves only on the first store
 * release of a calendar year, and only when fewer than five years remain from that year
 * (docs/00-0 §5, docs/02-0 §5).
 */
object Festivals {
    val FIRST: LocalDate = LocalDate.of(2026, 1, 1)
    val LAST: LocalDate = LocalDate.of(2030, 12, 31)

    /** Festivals active on [date], in catalog order. Two on the same day stay in that order. */
    fun activeOn(date: LocalDate): List<Festival> {
        val found = ArrayList<Festival>(2)
        if (inNewYear(date)) found += Festival.NEW_YEAR
        if (around(date, MonthDay.of(2, 14))) found += Festival.VALENTINE
        if (inTable(date) && around(date, movable.easter(date.year))) found += Festival.EASTER
        if (around(date, MonthDay.of(7, 4))) found += Festival.INDEPENDENCE
        if (inHalloween(date)) found += Festival.HALLOWEEN
        if (inTable(date) && around(date, movable.thanksgiving(date.year))) found += Festival.THANKSGIVING
        if (around(date, MonthDay.of(11, 5))) found += Festival.BONFIRE
        if (inChristmas(date)) found += Festival.CHRISTMAS
        if (inTable(date)) {
            val year = movable.chinese(date.year)
            if (span(date, year.eve, year.day)) found += Festival.SPRING
            if (around(date, year.lantern)) found += Festival.LANTERN
            if (around(date, year.dragonBoat)) found += Festival.DRAGON_BOAT
            if (around(date, year.qixi)) found += Festival.QIXI
            if (around(date, year.midAutumn)) found += Festival.MID_AUTUMN
            if (around(date, year.doubleNinth)) found += Festival.DOUBLE_NINTH
            if (around(date, movable.makha(date.year))) found += Festival.MAKHA
        }
        if (inSongkran(date)) found += Festival.SONGKRAN
        if (inTable(date)) {
            if (around(date, movable.visakha(date.year))) found += Festival.VISAKHA
            if (around(date, movable.loyKrathong(date.year))) found += Festival.LOY_KRATHONG
        }
        return found
    }

    /**
     * True only on the festival's own days. The day added at each end of the window is not formal:
     * a tap there still sends the emoji, not the blessing card.
     */
    fun isFormal(festival: Festival, date: LocalDate): Boolean {
        for (year in date.year - 1..date.year + 1) {
            val days = daysOf(festival, year) ?: continue
            if (days.any { it == date }) return true
        }
        return false
    }

    /**
     * The festival's own day, in parentheses, for the row next to [Festival.label].
     * A one-day festival is `（10/18）`. A span is the first day through the last, `（2/16-2/17）`,
     * not the extra day on either side of the window. [on] only picks which year's occurrence
     * is showing; the day before or after still names the festival day itself.
     */
    fun dateText(festival: Festival, on: LocalDate): String = formatSpan(occurrence(festival, on))

    /**
     * The formal span in [year], without asking whether [year] is inside the window.
     * Empty when that year has no table row. The test list uses this so a festival
     * shown out of season still names its own day.
     */
    fun spanText(festival: Festival, year: Int): String {
        val days = daysOf(festival, year) ?: return ""
        return formatSpan(days)
    }

    private fun formatSpan(days: List<LocalDate>): String {
        val first = days.first()
        val last = days.last()
        val start = "${first.monthValue}/${first.dayOfMonth}"
        if (first == last) return "（$start）"
        return "（$start-${last.monthValue}/${last.dayOfMonth}）"
    }

    private fun occurrence(festival: Festival, on: LocalDate): List<LocalDate> {
        for (year in on.year - 1..on.year + 1) {
            val days = daysOf(festival, year) ?: continue
            if (!on.isBefore(days.first().minusDays(1)) && !on.isAfter(days.last().plusDays(1))) return days
        }
        return listOf(on)
    }

    /** The festival days in [year], without the one-day margin. Null when that year has no table row. */
    private fun daysOf(festival: Festival, year: Int): List<LocalDate>? = when (festival) {
        Festival.NEW_YEAR -> listOf(LocalDate.of(year - 1, 12, 31), LocalDate.of(year, 1, 1))
        Festival.VALENTINE -> listOf(LocalDate.of(year, 2, 14))
        Festival.INDEPENDENCE -> listOf(LocalDate.of(year, 7, 4))
        Festival.HALLOWEEN -> listOf(LocalDate.of(year, 10, 31))
        Festival.BONFIRE -> listOf(LocalDate.of(year, 11, 5))
        Festival.CHRISTMAS -> listOf(LocalDate.of(year, 12, 24), LocalDate.of(year, 12, 25))
        Festival.SONGKRAN -> (13..15).map { LocalDate.of(year, 4, it) }
        else -> movableDays(festival, year)
    }

    private fun movableDays(festival: Festival, year: Int): List<LocalDate>? {
        if (year < FIRST.year || year > LAST.year) return null
        val chinese = movable.chinese(year)
        return when (festival) {
            Festival.EASTER -> listOf(movable.easter(year))
            Festival.THANKSGIVING -> listOf(movable.thanksgiving(year))
            Festival.SPRING -> listOf(chinese.eve, chinese.day)
            Festival.LANTERN -> listOf(chinese.lantern)
            Festival.DRAGON_BOAT -> listOf(chinese.dragonBoat)
            Festival.QIXI -> listOf(chinese.qixi)
            Festival.MID_AUTUMN -> listOf(chinese.midAutumn)
            Festival.DOUBLE_NINTH -> listOf(chinese.doubleNinth)
            Festival.MAKHA -> listOf(movable.makha(year))
            Festival.VISAKHA -> listOf(movable.visakha(year))
            Festival.LOY_KRATHONG -> listOf(movable.loyKrathong(year))
            else -> null
        }
    }

    private fun inTable(date: LocalDate) = !date.isBefore(FIRST) && !date.isAfter(LAST)

    /** Dec 31 and Jan 1, then one day either side: Dec 30 through Jan 2. */
    private fun inNewYear(date: LocalDate): Boolean {
        val day = date.dayOfMonth
        return (date.monthValue == 12 && day >= 30) || (date.monthValue == 1 && day <= 2)
    }

    /** Oct 31, then one day either side. */
    private fun inHalloween(date: LocalDate): Boolean =
        (date.monthValue == 10 && date.dayOfMonth >= 30) || (date.monthValue == 11 && date.dayOfMonth == 1)

    /** Christmas Eve and Christmas Day, then one day either side: Dec 23 through Dec 26. */
    private fun inChristmas(date: LocalDate): Boolean =
        date.monthValue == 12 && date.dayOfMonth in 23..26

    /** Apr 13–15, then one day either side: Apr 12 through Apr 16. */
    private fun inSongkran(date: LocalDate): Boolean =
        date.monthValue == 4 && date.dayOfMonth in 12..16

    private fun around(date: LocalDate, center: LocalDate): Boolean =
        !date.isBefore(center.minusDays(1)) && !date.isAfter(center.plusDays(1))

    private fun around(date: LocalDate, center: MonthDay): Boolean = around(date, center.atYear(date.year))

    private fun span(date: LocalDate, first: LocalDate, last: LocalDate): Boolean =
        !date.isBefore(first.minusDays(1)) && !date.isAfter(last.plusDays(1))

    /**
     * One row per year, 2026 through 2030.
     * Chinese dates are the civil day of that lunar date (除夕 is the day before 正月初一).
     * Thai dates are the published observance. Where a listing and the Bangkok full moon
     * differ by a day, the date two listings share is kept: 2028 佛诞 is May 9, 2029 万佛节
     * is Feb 28, 2029 佛诞 is May 27, 2030 佛诞 is May 17.
     */
    private val movable = Movable(
        easter = dates("2026-04-05", "2027-03-28", "2028-04-16", "2029-04-01", "2030-04-21"),
        thanksgiving = dates("2026-11-26", "2027-11-25", "2028-11-23", "2029-11-22", "2030-11-28"),
        chinese = listOf(
            Chinese("2026-02-16", "2026-02-17", "2026-03-03", "2026-06-19", "2026-08-19", "2026-09-25", "2026-10-18"),
            Chinese("2027-02-05", "2027-02-06", "2027-02-20", "2027-06-09", "2027-08-08", "2027-09-15", "2027-10-08"),
            Chinese("2028-01-25", "2028-01-26", "2028-02-09", "2028-05-28", "2028-08-26", "2028-10-03", "2028-10-26"),
            Chinese("2029-02-12", "2029-02-13", "2029-02-27", "2029-06-16", "2029-08-16", "2029-09-22", "2029-10-16"),
            Chinese("2030-02-02", "2030-02-03", "2030-02-17", "2030-06-05", "2030-08-05", "2030-09-12", "2030-10-05"),
        ),
        makha = dates("2026-03-03", "2027-02-21", "2028-02-10", "2029-02-28", "2030-02-18"),
        visakha = dates("2026-05-31", "2027-05-20", "2028-05-09", "2029-05-27", "2030-05-17"),
        loyKrathong = dates("2026-11-24", "2027-11-14", "2028-11-02", "2029-11-21", "2030-11-10"),
    )

    private fun dates(vararg iso: String): List<LocalDate> = iso.map(LocalDate::parse)
}

enum class Festival(val label: String, val emoji: String, val effect: FestivalEffect) {
    NEW_YEAR("新年", "🎉🥂🎆", FestivalEffect.FIREWORKS),
    VALENTINE("情人节", "💝🌹", FestivalEffect.HEARTS),
    EASTER("复活节", "🐣🌷", FestivalEffect.EGGS),
    INDEPENDENCE("独立日", "🎆🇺🇸", FestivalEffect.FIREWORKS),
    HALLOWEEN("万圣节", "🎃👻", FestivalEffect.PUMPKIN),
    THANKSGIVING("感恩节", "🦃🍂", FestivalEffect.LEAVES),
    BONFIRE("篝火之夜", "🔥🎆", FestivalEffect.BONFIRE),
    CHRISTMAS("圣诞节", "🎄🎅🎁", FestivalEffect.TREE),
    SPRING("春节", "🧧🧨🎆", FestivalEffect.FIREWORKS),
    LANTERN("元宵", "🏮🌕", FestivalEffect.LANTERNS),
    DRAGON_BOAT("端午", "🐲🚣", FestivalEffect.BOAT),
    QIXI("七夕", "🌌💫", FestivalEffect.STARS),
    MID_AUTUMN("中秋", "🌕🥮", FestivalEffect.MOON),
    DOUBLE_NINTH("重阳", "🌼🏔️", FestivalEffect.FLOWER),
    MAKHA("万佛节", "🪷🙏", FestivalEffect.LOTUS),
    SONGKRAN("宋干节", "💦🐘", FestivalEffect.WATER),
    VISAKHA("佛诞节", "🪷🕯️", FestivalEffect.LOTUS),
    LOY_KRATHONG("水灯节", "🕯️🪷🌕", FestivalEffect.KRATHONG),
}

enum class FestivalEffect {
    FIREWORKS,
    HEARTS,
    EGGS,
    PUMPKIN,
    LEAVES,
    BONFIRE,
    TREE,
    LANTERNS,
    BOAT,
    STARS,
    MOON,
    FLOWER,
    LOTUS,
    WATER,
    KRATHONG,
}

private class Chinese(
    eveIso: String,
    dayIso: String,
    lanternIso: String,
    dragonIso: String,
    qixiIso: String,
    midIso: String,
    ninthIso: String,
) {
    val eve: LocalDate = LocalDate.parse(eveIso)
    val day: LocalDate = LocalDate.parse(dayIso)
    val lantern: LocalDate = LocalDate.parse(lanternIso)
    val dragonBoat: LocalDate = LocalDate.parse(dragonIso)
    val qixi: LocalDate = LocalDate.parse(qixiIso)
    val midAutumn: LocalDate = LocalDate.parse(midIso)
    val doubleNinth: LocalDate = LocalDate.parse(ninthIso)
}

private class Movable(
    private val easter: List<LocalDate>,
    private val thanksgiving: List<LocalDate>,
    private val chinese: List<Chinese>,
    private val makha: List<LocalDate>,
    private val visakha: List<LocalDate>,
    private val loyKrathong: List<LocalDate>,
) {
    fun easter(year: Int) = easter[year - 2026]
    fun thanksgiving(year: Int) = thanksgiving[year - 2026]
    fun chinese(year: Int) = chinese[year - 2026]
    fun makha(year: Int) = makha[year - 2026]
    fun visakha(year: Int) = visakha[year - 2026]
    fun loyKrathong(year: Int) = loyKrathong[year - 2026]
}
