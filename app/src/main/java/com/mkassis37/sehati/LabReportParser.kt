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
    private val arabicTestNames = mapOf(
        "الهيموجلوبين" to "Hemoglobin", "الهيموغلوبين" to "Hemoglobin",
        "صورة الدم الكاملة" to "Complete Blood Count (CBC)", "تعداد الدم الكامل" to "Complete Blood Count (CBC)",
        "كريات الدم البيضاء" to "White Blood Cell Count (WBC)", "خلايا الدم البيضاء" to "White Blood Cell Count (WBC)",
        "كريات الدم الحمراء" to "Red Blood Cell Count (RBC)", "خلايا الدم الحمراء" to "Red Blood Cell Count (RBC)",
        "الصفائح الدموية" to "Platelet Count", "الصفائح" to "Platelet Count",
        "السكر الصائم" to "Fasting Blood Glucose", "سكر الدم الصائم" to "Fasting Blood Glucose",
        "السكر التراكمي" to "Hemoglobin A1c (HbA1c)", "الهيموجلوبين السكري" to "Hemoglobin A1c (HbA1c)",
        "فيتامين د" to "Vitamin D", "فيتامين ب١٢" to "Vitamin B12", "فيتامين ب12" to "Vitamin B12",
        "الكوليسترول الكلي" to "Total Cholesterol", "الكوليسترول" to "Cholesterol",
        "الدهون الثلاثية" to "Triglycerides", "الكرياتينين" to "Creatinine",
        "اليوريا" to "Urea", "حمض اليوريك" to "Uric Acid",
        "إنزيم الكبد alt" to "ALT", "انزيم الكبد alt" to "ALT",
        "إنزيم الكبد ast" to "AST", "انزيم الكبد ast" to "AST",
        "هرمون الغدة الدرقية tsh" to "Thyroid Stimulating Hormone (TSH)",
        "تحليل البول" to "Urinalysis", "البول" to "Urinalysis"
    )

    private fun canonicalTestName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        val normalized = cleaned.lowercase(java.util.Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي')
        return arabicTestNames.entries.firstOrNull { (arabic, _) ->
            val key = arabic.lowercase(java.util.Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
            normalized == key
        }?.value ?: cleaned
    }
    private val qualitativeValues = setOf(
        "positive", "negative", "pos", "neg", "nil", "hazy", "trace",
        "absent", "present", "clear", "yellow", "straw", "normal",
        "not detected", "not seen", "few", "many", "no growth",
        "إيجابي", "ايجابي", "سلبي", "موجب", "سالب", "عكر", "آثار", "صافي"
    )
    private val isoDate = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
    private val monthDate = Regex("""\b([A-Za-z]{3,9})\s+(\d{1,2}),\s*(\d{4})\b""")
    private val numericResult = Regex("""^([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?$""")
    private val numericRange = Regex("""^([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*[-–]\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$""")
    private val comparatorResult = Regex("""^(?:<=?|>=?)\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)$""")
    private val referenceWithUnit = Regex(
        """^((?:[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)\s*[-–]\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))|(?:<=?|>=?)\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*/\s*([A-Za-zµμ0-9^]+)$""",
        RegexOption.IGNORE_CASE
    )
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

        fun splitReferenceUnit(raw: String?): Pair<String?, String?> {
            val value = raw?.trim().orEmpty()
            val match = referenceWithUnit.matchEntire(value) ?: return value.takeIf { it.isNotBlank() } to null
            return match.groupValues[1] to "/" + match.groupValues[2]
        }

        fun add(nameRaw: String, resultRaw: String, referenceRaw: String?, unitRaw: String, source: String, flagRaw: String? = null) {
            val name = canonicalTestName(nameRaw.trim().trim(':', '-', '•'))
            val normalizedName = name.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
            // Reject OCR status/reference labels and page furniture as test names.
            val rejectedName = normalizedName in setOf(
                "normal", "abnormal", "high", "low", "reference", "reference range",
                "ref range", "test name", "result", "unit", "comment", "comments",
                "page", "report page", "normal range", "diabetic", "good control",
                "fair control", "poor control"
            ) || normalizedName.contains("normal ") ||
                normalizedName.matches(Regex("""^(?:page|report page)\s*[:#-]?\s*\d+.*$""")) ||
                normalizedName.matches(Regex("""^\d+(?:\s+\d+){0,2}$"""))
            if (rejectedName) return
            val resultText = resultRaw.trim()
            val result = numericResult.matchEntire(resultText)
            val value = result?.groupValues?.get(1)?.let(::numeric)
            val lowerResult = resultText.lowercase(java.util.Locale.ROOT)
            val qualitative = resultText.takeIf {
                lowerResult in qualitativeValues || numericRange.matches(resultText) ||
                    comparatorResult.matches(resultText)
            }
            if (value == null && qualitative == null) return
            val ref = reference(referenceRaw)
            val unit = unitRaw.trim()
            if (name.length !in 2..70 || name.none { it.isLetter() } ||
                unit.length > 24) return
            val candidate = LabReportCandidate(name, value, unit, ref?.first, ref?.second,
                flagRaw?.trim()?.takeIf { it.isNotEmpty() } ?: result?.groupValues?.get(2).orEmpty(), reportDate, source, qualitative)
            val key = listOf(name.lowercase(), value, qualitative?.lowercase(), unit,
                ref?.first, ref?.second, candidate.flag).joinToString("\u0000")
            found.putIfAbsent(key, candidate)
        }

        // Header-aware table parsing: supports reordered columns and common lab-specific labels.
        fun splitColumns(line: String): List<String> {
            val separator = when {
                line.contains('|') -> Regex("\\s*\\|\\s*")
                line.contains('\t') -> Regex("\\t+")
                Regex("\\s{2,}").containsMatchIn(line) -> Regex("\\s{2,}")
                else -> return emptyList()
            }
            return line.trim().split(separator).map(String::trim)
        }

        fun headerSchema(cells: List<String>): TableSchema? {
            if (cells.size < 3) return null
            fun norm(value: String) = value.lowercase(java.util.Locale.ROOT)
                .replace('.', ' ').replace(':', ' ').replace(Regex("\\s+"), " ").trim()
            val headers = cells.map(::norm)
            val testIndex = headers.indexOfFirst {
                it == "test" || it.contains("test name") || it.contains("procedure") ||
                    it.contains("investigation") || it.contains("analyte") || it.contains("parameter") ||
                    it.contains("اسم الفحص") || it == "الفحص" || it.contains("التحليل")
            }
            val refLowIndex = headers.indexOfFirst {
                (it.contains("reference") || it.contains("ref ") || it.contains("normal") || it.contains("limit")) &&
                    (it.contains("low") || it.contains("lower") || it.contains("minimum") ||
                        it.contains("min ") || it.contains("الحد الأدنى") || it.contains("أدنى"))
            }
            val refHighIndex = headers.indexOfFirst {
                (it.contains("reference") || it.contains("ref ") || it.contains("normal") || it.contains("limit")) &&
                    (it.contains("high") || it.contains("upper") || it.contains("maximum") ||
                        it.contains("max ") || it.contains("الحد الأعلى") || it.contains("أعلى"))
            }
            val refIndex = headers.indices.firstOrNull { index ->
                index != refLowIndex && index != refHighIndex &&
                    (headers[index].contains("reference") || headers[index].contains("ref range") ||
                        headers[index].contains("normal range") || headers[index].contains("المدى الطبيعي") ||
                        headers[index].contains("المعدل الطبيعي"))
            } ?: -1
            val unitIndex = headers.indexOfFirst {
                it == "unit" || it == "units" || it.contains("unit ") || it.contains("الوحدة")
            }
            val flagIndex = headers.indexOfFirst {
                it == "flag" || it == "abn" || it.contains("abnormal flag") ||
                    it.contains("علامة") || it.contains("الحالة")
            }
            val resultIndices = headers.indices.filter { index ->
                val h = headers[index]
                index != testIndex && index != refIndex && index != unitIndex && index != flagIndex &&
                    (h.contains("result") || h == "value" || h.contains("result value") ||
                        h.contains("normal result") || h.contains("abnormal result") ||
                        h == "normal" || h == "abnormal" || h.contains("النتيجة"))
            }
            if (testIndex < 0 || resultIndices.isEmpty()) return null
            return TableSchema(testIndex, resultIndices, refIndex, refLowIndex, refHighIndex, unitIndex, flagIndex, cells.size)
        }

        fun looksLikeResult(value: String): Boolean {
            val clean = value.trim()
            return numericResult.matches(clean) || numericRange.matches(clean) ||
                comparatorResult.matches(clean) || clean.lowercase(java.util.Locale.ROOT) in qualitativeValues
        }

        fun likelyUnit(value: String): Boolean {
            val v = value.trim()
            val lower = v.lowercase(java.util.Locale.ROOT)
            val knownStandaloneUnits = setOf(
                "fl", "pg", "sec", "secs", "second", "seconds", "mmhg",
                "ml", "l", "dl", "g", "mg", "ug", "mcg", "mmol", "umol",
                "nmol", "pmol", "mol", "meq", "iu", "miu", "uiu", "cells", "cell",
                "mosm/kg", "mosm/l", "copies"
            )
            val hasUnitSyntax = v.contains('/') || v.contains('%') ||
                v.contains('µ') || v.contains('μ') ||
                Regex("""^x?10\^?\d+/.+$""", RegexOption.IGNORE_CASE).matches(v)
            return v.length in 1..24 && (hasUnitSyntax || lower in knownStandaloneUnits) &&
                reference(v) == null && !looksLikeResult(v)
        }

        val consumedTableLines = mutableSetOf<Int>()
        var activeSchema: TableSchema? = null
        lines.forEachIndexed { lineIndex, line ->
            val cells = splitColumns(line)
            if (cells.size < 3) return@forEachIndexed
            val detected = headerSchema(cells)
            if (detected != null) {
                activeSchema = detected
                consumedTableLines += lineIndex
                return@forEachIndexed
            }
            val schema = activeSchema
            if (schema != null && cells.size >= schema.columnCount) {
                val row = cells.take(schema.columnCount)
                val testName = row.getOrNull(schema.testIndex).orEmpty()
                val resultCells = schema.resultIndices.asSequence()
                    .mapNotNull { row.getOrNull(it)?.takeIf(String::isNotBlank) }
                    .filter(::looksLikeResult).toList()
                // If both "normal" and "abnormal" result columns contain values,
                // the row is ambiguous. Keep it visible in the raw text, but do
                // not guess which value belongs in the medical record.
                consumedTableLines += lineIndex
                if (testName.isNotBlank() && resultCells.size == 1) {
                    val lowCell = row.getOrNull(schema.referenceLowIndex).orEmpty().trim()
                    val highCell = row.getOrNull(schema.referenceHighIndex).orEmpty().trim()
                    val refCellRaw = when {
                        lowCell.isNotBlank() && highCell.isNotBlank() -> "$lowCell - $highCell"
                        lowCell.isNotBlank() -> ">= $lowCell"
                        highCell.isNotBlank() -> "<= $highCell"
                        else -> row.getOrNull(schema.referenceIndex)?.takeIf(String::isNotBlank)
                    }
                    val (refCell, unitFromReference) = splitReferenceUnit(refCellRaw)
                    val unitCell = row.getOrNull(schema.unitIndex).orEmpty().ifBlank { unitFromReference.orEmpty() }
                    val flagCell = row.getOrNull(schema.flagIndex)
                    add(testName, resultCells.single(), refCell, unitCell, line, flagCell)
                    return@forEachIndexed
                }
            }

            // Headerless delimited row: infer cells conservatively by their content.
            if (schema == null) {
                val resultIndex = cells.indexOfFirst(::looksLikeResult)
                if (resultIndex > 0) {
                    val testName = cells.take(resultIndex).joinToString(" ").trim()
                    val tail = cells.drop(resultIndex + 1)
                    val refCandidate = tail.firstOrNull {
                        reference(it) != null || reference(splitReferenceUnit(it).first) != null
                    }
                    val (refCell, unitFromReference) = splitReferenceUnit(refCandidate)
                    val flagCell = tail.firstOrNull {
                        it.equals("H", true) || it.equals("L", true) ||
                            it.equals("A", true) || it.equals("*") || it.equals("ABN", true)
                    }
                    val unitCell = tail.firstOrNull { it != refCandidate && it != flagCell && likelyUnit(it) }
                        ?: unitFromReference.orEmpty()
                    if (testName.isNotBlank()) {
                        add(testName, cells[resultIndex], refCell, unitCell, line, flagCell)
                        consumedTableLines += lineIndex
                    }
                }
            }
        }

        // Some PDF generators expose a row as four or five consecutive cells in
        // varying order. Only accept a complete, unambiguous group; never guess
        // a missing unit or range from a neighboring test.
        var cellIndex = 0
        while (cellIndex + 3 < lines.size) {
            if ((cellIndex..(cellIndex + 3)).any { it in consumedTableLines }) {
                cellIndex++
                continue
            }
            val groupSize = if (cellIndex + 4 < lines.size &&
                (cellIndex..(cellIndex + 4)).none { it in consumedTableLines }) 5 else 4
            val group = lines.subList(cellIndex, cellIndex + groupSize)
            val resultCells = group.filter(::looksLikeResult)
            val refCells = group.filter { reference(it) != null }
            val unitCells = group.filter(::likelyUnit)
            val nameCells = group.filter { cell ->
                cell.isNotBlank() && !looksLikeResult(cell) && reference(cell) == null &&
                    !likelyUnit(cell) && !cell.equals("high", true) && !cell.equals("low", true)
            }
            if (resultCells.size == 1 && refCells.size == 1 && unitCells.size == 1 && nameCells.size == 1) {
                val flagCell = group.firstOrNull { it.equals("H", true) || it.equals("L", true) || it.equals("A", true) || it == "*" }
                add(nameCells.single(), resultCells.single(), refCells.single(), unitCells.single(),
                    group.joinToString(" | "), flagCell)
                consumedTableLines.addAll(cellIndex until cellIndex + groupSize)
                cellIndex += groupSize
            } else {
                cellIndex++
            }
        }

        // Typical PDF text layer: each cell appears on its own line.
        var i = 0
        while (i + 3 < lines.size) {
            if ((i..(i + 3)).any { it in consumedTableLines }) {
                i++
                continue
            }
            val name = lines[i]
            val result = lines[i + 1]
            val ref = lines[i + 2]
            val unit = lines[i + 3]
            val isHeader = name.contains("test name", true) || name.equals("result", true) ||
                name.contains("laboratory report", true) || name.contains("reference", true)
            val (referenceValue, unitFromReference) = splitReferenceUnit(ref)
            if (!isHeader && looksLikeResult(result) && reference(referenceValue) != null) {
                add(name, result, referenceValue, unit.ifBlank { unitFromReference.orEmpty() },
                    listOf(name, result, ref, unit).joinToString(" | "))
                i += 4
            } else {
                i++
            }
        }

        // OCR commonly returns a laboratory table row as one line:
        // Test | Result | Unit | Reference (or Test | Result | Reference | Unit).
        val resultUnitReference = Regex(
            """^(.+?)\s+([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([LH*])?\s+([A-Za-zµμ/%0-9'._/-]+)\s+((?:(?:[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)\s*[-–]\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))|(?:<=?|>=?)\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)))$""",
            RegexOption.IGNORE_CASE
        )
        val qualitativeRow = Regex(
            """^(.+?)\s+(Positive|Negative|Pos|Neg|Nil|Hazy|Trace|Absent|Present|Clear|Yellow|Straw|Normal|Not detected|Not seen|Few|Many|No growth|إيجابي|ايجابي|سلبي|موجب|سالب|عكر|آثار|صافي)\s+(.+?)$""",
            RegexOption.IGNORE_CASE
        )
        val inlinePrefixFlagResult = Regex(
            """^(.+?)\s+([LH*])\s+([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s+([A-Za-zµμ/%0-9'._/-]+)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val microscopyRangeRow = Regex(
            """^(.+?)\s+((?:[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)\s*[-–]\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)))\s+((?:[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)\s*[-–]\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)))(?:\s*/\s*([A-Za-zµμ0-9^]+))?$"""
        )
        val comparatorResultRow = Regex(
            """^(.+?)\s+(<=?|>=?)\s*([+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s+((?:[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)\s*[-–]\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))|(?:<=?|>=?)\s*[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+))$"""
        )

        lines.forEach { line ->
            inlinePrefixFlagResult.matchEntire(line)?.let { match ->
                val (refValue, unitFromReference) = splitReferenceUnit(match.groupValues[5])
                add(match.groupValues[1], match.groupValues[3], refValue,
                    match.groupValues[4].ifBlank { unitFromReference.orEmpty() }, line, match.groupValues[2])
            }
            resultUnitReference.matchEntire(line)?.let { match ->
                val (refValue, unitFromReference) = splitReferenceUnit(match.groupValues[5])
                add(match.groupValues[1], match.groupValues[2] + match.groupValues[3],
                    refValue, match.groupValues[4].ifBlank { unitFromReference.orEmpty() }, line)
            }
            microscopyRangeRow.matchEntire(line)?.let { match ->
                val unit = match.groupValues[4].takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()
                add(match.groupValues[1], match.groupValues[2], match.groupValues[3], unit, line)
            }
            comparatorResultRow.matchEntire(line)?.let { match ->
                add(match.groupValues[1], match.groupValues[2] + match.groupValues[3],
                    match.groupValues[4], "", line)
            }
            qualitativeRow.matchEntire(line)?.let { match ->
                add(match.groupValues[1], match.groupValues[2], null, "", line)
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
    private data class TableSchema(
        val testIndex: Int,
        val resultIndices: List<Int>,
        val referenceIndex: Int,
        val referenceLowIndex: Int,
        val referenceHighIndex: Int,
        val unitIndex: Int,
        val flagIndex: Int,
        val columnCount: Int
    )

}
