package com.mkassis37.sehati

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.AllergyRecord
import com.mkassis37.sehati.data.isLifeThreateningAllergySeverity
import com.mkassis37.sehati.data.isSevereAllergySeverity
import com.mkassis37.sehati.data.BodyMeasurement
import com.mkassis37.sehati.data.ChronicCondition
import com.mkassis37.sehati.data.FamilyDiseaseHistory
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.MedicalAppointment
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.displayValue
import com.mkassis37.sehati.data.MedicalHistoryEvent
import com.mkassis37.sehati.data.MedicalProcedure
import com.mkassis37.sehati.data.MedicationRecord
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class MedicalTimelineItem(val date: String, val title: String, val summary: String, val kind: String, val recordId: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicalHealthScreen(
    member: FamilyMember,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    allergies: List<AllergyRecord>, medications: List<MedicationRecord>, labResults: List<com.mkassis37.sehati.data.LabResult>, documents: List<MedicalDocument>,
    conditions: List<ChronicCondition>, procedures: List<MedicalProcedure>, history: List<MedicalHistoryEvent>,
    familyHistory: List<FamilyDiseaseHistory>, measurements: List<BodyMeasurement>,
    appointments: List<MedicalAppointment>,
    onSaveMember: suspend (FamilyMember) -> Unit,
    onAddCondition: suspend (ChronicCondition) -> Unit, onUpdateCondition: suspend (ChronicCondition) -> Unit,
    onDeleteCondition: suspend (Long) -> Unit,
    onAddProcedure: suspend (MedicalProcedure) -> Unit, onUpdateProcedure: suspend (MedicalProcedure) -> Unit,
    onDeleteProcedure: suspend (Long) -> Unit, onAttachProcedure: suspend (Long, Uri) -> Unit,
    onAddHistory: suspend (MedicalHistoryEvent) -> Unit, onUpdateHistory: suspend (MedicalHistoryEvent) -> Unit,
    onDeleteHistory: suspend (Long) -> Unit,
    onAddFamilyHistory: suspend (FamilyDiseaseHistory) -> Unit, onUpdateFamilyHistory: suspend (FamilyDiseaseHistory) -> Unit,
    onDeleteFamilyHistory: suspend (Long) -> Unit,
    onAddMeasurement: suspend (BodyMeasurement) -> Unit, onUpdateMeasurement: suspend (BodyMeasurement) -> Unit,
    onDeleteMeasurement: suspend (Long) -> Unit,
    onOpenDocument: (MedicalDocument) -> Unit,
    onDeleteDocument: (MedicalDocument) -> Unit,
    onOpenTimelineRecord: (String, Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable(member.id, initialTab) { mutableIntStateOf(initialTab) }
    var editor by rememberSaveable(member.id, stateSaver = editorStateSaver) { mutableStateOf<EditorState?>(null) }
    var emergencyEditor by rememberSaveable(member.id) { mutableStateOf(false) }
    var attachProcedureId by rememberSaveable(member.id) { mutableStateOf<Long?>(null) }
    var error by remember(member.id) { mutableStateOf("") }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val procedureId = attachProcedureId
        if (uri != null && procedureId != null) scope.launch {
            runCatching { onAttachProcedure(procedureId, uri) }
                .onFailure { error = "تعذر حفظ المرفق مشفرًا." }
        }
        attachProcedureId = null
    }
    val tabs = listOf("ملخص", "الحالات", "العمليات", "التاريخ السابق", "تاريخ العائلة", "القياسات", "الطوارئ", "السجل الزمني")
    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, label -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) }) }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        when (tab) {
            0 -> SummaryPanel(member, conditions, allergies, measurements, onEditEmergency = { emergencyEditor = true }, onGo = { tab = it })
            1 -> RecordListPanel("الأمراض والحالات المزمنة", "لا توجد حالات مسجلة. أضيفي الحالة التي شخّصها مختص.", conditions,
                titleOf = { it.name }, detailOf = { "${it.status} • ${it.diagnosedOn.toDisplayDate().ifBlank { "التاريخ غير محدد" }}\nالعلاج: ${it.treatment.ifBlank { "غير مسجل" }}\nالطبيب: ${it.diagnosingDoctor.ifBlank { "غير مسجل" }}\n${it.notes}" },
                add = { editor = EditorState("condition", null) }, edit = { editor = EditorState("condition", it.id) }, delete = { row -> scope.launch { onDeleteCondition(row.id) } })
            2 -> ProcedurePanel(procedures, documents, onAdd = { editor = EditorState("procedure", null) },
                onEdit = { editor = EditorState("procedure", it.id) }, onDelete = { row -> scope.launch { onDeleteProcedure(row.id) } },
                onAttach = { attachProcedureId = it; attachmentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) },
                onOpenDocument = onOpenDocument, onDeleteDocument = onDeleteDocument)
            3 -> RecordListPanel("الأمراض والإصابات والإقامات السابقة", "أضيفي أحداثًا صحية سابقة مهمة مع تواريخها.", history,
                titleOf = { "${it.category}: ${it.title}" }, detailOf = { "${it.eventDate.toDisplayDate().ifBlank { "التاريخ غير محدد" }}\n${it.notes}" },
                add = { editor = EditorState("history", null) }, edit = { editor = EditorState("history", it.id) }, delete = { row -> scope.launch { onDeleteHistory(row.id) } })
            4 -> {
                Column(Modifier.fillMaxSize()) {
                    Text("تاريخ الأمراض العائلية لا يعني تشخيص الفرد نفسه.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    RecordListPanel("أمراض العائلة", "سجلي المرض وصلته بصاحب الملف.", familyHistory,
                        titleOf = { "${it.disease} — ${it.relative}" }, detailOf = { it.notes },
                        add = { editor = EditorState("family", null) }, edit = { editor = EditorState("family", it.id) }, delete = { row -> scope.launch { onDeleteFamilyHistory(row.id) } })
                }
            }
            5 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("متابعة القياسات عبر الزمن", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                MeasurementTrendChart("الوزن", measurements.mapNotNull { row -> row.weightKg?.let { row.measuredOn to it } }, "كغ")
                MeasurementTrendChart("الطول", measurements.mapNotNull { row -> row.heightCm?.let { row.measuredOn to it } }, "سم")
                RecordListPanel("الطول والوزن", "احتفظي بقياسات مؤرخة لمتابعة التغير مع الوقت.", measurements,
                    titleOf = { "${it.measuredOn.toDisplayDate()} • ${it.heightCm?.let { v -> "$v سم" } ?: "الطول —"} • ${it.weightKg?.let { v -> "$v كغ" } ?: "الوزن —"}" },
                    detailOf = { it.notes }, add = { editor = EditorState("measurement", null) }, edit = { editor = EditorState("measurement", it.id) },
                    delete = { row -> scope.launch { onDeleteMeasurement(row.id) } })
            }
            6 -> EmergencyPanel(member, allergies, medications, conditions, onEdit = { emergencyEditor = true })
            else -> TimelinePanel(buildTimeline(conditions, procedures, history, familyHistory, allergies, medications, labResults, documents, measurements, appointments), onOpen = onOpenTimelineRecord)
        }
    }
    editor?.takeIf { request ->
        val id = request.id
        id == null || when (request.kind) {
            "condition" -> conditions.any { it.id == id }
            "procedure" -> procedures.any { it.id == id }
            "history" -> history.any { it.id == id }
            "family" -> familyHistory.any { it.id == id }
            "measurement" -> measurements.any { it.id == id }
            else -> false
        }
    }?.let { request ->
        val initial = editorFields(request, conditions, procedures, history, familyHistory, measurements, member.id)
        MedicalRecordDialog(request.kind, initial.first, initial.second, initial.third,
            onDismiss = { editor = null }, onSave = { values ->
                    try {
                        when (request.kind) {
                            "condition" -> {
                                val old = conditions.firstOrNull { it.id == request.id }
                                val row = ChronicCondition(id = old?.id ?: 0, memberId = member.id, name = values[0].trim(), diagnosedOn = values[1],
                                    diagnosingDoctor = values[2].trim(), status = values[3], treatment = values[4].trim(), notes = values[5].trim(), createdAt = old?.createdAt ?: System.currentTimeMillis())
                                if (old == null) onAddCondition(row) else onUpdateCondition(row)
                            }
                            "procedure" -> {
                                val old = procedures.firstOrNull { it.id == request.id }
                                val row = MedicalProcedure(id = old?.id ?: 0, memberId = member.id, name = values[0].trim(), performedOn = values[1],
                                    facility = values[2].trim(), doctor = values[3].trim(), reason = values[4].trim(), complications = values[5].trim(), notes = values[6].trim(), createdAt = old?.createdAt ?: System.currentTimeMillis())
                                if (old == null) onAddProcedure(row) else onUpdateProcedure(row)
                            }
                            "history" -> {
                                val old = history.firstOrNull { it.id == request.id }
                                val row = MedicalHistoryEvent(id = old?.id ?: 0, memberId = member.id, category = values[0], title = values[1].trim(), eventDate = values[2], notes = values[3].trim(), createdAt = old?.createdAt ?: System.currentTimeMillis())
                                if (old == null) onAddHistory(row) else onUpdateHistory(row)
                            }
                            "family" -> {
                                val old = familyHistory.firstOrNull { it.id == request.id }
                                val row = FamilyDiseaseHistory(id = old?.id ?: 0, memberId = member.id, relative = values[0], disease = values[1].trim(), notes = values[2].trim(), createdAt = old?.createdAt ?: System.currentTimeMillis())
                                if (old == null) onAddFamilyHistory(row) else onUpdateFamilyHistory(row)
                            }
                            "measurement" -> {
                                val old = measurements.firstOrNull { it.id == request.id }
                                val height = values[1].trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull()
                                val weight = values[2].trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull()
                                require(height != null || weight != null) { "أدخلي الطول أو الوزن على الأقل." }
                                require(height == null || height in 20.0..260.0) { "تحققي من قيمة الطول بالسنتيمتر." }
                                require(weight == null || weight in 1.0..500.0) { "تحققي من قيمة الوزن بالكيلوغرام." }
                                val row = BodyMeasurement(id = old?.id ?: 0, memberId = member.id, measuredOn = values[0], heightCm = height, weightKg = weight, notes = values[3].trim(), createdAt = old?.createdAt ?: System.currentTimeMillis())
                                if (old == null) onAddMeasurement(row) else onUpdateMeasurement(row)
                            }
                        }
                        editor = null
                        error = ""
                    } catch (e: Exception) { error = e.message ?: "تعذر حفظ السجل."; throw e }
            })
    }
    if (emergencyEditor) EmergencyEditor(member, onDismiss = { emergencyEditor = false }, onSave = { updated ->
        scope.launch {
            runCatching { onSaveMember(updated.copy(updatedAt = System.currentTimeMillis())) }
                .onSuccess { emergencyEditor = false; error = "" }
                .onFailure { error = "تعذر حفظ بطاقة الطوارئ." }
        }
    })
}

