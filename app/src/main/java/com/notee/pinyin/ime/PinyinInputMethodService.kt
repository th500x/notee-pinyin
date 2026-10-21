package com.notee.pinyin.ime

import android.graphics.Paint
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.notee.pinyin.R
import com.notee.pinyin.data.ClipHistory
import com.notee.pinyin.data.ClipboardWatcher
import com.notee.pinyin.data.EmojiUse
import com.notee.pinyin.data.Settings
import com.notee.pinyin.data.Storage
import com.notee.pinyin.data.WordTransfer
import com.notee.pinyin.emoji.EmojiCatalog
import com.notee.pinyin.engine.EngineAssets
import com.notee.pinyin.engine.EnglishLexicon
import com.notee.pinyin.engine.Lexicon
import com.notee.pinyin.engine.ScriptConverter
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.input.Composers
import com.notee.pinyin.keyboard.KeyFeedback
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** The system's handle on the keyboard: builds everything once and forwards the editor lifecycle. */
class PinyinInputMethodService : InputMethodService() {
    private lateinit var storage: Storage
    private lateinit var clipboard: ClipboardWatcher
    private lateinit var controller: KeyboardController

    override fun onCreate() {
        setTheme(R.style.Theme_NoteePinyin_Keyboard)
        super.onCreate()
        storage = Storage(this)
        val user = UserDictionary().apply {
            onRecorded = { code, text -> storage.write { addUserWord(code, text) } }
        }
        val emojiUse = EmojiUse(storage)
        val clips = ClipHistory(storage)
        controller = KeyboardController(
            context = this,
            editor = Editor(this),
            composers = Composers(
                Lexicon.from(mapAsset(EngineAssets.LEXICON)),
                EnglishLexicon.from(mapAsset(EngineAssets.ENGLISH)),
                user,
            ),
            script = ScriptConverter.from(mapAsset(EngineAssets.SCRIPT)),
            emojiCatalog = loadEmoji(),
            settings = Settings(this),
            emojiUse = emojiUse,
            clips = clips,
            feedback = KeyFeedback(this),
            user = user,
            storage = storage,
        )
        WordTransfer.onImported = controller::importWordFile
        WordTransfer.onNotice = controller::notice
        storage.load { saved ->
            saved.userWords.forEach { user.load(it.code, it.text, it.count) }
            emojiUse.restore(saved.emojiUse)
            clips.restore(saved.clips)
        }
        clipboard = ClipboardWatcher(this, controller::onClipboardText)
        if (clipboard.supported) clipboard.start()
    }

    override fun onCreateInputView(): View = controller.createView(layoutInflater)

    /** Fullscreen only while a blessing card is being signed, so that sheet covers the app. */
    override fun onEvaluateFullscreenMode(): Boolean = controller.coversApp()

    override fun onCreateExtractTextView(): View = controller.createCover(layoutInflater)

    /** The app may ask for no extract editor. The card sheet is ours, and it still has to show. */
    override fun onUpdateExtractingVisibility(info: EditorInfo) {
        if (controller.coversApp()) setExtractViewShown(true) else super.onUpdateExtractingVisibility(info)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.startInput(info, restarting)
        if (clipboard.supported) clipboard.check()
    }

    /** Insets are often ready only once the window is actually on screen. */
    override fun onWindowShown() {
        super.onWindowShown()
        controller.syncNavigationPadding()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.finishInput()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0 && isInputViewShown && controller.onBack()) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        WordTransfer.onImported = null
        WordTransfer.onNotice = null
        clipboard.stop()
        storage.close()
        super.onDestroy()
    }

    /** The tables are stored uncompressed, so they map straight out of the APK without a copy. */
    private fun mapAsset(name: String): ByteBuffer =
        assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
            }
        }

    /** Emoji this device cannot draw are left out rather than shown as boxes. */
    private fun loadEmoji(): EmojiCatalog {
        val text = assets.open(EngineAssets.EMOJI).bufferedReader().use { it.readText() }
        val paint = Paint()
        return EmojiCatalog.parse(text).filter(paint::hasGlyph)
    }
}
