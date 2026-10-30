package com.notee.pinyin.keyboard

import android.content.res.Resources
import com.notee.pinyin.R

/** Everything outside the page itself that changes what the keys say. Equal caps give an equal page. */
data class KeyCaps(
    val language: Language,
    val shifted: Boolean,
    val enterLabel: String,
    val password: Boolean,
    val emojiSlots: List<String?>,
    val symbolLanguage: Language,
    /** Letter-page comma and period stay ASCII, so a domain can be typed. The 中/英 label does not follow this. */
    val email: Boolean = false,
)

/** The key pages of docs/01-1 §3 to §6, on unit grids. */
class KeyboardLayouts(private val resources: Resources) {

    fun build(layout: Layout, page: Page, caps: KeyCaps): KeyGrid = when (page) {
        Page.LETTERS -> if (layout == Layout.T9) t9(caps) else letters(caps)
        Page.NUMBERS -> numbers(caps)
        Page.SYMBOLS -> symbols(caps.symbolLanguage)
    }

    /** 20 units wide so the bottom row can use half widths. */
    private fun letters(caps: KeyCaps): KeyGrid {
        val keys = ArrayList<Key>()
        "qwertyuiop".forEachIndexed { i, c -> keys += letter(c, i * 2f, 0f, "1234567890"[i].toString(), caps) }
        "asdfghjkl".forEachIndexed { i, c -> keys += letter(c, 1f + i * 2f, 1f, "~!@#%&*()"[i].toString(), caps) }
        keys += Key(
            label(R.string.key_shift), KeyAction.Shift, 0f, 2f, 3f,
            style = if (caps.shifted) KeyStyle.ACTIVE else KeyStyle.FUNCTION,
        )
        "zxcvbnm".forEachIndexed { i, c -> keys += letter(c, 3f + i * 2f, 2f, "',-:;?".getOrNull(i)?.toString(), caps) }
        keys += delete(17f, 2f, 3f)
        keys += Key(label(R.string.key_symbols), KeyAction.Symbols, 0f, 3f, 2.5f, style = KeyStyle.FUNCTION)
        keys += Key(label(R.string.key_numbers), KeyAction.Numbers, 2.5f, 3f, 2.5f, style = KeyStyle.FUNCTION)
        val chinese = caps.language == Language.CHINESE && !caps.email
        val comma = if (chinese) "，" else ","
        val period = if (chinese) "。" else "."
        keys += Key(comma, KeyAction.Commit(comma), 5f, 3f, 2f)
        keys += space(7f, 3f, 6f)
        keys += Key(period, KeyAction.Commit(period), 13f, 3f, 2f)
        keys += language(15f, 3f, 2f, caps)
        keys += enter(17f, 3f, 3f, caps)
        return KeyGrid(20f, 4f, keys)
    }

    /** 13 units: symbol column 2, three key columns of 3, right column 2. */
    private fun t9(caps: KeyCaps): KeyGrid {
        val keys = symbolColumn(T9_COLUMN)
        val slots = caps.emojiSlots
        keys += Key(
            "", KeyAction.LogoFace, 2f, 0f, 3f,
            hint = "1", longPress = KeyAction.Commit("1"), image = R.drawable.key_logo,
        )
        T9_GROUPS.forEachIndexed { i, letters ->
            val cell = i + 1
            val digit = ('2' + i).toString()
            keys += Key(
                letters.uppercase(), KeyAction.T9('2' + i), 2f + (cell % 3) * 3f, (cell / 3).toFloat(), 3f,
                hint = digit, longPress = KeyAction.Commit(digit),
            )
        }
        keys += delete(11f, 0f, 2f)
        keys += Key(label(R.string.key_clear), KeyAction.Clear, 11f, 1f, 2f, style = KeyStyle.FUNCTION)
        keys += emojiSlot(1, slots.getOrNull(1), 11f, 2f, 2f, caps)
        keys += Key(label(R.string.key_symbols), KeyAction.Symbols, 0f, 3f, 2f, style = KeyStyle.FUNCTION)
        keys += Key(label(R.string.key_numbers), KeyAction.Numbers, 2f, 3f, 2f, style = KeyStyle.FUNCTION)
        keys += space(4f, 3f, 5f, hint = "0")
        keys += language(9f, 3f, 2f, caps)
        keys += enter(11f, 3f, 2f, caps)
        return KeyGrid(13f, 4f, keys)
    }

    private fun numbers(caps: KeyCaps): KeyGrid {
        val keys = symbolColumn(NUMBER_COLUMN)
        for (n in 1..9) {
            val digit = n.toString()
            keys += Key(digit, KeyAction.Commit(digit), 2f + ((n - 1) % 3) * 3f, ((n - 1) / 3).toFloat(), 3f)
        }
        keys += delete(11f, 0f, 2f)
        keys += Key("%", KeyAction.Commit("%"), 11f, 1f, 2f)
        keys += Key(".", KeyAction.Commit("."), 11f, 2f, 2f)
        keys += Key(label(R.string.key_symbols), KeyAction.Symbols, 0f, 3f, 2f, style = KeyStyle.FUNCTION)
        keys += Key(label(R.string.key_back), KeyAction.Back, 2f, 3f, 3f, style = KeyStyle.FUNCTION)
        keys += Key("0", KeyAction.Commit("0"), 5f, 3f, 3f)
        keys += space(8f, 3f, 3f)
        keys += enter(11f, 3f, 2f, caps)
        return KeyGrid(13f, 4f, keys)
    }

