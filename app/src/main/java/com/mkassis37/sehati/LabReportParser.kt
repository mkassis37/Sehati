package com.mkassis37.sehati

/**
 * Conservative parser for the common vertical table layout used by laboratory PDFs.
 * A candidate is emitted only when a test name, numeric result, reference line and unit
 * can be associated in a recognizable sequence. Every candidate requires user review.
 */
data class LabReportCandidate(
    val testName: String,
    val value: Double,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val flag: String,
    val performedAt: String?,
    val sourceLine: String
)

object LabReportParser {
    private val isoDate = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
    private val monthDate = Regex("""\b([A-Za-z]{3,9})\s+(\d{1,2}),\s*(\d{4})\b""")
    private val numericResult = Regex("""^([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?$""")
    private val numericRange = Regex("""^([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*[-–]\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""")
    private val upperLimit = Regex("""^(?:Ref\s*:\s*)?<=\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""", RegexOption.IGNORE_CASE)
    private val lowerLimit = Regex("""^(?:Ref\s*:\s*)?>=\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""", RegexOption.IGNORE_CASE)
    private val inlineRow = Regex(
        """^(.+?)\s+([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?\s+(.+?)\s+([A-Za-zµμ/%0-9'._/-]+)$"""
    )

    fun parse(text: String): List<LabReportCandidate> {
        val lines = text.lineSequence().map { it.replace('\u00a0', ' ').trim() }
            .filter { it.isNotBlank() }.toList()
        val found = linkedMapOf<String, LabReportCandidate>()
        val reportDate = extractReportDate(lines)

        fun numeric(value: String): Double? = value.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() }

        fun reference(raw: String): Pair<Double?, Double?>? {
            val value = raw.trim().removeSurrounding("(", ")")
            numericRange.matchEntire(value)?.let {
                val low = numeric(it.groupValues[1]) ?: return null
                val high = numeric(it.groupValues[2]) ?: return null
                if (low <= high) return low to high
                return null
            }
            upperLimit.matchEntire(value)?.let { return null to numeric(it.groupValues[1]) }
            lowerLimit.matchEntire(value)?.let { return numeric(it.groupValues[1]) to null }
            return null
        }

        fun add(nameRaw: String, resultRaw: String, referenceRaw: String, unitRaw: String, source: String) {
            val name = nameRaw.trim().trim(':', '-', '•')
            val result = numericResult.matchEntire(resultRaw.trim()) ?: return
            val value = numeric(result.groupValues[1]) ?: return
            val ref = reference(referenceRaw) ?: return
            val unit = unitRaw.trim()
            if (name.length !in 2..70 || name.none { it.isLetter() } ||
                unit.isBlank() || unit.length > 24) return
            val candidate = LabReportCandidate(name, value, unit, ref.first, ref.second,
                result.groupValues[2], reportDate, source)
            found[name.lowercase()] = candidate
        }

        // Typical PDF text layer: each cell appears on its own line.
        var i = 0
        while (i + 3 < lines.size) {
            val name = lines[i]
            val result = lines[i + 1]
            val ref = lines[i + 2]
            val unit = lines[i + 3]
            val isHeader = name.contains("test name", true) || name.equals("result", true) ||
                name.contains("laboratory report", true) || name.contains("reference", true)
            if (!isHeader && numericResult.matches(result) &&
                (numericRange.matches(ref.removeSurrounding("(", ")")) ||
                    upperLimit.matches(ref) || lowerLimit.matches(ref))) {
                add(name, result, ref, unit, listOf(name, result, ref, unit).joinToString(" | "))
                i += 4
            } else {
                i++
            }
        }

        // Also accept a complete row when OCR returns the table as one line.
        lines.forEach { line ->
            val match = inlineRow.matchEntire(line) ?: return@forEach
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            val flag = match.groupValues[3]
            val ref = match.groupValues[4]
            val unit = match.groupValues[5]
            add(name, value + flag, ref, unit, line)
        }
        return found.values.toList()
    }
 
    private fun extractReportDate(lines: List<String>): String? {
        val dateSource = lines.firstOrNull {
            it.contains("specimen date", true) || it.contains("sampling date", true)
        } ?: lines.firstOrNull { it.contains("result date", true) }
            ?: lines.joinToString(" ")
        isoDate.find(dateSource)?.groupValues?.get(1)?.let { value ->
            runCatching { java.time.LocalDate.parse(value) }.getOrNull()?.let { return it.toString() }
        }
        monthDate.find(dateSource)?.let { match ->
            val value = match.value
            val parsed = listOf("MMM d, yyyy", "MMMM d, yyyy").firstNotNullOfOrNull { pattern ->
                runCatching {
                    java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.US))
                }.getOrNull()
            }
            if (parsed != null) return parsed.toString()
        }
        return null
    }
}
