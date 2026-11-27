package com.mkassis37.sehati

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.medicalShareDirectory
import com.mkassis37.sehati.data.SehatiDatabase
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PendingDocument(val memberId: Long, val uri: Uri)

internal data class DocumentLibraryActions(
    val pending: PendingDocument?,
    val pick: (Long) -> Unit,
    val capture: (Long) -> Unit,
    val scan: (Long) -> Unit,
    val cancel: () -> Unit,
    val save: suspend (DocumentCategory, String, DocumentLink?) -> Unit,
    val open: (MedicalDocument) -> Unit,
    val delete: suspend (MedicalDocument) -> Unit
)

@Composable
internal fun rememberDocumentLibrary(
    database: SehatiDatabase,
    store: SecureMedicalDocumentStore,
    returnToLibrary: (Long) -> Unit
): DocumentLibraryActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var owner by rememberSaveable { mutableStateOf<Long?>(null) }
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var openingPreview by remember { mutableStateOf(false) }
    var openedTemporaryPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun clearOpenedPreview() {
        // Clear state first so a failed cleanup can never block future previews.
        val name = openedTemporaryPath
        openedTemporaryPath = null
        if (name.isNullOrBlank() || File(name).name != name || !name.startsWith("sehati-preview-")) return

        // Only accept a generated filename and keep cleanup inside our private cache folder.
        val file = File(File(context.cacheDir, "medical-share"), name)
        runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            context.revokeUriPermission(contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Deletion is attempted even when URI permission revocation fails.
        runCatching { file.delete() }
    }
    val viewer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        clearOpenedPreview()
    }
    fun clear() {
        val capturedName = cameraPath
        cameraPath = null
        owner = null
        uri = null

        if (!capturedName.isNullOrBlank() &&
            File(capturedName).name == capturedName &&
            (capturedName.startsWith("sehati-camera-") || capturedName.startsWith("sehati-scan-"))
        ) {
            runCatching {
                File(File(context.cacheDir, "medical-share"), capturedName).delete()
            }
        }
    }
    fun complete(picked: Uri?) {
        val memberId = owner ?: return
        if (picked == null) clear() else uri = picked
        returnToLibrary(memberId)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::complete)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraPath?.let { File(medicalShareDirectory(context), it) }
        complete(if (success && file != null) FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file) else null)
    }
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val memberId = owner
        if (result.resultCode != Activity.RESULT_OK || memberId == null) {
            clear()
        } else {
            val scannedPdfUri = runCatching {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            }.getOrNull()

            if (scannedPdfUri == null) {
                clear()
                Toast.makeText(context, "لم يرجع الماسح ملف PDF صالحًا.", Toast.LENGTH_LONG).show()
            } else {
                scope.launch {
                    var stagedFile: File? = null
                    try {
                        val staged = withContext(Dispatchers.IO) {
                            val target = File.createTempFile("sehati-scan-", ".pdf", medicalShareDirectory(context))
                            try {
                                val source = context.contentResolver.openInputStream(scannedPdfUri)
                                    ?: throw java.io.IOException("تعذر قراءة المستند الممسوح.")
                                source.use { input ->
                                    FileOutputStream(target).use { output ->
                                        val buffer = ByteArray(16 * 1024)
                                        var copied = 0L
                                        try {
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                if (count == 0) continue
                                                copied += count
                                                require(copied <= MAX_SCANNED_DOCUMENT_BYTES) {
                                                    "المستند الممسوح أكبر من الحد المسموح (25 MB)."
                                                }
                                                output.write(buffer, 0, count)
                                            }
                                        } finally {
                                            buffer.fill(0)
                                        }
                                    }
                                }
                                target
                            } catch (error: Exception) {
                                target.delete()
                                throw error
                            }
                        }
                        stagedFile = staged
                        cameraPath = staged.name
                        uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            staged
                        )
                        returnToLibrary(memberId)
                        stagedFile = null // The staged file now belongs to the pending import flow.
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        clear()
                        Toast.makeText(
                            context,
                            "تعذر تجهيز المستند الممسوح للحفظ. تأكد أن حجمه لا يتجاوز 25 MB.",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        stagedFile?.delete()
                    }
                }
            }
        }
    }
    val pending = owner?.let { memberId -> uri?.let { PendingDocument(memberId, it) } }
    return DocumentLibraryActions(
        pending = pending,
        pick = { memberId ->
            if (owner == null) {
                owner = memberId
                try {
                    picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                } catch (_: Exception) {
                    clear()
                    Toast.makeText(context, "تعذر فتح منتقي الملفات.", Toast.LENGTH_SHORT).show()
                }
            }
        },
        capture = { memberId ->
            if (owner == null) {
                try {
                    val photo = File.createTempFile("sehati-camera-", ".jpg", medicalShareDirectory(context))
                    cameraPath = photo.name
                    owner = memberId
                    camera.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", photo))
                } catch (_: Exception) {
                    clear()
                    Toast.makeText(context, "تعذر تشغيل الكاميرا.", Toast.LENGTH_SHORT).show()
                }
            }
        },
        scan = { memberId ->
            if (owner == null) {
                val activity = context.findActivity()
                if (activity == null) {
                    Toast.makeText(context, "تعذر الوصول إلى شاشة المسح.", Toast.LENGTH_SHORT).show()
                } else {
                    owner = memberId
                    scanner.getStartScanIntent(activity)
                        .addOnSuccessListener { intentSender ->
                            try {
                                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            } catch (_: Exception) {
                                clear()
                                Toast.makeText(context, "تعذر تشغيل ماسح المستندات.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener {
                            clear()
                            Toast.makeText(
                                context,
                                "تعذر تشغيل ماسح المستندات. تحقق من خدمات Google Play واتصال الإنترنت عند الاستخدام الأول.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        },
        cancel = ::clear,
        save = { type, date, link ->
            checkNotNull(pending)
            withContext(Dispatchers.IO + NonCancellable) {
                val stagedName = cameraPath
                val name = when {
                    stagedName?.startsWith("sehati-scan-") == true -> "مستند ممسوح - $date.pdf"
                    stagedName?.startsWith("sehati-camera-") == true -> "صورة طبية - $date.jpg"
                    else -> context.contentResolver.query(pending.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "مرفق طبي"
                }
                val document = store.save(pending.uri, pending.memberId, name)
                try {
                    database.addLinkedDocument(document.copy(
                        category = type.name,
                        documentDate = date,
                        relatedAppointmentId = link?.takeIf { it.kind == "appointment" }?.id,
                        relatedLabResultId = link?.takeIf { it.kind == "lab" }?.id,
                        relatedProcedureId = link?.takeIf { it.kind == "procedure" }?.id
                    ))
                } catch (error: Exception) {
                    store.delete(document)
                    throw error
                }
            }
            clear()
        },
        open = { document ->
            if (!openingPreview && openedTemporaryPath == null) scope.launch {
                openingPreview = true
                var pendingFile: File? = null
                try {
                    val file = withContext(NonCancellable) { store.createTemporaryFile(document) }
                    pendingFile = file
                    currentCoroutineContext().ensureActive()
                    openedTemporaryPath = file.name
                    val content = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    viewer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(content, document.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    pendingFile = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: ActivityNotFoundException) {
                    clearOpenedPreview()
                    Toast.makeText(
                        context,
                        "لا يوجد تطبيق مثبت لفتح هذا الملف. يلزم قارئ PDF أو عارض صور مناسب.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                    clearOpenedPreview()
                    Toast.makeText(context, "تعذر معاينة الملف.", Toast.LENGTH_SHORT).show()
                } finally {
                    pendingFile?.delete()
                    openingPreview = false
                }
            }
        },
        delete = { document ->
            withContext(Dispatchers.IO + NonCancellable) {
                database.removeMemberDocument(document.id, document.memberId)?.let {
                    store.clearTemporaryDocument(it.id)
                    store.delete(it)
                }
            }
        }
    )
}


private const val MAX_SCANNED_DOCUMENT_BYTES = 25L * 1024L * 1024L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
