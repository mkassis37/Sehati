package com.mkassis37.sehati.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mkassis37.sehati.BuildConfig
import com.mkassis37.sehati.data.EncryptedBackup
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.SehatiDatabase
import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal fun scheduleDriveBackup(context: Context, prefs: DrivePreferences = DrivePreferences(context)) {
    if (!BuildConfig.GOOGLE_DRIVE_ENABLED || !prefs.automatic || prefs.account == null || !prefs.hasPassword) return
    val now = ZonedDateTime.now()
    var next = now.toLocalDate().atTime(prefs.time).atZone(now.zone)
    if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(prefs.time).atZone(now.zone)
    val work = OneTimeWorkRequestBuilder<DriveDailyBackup>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
        .setInputData(workDataOf("generation" to prefs.generation, "date" to next.toLocalDate().toString(),
            "zone" to next.zone.id, "scheduled_at" to next.toInstant().toEpochMilli()))
        .addTag(DRIVE_WORK_TAG)
        .build()
    val uniqueName = "$DRIVE_WORK_TAG/${prefs.generation}/${next.toInstant().toEpochMilli()}"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, work)
}

internal fun cancelDriveBackup(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag(DRIVE_WORK_TAG)
}

class DriveDailyBackup(app: Context, parameters: WorkerParameters) : CoroutineWorker(app, parameters) {
    override suspend fun doWork(): Result {
        val prefs = DrivePreferences(applicationContext)
        val email = prefs.account ?: return Result.success()
        val generation = inputData.getLong("generation", -1)
        if (!BuildConfig.GOOGLE_DRIVE_ENABLED || !prefs.automatic || generation != prefs.generation) return Result.success()
        val zone = inputData.getString("zone")
        val scheduledDate = inputData.getString("date") ?: return Result.success()
        if (zone != ZonedDateTime.now().zone.id) {
            scheduleDriveBackup(applicationContext, prefs)
            return Result.success()
        }
        prefs.recordAttempt()
        try {
            val client = DriveBackupClient(applicationContext, EncryptedBackup(
                SehatiDatabase.getInstance(applicationContext), SecureMedicalDocumentStore(applicationContext)
            ))
            val token = client.grant(email)
            val name = "sehati-backup-daily-$scheduledDate.shtb"
            if (client.list(token).none { it.name == name }) {
                if (prefs.generation != generation || prefs.account != email) return Result.success()
                client.upload(token, prefs.backupPassword(), name)
            }
            if (prefs.generation == generation) prefs.recordStatus("تم رفع النسخة المشفرة.", success = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: DriveNeedsConsent) {
            if (prefs.generation == generation) prefs.recordStatus("افتح إعدادات Drive لتجديد موافقة Google.")
        } catch (error: IOException) {
            val retryable = error !is DriveRequestFailed || error.status == 429 || error.status >= 500
            if (prefs.generation == generation) {
                prefs.recordStatus(if (retryable) "تعذّر الاتصال بخدمة Drive." else "رفض Drive الطلب؛ راجع الحساب والموافقة.")
                if (retryable && runAttemptCount < 4) return Result.retry()
            }
        } catch (_: Exception) {
            if (prefs.generation == generation) prefs.recordStatus("تعذّر إنشاء النسخة أو فتح كلمة مرور الجدولة.")
        }
        scheduleDriveBackup(applicationContext, prefs)
        return Result.success()
    }
}

private const val DRIVE_WORK_TAG = "sehati-drive-daily"
