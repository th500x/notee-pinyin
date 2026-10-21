package com.notee.pinyin.keyboard

/** 26 键 or 9 键, switched by 布局. */
enum class Layout { QWERTY, T9 }

/** 中/英 on the keyboard; also which half of the symbol page is shown. */
enum class Language { CHINESE, ENGLISH }

/** 简体/繁体 for committed pinyin text. */
enum class Script { SIMPLIFIED, TRADITIONAL }

/** Key pages. [LETTERS] is the 26-key or 9-key page of the current layout. */
enum class Page { LETTERS, NUMBERS, SYMBOLS }

/** What fills the area under the strip. */
enum class Panel { KEYS, CANDIDATES, EFFECTS, CLIPBOARD, EMOJI }
