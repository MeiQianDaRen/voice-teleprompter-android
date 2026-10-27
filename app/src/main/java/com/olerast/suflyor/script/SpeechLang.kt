package com.olerast.suflyor.script

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument

/**
 * Language the reader speaks: picks the recognizer model and the rules for comparing heard and written words.
 * Russian calls [TextNorm] exactly as before English existed; English uses [EnglishNorm], Chinese [ChineseNorm].
 */
enum class SpeechLang(val code: String) {
    /** Legacy text normalization for regression fixtures; not a supported speech language. */
    RU("ru"),
    EN("en"),
    ZH("zh");

    /** One word of script or recognizer text. English keeps inner apostrophes ("don't"); Russian splits on them. */
    val wordRegex: Regex
        get() = when (this) {
            RU -> TextNorm.WORD
            EN -> EnglishNorm.WORD
            ZH -> ChineseNorm.WORD
        }

    /** Hesitations the recognizer prints that never appear in a script. */
    val fillers: Set<String>
        get() = when (this) {
            RU -> TextNorm.FILLERS
            EN -> EnglishNorm.FILLERS
            ZH -> ChineseNorm.FILLERS
        }

    fun normalize(raw: String): String = when (this) {
        RU -> TextNorm.normalizeWord(raw)
        EN -> EnglishNorm.normalizeWord(raw)
        ZH -> ChineseNorm.normalizeWord(raw)
    }

    /** Normalized words of a text in this language: the recognizer's output on every update, or script text. */
    fun words(text: String): List<String> = when (this) {
        RU -> TextNorm.words(text)
        EN -> EnglishNorm.WORD.findAll(text).map { EnglishNorm.normalizeWord(it.value) }.filter { it.isNotEmpty() }.toList()
        ZH -> ChineseNorm.words(text)
    }

    /**
     * Written in the other alphabet: Latin in a Russian script (transliterated, weight 0.5), Cyrillic in an English
     * one (weight 0: the English model can't say it, so the tracker steps over it like over a number).
     */
    fun isForeign(raw: String): Boolean = when (this) {
        RU -> raw.any { it in 'a'..'z' || it in 'A'..'Z' }
        EN -> raw.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC }
        ZH -> raw.any { it.isLetter() } && !ChineseNorm.isHan(raw)
    }

    /** How much a matched script word proves the position (before the tracker's frequency adjustment). */
    fun weight(norm: String, foreign: Boolean): Float = when (this) {
        RU -> TextNorm.weight(norm, foreign)
        EN -> EnglishNorm.weight(norm, foreign)
        ZH -> ChineseNorm.weight(norm, foreign)
    }

    fun similarity(a: String, b: String): Float = when (this) {
        RU -> TextNorm.similarity(a, b)
        EN -> EnglishNorm.similarity(a, b)
        ZH -> ChineseNorm.similarity(a, b)
    }

    /** Groups word forms for the frequency weights: a word repeated in another form still proves little. */
    fun stem(w: String): String = when (this) {
        // Word forms of one Russian word usually share the first five letters.
        RU -> if (w.length > 5) w.substring(0, 5) else w
        EN -> EnglishNorm.stem(w)
        ZH -> w
    }

    /**
     * The recognizer's unfinished last word may match the start of a longer script word. A complete English
     * function word may not: "the" is not the start of "then", "in" not of "into".
     */
    fun canBePrefix(partial: String): Boolean = when (this) {
        RU -> true
        EN -> partial !in EnglishNorm.STOP
        ZH -> false
    }

    /**
     * Recognized words (fillers already removed) made comparable with the script words in [vocab].
     * @param chains glued runs of the script's hyphen chains of letter names with their parts (Russian only).
     */
    fun prepareHypothesis(
        hyp: List<String>,
        vocab: Set<String>,
        scriptHasDigits: Boolean,
        chains: Map<String, List<String>> = emptyMap(),
    ): List<String> = when (this) {
        RU -> TextNorm.regroupLetterNames(hyp, vocab, chains)
        EN -> EnglishNorm.prepareHypothesis(hyp, vocab, scriptHasDigits)
        ZH -> hyp
    }

    /**
     * Runs of skipped script words or extra heard words last until the next match (see ScriptTracker.align).
     * English needs it for spelled-out numbers ("in twenty twenty five we…"). Russian keeps the old rule, under which
     * a step in the other direction restarts the run, so its behaviour stays exactly as tuned.
     */
    val strictGapRuns: Boolean
        get() = this == EN || this == ZH

    companion object {
        val supported: List<SpeechLang> = listOf(ZH, EN)

        fun fromCode(code: String?): SpeechLang? = supported.firstOrNull { it.code == code }

        /**
         * Majority alphabet of what the reader will say: body paragraphs outside [stage directions].
         * Han-majority scripts use Chinese; all other scripts use English. Russian is no longer a recognition option.
         */
        fun detect(doc: ScriptDocument): SpeechLang {
            var cyrillic = 0
            var latin = 0
            var han = 0
            for (p in doc.paragraphs) {
                if (p.kind != Paragraph.Kind.BODY) continue
                var note = false
                for (ch in p.text.codePoints().toArray()) {
                    when {
                        ch == '['.code -> note = true
                        ch == ']'.code -> note = false
                        note || !Character.isLetter(ch) -> Unit
                        else -> when (Character.UnicodeScript.of(ch)) {
                            Character.UnicodeScript.CYRILLIC -> cyrillic++
                            Character.UnicodeScript.LATIN -> latin++
                            Character.UnicodeScript.HAN -> han++
                            else -> Unit
                        }
                    }
                }
            }
            return if (han > 0 && han >= maxOf(latin, cyrillic)) ZH else EN
        }
    }
}
