package com.mkassis37.sehati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabOcrLayoutTest {
    @Test
    fun readsArabicWordsAndTheirCoordinatesFromTesseractHocr() {
        val hocr = """
            <div class='ocr_page'>
              <span class='ocrx_word' id='word_1' title='bbox 300 10 390 28; x_wconf 98'>الهيموغلوبين</span>
              <span class='ocrx_word' id='word_2' title='bbox 300 40 330 58; x_wconf 95'>13.2</span>
            </div>
        """.trimIndent()
        val tokens = parseArabicHocrWords(hocr)
        assertEquals(1, tokens.size)
        assertEquals("الهيموغلوبين", tokens.single().text)
        assertEquals(300, tokens.single().left)
        assertEquals(390, tokens.single().right)
    }

    @Test
    fun mergesArabicAndLatinWordsInPositionOrderAndKeepsColumnSeparators() {
        val tokens = listOf(
            LabOcrToken("HbA1c", 180, 10, 220, 26),
            LabOcrToken("5.6", 120, 10, 145, 26),
            LabOcrToken("الهيموغلوبين", 300, 10, 390, 26),
            LabOcrToken("%", 82, 10, 92, 26),
            LabOcrToken("4.0-5.7", 5, 10, 52, 26)
        )
        val output = mergeLabOcrTokens(tokens)
        assertEquals("الهيموغلوبين\tHbA1c\t5.6\t%\t4.0-5.7", output)
    }

    @Test
    fun separatesDifferentVisualRowsInsteadOfMergingTheirCells() {
        val output = mergeLabOcrTokens(
            listOf(
                LabOcrToken("Glucose", 10, 10, 60, 25),
                LabOcrToken("87", 110, 10, 130, 25),
                LabOcrToken("Sodium", 10, 50, 60, 65),
                LabOcrToken("141", 110, 50, 140, 65)
            )
        )
        assertEquals("Glucose\t87\nSodium\t141", output)
    }

    @Test
    fun ignoresLatinOnlyWordsInArabicHocrButRetainsArabicDigits() {
        val hocr = """
            <span class='ocrx_word' title='bbox 10 10 40 25; x_wconf 98'>WBC</span>
            <span class='ocrx_word' title='bbox 50 10 70 25; x_wconf 98'>١٢</span>
        """.trimIndent()
        val tokens = parseArabicHocrWords(hocr)
        assertEquals(1, tokens.size)
        assertTrue(tokens.single().text.contains('١'))
    }
}