@Composable
private fun ProfileInfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SummaryPanel(member: FamilyMember, conditions: List<ChronicCondition>, allergies: List<AllergyRecord>, measurements: List<BodyMeasurement>, onEditEmergency: () -> Unit, onGo: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الملف الصحي لـ ${member.fullName}", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("المعلومات الأساسية", style = MaterialTheme.typography.titleMedium)
                ProfileInfoLine("تاريخ الميلاد", member.dateOfBirth.toDisplayDate().ifBlank { "غير مسجل" })
                ProfileInfoLine("الجنس", member.sex.ifBlank { "غير مسجل" })
                ProfileInfoLine("فصيلة الدم", member.bloodGroup.ifBlank { "غير مسجلة" })
                ProfileInfoLine("رقم التواصل", member.phone.ifBlank { "غير مسجل" })
                measurements.firstOrNull()?.let { measurement ->
                    ProfileInfoLine(
                        "آخر قياس · ${measurement.measuredOn.toDisplayDate()}",
                        "الطول: ${measurement.heightCm?.let { value -> "$value سم" } ?: "غير مسجل"}  •  الوزن: ${measurement.weightKg?.let { value -> "$value كغ" } ?: "غير مسجل"}"
                    )
                }
                if (member.additionalInfo.isNotBlank()) ProfileInfoLine("معلومات إضافية", member.additionalInfo)
                if (member.medicalNotes.isNotBlank()) ProfileInfoLine("ملاحظات طبية", member.medicalNotes)
            }
        }
        val lifeThreatening = allergies.filter { it.severity.isLifeThreateningAllergySeverity() }
        val severe = allergies.filter { it.severity.isSevereAllergySeverity() && !it.severity.isLifeThreateningAllergySeverity() }
        if (lifeThreatening.isNotEmpty()) Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تحذير مهم: حساسيات مهددة للحياة (${lifeThreatening.size})", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                lifeThreatening.forEach { allergy ->
                    Text(allergy.substance.ifBlank { allergy.commercialName.ifBlank { allergy.activeIngredient.ifBlank { "حساسية دون اسم" } } }, style = MaterialTheme.typography.titleSmall)
                    Text("المادة الفعالة: ${allergy.activeIngredient.ifBlank { "غير مسجلة" }}")
                    Text("رد الفعل: ${allergy.reaction.ifBlank { "غير مسجل" }}")
                    Text("التصنيف: ${allergy.category} · الشدة: ${allergy.severity}")
                    if (allergy.notes.isNotBlank()) Text("ملاحظات: ${allergy.notes}")
                }
                Text("أبلغي الطبيب أو الطاقم الطبي بهذه الحساسية عند كل زيارة.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (severe.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("حساسيات شديدة مسجلة", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            severe.forEach { Text("${it.substance.ifBlank { it.commercialName }} — ${it.reaction}") }
        } }
        val activeConditions = conditions.filter { it.status != "متوقف" }
        if (activeConditions.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("الحالات المهمة", style = MaterialTheme.typography.titleMedium)
            activeConditions.forEach { Text("${it.name} — ${it.status}") }
        } }
        Button(onClick = onEditEmergency, modifier = Modifier.fillMaxWidth()) { Text("عرض بطاقة الطوارئ") }
        listOf("الحالات المزمنة" to 1, "العمليات" to 2, "التاريخ السابق" to 3, "التاريخ العائلي" to 4, "القياسات" to 5, "السجل الزمني" to 7).forEach { (title, index) ->
            OutlinedButton(onClick = { onGo(index) }, modifier = Modifier.fillMaxWidth()) { Text(title) }
        }
    }
}

