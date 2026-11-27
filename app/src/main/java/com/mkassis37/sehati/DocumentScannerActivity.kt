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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
        private const val MAX_IMAGE_DIMENSION = 2200
    }

    private var cameraPermissionGranted by mutableStateOf(false)
    private var cameraPermissionDenied by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var selectedFilter by mutableStateOf(DocumentScanFilter.COLOR)
    private var imageCapture by mutableStateOf<ImageCapture?>(null)
    private val pages = mutableStateListOf<ScannedPage>()
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
                    onRemovePage = { index ->
                        if (index in pages.indices && !busy) {
                            pages.removeAt(index).file.delete()
                        }
                    },
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
        if (busy || pages.size >= MAX_PAGES) return

        busy = true
        errorMessage = ""
        val rawFile = File.createTempFile("sehati-capture-", ".jpg", medicalShareDirectory(this))
        val output = ImageCapture.OutputFileOptions.Builder(rawFile).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    lifecycleScope.launch {
                        var processedFile: File? = null
                        try {
                            val created = withContext(Dispatchers.Default) {
                                val bitmap = processDocumentImage(rawFile, selectedFilter)
                                try {
                                    val target = File.createTempFile(
                                        "sehati-page-",
                                        ".jpg",
                                        medicalShareDirectory(this@DocumentScannerActivity)
                                    )
                                    try {
                                        FileOutputStream(target).use { stream ->
                                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 93, stream)) {
                                                "تعذر ضغط الصفحة."
                                            }
                                        }
                                        target
                                    } catch (error: Exception) {
                                        target.delete()
                                        throw error
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            processedFile = created
                            val thumb = withContext(Dispatchers.Default) {
                                decodeThumbnail(created)
                            }
                            pages.add(ScannedPage(created, thumb))
                            processedFile = null
                            if (pages.size >= MAX_PAGES) {
                                errorMessage = "وصلت إلى الحد الأقصى: $MAX_PAGES صفحة."
                            }
                        } catch (_: Exception) {
                            errorMessage = "تعذرت معالجة الصورة. حاول تصوير الصفحة بإضاءة أوضح."
                        } finally {
                            rawFile.delete()
                            processedFile?.delete()
                            busy = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    rawFile.delete()
                    busy = false
                    errorMessage = "تعذر التقاط الصورة. حاول مرة أخرى."
                }
            }
        )
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
        pages.forEach { page ->
            page.file.delete()
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

    private data class ScannedPage(val file: File, val thumbnail: Bitmap)

    private fun decodeThumbnail(file: File): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("تعذر إنشاء معاينة الصفحة.")
    }

    private fun processDocumentImage(file: File, filter: DocumentScanFilter): Bitmap {
        val decoded = decodeOrientedBitmap(file)
        val sourceBitmap = resizeBitmap(decoded, MAX_IMAGE_DIMENSION)
        if (sourceBitmap !== decoded) decoded.recycle()

        val source = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val warped = Mat()
        val filtered = Mat()
        try {
            Utils.bitmapToMat(sourceBitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)
            Imgproc.dilate(edges, edges, kernel)

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            val corners = findDocumentCorners(contours, source.cols(), source.rows())
            if (corners != null) {
                perspectiveCrop(source, warped, corners)
            } else {
                source.copyTo(warped)
            }
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
            blurred.release()
            edges.release()
            hierarchy.release()
            kernel.release()
            warped.release()
            filtered.release()
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
        imageWidth: Int,
        imageHeight: Int
    ): Array<Point>? {
        val minArea = imageWidth.toDouble() * imageHeight * 0.12
        return try {
            contours.asSequence()
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(25)
                .mapNotNull { contour ->
                    val area = Imgproc.contourArea(contour)
                    if (area < minArea) return@mapNotNull null
                    val curve = MatOfPoint2f(*contour.toArray())
                    val approximation = MatOfPoint2f()
                    try {
                        val perimeter = Imgproc.arcLength(curve, true)
                        Imgproc.approxPolyDP(curve, approximation, perimeter * 0.02, true)
                        if (approximation.total() != 4L) return@mapNotNull null
                        val points = approximation.toArray()
                        val polygon = MatOfPoint(*points)
                        try {
                            if (!Imgproc.isContourConvex(polygon)) return@mapNotNull null
                        } finally {
                            polygon.release()
                        }
                        orderCorners(points)
                    } finally {
                        curve.release()
                        approximation.release()
                    }
                }
                .firstOrNull()
        } finally {
            contours.forEach { it.release() }
        }
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
    busy: Boolean,
    errorMessage: String,
    selectedFilter: DocumentScanFilter,
    onRequestPermission: () -> Unit,
    onFilterSelected: (DocumentScanFilter) -> Unit,
    onCaptureReady: (ImageCapture?) -> Unit,
    onCameraError: (String) -> Unit,
    onCapture: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ماسح المستندات", color = Color.White, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء", color = Color.White) }
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

            Text("الصفحات: $pageCount / 20", color = Color.White)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocumentScanFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (pageThumbnails.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pageThumbnails) { index, bitmap ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = "الصفحة ${index + 1}",
                                modifier = Modifier.size(64.dp)
                            )
                            TextButton(onClick = { onRemovePage(index) }, enabled = !busy) {
                                Text("حذف")
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
                OutlinedButton(
                    onClick = onCapture,
                    enabled = permissionGranted && !busy && pageCount < 20,
                    modifier = Modifier.weight(1f)
                ) { Text("تصوير صفحة") }
                Button(
                    onClick = onFinish,
                    enabled = pageCount > 0 && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp))
                    else Text("إنشاء PDF وحفظ")
                }
            }
        }
    }
}

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
