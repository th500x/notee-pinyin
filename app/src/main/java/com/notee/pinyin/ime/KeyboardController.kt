package com.notee.pinyin.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.notee.pinyin.R
import com.notee.pinyin.festival.BlessingCard
import com.notee.pinyin.festival.Festival
import com.notee.pinyin.festival.Festivals
import com.notee.pinyin.festival.Signature
import com.notee.pinyin.data.ClipHistory
import com.notee.pinyin.data.EmojiUse
import com.notee.pinyin.data.Settings
import com.notee.pinyin.data.Storage
import com.notee.pinyin.data.WordFileActivity
import com.notee.pinyin.data.WordPlaces
import com.notee.pinyin.data.WordTransfer
import com.notee.pinyin.emoji.EmojiCatalog
import com.notee.pinyin.emoji.EmojiTab
import kotlin.random.Random
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.engine.WordFile
import com.notee.pinyin.engine.CandidateItem
import com.notee.pinyin.engine.CandidateList
import com.notee.pinyin.engine.ScriptConverter
import com.notee.pinyin.input.Composer
import com.notee.pinyin.input.Composers
import com.notee.pinyin.input.EmailDomains
import com.notee.pinyin.input.EnglishSpacing
import com.notee.pinyin.input.Preedit
import com.notee.pinyin.keyboard.ButtonRowView
import com.notee.pinyin.keyboard.CandidateGridView
import com.notee.pinyin.keyboard.CandidateStripView
import com.notee.pinyin.keyboard.ClipboardPanelView
import com.notee.pinyin.keyboard.EmojiGridView
import com.notee.pinyin.keyboard.FestivalBurstView
import com.notee.pinyin.keyboard.Key
import com.notee.pinyin.keyboard.KeyAction
import com.notee.pinyin.keyboard.KeyCaps
import com.notee.pinyin.keyboard.KeyFeedback
import com.notee.pinyin.keyboard.KeyGrid
import com.notee.pinyin.keyboard.KeyStyle
import com.notee.pinyin.keyboard.KeyboardLayouts
import com.notee.pinyin.keyboard.KeyboardView
import com.notee.pinyin.keyboard.Language
import com.notee.pinyin.keyboard.Layout
import com.notee.pinyin.keyboard.Page
import com.notee.pinyin.keyboard.Panel
import com.notee.pinyin.keyboard.Script
import com.notee.pinyin.math.Answer
import com.notee.pinyin.math.Arithmetic
import java.time.LocalDate

/**
 * The keyboard's state and every rule about it: which page and panel show, what each key does,
 * and what reaches the editor. Views only draw what [render] gives them and report touches back.
 */
