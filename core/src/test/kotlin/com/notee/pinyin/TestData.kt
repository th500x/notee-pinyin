package com.notee.pinyin

import com.notee.pinyin.dict.DictSources
import com.notee.pinyin.engine.CandidateItem
import com.notee.pinyin.engine.CandidateList
import com.notee.pinyin.engine.EngineAssets
import com.notee.pinyin.engine.EnglishLexicon
import com.notee.pinyin.engine.Lexicon
import com.notee.pinyin.engine.ScriptConverter
import com.notee.pinyin.engine.UserDictionary
import com.notee.pinyin.input.Composers
import java.io.File

/** The real dictionaries, compiled once per test run. */
object TestData {
    val repoDir = File(checkNotNull(System.getProperty("notee.repoDir")) { "run through Gradle" })

    private val assetDir: File by lazy {
        File(checkNotNull(System.getProperty("notee.testAssetDir"))).also { dir ->
            val sources = DictSources(File(repoDir, "dict"))
            sources.compileLexicon(dir)
            sources.compileScript(dir)
            sources.compileEnglish(dir)
        }
    }

    val lexicon: Lexicon by lazy { Lexicon.open(File(assetDir, EngineAssets.LEXICON)) }

    val script: ScriptConverter by lazy { ScriptConverter.open(File(assetDir, EngineAssets.SCRIPT)) }

    val english: EnglishLexicon by lazy { EnglishLexicon.open(File(assetDir, EngineAssets.ENGLISH)) }

    val emojiFile: File get() = File(repoDir, "app/src/main/assets/${EngineAssets.EMOJI}")

    /** Fresh composers with an empty user dictionary. */
    fun composers(user: UserDictionary = UserDictionary()) = Composers(lexicon, english, user)
}

fun CandidateList.texts(): List<String> = items.map {
    when (it) {
        is CandidateItem.Word -> it.candidate.text
        CandidateItem.Bar -> "|"
    }
}
