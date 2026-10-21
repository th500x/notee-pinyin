package com.notee.pinyin.data

import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Hears clipboard changes while this is the active keyboard and passes on plain text. Sensitive
 * clips (passwords, codes) and non-text clips are skipped (docs/02-0 §4).
 */
class ClipboardWatcher(context: Context, private val onText: (String) -> Unit) : ClipboardManager.OnPrimaryClipChangedListener {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    /** Sensitive clips are only marked from Android 13. Older releases do not keep a history. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun start() {
        clipboard?.addPrimaryClipChangedListener(this)
    }

    fun stop() {
        clipboard?.removePrimaryClipChangedListener(this)
    }

    override fun onPrimaryClipChanged() = check()

    /** Reads the current clip. Also run when the keyboard opens, in case a change went unheard. */
    fun check() {
        readText()?.let(onText)
    }

    private fun readText(): String? {
        val clip = try {
            clipboard?.primaryClip
        } catch (_: SecurityException) {
            null
        } ?: return null
        val description = clip.description
        if (description.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return null
        if (!description.hasMimeType("text/*") || clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        /** `ClipDescription.EXTRA_IS_SENSITIVE` from API 33; apps on older systems set the same key. */
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
