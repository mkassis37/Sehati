package com.mkassis37.sehati

/**
 * Conservative line parser for laboratory OCR text.
 * It only proposes rows where a name, numeric result and optional numeric reference range
 * appear on one OCR line. Every proposal must be reviewed by the user before saving.
 */
data class LabReportCandidate(
    val testName: String,
    val value: Double,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val sourceLine: String
)

object LabReportParser {
    private val rowPattern = Regex(
        """^(.+?)\s+([+-]?\d+(?:[.,]\d+)?)\s*([A-Za-zµμ/%³²0-9._/-]*)\s*(?:\(?\s*([+-]?\d+(?:[.,]\d+)?)\s*[-–]\s*([+-]?\d+(?:[.,]\d+)?)\s*\)?)?$"""
    )

    fun parse(text: String): List<LabReportCandidate> {
        val candidates = linkedMapOf<String, LabReportCandidate>()
        text.lineSequence().forEach { raw ->
            val line = raw.replace('|', ' ').replace(Regex("""\s+"""), " ").trim()
            if (line.length < 5 || line.startsWith("---") || line.contains("Reference range", true) ||
                line.contains("Test name", true) || line.contains("Result", true)) return@forEach
            val match = rowPattern.matchEntire(line) ?: return@forEach
            val name = match.groupValues[1].trim().trim('-', ':', '•')
            val value = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return@forEach
            val unit = match.groupValues[3].trim()
            val low = match.groupValues[4].takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
            val high = match.groupValues[5].takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
            if (name.length < 2 || name.length > 70 || name.none { it.isLetter() } ||
                value.isNaN() || value.isInfinite() || (low != null && high != null && low > high)) return@forEach
            val candidate = LabReportCandidate(name, value, unit, low, high, line)
            candidates[name.lowercase()] = candidate
        }
        return candidates.values.toList()
    }
}
