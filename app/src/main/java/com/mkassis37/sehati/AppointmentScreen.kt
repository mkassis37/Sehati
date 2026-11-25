package com.mkassis37.sehati

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.AppointmentStatus
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.MedicalAppointment
import com.mkassis37.sehati.data.MedicalDocument
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class CalendarView(val label: String) {
    MONTH("شهري"), WEEK("أسبوعي"), DAY("يومي"), HISTORY("الزيارات السابقة")
}

private val arabicLocale = Locale.forLanguageTag("ar-JO")
private val monthTitle = DateTimeFormatter.ofPattern("MMMM uuuu", arabicLocale)
private val dayTitle = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(arabicLocale)

@Composable
internal fun AppointmentScreen(
    member: FamilyMember,
    appointments: List<MedicalAppointment>,
    documents: List<MedicalDocument>,
    onAttach: (MedicalAppointment) -> Unit,
    onOpenDocument: (MedicalDocument) -> Unit,
    onSave: suspend (MedicalAppointment) -> Boolean,
    onDelete: suspend (MedicalAppointment) -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var selectedDay by rememberSaveable(member.id) { mutableStateOf(today.toString()) }
    var view by rememberSaveable(member.id) { mutableStateOf(CalendarView.MONTH) }
    var showEditor by remember(member.id) { mutableStateOf(false) }
    var editing by remember(member.id) { mutableStateOf<MedicalAppointment?>(null) }
    var deleting by remember(member.id) { mutableStateOf<MedicalAppointment?>(null) }
    var deletingNow by remember(member.id) { mutableStateOf(false) }
    var error by remember(member.id) { mutableStateOf("") }
    val day = LocalDate.parse(selectedDay)
    val weekStart = day.with(WeekFields.of(arabicLocale).dayOfWeek(), 1)
    val monthStart = day.withDayOfMonth(1)
    val monthGridStart = monthStart.with(WeekFields.of(arabicLocale).dayOfWeek(), 1)
    val groups = appointments.groupBy { it.date }
    val visible = when (view) {
        CalendarView.MONTH, CalendarView.DAY -> groups[selectedDay].orEmpty()
        CalendarView.WEEK -> appointments.filter { it.date in weekStart.toString()..weekStart.plusDays(6).toString() }
        CalendarView.HISTORY -> appointments.filter {
            it.date < today.toString() || it.status != AppointmentStatus.UPCOMING.name
        }.asReversed()
    }
    fun move(step: Long) {
        selectedDay = when (view) {
            CalendarView.MONTH -> day.plusMonths(step)
            CalendarView.WEEK -> day.plusWeeks(step)
            CalendarView.DAY -> day.plusDays(step)
            CalendarView.HISTORY -> day
        }.toString()
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("مواعيد ${member.fullName}", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarView.entries.take(3).forEach { option ->
                    FilterChip(view == option, { view = option }, label = { Text(option.label) })
                }
            }
            FilterChip(
                view == CalendarView.HISTORY,
                { view = if (view == CalendarView.HISTORY) CalendarView.MONTH else CalendarView.HISTORY },
                label = { Text(CalendarView.HISTORY.label) }
            )
        }
        if (view != CalendarView.HISTORY) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { move(-1) }) { Text("السابق") }
                    Text(day.format(monthTitle), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { move(1) }) { Text("التالي") }
                }
                DatePickerField("الانتقال إلى تاريخ", selectedDay, { selectedDay = it }, Modifier.fillMaxWidth())
            }
        }
        if (view == CalendarView.MONTH || view == CalendarView.WEEK) {
            val start = if (view == CalendarView.MONTH) monthGridStart else weekStart
            val days = if (view == CalendarView.MONTH) {
                val untilEnd = java.time.temporal.ChronoUnit.DAYS.between(start, monthStart.plusMonths(1))
                ((untilEnd + 6) / 7 * 7).toInt()
            } else 7
            item {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { offset ->
                        val label = start.plusDays(offset.toLong()).format(DateTimeFormatter.ofPattern("EEE", arabicLocale))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            items(days / 7) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(7) { offset ->
                        val date = start.plusDays((week * 7 + offset).toLong())
                        val count = groups[date.toString()].orEmpty().size
                        CalendarDay(date, count, day == date, { selectedDay = date.toString() }, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            if (view != CalendarView.HISTORY) {
                Text(if (view == CalendarView.WEEK) "مواعيد الأسبوع" else day.format(dayTitle))
            }
            Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                Text("إضافة موعد")
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (visible.isEmpty()) item { Text("لا توجد مواعيد في هذا العرض.") }
        items(visible, key = { it.id }) { record ->
            AppointmentCard(
                record,
                documents.filter { it.relatedAppointmentId == record.id },
                onAttach = { onAttach(record) },
                onOpenDocument = onOpenDocument,
                onEdit = { editing = record; showEditor = true },
                onDelete = { deleting = record }
            )
        }
    }
    if (showEditor) AppointmentEditor(member.id, selectedDay, editing, { showEditor = false }) { record ->
        val saved = onSave(record)
        if (saved && view != CalendarView.HISTORY) selectedDay = record.date
        saved
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { if (!deletingNow) deleting = null },
            title = { Text("حذف الموعد؟") },
            text = { Text("سيُحذف موعد ${record.doctor} وتفاصيل الزيارة. تبقى المرفقات في ملف الفرد.") },
            confirmButton = {
                TextButton(enabled = !deletingNow, onClick = {
                    scope.launch {
                        deletingNow = true
                        try {
                            if (onDelete(record)) {
                                error = ""
                            } else {
                                error = "تعذر العثور على الموعد."
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "تعذر حذف الموعد."
                        } finally {
                            deleting = null
                            deletingNow = false
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(enabled = !deletingNow, onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun CalendarDay(date: LocalDate, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
            if (count > 0) Text("$count", style = MaterialTheme.typography.labelSmall)
        }
    }
    val padding = PaddingValues(horizontal = 1.dp)
    if (selected) Button(onClick, modifier, contentPadding = padding, content = { label() })
    else OutlinedButton(onClick, modifier, contentPadding = padding, content = { label() })
}

@Composable
private fun AppointmentCard(
    record: MedicalAppointment,
    documents: List<MedicalDocument>,
    onAttach: () -> Unit,
    onOpenDocument: (MedicalDocument) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(record.doctor, style = MaterialTheme.typography.titleMedium)
            Text("${record.date.toDisplayDate()} • ${record.time} • ${AppointmentStatus.entries.firstOrNull { it.name == record.status }?.label.orEmpty()}")
            listOf(
                "التخصص" to record.specialty, "المركز" to record.facility, "سبب الزيارة" to record.reason,
                "ملاحظات" to record.notes, "نتيجة الزيارة" to record.outcome,
                "التشخيص" to record.diagnosis, "التوصيات" to record.recommendations
            ).forEach { (label, value) ->
                if (value.isNotBlank()) Text("$label · $value")
            }
            documents.forEach { document ->
                TextButton(onClick = { onOpenDocument(document) }) {
                    Text(document.displayName, maxLines = 2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAttach) { Text("إرفاق ملف") }
                TextButton(onClick = onEdit) { Text("تعديل") }
                TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentEditor(
    memberId: Long,
    initialDate: String,
    record: MedicalAppointment?,
    onDismiss: () -> Unit,
    onSave: suspend (MedicalAppointment) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doctor by rememberSaveable { mutableStateOf(record?.doctor.orEmpty()) }
    var specialty by rememberSaveable { mutableStateOf(record?.specialty.orEmpty()) }
    var facility by rememberSaveable { mutableStateOf(record?.facility.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(record?.date ?: initialDate) }
    var time by rememberSaveable { mutableStateOf(record?.time ?: "09:00") }
    var reason by rememberSaveable { mutableStateOf(record?.reason.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(record?.notes.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(AppointmentStatus.entries.firstOrNull { it.name == record?.status } ?: AppointmentStatus.UPCOMING) }
    var outcome by rememberSaveable { mutableStateOf(record?.outcome.orEmpty()) }
    var diagnosis by rememberSaveable { mutableStateOf(record?.diagnosis.orEmpty()) }
    var recommendations by rememberSaveable { mutableStateOf(record?.recommendations.orEmpty()) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (record == null) "إضافة موعد" else "تعديل الموعد والزيارة") },
        text = {
            Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(doctor, { doctor = it }, label = { Text("اسم الطبيب") }, singleLine = true)
                OutlinedTextField(specialty, { specialty = it }, label = { Text("التخصص") }, singleLine = true)
                OutlinedTextField(facility, { facility = it }, label = { Text("المركز الطبي") }, singleLine = true)
                DatePickerField("التاريخ", date, { date = it }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) { Text("الوقت · $time") }
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب الزيارة") })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppointmentStatus.entries.forEach { option ->
                        FilterChip(status == option, { status = option }, label = { Text(option.label) })
                    }
                }
                if (status == AppointmentStatus.COMPLETED) {
                    OutlinedTextField(outcome, { outcome = it }, label = { Text("نتيجة الزيارة") })
                    OutlinedTextField(diagnosis, { diagnosis = it }, label = { Text("التشخيص كما سجله الطبيب") })
                    OutlinedTextField(recommendations, { recommendations = it }, label = { Text("التوصيات") })
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (doctor.isBlank()) {
                    error = "أدخل اسم الطبيب."
                    return@TextButton
                }
                val edited = MedicalAppointment(
                    id = record?.id ?: 0L, memberId = memberId,
                    doctor = doctor.trim(), specialty = specialty.trim(), facility = facility.trim(),
                    date = date, time = time, reason = reason.trim(), notes = notes.trim(),
                    status = status.name, outcome = outcome.trim(), diagnosis = diagnosis.trim(),
                    recommendations = recommendations.trim(), createdAt = record?.createdAt ?: System.currentTimeMillis()
                )
                scope.launch {
                    busy = true
                    try {
                        if (onSave(edited)) onDismiss() else error = "تعذر العثور على الموعد."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "تعذر حفظ الموعد."
                    } finally {
                        busy = false
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") } }
    )
    if (showTime) {
        val initialTime = LocalTime.parse(time)
        val state = rememberTimePickerState(initialTime.hour, initialTime.minute, android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("وقت الموعد") },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute).toString()
                    showTime = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } }
        )
    }
}
