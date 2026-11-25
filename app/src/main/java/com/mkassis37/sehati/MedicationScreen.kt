package com.mkassis37.sehati

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.MedicationRecord
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun MedicationScreen(
    member: FamilyMember,
    records: List<MedicationRecord>,
    addMedication: suspend (MedicationRecord) -> Unit,
    updateMedication: suspend (MedicationRecord) -> Unit,
    deleteMedication: suspend (MedicationRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MedicationRecord?>(null) }
    var showForm by rememberSaveable(member.id) { mutableStateOf(false) }
    var showInactive by rememberSaveable(member.id) { mutableStateOf(false) }
    val visible = records.filter { showInactive || it.isActive }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("أدوية وعلاجات ${member.fullName}", style = MaterialTheme.typography.titleLarge)
        Text("كل سجل مرتبط بهذا الفرد فقط. أدخل تفاصيل العلاج كما وصفها الطبيب أو الصيدلي.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("العلاجات الحالية: ${records.count { it.isActive }}")
            TextButton(onClick = { showInactive = !showInactive }) { Text(if (showInactive) "إخفاء المتوقفة" else "عرض المتوقفة") }
        }
        Button(onClick = { editing = null; showForm = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة دواء أو علاج") }
        if (visible.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (showInactive) "لا توجد أدوية مسجلة." else "لا توجد علاجات حالية.")
                Text("أضف اسم الدواء والجرعة ومواعيد الاستخدام لتكوين سجل واضح.", style = MaterialTheme.typography.bodySmall)
            } }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.name, style = MaterialTheme.typography.titleMedium)
                                    if (record.strength.isNotBlank()) Text(record.strength, style = MaterialTheme.typography.bodySmall)
                                }
                                AssistChip(onClick = { scope.launch { updateMedication(record.copy(isActive = !record.isActive, updatedAt = System.currentTimeMillis())) } },
                                    label = { Text(if (record.isActive) "نشط" else "متوقف") })
                            }
                            DetailRow("الجرعة", record.dosage.ifBlank { "غير محددة" })
                            DetailRow("الشكل", record.form.ifBlank { "غير محدد" })
                            DetailRow("التكرار", record.frequency.ifBlank { "غير محدد" })
                            if (record.scheduledTimes.isNotBlank()) DetailRow("الأوقات", record.scheduledTimes)
                            DetailRow("الفترة", record.startDate + if (record.endDate.isBlank()) " – مستمر" else " إلى ${record.endDate}")
                            if (record.prescribingDoctor.isNotBlank()) DetailRow("الطبيب", record.prescribingDoctor)
                            if (record.instructions.isNotBlank()) DetailRow("طريقة الاستخدام", record.instructions)
                            if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editing = record; showForm = true }) { Text("تعديل") }
                                TextButton(onClick = { scope.launch { deleteMedication(record) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showForm) MedicationEditor(member.id, editing, { showForm = false; editing = null }, { record ->
        scope.launch {
            if (editing == null) addMedication(record) else updateMedication(record)
            showForm = false
            editing = null
        }
    })
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MedicationEditor(
    memberId: Long,
    initial: MedicationRecord?,
    onDismiss: () -> Unit,
    onSave: (MedicationRecord) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var strength by remember(initial?.id) { mutableStateOf(initial?.strength.orEmpty()) }
    var dosage by remember(initial?.id) { mutableStateOf(initial?.dosage.orEmpty()) }
    var form by remember(initial?.id) { mutableStateOf(initial?.form.orEmpty()) }
    var frequency by remember(initial?.id) { mutableStateOf(initial?.frequency.orEmpty()) }
    var times by remember(initial?.id) { mutableStateOf(initial?.scheduledTimes.orEmpty()) }
    var start by remember(initial?.id) { mutableStateOf(initial?.startDate ?: LocalDate.now().toString()) }
    var end by remember(initial?.id) { mutableStateOf(initial?.endDate.orEmpty()) }
    var doctor by remember(initial?.id) { mutableStateOf(initial?.prescribingDoctor.orEmpty()) }
    var instructions by remember(initial?.id) { mutableStateOf(initial?.instructions.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "إضافة دواء أو علاج" else "تعديل العلاج") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الدواء أو العلاج *") }, singleLine = true)
                OutlinedTextField(strength, { strength = it }, label = { Text("التركيز (مثال: 500 mg)") }, singleLine = true)
                OutlinedTextField(dosage, { dosage = it }, label = { Text("الجرعة (مثال: حبة واحدة)") }, singleLine = true)
                OutlinedTextField(form, { form = it }, label = { Text("الشكل (حبوب، شراب، كريم...)") }, singleLine = true)
                OutlinedTextField(frequency, { frequency = it }, label = { Text("عدد مرات الاستخدام") }, singleLine = true)
                OutlinedTextField(times, { times = it }, label = { Text("أوقات الجرعات (مثال: 08:00، 20:00)") })
                OutlinedTextField(start, { start = it }, label = { Text("تاريخ بداية العلاج (YYYY-MM-DD) *") }, singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("تاريخ انتهاء العلاج (اختياري)") }, singleLine = true)
                OutlinedTextField(doctor, { doctor = it }, label = { Text("الطبيب أو الصيدلي") }, singleLine = true)
                OutlinedTextField(instructions, { instructions = it }, label = { Text("تعليمات الاستخدام") })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("سجّل الجرعة حسب الوصفة الطبية؛ التطبيق لا يحدد جرعات علاجية.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = {
            val validStart = runCatching { LocalDate.parse(start); true }.getOrDefault(false)
            val validEnd = end.isBlank() || runCatching { LocalDate.parse(end); true }.getOrDefault(false)
            if (name.isBlank()) error = "أدخل اسم الدواء أو العلاج."
            else if (!validStart || !validEnd) error = "استخدم صيغة التاريخ YYYY-MM-DD."
            else if (end.isNotBlank() && end < start) error = "تاريخ الانتهاء يجب ألا يسبق البداية."
            else onSave(MedicationRecord(
                id = initial?.id ?: 0, memberId = memberId, name = name.trim(),
                strength = strength.trim(), dosage = dosage.trim(), form = form.trim(),
                frequency = frequency.trim(), scheduledTimes = times.trim(), startDate = start,
                endDate = end, prescribingDoctor = doctor.trim(), instructions = instructions.trim(),
                notes = notes.trim(), isActive = initial?.isActive ?: true,
                createdAt = initial?.createdAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            ))
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
