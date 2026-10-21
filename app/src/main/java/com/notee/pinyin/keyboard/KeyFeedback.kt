package com.notee.pinyin.keyboard

import android.content.Context
import android.media.AudioManager

/**
 * 工具 (docs/01-1 §1): the system key click, only for key caps and only when switched on.
 * [muted] silences it in password fields. There is no vibration: phone makers drop a third-party
 * keyboard's buzz often enough that a switch for it would lie.
 */
class KeyFeedback(context: Context) {
    var sound = false
    var muted = false

    private val audio = context.getSystemService(AudioManager::class.java)

    fun keyDown(action: KeyAction) {
        if (muted || !sound) return
        if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            audio.playSoundEffect(effectOf(action), SYSTEM_VOLUME)
        }
    }

    private fun effectOf(action: KeyAction): Int = when (action) {
        KeyAction.Delete -> AudioManager.FX_KEYPRESS_DELETE
        KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
        KeyAction.Enter -> AudioManager.FX_KEYPRESS_RETURN
        else -> AudioManager.FX_KEYPRESS_STANDARD
    }

    private companion object {
        /** Negative means the system's own key-click volume. */
        const val SYSTEM_VOLUME = -1f
    }
}
