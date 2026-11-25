package com.mkassis37.sehati.reports

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.OutputStream

internal fun writeMedicalReportPdf(draft: ReportDraft, output: OutputStream) {
    val width = 595
    val height = 842
    val margin = 42
    val document = PdfDocument()
    try {
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        var y = margin
        fun nextPage() {
            document.finishPage(page)
            page = document.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNumber).create())
            y = margin
        }
        (draft.header() + draft.lines).forEach { line ->
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = if (line.heading) Color.rgb(15, 73, 76) else Color.rgb(21, 27, 29)
                textSize = if (line.heading) 16f else 12f
                textLocale = java.util.Locale.forLanguageTag("ar")
                typeface = Typeface.create("sans-serif", if (line.heading) Typeface.BOLD else Typeface.NORMAL)
            }
            val text = line.text
            var start = 0
            while (start < text.length) {
                if (y + 36 >= height - margin) nextPage()
                fun layout(end: Int) = StaticLayout.Builder.obtain(text, start, end, paint, width - margin * 2)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setLineSpacing(4f, 1f)
                    .build()
                val remaining = layout(text.length)
                val available = height - margin - y
                val lastLine = (0 until remaining.lineCount).lastOrNull { remaining.getLineBottom(it) <= available }
                if (lastLine == null) {
                    nextPage()
                    continue
                }
                val end = remaining.getLineEnd(lastLine)
                val printable = if (end == text.length) remaining else layout(end)
                page.canvas.save()
                page.canvas.translate(margin.toFloat(), y.toFloat())
                printable.draw(page.canvas)
                page.canvas.restore()
                y += printable.height + if (line.heading) 12 else 7
                start = end
            }
        }
        document.finishPage(page)
        document.writeTo(output)
    } finally {
        document.close()
    }
}
