package com.mkassis37.sehati

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.medicalShareDirectory
import com.mkassis37.sehati.data.SehatiDatabase
import java.io.File
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
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val memberId = owner
        if (result.resultCode != Activity.RESULT_OK || memberId == null) {
            clear()
        } else {
            val fileName = result.data?.getStringExtra(DocumentScannerActivity.EXTRA_RESULT_FILE_NAME)
            val base = medicalShareDirectory(context).canonicalFile
            val scannedFile = runCatching {
                if (fileName.isNullOrBlank() ||
                    !fileName.matches(Regex("sehati-scan-[A-Za-z0-9_-]+\\.pdf"))
                ) null else File(base, fileName).canonicalFile.takeIf { it.parentFile == base }
            }.getOrNull()

            if (scannedFile == null || !scannedFile.isFile ||
                scannedFile.length() !in 1..MAX_SCANNED_DOCUMENT_BYTES
            ) {
                scannedFile?.delete()
                clear()
                Toast.makeText(context, "ملف المسح غير صالح أو حجمه أكبر من 25 MB.", Toast.LENGTH_LONG).show()
            } else {
                cameraPath = scannedFile.name
                uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    scannedFile
                )
                returnToLibrary(memberId)
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
                owner = memberId
                try {
                    scannerLauncher.launch(Intent(context, DocumentScannerActivity::class.java))
                } catch (_: Exception) {
                    clear()
                    Toast.makeText(context, "تعذر فتح ماسح المستندات المدمج.", Toast.LENGTH_LONG).show()
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
