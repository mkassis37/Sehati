package com.mkassis37.sehati

import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Reconstructs PDF text lines while retaining wide horizontal gaps as cell
 * separators. This is useful for table PDFs whose visible borders are not
 * represented in the text layer. It does not infer data from border graphics.
 */
internal data class PositionedLabGlyph(
    val text: String,
    val left: Float,
    val width: Float,
    val height: Float
)

internal fun reconstructLabPdfLine(glyphs: List<PositionedLabGlyph>): String {
    if (glyphs.isEmpty()) return ""
    val ordered = glyphs.sortedBy { it.left }
    val heights = ordered.map { it.height.coerceAtLeast(1f) }.sorted()
    val typicalHeight = heights[heights.size / 2].coerceAtLeast(1f)

    return buildString {
        var previousRight: Float? = null
        ordered.forEach { glyph ->
            val prior = previousRight
            if (prior != null) {
                val gap = glyph.left - prior
                when {
                    gap >= typicalHeight * 1.5f -> append('\t')
                    gap >= typicalHeight * 0.28f -> append(' ')
                }
            }
            append(glyph.text)
            previousRight = maxOf(glyph.left + glyph.width, glyph.left)
        }
    }.trim()
}

internal class LabColumnAwareTextStripper : PDFTextStripper() {
    init {
        setSortByPosition(true)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        val glyphs = textPositions.mapNotNull { position ->
            val value = position.unicode?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            PositionedLabGlyph(
                text = value,
                left = position.xDirAdj,
                width = position.widthDirAdj.coerceAtLeast(0f),
                height = position.heightDir.coerceAtLeast(1f)
            )
        }
        val positionedLine = reconstructLabPdfLine(glyphs)
        super.writeString(positionedLine.ifBlank { text })
    }
}
