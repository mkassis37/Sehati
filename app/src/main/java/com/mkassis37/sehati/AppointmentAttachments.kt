package com.mkassis37.sehati

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
import com.mkassis37.sehati.data.MedicalAppointment
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.SehatiDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AppointmentAttachmentActions(
    val attach: (MedicalAppointment) -> Unit
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
        }
    )
}
