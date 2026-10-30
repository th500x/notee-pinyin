package com.notee.pinyin.rank

/**
 * Orders that leave the default (docs/03-0 §2, docs/01-0 §5).
 *
 * Default: each syllable's pins in table order; on 9-key the syllables go by dictionary weight;
 * a whole word goes by dictionary weight. A row here replaces that for one specific case.
 * The next adjustment is another row.
 */
object OrderAdjustments {
    enum class Kind { SYLLABLE, GROUP }

    /** [spellings] is one syllable, or the full-cover readings when none has been chosen. */
    data class Row(val kind: Kind, val spellings: Set<String>, val leading: List<String>)

    val rows: List<Row> = listOf(
        Row(Kind.SYLLABLE, setOf("ba"), listOf("吧", "把", "八")),
        Row(Kind.GROUP, setOf("min", "nin"), listOf("您", "民", "敏")),
        Row(Kind.GROUP, setOf("ke", "le"), listOf("了")),
    )

    /** [earlier] stays ahead of [later] whenever both are candidates. Dictionary weight does not swap them. */
    data class Phrase(val earlier: String, val later: String)

    val phrases: List<Phrase> = listOf(
        Phrase("租金", "租赁"),
    )

    /** [tableOrder] with this syllable's row in front. Unlisted pins keep their table order after it. */
    fun pinOrder(spelling: String, tableOrder: List<String>): List<String> {
        val leading = rows.firstOrNull { it.kind == Kind.SYLLABLE && it.spellings == setOf(spelling) }?.leading
            ?: return tableOrder
        return leading + tableOrder.filter { it !in leading }
    }

    /** The leading run when [spellings] are exactly one group's full-cover readings. */
    fun together(spellings: Set<String>): List<String>? =
        rows.firstOrNull { it.kind == Kind.GROUP && it.spellings == spellings }?.leading
}
