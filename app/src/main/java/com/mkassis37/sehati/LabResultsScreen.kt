package com.mkassis37.sehati

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.LabResult
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.displayValue
import com.mkassis37.sehati.data.hasSameReferenceRange
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LabResultsScreen(member: FamilyMember, records: List<LabResult>, documents: List<MedicalDocument>, saveResult: suspend (LabResult, Uri?) -> Boolean, deleteResult: suspend (Long) -> Unit, saveDocument: suspend (Uri) -> Unit, openDocument: (MedicalDocument) -> Unit, deleteDocument: (MedicalDocument) -> Unit, modifier: Modifier = Modifier, initiallyAdd: Boolean = false) {
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable(member.id) { mutableStateOf(initiallyAdd) }
    var editingId by rememberSaveable(member.id) { mutableStateOf<Long?>(null) }
    val editing = records.firstOrNull { it.id == editingId }
    var sourceUri by rememberSaveable(member.id) { mutableStateOf<Uri?>(null) }
    var reviewCandidate by remember(member.id) { mutableStateOf<LabReportCandidate?>(null) }
    var selectedTest by rememberSaveable(member.id) { mutableStateOf("") }
    var selectedUnit by rememberSaveable(member.id) { mutableStateOf<String?>(null) }
    var years by rememberSaveable(member.id) { mutableIntStateOf(0) }
    val tests = records.map { it.testName }.distinct().sorted()
    val units = records.filter { it.testName == selectedTest }.map { it.unit }.distinct().sorted()
    val latestUnit = records.filter { it.testName == selectedTest }.maxByOrNull { it.performedAt }?.unit.orEmpty()
    val activeUnit = selectedUnit ?: latestUnit
    val filtered = records.filter { selectedTest.isBlank() || (it.testName == selectedTest && it.unit == activeUnit) }
        .filter { row -> years == 0 || runCatching { LocalDate.parse(row.performedAt).isAfter(LocalDate.now().minusYears(years.toLong()).minusDays(1)) }.getOrDefault(false) }
    val chronological = filtered.sortedBy { it.performedAt }
    val latest = chronological.lastOrNull()
    val previous = chronological.dropLast(1).lastOrNull()
    val numericHistory = chronological.filter { row ->
        row.value?.isFinite() == true && latest != null && row.hasSameReferenceRange(latest) &&
            runCatching { LocalDate.parse(row.performedAt) }.isSuccess
    }
    val latestValue = latest?.value
    val previousValue = previous?.value
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("فحوصات " + member.fullName, style = MaterialTheme.typography.titleLarge)
        Text("تُحفظ كل نتيجة بتاريخها ومجالها المرجعي، وتبقى متاحة للمقارنة لسنوات.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { editingId = null; reviewCandidate = null; sourceUri = null; showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة نتيجة فحص") }
        LabPdfReader(
            onExtractedText = { /* تتم المعاينة محليًا فقط؛ لا يُحفظ النص الخام */ },
            onCandidateSelected = { candidate, uri -> editingId = null; reviewCandidate = candidate; sourceUri = uri; showAdd = true },
            onSavePdf = saveDocument
        )
        Text("التقارير المحفوظة في ملف " + member.fullName, style = MaterialTheme.typography.titleMedium)
        if (documents.isEmpty()) {
            Text("لم يتم حفظ مستندات لهذا الفرد بعد.", style = MaterialTheme.typography.bodySmall)
        } else {
            documents.forEach { document ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(document.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(String.format(Locale.getDefault(), "%.1f KB", document.sizeBytes / 1024.0), style = MaterialTheme.typography.bodySmall)
                            Text(document.addedAt.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { openDocument(document) }) { Text("فتح") }
                        TextButton(onClick = { deleteDocument(document) }) { Text("حذف") }
                    }
                }
            }
        }
        Text("أو أدخل النتيجة يدويًا", style = MaterialTheme.typography.titleSmall)
        if (tests.isNotEmpty()) {
            Text("الفحص")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selectedTest.isBlank(), onClick = { selectedTest = ""; selectedUnit = null }, label = { Text("الكل") })
                tests.forEach { name -> FilterChip(selected = selectedTest == name, onClick = { selectedTest = name; selectedUnit = null }, label = { Text(name) }) }
            }
            if (selectedTest.isNotBlank() && units.size > 1) {
                Text("الوحدة")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { unit -> FilterChip(selected = activeUnit == unit, onClick = { selectedUnit = unit }, label = { Text(unit.ifBlank { "بدون وحدة" }) }) }
                }
                Text("يتم فصل المقارنة حسب الوحدة حتى لا نقارن قيماً غير متجانسة.", style = MaterialTheme.typography.bodySmall)
            }
            Text("الفترة الزمنية")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 3, 5).forEach { count -> FilterChip(selected = years == count, onClick = { years = count }, label = { Text(if (count == 0) "كل السنوات" else count.toString() + " سنوات") }) }
            }
        }
        if (latest != null && selectedTest.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("أحدث نتيجة: " + latest.testName, style = MaterialTheme.typography.titleMedium)
                Text(latest.displayValue, style = MaterialTheme.typography.headlineSmall)
                Text("التاريخ: " + latest.performedAt.toDisplayDate())
                when {
                    previous == null -> Text("لا توجد نتيجة أقدم للمقارنة.")
                    latestValue != null && previousValue != null && !latest.hasSameReferenceRange(previous) ->
                        Text("المجال المرجعي مختلف عن النتيجة السابقة. راجع حدود كل نتيجة في السجل.")
                    latestValue != null && previousValue != null ->
                        Text("مقارنة بالسابقة: " + (latestValue - previousValue) + " " + latest.unit + " (" + previous.performedAt.toDisplayDate() + ")")
                    else -> Text("النتيجة الحالية أو السابقة نصية؛ القيم متاحة في سجل النتائج.")
                }
                val low = latest.referenceLow; val high = latest.referenceHigh
                if (latestValue != null && (low != null || high != null)) {
                    val status = when { low != null && latestValue < low -> "أقل من المجال المرجعي المسجل"; high != null && latestValue > high -> "أعلى من المجال المرجعي المسجل"; else -> "ضمن المجال المرجعي المسجل" }
                    Text(status, color = if (status.startsWith("ضمن")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text("المجال: " + (low?.toString() ?: "—") + " إلى " + (high?.toString() ?: "—") + " " + latest.unit)
                }
            } }
            if (numericHistory.size >= 2) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التغير عبر الزمن ضمن المجال المرجعي نفسه", style = MaterialTheme.typography.titleMedium)
                    val chartLineColor = MaterialTheme.colorScheme.primary
                    val chartPointColor = MaterialTheme.colorScheme.tertiary
                    val dates = numericHistory.map { LocalDate.parse(it.performedAt).toEpochDay() }
                    val firstDay = dates.first()
                    val duration = dates.last() - firstDay
                    var selectedPoint by remember(numericHistory) { mutableIntStateOf(numericHistory.lastIndex) }
                    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                        val values = numericHistory.mapNotNull { it.value }; val min = values.minOrNull() ?: 0.0; val max = values.maxOrNull() ?: 1.0; val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                        val points = values.mapIndexed { index, value -> androidx.compose.ui.geometry.Offset(if (duration == 0L) size.width / 2 else size.width * ((dates[index] - firstDay).toDouble() / duration).toFloat(), size.height - ((value - min) / range).toFloat() * (size.height - 16.dp.toPx()) - 8.dp.toPx()) }
                        points.zipWithNext().forEach { pair -> drawLine(chartLineColor, pair.first, pair.second, strokeWidth = 4f) }
                        points.forEachIndexed { index, point -> drawCircle(chartPointColor, if (index == selectedPoint) 8f else 5f, point) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        numericHistory.forEachIndexed { index, point ->
                            FilterChip(selected = selectedPoint == index, onClick = { selectedPoint = index },
                                label = { Text(point.performedAt.toDisplayDate()) })
                        }
                    }
                    val point = numericHistory[selectedPoint]
                    Text(point.displayValue + " · " + point.performedAt.toDisplayDate(), style = MaterialTheme.typography.titleSmall)
                    if (point.referenceLow != null || point.referenceHigh != null) {
                        Text("المجال المرجعي: ${point.referenceLow ?: "—"} إلى ${point.referenceHigh ?: "—"} ${point.unit}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
        }
        if (selectedTest.isBlank() && tests.isNotEmpty()) Text("اختاري فحصًا محددًا لعرض المقارنة والرسم البياني؛ لا نقارن فحوصات مختلفة أو بوحدات مختلفة.")
        Text("سجل النتائج (" + filtered.size + ")", style = MaterialTheme.typography.titleMedium)
        if (filtered.isEmpty()) Text("لا توجد نتائج ضمن الاختيار الحالي. أضف أول فحص للبدء.")
        filtered.forEach { row -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.testName, style = MaterialTheme.typography.titleSmall)
                Text(row.displayValue)
                Text(row.performedAt.toDisplayDate() + " • " + row.laboratory.ifBlank { "المختبر غير محدد" },
                    style = MaterialTheme.typography.bodySmall)
                if (row.value != null) {
                    Text("المرجع: " + (row.referenceLow?.toString() ?: "—") + " إلى " + (row.referenceHigh?.toString() ?: "—"),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall)
                documents.filter { it.relatedLabResultId == row.id }.forEach { document ->
                    TextButton(onClick = { openDocument(document) }) { Text(document.displayName) }
                }
            }
            Column {
                TextButton(onClick = { editingId = row.id; showAdd = true }) { Text("تعديل") }
                TextButton(onClick = { scope.launch { deleteResult(row.id) } }) { Text("حذف") }
            }
        } } }
    }
    if (showAdd && (editingId == null || editing != null)) {
        LabResultEditor(member.id, editing, reviewCandidate, onDismiss = {
            showAdd = false
            editingId = null
            reviewCandidate = null
            sourceUri = null
        }, source = sourceUri, onSave = saveResult)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabResultEditor(
    memberId: Long,
    existing: LabResult?,
    candidate: LabReportCandidate?,
    onDismiss: () -> Unit,
    source: Uri?,
    onSave: suspend (LabResult, Uri?) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(existing?.id, candidate?.testName) {
        mutableStateOf(existing?.testName ?: candidate?.testName.orEmpty())
    }
    var result by rememberSaveable(existing?.id, candidate?.sourceLine) {
        mutableStateOf((existing?.qualitativeValue ?: existing?.value?.toString()
            ?: candidate?.qualitativeValue ?: candidate?.value?.toString()).orEmpty())
    }
    val initialUnit = existing?.unit ?: candidate?.unit.orEmpty()
    var unit by rememberSaveable(existing?.id, initialUnit) { mutableStateOf(initialUnit) }
    var low by rememberSaveable(existing?.id, candidate?.referenceLow) {
        mutableStateOf((existing?.referenceLow ?: candidate?.referenceLow)?.toString().orEmpty())
    }
    var high by rememberSaveable(existing?.id, candidate?.referenceHigh) {
        mutableStateOf((existing?.referenceHigh ?: candidate?.referenceHigh)?.toString().orEmpty())
    }
    var date by rememberSaveable(existing?.id, candidate?.performedAt) {
        mutableStateOf(existing?.performedAt ?: candidate?.performedAt ?: LocalDate.now().toString())
    }
    var lab by rememberSaveable(existing?.id) { mutableStateOf(existing?.laboratory.orEmpty()) }
    var notes by rememberSaveable(existing?.id, candidate?.sourceLine) {
        mutableStateOf(existing?.notes ?: candidate?.let {
            "اقتراح مستخرج من تقرير المختبر. راجع النتيجة والوحدة والمجال والتاريخ مقابل التقرير الأصلي." +
                if (it.flag.isNotBlank()) " علامة المختبر: " + it.flag else ""
        }.orEmpty())
    }
    var error by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var qualitative by rememberSaveable(existing?.id, candidate?.sourceLine) {
        mutableStateOf(existing?.qualitativeValue != null || candidate?.qualitativeValue != null)
    }
    var unitMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var testNameMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var customUnit by rememberSaveable(existing?.id, initialUnit) {
        mutableStateOf(initialUnit.isNotBlank() && initialUnit !in LAB_RESULT_UNITS)
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (existing == null) "إضافة نتيجة فحص" else "تعديل نتيجة الفحص") }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = testNameMenuExpanded, onExpandedChange = { testNameMenuExpanded = it }) {
            OutlinedTextField(value = name, onValueChange = { name = it; testNameMenuExpanded = true },
                label = { Text("اسم الفحص") }, placeholder = { Text("اكتبي الاسم أو اختاري فحصًا شائعًا") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = testNameMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
            val matchingTests = LAB_TEST_NAME_SUGGESTIONS.filter { it.contains(name.trim(), ignoreCase = true) }
            ExposedDropdownMenu(expanded = testNameMenuExpanded, onDismissRequest = { testNameMenuExpanded = false }) {
                (if (name.isBlank()) LAB_TEST_NAME_SUGGESTIONS else matchingTests).forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { name = option; testNameMenuExpanded = false })
                }
                if (name.isNotBlank() && matchingTests.none { it.equals(name.trim(), ignoreCase = true) }) {
                    DropdownMenuItem(text = { Text("استخدام الاسم المكتوب: ${name.trim()}") }, onClick = { testNameMenuExpanded = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !qualitative, onClick = { qualitative = false }, label = { Text("رقمية") })
            FilterChip(selected = qualitative, onClick = { qualitative = true }, label = { Text("نصية") })
        }
        OutlinedTextField(result, { result = it }, label = { Text(if (qualitative) "النتيجة النصية" else "النتيجة الرقمية") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (qualitative) KeyboardType.Text else KeyboardType.Decimal))
        ExposedDropdownMenuBox(expanded = unitMenuExpanded, onExpandedChange = { unitMenuExpanded = it }) {
            OutlinedTextField(value = if (customUnit) "وحدة مخصصة" else unit, onValueChange = {}, readOnly = true,
                label = { Text("وحدة النتيجة (اختياري)") }, placeholder = { Text("اختاري الوحدة") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
            ExposedDropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                (LAB_RESULT_UNITS + "أخرى / كتابة وحدة مخصصة").forEach { choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = {
                        val wasCustom = customUnit
                        customUnit = choice.startsWith("أخرى")
                        if (customUnit && !wasCustom) unit = ""
                        if (!customUnit) unit = if (choice == "بدون وحدة") "" else choice
                        unitMenuExpanded = false
                    })
                }
            }
        }
        if (customUnit) OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("اكتبي الوحدة كما في التقرير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (!qualitative) OutlinedTextField(low, { low = it }, label = { Text("الحد الأدنى المرجعي (اختياري)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (!qualitative) OutlinedTextField(high, { high = it }, label = { Text("الحد الأعلى المرجعي (اختياري)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        DatePickerField("تاريخ الفحص", date, { date = it }, Modifier.fillMaxWidth())
        OutlinedTextField(lab, { lab = it }, label = { Text("اسم المختبر (اختياري)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    } }, confirmButton = {
        TextButton(enabled = !saving, onClick = {
            if (saving) return@TextButton
            fun String.parseNumber() = trim().replace('٫', '.').replace(',', '.').map {
                it.digitToIntOrNull()?.digitToChar() ?: it
            }.joinToString("").toDoubleOrNull()?.takeIf { it.isFinite() }
            val number = if (qualitative) null else result.parseNumber()
            val lower = if (qualitative) null else low.parseNumber()
            val upper = if (qualitative) null else high.parseNumber()
            val validValue = if (qualitative) result.isNotBlank() else number != null
            val validRange = qualitative || ((low.isBlank() || lower != null) &&
                (high.isBlank() || upper != null) && (lower == null || upper == null || lower <= upper))
            if (name.isBlank() || !validValue || !validRange || runCatching { LocalDate.parse(date) }.isFailure) {
                error = "تحقق من اسم الفحص والنتيجة والتاريخ والمجال المرجعي."
            } else {
                val record = LabResult(
                    id = existing?.id ?: 0,
                    memberId = memberId, testName = name.trim(), value = number,
                    qualitativeValue = result.trim().takeIf { qualitative }, unit = unit.trim(),
                    referenceLow = lower, referenceHigh = upper, performedAt = date,
                    laboratory = lab.trim(), source = existing?.source ?: if (source != null) "report_review" else "manual",
                    notes = notes.trim(), createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
                saving = true
                scope.launch {
                    try {
                        if (onSave(record, source)) onDismiss() else error = "تعذر العثور على نتيجة الفحص."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "تعذر حفظ نتيجة الفحص."
                    } finally {
                        saving = false
                    }
                }
            }
        }) { Text(if (saving) "جارٍ الحفظ…" else if (source != null) "حفظ النتيجة وإرفاق المصدر" else "حفظ النتيجة") }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}

private val LAB_RESULT_UNITS = listOf(
    "بدون وحدة", "mg/dL", "mmol/L", "µmol/L", "ng/mL", "pg/mL", "g/dL", "g/L", "mg/L",
    "mEq/L", "mmol/mol", "%", "U/L", "IU/L", "mIU/L", "µIU/mL", "ng/dL", "µg/dL", "µg/L",
    "fL", "pg", "10³/µL", "10⁶/µL", "cells/µL", "mm/h", "mL/min/1.73m²", "copies/mL", "ثانية", "دقيقة"
)
private val LAB_TEST_NAME_SUGGESTIONS = listOf(
    "تعداد الدم الكامل (CBC)", "الهيموغلوبين (Hemoglobin)", "الهيماتوكريت (Hematocrit)",
    "كريات الدم البيضاء (WBC)", "كريات الدم الحمراء (RBC)", "الصفائح الدموية (Platelets)",
    "العدلات (Neutrophils)", "الخلايا اللمفاوية (Lymphocytes)", "الخلايا الحمضية (Eosinophils)",
    "سكر صائم (Fasting glucose)", "سكر عشوائي (Random glucose)", "سكر بعد الطعام (Post-meal glucose)",
    "الهيموغلوبين السكري (HbA1c)", "الكرياتينين (Creatinine)", "اليوريا (Urea)", "نيتروجين اليوريا (BUN)",
    "معدل الترشيح الكبيبي (eGFR)", "حمض اليوريك (Uric acid)", "إنزيم ALT", "إنزيم AST", "الفوسفاتاز القلوي (ALP)",
    "جاما جلوتاميل ترانسفيراز (GGT)", "البيليروبين الكلي (Total bilirubin)", "البيليروبين المباشر (Direct bilirubin)",
    "الألبومين (Albumin)", "الكوليسترول الكلي (Total cholesterol)", "الكوليسترول الضار (LDL)",
    "الكوليسترول النافع (HDL)", "الدهون الثلاثية (Triglycerides)", "الصوديوم (Sodium)", "البوتاسيوم (Potassium)",
    "الكلوريد (Chloride)", "الكالسيوم (Calcium)", "المغنيسيوم (Magnesium)", "الفوسفات (Phosphate)",
    "هرمون الغدة الدرقية المحفز (TSH)", "الثيروكسين الحر (Free T4)", "ثلاثي يودوثيرونين الحر (Free T3)",
    "فيتامين د (Vitamin D)", "فيتامين ب12 (Vitamin B12)", "حمض الفوليك (Folate)", "الفيريتين (Ferritin)",
    "الحديد (Iron)", "البروتين المتفاعل C (CRP)", "سرعة ترسيب الدم (ESR)", "تحليل البول الكامل (Urinalysis)",
    "مزرعة البول (Urine culture)", "مزرعة الدم (Blood culture)", "اختبار الحمل (hCG)", "زمن البروثرومبين (PT/INR)",
    "زمن الثرومبوبلاستين الجزئي (aPTT)", "الأميليز (Amylase)", "الليباز (Lipase)", "مستضد البروستاتا (PSA)",
    "الخلايا الوحيدة (Monocytes)", "الخلايا القاعدية (Basophils)", "الشبكيات (Reticulocytes)", "لطاخة الدم (Blood smear)",
    "الغازات في الدم (Blood gas)", "البيكربونات (Bicarbonate)", "ثاني أكسيد الكربون (CO2)", "الأسمولالية (Osmolality)",
    "اللاكتات (Lactate)", "بروتين كلي (Total protein)", "غلوبولين (Globulin)", "نسبة ألبومين/غلوبولين (A/G ratio)",
    "نازعة هيدروجين اللاكتات (LDH)", "كرياتين كيناز (CK)", "تروبونين (Troponin)", "الببتيد الدماغي المدر للصوديوم (BNP)",
    "الأمونيا (Ammonia)", "النحاس (Copper)", "الزنك (Zinc)", "فيتامين أ (Vitamin A)", "فيتامين هـ (Vitamin E)",
    "مستوى حمض الفالبرويك (Valproate level)", "مستوى الليثيوم (Lithium level)", "مستوى الديجوكسين (Digoxin level)",
    "مستضد التهاب الكبد B (HBsAg)", "أجسام مضادة لالتهاب الكبد C (Anti-HCV)", "فحص HIV", "فحص كوفيد-19 (COVID-19)",
    "فحص الإنفلونزا (Influenza)", "فحص جرثومة المعدة (H. pylori)", "أجسام مضادة للسيلياك (tTG-IgA)",
    "الأجسام المضادة للنواة (ANA)", "العامل الروماتويدي (Rheumatoid factor)", "أجسام مضادة CCP",
    "هرمون جار الدرقية (PTH)", "الكالسيتونين (Calcitonin)", "هرمون النمو (Growth hormone)", "البرولاكتين (Prolactin)",
    "التستوستيرون (Testosterone)", "الإستروجين (Estradiol)", "البروجسترون (Progesterone)",
    "الهرمون اللوتيني (LH)", "الهرمون المنبه للجريب (FSH)", "تحليل البراز (Stool analysis)", "الدم الخفي في البراز (FOBT)",
    "زرع البلغم (Sputum culture)", "مزرعة الحلق (Throat culture)", "مزرعة الجروح (Wound culture)",
    "الكثافة النوعية للبول (Urine specific gravity)", "الزلال في البول (Urine albumin)",
    "نسبة الألبومين/الكرياتينين في البول (UACR)", "تحليل آخر"
)
