package com.notee.pinyin.data

import android.content.Context
import com.notee.pinyin.keyboard.Language
import com.notee.pinyin.keyboard.Layout
import com.notee.pinyin.keyboard.Script

/** Keyboard choices kept on this device. Defaults per docs/01-1 §1: 简体, 26 键, 中文, key sound off. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var script: Script
        get() = enum(KEY_SCRIPT, Script.SIMPLIFIED)
        set(value) = putEnum(KEY_SCRIPT, value)

    var layout: Layout
        get() = enum(KEY_LAYOUT, Layout.QWERTY)
        set(value) = putEnum(KEY_LAYOUT, value)

    var language: Language
        get() = enum(KEY_LANGUAGE, Language.CHINESE)
        set(value) = putEnum(KEY_LANGUAGE, value)

    /**
     * Which half of the symbol page is showing. Opening 符 sets this from the current
     * input language; the 中文 / 英文 keys on that page can still change it for this visit.
     */
    var symbolLanguage: Language
        get() = enum(KEY_SYMBOL_LANGUAGE, Language.CHINESE)
        set(value) = putEnum(KEY_SYMBOL_LANGUAGE, value)

    var keySound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    /**
     * While on, every festival is listed and a tap sends the blessing card even when today
     * is not that festival's own day. The tools panel no longer has a switch for this.
     * Off unless a previous build stored it on. The behavior stays so that value still works.
     */
    var festivalTest: Boolean
        get() = prefs.getBoolean(KEY_FESTIVAL_TEST, false)
        set(value) = prefs.edit().putBoolean(KEY_FESTIVAL_TEST, value).apply()

    /**
     * The emoji pinned on a 9-key slot, or null. Slot 1 is the right column.
     * Slot 0 was the top-left pin; that key is now 「极」 and does not read this.
     */
    fun emojiSlot(slot: Int): String? = prefs.getString(emojiSlotKey(slot), null)

    fun setEmojiSlot(slot: Int, emoji: String) = prefs.edit().putString(emojiSlotKey(slot), emoji).apply()

    private fun emojiSlotKey(slot: Int) = "emoji_slot_${slot + 1}"

    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T {
        val name = prefs.getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    private fun putEnum(key: String, value: Enum<*>) = prefs.edit().putString(key, value.name).apply()

    companion object {
        const val EMOJI_SLOTS = 2

        private const val FILE = "settings"
        private const val KEY_SCRIPT = "script"
        private const val KEY_LAYOUT = "layout"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SYMBOL_LANGUAGE = "symbol_language"
        private const val KEY_SOUND = "key_sound"
        private const val KEY_FESTIVAL_TEST = "festival_test"
    }
}
