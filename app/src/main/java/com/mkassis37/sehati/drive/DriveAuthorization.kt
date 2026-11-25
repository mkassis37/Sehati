package com.mkassis37.sehati.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.mkassis37.sehati.BuildConfig
import com.mkassis37.sehati.data.EncryptedBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DriveAuthorization(val request: (String?) -> Unit, val status: String, val busy: Boolean)

@Composable
internal fun rememberDriveAuthorization(
    backup: EncryptedBackup, onReturn: () -> Unit
): DriveAuthorization {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { DrivePreferences(context) }
    val drive = remember(context, backup) { DriveBackupClient(context, backup) }
    val auth = remember(context) { if (BuildConfig.GOOGLE_DRIVE_ENABLED) Identity.getAuthorizationClient(context) else null }
    var busy by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    suspend fun connected(result: AuthorizationResult) {
        val token = checkNotNull(result.accessToken)
        val email = result.toGoogleSignInAccount()?.email ?: drive.emailFor(token)
        withContext(Dispatchers.IO) { prefs.connect(email) }
        status = "اتصل الحساب بنسخ صحتي."
        scheduleDriveBackup(context, prefs)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        resolving = false
        if (result.resultCode != Activity.RESULT_OK || auth == null) {
            status = "أُلغيت موافقة Google."
            onReturn()
        } else {
            scope.launch {
                busy = true
                try {
                    connected(auth.getAuthorizationResultFromIntent(result.data))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    status = "تعذّر إتمام موافقة Google؛ تحقق من OAuth والحساب."
                } finally {
                    busy = false
                    onReturn()
                }
            }
        }
    }
    return DriveAuthorization(
        request = { account ->
            if (auth != null && !busy && !resolving) scope.launch {
                busy = true
                try {
                    val result = auth.authorize(driveAuthorization(account)).awaitDrive()
                    val resolution = result.pendingIntent
                    if (result.hasResolution() && resolution != null) {
                        resolving = true
                        launcher.launch(IntentSenderRequest.Builder(resolution).build())
                    } else connected(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    resolving = false
                    status = "لم يتصل حساب Google؛ تحقق من إعداد التطبيق."
                } finally {
                    busy = false
                }
            }
        },
        status = status,
        busy = busy || resolving
    )
}
