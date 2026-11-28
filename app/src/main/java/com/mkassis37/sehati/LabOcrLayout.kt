package com.mkassis37.sehati

import com.google.mlkit.vision.text.Text
import java.util.Locale

internal data class LabOcrToken(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerY: Float get() = (top + bottom) / 2f
    val isArabic: Boolean get() = text.any { it in '\u0600'..'\u06FF' }
}

internal fun parseArabicHocrWords(hocr: String): List<LabOcrToken> {
    if (hocr.isBlank()) return emptyList()
    val wordSpans = Regex(
        """<span\b[^>]*class=['"][^'"]*ocrx_word[^'"]*['"][^>]*>.*?</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val boxPattern = Regex("""\bbbox\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)\b""")
    return wordSpans.findAll(hocr).mapNotNull { match ->
        val span = match.value
        val box = boxPattern.find(span)?.groupValues ?: return@mapNotNull null
        val rawText = span.substringAfter('>', "").substringBeforeLast("</span>", "")
        val text = rawText.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace(Regex("[\u200e\u200f\u202a-\u202e]"), "").trim()
        if (text.isBlank() || text.none { it in '\u0600'..'\u06FF' || it in '\u0660'..'\u0669' }) {
            return@mapNotNull null
        }
        LabOcrToken(
            text = text,
            left = box[1].toIntOrNull() ?: return@mapNotNull null,
            top = box[2].toIntOrNull() ?: return@mapNotNull null,
            right = box[3].toIntOrNull() ?: return@mapNotNull null,
            bottom = box[4].toIntOrNull() ?: return@mapNotNull null
        )
    }.toList()
}

internal fun extractLatinOcrTokens(result: Text): List<LabOcrToken> =
    result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
        val box = element.boundingBox ?: return@mapNotNull null
        val value = element.text.trim()
        if (value.isBlank() || value.any { it in '\u0600'..'\u06FF' }) return@mapNotNull null
        if (value.none { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() || it == 'µ' || it == 'μ' || it == '%' }) {
            return@mapNotNull null
        }
        LabOcrToken(value, box.left, box.top, box.right, box.bottom)
    }

internal fun mergeLabOcrTokens(tokens: List<LabOcrToken>): String {
    if (tokens.isEmpty()) return ""
    val rows = mutableListOf<MutableList<LabOcrToken>>()
    tokens.sortedBy { it.centerY }.forEach { token ->
        val row = rows.minByOrNull { existing ->
            val center = existing.map { it.centerY }.average().toFloat()
            kotlin.math.abs(center - token.centerY)
        }?.takeIf { existing ->
            val center = existing.map { it.centerY }.average().toFloat()
            val typicalHeight = existing.map { it.height }.sorted()[existing.size / 2]
            val verticalOverlap = existing.maxOf { candidate ->
                (minOf(candidate.bottom, token.bottom) - maxOf(candidate.top, token.top)).coerceAtLeast(0)
            }
            val minimumHeight = minOf(existing.minOf { it.height }, token.height).coerceAtLeast(1)
            verticalOverlap.toFloat() / minimumHeight >= 0.30f ||
                kotlin.math.abs(center - token.centerY) <= maxOf(typicalHeight, token.height) * 0.45f
        }
        if (row == null) rows += mutableListOf(token) else row += token
    }

    return rows.sortedBy { row -> row.map { it.centerY }.average() }.joinToString("\n") { row ->
        val arabicTokens = row.filter { it.isArabic }
        val rowMidpoint = row.map { (it.left + it.right) / 2f }.average().toFloat()
        val arabicMidpoint = arabicTokens.takeIf { it.isNotEmpty() }
            ?.map { (it.left + it.right) / 2f }?.average()?.toFloat()
        // Infer row direction from where Arabic text sits in the table, rather
        // than from the total number of Latin digits and unit tokens.
        val rightToLeft = arabicMidpoint != null && arabicMidpoint > rowMidpoint
        val ordered = if (rightToLeft) row.sortedByDescending { it.left }
            else row.sortedBy { it.left }
        val typicalHeight = ordered.map { it.height }.sorted()[ordered.size / 2].coerceAtLeast(1)
        buildString {
            var previous: LabOcrToken? = null
            ordered.forEach { token ->
                previous?.let { prior ->
                    val gap = if (rightToLeft) prior.left - token.right
                        else token.left - prior.right
                    when {
                        gap >= typicalHeight * 1.8f -> append('\t')
                        gap >= typicalHeight * 0.28f -> append(' ')
                    }
                }
                append(token.text)
                previous = token
            }
        }
    }.trim()
}

internal data class ArabicHocrResult(val hocr: String, val fallbackText: String = "")

internal fun combineLabOcr(
    arabic: ArabicHocrResult?,
    latin: Text?
): String {
    val positioned = buildList {
        arabic?.let { addAll(parseArabicHocrWords(it.hocr)) }
        latin?.let { addAll(extractLatinOcrTokens(it)) }
    }
    if (positioned.isNotEmpty()) return mergeLabOcrTokens(positioned)
    val fallback = arabic?.fallbackText.orEmpty()
    val arabicLines = fallback.lineSequence()
        .filter { line -> line.any { it in '\u0600'..'\u06FF' } }
        .joinToString("\n")
    return listOf(latin?.text.orEmpty(), arabicLines).filter { it.isNotBlank() }.joinToString("\n")
}
