package com.mkassis37.sehati

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.exifinterface.media.ExifInterface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.mkassis37.sehati.data.medicalShareDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Scalar
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class DocumentScanFilter(val label: String) {
    COLOR("ملون"),
    GRAYSCALE("رمادي"),
    BLACK_WHITE("أبيض وأسود")
}

internal class DocumentScannerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RESULT_FILE_NAME = "sehati_scanned_pdf_file_name"
        private const val MAX_PAGES = 20
        private const val MAX_OUTPUT_BYTES = 25L * 1024L * 1024L
        private const val MAX_IMAGE_DIMENSION = 1700
    }

    private var cameraPermissionGranted by mutableStateOf(false)
    private var cameraPermissionDenied by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var selectedFilter by mutableStateOf(DocumentScanFilter.COLOR)
    private var imageCapture by mutableStateOf<ImageCapture?>(null)
    private val pages = mutableStateListOf<ScannedPage>()
    private var draftSourceFile by mutableStateOf<File?>(null)
    private var draftBitmap by mutableStateOf<Bitmap?>(null)
    private var draftCorners by mutableStateOf(fullPageCorners())
    private var draftMessage by mutableStateOf("")
    private var editingPageIndex by mutableStateOf<Int?>(null)
    private var draftOwnsSourceFile by mutableStateOf(false)
    private var cameraInitializationError by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        cameraPermissionDenied = !granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        if (!OpenCVLoader.initLocal()) {
            finishWithError("تعذر تشغيل محرك معالجة الصور المدمج.")
            return
        }

        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            MaterialTheme {
                DocumentScannerUi(
                    permissionGranted = cameraPermissionGranted,
                    permissionDenied = cameraPermissionDenied,
                    pageCount = pages.size,
                    pageThumbnails = pages.map { it.thumbnail },
                    draftBitmap = draftBitmap,
                    draftCorners = draftCorners,
                    draftMessage = draftMessage,
                    busy = busy,
                    errorMessage = errorMessage.ifBlank { cameraInitializationError },
                    selectedFilter = selectedFilter,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onFilterSelected = { selectedFilter = it },
                    onCaptureReady = { imageCapture = it },
                    onCameraError = { cameraInitializationError = it },
                    onCapture = ::capturePage,
                    onEditPage = ::editPage,
                    onMovePage = ::movePage,
                    onRemovePage = { index ->
                        if (index in pages.indices && !busy) {
                            val removed = pages.removeAt(index)
                            removed.file.delete()
                            removed.sourceFile.delete()
                            removed.thumbnail.recycle()
                        }
                    },
                    onCornersChanged = { draftCorners = it },
                    onAutoDetect = ::autoDetectDraft,
                    onRotateDraft = ::rotateDraft,
                    onApplyCrop = { applyDraft(useCrop = true) },
                    onUseFullPage = { applyDraft(useCrop = false) },
                    onCancelDraft = ::cancelDraft,
                    onFinish = ::createPdfAndFinish,
                    onCancel = { setResult(RESULT_CANCELED); finish() }
                )
            }
        }
    }

    private fun capturePage() {
        val capture = imageCapture ?: run {
            errorMessage = "الكاميرا ليست جاهزة بعد."
            return
        }
        if (busy || pages.size >= MAX_PAGES || draftBitmap != null) return

        busy = true
        errorMessage = ""
        val rawFile = File.createTempFile("sehati-capture-", ".jpg", medicalShareDirectory(this))
        val output = ImageCapture.OutputFileOptions.Builder(rawFile).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    openDraft(rawFile, editingIndex = null, ownsSource = true)
                }

                override fun onError(exception: ImageCaptureException) {
                    rawFile.delete()
                    busy = false
                    errorMessage = "تعذر التقاط الصورة. حاول مرة أخرى."
                }
            }
        )
    }

    private fun openDraft(
        file: File,
        editingIndex: Int?,
        ownsSource: Boolean,
        initialCorners: List<Offset>? = null
    ) {
        busy = true
        errorMessage = ""
        lifecycleScope.launch {
            var normalizedFile: File? = null
            var preparedBitmap: Bitmap? = null
            try {
                val prepared = withContext(Dispatchers.Default) {
                    val decoded = decodeOrientedBitmap(file)
                    val bitmap = resizeBitmap(decoded, MAX_IMAGE_DIMENSION)
                    if (bitmap !== decoded) decoded.recycle()
                    var stableFile = file
                    if (ownsSource) {
                        stableFile = File.createTempFile(
                            "sehati-source-", ".jpg", medicalShareDirectory(this@DocumentScannerActivity)
                        )
                        try {
                            FileOutputStream(stableFile).use { output ->
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                                    "تعذر تجهيز الصورة للمراجعة."
                                }
                            }
                        } catch (error: Exception) {
                            stableFile.delete()
                            bitmap.recycle()
                            throw error
                        }
                    }
                    val detected = initialCorners ?: detectDocumentCorners(bitmap)?.map { point ->
                        Offset(
                            (point.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                            (point.y / bitmap.height).toFloat().coerceIn(0f, 1f)
                        )
                    }
                    Triple(stableFile, bitmap, detected ?: fullPageCorners())
                }
                normalizedFile = prepared.first
                preparedBitmap = prepared.second
                if (ownsSource && prepared.first != file) file.delete()
                draftSourceFile = prepared.first
                draftBitmap = prepared.second
                draftCorners = prepared.third
                draftOwnsSourceFile = ownsSource
                editingPageIndex = editingIndex
                draftMessage = if (initialCorners != null) {
                    "حرّكي الزوايا لتعديل القص، أو اختاري اكتشاف الحدود من جديد."
                } else if (prepared.third != fullPageCorners()) {
                    "اكتشفنا حدودًا مقترحة للورقة. راجعي الزوايا وعدّليها قبل الاعتماد."
                } else {
                    "لم تُكتشف الحدود تلقائيًا؛ اسحبي الدوائر الأربع حول الورقة يدويًا."
                }
                normalizedFile = null
                preparedBitmap = null
            } catch (_: Exception) {
                if (ownsSource) file.delete()
                normalizedFile?.delete()
                preparedBitmap?.recycle()
                errorMessage = "تعذر تجهيز الصورة للمراجعة. جرّبي تصويرها مرة أخرى."
            } finally {
                busy = false
            }
        }
    }

    private fun editPage(index: Int) {
        if (busy || draftBitmap != null || index !in pages.indices) return
        val page = pages[index]
        selectedFilter = page.filter
        openDraft(page.sourceFile, editingIndex = index, ownsSource = false, initialCorners = page.corners)
    }

    private fun movePage(from: Int, to: Int) {
        if (busy || from !in pages.indices || to !in pages.indices || from == to) return
        val page = pages.removeAt(from)
        pages.add(to, page)
    }

    private fun autoDetectDraft() {
        val bitmap = draftBitmap ?: return
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                val detected = withContext(Dispatchers.Default) { detectDocumentCorners(bitmap) }
                draftCorners = detected?.map { point ->
                    Offset(
                        (point.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                        (point.y / bitmap.height).toFloat().coerceIn(0f, 1f)
                    )
                } ?: fullPageCorners()
                draftMessage = if (detected == null) {
                    "لم أجد حدودًا مؤكدة. حدّدي الزوايا الأربع بنفسك، أو استخدمي الصورة كاملة."
                } else {
                    "تم تحديث حدود القص تلقائيًا. افحصيها قبل الحفظ."
                }
            } catch (_: Exception) {
                draftMessage = "تعذر اكتشاف الحدود. يمكنك تحديدها يدويًا."
            } finally {
                busy = false
            }
        }
    }

    private fun rotateDraft() {
        val bitmap = draftBitmap ?: return
        if (busy) return
        busy = true
        lifecycleScope.launch {
            var rotatedFile: File? = null
            try {
                val rotated = withContext(Dispatchers.Default) {
                    val matrix = Matrix().apply { postRotate(90f) }
                    val outputBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    val target = File.createTempFile(
                        "sehati-rotated-", ".jpg", medicalShareDirectory(this@DocumentScannerActivity)
                    )
                    try {
                        FileOutputStream(target).use { output ->
                            check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                                "تعذر تدوير الصورة."
                            }
                        }
                        target to outputBitmap
                    } catch (error: Exception) {
                        target.delete()
                        outputBitmap.recycle()
                        throw error
                    }
                }
                rotatedFile = rotated.first
                if (draftOwnsSourceFile) draftSourceFile?.delete()
                draftSourceFile = rotated.first
                draftOwnsSourceFile = true
                bitmap.recycle()
                draftBitmap = rotated.second
                draftCorners = fullPageCorners()
                draftMessage = "دُوّرت الصورة. حدّدي زوايا الورقة من جديد."
                rotatedFile = null
            } catch (_: Exception) {
                draftMessage = "تعذر تدوير الصورة."
            } finally {
                rotatedFile?.delete()
                busy = false
            }
        }
    }

    private fun applyDraft(useCrop: Boolean) {
        val sourceFile = draftSourceFile ?: return
        val bitmap = draftBitmap ?: return
        if (busy || pages.size >= MAX_PAGES && editingPageIndex == null) return
        val chosenCorners = if (useCrop) draftCorners.toList() else null
        val savedCorners = if (useCrop) draftCorners.toList() else fullPageCorners()
        val filter = selectedFilter
        val targetIndex = editingPageIndex
        busy = true
        errorMessage = ""
        lifecycleScope.launch {
            var createdFile: File? = null
            var thumbnail: Bitmap? = null
            try {
                val created = withContext(Dispatchers.Default) {
                    val processed = processDocumentImage(sourceFile, filter, chosenCorners)
                    try {
                        val target = File.createTempFile(
                            "sehati-page-", ".jpg", medicalShareDirectory(this@DocumentScannerActivity)
                        )
                        try {
                            FileOutputStream(target).use { output ->
                                check(processed.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                                    "تعذر ضغط الصفحة."
                                }
                            }
                            target
                        } catch (error: Exception) {
                            target.delete()
                            throw error
                        }
                    } finally {
                        processed.recycle()
                    }
                }
                createdFile = created
                val thumb = withContext(Dispatchers.Default) { decodeThumbnail(created) }
                thumbnail = thumb
                val replacement = ScannedPage(sourceFile, created, thumb, savedCorners, filter)
                if (targetIndex == null) {
                    pages.add(replacement)
                } else {
                    val old = pages.getOrNull(targetIndex)
                        ?: throw IllegalStateException("الصفحة التي نعدّلها لم تعد موجودة.")
                    pages[targetIndex] = replacement
                    old.file.delete()
                    old.thumbnail.recycle()
                    if (old.sourceFile != sourceFile) old.sourceFile.delete()
                }
                draftOwnsSourceFile = false
                clearDraft(deleteOwnedSource = false)
                createdFile = null
                thumbnail = null
                errorMessage = if (pages.size >= MAX_PAGES) "وصلت إلى الحد الأقصى: $MAX_PAGES صفحة." else ""
            } catch (_: Exception) {
                errorMessage = "تعذر تجهيز الصفحة. عدّلي القص أو اختاري الصورة كاملة ثم حاولي مجددًا."
            } finally {
                createdFile?.delete()
                thumbnail?.recycle()
                busy = false
            }
        }
    }

    private fun cancelDraft() {
        if (busy) return
        clearDraft(deleteOwnedSource = true)
    }

    private fun clearDraft(deleteOwnedSource: Boolean) {
        val source = draftSourceFile
        if (deleteOwnedSource && draftOwnsSourceFile) source?.delete()
        draftBitmap?.recycle()
        draftBitmap = null
        draftSourceFile = null
        draftCorners = fullPageCorners()
        draftMessage = ""
        editingPageIndex = null
        draftOwnsSourceFile = false
    }

    private fun createPdfAndFinish() {
        if (busy || pages.isEmpty()) return
        busy = true
        errorMessage = ""
        lifecycleScope.launch {
            try {
                val pdf = withContext(Dispatchers.IO) {
                    val target = File.createTempFile(
                        "sehati-scan-${UUID.randomUUID()}-",
                        ".pdf",
                        medicalShareDirectory(this@DocumentScannerActivity)
                    )
                    try {
                        val document = PdfDocument()
                        try {
                            pages.forEachIndexed { index, page ->
                                val bitmap = BitmapFactory.decodeFile(page.file.absolutePath)
                                    ?: throw IllegalStateException("تعذر قراءة صفحة ممسوحة.")
                                try {
                                    val landscape = bitmap.width > bitmap.height
                                    val pageWidth = if (landscape) 842 else 595
                                    val pageHeight = if (landscape) 595 else 842
                                    val info = PdfDocument.PageInfo.Builder(
                                        pageWidth,
                                        pageHeight,
                                        index + 1
                                    ).create()
                                    val pdfPage = document.startPage(info)
                                    try {
                                        val scale = min(
                                            pageWidth.toFloat() / bitmap.width,
                                            pageHeight.toFloat() / bitmap.height
                                        )
                                        val drawWidth = bitmap.width * scale
                                        val drawHeight = bitmap.height * scale
                                        val left = (pageWidth - drawWidth) / 2f
                                        val top = (pageHeight - drawHeight) / 2f
                                        pdfPage.canvas.drawBitmap(
                                            bitmap,
                                            null,
                                            RectF(left, top, left + drawWidth, top + drawHeight),
                                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                        )
                                    } finally {
                                        document.finishPage(pdfPage)
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            FileOutputStream(target).use { output -> document.writeTo(output) }
                        } finally {
                            document.close()
                        }
                        require(target.length() in 1..MAX_OUTPUT_BYTES) {
                            "حجم ملف PDF الناتج أكبر من 25 MB."
                        }
                        target
                    } catch (error: Exception) {
                        target.delete()
                        throw error
                    }
                }
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_RESULT_FILE_NAME, pdf.name)
                )
                finish()
            } catch (_: Exception) {
                errorMessage = "تعذر إنشاء PDF. جرّب تقليل عدد الصفحات أو تصويرها بجودة أوضح."
                busy = false
            }
        }
    }

    override fun onDestroy() {
        clearDraft(deleteOwnedSource = true)
        pages.forEach { page ->
            page.file.delete()
            page.sourceFile.delete()
            page.thumbnail.recycle()
        }
        pages.clear()
        // The PDF is intentionally retained only after a successful result. The caller
        // immediately imports it into SecureMedicalDocumentStore and removes this cache copy.
        super.onDestroy()
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    private data class ScannedPage(
        val sourceFile: File,
        val file: File,
        val thumbnail: Bitmap,
        val corners: List<Offset>,
        val filter: DocumentScanFilter
    )

    private fun decodeThumbnail(file: File): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("تعذر إنشاء معاينة الصفحة.")
    }

    private fun processDocumentImage(
        file: File,
        filter: DocumentScanFilter,
        cropCorners: List<Offset>? = null
    ): Bitmap {
        val decoded = decodeOrientedBitmap(file)
        val sourceBitmap = resizeBitmap(decoded, MAX_IMAGE_DIMENSION)
        if (sourceBitmap !== decoded) decoded.recycle()

        val source = Mat()
        val gray = Mat()
        val warped = Mat()
        val filtered = Mat()
        try {
            Utils.bitmapToMat(sourceBitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            val corners = cropCorners?.map { point ->
                Point(
                    point.x.toDouble() * (source.cols() - 1),
                    point.y.toDouble() * (source.rows() - 1)
                )
            }?.toTypedArray()
            if (corners != null) perspectiveCrop(source, warped, orderCorners(corners))
            else source.copyTo(warped)

            applyFilter(warped, filtered, filter)
            val output = Bitmap.createBitmap(
                filtered.cols(),
                filtered.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(filtered, output)
            return output
        } finally {
            sourceBitmap.recycle()
            source.release()
            gray.release()
            warped.release()
            filtered.release()
        }
    }

    private fun detectDocumentCorners(bitmap: Bitmap): Array<Point>? {
        val source = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            return detectDocumentCorners(source, gray)
        } finally {
            source.release()
            gray.release()
        }
    }

    private fun detectDocumentCorners(source: Mat, gray: Mat): Array<Point>? {
        val blurred = Mat()
        val edges = Mat()
        val edgeHierarchy = Mat()
        val edgeContours = ArrayList<MatOfPoint>()
        val brightPaper = Mat()
        val paperKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(25.0, 25.0))
        val paperHierarchy = Mat()
        val paperContours = ArrayList<MatOfPoint>()
        var transferred = false
        try {
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)
            val edgeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            try {
                Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, edgeKernel)
            } finally {
                edgeKernel.release()
            }
            Imgproc.findContours(
                edges, edgeContours, edgeHierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            Imgproc.threshold(
                gray, brightPaper, 0.0, 255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )
            Imgproc.morphologyEx(brightPaper, brightPaper, Imgproc.MORPH_CLOSE, paperKernel)
            Imgproc.findContours(
                brightPaper, paperContours, paperHierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            val combined = ArrayList<MatOfPoint>(edgeContours.size + paperContours.size)
            combined.addAll(edgeContours)
            combined.addAll(paperContours)
            transferred = true
            return findDocumentCorners(combined, gray)
        } finally {
            if (!transferred) {
                edgeContours.forEach { it.release() }
                paperContours.forEach { it.release() }
            }
            blurred.release()
            edges.release()
            edgeHierarchy.release()
            brightPaper.release()
            paperKernel.release()
            paperHierarchy.release()
        }
    }

    private fun decodeOrientedBitmap(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ملف الصورة غير صالح." }
        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_IMAGE_DIMENSION * 2) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: throw IllegalArgumentException("ملف الصورة غير صالح.")
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun findDocumentCorners(
        contours: List<MatOfPoint>,
        gray: Mat
    ): Array<Point>? {
        val imageWidth = gray.cols()
        val imageHeight = gray.rows()
        val imageArea = imageWidth.toDouble() * imageHeight
        var bestCorners: Array<Point>? = null
        var bestScore = Double.NEGATIVE_INFINITY

        try {
            contours.sortedByDescending { Imgproc.contourArea(it) }.take(35).forEach { contour ->
                val area = Imgproc.contourArea(contour)
                val areaRatio = area / imageArea
                if (areaRatio !in 0.10..0.96) return@forEach

                val curve = MatOfPoint2f(*contour.toArray())
                val approximation = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(curve, true)
                    Imgproc.approxPolyDP(curve, approximation, perimeter * 0.025, true)
                    if (approximation.total() != 4L) return@forEach

                    val points = approximation.toArray()
                    val polygon = MatOfPoint(*points)
                    try {
                        if (!Imgproc.isContourConvex(polygon)) return@forEach
                        val ordered = orderCorners(points)

                        // Reject the camera frame/table outline: paper edges must have
                        // a visible margin from all four image boundaries.
                        val marginX = imageWidth * 0.012
                        val marginY = imageHeight * 0.012
                        if (ordered.any {
                            it.x <= marginX || it.x >= imageWidth - marginX ||
                                it.y <= marginY || it.y >= imageHeight - marginY
                        }) return@forEach

                        val width = (distance(ordered[0], ordered[1]) +
                            distance(ordered[3], ordered[2])) / 2.0
                        val height = (distance(ordered[0], ordered[3]) +
                            distance(ordered[1], ordered[2])) / 2.0
                        if (width <= 0.0 || height <= 0.0) return@forEach

                        // Common paper proportions: A-series and letter-like sheets,
                        // in portrait or landscape orientation.
                        val ratio = min(width / height, height / width)
                        if (ratio !in 0.38..1.02) return@forEach
                        val aspectScore = (1.0 - kotlin.math.abs(ratio - 0.707) / 0.45)
                            .coerceIn(0.0, 1.0)

                        val insideMask = Mat.zeros(imageHeight, imageWidth, CvType.CV_8UC1)
                        val expandedMask = Mat()
                        val outsideMask = Mat()
                        val ringKernel = Imgproc.getStructuringElement(
                            Imgproc.MORPH_RECT,
                            Size(25.0, 25.0)
                        )
                        try {
                            Imgproc.fillConvexPoly(insideMask, polygon, Scalar(255.0))
                            Imgproc.dilate(insideMask, expandedMask, ringKernel)
                            Core.subtract(expandedMask, insideMask, outsideMask)
                            val paperBrightness = Core.mean(gray, insideMask).`val`[0]
                            val backgroundBrightness = Core.mean(gray, outsideMask).`val`[0]
                            val contrast = paperBrightness - backgroundBrightness

                            // A true sheet should be brighter than the narrow band
                            // immediately outside it; a table/grid contour usually is not.
                            if (contrast < -8.0) return@forEach
                            val contrastScore = ((contrast + 8.0) / 55.0).coerceIn(0.0, 1.0)
                            val areaScore = ((areaRatio - 0.10) / 0.48).coerceIn(0.0, 1.0)
                            val oversizedPenalty = ((areaRatio - 0.72) / 0.18).coerceIn(0.0, 1.0) * 0.55
                            val score = aspectScore * 0.24 + areaScore * 0.20 +
                                contrastScore * 0.56 - oversizedPenalty

                            if (score > bestScore) {
                                bestScore = score
                                bestCorners = ordered
                            }
                        } finally {
                            insideMask.release()
                            expandedMask.release()
                            outsideMask.release()
                            ringKernel.release()
                        }
                    } finally {
                        polygon.release()
                    }
                } finally {
                    curve.release()
                    approximation.release()
                }
            }
        } finally {
            contours.forEach { it.release() }
        }

        return bestCorners
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.minBy { it.y - it.x }
        val bottomLeft = points.maxBy { it.y - it.x }
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun perspectiveCrop(source: Mat, output: Mat, corners: Array<Point>) {
        val topWidth = distance(corners[0], corners[1])
        val bottomWidth = distance(corners[3], corners[2])
        val leftHeight = distance(corners[0], corners[3])
        val rightHeight = distance(corners[1], corners[2])
        val width = max(topWidth, bottomWidth).roundToInt().coerceIn(400, 2600)
        val height = max(leftHeight, rightHeight).roundToInt().coerceIn(400, 3600)
        val inputPoints = MatOfPoint2f(*corners)
        val outputPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0),
            Point(0.0, height - 1.0)
        )
        val transform = Imgproc.getPerspectiveTransform(inputPoints, outputPoints)
        try {
            Imgproc.warpPerspective(
                source,
                output,
                transform,
                Size(width.toDouble(), height.toDouble()),
                Imgproc.INTER_CUBIC
            )
        } finally {
            inputPoints.release()
            outputPoints.release()
            transform.release()
        }
    }

    private fun distance(a: Point, b: Point): Double =
        hypot(a.x - b.x, a.y - b.y)

    private fun applyFilter(source: Mat, output: Mat, filter: DocumentScanFilter) {
        when (filter) {
            DocumentScanFilter.COLOR -> source.copyTo(output)
            DocumentScanFilter.GRAYSCALE -> {
                val gray = Mat()
                try {
                    Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.cvtColor(gray, output, Imgproc.COLOR_GRAY2RGBA)
                } finally {
                    gray.release()
                }
            }
            DocumentScanFilter.BLACK_WHITE -> {
                val gray = Mat()
                val clean = Mat()
                try {
                    Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.adaptiveThreshold(
                        gray,
                        clean,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,
                        31,
                        12.0
                    )
                    Imgproc.cvtColor(clean, output, Imgproc.COLOR_GRAY2RGBA)
                } finally {
                    gray.release()
                    clean.release()
                }
            }
        }
    }
}

