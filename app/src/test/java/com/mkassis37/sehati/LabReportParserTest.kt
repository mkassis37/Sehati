package com.mkassis37.sehati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabReportParserTest {
    @Test
    fun parsesNumericResultWithUnitAndReferenceRange() {
        val rows = LabReportParser.parse("Hemoglobin Level 13.8 g/dL (11.7 - 15.5)")
        assertEquals(1, rows.size)
        assertEquals("Hemoglobin Level", rows[0].testName)
        assertEquals(13.8, rows[0].value, 0.0001)
        assertEquals("g/dL", rows[0].unit)
        assertEquals(11.7, rows[0].referenceLow!!, 0.0001)
        assertEquals(15.5, rows[0].referenceHigh!!, 0.0001)
    }

    @Test
    fun skipsHeadersAndDoesNotGuessWhenReferenceIsMissing() {
        val rows = LabReportParser.parse(
            "Test name   Result   Reference range\n" +
            "Creatinine 0.6 mg/dl (0.6 - 1.2)\n" +
            "Clinical Chemistry Report"
        )
        assertEquals(1, rows.size)
        assertEquals("Creatinine", rows[0].testName)
        assertTrue(rows[0].referenceLow != null)
    }

    @Test
    fun supportsDecimalComma() {
        val rows = LabReportParser.parse("Vitamin D 22,3 ng/mL")
        assertEquals(1, rows.size)
        assertEquals(22.3, rows[0].value, 0.0001)
    }

    @Test
    fun doesNotTreatMicroscopyRangeAsNumericResult() {
        val rows = LabReportParser.parse("Pus Cells 2-3")
        assertTrue(rows.isEmpty())
    }
}
