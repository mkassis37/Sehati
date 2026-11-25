package com.mkassis37.sehati.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ReminderSettings(reminders: MedicalReminders) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val notificationsAllowed = remember(refresh) { reminders.notificationsAllowed }
    val exactAllowed = remember(refresh) { reminders.exactAlarmsAllowed }
    val enabled = remember(refresh) { ReminderKind.entries.associateWith(reminders::enabled) }

    fun refreshSchedule() {
        refresh++
        scope.launch {
            try {
                reminders.updateAlarms()
                message = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "تعذر تحديث جدول التنبيهات."
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSchedule()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSchedule()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الإشعارات والتنبيهات", style = MaterialTheme.typography.titleMedium)
            Text(if (notificationsAllowed) "إذن الإشعارات متاح." else "فعّل إذن الإشعارات ليتمكن صحتي من إرسال التنبيهات.")
            if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
                OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("طلب إذن الإشعارات")
                }
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("إعدادات الإشعارات والنغمات في Android") }
            ReminderKind.entries.forEach { kind ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(kind.title, Modifier.weight(1f))
                    Switch(checked = enabled.getValue(kind), onCheckedChange = {
                        reminders.setEnabled(kind, it)
                        refreshSchedule()
                    })
                }
            }
            Text("يُجدول تذكير الموعد قبل 30 دقيقة، وتذكير انتهاء العلاج الساعة 7 صباحًا في اليوم السابق أو يوم البدء للعلاج القصير.")
            Text(if (exactAllowed) "التنبيه الدقيق متاح." else "التنبيه التقريبي قد يتأخر بسبب إعدادات Android.")
            if (Build.VERSION.SDK_INT >= 31 && !exactAllowed) {
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")))
                    } catch (_: android.content.ActivityNotFoundException) {
                        message = "إعداد المنبهات الدقيقة غير متاح على هذا الجهاز."
                    }
                }) { Text("إعداد المنبهات الدقيقة") }
            }
            OutlinedButton(enabled = notificationsAllowed, onClick = reminders::testNotification) {
                Text("إرسال إشعار تجريبي")
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}
