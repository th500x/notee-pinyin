package com.notee.pinyin.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The database behind one keyboard session. Every read and write runs in order on one IO thread
 * (`Dispatchers.IO.limitedParallelism(1)`), so a failed write never stalls or crashes typing.
 */
class Storage(context: Context) {

    class Snapshot(val userWords: List<UserWordRow>, val emojiUse: Map<String, Int>, val clips: List<String>)

    private val database = PinyinDatabase(context.applicationContext)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1) +
            CoroutineExceptionHandler { _, error -> Log.w(TAG, "storage failed", error) },
    )

    /** Reads everything once and hands it over on the main thread. */
    fun load(onLoaded: (Snapshot) -> Unit) {
        scope.launch {
            val snapshot = Snapshot(database.userWords(), database.emojiUse(), database.clips())
            withContext(Dispatchers.Main) { onLoaded(snapshot) }
        }
    }

    fun write(block: PinyinDatabase.() -> Unit) {
        scope.launch { database.block() }
    }

    /** Closes after the writes already queued. */
    fun close() {
        scope.launch { database.close() }
    }

    private companion object {
        const val TAG = "NoteePinyin"
    }
}
