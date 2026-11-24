package com.mkassis37.sehati

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Selects a laboratory PDF, renders its first page for visual verification,
 * and performs on-device OCR. OCR output is only a review aid; it is never saved automatically.
 */
@Composable
fun LabPdfReader(onExtractedText: (String) -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var extractedText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("لم يتم اختيار تقرير PDF بعد.") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            extractedText = ""
            pageBitmap = null
            status = "جارٍ فتح التقرير..."
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        busy = true
        try {
            val bitmapAndCount = withContext(Dispatchers.IO) {
                val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("تعذر فتح ملف PDF")
                descriptor.use { parcel ->
                    PdfRenderer(parcel).use { renderer ->
                        require(renderer.pageCount > 0) { "ملف PDF لا يحتوي صفحات." }
                        val page = renderer.openPage(0)
                        try {
                            val width = (page.width * 2).coerceAtMost(1800)
                            val height = (page.height * 2).coerceAtMost(2400)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap to renderer.pageCount
                        } finally { page.close() }
                    }
                }
            }
            pageBitmap = bitmapAndCount.first
            pageCount = bitmapAndCount.second
            val image = InputImage.fromBitmap(bitmapAndCount.first, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { result ->
                    extractedText = result.text
                    status = if (result.text.isBlank())
                        "تم عرض الصفحة، لكن لم يُستخرج نص واضح. راجع التقرير بصريًا وأدخل القيم يدويًا."
                    else "تم استخراج نص من الصفحة الأولى. راجعه مقابل صورة التقرير."
                    onExtractedText(result.text)
                    busy = false
                }
                .addOnFailureListener {
                    status = "تعذّر استخراج النص آليًا. يمكنك معاينة PDF وإدخال النتائج يدويًا."
                    busy = false
                }
        } catch (error: Exception) {
            status = "تعذّر فتح PDF: ${error.message ?: "ملف غير صالح"}"
            busy = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("إضافة تقرير مختبر PDF", style = MaterialTheme.typography.titleMedium)
            Text("اختر التقرير الأصلي لمعاينة الصفحة الأولى واستخراج النص محليًا على الجهاز. لا تُحفظ أي نتيجة تلقائيًا.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("اختيار ملف PDF") }
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (busy) CircularProgressIndicator()
            pageBitmap?.let { bitmap ->
                Text("معاينة التقرير — الصفحة 1 من $pageCount", style = MaterialTheme.typography.titleSmall)
                Image(bitmap.asImageBitmap(), contentDescription = "معاينة الصفحة الأولى من تقرير المختبر PDF", modifier = Modifier.fillMaxWidth())
            }
            if (extractedText.isNotBlank()) {
                Text("النص المستخرج للمراجعة", style = MaterialTheme.typography.titleSmall)
                Text(extractedText, style = MaterialTheme.typography.bodyMedium)
                Text("تنبيه: قد يخطئ OCR في قراءة الأرقام أو الوحدات. اعتمد القيم بعد مطابقتها بصريًا مع التقرير.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