@Composable
private fun <T> RecordListPanel(title: String, empty: String, rows: List<T>, titleOf: (T) -> String, detailOf: (T) -> String, add: () -> Unit, edit: (T) -> Unit, delete: (T) -> Unit) {
    var pendingDelete by remember(rows) { mutableStateOf<T?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Button(onClick = add) { Text("إضافة") }
        }
        if (rows.isEmpty()) Text(empty, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { row ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(titleOf(row), style = MaterialTheme.typography.titleMedium)
                    val detail = detailOf(row)
                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { edit(row) }) { Text("تعديل") }
                        TextButton(onClick = { pendingDelete = row }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                } }
            }
        }
    }
    pendingDelete?.let { row -> AlertDialog(
        onDismissRequest = { pendingDelete = null }, title = { Text("تأكيد الحذف") },
        text = { Text("سيُحذف هذا السجل نهائيًا من الملف الطبي.") },
        confirmButton = { TextButton(onClick = { delete(row); pendingDelete = null }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
    ) }
}

@Composable
private fun ProcedurePanel(rows: List<MedicalProcedure>, documents: List<MedicalDocument>, onAdd: () -> Unit, onEdit: (MedicalProcedure) -> Unit, onDelete: (MedicalProcedure) -> Unit, onAttach: (Long) -> Unit, onOpenDocument: (MedicalDocument) -> Unit, onDeleteDocument: (MedicalDocument) -> Unit) {
    var pendingDelete by remember(rows) { mutableStateOf<MedicalProcedure?>(null) }
    var pendingDocumentDelete by remember(documents) { mutableStateOf<MedicalDocument?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("العمليات والإجراءات", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAdd) { Text("إضافة") }
        }
        if (rows.isEmpty()) Text("لا توجد عمليات أو إجراءات مسجلة.", modifier = Modifier.padding(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { procedure ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(procedure.name, style = MaterialTheme.typography.titleMedium)
                    Text("${procedure.performedOn.toDisplayDate().ifBlank { "التاريخ غير محدد" }} • ${procedure.facility.ifBlank { "المركز غير مسجل" }}\nالطبيب: ${procedure.doctor.ifBlank { "غير مسجل" }}\nالسبب: ${procedure.reason.ifBlank { "غير مسجل" }}\nالمضاعفات: ${procedure.complications.ifBlank { "لا توجد ملاحظات" }}\n${procedure.notes}")
                    documents.filter { it.relatedProcedureId == procedure.id }.forEach { doc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onOpenDocument(doc) }) { Text("فتح: ${doc.displayName}") }
                            TextButton(onClick = { pendingDocumentDelete = doc }) { Text("حذف المرفق", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Row { TextButton(onClick = { onAttach(procedure.id) }) { Text("إرفاق PDF أو صورة") }; TextButton(onClick = { onEdit(procedure) }) { Text("تعديل") }; TextButton(onClick = { pendingDelete = procedure }) { Text("حذف", color = MaterialTheme.colorScheme.error) } }
                } }
            }
        }
    }
    pendingDelete?.let { procedure -> AlertDialog(
        onDismissRequest = { pendingDelete = null }, title = { Text("تأكيد حذف الإجراء") },
        text = { Text("سيُحذف سجل ${procedure.name}. ستبقى المرفقات المشفرة في قسم الملفات مع فك ارتباطها بهذا الإجراء.") },
        confirmButton = { TextButton(onClick = { onDelete(procedure); pendingDelete = null }) { Text("حذف السجل") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
    ) }
    pendingDocumentDelete?.let { document -> AlertDialog(
        onDismissRequest = { pendingDocumentDelete = null }, title = { Text("تأكيد حذف المرفق") },
        text = { Text("سيُحذف ${document.displayName} من تخزين التطبيق المشفر.") },
        confirmButton = { TextButton(onClick = { onDeleteDocument(document); pendingDocumentDelete = null }) { Text("حذف نهائي") } },
        dismissButton = { TextButton(onClick = { pendingDocumentDelete = null }) { Text("إلغاء") } }
    ) }
}

@Composable
private fun EmergencyPanel(member: FamilyMember, allergies: List<AllergyRecord>, medications: List<MedicationRecord>, conditions: List<ChronicCondition>, onEdit: () -> Unit) {
    val lifeThreatening = allergies.filter { it.severity.isLifeThreateningAllergySeverity() }
    val displayedAllergies = (if (member.emergencyShowAllergies) allergies else lifeThreatening).sortedWith(
        compareBy<AllergyRecord> { when { it.severity.isLifeThreateningAllergySeverity() -> 0; it.severity.isSevereAllergySeverity() -> 1; else -> 2 } }.thenByDescending { it.createdAt }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("بطاقة الطوارئ", style = MaterialTheme.typography.headlineSmall)
        Text("هذه البطاقة داخل التطبيق فقط، ولا تظهر على شاشة القفل. اختاري المعلومات التي تريدين عرضها.", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(member.fullName, style = MaterialTheme.typography.titleLarge)
            Text("الميلاد: ${member.dateOfBirth.toDisplayDate().ifBlank { "غير مسجل" }} • فصيلة الدم: ${member.bloodGroup.ifBlank { "غير مسجلة" }}")
            if (member.emergencyShowAllergies || lifeThreatening.isNotEmpty()) {
                Text("الحساسيات", style = MaterialTheme.typography.titleMedium)
                if (!member.emergencyShowAllergies && lifeThreatening.isNotEmpty()) Text("تم إخفاء بقية الحساسيات حسب الإعداد، مع إبقاء تحذيرات الحساسية المهددة للحياة ظاهرة.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                displayedAllergies.forEach { allergy ->
                    val critical = allergy.severity.isLifeThreateningAllergySeverity()
                    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (critical) Text("⚠ تحذير: حساسية مهددة للحياة", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                            Text(allergy.substance.ifBlank { allergy.commercialName.ifBlank { allergy.activeIngredient.ifBlank { "حساسية دون اسم" } } }, style = MaterialTheme.typography.titleMedium)
                            if (allergy.commercialName.isNotBlank()) Text("الاسم التجاري: ${allergy.commercialName}")
                            if (allergy.activeIngredient.isNotBlank()) Text("المادة الفعالة: ${allergy.activeIngredient}")
                            Text("المسبب: ${allergy.substance.ifBlank { "غير مسجل" }}")
                            Text("التصنيف: ${allergy.category}")
                            Text("رد الفعل: ${allergy.reaction.ifBlank { "غير مسجل" }}")
                            Text("درجة الخطورة: ${allergy.severity.ifBlank { "غير محددة" }}")
                            if (allergy.notes.isNotBlank()) Text("ملاحظات: ${allergy.notes}")
                        }
                    }
                }
            }
            if (member.emergencyShowMedications) { Text("الأدوية الحالية", style = MaterialTheme.typography.titleMedium); Text(medications.filter { it.isActive }.joinToString("\n") { "${it.name} ${it.strength} — ${it.dosage}" }.ifBlank { "لا توجد أدوية نشطة مسجلة" }) }
            if (member.emergencyShowConditions) { Text("الحالات الصحية", style = MaterialTheme.typography.titleMedium); Text(conditions.filter { it.status != "متوقف" }.joinToString("\n") { "${it.name} — ${it.status}" }.ifBlank { "لا توجد حالات مسجلة" }) }
            if (member.emergencyShowNotes && member.medicalNotes.isNotBlank()) Text("ملاحظات مهمة: ${member.medicalNotes}")
            Text("جهة اتصال للطوارئ", style = MaterialTheme.typography.titleMedium)
            Text("${member.emergencyContactName.ifBlank { "غير مسجل" }} • ${member.emergencyContactPhone.ifBlank { "لا يوجد رقم" }} • ${member.emergencyContactRelation.ifBlank { "صلة القرابة غير محددة" }}")
        } }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("تعديل البطاقة ومعلومات العرض") }
    }
}

