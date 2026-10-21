package com.notee.pinyin.input

import com.notee.pinyin.engine.Associations
import com.notee.pinyin.engine.EnglishLexicon
import com.notee.pinyin.engine.Lexicon
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.engine.WordSearch
import com.notee.pinyin.rank.CandidateRanker
import com.notee.pinyin.rank.PinTable
import com.notee.pinyin.rank.T9Matcher

/** Both pinyin keyboards, plus English completion, over one user dictionary. Only the visible layout's pinyin composer is used. */
class Composers(lexicon: Lexicon, englishLexicon: EnglishLexicon, private val user: UserDictionary) {
    val syllables = lexicon.syllables
    val associations = Associations(lexicon, user)
    private val search = WordSearch(lexicon, user)
    private val matcher = T9Matcher(syllables)
    private val ranker = CandidateRanker(syllables, PinTable.parse(lexicon.pinText, syllables), user)
    private val transposition = Transposition(lexicon, user, search, matcher)

    val qwerty = QwertyComposer(syllables, search, ranker, user, transposition)
    val t9 = T9Composer(syllables, search, ranker, user, matcher, transposition)
    val english = EnglishComposer(englishLexicon)

    /** Off while the field asked not to learn. Both pinyin composers share [user]. */
    var learning: Boolean
        get() = user.learning
        set(value) { user.learning = value }
}
