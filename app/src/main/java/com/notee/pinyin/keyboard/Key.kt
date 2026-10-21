package com.notee.pinyin.keyboard

/** What a key does when tapped or held. */
sealed interface KeyAction {
    /** 26-key letter, always lowercase here. */
    data class Letter(val letter: Char) : KeyAction

    /** 9-key letter group, by its digit. */
    data class T9(val digit: Char) : KeyAction

    /** Text that goes straight to the editor: digits, punctuation, symbols. */
    data class Commit(val text: String) : KeyAction

    /** `()` `[]` `{}`: both halves, cursor left between them. */
    data class Brackets(val open: String, val close: String) : KeyAction

    /** `'` held on `z`: a syllable boundary while composing, the apostrophe otherwise. */
    data object Separator : KeyAction

    data object Delete : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    data object ToggleLanguage : KeyAction
    /** Drops the 9-key composition. Nothing already committed to the editor is touched. */
    data object Clear : KeyAction
    data object Numbers : KeyAction
    data object Symbols : KeyAction
    data object Back : KeyAction

    data class SymbolLanguage(val language: Language) : KeyAction

    data class EmojiSlot(val slot: Int) : KeyAction
}

enum class KeyStyle {
    NORMAL,
    FUNCTION,
    DELETE,

    /** The selected half of a two-way choice, e.g. 中文 / 英文 on the symbol page. */
    ACTIVE,

    /** Placeholder text, e.g. an emoji key with nothing pinned yet. */
    PLACEHOLDER,

    /** Shown but does nothing here, e.g. 中/英 in a password field. */
    DISABLED,
}

/**
 * One key on a [KeyGrid], in grid units. [hint] is the small corner text that [longPress] commits.
 * A [repeats] key acts on press and again while held.
 */
class Key(
    val label: String,
    val action: KeyAction,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float = 1f,
    val hint: String? = null,
    val longPress: KeyAction? = null,
    val style: KeyStyle = KeyStyle.NORMAL,
    val repeats: Boolean = false,
)

/** A page of keys on a [columns] × [rows] unit grid; the view stretches units to its size. */
class KeyGrid(val columns: Float, val rows: Float, val keys: List<Key>)
