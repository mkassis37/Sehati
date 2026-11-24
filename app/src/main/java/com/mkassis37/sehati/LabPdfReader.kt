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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext

/**
 * Local PDF viewer and OCR reader. Every page is rendered and processed on-device.
 * OCR text is presented for human verification and is never written to the medical record automatically.
 */
@Composable
fun LabPdfReader(onExtractedText: (String) -> Unit, onCandidateSelected: (LabReportCandidate) -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var extractedText by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<LabReportCandidate>>(emptyList()) }
    var status by remember { mutableStateOf("اختاري تقرير PDF من الهاتف.") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            pageIndex = 0
            pageCount = 0
            pageBitmap = null
            extractedText = ""
            candidates = emptyList()
            status = "تم اختيار التقرير. جارٍ فحص صفحاته..."
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        busy = true
        try {
            val result = withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("لم يتمكن أندرويد من فتح الملف. جرّبي حفظ PDF على الجهاز أولًا.")
                descriptor.use { parcel ->
                    PdfRenderer(parcel).use { renderer ->
                        require(renderer.pageCount > 0) { "ملف PDF لا يحتوي صفحات." }
                        val pages = renderer.pageCount
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        val text = StringBuilder()
                        var firstBitmap: Bitmap? = null
                        for (index in 0 until pages) {
                            renderer.openPage(index).use { page ->
                                val scale = minOf(2.0, 1800.0 / page.width, 2400.0 / page.height)
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * scale).toInt().coerceAtLeast(1),
                                    (page.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                if (index == 0) firstBitmap = bitmap
                                val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
                                text.append("\n\n--- الصفحة ${index + 1} ---\n")
                                    .append(recognized.text)
                                if (index != 0) bitmap.recycle()
                            }
                        }
                        recognizer.close()
                        Triple(firstBitmap, pages, text.toString().trim())
                    }
                }
            }
            pageBitmap = result.first
            pageCount = result.second
            extractedText = result.third
            candidates = LabReportParser.parse(result.third)
            onExtractedText(result.third)
            status = if (result.third.isBlank())
                "تم فتح PDF، لكن لم يظهر نص قابل للقراءة. تحققي من وضوح الصفحات أو أدخلي النتائج يدويًا."
            else "تمت معاينة الصفحة الأولى وقراءة النص من $pageCount صفحة."
        } catch (error: Exception) {
            status = "تعذرت قراءة هذا الملف: ${error.message ?: "صيغة غير مدعومة"}. جرّبي تنزيله إلى الهاتف ثم اختياره مجددًا."
        } finally {
            busy = false
        }
    }

    LaunchedEffect(selectedUri, pageIndex, pageCount) {
        val uri = selectedUri ?: return@LaunchedEffect
        if (pageCount == 0) return@LaunchedEffect
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("تعذر فتح الملف")
                descriptor.use { parcel ->
                    PdfRenderer(parcel).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val scale = minOf(2.0, 1800.0 / page.width, 2400.0 / page.height)
                            Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
            pageBitmap?.takeIf { it !== bitmap }?.recycle()
            pageBitmap = bitmap
        } catch (_: Exception) { status = "تعذر عرض الصفحة ${pageIndex + 1}." }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("قراءة تقرير المختبر PDF", style = MaterialTheme.typography.titleMedium)
            Text(
                "تتم المعالجة على الجهاز دون إرسال التقرير إلى خادم خارجي. تتم قراءة كل الصفحات، وتبقى النتائج بحاجة إلى مراجعتك قبل إضافتها للملف الطبي.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("اختيار تقرير PDF") }
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (busy) CircularProgressIndicator()
            pageBitmap?.let { bitmap ->
                Text("معاينة الصفحة ${pageIndex + 1} من $pageCount", style = MaterialTheme.typography.titleSmall)
                Image(bitmap.asImageBitmap(), contentDescription = "معاينة صفحة من تقرير المختبر", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = pageIndex > 0, onClick = { pageIndex-- }) { Text("الصفحة السابقة") }
                    Button(enabled = pageIndex < pageCount - 1, onClick = { pageIndex++ }) { Text("الصفحة التالية") }
                }
            }
            if (candidates.isNotEmpty()) {
                Text("نتائج مرشحة للمراجعة (${candidates.size})", style = MaterialTheme.typography.titleSmall)
                Text("هذه اقتراحات آلية أولية وليست نتائج معتمدة. افتحي كل نتيجة وراجعيها مع التقرير قبل الحفظ.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                candidates.forEach { candidate ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(candidate.testName, style = MaterialTheme.typography.titleSmall)
                            Text(candidate.value.toString() + " " + candidate.unit)
                            Text("المرجع المقروء: " + (candidate.referenceLow?.toString() ?: "—") + " إلى " + (candidate.referenceHigh?.toString() ?: "—"), style = MaterialTheme.typography.bodySmall)
                            Text("النص الأصلي: " + candidate.sourceLine, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onCandidateSelected(candidate) }) { Text("مراجعة وإدخال هذه النتيجة") }
                        }
                    }
                }
            }
            if (extractedText.isNotBlank()) {
                Text("النص المستخرج من جميع الصفحات", style = MaterialTheme.typography.titleSmall)
                Text(extractedText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "تنبيه: قد يخطئ OCR في قراءة الأرقام والفواصل والوحدات، كما أن محرك القراءة الحالي يتعرف أساسًا على النص اللاتيني. طابقي كل قيمة مع صورة التقرير قبل حفظها.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
