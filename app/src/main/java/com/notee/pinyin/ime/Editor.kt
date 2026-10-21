package com.notee.pinyin.ime

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo

/** The text field on the other end: what kind it is, and the few ways we change it. */
class Editor(private val service: InputMethodService) {
    var info: EditorInfo = EditorInfo()

    private val inputClass: Int get() = info.inputType and InputType.TYPE_MASK_CLASS
    private val variation: Int get() = info.inputType and InputType.TYPE_MASK_VARIATION

    /** Any password field, visible and web ones included. */
    val isPassword: Boolean
        get() = when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation in TEXT_PASSWORDS
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }

    /** Numbers, phone numbers, dates: open on the number page. */
    val isNumeric: Boolean
        get() = inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME

    /** The editor action enter performs, or null when enter is a line break. */
    val enterAction: Int?
        get() {
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
            return (info.imeOptions and EditorInfo.IME_MASK_ACTION).takeIf { it in ACTIONS }
        }

    fun commit(text: CharSequence) {
        service.currentInputConnection?.commitText(text, 1)
    }

    /**
     * Sends a jpeg when this field says it accepts one. False when it does not,
     * or when the field refuses the picture, so the caller can send text instead.
     */
    fun acceptsImage(): Boolean {
        val types = info.contentMimeTypes ?: return false
        return types.any { it.equals("image/jpeg", ignoreCase = true) || it.equals("image/*", ignoreCase = true) }
    }

    fun commitImage(uri: Uri, label: String): Boolean {
        if (!acceptsImage()) return false
        val connection = service.currentInputConnection ?: return false
        val content = InputContentInfo(uri, ClipDescription(label, arrayOf("image/jpeg")))
        return try {
            connection.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Both halves, with the cursor between them. */
    fun commitBrackets(open: String, close: String) {
        val connection = service.currentInputConnection ?: return
        connection.beginBatchEdit()
        connection.commitText(open, 1)
        connection.commitText(close, 0)
        connection.endBatchEdit()
    }

    /** A real delete key event, so editors handle selections and emoji themselves. */
    fun deleteBackward() = service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)

    fun enter() {
        val action = enterAction
        if (action != null) {
            service.currentInputConnection?.performEditorAction(action)
        } else {
            service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    fun textBeforeCursor(length: Int): CharSequence? = service.currentInputConnection?.getTextBeforeCursor(length, 0)

    private companion object {
        val TEXT_PASSWORDS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )

        val ACTIONS = setOf(
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            EditorInfo.IME_ACTION_DONE,
        )
    }
}