@Composable
private fun DocumentScannerUi(
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    pageCount: Int,
    pageThumbnails: List<Bitmap>,
    draftBitmap: Bitmap?,
    draftCorners: List<Offset>,
    draftMessage: String,
    busy: Boolean,
    errorMessage: String,
    selectedFilter: DocumentScanFilter,
    onRequestPermission: () -> Unit,
    onFilterSelected: (DocumentScanFilter) -> Unit,
    onCaptureReady: (ImageCapture?) -> Unit,
    onCameraError: (String) -> Unit,
    onCapture: () -> Unit,
    onEditPage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onRemovePage: (Int) -> Unit,
    onCornersChanged: (List<Offset>) -> Unit,
    onAutoDetect: () -> Unit,
    onRotateDraft: () -> Unit,
    onApplyCrop: () -> Unit,
    onUseFullPage: () -> Unit,
    onCancelDraft: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ماسح المستندات", color = Color.White, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCancel, enabled = !busy && draftBitmap == null) {
                    Text("إلغاء", color = Color.White)
                }
            }

            if (permissionGranted) {
                CameraXPreview(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onCaptureReady = onCaptureReady,
                    onError = onCameraError
                )
            } else {
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Color(0xFF202124)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (permissionDenied) "يلزم السماح للكاميرا حتى نتمكن من مسح المستندات."
                            else "نحتاج إذن الكاميرا لبدء المسح.",
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRequestPermission) { Text("السماح بالكاميرا") }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الصفحات: $pageCount / 20", color = Color.White)
                Text("اضغطي على أي صفحة لتعديلها", color = Color(0xFFB8C7C5), style = MaterialTheme.typography.bodySmall)
            }

            if (pageThumbnails.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pageThumbnails) { index, bitmap ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.background(Color(0xFF202124), MaterialTheme.shapes.small).padding(4.dp)
                        ) {
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = "تعديل الصفحة ${index + 1}",
                                modifier = Modifier.size(66.dp).clickable { onEditPage(index) },
                                contentScale = ContentScale.Fit
                            )
                            Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onMovePage(index, index - 1) }, enabled = !busy && index > 0) {
                                    Text("←")
                                }
                                TextButton(onClick = { onMovePage(index, index + 1) }, enabled = !busy && index < pageThumbnails.lastIndex) {
                                    Text("→")
                                }
                                TextButton(onClick = { onRemovePage(index) }, enabled = !busy) {
                                    Text("حذف", color = Color(0xFFFFB4AB))
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage.isNotBlank()) {
                Text(errorMessage, color = Color(0xFFFFB4AB))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCapture,
                    enabled = permissionGranted && !busy && draftBitmap == null && pageCount < 20,
                    modifier = Modifier.weight(1f)
                ) { Text("●  تصوير صفحة") }
                Button(
                    onClick = onFinish,
                    enabled = pageCount > 0 && !busy && draftBitmap == null,
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp))
                    else Text("إنشاء PDF وحفظ")
                }
            }
        }
    }

    if (draftBitmap != null) {
        CropEditorDialog(
            bitmap = draftBitmap,
            corners = draftCorners,
            message = draftMessage,
            selectedFilter = selectedFilter,
            busy = busy,
            onCornersChanged = onCornersChanged,
            onAutoDetect = onAutoDetect,
            onRotate = onRotateDraft,
            onFilterSelected = onFilterSelected,
            onApplyCrop = onApplyCrop,
            onUseFullPage = onUseFullPage,
            onCancel = onCancelDraft
        )
    }
}

