package com.olerast.suflyor.doc

import java.nio.FloatBuffer

/** CTC blank/repetition handling shared by Chinese and English RapidOCR models. */
internal object RapidOcrMath {
    data class Text(val text: String, val confidence: Float)

    fun decode(scores: FloatBuffer, steps: Int, classes: Int, characters: List<String>): Text {
        require(classes == characters.size && scores.remaining() >= steps * classes) { "CTC classes=$classes dictionary=${characters.size} values=${scores.remaining()} steps=$steps" }
        val text = StringBuilder()
        var previous = -1
        var confidence = 0f
        var count = 0
        repeat(steps) { time ->
            var best = 0
            var score = Float.NEGATIVE_INFINITY
            repeat(classes) { char ->
                val value = scores.get(time * classes + char)
                if (value > score) { score = value; best = char }
            }
            if (best != 0 && best != previous) {
                text.append(characters[best])
                confidence += score
                count++
            }
            previous = best
        }
        return Text(text.toString(), if (count == 0) 0f else confidence / count)
    }

    fun characters(metadata: String): List<String> = listOf("") + metadata.trimEnd('\n', '\r').split('\n').map { it.trimEnd('\r') } + " "
}