@Composable
private fun MeasurementTrendChart(title: String, points: List<Pair<String, Double>>, unit: String) {
    val ordered = points.mapNotNull { (date, value) ->
        runCatching { LocalDate.parse(date) }.getOrNull()?.let { it to value }
    }.sortedBy { it.first }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (ordered.isEmpty()) {
                Text("أضيفي قياسين مؤرخين على الأقل لعرض المنحنى.", style = MaterialTheme.typography.bodySmall)
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 150.dp)) {
                    val left = 12f
                    val right = size.width - 12f
                    val top = 12f
                    val bottom = size.height - 12f
                    repeat(3) { index ->
                        val y = top + (bottom - top) * index / 2f
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                    }
                    if (ordered.size >= 2) {
                        val values = ordered.map { it.second }
                        val minValue = values.minOrNull() ?: 0.0
                        val maxValue = values.maxOrNull() ?: 1.0
                        val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
                        val path = Path()
                        ordered.forEachIndexed { index, item ->
                            val x = left + (right - left) * index / (ordered.size - 1).toFloat()
                            val y = bottom - ((item.second - minValue) / range).toFloat() * (bottom - top)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(lineColor, radius = 4f, center = Offset(x, y))
                        }
                        drawPath(path, lineColor, style = Stroke(width = 3f))
                    } else {
                        drawCircle(lineColor, radius = 5f, center = Offset((left + right) / 2f, (top + bottom) / 2f))
                    }
                }
                val first = ordered.first()
                val last = ordered.last()
                Text("الأقدم: ${first.second} $unit • ${first.first}", style = MaterialTheme.typography.bodySmall)
                Text("الأحدث: ${last.second} $unit • ${last.first}", style = MaterialTheme.typography.bodySmall)
                if (ordered.size == 1) Text("يلزم قياس آخر لإظهار اتجاه التغير.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimelinePanel(items: List<MedicalTimelineItem>, onOpen: (String, Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("السجل الزمني الصحي", style = MaterialTheme.typography.headlineSmall)
        Text("الأحداث معروضة من سجلاتها الأصلية؛ لا تُنشأ نسخة إضافية منها.", style = MaterialTheme.typography.bodySmall)
        if (items.isEmpty()) Text("ستظهر هنا الأحداث الصحية عند إضافتها.", modifier = Modifier.padding(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item -> Card(Modifier.fillMaxWidth().clickable { onOpen(item.kind, item.recordId) }) {
                Column(Modifier.padding(14.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.date.toDisplayDate().ifBlank { "التاريخ غير محدد" })
                    if (item.summary.isNotBlank()) Text(item.summary)
                    Text("اضغطي لفتح القسم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            } }
        }
    }
}

private fun buildTimeline(conditions: List<ChronicCondition>, procedures: List<MedicalProcedure>, history: List<MedicalHistoryEvent>, familyHistory: List<FamilyDiseaseHistory>, allergies: List<AllergyRecord>, medications: List<MedicationRecord>, labResults: List<com.mkassis37.sehati.data.LabResult>, documents: List<MedicalDocument>, measurements: List<BodyMeasurement>, appointments: List<MedicalAppointment>) = buildList {
    conditions.forEach { add(MedicalTimelineItem(it.diagnosedOn, "حالة صحية: ${it.name}", it.status, "CONDITION", it.id)) }
    procedures.forEach { add(MedicalTimelineItem(it.performedOn, "إجراء طبي: ${it.name}", it.facility, "PROCEDURE", it.id)) }
    history.forEach { add(MedicalTimelineItem(it.eventDate, "${it.category}: ${it.title}", it.notes, "HISTORY", it.id)) }
    familyHistory.forEach { add(MedicalTimelineItem("", "تاريخ عائلي — ليس تشخيصًا: ${it.disease}", "${it.relative} • ${it.notes}", "FAMILY_HISTORY", it.id)) }
    allergies.forEach { add(MedicalTimelineItem(Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(), "حساسية: ${it.substance.ifBlank { it.commercialName }}", it.severity, "ALLERGY", it.id)) }
    medications.forEach { add(MedicalTimelineItem(it.startDate, "دواء: ${it.name}", if (it.isActive) "نشط" else "متوقف", "MEDICATION", it.id)) }
    medications.filter { !it.isActive && it.endDate.isNotBlank() }.forEach { add(MedicalTimelineItem(it.endDate, "إيقاف دواء: ${it.name}", it.endDate, "MEDICATION", it.id)) }
    labResults.forEach { add(MedicalTimelineItem(it.performedAt, "فحص: ${it.testName}", it.displayValue, "LAB", it.id)) }
    documents.forEach { add(MedicalTimelineItem(Instant.ofEpochMilli(it.addedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(), "مرفق: ${it.displayName}", "الوثائق والتحاليل", "DOCUMENT", it.id)) }
    appointments.forEach { add(MedicalTimelineItem(it.date, "موعد: ${it.doctor}", listOf(it.time, it.facility, it.reason).filter(String::isNotBlank).joinToString(" • "), "VISIT", it.id)) }
    measurements.forEach { add(MedicalTimelineItem(it.measuredOn, "قياس جسدي", listOfNotNull(it.heightCm?.let { h -> "$h سم" }, it.weightKg?.let { w -> "$w كغ" }).joinToString(" • "), "MEASUREMENT", it.id)) }
}.sortedByDescending { it.date }

private data class EditorState(val kind: String, val id: Long?)

private val editorStateSaver = listSaver<EditorState?, Any>(
    save = { state -> if (state == null) emptyList() else listOf(state.kind, state.id ?: 0L) },
    restore = { values -> if (values.isEmpty()) null else EditorState(values[0] as String, (values[1] as Long).takeIf { it != 0L }) }
)

private fun editorFields(request: EditorState, conditions: List<ChronicCondition>, procedures: List<MedicalProcedure>, history: List<MedicalHistoryEvent>, family: List<FamilyDiseaseHistory>, measurements: List<BodyMeasurement>, memberId: Long): Triple<String, List<String>, List<String>> = when (request.kind) {
    "condition" -> conditions.firstOrNull { it.id == request.id }?.let { Triple("حالة مزمنة", listOf("اسم المرض أو الحالة", "تاريخ التشخيص", "الطبيب المشخّص", "الحالة", "العلاج", "ملاحظات"), listOf(it.name, it.diagnosedOn, it.diagnosingDoctor, it.status, it.treatment, it.notes)) }
        ?: Triple("حالة مزمنة", listOf("اسم المرض أو الحالة", "تاريخ التشخيص", "الطبيب المشخّص", "الحالة", "العلاج", "ملاحظات"), listOf("", "", "", "نشط", "", ""))
    "procedure" -> procedures.firstOrNull { it.id == request.id }?.let { Triple("عملية أو إجراء طبي", listOf("اسم العملية أو الإجراء", "تاريخ الإجراء", "المستشفى أو المركز", "الطبيب", "السبب", "المضاعفات", "ملاحظات"), listOf(it.name, it.performedOn, it.facility, it.doctor, it.reason, it.complications, it.notes)) }
        ?: Triple("عملية أو إجراء طبي", listOf("اسم العملية أو الإجراء", "تاريخ الإجراء", "المستشفى أو المركز", "الطبيب", "السبب", "المضاعفات", "ملاحظات"), List(7) { "" })
    "history" -> history.firstOrNull { it.id == request.id }?.let { Triple("حدث سابق", listOf("نوع الحدث", "اسم المرض أو الإصابة أو الإقامة", "التاريخ", "ملاحظات"), listOf(it.category, it.title, it.eventDate, it.notes)) }
        ?: Triple("حدث سابق", listOf("نوع الحدث", "اسم المرض أو الإصابة أو الإقامة", "التاريخ", "ملاحظات"), listOf("مرض سابق", "", "", ""))
    "family" -> family.firstOrNull { it.id == request.id }?.let { Triple("مرض في العائلة", listOf("صلة القرابة", "المرض", "ملاحظات"), listOf(it.relative, it.disease, it.notes)) }
        ?: Triple("مرض في العائلة", listOf("صلة القرابة", "المرض", "ملاحظات"), listOf("الأب", "", ""))
    else -> measurements.firstOrNull { it.id == request.id }?.let { Triple("قياس الطول والوزن", listOf("تاريخ القياس", "الطول بالسنتيمتر", "الوزن بالكيلوغرام", "ملاحظات"), listOf(it.measuredOn, it.heightCm?.toString().orEmpty(), it.weightKg?.toString().orEmpty(), it.notes)) }
        ?: Triple("قياس الطول والوزن", listOf("تاريخ القياس", "الطول بالسنتيمتر", "الوزن بالكيلوغرام", "ملاحظات"), listOf(LocalDate.now().toString(), "", "", ""))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MedicalRecordDialog(kind: String, title: String, labels: List<String>, initial: List<String>, onDismiss: () -> Unit, onSave: suspend (List<String>) -> Unit) {
    var values by rememberSaveable(kind, initial) { mutableStateOf(ArrayList(initial)) }
    fun updateValue(index: Int, value: String) {
        values = ArrayList(values).apply { this[index] = value }
    }
    val dateIndices = when (kind) { "condition" -> setOf(1); "procedure" -> setOf(1); "history" -> setOf(2); "measurement" -> setOf(0); else -> emptySet() }
    var validation by remember(kind, initial) { mutableStateOf("") }
    var saveError by remember(kind, initial) { mutableStateOf("") }
    var expandedIndex by remember(kind, initial) { mutableIntStateOf(-1) }
    var saving by remember(kind, initial) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val choices: Map<Int, List<String>> = when (kind) {
        "condition" -> mapOf(3 to listOf("نشط", "متوقف", "تحت المتابعة"))
        "family" -> mapOf(0 to listOf(
            "الأب", "الأم", "الأخ", "الأخت", "الأخ غير الشقيق", "الأخت غير الشقيقة",
            "الجد من جهة الأب", "الجدة من جهة الأب", "الجد من جهة الأم", "الجدة من جهة الأم",
            "العم", "العمة", "الخال", "الخالة", "ابن العم", "ابنة العم", "ابن الخال", "ابنة الخال",
            "الابن", "الابنة", "الحفيد", "الحفيدة", "قريب آخر"
        ))
        "history" -> mapOf(0 to listOf("مرض سابق", "إصابة مهمة", "دخول مستشفى", "إجراء طبي سابق"))
        else -> emptyMap()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                if (dateIndices.contains(index)) DatePickerField(label, values[index], { updateValue(index, it) }, Modifier.fillMaxWidth())
                else if (choices[index] != null) {
                    ExposedDropdownMenuBox(expanded = expandedIndex == index, onExpandedChange = { expandedIndex = if (it) index else -1 }) {
                        OutlinedTextField(value = values[index], onValueChange = {}, readOnly = true, label = { Text(label) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIndex == index) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
                        ExposedDropdownMenu(expanded = expandedIndex == index, onDismissRequest = { expandedIndex = -1 }) {
                            choices.getValue(index).forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { updateValue(index, option); expandedIndex = -1 }) }
                        }
                    }
                } else OutlinedTextField(value = values[index], onValueChange = { updateValue(index, it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = label != "ملاحظات")
            }
            if (validation.isNotBlank()) Text(validation, color = MaterialTheme.colorScheme.error)
            if (saveError.isNotBlank()) {
                Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Text("لم يُغلق النموذج؛ يمكنك مراجعة البيانات والمحاولة مرة أخرى.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            val requiredIndex = if (kind == "history") 1 else if (kind == "family") 1 else 0
            if (values[requiredIndex].isBlank()) validation = "هذا الحقل مطلوب."
            else if (kind == "condition" && values[3] !in listOf("نشط", "متوقف", "تحت المتابعة")) validation = "اختاري حالة من: نشط، متوقف، تحت المتابعة."
            else scope.launch {
                saving = true
                saveError = ""
                try {
                    onSave(values.toList())
                } catch (e: Exception) {
                    saveError = e.message?.takeIf { it.isNotBlank() } ?: "تعذر حفظ السجل بسبب خطأ غير معروف."
                } finally {
                    saving = false
                }
            }
        }) { Text(if (saving) "جارٍ الحفظ..." else "حفظ") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun EmergencyEditor(member: FamilyMember, onDismiss: () -> Unit, onSave: (FamilyMember) -> Unit) {
    var name by rememberSaveable(member.id) { mutableStateOf(member.emergencyContactName) }
    var phone by rememberSaveable(member.id) { mutableStateOf(member.emergencyContactPhone) }
    var relation by rememberSaveable(member.id) { mutableStateOf(member.emergencyContactRelation) }
    var showAllergies by rememberSaveable(member.id) { mutableStateOf(member.emergencyShowAllergies) }
    var showMeds by rememberSaveable(member.id) { mutableStateOf(member.emergencyShowMedications) }
    var showConditions by rememberSaveable(member.id) { mutableStateOf(member.emergencyShowConditions) }
    var showNotes by rememberSaveable(member.id) { mutableStateOf(member.emergencyShowNotes) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إعداد بطاقة الطوارئ") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("اسم جهة الاتصال") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(relation, { relation = it }, label = { Text("صلة القرابة") }, modifier = Modifier.fillMaxWidth())
            PreferenceRow("إظهار الحساسيات", showAllergies) { showAllergies = it }
            PreferenceRow("إظهار الأدوية النشطة", showMeds) { showMeds = it }
            PreferenceRow("إظهار الحالات المزمنة", showConditions) { showConditions = it }
            PreferenceRow("إظهار الملاحظات الطبية", showNotes) { showNotes = it }
            Text("لا تظهر هذه البطاقة على شاشة القفل. تبقى تحذيرات الحساسية المهددة للحياة ظاهرة في بطاقة الطوارئ حتى عند إخفاء بقية الحساسيات.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onSave(member.copy(emergencyContactName = name.trim(), emergencyContactPhone = phone.trim(), emergencyContactRelation = relation.trim(), emergencyShowAllergies = showAllergies, emergencyShowMedications = showMeds, emergencyShowConditions = showConditions, emergencyShowNotes = showNotes)) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun PreferenceRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
