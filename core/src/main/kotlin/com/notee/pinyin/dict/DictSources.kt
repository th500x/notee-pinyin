package com.notee.pinyin.dict

import com.notee.pinyin.engine.EngineAssets
import java.io.File

/** Where the vendored sources sit under the repo's `dict/` directory. */
class DictSources(dir: File) {
    val characters = File(dir, "rime-ice/8105.dict.yaml")
    val words = listOf(File(dir, "rime-ice/base.dict.yaml"), File(dir, "rime-ice/ext.dict.yaml"))
    val pins = File(dir, "pins.tsv")
    val scriptTables = listOf(File(dir, "opencc/STCharacters.txt"), File(dir, "opencc/STPhrases.txt"))
    val englishWords = listOf(
        File(dir, "rime-ice/en_dicts/en.dict.yaml"),
        File(dir, "rime-ice/en_dicts/en_ext.dict.yaml"),
    )
    val englishFrequency = File(dir, "aosp/en_US_wordlist.combined.gz")
    val englishNext = File(dir, "aosp/en_next.tsv")

    val all: List<File>
        get() = listOf(characters, pins, englishFrequency, englishNext) + words + englishWords + scriptTables

    fun compileLexicon(outputDir: File): LexiconCompiler.Report =
        LexiconCompiler.compile(characters, words, pins, File(outputDir, EngineAssets.LEXICON))

    fun compileScript(outputDir: File): Int =
        ScriptTableCompiler.compile(scriptTables, File(outputDir, EngineAssets.SCRIPT))

    fun compileEnglish(outputDir: File): EnglishCompiler.Report =
        EnglishCompiler.compile(englishWords, englishFrequency, englishNext, File(outputDir, EngineAssets.ENGLISH))
}

/** Build entry point, `com.notee.pinyin.dict.DictSourcesKt <dictDir> <assetDir>`. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <dictDir> <assetDir>" }
    val sources = DictSources(File(args[0]))
    val outputDir = File(args[1])
    val lexicon = sources.compileLexicon(outputDir)
    val script = sources.compileScript(outputDir)
    val english = sources.compileEnglish(outputDir)
    println(
        "lexicon: ${lexicon.syllables} syllables, ${lexicon.codes} codes, ${lexicon.entries} entries, " +
            "${lexicon.dropped} dropped; s2t: $script keys; english: ${english.words} words, ${english.nextHeads} next",
    )
}
