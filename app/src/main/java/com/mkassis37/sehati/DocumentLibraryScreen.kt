package com.mkassis37.sehati

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.MedicalDocument
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class DocumentCategory(val label: String) {
    LAB("تحاليل مخبرية"), IMAGING("أشعة وصور طبية"), PRESCRIPTION("وصفات طبية"),
    REPORT("تقارير أطباء"), PROCEDURE("عمليات وإجراءات"), OTHER("وثائق أخرى")
}

internal data class DocumentLink(val kind: String, val id: Long, val label: String)

@Composable
internal fun DocumentLibraryScreen(
    member: FamilyMember,
    documents: List<MedicalDocument>,
    links: List<DocumentLink>,
    actions: DocumentLibraryActions,
    modifier: Modifier = Modifier,
    focused: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable(member.id) { mutableStateOf("") }
    var category by rememberSaveable(member.id) { mutableStateOf<DocumentCategory?>(null) }
    var date by rememberSaveable(member.id) { mutableStateOf("") }
    var delete by remember(member.id) { mutableStateOf<MedicalDocument?>(null) }
    var deleting by remember(member.id) { mutableStateOf(false) }
    var error by remember(member.id) { mutableStateOf("") }
    val zone = ZoneId.systemDefault()
    val visible = if (focused) documents else documents.filter { document ->
        val day = document.documentDate.ifBlank { Instant.ofEpochMilli(document.addedAt).atZone(zone).toLocalDate().toString() }
        document.displayName.contains(query.trim(), true) &&
            (category == null || document.category == category?.name) &&
            (date.isBlank() || date == day)
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("الملفات الطبية · ${member.fullName}", style = MaterialTheme.typography.titleLarge)
            if (!focused) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.pick(member.id) }) { Text("اختيار ملف") }
                    OutlinedButton(onClick = { actions.capture(member.id) }) { Text("التقاط صورة") }
                }
                OutlinedTextField(query, { query = it }, label = { Text("البحث باسم الملف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(category == null, { category = null }, label = { Text("كل الأنواع") })
                    DocumentCategory.entries.forEach { option ->
                        FilterChip(category == option, { category = option }, label = { Text(option.label) })
                    }
                }
                DatePickerField("تصفية حسب التاريخ", date, { date = it }, Modifier.fillMaxWidth())
                if (date.isNotBlank()) TextButton(onClick = { date = "" }) { Text("كل التواريخ") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (visible.isEmpty()) item { Text("لا توجد ملفات ضمن الاختيار الحالي.") }
        items(visible, key = { it.id }) { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(document.displayName, style = MaterialTheme.typography.titleMedium)
                    val label = DocumentCategory.entries.firstOrNull { it.name == document.category }?.label.orEmpty()
                    val day = document.documentDate.ifBlank { Instant.ofEpochMilli(document.addedAt).atZone(zone).toLocalDate().toString() }
                    Text("$label · ${day.toDisplayDate()}", style = MaterialTheme.typography.bodySmall)
                    val linkLabel = links.firstOrNull { link ->
                        when (link.kind) {
                            "appointment" -> link.id == document.relatedAppointmentId
                            "lab" -> link.id == document.relatedLabResultId
                            "procedure" -> link.id == document.relatedProcedureId
                            else -> false
                        }
                    }?.label
                    if (linkLabel != null) Text(linkLabel, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { actions.open(document) }) { Text("معاينة") }
                        TextButton(onClick = { delete = document }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    actions.pending?.takeIf { it.memberId == member.id }?.let {
        DocumentImportDialog(links, actions.cancel, actions.save)
    }
    delete?.let { document ->
        AlertDialog(
            onDismissRequest = { if (!deleting) delete = null },
            title = { Text("حذف الملف؟") },
            text = { Text(document.displayName) },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch {
                        try {
                            actions.delete(document)
                            error = ""
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "تعذر حذف الملف أو تنظيف مرفقه."
                        } finally {
                            deleting = false
                            delete = null
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { delete = null }) { Text("إلغاء") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentImportDialog(
    links: List<DocumentLink>,
    onCancel: () -> Unit,
    onSave: suspend (DocumentCategory, String, DocumentLink?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf(DocumentCategory.OTHER) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var activeLink by rememberSaveable { mutableStateOf<String?>(null) }
    var showLinks by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val selected = links.firstOrNull { "${it.kind}/${it.id}" == activeLink }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("حفظ مرفق مشفر") },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DocumentCategory.entries.chunked(2).forEach { row ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { item ->
                            FilterChip(category == item, { category = item }, label = { Text(item.label) })
                        }
                    }
                }
                DatePickerField("تاريخ الملف", date, { date = it }, Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = showLinks, onExpandedChange = { showLinks = it }) {
                    OutlinedTextField(selected?.label ?: "بدون ربط", {}, readOnly = true,
                        label = { Text("الربط بسجل طبي") }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(showLinks, { showLinks = false }) {
                        DropdownMenuItem({ Text("بدون ربط") }, { activeLink = null; showLinks = false })
                        links.forEach { link ->
                            DropdownMenuItem({ Text(link.label) }, { activeLink = "${link.kind}/${link.id}"; showLinks = false })
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        onSave(category, date, selected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "تعذر حفظ الملف. تحقق من النوع والحجم والسجل المرتبط."
                    } finally {
                        busy = false
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text("إلغاء") } }
    )
}
