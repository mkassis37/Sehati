package com.mkassis37.sehati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabReportParserTest {
    @Test
    fun ageUsesCompletedYearsAndRejectsFutureOrInvalidBirthdays() {
        assertEquals(31, "1995-11-25".ageOnDate(java.time.LocalDate.parse("2026-11-25")))
        assertEquals(30, "1995-11-26".ageOnDate(java.time.LocalDate.parse("2026-11-25")))
        assertEquals(null, "2027-01-01".ageOnDate(java.time.LocalDate.parse("2026-11-25")))
        assertEquals(null, "not-a-date".ageOnDate(java.time.LocalDate.parse("2026-11-25")))
    }

    @Test
    fun parsesVerticalPdfTableWithFlagAndReferenceRange() {
        val rows = LabReportParser.parse(
            "Specimen Date : Oct 20, 2026@12:24:13\nTest Name\nResult\nRef. Range\nUnit\n" +
            "Hemoglobin\n11.5 L\n13.2 - 16.6\ng/dL\n"
        )
        assertEquals(1, rows.size)
        assertEquals("Hemoglobin", rows[0].testName)
        assertEquals(11.5, rows[0].value!!, 0.0001)
        assertEquals("g/dL", rows[0].unit)
        assertEquals(13.2, rows[0].referenceLow!!, 0.0001)
        assertEquals(16.6, rows[0].referenceHigh!!, 0.0001)
        assertEquals("L", rows[0].flag)
        assertEquals("2026-10-20", rows[0].performedAt)
    }

    @Test
    fun parsesOneSidedReferenceLimit() {
        val rows = LabReportParser.parse("ALT\n26.3\nRef: <=41\nU/L")
        assertEquals(1, rows.size)
        assertEquals(26.3, rows[0].value!!, 0.0001)
        assertEquals(null, rows[0].referenceLow)
        assertEquals(41.0, rows[0].referenceHigh!!, 0.0001)
    }

    @Test
    fun supportsDecimalCommaAndArabicText() {
        val rows = LabReportParser.parse("فيتامين د\n22,3\n20 - 50\nng/mL")
        assertEquals(1, rows.size)
        assertEquals("فيتامين د", rows[0].testName)
        assertEquals(22.3, rows[0].value!!, 0.0001)
    }

    @Test
    fun skipsAmbiguousTextualAndCellRangeResults() {
        val rows = LabReportParser.parse("Pus Cells\n2-3\n0-5/HPF\n")
        assertTrue(rows.isEmpty())
    }
    @Test
    fun parsesQualitativeTableCellsAndRetainsArabicDigitsInNumericResults() {
        val rows = LabReportParser.parse("Urine appearance\tHazy\tClear\nKetones\tNegative\tNegative\nفيتامين د\n٢٢٫٣\n٢٠ - ٥٠\nng/mL")
        assertEquals("Hazy", rows.first { it.testName == "Urine appearance" }.qualitativeValue)
        assertEquals(null, rows.first { it.testName == "Ketones" }.value)
        assertEquals("Negative", rows.first { it.testName == "Ketones" }.qualitativeValue)
        assertEquals(22.3, rows.first { it.testName == "فيتامين د" }.value!!, 0.0001)
    }


    @Test
    fun translatesCommonArabicLabNamesToEnglishCanonicalNames() {
        val rows = LabReportParser.parse(
            "الهيموجلوبين\\n13.2\\n12 - 16\\ng/dL\\n" +
            "فيتامين د\\n25\\n20 - 50\\nng/mL\\n" +
            "السكر التراكمي\\n5.6\\n4 - 5.7\\n%"
        )
        assertTrue(rows.any { it.testName == "Hemoglobin" })
        assertTrue(rows.any { it.testName == "Vitamin D" })
        assertTrue(rows.any { it.testName == "Hemoglobin A1c (HbA1c)" })
    }

    @Test
    fun keepsEnglishTestNamesAndRejectsNormalReferenceLabels() {
        val rows = LabReportParser.parse(
            "Hemoglobin Level 13.8 g/dl (11.7 - 15.5)\n" +
            "Normal 80 - 137 mg/dL\n" +
            "Report page 4 4 0"
        )
        assertTrue(rows.any { it.testName == "Hemoglobin Level" && it.value == 13.8 })
        assertTrue(rows.none { it.testName.contains("Normal", ignoreCase = true) })
        assertTrue(rows.none { it.testName.contains("Report page", ignoreCase = true) })
    }
}
