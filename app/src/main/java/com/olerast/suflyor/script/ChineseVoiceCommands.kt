package com.olerast.suflyor.script

/** Exact local commands; kept separate from fuzzy script matching to avoid accidental restarts. */
internal object ChineseVoiceCommands {
    val restartPhrases = listOf(
        "欧了重新开始", "欧了从新开始", "欧了重新来", "欧了从新来", "欧了从头开始", "欧了返回开始",
    )
    private val characters = restartPhrases.map { it.map(Char::toString) }

    /** Exclusive end positions, so words after the last command can immediately follow the script again. */
    fun restartEnds(words: List<String>): List<Int> = words.indices.mapNotNull { start ->
        characters.firstOrNull { phrase -> start + phrase.size <= words.size &&
            phrase.indices.all { words[start + it] == phrase[it] }
        }?.let { start + it.size }
    }
}
