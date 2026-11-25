package com.mkassis37.sehati

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.DoseLog
import com.mkassis37.sehati.data.DoseStatus
import com.mkassis37.sehati.data.MedicationRecord
import com.mkassis37.sehati.reminders.parseDoseTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DoseHistoryDialog(
    medication: MedicationRecord,
    logs: List<DoseLog>,
    onSave: suspend (DoseLog) -> Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var date by rememberSaveable(medication.id) { mutableStateOf(today.toString()) }
    var saving by remember(medication.id) { mutableStateOf(false) }
    var error by remember(medication.id) { mutableStateOf("") }
    val day = LocalDate.parse(date)
    val start = runCatching { LocalDate.parse(medication.startDate) }.getOrNull()
    val end = runCatching { LocalDate.parse(medication.endDate) }.getOrNull()
    val planned = if (day <= today && start != null && day >= start && (end == null || day <= end)) {
        parseDoseTimes(medication.scheduledTimes)
    } else emptyList()
    val dateLogs = logs.filter { it.date == date }.associateBy { it.time }
    val times = (planned + dateLogs.keys.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }).distinct().sorted()

    fun save(time: LocalTime, status: DoseStatus) {
        saving = true
        val now = System.currentTimeMillis()
        scope.launch {
            try {
                val updated = onSave(DoseLog(
                    medication.id, medication.memberId, date, time.toString(),
                    status.name, now, if (status == DoseStatus.SNOOZED) now + 10 * 60_000L else null
                ))
                error = if (updated) "" else "تعذر العثور على العلاج."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "تعذر حفظ حالة الجرعة."
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("سجل جرعات ${medication.name}") },
        text = {
            Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField("تاريخ الجرعات", date, { if (!saving) date = it }, Modifier.fillMaxWidth())
                if (times.isEmpty()) Text("لا توجد جرعات مجدولة أو مسجلة لهذا اليوم.")
                times.forEach { time ->
                    val current = dateLogs[time.toString()]
                    val status = DoseStatus.entries.find { it.name == current?.status }
                    val due = !day.atTime(time).isAfter(LocalDateTime.now())
                    Text("$time · ${status?.label ?: "غير مسجلة"}", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(enabled = !saving && due, onClick = { save(time, DoseStatus.TAKEN) }) { Text("أُخذت") }
                        TextButton(enabled = !saving && due, onClick = { save(time, DoseStatus.MISSED) }) { Text("فاتت") }
                        if (medication.isActive && !medication.completed && day == today && time in planned) {
                            TextButton(
                                enabled = !saving && due && (current == null || status == DoseStatus.SNOOZED),
                                onClick = { save(time, DoseStatus.SNOOZED) }
                            ) { Text("تأجيل 10 دقائق") }
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("آخر الجرعات المسجلة", style = MaterialTheme.typography.titleMedium)
                if (logs.isEmpty()) Text("لا توجد جرعات مسجلة بعد.")
                logs.take(20).forEach { log ->
                    val status = DoseStatus.entries.find { it.name == log.status }?.label.orEmpty()
                    Text("${log.date.toDisplayDate()} · ${log.time} · $status")
                }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("تم") } }
    )
}
