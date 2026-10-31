package com.olerast.suflyor.script

import android.icu.text.Transliterator

/** Android 10+ system ICU, entirely offline. Unsupported characters simply keep exact matching. */
internal object ChinesePhonetics {
    private val converter by lazy { runCatching { Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }.getOrNull() }
    @Synchronized
    fun key(character: String): String? = runCatching {
        converter?.transliterate(character)?.trim()?.takeIf { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } }
    }.getOrNull()
}
