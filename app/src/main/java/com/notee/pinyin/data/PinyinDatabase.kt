package com.notee.pinyin.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One row of `user_word`: a word the user picked, under its `ni hao` code. */
class UserWordRow(val code: String, val text: String, val count: Int)

/** One row of `syllable_pick`: a 9-key reading the user picked for [digits]. */
class SyllablePickRow(val digits: String, val spelling: String, val count: Int)

/**
 * `notee_pinyin.db`. Four small tables, all keyed by their natural key:
 * `user_word(code, text, count)`, `syllable_pick(digits, spelling, count)`,
 * `emoji_use(emoji, count)`, `clip_history(text, copied_at)`.
 */
class PinyinDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $USER_WORD ($CODE TEXT NOT NULL, $TEXT TEXT NOT NULL, $COUNT INTEGER NOT NULL, " +
                "PRIMARY KEY ($CODE, $TEXT)) WITHOUT ROWID",
        )
        db.execSQL("CREATE TABLE $EMOJI_USE ($EMOJI TEXT NOT NULL PRIMARY KEY, $COUNT INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE TABLE $CLIP_HISTORY ($TEXT TEXT NOT NULL PRIMARY KEY, $COPIED_AT INTEGER NOT NULL) WITHOUT ROWID")
        createSyllablePick(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createSyllablePick(db)
    }

    private fun createSyllablePick(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $SYLLABLE_PICK ($DIGITS TEXT NOT NULL, $SPELLING TEXT NOT NULL, " +
                "$COUNT INTEGER NOT NULL, PRIMARY KEY ($DIGITS, $SPELLING)) WITHOUT ROWID",
        )
    }

    fun userWords(): List<UserWordRow> =
        readableDatabase.query(USER_WORD, arrayOf(CODE, TEXT, COUNT), null, null, null, null, null).use { c ->
            List(c.count) {
                c.moveToNext()
                UserWordRow(c.getString(0), c.getString(1), c.getInt(2))
            }
        }

    fun addUserWord(code: String, text: String) = addOne(USER_WORD, arrayOf(CODE, TEXT), arrayOf(code, text))

    fun deleteUserWord(code: String, text: String) {
        writableDatabase.delete(USER_WORD, "$CODE = ? AND $TEXT = ?", arrayOf(code, text))
    }

    fun syllablePicks(): List<SyllablePickRow> =
        readableDatabase.query(SYLLABLE_PICK, arrayOf(DIGITS, SPELLING, COUNT), null, null, null, null, null).use { c ->
            List(c.count) {
                c.moveToNext()
                SyllablePickRow(c.getString(0), c.getString(1), c.getInt(2))
            }
        }

    fun addSyllablePick(digits: String, spelling: String) = addOne(SYLLABLE_PICK, arrayOf(DIGITS, SPELLING), arrayOf(digits, spelling))

    /** Sets the count to the larger of the stored value and [count]. Does not add one. */
    fun raiseSyllablePick(digits: String, spelling: String, count: Int) {
        if (count <= 0) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR IGNORE INTO $SYLLABLE_PICK ($DIGITS, $SPELLING, $COUNT) VALUES (?, ?, 0)",
                arrayOf<Any>(digits, spelling),
            )
            db.execSQL(
                "UPDATE $SYLLABLE_PICK SET $COUNT = MAX($COUNT, ?) WHERE $DIGITS = ? AND $SPELLING = ?",
                arrayOf<Any>(count, digits, spelling),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Sets the count to the larger of the stored value and [count]. Does not add one. */
    fun raiseUserWord(code: String, text: String, count: Int) {
        if (count <= 0) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR IGNORE INTO $USER_WORD ($CODE, $TEXT, $COUNT) VALUES (?, ?, 0)",
                arrayOf<Any>(code, text),
            )
            db.execSQL(
                "UPDATE $USER_WORD SET $COUNT = MAX($COUNT, ?) WHERE $CODE = ? AND $TEXT = ?",
                arrayOf<Any>(count, code, text),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun emojiUse(): Map<String, Int> =
        readableDatabase.query(EMOJI_USE, arrayOf(EMOJI, COUNT), null, null, null, null, null).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getString(0), c.getInt(1))
            }
        }

    fun addEmojiUse(emoji: String) = addOne(EMOJI_USE, arrayOf(EMOJI), arrayOf(emoji))

    /** count + 1 for the row with these keys, starting from 0. No UPSERT: minSdk 26 ships SQLite 3.18. */
    private fun addOne(table: String, keys: Array<String>, values: Array<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val columns = keys.joinToString(", ")
            val marks = keys.joinToString(", ") { "?" }
            db.execSQL("INSERT OR IGNORE INTO $table ($columns, $COUNT) VALUES ($marks, 0)", values)
            db.execSQL("UPDATE $table SET $COUNT = $COUNT + 1 WHERE ${keys.joinToString(" AND ") { "$it = ?" }}", values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Newest first. */
    fun clips(): List<String> =
        readableDatabase.query(CLIP_HISTORY, arrayOf(TEXT), null, null, null, null, "$COPIED_AT DESC").use { c ->
            List(c.count) {
                c.moveToNext()
                c.getString(0)
            }
        }

    /** Replaces the whole history with [texts], newest first. */
    fun putClips(texts: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(CLIP_HISTORY, null, null)
            val now = System.currentTimeMillis()
            texts.forEachIndexed { i, text ->
                db.insert(
                    CLIP_HISTORY, null,
                    ContentValues().apply {
                        put(TEXT, text)
                        put(COPIED_AT, now - i)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val NAME = "notee_pinyin.db"
        const val VERSION = 2

        const val USER_WORD = "user_word"
        const val SYLLABLE_PICK = "syllable_pick"
        const val EMOJI_USE = "emoji_use"
        const val CLIP_HISTORY = "clip_history"

        const val CODE = "code"
        const val TEXT = "text"
        const val COUNT = "count"
        const val DIGITS = "digits"
        const val SPELLING = "spelling"
        const val EMOJI = "emoji"
        const val COPIED_AT = "copied_at"
    }
}
