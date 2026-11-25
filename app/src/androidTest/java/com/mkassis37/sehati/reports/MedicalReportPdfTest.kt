package com.mkassis37.sehati.reports

import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicalReportPdfTest {
    @Test
    fun createsReadableMultipageReportWithArabicText() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = File.createTempFile("synthetic-report", ".pdf", context.cacheDir)
        try {
            val syntheticRows = List(100) {
                ReportRecord(ReportSection.HISTORY, "2026-11-25", "بيانات اختبار اصطناعية لسجل صحي تجريبي $it")
            }
            val draft = prepareReport("فرد اصطناعي", ReportType.TIMELINE, "", "", ReportType.TIMELINE.sections, syntheticRows)
            target.outputStream().use { writeMedicalReportPdf(draft, it) }
            val descriptor = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { assertTrue(it.pageCount > 1) }
        } finally {
            target.delete()
        }
    }
}