class KeyboardController(
    private val context: Context,
    private val editor: Editor,
    private val composers: Composers,
    private val script: ScriptConverter,
    private val emojiCatalog: EmojiCatalog,
    private val settings: Settings,
    private val emojiUse: EmojiUse,
    private val clips: ClipHistory,
    private val feedback: KeyFeedback,
    private val user: UserDictionary,
    private val storage: Storage,
) : KeyboardView.Listener, CandidateStripView.Listener, CandidateGridView.Listener {

    /** Reads the system clipboard. Set by the service; only the clipboard button runs it. */
    var refreshClipboard: () -> Unit = {}

    private enum class ToolbarButton { EFFECTS, SCRIPT, LAYOUT, CLIPBOARD, EMOJI }

    private class Views(val root: View) {
        val toolbar: ButtonRowView = root.findViewById(R.id.toolbar)
        val strip: CandidateStripView = root.findViewById(R.id.candidate_strip)
        val emojiTabs: ButtonRowView = root.findViewById(R.id.emoji_tabs)
        val keys: KeyboardView = root.findViewById(R.id.keys)
        val grid: CandidateGridView = root.findViewById(R.id.candidate_grid)
        val effectsPanel: View = root.findViewById(R.id.effects_panel)
        val toolSound: ButtonRowView = root.findViewById(R.id.tool_sound)
        val toolFestivalTest: ButtonRowView = root.findViewById(R.id.tool_festival_test)
        val wordActions: ButtonRowView = root.findViewById(R.id.word_actions)
        val wordPlaces: ButtonRowView = root.findViewById(R.id.word_places)
        val festivalRows: LinearLayout = root.findViewById(R.id.festival_rows)
        val burst: FestivalBurstView = root.findViewById(R.id.festival_burst)
        val clipboard: ClipboardPanelView = root.findViewById(R.id.clipboard_panel)
        val emojiPanel: ScrollView = root.findViewById(R.id.emoji_panel)
        val emojiGrid: EmojiGridView = root.findViewById(R.id.emoji_grid)
    }

    /** The sheet that covers the app while a card is addressed. Not an editor, so the IME target stays put. */
    private class Cover(val root: View) {
        val name: TextView = root.findViewById(R.id.cover_name)
        val wish: TextView = root.findViewById(R.id.cover_wish)
        val notice: TextView = root.findViewById(R.id.cover_notice)
        val ok: TextView = root.findViewById(R.id.cover_ok)
        val hidden: View = root.findViewById(android.R.id.inputExtractEditText)
    }

    private enum class CardSlot { NAME, WISH }

    private data class PageKey(val layout: Layout, val page: Page, val caps: KeyCaps)

    private val layouts = KeyboardLayouts(context.resources)
    private var views: Views? = null

    private var page = Page.LETTERS
    private var pageBeforeSymbols = Page.LETTERS
    private var panel = Panel.KEYS

    /** ⇧ is a lock: letters stay capital until it is tapped again (docs/01-1 §3). */
    private var shifted = false
    private var password = false

    /** False when the field asked the keyboard not to learn, such as private browsing. */
    private var learn = true

    /** The last commit was digits or operators, so an expression may end at the cursor. */
    private var arithmeticArmed = false

    /** Our own last commit; copying exactly that is not kept in the history (docs/02-0 §4). */
    private var lastCommitted: String? = null

    /** Unused next-word row. A commit leaves this empty so the toolbar stays (docs/01-0 §6). */
    private var association: List<String> = emptyList()

    /** Domains for an email field, after a single `@` at the cursor (docs/01-0 §6). */
    private var emailOffers: List<EmailDomains.Offer> = emptyList()

    /** Cursor after a commit. A tap elsewhere dismisses a leftover association row. */
    private var associationCursor = -1
    private var acceptNextSelection = false

    /** 0 is 常用, then [EmojiTab] in order. */
    private var emojiTab = 0

    /** The last face the 「极」 key sent, so the next tap picks a different one. */
    private var lastLogoFace: String? = null

    /** The 9-key slot waiting for an emoji, or null when emoji are sent. */
    private var emojiTarget: Int? = null

    /** Festivals whose window contains [festivalDay]. Empty outside a window, and the toolbar has no bulb. */
    private var activeFestivals: List<Festival> = emptyList()

    /** The local day [activeFestivals] was resolved for. Date text uses this, not a second clock read. */
    private var festivalDay: LocalDate = LocalDate.now()

    /** Which word-file action is waiting for 本地目录 or 云端硬盘. Null hides that row. */
    private var wordSide: WordSide? = null

    /** Rows already built for [activeFestivals], so a redraw does not rebuild them. */
    private var shownFestivals: List<Festival> = emptyList()

    /** Language those rows were named in. A locale change rebuilds them. */
    private var shownFestivalLanguage: String = ""

    /** Which of [shownFestivals] send a card. The right-hand cell follows this. */
    private var shownCardRows: List<Boolean> = emptyList()

    /** Set while the card sheet is open. Keys type into the active line, not the editor. */
    private var signing: Festival? = null

    private var signature: String = ""

    private var wish: String = ""

    /** Cursor in each line, as an index into that line's text. */
    private var nameAt: Int = 0

    private var wishAt: Int = 0

    private var cursor: Int
        get() = if (slot == CardSlot.NAME) nameAt else wishAt
        set(value) {
            val snapped = Signature.boundary(cardText(), value)
            if (slot == CardSlot.NAME) nameAt = snapped else wishAt = snapped
        }

    private var slot: CardSlot = CardSlot.NAME

    /** Shown under the two lines when the active one will not take another character. */
    private var limitNotice: Int? = null

    private var cover: Cover? = null

    private var caretOn: Boolean = true

    private val caretTick: Runnable = object : Runnable {
        override fun run() {
            val root = cover?.root ?: return
            if (signing == null || !root.isAttachedToWindow) return
            caretOn = !caretOn
            paintCover()
            root.postDelayed(this, 530)
        }
    }

    private enum class WordSide { EXPORT, IMPORT }

    private var pageKey: PageKey? = null
    private var keyGrid: KeyGrid? = null
    private var labelSource: CandidateList? = null
    private var labelScript: Script? = null
    private var labels: List<String?> = emptyList()
    private var learnedMarks: List<Boolean> = emptyList()

    init {
        feedback.sound = settings.keySound
    }

    private val language: Language get() = if (password) Language.ENGLISH else settings.language

    /** The field declared an email address. Letters commit directly; the saved language and layout stay. */
    private val emailField: Boolean get() = editor.isEmail && !password

    /** English and an email field stay on the 26-key page. Password uses that page too. */
    private val layout: Layout
        get() = if (language == Language.ENGLISH || emailField) Layout.QWERTY else settings.layout

    /** Password is checked before language: it is English, and it does not compose a word. */
    private val typingEnglish: Boolean get() = !password && language == Language.ENGLISH

    /** Open pinyin, or the English word still being typed. */
    private val inputComposing: Boolean
        get() = if (typingEnglish) composers.english.isComposing else composer.isComposing

    /** English only takes the candidate row while a longer word is still offered. */
    private val showingComposition: Boolean
        get() = if (typingEnglish) composers.english.completions().isNotEmpty() else composer.isComposing

    private val composer: Composer get() = if (layout == Layout.T9) composers.t9 else composers.qwerty

    /** The toolbar under the header keeps clear of the preedit line. The arrow column is reserved by the row itself. */
    private val headerTop: Int get() = context.resources.getDimensionPixelSize(R.dimen.preedit_height)

    /** Open pinyin belongs to one layout's keys, so 布局 waits until it is committed or cleared. */
    private val canSwitchLayout: Boolean
        get() = language == Language.CHINESE && !emailField && !composer.isComposing

    /** Android 13 marks sensitive clips. Older releases keep the button, and it explains itself. */
    private val clipsSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** History stays, but this field does not open the page: a password, or a field that asked not to learn. */
    private val clipboardPageAllowed: Boolean get() = clipsSupported && learn && !password

    // ---- lifecycle ----

    fun createView(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.input_view, null)
        root.setOnApplyWindowInsetsListener { view, insets ->
            applyNavigationPadding(view, insets)
            insets
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.requestApplyInsets()
                view.post { applyNavigationPadding(view, view.rootWindowInsets) }
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
        if (signing != null) clearCompositions()
        signing = null
        signature = ""
        wish = ""
        nameAt = 0
        wishAt = 0
        slot = CardSlot.NAME
        limitNotice = null
        stopCaret()
        val views = Views(root)
        views.keys.listener = this
        views.strip.listener = this
        views.grid.listener = this
        views.toolbar.showsEnd = true
        views.toolbar.onEnd = { hideKeyboard() }
        views.toolbar.onTap = { onToolbar(ToolbarButton.entries[it]) }
        views.toolSound.onTap = ::onSound
        views.toolFestivalTest.onTap = ::onFestivalTest
        views.wordActions.onTap = ::onWordAction
        views.wordPlaces.onTap = ::onWordPlace
        val toolAnchor = text(R.string.effects_sound)
        views.toolSound.alignToLabel = toolAnchor
        views.toolFestivalTest.alignToLabel = toolAnchor
        views.wordActions.alignToLabel = toolAnchor
        views.wordPlaces.alignToLabel = toolAnchor
        views.emojiTabs.onTap = ::onEmojiTab
        views.emojiTabs.onPress = { if (it == EMOJI_DELETE) feedback.keyDown(KeyAction.Delete) }
        views.emojiGrid.onEmoji = ::onEmoji
        views.clipboard.onPick = ::onClip
        this.views = views
        // A rebuilt view (light/dark switch) starts on the keys; the composition stays.
        panel = Panel.KEYS
        wordSide = null
        pageKey = null
        render()
        syncCover()
        return root
    }

    fun startInput(info: EditorInfo, restarting: Boolean) {
        editor.info = info
        password = editor.isPassword
        learn = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        composers.learning = learn
        feedback.muted = password
        if (!clipboardPageAllowed && panel == Panel.CLIPBOARD) panel = Panel.KEYS
        if (!restarting) {
            cancelSignature()
            clearCompositions()
            panel = Panel.KEYS
            page = if (editor.isNumeric) Page.NUMBERS else Page.LETTERS
            shifted = false
            arithmeticArmed = false
            emojiTarget = null
            views?.burst?.stop()
        }
        clearAssociation()
        if (emailField) clearCompositions()
        render()
        syncNavigationPadding()
    }

    /**
     * The navigation-bar inset sometimes arrives after the first layout, or only on the next
     * traversal. Until it does, the keys sit in the gesture area and then jump up. Read it again
     * when the window is shown, and once more on the following frame.
     */
    fun syncNavigationPadding() {
        val root = views?.root ?: return
        root.requestApplyInsets()
        applyNavigationPadding(root, root.rootWindowInsets)
        root.post { applyNavigationPadding(root, root.rootWindowInsets) }
    }

    fun finishInput() {
        cancelSignature()
        clearCompositions()
        panel = Panel.KEYS
        emojiTarget = null
        clearAssociation()
        views?.burst?.stop()
    }

    /**
     * [newStart] and [newEnd] are the editor selection after the change. The update caused by our
     * own commit is kept; a cursor that then moves somewhere else dismisses the association row.
     * An update that reports the same range is ignored while that commit is still outstanding,
     * because some editors send it before the cursor has actually moved. A caret that returns to
     * the start is a send: the field was cleared, so the row goes even if that update arrives
     * before we have accepted our own commit.
     */
    fun onSelectionChanged(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int) {
        val returnedToStart = newStart == 0 && newEnd == 0 && (oldStart != 0 || oldEnd != 0)
        if (returnedToStart && association.isNotEmpty()) {
            acceptNextSelection = false
            clearAssociation()
            if (signing == null && composers.english.isComposing) composers.english.clear()
            render()
            return
        }
        if (acceptNextSelection) {
            val unchanged = newStart == oldStart && newEnd == oldEnd
            if (unchanged) return
            if (newStart == newEnd) {
                acceptNextSelection = false
                associationCursor = newStart
                render()
                return
            }
        }
        if (signing == null && composers.english.isComposing) composers.english.clear()
        if (newStart != associationCursor || newStart != newEnd) clearAssociation()
        render()
    }

    /** System back: closes the grid or a panel. False when there is nothing to close. */
    fun onBack(): Boolean {
        if (signing != null) {
            cancelSignature()
            panel = Panel.EFFECTS
            render()
            return true
        }
        if (panel == Panel.KEYS) return false
        panel = Panel.KEYS
        wordSide = null
        emojiTarget = null
        render()
        return true
    }

    fun onClipboardText(text: String) {
        if (!clipsSupported || password) return
        if (text == lastCommitted) return
        clips.add(text)
        if (panel == Panel.CLIPBOARD) views?.clipboard?.show(clips.items)
    }

    // ---- keys ----

    override fun onKeyDown(key: Key) {
        if (key.style != KeyStyle.DISABLED) feedback.keyDown(key.action)
    }

    override fun onKey(action: KeyAction, longPress: Boolean) {
        when (action) {
            is KeyAction.Letter -> typeLetter(action.letter)
            is KeyAction.T9 -> typeT9(action.digit)
            is KeyAction.Commit -> commitText(action.text)
            is KeyAction.Brackets -> {
                finishComposition()
                clearAssociation()
                if (signing != null) insertCard(action.open + action.close, action.open.length) else {
                    editor.commitBrackets(action.open, action.close)
                    noteCommit(action.open + action.close)
                }
            }
            KeyAction.Separator -> when {
                composers.qwerty.isComposing -> composers.qwerty.separate()
                typingEnglish && composers.english.isComposing -> echoEnglish('\'')
                else -> commitText("'")
            }
            KeyAction.Delete -> {
                clearAssociation()
                deleteBackward()
            }
            KeyAction.Clear -> {
                when {
                    typingEnglish && composers.english.isComposing -> discardEnglishWord()
                    composer.isComposing -> composer.clear()
                    signing != null -> clearCard()
                }
                clearAssociation()
            }
            KeyAction.Space -> when {
                typingEnglish && composers.english.isComposing -> commitEnglishSpace()
                composer.isComposing -> selectFirstIntoSignature()
                else -> commitText(" ")
            }
            KeyAction.Enter -> enter()
            KeyAction.Shift -> shifted = !shifted
            KeyAction.ToggleLanguage -> toggleLanguage()
            KeyAction.Numbers -> page = Page.NUMBERS
            KeyAction.Symbols -> {
                pageBeforeSymbols = page
                settings.symbolLanguage = if (page == Page.NUMBERS) Language.ENGLISH else language
                page = Page.SYMBOLS
            }
            KeyAction.Back -> page = if (page == Page.SYMBOLS) pageBeforeSymbols else Page.LETTERS
            is KeyAction.SymbolLanguage -> settings.symbolLanguage = action.language
            is KeyAction.EmojiSlot -> emojiSlot(action.slot, longPress)
            KeyAction.LogoFace -> typeLogoFace()
        }
        render()
    }

    private fun typeLetter(letter: Char) {
        val typed = if (shifted) letter.uppercaseChar() else letter
        when {
            password || emailField -> commitText(typed.toString())
            language == Language.CHINESE && shifted -> commitText(typed.toString())
            language == Language.CHINESE -> {
                arithmeticArmed = false
                clearAssociation()
                composers.qwerty.type(letter)
            }
            else -> echoEnglish(typed)
        }
    }

    /** The letter goes into the editor immediately. Candidates are only the letters still missing. */
    private fun echoEnglish(char: Char) {
        arithmeticArmed = false
        clearAssociation()
        if (signing != null && !acceptCard(char.toString())) return
        val before = composers.english.raw().length
        composers.english.type(char)
        if (composers.english.raw().length == before) return
        val added = composers.english.raw().substring(before)
        if (signing != null) {
            insertCard(added)
            return
        }
        acceptNextSelection = true
        editor.commit(added)
        noteCommit(added)
    }

    private fun typeT9(digit: Char) {
        arithmeticArmed = false
        clearAssociation()
        composers.t9.press(digit)
    }

    private fun deleteBackward() {
        when {
            typingEnglish && composers.english.isComposing -> {
                composers.english.deleteBackward()
                if (signing != null) dropCard() else {
                    acceptNextSelection = true
                    editor.deleteBackward()
                }
            }
            composer.isComposing -> composer.deleteBackward()
            signing != null -> dropCard()
            else -> editor.deleteBackward()
        }
    }

    /** Enter with a composition open sends the letters as typed; otherwise the editor's own action. */
    private fun enter() {
        if (signing != null) {
            when {
                typingEnglish && composers.english.isComposing -> endEnglishWord()
                composer.isComposing -> commitRaw()
                else -> confirmSignature()
            }
            return
        }
        when {
            typingEnglish && composers.english.isComposing -> {
                endEnglishWord()
                editor.enter()
                arithmeticArmed = false
            }
            composer.isComposing -> commitRaw()
            else -> {
                clearAssociation()
                editor.enter()
                arithmeticArmed = false
            }
        }
    }

    /** Leaving a composition sends its letters, like enter. A next-word row that is not composing is dismissed. */
    private fun toggleLanguage() {
        if (password) return
        when {
            composer.isComposing -> commitRaw()
            composers.english.isComposing -> endEnglishWord()
            association.isNotEmpty() -> clearAssociation()
        }
        shifted = false
        settings.language = if (settings.language == Language.CHINESE) Language.ENGLISH else Language.CHINESE
    }

    private fun emojiSlot(slot: Int, longPress: Boolean) {
        if (password) return
        val pinned = settings.emojiSlot(slot)
        if (pinned != null && !longPress) sendEmoji(pinned) else openEmoji(target = slot)
    }

    /** A round face from 黄脸. This tap is not counted, so 常用 stays as it was. */
    private fun typeLogoFace() {
        if (password) return
        val face = EmojiCatalog.pickLogoFace(emojiCatalog.logoFaces(), lastLogoFace, Random.nextInt()) ?: return
        lastLogoFace = face
        commitText(face)
    }

    // ---- committing ----

    /** Text that bypasses composition. An open composition goes out first, without leaving a next-word row. */
    private fun commitText(text: String) {
        finishComposition()
        clearAssociation()
        place(text)
    }

    /** While a card sheet is open, text stays on the active line. Otherwise it goes to the editor. */
    private fun place(text: String) {
        val join = joinsEnglishMark(text)
        if (signing != null) {
            if (join) {
                val (next, at) = Signature.deleteBefore(cardText(), cursor)
                if (Signature.fits(next, text, cardLimit())) {
                    writeCard(next)
                    cursor = at
                }
            }
            insertCard(text)
            return
        }
        if (join) editor.commitAfterSpace(text) else editor.commit(text)
        noteCommit(text)
    }

    /** English sentence marks replace the one space a word just left behind (docs/01-0 §6). */
    private fun joinsEnglishMark(text: String): Boolean {
        if (!typingEnglish) return false
        return EnglishSpacing.dropSpaceBefore(textBeforeCaret(2), text)
    }

    private fun textBeforeCaret(length: Int): CharSequence? {
        if (signing != null) {
            val line = cardText()
            val end = cursor.coerceIn(0, line.length)
            return line.substring((end - length).coerceAtLeast(0), end)
        }
        return editor.textBeforeCursor(length)
    }

    private fun finishComposition() {
        when {
            typingEnglish && composers.english.isComposing -> endEnglishWord()
            composer.isComposing -> commitPinyin(composer.commitAll())
        }
    }

    /** Chosen words, in the script the toolbar shows. Committing does not open a next-word row. */
    private fun commitPinyin(text: String) {
        clearAssociation()
        place(toScript(text))
    }

    private fun commitRaw() {
        clearAssociation()
        val raw = composer.rawText()
        composer.clear()
        place(raw)
    }

    /** The letters are already in the editor. The user's own space does not offer a next word. */
    private fun commitEnglishSpace() {
        val typed = composers.english.raw()
        val spelled = composers.english.placeSpelling()
        composers.english.clear()
        clearAssociation()
        if (spelled != null && replaceEchoed(typed, "$spelled ")) return
        place(" ")
    }

    /** Stop tracking the word. A finished place name typed in lowercase takes its capital. */
    private fun endEnglishWord() {
        val typed = composers.english.raw()
        val spelled = composers.english.placeSpelling()
        composers.english.clear()
        clearAssociation()
        if (spelled != null) replaceEchoed(typed, spelled)
    }

    /**
     * Writes [replacement] over the letters just echoed. False when those letters are not
     * immediately before the cursor, or when a blessing card has no room.
     */
    private fun replaceEchoed(typed: String, replacement: String): Boolean {
        if (typed.isEmpty() || typed == replacement) return false
        acceptNextSelection = true
        if (signing != null) {
            val line = cardText()
            val end = cursor.coerceIn(0, line.length)
            val start = end - typed.length
            if (start < 0 || line.substring(start, end) != typed) return false
            val next = line.substring(0, start) + replacement + line.substring(end)
            if (Signature.units(next) > cardLimit()) return false
            limitNotice = null
            writeCard(next)
            cursor = start + replacement.length
            return true
        }
        if (!editor.replaceBeforeCursor(typed, replacement)) return false
        noteCommit(replacement)
        return true
    }

    /** 清除 drops the word being typed, including the letters already echoed into the editor. */
    private fun discardEnglishWord() {
        val count = composers.english.raw().length
        composers.english.clear()
        repeat(count) {
            if (signing != null) dropCard() else {
                acceptNextSelection = true
                editor.deleteBackward()
            }
        }
    }

    private fun clearAssociation() {
        association = emptyList()
        associationCursor = -1
        acceptNextSelection = false
    }

    private fun noteCommit(text: String) {
        lastCommitted = text
        arithmeticArmed = text.isNotEmpty() && text.all(Arithmetic::isExpressionChar)
    }

    private fun sendEmoji(emoji: String) {
        commitText(emoji)
        if (learn) emojiUse.add(emoji)
    }

    private fun toScript(text: String): String = if (settings.script == Script.TRADITIONAL) script.toTraditional(text) else text

    private fun answer(): Answer? {
        if (!arithmeticArmed || password) return null
        return Arithmetic.answer(editor.textBeforeCursor(Arithmetic.LOOK_BACK) ?: return null)
    }

    // ---- strip, grid ----

    override fun onCandidate(index: Int) {
        when {
            typingEnglish && composers.english.isComposing -> pickEnglish(index)
            composer.isComposing -> pick(index)
            emailOffers.isNotEmpty() -> pickEmail(index)
            association.isNotEmpty() -> pickAssociation(index)
            else -> answer()?.let {
                if (signing == null) {
                    editor.commit(it.appended)
                    noteCommit(it.appended)
                }
            }
        }
        render()
    }

    override fun onSyllableChoice(index: Int) {
        composers.t9.preedit.choices.getOrNull(index)?.let { choseSyllable(it.syllable) }
        render()
    }

    /** Opening the grid closes 特效 or 剪贴板 if one is open (docs/01-1 §2). */
    override fun onToggleGrid() {
        val views = views ?: return
        if (panel == Panel.CANDIDATES) {
            panel = Panel.KEYS
        } else {
            showWordGrid()
            panel = Panel.CANDIDATES
        }
        render()
    }

    override fun onToggleSyllables() {
        val views = views ?: return
        if (panel == Panel.SYLLABLES) {
            panel = Panel.KEYS
        } else {
            views.grid.show(composers.t9.preedit.choices.map { it.spelling })
            panel = Panel.SYLLABLES
        }
        render()
    }

    override fun onEmoji() {
        openEmoji(target = null)
        render()
    }

    override fun onGridCandidate(index: Int) {
        when {
            panel == Panel.SYLLABLES -> pickSyllable(index)
            typingEnglish && composers.english.isComposing -> pickEnglish(index)
            composer.isComposing -> pick(index)
            emailOffers.isNotEmpty() -> pickEmail(index)
            else -> pickAssociation(index)
        }
        render()
    }

    override fun onGridClear() {
        when {
            typingEnglish && composers.english.isComposing -> discardEnglishWord()
            composer.isComposing -> composer.clear()
            else -> clearAssociation()
        }
        panel = Panel.KEYS
        render()
    }

    private fun pickEmail(index: Int) {
        val offer = emailOffers.getOrNull(index) ?: return
        place(offer.missing)
        if (panel == Panel.CANDIDATES) panel = Panel.KEYS
    }

    private fun pickAssociation(index: Int) {
        val suffix = association.getOrNull(index) ?: return
        if (signing != null) {
            place(if (typingEnglish) suffix else toScript(suffix))
            clearAssociation()
            if (panel == Panel.CANDIDATES) panel = Panel.KEYS
            return
        }
        clearAssociation()
        if (typingEnglish) {
            val before = editor.textBeforeCursor(1)
            val lead = if (before != null && before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
            place(lead + suffix)
        } else {
            place(toScript(suffix))
        }
        if (panel == Panel.CANDIDATES) panel = Panel.KEYS
    }

    private fun pickEnglish(index: Int) {
        val selection = composers.english.selection(index) ?: return
        val typed = composers.english.raw()
        val rest = selection.word.substring(typed.length)
        if (signing != null && !acceptCard("$rest ")) return
        composers.english.clear()
        clearAssociation()
        val wrote = selection.replace && replaceEchoed(typed, "${selection.word} ")
        if (!wrote) {
            if (signing != null) place("$rest ") else {
                acceptNextSelection = true
                editor.commit("$rest ")
                noteCommit("$rest ")
            }
        }
        if (panel == Panel.CANDIDATES) panel = Panel.KEYS
    }

    /**
     * Fixes the next reading. The open grid keeps the words it was filled with, so a reading
     * chosen afterwards refills those cells. The strip already does this on every redraw
     * (docs/01-1 §2).
     */
    private fun choseSyllable(syllable: Int): Boolean {
        if (!composers.t9.choose(syllable)) return false
        if (panel == Panel.CANDIDATES || panel == Panel.SYLLABLES) {
            showWordGrid()
            panel = Panel.CANDIDATES
        }
        return true
    }

    private fun pickSyllable(index: Int) {
        val choice = composers.t9.preedit.choices.getOrNull(index)
        if (choice == null || !choseSyllable(choice.syllable)) {
            views?.grid?.show(composers.t9.preedit.choices.map { it.spelling })
        }
    }

    private fun pick(index: Int) {
        val word = composer.candidates.items.getOrNull(index) as? CandidateItem.Word
        if (word == null) {
            showWordGrid()
            return
        }
        if (signing != null && !acceptCard(toScript(word.candidate.text))) return
        composer.select(word.candidate)?.let(::commitPinyin)
        if (panel == Panel.CANDIDATES) panel = Panel.KEYS
    }

    /** Space commits the first candidate, unless that word would make the signature too long. */
    private fun selectFirstIntoSignature() {
        if (signing != null) {
            val preview = toScript(composer.candidates.first?.text ?: composer.rawText())
            if (!acceptCard(preview)) return
        }
        composer.selectFirst()?.let(::commitPinyin)
    }

    private fun candidateLabels(): List<String?> {
        if (typingEnglish) return composers.english.completions()
        val list = composer.candidates
        val current = settings.script
        if (list !== labelSource || current != labelScript) {
            labels = list.items.map { (it as? CandidateItem.Word)?.let { word -> toScript(word.candidate.text) } }
            learnedMarks = list.items.map { item ->
                val word = (item as? CandidateItem.Word)?.candidate ?: return@map false
                user.count(composers.syllables.code(word.syllables), word.text) > 0
            }
            labelSource = list
            labelScript = current
        }
        return labels
    }

    /** Blue chips belong to pinyin candidates the user has stored. English and mail stay plain. */
    private fun wordMarks(): List<Boolean> =
        if (typingEnglish || !composer.isComposing) emptyList() else learnedMarks

    private fun showWordGrid(resetPage: Boolean = true) {
        views?.grid?.show(choiceLabels(), wordMarks(), resetPage)
    }

    override fun onForgetCandidate(index: Int) {
        if (typingEnglish || !composer.isComposing) return
        val word = composer.candidates.items.getOrNull(index) as? CandidateItem.Word ?: return
        val text = word.candidate.text
        val code = composers.syllables.code(word.candidate.syllables)
        if (!user.forget(code, text)) return
        storage.write { deleteUserWord(code, text) }
        toast(R.string.words_forgotten)
        if (panel == Panel.CANDIDATES) showWordGrid(resetPage = false)
        render()
    }

    // ---- toolbar and panels ----

    private fun onToolbar(button: ToolbarButton) {
        if (signing != null && button == ToolbarButton.EFFECTS) return
        when (button) {
            ToolbarButton.EFFECTS -> {
                if (panel == Panel.EFFECTS) panel = Panel.KEYS
                else panel = Panel.EFFECTS
                wordSide = null
            }
            ToolbarButton.SCRIPT -> {
                settings.script = if (settings.script == Script.SIMPLIFIED) Script.TRADITIONAL else Script.SIMPLIFIED
                if (panel == Panel.CANDIDATES) showWordGrid()
            }
            ToolbarButton.LAYOUT -> if (canSwitchLayout) {
                settings.layout = if (settings.layout == Layout.QWERTY) Layout.T9 else Layout.QWERTY
                page = Page.LETTERS
            }
            ToolbarButton.CLIPBOARD -> when {
                !clipsSupported -> Toast.makeText(context, R.string.clipboard_unavailable, Toast.LENGTH_SHORT).show()
                !learn -> Unit
                panel == Panel.CLIPBOARD -> panel = Panel.KEYS
                else -> {
                    refreshClipboard()
                    views?.clipboard?.show(clips.items)
                    panel = Panel.CLIPBOARD
                }
            }
            ToolbarButton.EMOJI -> openEmoji(target = null)
        }
        render()
    }

    private fun onSound(index: Int) {
        if (index != 0) return
        settings.keySound = !settings.keySound
        feedback.sound = settings.keySound
        render()
    }

    private fun onFestivalTest(index: Int) {
        if (index != 0) return
        settings.festivalTest = !settings.festivalTest
        render()
    }

    private fun onWordAction(index: Int) {
        val next = if (index == 0) WordSide.EXPORT else WordSide.IMPORT
        wordSide = if (wordSide == next) null else next
        render()
    }

    private fun onWordPlace(index: Int) {
        val side = wordSide ?: return
        wordSide = null
        render()
        val local = index == 0
        when {
            side == WordSide.EXPORT && local -> exportLocal()
            side == WordSide.IMPORT && local -> WordFileActivity.start(context, WordFileActivity.MODE_IMPORT, null, WordPlaces.localOpen())
            side == WordSide.EXPORT -> exportDrive()
            else -> importDrive()
        }
    }

    private fun exportText(): String = WordFile.encode(user.learnedWords(), user.learnedSyllables())

    private fun exportLocal() {
        val text = exportText()
        val name = WordPlaces.fileName(text(R.string.words_file_stem))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val saved = WordPlaces.saveToDownloads(context, name, text)
            toast(if (saved) R.string.words_saved_downloads else R.string.words_save_failed)
        } else {
            WordFileActivity.start(context, WordFileActivity.MODE_EXPORT, text, WordPlaces.localCreate(name))
        }
    }

    private fun exportDrive() {
        if (!WordPlaces.driveInstalled(context)) {
            toast(R.string.words_drive_missing)
            return
        }
        val send = WordPlaces.driveSend(context, exportText(), WordPlaces.fileName(text(R.string.words_file_stem)))
        if (send == null) {
            toast(R.string.words_drive_sign_in)
            return
        }
        try {
            context.startActivity(send)
            toast(R.string.words_drive_opened)
        } catch (_: android.content.ActivityNotFoundException) {
            toast(R.string.words_drive_sign_in)
        }
    }

    private fun importDrive() {
        if (!WordPlaces.driveInstalled(context)) {
            toast(R.string.words_drive_missing)
            return
        }
        val open = WordPlaces.driveOpen(context)
        if (open == null) {
            toast(R.string.words_drive_sign_in)
            return
        }
        WordFileActivity.start(context, WordFileActivity.MODE_IMPORT, null, open)
    }

    /** A file the user picked. Keeps the larger count and never treats the import as a new pick. */
    fun importWordFile(text: String?) {
        if (text == null) {
            toast(R.string.words_read_failed)
            return
        }
        val file = WordFile.decode(text)
        if (file == null) {
            toast(R.string.words_not_file)
            return
        }
        for (word in file.words) user.raiseTo(word.code, word.text, word.count)
        for (row in file.syllables) user.raiseSyllable(row.digits, row.spelling, row.count)
        val words = file.words
        val syllables = file.syllables
        storage.write {
            for (word in words) raiseUserWord(word.code, word.text, word.count)
            for (row in syllables) raiseSyllablePick(row.digits, row.spelling, row.count)
        }
        Toast.makeText(context, context.getString(R.string.words_imported, words.size + syllables.size), Toast.LENGTH_SHORT).show()
    }

    fun notice(message: Int) = toast(message)

    private fun toast(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun onClip(text: String) {
        commitText(text)
        panel = Panel.KEYS
        render()
    }

    private fun openEmoji(target: Int?) {
        // Letters already in the editor must stop being one English word, or the next letter
        // would continue it on the far side of the emoji.
        if (typingEnglish && composers.english.isComposing) endEnglishWord()
        emojiTarget = target
        panel = Panel.EMOJI
        showEmojiTab(0)
    }

    /** Same as system back while the keys are showing: the window hides, and finishInput clears composition. */
    private fun hideKeyboard() {
        (context as InputMethodService).requestHideSelf(0)
    }

    /** The tab row: 返回, 常用, each [EmojiTab], then ⌫ at [EMOJI_DELETE]. */
    private fun onEmojiTab(index: Int) {
        when (index) {
            0 -> {
                panel = Panel.KEYS
                emojiTarget = null
            }
            EMOJI_DELETE -> deleteBackward()
            else -> showEmojiTab(index - 1)
        }
        render()
    }

    /** The 常用 order is taken when the tab opens, so cells do not move under the finger. */
    private fun showEmojiTab(tab: Int) {
        emojiTab = tab
        val cells = if (tab == 0) emojiCatalog.frequentPage(emojiUse.all) else emojiCatalog.page(EmojiTab.entries[tab - 1])
        views?.let {
            it.emojiGrid.show(cells)
            it.emojiPanel.scrollTo(0, 0)
        }
    }

    private fun onEmoji(emoji: String) {
        val target = emojiTarget
        if (target != null) {
            settings.setEmojiSlot(target, emoji)
            emojiTarget = null
            panel = Panel.KEYS
        } else {
            sendEmoji(emoji)
        }
        render()
    }

    // ---- rendering ----

    private fun render() {
        val views = views ?: return
        festivalDay = LocalDate.now()
        activeFestivals = when {
            password -> emptyList()
            settings.festivalTest -> Festival.entries
            else -> Festivals.activeOn(festivalDay)
        }
        refreshEmailOffers()
        if ((panel == Panel.CANDIDATES || panel == Panel.SYLLABLES) &&
            !showingComposition && association.isEmpty() && emailOffers.isEmpty()
        ) {
            panel = Panel.KEYS
        }
        if (password && panel != Panel.KEYS) panel = Panel.KEYS
        if (panel != Panel.EFFECTS) wordSide = null
        renderStrip(views)
        views.keys.grid = currentKeyGrid()
        views.keys.visibility = visibleIf(panel == Panel.KEYS)
        views.grid.visibility = visibleIf(panel == Panel.CANDIDATES || panel == Panel.SYLLABLES)
        views.effectsPanel.visibility = visibleIf(panel == Panel.EFFECTS)
        views.clipboard.visibility = visibleIf(panel == Panel.CLIPBOARD)
        views.emojiPanel.visibility = visibleIf(panel == Panel.EMOJI)
        if (panel == Panel.EFFECTS) {
            views.toolSound.buttons = soundButtons()
            views.toolFestivalTest.visibility = View.GONE
            views.toolFestivalTest.buttons = festivalTestButtons()
            views.wordActions.buttons = wordActionButtons()
            views.wordPlaces.visibility = visibleIf(wordSide != null)
            if (wordSide != null) views.wordPlaces.buttons = placeButtons()
            showFestivalRows(views)
        } else {
            views.burst.stop()
        }
        paintCover()
    }

    /** One full-width row per festival active today, under the word-file rows. */
    private fun showFestivalRows(views: Views) {
        val language = context.resources.configuration.locales[0].language
        val cardRows = activeFestivals.map(::showsCard)
        if (
            shownFestivals == activeFestivals &&
            shownFestivalLanguage == language &&
            shownCardRows == cardRows &&
            views.festivalRows.childCount == activeFestivals.size
        ) {
            return
        }
        shownFestivals = activeFestivals
        shownFestivalLanguage = language
        shownCardRows = cardRows
        views.festivalRows.removeAllViews()
        val height = context.resources.getDimensionPixelSize(R.dimen.tool_row_height)
        val emojiSize = context.resources.getDimension(R.dimen.text_emoji)
        val nameColor = context.getColor(R.color.key_function)
        val emojiColor = context.getColor(R.color.key_background)
        val sample = if (cardRows.any { it }) BlessingCard.sample(context) else null
        activeFestivals.forEachIndexed { index, festival ->
            val card = cardRows[index] && sample != null
            val row = ButtonRowView(context)
            row.alignToLabel = text(R.string.effects_sound)
            row.buttons = listOf(
                ButtonRowView.Button(
                    festivalName(festival),
                    enabled = false,
                    mark = if (settings.festivalTest) {
                        Festivals.spanText(festival, festivalDay.year)
                    } else {
                        Festivals.dateText(festival, festivalDay)
                    },
                    background = nameColor,
                ),
                if (card) {
                    ButtonRowView.Button("", background = emojiColor, image = sample)
                } else {
                    ButtonRowView.Button(festival.emoji, textSize = emojiSize, background = emojiColor)
                },
            )
            row.onTap = { if (it == 1) onFestival(index) }
            views.festivalRows.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height),
            )
        }
    }

    /** English systems use the English name. Every other language keeps the Simplified label. */
    private fun festivalName(festival: Festival): String = text(
        when (festival) {
            Festival.NEW_YEAR -> R.string.festival_new_year
            Festival.VALENTINE -> R.string.festival_valentine
            Festival.EASTER -> R.string.festival_easter
            Festival.INDEPENDENCE -> R.string.festival_independence
            Festival.HALLOWEEN -> R.string.festival_halloween
            Festival.THANKSGIVING -> R.string.festival_thanksgiving
            Festival.BONFIRE -> R.string.festival_bonfire
            Festival.CHRISTMAS -> R.string.festival_christmas
            Festival.SPRING -> R.string.festival_spring
            Festival.LANTERN -> R.string.festival_lantern
            Festival.DRAGON_BOAT -> R.string.festival_dragon_boat
            Festival.QIXI -> R.string.festival_qixi
            Festival.MID_AUTUMN -> R.string.festival_mid_autumn
            Festival.DOUBLE_NINTH -> R.string.festival_double_ninth
            Festival.MAKHA -> R.string.festival_makha
            Festival.SONGKRAN -> R.string.festival_songkran
            Festival.VISAKHA -> R.string.festival_vesak
            Festival.LOY_KRATHONG -> R.string.festival_loy_krathong
        },
    )

    private fun onFestival(index: Int) {
        val festival = activeFestivals.getOrNull(index) ?: return
        if (showsCard(festival)) openSignature(festival) else {
            commitText(festival.emoji)
            views?.burst?.play(festival.effect)
            render()
        }
    }

    /** The test switch lists every festival and sends a card. Otherwise only a formal day does. */
    private fun sendsCard(festival: Festival): Boolean =
        settings.festivalTest || Festivals.isFormal(festival, festivalDay)

    /** A thumbnail only when this field will take the picture. Otherwise the row stays the emoji. */
    private fun showsCard(festival: Festival): Boolean = sendsCard(festival) && editor.acceptsImage()

    /** True while the card sheet should cover the application, keyboard included. */
    fun coversApp(): Boolean = signing != null

    /** Built once, the first time the sheet opens. The hidden editor is only there for the framework. */
    fun createCover(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.blessing_cover, null)
        val created = Cover(root)
        created.name.setOnTouchListener { view, event -> onCoverTouch(CardSlot.NAME, view as TextView, event) }
        created.wish.setOnTouchListener { view, event -> onCoverTouch(CardSlot.WISH, view as TextView, event) }
        created.ok.setOnClickListener { confirmSignature() }
        created.name.isFocusable = false
        created.wish.isFocusable = false
        created.ok.isFocusable = false
        created.hidden.visibility = View.GONE
        created.hidden.isFocusable = false
        created.hidden.isFocusableInTouchMode = false
        cover = created
        return root
    }

    private fun syncCover() {
        val service = context as InputMethodService
        service.updateFullscreenMode()
        if (signing == null) {
            stopCaret()
            return
        }
        service.window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val frame = cover?.root?.parent as? View
        frame?.setBackgroundColor(Color.TRANSPARENT)
        (frame?.parent as? View)?.background = null
        hideExtractEditor()
        startCaret()
    }

    /** The framework's extract editor mirrors the app. Keep it out of the sheet. */
    private fun hideExtractEditor() {
        val hidden = cover?.hidden ?: return
        hidden.visibility = View.GONE
        hidden.alpha = 0f
        hidden.isFocusable = false
        hidden.isFocusableInTouchMode = false
    }

    private fun startCaret() {
        caretOn = true
        val root = cover?.root ?: return
        root.removeCallbacks(caretTick)
        root.postDelayed(caretTick, 530)
    }

    private fun stopCaret() {
        cover?.root?.removeCallbacks(caretTick)
    }

    private fun paintCover() {
        val sheet = cover ?: return
        if (signing == null) return
        hideExtractEditor()
        paintLine(sheet.name, signature, R.string.blessing_sign_hint, slot == CardSlot.NAME, nameAt)
        paintLine(sheet.wish, wish, R.string.blessing_wish_hint, slot == CardSlot.WISH, wishAt)
        sheet.ok.text = text(R.string.blessing_sign_ok)
        val notice = limitNotice
        if (notice == null) {
            sheet.notice.visibility = View.GONE
        } else {
            sheet.notice.visibility = View.VISIBLE
            sheet.notice.text = text(notice)
        }
    }

    private fun paintLine(view: TextView, value: String, hint: Int, active: Boolean, at: Int) {
        val shown = SpannableStringBuilder()
        if (value.isEmpty()) {
            if (active) shown.append(caret())
            shown.append(text(hint))
        } else {
            val index = at.coerceIn(0, value.length)
            shown.append(value, 0, index)
            if (active) shown.append(caret())
            if (index < value.length) shown.append(value, index, value.length)
        }
        view.text = shown
        view.setTextColor(context.getColor(if (value.isEmpty()) R.color.key_hint else R.color.key_text))
        view.setBackgroundResource(
            if (active) R.drawable.signature_field_active_bg else R.drawable.signature_field_bg,
        )
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
    }

    /** Sits in the text, at the cursor, so a tap can move it. */
    private fun caret(): SpannableStringBuilder {
        val width = (context.resources.displayMetrics.density * 2f).toInt().coerceAtLeast(2)
        val span = CaretSpan(context.getColor(R.color.preedit_text), width, caretOn)
        return SpannableStringBuilder("\u200B").apply {
            setSpan(span, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** A tap chooses the line and puts the cursor where the finger landed. */
    private fun onCoverTouch(next: CardSlot, view: TextView, event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || signing == null) return true
        val value = if (next == CardSlot.NAME) signature else wish
        val caretAt = if (next == CardSlot.NAME) nameAt else wishAt
        val tapped = valueOffset(view, event.x, event.y, value, caretAt, slot == next)
        moveCursor(next, tapped)
        return true
    }

    private fun valueOffset(view: TextView, x: Float, y: Float, value: String, caretAt: Int, active: Boolean): Int {
        if (value.isEmpty() || view.layout == null) return 0
        val raw = view.getOffsetForPosition(x, y)
        if (raw < 0) return value.length
        val index = if (active && raw > caretAt) raw - 1 else raw
        return Signature.boundary(value, index)
    }

    private fun moveCursor(next: CardSlot, tapped: Int) {
        var index = tapped
        if (slot != next) {
            if (!settleComposition()) return
            slot = next
            limitNotice = null
        } else if (composer.isComposing) {
            val old = cursor
            val before = cardText().length
            if (!settleComposition()) return
            if (index >= old) index += cardText().length - before
        } else if (typingEnglish && composers.english.isComposing) {
            endEnglishWord()
        }
        cursor = index
        render()
    }

    /** Commits an open composition into the current line. False when that line will not take it. */
    private fun settleComposition(): Boolean {
        if (!composer.isComposing && !(typingEnglish && composers.english.isComposing)) return true
        if (composer.isComposing) {
            finishComposition()
            if (limitNotice != null) {
                render()
                return false
            }
        } else {
            endEnglishWord()
        }
        return true
    }

    private fun cardText(): String = if (slot == CardSlot.NAME) signature else wish

    private fun cardLimit(): Int = if (slot == CardSlot.NAME) Signature.LIMIT else Signature.WISH_LIMIT

    private fun writeCard(value: String) {
        if (slot == CardSlot.NAME) signature = value else wish = value
    }

    private fun acceptCard(more: String): Boolean {
        if (Signature.fits(cardText(), more, cardLimit())) {
            limitNotice = null
            return true
        }
        limitNotice = if (slot == CardSlot.NAME) R.string.blessing_sign_limit else R.string.blessing_wish_limit
        toast(limitNotice!!)
        return false
    }

    private fun insertCard(text: String, caretInInserted: Int = text.length) {
        if (text.isEmpty() || !acceptCard(text)) return
        val (next, end) = Signature.insert(cardText(), cursor, text)
        writeCard(next)
        cursor = end - text.length + caretInInserted.coerceIn(0, text.length)
    }

    private fun dropCard() {
        limitNotice = null
        val (next, at) = Signature.deleteBefore(cardText(), cursor)
        writeCard(next)
        cursor = at
    }

    private fun clearCard() {
        limitNotice = null
        writeCard("")
        cursor = 0
    }

    private fun openSignature(festival: Festival) {
        finishComposition()
        clearAssociation()
        signing = festival
        signature = ""
        wish = ""
        nameAt = 0
        wishAt = 0
        slot = CardSlot.NAME
        limitNotice = null
        panel = Panel.KEYS
        page = Page.LETTERS
        syncCover()
        render()
    }

    private fun confirmSignature() {
        val festival = signing ?: return
        finishComposition()
        val signed = signature.trim()
        val custom = wish.trim()
        signing = null
        signature = ""
        wish = ""
        nameAt = 0
        wishAt = 0
        slot = CardSlot.NAME
        limitNotice = null
        syncCover()
        if (!sendBlessing(festival, signed, custom)) commitText(festival.emoji)
        views?.burst?.play(festival.effect)
        panel = Panel.EFFECTS
        render()
    }

    /** Back, or leaving for another tool, drops both lines and does not send a card. */
    private fun cancelSignature() {
        if (signing == null) return
        signing = null
        signature = ""
        wish = ""
        nameAt = 0
        wishAt = 0
        slot = CardSlot.NAME
        limitNotice = null
        clearCompositions()
        clearAssociation()
        syncCover()
    }

    /** A formal day sends the card. Anything else, including a refused picture, sends the emoji. */
    private fun sendBlessing(festival: Festival, signature: String, wishLine: String): Boolean {
        if (!sendsCard(festival) || !editor.acceptsImage()) return false
        clearAssociation()
        val english = context.resources.configuration.locales[0].language == "en"
        val from = if (signature.isEmpty()) "" else context.getString(R.string.blessing_from, signature)
        return BlessingCard.send(
            context,
            editor,
            festival,
            festivalName(festival),
            festivalDay,
            from,
            wishLine,
            english,
        )
    }

    /**
     * One row. Over the keys it carries the open composition and its candidates, an association row, or an
     * arithmetic answer, or else the toolbar. Over a panel it is the toolbar, under the preedit and
     * beside ▼/▲ while composing or associating (docs/01-1 §1–2). The emoji page has its tabs
     * there; password fields show nothing.
     */
    private fun renderStrip(views: Views) {
        val composing = showingComposition
        val emailOnStrip = !composing && emailOffers.isNotEmpty() &&
            (panel == Panel.KEYS || panel == Panel.CANDIDATES)
        val associating = !composing && (association.isNotEmpty() || emailOnStrip)
        val overKeys = panel == Panel.KEYS
        val answer = if (overKeys && !composing && !associating) answer() else null
        val choices = overKeys && (composing || answer != null || associating)
        val header = (composing || associating) && !overKeys && panel != Panel.EMOJI
        val toolbar = !password && panel != Panel.EMOJI && !choices
        val syllablesOpen = panel == Panel.SYLLABLES
        val englishRow = typingEnglish && overKeys && (composing || associating) && !emailOnStrip
        views.emojiTabs.visibility = visibleIf(panel == Panel.EMOJI)
        views.strip.visibility = visibleIf(choices || header)
        views.toolbar.visibility = visibleIf(toolbar)
        when {
            panel == Panel.EMOJI -> views.emojiTabs.buttons = emojiTabButtons()
            answer != null -> views.strip.showAnswer(answer.shown)
            associating && overKeys -> views.strip.showCandidates(
                null,
                if (emailOnStrip) emailOffers.map { it.label } else associationLabels(),
                emoji = englishRow,
            )
            composing && overKeys -> views.strip.showCandidates(
                if (typingEnglish) null else currentPreedit(),
                candidateLabels(),
                syllablesExpanded = syllablesOpen,
                emoji = englishRow,
                learned = wordMarks(),
            )
            header -> views.strip.showHeader(
                if (composing && !typingEnglish) currentPreedit() else Preedit.EMPTY,
                wordsExpanded = panel == Panel.CANDIDATES,
                syllablesExpanded = syllablesOpen,
            )
        }
        if (toolbar) {
            // The toolbar already reserves the arrow column, so a second right inset would shift `|`.
            if (header) views.toolbar.setPadding(0, headerTop, 0, 0) else views.toolbar.setPadding(0, 0, 0, 0)
            views.toolbar.buttons = toolbarButtons()
        }
    }

    private fun currentPreedit(): Preedit {
        if (typingEnglish) return Preedit("", composers.english.raw(), emptyList())
        val preedit = composer.preedit
        return Preedit(toScript(preedit.chosen), preedit.pinyin, preedit.choices)
    }

    private fun currentKeyGrid(): KeyGrid {
        val caps = KeyCaps(
            language = language,
            shifted = shifted,
            enterLabel = enterLabel(),
            password = password,
            emojiSlots = List(Settings.EMOJI_SLOTS, settings::emojiSlot),
            symbolLanguage = settings.symbolLanguage,
            email = emailField,
        )
        val key = PageKey(layout, page, caps)
        if (key != pageKey || keyGrid == null) {
            pageKey = key
            keyGrid = layouts.build(layout, page, caps)
        }
        return keyGrid!!
    }

    private fun enterLabel(): String {
        val id = if (composer.isComposing) {
            R.string.enter_newline
        } else {
            when (editor.enterAction) {
                EditorInfo.IME_ACTION_SEARCH -> R.string.enter_search
                EditorInfo.IME_ACTION_SEND -> R.string.enter_send
                EditorInfo.IME_ACTION_GO -> R.string.enter_go
                EditorInfo.IME_ACTION_NEXT -> R.string.enter_next
                EditorInfo.IME_ACTION_PREVIOUS -> R.string.enter_previous
                EditorInfo.IME_ACTION_DONE -> R.string.enter_done
                else -> R.string.enter_newline
            }
        }
        return context.getString(id)
    }

    private fun toolbarButtons(): List<ButtonRowView.Button> = ToolbarButton.entries.map { button ->
        when (button) {
            ToolbarButton.EFFECTS -> ButtonRowView.Button(
                text(R.string.toolbar_tools),
                selected = panel == Panel.EFFECTS,
                enabled = signing == null,
                mark = if (activeFestivals.isNotEmpty()) "✨" else null,
            )
            ToolbarButton.SCRIPT -> ButtonRowView.Button(
                text(if (settings.script == Script.TRADITIONAL) R.string.toolbar_traditional else R.string.toolbar_simplified),
            )
            ToolbarButton.LAYOUT -> ButtonRowView.Button(
                text(if (layout == Layout.T9) R.string.toolbar_layout_t9 else R.string.toolbar_layout_qwerty),
                enabled = canSwitchLayout,
            )
            ToolbarButton.CLIPBOARD -> ButtonRowView.Button(text(R.string.toolbar_clipboard), selected = panel == Panel.CLIPBOARD)
            ToolbarButton.EMOJI -> ButtonRowView.Button(text(R.string.toolbar_emoji))
        }
    }

    private fun soundButtons(): List<ButtonRowView.Button> = listOf(
        switchButton(text(R.string.effects_sound), settings.keySound),
        ButtonRowView.Button("", blank = true, enabled = false),
    )

    private fun festivalTestButtons(): List<ButtonRowView.Button> = listOf(
        switchButton(text(R.string.effects_festival_test), settings.festivalTest),
        ButtonRowView.Button("", blank = true, enabled = false),
    )

    private fun wordActionButtons(): List<ButtonRowView.Button> = listOf(
        toolButton(text(R.string.words_export), wordSide == WordSide.EXPORT),
        toolButton(text(R.string.words_import), wordSide == WordSide.IMPORT),
    )

    private fun placeButtons(): List<ButtonRowView.Button> = listOf(
        toolButton(text(R.string.words_local), selected = false),
        toolButton(text(R.string.words_drive), selected = false),
    )

    /** A resting tool key uses the function cap. An open one uses the active blue. */
    private fun toolButton(label: String, selected: Boolean) = ButtonRowView.Button(
        label,
        selected = selected,
        background = if (selected) null else context.getColor(R.color.key_function),
    )

    /** Label plus the red/gray slider. The cap stays the function color; the slider shows on or off. */
    private fun switchButton(label: String, on: Boolean) = ButtonRowView.Button(
        label,
        switchOn = on,
        background = context.getColor(R.color.key_function),
    )

    /** Candidates while composing, mail domains after `@`, association remainders otherwise. */
    private fun choiceLabels(): List<String?> = when {
        inputComposing -> candidateLabels()
        emailOffers.isNotEmpty() -> emailOffers.map { it.label }
        else -> associationLabels()
    }

    /** The token at the cursor, only while this field declared itself an email address. */
    private fun refreshEmailOffers() {
        if (!emailField || signing != null) {
            emailOffers = emptyList()
            return
        }
        val before = editor.textBeforeCursor(EMAIL_LOOK)?.toString().orEmpty()
        val after = editor.textAfterCursor(1)?.toString().orEmpty()
        emailOffers = EmailDomains.offers(before, after)
    }

    private fun associationLabels(): List<String> = if (typingEnglish) association else association.map(::toScript)

    private fun emojiTabButtons(): List<ButtonRowView.Button> {
        val names = listOf(R.string.emoji_back, R.string.emoji_tab_frequent) + EmojiTab.entries.map(::tabName)
        val tabs = names.mapIndexed { i, name -> ButtonRowView.Button(text(name), selected = i == emojiTab + 1) }
        return tabs + ButtonRowView.Button(text(R.string.key_delete), repeats = true)
    }

    private fun tabName(tab: EmojiTab): Int = when (tab) {
        EmojiTab.FACE -> R.string.emoji_tab_face
        EmojiTab.HAND -> R.string.emoji_tab_hand
        EmojiTab.SYMBOL -> R.string.emoji_tab_symbol
        EmojiTab.PERSON -> R.string.emoji_tab_person
        EmojiTab.FOOD -> R.string.emoji_tab_food
        EmojiTab.NATURE -> R.string.emoji_tab_nature
    }

    private fun text(id: Int): String = context.getString(id)

    private fun clearCompositions() {
        composers.qwerty.clear()
        composers.t9.clear()
        composers.english.clear()
    }

    private companion object {
        /** Index of ⌫ in the emoji tab row, after 返回, 常用 and the tabs. */
        val EMOJI_DELETE = EmojiTab.entries.size + 2

        /** Local part, `@`, and a short domain all fit inside this. */
        const val EMAIL_LOOK = 96

        fun visibleIf(shown: Boolean): Int = if (shown) View.VISIBLE else View.GONE

        fun navigationBarHeight(insets: WindowInsets): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
    }

    /**
     * Prefer the window's own insets. A dispatched pass can report 0 before the window is ready,
     * which is what drops the keys into the gesture area.
     */
    private fun applyNavigationPadding(view: View, dispatched: WindowInsets?) {
        val fromDispatch = dispatched?.let(::navigationBarHeight)
        val fromRoot = view.rootWindowInsets?.let(::navigationBarHeight)
        val bottom = when {
            fromDispatch != null && fromDispatch > 0 -> fromDispatch
            fromRoot != null -> fromRoot
            fromDispatch != null -> fromDispatch
            else -> return
        }
        if (view.paddingBottom != bottom) view.setPadding(0, 0, 0, bottom)
    }

    /** A bar in the text run, so it stays beside the last character instead of the box edge. */
    private class CaretSpan(
        private val color: Int,
        private val width: Int,
        private val shown: Boolean,
    ) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = width

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            if (!shown) return
            val old = paint.color
            paint.color = color
            val height = (bottom - top).toFloat().coerceAtLeast(1f)
            val bar = height * 0.72f
            val barTop = top + (height - bar) / 2f
            canvas.drawRect(x, barTop, x + width, barTop + bar, paint)
            paint.color = old
        }
    }
}