@Composable
private fun CropEditorDialog(
    bitmap: Bitmap,
    corners: List<Offset>,
    message: String,
    selectedFilter: DocumentScanFilter,
    busy: Boolean,
    onCornersChanged: (List<Offset>) -> Unit,
    onAutoDetect: () -> Unit,
    onRotate: () -> Unit,
    onFilterSelected: (DocumentScanFilter) -> Unit,
    onApplyCrop: () -> Unit,
    onUseFullPage: () -> Unit,
    onCancel: () -> Unit
) {
    val currentCorners by rememberUpdatedState(corners)
    val updateCorners by rememberUpdatedState(onCornersChanged)
    Dialog(
        onDismissRequest = { if (!busy) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF15191A)) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("مراجعة وقص الصفحة", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("اسحبي الدوائر الأربع إلى أطراف الورقة", color = Color(0xFFB8C7C5), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onCancel, enabled = !busy) { Text("رجوع", color = Color.White) }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Color(0xFF252B2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "الصورة الأصلية لتحديد حدود القص",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(bitmap.width, bitmap.height) {
                            var activeCorner = -1
                            detectDragGestures(
                                onDragStart = { position ->
                                    val rect = scannerImageRect(size.width, size.height, bitmap.width, bitmap.height)
                                    val handles = currentCorners.map { point ->
                                        Offset(rect.left + point.x * rect.width(), rect.top + point.y * rect.height())
                                    }
                                    val nearest = handles.indices.minByOrNull { index ->
                                        val dx = handles[index].x - position.x
                                        val dy = handles[index].y - position.y
                                        dx * dx + dy * dy
                                    } ?: -1
                                    activeCorner = if (nearest >= 0) {
                                        val dx = handles[nearest].x - position.x
                                        val dy = handles[nearest].y - position.y
                                        if (dx * dx + dy * dy <= 72f * 72f) nearest else -1
                                    } else -1
                                },
                                onDrag = { change, _ ->
                                    if (activeCorner >= 0) {
                                        val rect = scannerImageRect(size.width, size.height, bitmap.width, bitmap.height)
                                        if (rect.width() > 0f && rect.height() > 0f) {
                                            val point = Offset(
                                                ((change.position.x - rect.left) / rect.width()).coerceIn(0f, 1f),
                                                ((change.position.y - rect.top) / rect.height()).coerceIn(0f, 1f)
                                            )
                                            updateCorners(moveCropCorner(currentCorners, activeCorner, point))
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = { activeCorner = -1 },
                                onDragCancel = { activeCorner = -1 }
                            )
                        }
                    ) {
                        val rect = scannerImageRect(size.width, size.height, bitmap.width, bitmap.height)
                        val points = corners.map { point ->
                            Offset(rect.left + point.x * rect.width(), rect.top + point.y * rect.height())
                        }
                        if (points.size == 4) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(points[0].x, points[0].y)
                                lineTo(points[1].x, points[1].y)
                                lineTo(points[2].x, points[2].y)
                                lineTo(points[3].x, points[3].y)
                                close()
                            }
                            drawPath(path, Color(0xFF21D4C2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                            points.forEach { point ->
                                drawCircle(Color.Black, radius = 15.dp.toPx(), center = point)
                                drawCircle(Color(0xFF21D4C2), radius = 10.dp.toPx(), center = point)
                                drawCircle(Color.White, radius = 4.dp.toPx(), center = point)
                            }
                        }
                    }
                }

                Text(message, color = Color(0xFFCAD6D4), style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onAutoDetect, enabled = !busy) { Text("اكتشاف تلقائي") }
                    OutlinedButton(onClick = onRotate, enabled = !busy) { Text("تدوير 90°") }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentScanFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            enabled = !busy,
                            label = { Text(filter.label) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onUseFullPage,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("استخدام الصورة كاملة") }
                    Button(
                        onClick = onApplyCrop,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp))
                        else Text("اعتماد القص")
                    }
                }
                if (busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ تجهيز الصفحة...", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun scannerImageRect(viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int): RectF {
    val scale = min(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (viewWidth - width) / 2f
    val top = (viewHeight - height) / 2f
    return RectF(left, top, left + width, top + height)
}

private fun moveCropCorner(corners: List<Offset>, index: Int, point: Offset): List<Offset> {
    if (corners.size != 4 || index !in 0..3) return corners
    val result = corners.toMutableList()
    val gap = 0.025f
    val next = when (index) {
        0 -> Offset(
            point.x.coerceIn(0f, (corners[1].x - gap).coerceAtLeast(gap)),
            point.y.coerceIn(0f, (corners[3].y - gap).coerceAtLeast(gap))
        )
        1 -> Offset(
            point.x.coerceIn((corners[0].x + gap).coerceAtMost(1f - gap), 1f),
            point.y.coerceIn(0f, (corners[2].y - gap).coerceAtLeast(gap))
        )
        2 -> Offset(
            point.x.coerceIn((corners[3].x + gap).coerceAtMost(1f - gap), 1f),
            point.y.coerceIn((corners[1].y + gap).coerceAtMost(1f - gap), 1f)
        )
        else -> Offset(
            point.x.coerceIn(0f, (corners[2].x - gap).coerceAtLeast(gap)),
            point.y.coerceIn((corners[0].y + gap).coerceAtMost(1f - gap), 1f)
        )
    }
    result[index] = next
    return result
}

private fun fullPageCorners(): List<Offset> = listOf(
    Offset(0.015f, 0.015f), Offset(0.985f, 0.015f),
    Offset(0.985f, 0.985f), Offset(0.015f, 0.985f)
)

@Composable
private fun CameraXPreview(
    modifier: Modifier = Modifier,
    onCaptureReady: (ImageCapture?) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) {
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(targetRotation)
                        .build()
                    provider?.unbindAll()
                    provider?.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    onCaptureReady(imageCapture)
                } catch (_: Exception) {
                    onError("تعذر تهيئة الكاميرا على هذا الجهاز.")
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            onCaptureReady(null)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
