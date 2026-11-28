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
        assertEquals("Vitamin D", rows[0].testName)
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
        assertEquals(22.3, rows.first { it.testName == "Vitamin D" }.value!!, 0.0001)
    }


    @Test
    fun translatesCommonArabicLabNamesToEnglishCanonicalNames() {
        val rows = LabReportParser.parse(
            "الهيموجلوبين\n13.2\n12 - 16\ng/dL\n" +
            "فيتامين د\n25\n20 - 50\nng/mL\n" +
            "السكر التراكمي\n5.6\n4 - 5.7\n%"
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

    @Test
    fun parsesReorderedLabColumnsUsingHeaderLabels() {
        val rows = LabReportParser.parse(
            "REFERENCE RANGE | UNITS | RESULT VALUES | ABN | PROCEDURE\n" +
            "4.0 - 5.7 | % | 5.6 | | Hemoglobin A1c (HbA1c)\n" +
            "136 - 145 | mmol/L | 141 | | Sodium"
        )
        val a1c = rows.single { it.testName.contains("Hemoglobin A1c") }
        assertEquals(5.6, a1c.value!!, 0.0001)
        assertEquals("%", a1c.unit)
        assertEquals(4.0, a1c.referenceLow!!, 0.0001)
        assertEquals(5.7, a1c.referenceHigh!!, 0.0001)
        val sodium = rows.single { it.testName == "Sodium" }
        assertEquals(141.0, sodium.value!!, 0.0001)
        assertEquals("mmol/L", sodium.unit)
        assertEquals(136.0, sodium.referenceLow!!, 0.0001)
        assertEquals(145.0, sodium.referenceHigh!!, 0.0001)
    }

    @Test
    fun parsesSeparateNormalAndAbnormalResultColumnsWithoutMixingRows() {
        val rows = LabReportParser.parse(
            "TEST NAME | RESULT ABNORMAL | RESULT NORMAL | UNITS | REFERENCE RANGE\n" +
            "Glucose | | 87.0 | mg/dL | 65.0-125\n" +
            "Potassium | 5.8 | | mmol/L | 3.5-5.1"
        )
        assertEquals(2, rows.size)
        val glucose = rows.single { it.testName == "Glucose" }
        assertEquals(87.0, glucose.value!!, 0.0001)
        assertEquals("mg/dL", glucose.unit)
        assertEquals(65.0, glucose.referenceLow!!, 0.0001)
        assertEquals(125.0, glucose.referenceHigh!!, 0.0001)
        val potassium = rows.single { it.testName == "Potassium" }
        assertEquals(5.8, potassium.value!!, 0.0001)
        assertEquals("mmol/L", potassium.unit)
        assertEquals(3.5, potassium.referenceLow!!, 0.0001)
        assertEquals(5.1, potassium.referenceHigh!!, 0.0001)
    }

    @Test
    fun parsesArabicAndLatinMixedAnalyteNamesAndArabicNumeralsInDelimitedColumns() {
        val rows = LabReportParser.parse(
            "اسم الفحص | النتيجة | المعدل الطبيعي | الوحدة\n" +
            "السكر التراكمي HbA1c | ٥٫٦ | ٤٫٠ - ٥٫٧ | %\n" +
            "الهيموغلوبين Hb | ١٣٫٢ | ١٢ - ١٦ | g/dL"
        )
        assertEquals(2, rows.size)
        val a1c = rows.single { it.testName.contains("HbA1c") }
        assertEquals(5.6, a1c.value!!, 0.0001)
        assertEquals(4.0, a1c.referenceLow!!, 0.0001)
        assertEquals(5.7, a1c.referenceHigh!!, 0.0001)
        assertEquals("%", a1c.unit)
        val hb = rows.single { it.testName.contains("Hb") }
        assertEquals(13.2, hb.value!!, 0.0001)
        assertEquals("g/dL", hb.unit)
    }

    @Test
    fun parsesWhitespaceAlignedColumnsAndDifferentUnitFormats() {
        val rows = LabReportParser.parse(
            "TEST NAME             RESULT       UNITS        REFERENCE RANGE\n" +
            "Creatinine             0.9          mg/dL        0.8 - 1.3\n" +
            "Vitamin D              22,3         ng/mL        20 - 50"
        )
        assertEquals(2, rows.size)
        assertEquals("mg/dL", rows.single { it.testName == "Creatinine" }.unit)
        assertEquals(22.3, rows.single { it.testName == "Vitamin D" }.value!!, 0.0001)
        assertEquals("ng/mL", rows.single { it.testName == "Vitamin D" }.unit)
    }

    @Test
    fun parsesHeaderlessRowsConservativelyAndKeepsEachReferenceWithItsTest() {
        val rows = LabReportParser.parse(
            "WBC | 6.10 | x10^3/uL | 4.00 - 11.00\n" +
            "RBC | 5.20 | x10^6/uL | 4.40 - 5.90"
        )
        assertEquals(2, rows.size)
        val wbc = rows.single { it.testName == "WBC" }
        assertEquals(6.10, wbc.value!!, 0.0001)
        assertEquals("x10^3/uL", wbc.unit)
        assertEquals(4.0, wbc.referenceLow!!, 0.0001)
        assertEquals(11.0, wbc.referenceHigh!!, 0.0001)
        val rbc = rows.single { it.testName == "RBC" }
        assertEquals(5.2, rbc.value!!, 0.0001)
        assertEquals(4.4, rbc.referenceLow!!, 0.0001)
        assertEquals(5.9, rbc.referenceHigh!!, 0.0001)
    }

    @Test
    fun ignoresTableHeadingsAndCommentRowsInsteadOfInventingLabResults() {
        val rows = LabReportParser.parse(
            "INVESTIGATION | RESULT | UNIT | REFERENCE INTERVAL\n" +
            "Chemistry Panel | | | \n" +
            "Comment | Sample is non-fasting | | \n" +
            "Sodium | 141 | mmol/L | 136-145"
        )
        assertEquals(1, rows.size)
        assertEquals("Sodium", rows.single().testName)
        assertEquals(141.0, rows.single().value!!, 0.0001)
    }


    @Test
    fun parsesProcedureResultFlagReferenceAndUnitLayoutSeenInPublicSampleReports() {
        val rows = LabReportParser.parse(
            "PROCEDURE | RESULT VALUES | ABN | REFERENCE RANGES | UNITS\n" +
            "Sodium | 141 | | 136 - 145 | mEq/L\n" +
            "Potassium | 4.8 | | 3.5 - 5.0 | mEq/L\n" +
            "Hgb | 10.9 | L | 12 - 16 | g/dL\n" +
            "Rapid strep screen | Positive | | |"
        )
        assertEquals(4, rows.size)
        assertEquals(141.0, rows.single { it.testName == "Sodium" }.value!!, 0.0001)
        assertEquals("mEq/L", rows.single { it.testName == "Potassium" }.unit)
        val hgb = rows.single { it.testName == "Hgb" }
        assertEquals(10.9, hgb.value!!, 0.0001)
        assertEquals("L", hgb.flag)
        assertEquals(12.0, hgb.referenceLow!!, 0.0001)
        assertEquals(16.0, hgb.referenceHigh!!, 0.0001)
        assertEquals("Positive", rows.single { it.testName == "Rapid strep screen" }.qualitativeValue)
    }

    @Test
    fun parsesExpandedReportWithSeparateNormalAndAbnormalColumnsAndFlags() {
        val rows = LabReportParser.parse(
            "Test | Normal | Abnormal | Flag | Units | Reference Range\n" +
            "Sodium | | 124 | L | mEq/L | 136-145\n" +
            "Carbon Dioxide | 25 | | | mEq/L | 23-29\n" +
            "Glucose | | 107 | H | mg/dL | 74-100"
        )
        assertEquals(3, rows.size)
        assertEquals(124.0, rows.single { it.testName == "Sodium" }.value!!, 0.0001)
        assertEquals("L", rows.single { it.testName == "Sodium" }.flag)
        assertEquals(25.0, rows.single { it.testName == "Carbon Dioxide" }.value!!, 0.0001)
        assertEquals("H", rows.single { it.testName == "Glucose" }.flag)
        assertEquals(100.0, rows.single { it.testName == "Glucose" }.referenceHigh!!, 0.0001)
    }

    @Test
    fun supportsInvestigationSpecimenAndReferenceIntervalHeadings() {
        val rows = LabReportParser.parse(
            "INVESTIGATION / SPECIMEN | RESULT | UNIT | REFERENCE INTERVAL\n" +
            "TSH | 2.29 | uIU/mL | 0.25 - 5.00\n" +
            "T4 | 89.76 | nmol/L | 60.00 - 120.00"
        )
        assertEquals(2, rows.size)
        assertEquals("TSH", rows[0].testName)
        assertEquals("uIU/mL", rows[0].unit)
        assertEquals(0.25, rows[0].referenceLow!!, 0.0001)
        assertEquals(5.0, rows[0].referenceHigh!!, 0.0001)
    }

    @Test
    fun retainsArabicCommentsInSourceTextWithoutTreatingThemAsTestRows() {
        val text = (
            "TEST | RESULT | UNIT | REFERENCE RANGE\n" +
            "Glucose | 107 | mg/dL | 74-100\n" +
            "ملاحظة المختبر: العينة غير صائمة، يرجى مراجعة الطبيب.\n"
        )
        val rows = LabReportParser.parse(text)
        assertEquals(1, rows.size)
        assertEquals("Glucose", rows.single().testName)
        assertTrue(text.contains("العينة غير صائمة"))
    }


    @Test
    fun parsesConventionalTestResultUnitsReferenceRangeLayout() {
        val rows = LabReportParser.parse(
            "Test | Result | Units | Reference Range\n" +
            "RED BLOOD CELLS | 5.8 | x 10^12/L | 4.5 - 6.5\n" +
            "HAEMOGLOBIN | 15.2 | g/dL | 13.8 - 18.8\n" +
            "ALT | 10.9 | IU/L | 0 - 40"
        )
        assertEquals(3, rows.size)
        assertEquals("x 10^12/L", rows.single { it.testName == "RED BLOOD CELLS" }.unit)
        assertEquals(15.2, rows.single { it.testName == "HAEMOGLOBIN" }.value!!, 0.0001)
        assertEquals("IU/L", rows.single { it.testName == "ALT" }.unit)
    }

    @Test
    fun parsesQualitativeUrineValuesAndKeepsReferenceTextSeparate() {
        val rows = LabReportParser.parse(
            "PROCEDURE | RESULT VALUES | ABN | REFERENCE RANGES | UNITS\n" +
            "Appearance | Clear | | Clear |\n" +
            "Glucose | Negative | | Negative |\n" +
            "Bacteria | Absent | | Absent | /HPF\n" +
            "Protein | Trace | A | Negative |"
        )
        assertEquals(4, rows.size)
        assertEquals("Clear", rows.single { it.testName == "Appearance" }.qualitativeValue)
        assertEquals("Negative", rows.single { it.testName == "Glucose" }.qualitativeValue)
        assertEquals("Absent", rows.single { it.testName == "Bacteria" }.qualitativeValue)
        assertEquals("/HPF", rows.single { it.testName == "Bacteria" }.unit)
        assertEquals("Trace", rows.single { it.testName == "Protein" }.qualitativeValue)
        assertEquals("A", rows.single { it.testName == "Protein" }.flag)
    }


    @Test
    fun combinesSeparateLowAndHighReferenceColumns() {
        val rows = LabReportParser.parse(
            "TEST | RESULT | UNITS | LOW LIMIT | HIGH LIMIT\n" +
            "TSH | 2.29 | uIU/mL | 0.25 | 5.00"
        )
        assertEquals(1, rows.size)
        val tsh = rows.single()
        assertEquals(2.29, tsh.value!!, 0.0001)
        assertEquals("uIU/mL", tsh.unit)
        assertEquals(0.25, tsh.referenceLow!!, 0.0001)
        assertEquals(5.0, tsh.referenceHigh!!, 0.0001)
    }

    @Test
    fun skipsAmbiguousRowsWhenBothNormalAndAbnormalResultCellsAreFilled() {
        val rows = LabReportParser.parse(
            "TEST | RESULT NORMAL | RESULT ABNORMAL | UNITS | REFERENCE RANGE\n" +
            "Glucose | 87 | 107 | mg/dL | 74-100\n" +
            "Sodium | 141 | | mmol/L | 136-145"
        )
        assertEquals(1, rows.size)
        assertEquals("Sodium", rows.single().testName)
        assertEquals(141.0, rows.single().value!!, 0.0001)
    }


    @Test
    fun keepsMicroscopyResultRangesAndReferenceUnitsAsText() {
        val rows = LabReportParser.parse(
            "Pus Cells 2-3 0-5/HPF\n" +
            "Red Cells 1-3 0-5/HPF"
        )
        assertEquals(2, rows.size)
        val pus = rows.single { it.testName == "Pus Cells" }
        assertEquals("2-3", pus.qualitativeValue)
        assertEquals("/HPF", pus.unit)
        assertEquals(0.0, pus.referenceLow!!, 0.0001)
        assertEquals(5.0, pus.referenceHigh!!, 0.0001)
        val red = rows.single { it.testName == "Red Cells" }
        assertEquals("1-3", red.qualitativeValue)
        assertEquals("/HPF", red.unit)
    }

    @Test
    fun parsesComparisonResultWithoutDiscardingItsOperator() {
        val rows = LabReportParser.parse(
            "Specific Gravity <=1.005 1.015 - 1.025"
        )
        assertEquals(1, rows.size)
        val result = rows.single()
        assertEquals("<=1.005", result.qualitativeValue)
        assertEquals(1.015, result.referenceLow!!, 0.0001)
        assertEquals(1.025, result.referenceHigh!!, 0.0001)
    }

    @Test
    fun readsHighAndLowFlagsPlacedBeforeTheResultValue() {
        val rows = LabReportParser.parse(
            "Lymphocytes H 45.3 % 18 - 44\n" +
            "MPV L 6.8 fL 7.5 - 11.5\n" +
            "HDL (High-density Lipoprotein) L 41.5 mg/dL 45.0 - 65.0"
        )
        assertEquals(3, rows.size)
        val lymphocytes = rows.single { it.testName == "Lymphocytes" }
        assertEquals(45.3, lymphocytes.value!!, 0.0001)
        assertEquals("H", lymphocytes.flag)
        val mpv = rows.single { it.testName == "MPV" }
        assertEquals(6.8, mpv.value!!, 0.0001)
        assertEquals("L", mpv.flag)
        val hdl = rows.single { it.testName.startsWith("HDL") }
        assertEquals(41.5, hdl.value!!, 0.0001)
        assertEquals("L", hdl.flag)
        assertEquals(45.0, hdl.referenceLow!!, 0.0001)
    }

}