    /** 5 × 5 symbols and a bar of 返回 · 中文 · 英文 · ⌫. */
    private fun symbols(language: Language): KeyGrid {
        val symbols = if (language == Language.CHINESE) CHINESE_SYMBOLS else ENGLISH_SYMBOLS
        val keys = ArrayList<Key>()
        symbols.forEachIndexed { i, symbol ->
            val action = if (symbol in BRACKETS) KeyAction.Brackets(symbol.take(1), symbol.drop(1)) else KeyAction.Commit(symbol)
            keys += Key(symbol, action, (i % 5).toFloat(), (i / 5).toFloat(), 1f)
        }
        val quarter = 5f / 4
        keys += Key(label(R.string.key_back), KeyAction.Back, 0f, 5f, quarter, style = KeyStyle.FUNCTION)
        keys += Key(
            label(R.string.key_symbols_chinese), KeyAction.SymbolLanguage(Language.CHINESE), quarter, 5f, quarter,
            style = if (language == Language.CHINESE) KeyStyle.ACTIVE else KeyStyle.FUNCTION,
        )
        keys += Key(
            label(R.string.key_symbols_english), KeyAction.SymbolLanguage(Language.ENGLISH), 2 * quarter, 5f, quarter,
            style = if (language == Language.ENGLISH) KeyStyle.ACTIVE else KeyStyle.FUNCTION,
        )
        keys += delete(3 * quarter, 5f, quarter)
        return KeyGrid(5f, 6f, keys)
    }

    /** Four keys down the left of the 9-key and number pages, spread over the three upper rows. */
    private fun symbolColumn(column: List<Pair<String, String>>): ArrayList<Key> {
        val step = 3f / column.size
        return column.mapIndexedTo(ArrayList()) { i, (label, text) ->
            Key(label, KeyAction.Commit(text), 0f, i * step, 2f, step, style = KeyStyle.FUNCTION)
        }
    }

    private fun letter(c: Char, x: Float, y: Float, hint: String?, caps: KeyCaps): Key {
        val longPress = when {
            hint == null -> null
            hint == "'" -> KeyAction.Separator
            else -> KeyAction.Commit(hint)
        }
        val label = if (caps.shifted) c.uppercaseChar() else c
        return Key(label.toString(), KeyAction.Letter(c), x, y, 2f, hint = hint, longPress = longPress)
    }

    private fun delete(x: Float, y: Float, width: Float) =
        Key(label(R.string.key_delete), KeyAction.Delete, x, y, width, style = KeyStyle.DELETE, repeats = true)

    private fun space(x: Float, y: Float, width: Float, hint: String? = null) = Key(
        label(R.string.key_space), KeyAction.Space, x, y, width,
        hint = hint, longPress = hint?.let { KeyAction.Commit(it) },
    )

    private fun enter(x: Float, y: Float, width: Float, caps: KeyCaps) =
        Key(caps.enterLabel, KeyAction.Enter, x, y, width, style = KeyStyle.FUNCTION)

    private fun language(x: Float, y: Float, width: Float, caps: KeyCaps): Key {
        val text = label(if (caps.language == Language.CHINESE) R.string.key_chinese else R.string.key_english)
        return Key(text, KeyAction.ToggleLanguage, x, y, width, style = if (caps.password) KeyStyle.DISABLED else KeyStyle.FUNCTION)
    }

    /** The right-column emoji. Holding it opens the page to change the pin; it has no digit. */
    private fun emojiSlot(
        slot: Int,
        emoji: String?,
        x: Float,
        y: Float,
        width: Float,
        caps: KeyCaps,
    ): Key {
        val action = KeyAction.EmojiSlot(slot)
        return when {
            caps.password -> Key(label(R.string.key_emoji_unset), action, x, y, width, style = KeyStyle.DISABLED)
            emoji == null -> Key(
                label(R.string.key_emoji_unset), action, x, y, width,
                longPress = action, style = KeyStyle.PLACEHOLDER,
            )
            else -> Key(emoji, action, x, y, width, longPress = action)
        }
    }

    private fun label(id: Int): String = resources.getString(id)

    private companion object {
        val T9_GROUPS = listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")

        /** Addition, subtraction, multiplication, division. */
        val NUMBER_COLUMN = listOf("+" to "+", "-" to "-", "*" to "*", "/" to "/")

        /** The 9-key page only types Chinese. */
        val T9_COLUMN = listOf("，" to "，", "。" to "。", "？" to "？", "！" to "！")

        val BRACKETS = setOf("()", "[]", "{}", "\"\"", "（）", "［］", "｛｝", "“”", "‘’", "《》")

        /**
         * Five rows. The first four differ by language: sentence marks, pairs, arithmetic, other marks.
         * The last row is the same halfwidth signs on both pages. A fullwidth ＠ does not open a
         * group chat's mention list. `,` and `.` stay on the letter pages (docs/01-1 §5).
         * The Chinese ellipsis is six dots. U+2026 draws as two dots in the phone's font.
         */
        val SHARED_SYMBOLS = listOf("@", "#", "&", "_", "|")

        val ENGLISH_SYMBOLS = listOf(
            "?", "!", ":", ";", "...",
            "()", "[]", "{}", "\"\"", "'",
            "+", "-", "*", "/", "=",
            "<", ">", "%", "~", "$",
        ) + SHARED_SYMBOLS

        val CHINESE_SYMBOLS = listOf(
            "？", "！", "：", "；", "......",
            "（）", "［］", "｛｝", "“”", "‘’",
            "＋", "－", "＊", "／", "＝",
            "《》", "、", "％", "～", "￥",
        ) + SHARED_SYMBOLS
    }
}
