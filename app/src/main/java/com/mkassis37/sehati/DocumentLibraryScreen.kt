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
import androidx.compose.ui.Alignment
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
    fun documentDay(document: MedicalDocument): String =
        document.documentDate.ifBlank {
            Instant.ofEpochMilli(document.addedAt).atZone(zone).toLocalDate().toString()
        }
    val hasFilters = query.isNotBlank() || category != null || date.isNotBlank()
    val visible = (if (focused) documents else documents.filter { document ->
        document.displayName.contains(query.trim(), ignoreCase = true) &&
            (category == null || document.category == category?.name) &&
            (date.isBlank() || date == documentDay(document))
    }).sortedByDescending(::documentDay)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("الملفات الطبية · ${member.fullName}", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (focused) "المرفقات المرتبطة" else "ملفات هذا الفرد")
                        Text(
                            if (focused) "${visible.size} مرفق" else "${visible.size} من ${documents.size} ملف",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("${documents.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!focused) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { actions.pick(member.id) },
                        modifier = Modifier.weight(1f)
                    ) { Text("اختيار ملف") }
                    OutlinedButton(
                        onClick = { actions.capture(member.id) },
                        modifier = Modifier.weight(1f)
                    ) { Text("التقاط صورة") }
                }
                OutlinedButton(
                    onClick = { actions.scan(member.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("مسح مستند ضوئيًا · PDF متعدد الصفحات")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("البحث باسم الملف") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(category == null, { category = null }, label = { Text("كل الأنواع") })
                    DocumentCategory.entries.forEach { option ->
                        FilterChip(category == option, { category = option }, label = { Text(option.label) })
                    }
                }
                DatePickerField("تصفية حسب التاريخ", date, { date = it }, Modifier.fillMaxWidth())
                if (hasFilters) {
                    TextButton(onClick = {
                        query = ""
                        category = null
                        date = ""
                    }) { Text("مسح البحث والفلاتر") }
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (documents.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("لا توجد مرفقات محفوظة لهذا الفرد بعد.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "يمكنك إضافة تقرير PDF أو صورة طبية. تُحفظ المرفقات داخل مساحة التطبيق الخاصة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (visible.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("لا توجد نتائج مطابقة", style = MaterialTheme.typography.titleMedium)
                        Text("جرّبي تغيير البحث أو نوع الملف أو التاريخ.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            query = ""
                            category = null
                            date = ""
                        }) { Text("عرض جميع الملفات") }
                    }
                }
            }
        }
        items(visible, key = { it.id }) { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(document.displayName, style = MaterialTheme.typography.titleMedium)
                    val label = DocumentCategory.entries.firstOrNull { it.name == document.category }?.label ?: DocumentCategory.OTHER.label
                    Text("$label · ${documentDay(document).toDisplayDate()}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${documentMimeLabel(document.mimeType)} · ${formatDocumentSize(document.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val linkLabel = links.firstOrNull { link ->
                        when (link.kind) {
                            "appointment" -> link.id == document.relatedAppointmentId
                            "lab" -> link.id == document.relatedLabResultId
                            "procedure" -> link.id == document.relatedProcedureId
                            else -> false
                        }
                    }?.label
                    Text(
                        linkLabel ?: "غير مرتبط بسجل طبي",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (linkLabel == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { actions.open(document) }) { Text("معاينة") }
                        TextButton(onClick = { delete = document }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    actions.pending?.takeIf { it.memberId == member.id }?.let {
        DocumentImportDialog(it.uri.toString(), links, actions.cancel, actions.save)
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

private fun documentMimeLabel(mimeType: String): String = when (mimeType.lowercase()) {
    "application/pdf" -> "PDF"
    "image/jpeg" -> "صورة JPEG"
    "image/png" -> "صورة PNG"
    "image/webp" -> "صورة WEBP"
    else -> "مرفق"
}

private fun formatDocumentSize(bytes: Long): String {
    if (bytes < 0L) return "الحجم غير معروف"
    return when {
        bytes < 1024L -> "$bytes بايت"
        bytes < 1024L * 1024L -> "${String.format(java.util.Locale.US, "%.1f", bytes / 1024.0)} كيلوبايت"
        else -> "${String.format(java.util.Locale.US, "%.1f", bytes / (1024.0 * 1024.0))} ميغابايت"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentImportDialog(
    importKey: String,
    links: List<DocumentLink>,
    onCancel: () -> Unit,
    onSave: suspend (DocumentCategory, String, DocumentLink?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var category by rememberSaveable(importKey) { mutableStateOf(DocumentCategory.OTHER) }
    var date by rememberSaveable(importKey) { mutableStateOf(LocalDate.now().toString()) }
    var activeLink by rememberSaveable(importKey) { mutableStateOf<String?>(null) }
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
                    ExposedDropdownMenu(expanded = showLinks, onDismissRequest = { showLinks = false }, modifier = Modifier.heightIn(max = 240.dp)) {
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
