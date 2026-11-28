package com.mkassis37.sehati

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.MedicalSearchHit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private val searchLabels = mapOf(
    "MEMBER" to "فرد", "ALLERGY" to "حساسية", "LAB" to "فحص", "MEDICATION" to "دواء",
    "VISIT" to "زيارة", "DOCUMENT" to "وثيقة", "PROCEDURE" to "إجراء", "HISTORY" to "حدث طبي",
    "CONDITION" to "حالة مزمنة", "FAMILY_HISTORY" to "تاريخ عائلي",
    "BODY_MEASUREMENT" to "قياس جسدي", "DOSE_LOG" to "سجل جرعة"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicalSearchScreen(
    members: List<FamilyMember>,
    text: String,
    onTextChange: (String) -> Unit,
    chosenMember: Long?,
    onChooseMember: (Long?) -> Unit,
    search: (String, Long?) -> Flow<List<MedicalSearchHit>>,
    open: (MedicalSearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val member = members.firstOrNull { it.id == chosenMember }
    val query = text.trim()
    val results by key(query, chosenMember) {
        produceState<List<MedicalSearchHit>?>(null) {
            if (query.length >= 2) {
                delay(220)
                search(query, chosenMember).collect { value = it }
            } else value = emptyList()
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                text, onTextChange, label = { Text("ابحث في الملفات الصحية") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            ExposedDropdownMenuBox(menu, { menu = it }) {
                OutlinedTextField(member?.fullName ?: "جميع الأفراد", {}, label = { Text("نطاق البحث") },
                    readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("جميع الأفراد") }, { onChooseMember(null); menu = false })
                    members.forEach { option ->
                        DropdownMenuItem({ Text(option.fullName) }, { onChooseMember(option.id); menu = false })
                    }
                }
            }
        }
        val filtered = results
        when {
            query.length < 2 -> item { Text("أدخل حرفين على الأقل.") }
            filtered == null -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
            filtered.isEmpty() -> item { Text("لم يعثر البحث على سجلات مطابقة.") }
            else -> {
                if (filtered.size == 100) item { Text("ضَيّق البحث لعرض نتائج أكثر تحديدًا.") }
                items(filtered, key = { "${it.kind}/${it.recordId}" }) { result ->
                    Card(onClick = { open(result) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(result.title, style = MaterialTheme.typography.titleMedium)
                            Text("${searchLabels[result.kind].orEmpty()} · ${result.memberName}", style = MaterialTheme.typography.bodySmall)
                            if (result.detail.isNotBlank()) Text(result.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
