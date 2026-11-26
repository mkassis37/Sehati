package com.mkassis37.sehati

/**
 * Conservative parser for the common vertical table layout used by laboratory PDFs.
 * Recognizes numeric rows and known qualitative cells in structured tables.
 * Every candidate requires user review.
 */
data class LabReportCandidate(
    val testName: String,
    val value: Double?,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val flag: String,
    val performedAt: String?,
    val sourceLine: String,
    val qualitativeValue: String? = null
)

object LabReportParser {
    private val qualitativeValues = setOf("positive", "negative", "nil", "hazy", "trace",
        "إيجابي", "سلبي", "عكر", "آثار")
    private val isoDate = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
    private val monthDate = Regex("""\b([A-Za-z]{3,9})\s+(\d{1,2}),\s*(\d{4})\b""")
    private val numericResult = Regex("""^([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?$""")
    private val numericRange = Regex("""^([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*[-–]\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""")
    private val upperLimit = Regex("""^(?:Ref\s*:\s*)?<=?\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""", RegexOption.IGNORE_CASE)
    private val lowerLimit = Regex("""^(?:Ref\s*:\s*)?>=?\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""", RegexOption.IGNORE_CASE)
    private val inlineRow = Regex(
        """^(.+?)\s+([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?\s+(.+?)\s+([A-Za-zµμ/%0-9'._/-]+)$"""
    )
    private val inlineUnitBeforeReference = Regex(
        """^(.+?)\s+([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?\s+([A-Za-zµμ/%0-9'._/-]+)\s+(\(?\s*(?:(?:[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)\s*[-–]\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))|(?:<=?|>=?)\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*\)?)$"""
    )

    fun parse(text: String): List<LabReportCandidate> {
        val lines = text.lineSequence().map { line ->
            line.replace('\u00a0', ' ').replace('٫', '.').map { character ->
                character.digitToIntOrNull()?.digitToChar() ?: character
            }.joinToString("").trim()
        }
            .filter { it.isNotBlank() }.toList()
        val found = linkedMapOf<String, LabReportCandidate>()
        val reportDate = extractReportDate(lines)

        fun numeric(value: String): Double? = value.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() }

        fun reference(raw: String?): Pair<Double?, Double?>? {
            if (raw.isNullOrBlank()) return null
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

        fun add(nameRaw: String, resultRaw: String, referenceRaw: String?, unitRaw: String, source: String) {
            val name = nameRaw.trim().trim(':', '-', '•')
            val normalizedName = name.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
            // Reject OCR status/reference labels and page furniture as test names.
            val rejectedName = normalizedName in setOf(
                "normal", "abnormal", "high", "low", "reference", "reference range",
                "ref range", "test name", "result", "unit", "comment", "comments",
                "page", "report page", "normal range", "diabetic", "good control",
                "fair control", "poor control"
            ) || normalizedName.contains("normal ") ||
                normalizedName.matches(Regex("""^(?:page|report page)\\s*[:#-]?\\s*\\d+.*$""")) ||
                normalizedName.matches(Regex("""^\\d+(?:\\s+\\d+){0,2}$"""))
            if (rejectedName) return
            val resultText = resultRaw.trim()
            val result = numericResult.matchEntire(resultText)
            val value = result?.groupValues?.get(1)?.let(::numeric)
            val qualitative = resultText.takeIf { it.lowercase() in qualitativeValues }
            if (value == null && qualitative == null) return
            val ref = if (qualitative == null) reference(referenceRaw) else null
            val unit = unitRaw.trim()
            if (name.length !in 2..70 || name.none { it.isLetter() } ||
                unit.length > 24) return
            val candidate = LabReportCandidate(name, value, unit, ref?.first, ref?.second,
                result?.groupValues?.get(2).orEmpty(), reportDate, source, qualitative)
            val key = listOf(name.lowercase(), value, qualitative?.lowercase(), unit,
                ref?.first, ref?.second, candidate.flag).joinToString("\u0000")
            found.putIfAbsent(key, candidate)
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

        lines.forEach { line ->
            val match = inlineUnitBeforeReference.matchEntire(line) ?: return@forEach
            add(
                nameRaw = match.groupValues[1],
                resultRaw = match.groupValues[2] + match.groupValues[3],
                referenceRaw = match.groupValues[5],
                unitRaw = match.groupValues[4],
                source = line
            )
        }

        // A number of laboratory exports use tab-separated columns in the order
        // Test | Result | Unit | Reference, rather than the order used above.
        lines.forEach { line ->
            val cells = line.split('\t').map(String::trim).filter(String::isNotEmpty)
            if (cells.size < 2) return@forEach
            val resultIndex = cells.indexOfFirst { numericResult.matches(it) || it.lowercase() in qualitativeValues }
            if (resultIndex <= 0) return@forEach
            val name = cells.take(resultIndex).joinToString(" ")
            val result = cells[resultIndex]
            val tail = cells.drop(resultIndex + 1)
            val referenceCell = tail.firstOrNull { reference(it) != null }
            val unit = if (result.lowercase() in qualitativeValues) "" else
                tail.firstOrNull { it != referenceCell && it.length <= 24 }.orEmpty()
            add(name, result, referenceCell, unit, line)
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
