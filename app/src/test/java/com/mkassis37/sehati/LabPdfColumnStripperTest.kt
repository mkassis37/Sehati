package com.mkassis37.sehati

import org.junit.Assert.assertEquals
import org.junit.Test

class LabPdfColumnStripperTest {
    @Test
    fun convertsWideHorizontalGapsIntoTableCellSeparators() {
        val line = reconstructLabPdfLine(
            listOf(
                PositionedLabGlyph("Sodium", 10f, 34f, 10f),
                PositionedLabGlyph("141", 90f, 18f, 10f),
                PositionedLabGlyph("mmol/L", 150f, 30f, 10f),
                PositionedLabGlyph("136-145", 230f, 38f, 10f)
            )
        )
        assertEquals("Sodium\t141\tmmol/L\t136-145", line)
    }

    @Test
    fun preservesWordsWithinCellsAndSortsCellsByPosition() {
        val line = reconstructLabPdfLine(
            listOf(
                PositionedLabGlyph("mg/dL", 145f, 28f, 10f),
                PositionedLabGlyph("Glucose", 10f, 32f, 10f),
                PositionedLabGlyph("fasting", 45f, 28f, 10f),
                PositionedLabGlyph("87", 95f, 12f, 10f),
                PositionedLabGlyph("65-125", 220f, 30f, 10f)
            )
        )
        assertEquals("Glucose fasting\t87\tmg/dL\t65-125", line)
    }

    @Test
    fun retainsArabicTextInPositionedCells() {
        val line = reconstructLabPdfLine(
            listOf(
                PositionedLabGlyph("الوحدة", 200f, 30f, 12f),
                PositionedLabGlyph("النتيجة", 100f, 30f, 12f),
                PositionedLabGlyph("الهيموغلوبين", 10f, 70f, 12f)
            )
        )
        assertEquals("الهيموغلوبين\tالنتيجة\tالوحدة", line)
    }
}
