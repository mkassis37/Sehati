package com.mkassis37.sehati.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.mkassis37.sehati.MainActivity
import com.mkassis37.sehati.R
import com.mkassis37.sehati.data.SehatiDatabase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class MedicalReminders(context: Context, private val database: SehatiDatabase) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("medical_reminder_preferences", Context.MODE_PRIVATE)
    private val alarms = app.getSystemService(AlarmManager::class.java)

    val notificationsAllowed: Boolean
        get() = NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    val exactAlarmsAllowed: Boolean
        get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun enabled(kind: ReminderKind): Boolean = preferences.getBoolean(kind.name, false)

    fun setEnabled(kind: ReminderKind, enabled: Boolean) {
        preferences.edit().putBoolean(kind.name, enabled).apply()
    }

    suspend fun updateAlarms(deliveredKind: ReminderKind? = null, expectedAt: Long = 0L) =
        withContext(Dispatchers.IO) {
            schedulingLock.withLock {
                if (!notificationsAllowed || ReminderKind.entries.none(::enabled)) {
                    ReminderKind.entries.forEach { alarms.cancel(alarmIntent(it)) }
                    return@withLock
                }
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val (medications, appointments, doses) = database.withTransaction {
                    val oldestDate = minOf(now, if (expectedAt > 0L) Instant.ofEpochMilli(expectedAt) else now)
                        .atZone(zone).toLocalDate().minusDays(1).toString()
                    database.medicalDao().run { Triple(getAllMedications(), getAllAppointments(), getDoseLogsSince(oldestDate)) }
                }
                if (deliveredKind != null && enabled(deliveredKind) && expectedAt > 0L) {
                    val expected = Instant.ofEpochMilli(expectedAt)
                    if (expected <= now && Duration.between(expected, now).toMinutes() <= 90 &&
                        nextReminder(deliveredKind, expected, zone, medications, appointments, doses) == expected) {
                        post(deliveredKind, deliveredKind.body, deliveredKind.ordinal + 100)
                    }
                }
                ReminderKind.entries.forEach { kind ->
                    val earliest = if (deliveredKind == kind && expectedAt > 0L) {
                        maxOf(now, Instant.ofEpochMilli(expectedAt).plusMillis(1))
                    } else now
                    val next = if (enabled(kind)) nextReminder(kind, earliest, zone, medications, appointments, doses) else null
                    if (next == null) alarms.cancel(alarmIntent(kind))
                    else schedule(kind, next.toEpochMilli())
                }
            }
        }

    fun testNotification() {
        post(ReminderKind.DOSE, "هذا إشعار تجريبي من صحتي.", 199)
    }

    private fun schedule(kind: ReminderKind, timestamp: Long) {
        val operation = alarmIntent(kind, timestamp)
        if (exactAlarmsAllowed) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, operation)
                return
            } catch (_: SecurityException) {
                // Permission can change between the capability check and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, operation)
    }

    private fun alarmIntent(kind: ReminderKind, timestamp: Long = 0) = PendingIntent.getBroadcast(
        app, kind.ordinal,
        Intent(app, MedicalReminderReceiver::class.java).setAction(ACTION_ALARM)
            .putExtra(EXTRA_KIND, kind.name).putExtra(EXTRA_TIME, timestamp),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun post(kind: ReminderKind, message: String, id: Int) {
        if (!notificationsAllowed) return
        val channel = "sehati-reminders-${kind.name.lowercase()}"
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, kind.title, NotificationManager.IMPORTANCE_DEFAULT))
        val openApp = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_medical_reminder)
            .setContentTitle("صحتي")
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission may have been revoked after the capability check.
        }
    }

    private companion object {
        val schedulingLock = Mutex()
    }
}

class MedicalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = MedicalReminders(context, SehatiDatabase.getInstance(context))
                val kind = if (intent.action == ACTION_ALARM) {
                    ReminderKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
                } else null
                scheduler.updateAlarms(kind, intent.getLongExtra(EXTRA_TIME, 0L))
            } catch (_: Exception) {
                // Unavailable device storage is retried when the user opens Sehati.
            } finally {
                result.finish()
            }
        }
    }
}

private const val ACTION_ALARM = "com.mkassis37.sehati.REMINDER_ALARM"
private const val EXTRA_KIND = "kind"
private const val EXTRA_TIME = "scheduled_at"
