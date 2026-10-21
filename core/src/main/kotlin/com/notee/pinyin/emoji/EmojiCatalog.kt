package com.notee.pinyin.emoji

/** The emoji groups of `emoji.tsv` (docs/02-0 §2). [key] is the name there. */
enum class EmojiCategory(val key: String) {
    FACE("face"),
    HAND("hand"),
    SYMBOL("symbol"),
    PERSON("person"),
    FOOD("food"),
    ANIMAL("animal"),
    PLANT("plant"),
}

/** The tabs after 常用, in tab order. A tab of two categories rules a line between them. */
enum class EmojiTab(val categories: List<EmojiCategory>) {
    FACE(listOf(EmojiCategory.FACE)),
    HAND(listOf(EmojiCategory.HAND)),
    SYMBOL(listOf(EmojiCategory.SYMBOL)),
    PERSON(listOf(EmojiCategory.PERSON)),
    FOOD(listOf(EmojiCategory.FOOD)),
    NATURE(listOf(EmojiCategory.ANIMAL, EmojiCategory.PLANT)),
}

/** One cell of an emoji page. */
sealed interface EmojiCell {
    class Emoji(val text: String) : EmojiCell

    /** The `|` between two halves of a 常用 row. */
    data object Bar : EmojiCell

    data object Empty : EmojiCell

    /** A line across a row of its own, between the categories of one tab. */
    data object Rule : EmojiCell
}

class EmojiCatalog(private val byCategory: Map<EmojiCategory, List<String>>) {

    fun emoji(category: EmojiCategory): List<String> = byCategory[category].orEmpty()

    /** Same catalog without what the device cannot draw. */
    fun filter(canShow: (String) -> Boolean): EmojiCatalog = EmojiCatalog(byCategory.mapValues { (_, list) -> list.filter(canShow) })

    /** A tab's emoji in preset order, its categories one after another with a [EmojiCell.Rule] between. */
    fun page(tab: EmojiTab): List<EmojiCell> = tab.categories.flatMapIndexed { index, category ->
        val cells = emoji(category).map(EmojiCell::Emoji)
        if (index == 0) cells else listOf(EmojiCell.Rule) + cells
    }

    /**
     * The 常用 page, 7 × 5, row by row: three rows of 7 from 黄脸, 手势, 元素, then 人物 | 食物 and
     * 动物 | 植物, 3 on each side of a `|`. Within a category: most used first, preset order for
     * ties and for anything never used. Short categories leave cells empty.
     */
    fun frequentPage(counts: Map<String, Int>): List<EmojiCell> {
        fun top(category: EmojiCategory, n: Int): List<EmojiCell> {
            val ranked = emoji(category).sortedByDescending { counts[it] ?: 0 }.take(n).map(EmojiCell::Emoji)
            return ranked + List(n - ranked.size) { EmojiCell.Empty }
        }
        return top(EmojiCategory.FACE, COLUMNS) +
            top(EmojiCategory.HAND, COLUMNS) +
            top(EmojiCategory.SYMBOL, COLUMNS) +
            top(EmojiCategory.PERSON, HALF) + EmojiCell.Bar + top(EmojiCategory.FOOD, HALF) +
            top(EmojiCategory.ANIMAL, HALF) + EmojiCell.Bar + top(EmojiCategory.PLANT, HALF)
    }

    companion object {
        const val COLUMNS = 7
        const val ROWS = 5
        private const val HALF = 3

        /** Reads `category<TAB>emoji emoji …` lines. */
        fun parse(text: String): EmojiCatalog {
            val byKey = EmojiCategory.entries.associateBy { it.key }
            val byCategory = HashMap<EmojiCategory, List<String>>()
            text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line ->
                    val columns = line.split('\t')
                    val category = byKey[columns[0]] ?: error("unknown emoji category: ${columns[0]}")
                    byCategory[category] = columns.getOrElse(1) { "" }.split(' ').filter { it.isNotEmpty() }
                }
            return EmojiCatalog(byCategory)
        }
    }
}
