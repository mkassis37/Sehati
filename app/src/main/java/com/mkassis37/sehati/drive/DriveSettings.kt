package com.mkassis37.sehati.drive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.BuildConfig
import com.mkassis37.sehati.data.EncryptedBackup
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DriveSettings(
    backup: EncryptedBackup,
    authorization: DriveAuthorization,
    onPreview: (EncryptedBackup.BackupPreview) -> Unit
) {
    if (!BuildConfig.GOOGLE_DRIVE_ENABLED) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نسخ Google Drive", style = MaterialTheme.typography.titleMedium)
                Text("يتطلب هذا البناء إعداد OAuth وتفعيل Drive API لمعرّف تطبيق صحتي وبصمة توقيعه.")
                Text("يمكنك إنشاء نسخة يدوية مشفرة من قسم النسخ الاحتياطي.")
            }
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { DrivePreferences(context) }
    val drive = remember(context, backup) { DriveBackupClient(context, backup) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(prefs.status) }
    var refresh by remember { mutableIntStateOf(0) }
    var passwordMode by remember { mutableStateOf<DrivePasswordMode?>(null) }
    var showTime by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<DriveFile>?>(null) }
    var restoreFile by remember { mutableStateOf<DriveFile?>(null) }
    val state = remember(refresh) {
        Triple(prefs.account, prefs.automatic, prefs.time)
    }

    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: DriveNeedsConsent) {
                status = "يلزم تجديد موافقة Google."
            } catch (_: Exception) {
                status = "تعذر إكمال طلب Drive. تحقق من الاتصال أو إعداد OAuth وكلمة مرور النسخة."
            } finally {
                busy = false
                refresh++
            }
        }
    }
    LaunchedEffect(prefs) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            refresh++
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("النسخ المشفر في Google Drive", style = MaterialTheme.typography.titleMedium)
            Text(state.first ?: "لا يوجد حساب مرتبط.")
            OutlinedButton(enabled = !busy && !authorization.busy, onClick = { authorization.request(state.first) }) {
                Text(if (state.first == null) "ربط حساب Google" else "تجديد الموافقة")
            }
            if (authorization.status.isNotBlank() && state.first == null) Text(authorization.status)

            if (state.first != null) {
                OutlinedButton(enabled = !busy, onClick = {
                    work {
                        withContext(Dispatchers.IO) { prefs.disconnect() }
                        cancelDriveBackup(context)
                        status = "فُصل الحساب من صحتي وأُوقفت الجدولة."
                    }
                }) { Text("فصل الحساب") }
                OutlinedButton(enabled = !busy, onClick = { passwordMode = DrivePasswordMode.SCHEDULE }) {
                    Text(if (prefs.hasPassword) "تغيير كلمة مرور النسخ التلقائي" else "تعيين كلمة مرور النسخ التلقائي")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("نسخ يومي مشفر", Modifier.weight(1f))
                    Switch(checked = state.second, enabled = !busy && prefs.hasPassword, onCheckedChange = { enabled ->
                        work {
                            withContext(Dispatchers.IO) { prefs.updateAutomatic(enabled, state.third) }
                            if (enabled) scheduleDriveBackup(context, prefs) else cancelDriveBackup(context)
                        }
                    })
                }
                OutlinedButton(enabled = !busy, onClick = { showTime = true }) { Text("وقت النسخ · ${state.third}") }
                OutlinedButton(enabled = !busy, onClick = { passwordMode = DrivePasswordMode.UPLOAD }) {
                    Text("رفع نسخة مشفرة الآن")
                }
                OutlinedButton(enabled = !busy, onClick = {
                    work { backups = drive.list(drive.grant(state.first)) }
                }) { Text("عرض نسخ Drive للاستعادة") }
            }
            Text("آخر محاولة: ${driveTime(prefs.lastAttempt)}")
            Text("آخر نجاح: ${driveTime(prefs.lastSuccess)}")
            if (prefs.status.isNotBlank() && prefs.status != status) Text(prefs.status)
            if (status.isNotBlank()) Text(status)
            if (busy || authorization.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    backups?.let { list ->
        AlertDialog(
            onDismissRequest = { backups = null },
            title = { Text("نسخ Drive المشفرة") },
            text = {
                Column {
                    if (list.isEmpty()) Text("لا توجد نسخ مشفرة في الحساب.")
                    list.take(12).forEach { file ->
                        TextButton(onClick = {
                            restoreFile = file
                            backups = null
                            passwordMode = DrivePasswordMode.RESTORE
                        }) { Text(file.created.ifBlank { file.name }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { backups = null }) { Text("إغلاق") } }
        )
    }
    passwordMode?.let { mode ->
        var password by remember(mode) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordMode = null },
            title = { Text("كلمة مرور النسخة الاحتياطية") },
            text = {
                Column {
                    Text(if (mode == DrivePasswordMode.RESTORE) "أدخل كلمة مرور النسخة المختارة."
                        else "استخدم 10 أحرف على الأقل واحتفظ بها لاستعادة النسخة.")
                    OutlinedTextField(password, { password = it }, visualTransformation = PasswordVisualTransformation(),
                        label = { Text("كلمة المرور") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= if (mode == DrivePasswordMode.RESTORE) 1 else 10, onClick = {
                    val pass = password.toCharArray()
                    password = ""
                    passwordMode = null
                    work {
                        try {
                            when (mode) {
                                DrivePasswordMode.SCHEDULE -> withContext(Dispatchers.IO) { prefs.updatePassword(pass) }
                                DrivePasswordMode.UPLOAD -> {
                                    prefs.recordAttempt()
                                    try {
                                        drive.upload(drive.grant(state.first), pass, driveBackupName())
                                        prefs.recordStatus("تم رفع النسخة المشفرة.", success = true)
                                        status = prefs.status
                                    } catch (error: Exception) {
                                        prefs.recordStatus("فشلت محاولة رفع النسخة.")
                                        throw error
                                    }
                                }
                                DrivePasswordMode.RESTORE -> {
                                    val file = checkNotNull(restoreFile)
                                    onPreview(drive.preview(drive.grant(state.first), file, pass))
                                    restoreFile = null
                                }
                            }
                        } finally {
                            pass.fill('\u0000')
                        }
                    }
                }) { Text("متابعة") }
            },
            dismissButton = { TextButton(onClick = { passwordMode = null }) { Text("إلغاء") } }
        )
    }
    if (showTime) {
        val time = rememberTimePickerState(state.third.hour, state.third.minute, true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("وقت النسخ المحلي") },
            text = { TimeInput(time) },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    work {
                        withContext(Dispatchers.IO) { prefs.updateAutomatic(state.second, LocalTime.of(time.hour, time.minute)) }
                        if (state.second) scheduleDriveBackup(context, prefs)
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } }
        )
    }
}

private enum class DrivePasswordMode { SCHEDULE, UPLOAD, RESTORE }

private fun driveTime(epoch: Long): String = if (epoch <= 0) "لم تُسجّل"
    else DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch))
