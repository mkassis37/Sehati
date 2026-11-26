package com.mkassis37.sehati

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
        openedTemporaryPath?.takeIf { it == File(it).name && it.startsWith("sehati-preview-") }?.let { name ->
            val file = File(medicalShareDirectory(context), name)
            try {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } finally {
                file.delete()
            }
        }
        openedTemporaryPath = null
    }
    val viewer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        clearOpenedPreview()
    }
    fun clear() {
        cameraPath?.let { File(medicalShareDirectory(context), it).delete() }
        cameraPath = null
        owner = null
        uri = null
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
        cancel = ::clear,
        save = { type, date, link ->
            checkNotNull(pending)
            withContext(Dispatchers.IO + NonCancellable) {
                val name = if (cameraPath != null) "صورة طبية - $date.jpg" else
                    context.contentResolver.query(pending.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "مرفق طبي"
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
