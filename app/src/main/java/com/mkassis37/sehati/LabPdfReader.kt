package com.mkassis37.sehati

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
fun LabPdfReader(onExtractedText: (String) -> Unit, onCandidateSelected: (LabReportCandidate, Uri) -> Unit, onSavePdf: suspend (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isImage by remember { mutableStateOf(false) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var extractedText by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<LabReportCandidate>>(emptyList()) }
    var status by remember { mutableStateOf("اختر صورة أو تقرير PDF من الهاتف.") }
    var busy by remember { mutableStateOf(false) }
    var savingPdf by remember { mutableStateOf(false) }
    var pdfSaved by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isImage = context.contentResolver.getType(uri)?.startsWith("image/") == true
            selectedUri = uri
            pageIndex = 0
            pageCount = 0
            pageBitmap?.recycle()
            pageBitmap = null
            extractedText = ""
            candidates = emptyList()
            pdfSaved = false
            status = "تم اختيار التقرير. جارٍ فحص صفحاته..."
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        busy = true
        try {
            val result = withContext(Dispatchers.IO) {
                if (isImage) {
                    val bitmap = readLabImage(context.contentResolver, uri)
                    return@withContext try {
                        1 to recognizeLabImage(context, bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("لم يتمكن أندرويد من فتح الملف. جرّبي حفظ PDF على الجهاز أولًا.")
                descriptor.use { parcel ->
                    require(parcel.statSize in 1..MAX_LAB_PDF_BYTES) {
                        "اختر ملف PDF محليًا لا يتجاوز 25 ميغابايت."
                    }
                    PdfRenderer(parcel).use { renderer ->
                        val pages = renderer.pageCount
                        require(pages in 1..MAX_LAB_PDF_PAGES) { "يدعم الاستيراد من صفحة واحدة إلى 40 صفحة." }
                        PDFBoxResourceLoader.init(context.applicationContext)
                        val pageTexts = context.contentResolver.openInputStream(uri)?.use { input ->
                            runCatching {
                                PDDocument.load(input).use { document ->
                                    val stripper = LabColumnAwareTextStripper()
                                    List(pages) { index ->
                                        ensureActive()
                                        stripper.startPage = index + 1
                                        stripper.endPage = index + 1
                                        stripper.getText(document).take(MAX_LAB_PAGE_TEXT)
                                    }
                                }
                            }.getOrElse {
                                if (it is CancellationException) throw it
                                emptyList()
                            }
                        }.orEmpty()
                        val text = StringBuilder()
                        val recognizer = lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
                        val tess = lazy { runCatching { createArabicTesseract(context) }.getOrNull() }
                        try {
                            for (index in 0 until pages) {
                                ensureActive()
                                val pageText = pageTexts.getOrElse(index) { "" }
                                text.append("\n\n--- الصفحة ${index + 1} ---\n").append(pageText)
                                renderer.openPage(index).use { page ->
                                    val bitmap = page.renderLabPage()
                                    try {
                                        text.append("\n").append(recognizeLabBitmap(
                                            recognizeArabic = {
                                                tess.value?.setImage(bitmap)
                                                tess.value?.getUTF8Text().orEmpty()
                                            },
                                            recognizeLatin = {
                                                recognizer.value.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text
                                            }
                                        ))
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            }
                        } finally {
                            if (tess.isInitialized()) tess.value?.recycle()
                            if (recognizer.isInitialized()) recognizer.value.close()
                        }
                        pages to text.toString().trim()
                    }
                }
            }
            pageCount = result.first
            extractedText = result.second
            candidates = LabReportParser.parse(extractedText)
            onExtractedText(extractedText)
            status = if (extractedText.isBlank())
                "تم فتح الملف، لكن لم يظهر نص قابل للقراءة. تحققي من وضوح الصفحات أو أدخلي النتائج يدويًا."
            else "تمت معاينة الصفحة الأولى وقراءة النص من $pageCount صفحة."
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                if (isImage) return@withContext readLabImage(context.contentResolver, uri)
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("تعذر فتح الملف")
                descriptor.use { parcel ->
                    PdfRenderer(parcel).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            page.renderLabPage()
                        }
                    }
                }
            }
            pageBitmap?.takeIf { it !== bitmap }?.recycle()
            pageBitmap = bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { status = "تعذر عرض الصفحة ${pageIndex + 1}." }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("قراءة تقرير المختبر", style = MaterialTheme.typography.titleMedium)
            Text(
                "تتم المعالجة على الجهاز دون إرسال التقرير إلى خادم خارجي. تتم قراءة كل الصفحات، وتبقى النتائج بحاجة إلى مراجعتك قبل إضافتها للملف الطبي.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }) { Text("اختيار صورة أو PDF") }
            if (selectedUri != null) {
                Button(enabled = !busy && !savingPdf && !pdfSaved, onClick = {
                    val uri = selectedUri ?: return@Button
                    scope.launch {
                        savingPdf = true
                        try { onSavePdf(uri); pdfSaved = true; status = "تم حفظ نسخة مشفرة من التقرير ضمن الملف الطبي." }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { status = "تعذر حفظ التقرير: ${error.message ?: "خطأ غير معروف"}" }
                        finally { savingPdf = false }
                    }
                }) { Text(if (pdfSaved) "تم حفظ التقرير" else if (savingPdf) "جارٍ حفظ التقرير..." else "حفظ الملف الأصلي") }
            }
            if (selectedUri != null) {
                Button(enabled = !busy && !savingPdf, onClick = {
                    val uri = selectedUri ?: return@Button
                    val mimeType = context.contentResolver.getType(uri)
                        ?: if (isImage) "image/*" else "application/pdf"
                    val send = Intent(Intent.ACTION_SEND).setType(mimeType)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "مشاركة تقرير المختبر"))
                    }.onFailure {
                        status = "تعذرت مشاركة الملف. احفظيه أولًا ثم حاولي مشاركته من الملفات."
                    }
                }) { Text("مشاركة التقرير الأصلي") }
            }
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
                            Text(listOfNotNull(candidate.qualitativeValue ?: candidate.value?.toString(), candidate.unit.takeIf { it.isNotBlank() }).joinToString(" "))
                            Text("المرجع المقروء: " + (candidate.referenceLow?.toString() ?: "—") + " إلى " + (candidate.referenceHigh?.toString() ?: "—"), style = MaterialTheme.typography.bodySmall)
                            Text("النص الأصلي: " + candidate.sourceLine, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { selectedUri?.let { onCandidateSelected(candidate, it) } }) { Text("مراجعة وإدخال هذه النتيجة") }
                        }
                    }
                }
            }
            if (extractedText.isNotBlank()) {
                Text("النص المستخرج من جميع الصفحات", style = MaterialTheme.typography.titleSmall)
                Text(extractedText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "تنبيه: قد يخطئ OCR في قراءة العربية والإنجليزية والأرقام والفواصل والوحدات. طابقي كل قيمة مع صورة التقرير قبل حفظها.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun readLabImage(resolver: android.content.ContentResolver, uri: Uri): Bitmap {
    resolver.openFileDescriptor(uri, "r")?.use {
        require(it.statSize in 1..MAX_LAB_PDF_BYTES) { "اختر صورة محلية لا تتجاوز 25 ميغابايت." }
    } ?: error("تعذر فتح الصورة.")
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    require(options.outWidth > 0 && options.outHeight > 0) { "تعذر قراءة أبعاد الصورة." }
    val edge = maxOf(options.outWidth, options.outHeight)
    options.inSampleSize = 1
    while (edge / options.inSampleSize > 2400) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("تعذر فك ترميز الصورة.")
}

private suspend fun recognizeLabImage(context: android.content.Context, bitmap: Bitmap): String {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val tess = runCatching { createArabicTesseract(context) }.getOrNull()
    try {
        return recognizeLabBitmap( recognizeArabic = {
            tess?.setImage(bitmap)
            tess?.getUTF8Text().orEmpty()
        }, recognizeLatin = {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text
        })
    } finally {
        tess?.recycle()
        recognizer.close()
    }
}

private suspend fun recognizeLabBitmap(
    recognizeArabic: () -> String,
    recognizeLatin: suspend () -> String
): String {
    val arabic = runCatching(recognizeArabic).getOrDefault("")
    // Keep the supplied bitmap alive until ML Kit finishes reading it.
    val latin = try { withContext(NonCancellable) { recognizeLatin() } }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { "" }
    val arabicLines = arabic.lineSequence()
        .filter { line -> line.any { it in '\u0600'..'\u06FF' } }
        .joinToString("\n")
    return latin.take(MAX_LAB_PAGE_TEXT) + "\n" + arabicLines.take(MAX_LAB_PAGE_TEXT)
}

private fun createArabicTesseract(context: android.content.Context): TessBaseAPI {
    val root = File(context.filesDir, "tesseract")
    val data = File(root, "tessdata")
    if (!data.exists()) data.mkdirs()
    val model = File(data, "ara.traineddata")
    if (!model.exists()) {
        context.assets.open("tessdata/ara.traineddata").use { input ->
            model.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return TessBaseAPI().also { api ->
        if (!api.init(root.absolutePath, "ara")) {
            api.recycle()
            throw IllegalStateException("تعذر تهيئة نموذج اللغة العربية")
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

private fun PdfRenderer.Page.renderLabPage(): Bitmap {
    val scale = minOf(2.0, 1800.0 / width, 2400.0 / height)
    val bitmap = Bitmap.createBitmap(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    try {
        bitmap.eraseColor(android.graphics.Color.WHITE)
        render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    } catch (error: Exception) {
        bitmap.recycle()
        throw error
    }
}

private const val MAX_LAB_PAGE_TEXT = 24_000
private const val MAX_LAB_PDF_BYTES = 25L * 1024 * 1024
private const val MAX_LAB_PDF_PAGES = 40
