package com.olerast.suflyor

import com.olerast.suflyor.doc.RapidOcrMath
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.FloatBuffer

class RapidOcrMathTest {
    private fun decode(vararg indices: Int): String {
        val characters = listOf("", "中", "文", " ", "A")
        val scores = FloatArray(indices.size * characters.size) { .01f }
        indices.forEachIndexed { time, char -> scores[time * characters.size + char] = .95f }
        return RapidOcrMath.decode(FloatBuffer.wrap(scores), indices.size, characters.size, characters).text
    }
    @Test fun removesCtcBlanksAndConsecutiveRepeats() { assertEquals("中文", decode(0, 1, 1, 0, 2, 2, 0)) }
    @Test fun keepsRepeatedCharactersSeparatedByBlank() { assertEquals("中中", decode(1, 1, 0, 1)) }
    @Test fun preservesMixedEnglishAndSpaces() { assertEquals("中文 A", decode(1, 2, 3, 4)) }
    @Test fun blankPageHasNoInventedText() { assertEquals("", decode(0, 0)) }
    @Test fun preservesSupplementaryCharactersInDictionary() { assertEquals(listOf("", "中", "𠀀", "🔕", " "), RapidOcrMath.characters("中\n𠀀\n🔕\n")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDictionaryModelMismatch() {
        RapidOcrMath.decode(FloatBuffer.wrap(floatArrayOf(.1f, .9f)), 1, 2, listOf(""))
    }
}
