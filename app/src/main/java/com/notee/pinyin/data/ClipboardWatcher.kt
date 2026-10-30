package com.notee.pinyin.data

import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Reads the current plain-text clip when the clipboard page opens (docs/02-0 §4).
 *
 * The service is fetched only inside [check], and only the clipboard button calls [check].
 * Typing never comes here: there is no listener, and this class never writes the clipboard.
 * Sensitive clips and non-text clips are skipped. A paste toast during typing is the
 * system's reaction to [android.view.inputmethod.InputConnection.commitText], which
 * [com.notee.pinyin.ime.Editor] does not use for an ordinary commit.
 */
class ClipboardWatcher(private val context: Context, private val onText: (String) -> Unit) {

    /** Sensitive clips are only marked from Android 13. Older releases do not keep a history. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Reads the current clip. Only called when the user opens the clipboard page. */
    fun check() {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = try {
            clipboard.primaryClip
        } catch (_: SecurityException) {
            null
        } ?: return
        val description = clip.description
        if (description.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return
        if (!description.hasMimeType("text/*") || clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() } ?: return
        onText(text)
    }

    private companion object {
        /** `ClipDescription.EXTRA_IS_SENSITIVE` from API 33; apps on older systems set the same key. */
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
