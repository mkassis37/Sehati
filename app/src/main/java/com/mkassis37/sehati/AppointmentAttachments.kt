package com.mkassis37.sehati

import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.mkassis37.sehati.data.MedicalAppointment
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.SehatiDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AppointmentAttachmentActions(
    val attach: (MedicalAppointment) -> Unit,
    val open: (MedicalDocument) -> Unit
)

@Composable
internal fun rememberAppointmentAttachments(
    database: SehatiDatabase,
    documents: SecureMedicalDocumentStore,
    onAttached: (Long) -> Unit
): AppointmentAttachmentActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingAppointmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingMemberId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val appointmentId = pendingAppointmentId
        val memberId = pendingMemberId
        pendingAppointmentId = null
        pendingMemberId = null
        if (uri != null && appointmentId != null && memberId != null) {
            scope.launch {
                try {
                    val name = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    } ?: "مرفق طبي"
                    withContext(Dispatchers.IO + NonCancellable) {
                        val document = documents.save(uri, memberId, name)
                        try {
                            database.addAppointmentDocument(document, appointmentId)
                        } catch (error: Exception) {
                            documents.delete(document)
                            throw error
                        }
                    }
                    onAttached(memberId)
                    Toast.makeText(context, "حُفظ المرفق المشفر.", Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, "تعذر إرفاق الملف بالموعد.", Toast.LENGTH_LONG).show()
                }
            }
        } else if (memberId != null) {
            onAttached(memberId)
        }
    }
    return AppointmentAttachmentActions(
        attach = { appointment ->
            if (pendingAppointmentId == null) {
                pendingAppointmentId = appointment.id
                pendingMemberId = appointment.memberId
                picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
            }
        },
        open = { document ->
            scope.launch {
                try {
                    val file = documents.createTemporaryFile(document)
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    documents.clearTemporaryDocument(document.id)
                    Toast.makeText(context, "تعذر عرض المرفق.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )
}
