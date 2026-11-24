package com.mkassis37.sehati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.AllergyRecord
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.SehatiDatabase
import com.mkassis37.sehati.ui.theme.SehatiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = SehatiDatabase.getInstance(applicationContext)
        setContent {
            SehatiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SehatiApp(db)
                }
            }
        }
    }
}

private enum class Screen { MEMBERS, DETAILS, FORM, ALLERGIES, SETTINGS, MEDICATIONS, APPOINTMENTS, DOCUMENTS, REPORTS, REMINDERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SehatiApp(db: SehatiDatabase) {
    val dao = db.medicalDao()
    var appInForeground by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appInForeground = true
                Lifecycle.Event.ON_STOP -> appInForeground = false
                else -> Unit
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }
    val members by dao.observeMembers().collectAsState(initial = emptyList())
    var screen by rememberSaveable { mutableStateOf(Screen.MEMBERS) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(appInForeground) {
        if (!appInForeground) {
            // Hide an open medical record when the app leaves the foreground.
            screen = Screen.MEMBERS
            selectedId = null
            editingId = null
        }
    }
    val selected = members.firstOrNull { it.id == selectedId }
    val allergyFlow = remember(selectedId) {
        selectedId?.let { dao.observeAllergies(it) } ?: flowOf(emptyList())
    }
    val allergies by allergyFlow.collectAsState(initial = emptyList())

    val title = when (screen) {
        Screen.MEMBERS -> "أفراد العائلة"
        Screen.DETAILS -> selected?.fullName ?: "الملف الطبي"
        Screen.FORM -> if (editingId == null) "إضافة فرد" else "تعديل البيانات"
        Screen.ALLERGIES -> "سجل الحساسيات"
        Screen.SETTINGS -> "الإعدادات"
        Screen.MEDICATIONS -> "الأدوية والعلاجات"
        Screen.APPOINTMENTS -> "المواعيد الطبية"
        Screen.DOCUMENTS -> "الملفات الطبية"
        Screen.REPORTS -> "التقارير الصحية"
        Screen.REMINDERS -> "التنبيهات والتذكيرات"
    }

    // Keep Android system-back inside the app instead of finishing the Activity.
    // Details returns to the family list so another member can be selected.
    val navigateBack: () -> Unit = {
        screen = when (screen) {
            Screen.FORM -> if (editingId == null) Screen.MEMBERS else Screen.DETAILS
            Screen.DETAILS -> Screen.MEMBERS
            Screen.ALLERGIES -> Screen.DETAILS
            Screen.SETTINGS, Screen.MEDICATIONS, Screen.APPOINTMENTS, Screen.DOCUMENTS,
            Screen.REPORTS, Screen.REMINDERS -> Screen.MEMBERS
            Screen.MEMBERS -> Screen.MEMBERS
        }
    }
    BackHandler(enabled = screen != Screen.MEMBERS) { navigateBack() }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(title)
                    if (screen == Screen.MEMBERS) Text("ملف صحي مستقل لكل فرد", style = MaterialTheme.typography.labelSmall)
                }
            },
            actions = {
                if (screen == Screen.MEMBERS) {
                    IconButton(onClick = { screen = Screen.SETTINGS }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "الإعدادات")
                    }
                }
            },
            navigationIcon = {
                if (screen != Screen.MEMBERS) {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع")
                    }
                }
            }
        )
    }) { padding ->
        when (screen) {
            Screen.MEMBERS -> MembersScreen(members, {
                editingId = null
                screen = Screen.FORM
            }, {
                selectedId = it
                screen = Screen.DETAILS
            }, Modifier.padding(padding))

            Screen.DETAILS -> if (selected != null) {
                MemberDetailsScreen(selected, { editingId = selected.id; screen = Screen.FORM },
                    { screen = Screen.ALLERGIES }, allergies.size, Modifier.padding(padding))
            } else {
                MembersScreen(members, { editingId = null; screen = Screen.FORM },
                    { selectedId = it; screen = Screen.DETAILS }, Modifier.padding(padding))
            }

            Screen.FORM -> {
                val editing = members.firstOrNull { it.id == editingId }
                MemberFormScreen(editing,
                    onCancel = { screen = if (editingId == null) Screen.MEMBERS else Screen.DETAILS },
                    onSave = { name, dob, blood, notes ->
                        if (editing == null) {
                            selectedId = dao.addMember(FamilyMember(
                                fullName = name, dateOfBirth = dob, bloodGroup = blood,
                                medicalNotes = notes, updatedAt = System.currentTimeMillis()
                            ))
                        } else {
                            dao.updateMember(editing.copy(
                                fullName = name, dateOfBirth = dob, bloodGroup = blood,
                                medicalNotes = notes, updatedAt = System.currentTimeMillis()
                            ))
                            selectedId = editing.id
                        }
                        screen = Screen.DETAILS
                    }, Modifier.padding(padding))
            }

            Screen.ALLERGIES -> if (selected != null) {
                AllergyScreen(selected, allergies,
                    addAllergy = { dao.addAllergy(it) },
                    deleteAllergy = { dao.deleteAllergy(it, selected.id) },
                    modifier = Modifier.padding(padding))
            }
            Screen.SETTINGS -> SettingsHub(
                onMedications = { screen = Screen.MEDICATIONS },
                onAppointments = { screen = Screen.APPOINTMENTS },
                onDocuments = { screen = Screen.DOCUMENTS },
                onReports = { screen = Screen.REPORTS },
                onReminders = { screen = Screen.REMINDERS },
                modifier = Modifier.padding(padding))
            Screen.MEDICATIONS -> FutureScreen("الأدوية والعلاجات", "سيُضاف هنا سجل الأدوية والجرعات لكل فرد.", Modifier.padding(padding))
            Screen.APPOINTMENTS -> FutureScreen("المواعيد الطبية", "سيُضاف هنا سجل المواعيد والزيارات الطبية.", Modifier.padding(padding))
            Screen.DOCUMENTS -> FutureScreen("الملفات الطبية", "سيُضاف هنا تنظيم الوثائق والصور الطبية.", Modifier.padding(padding))
            Screen.REPORTS -> FutureScreen("التقارير الصحية", "سيُضاف هنا إنشاء ملخصات وتقارير صحية.", Modifier.padding(padding))
            Screen.REMINDERS -> FutureScreen("التنبيهات والتذكيرات", "سيُضاف هنا تذكير المواعيد والمتابعات.", Modifier.padding(padding))
        }
    }
}

@Composable
private fun MembersScreen(
    members: List<FamilyMember>,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("الملفات الطبية", style = MaterialTheme.typography.titleLarge)
                Text("عدد الأفراد: ${members.size}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Text("إضافة فرد") }
        }
        if (members.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لا توجد ملفات طبية بعد.", style = MaterialTheme.typography.titleMedium)
                    Text("أضف أول فرد لتبدأ بإنشاء ملفه الصحي.")
                    Button(onClick = onAdd) { Text("إنشاء أول ملف") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(members, key = { it.id }) { member ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(member.id) }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                                val subtitle = listOfNotNull(
                                    member.dateOfBirth.takeIf { it.isNotBlank() }?.let { "الميلاد: ${it.toDisplayDate()}" },
                                    member.bloodGroup.takeIf { it.isNotBlank() }?.let { "فصيلة الدم: $it" }
                                ).joinToString(" • ")
                                Text(subtitle.ifBlank { "اضغط لعرض الملف الطبي" }, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("عرض")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberDetailsScreen(
    member: FamilyMember,
    onEdit: () -> Unit,
    onAllergies: () -> Unit,
    allergyCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(member.fullName, style = MaterialTheme.typography.headlineSmall)
                DetailLine("تاريخ الميلاد", member.dateOfBirth.toDisplayDate().ifBlank { "غير مسجل" })
                DetailLine("فصيلة الدم", member.bloodGroup.ifBlank { "غير مسجلة" })
                DetailLine("التاريخ والملاحظات الطبية", member.medicalNotes.ifBlank { "لا توجد ملاحظات" })
                Button(onClick = onEdit, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, null); Text("تعديل بيانات الفرد")
                }
            }
        }
        Card(Modifier.fillMaxWidth().clickable(onClick = onAllergies)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("سجل الحساسيات", style = MaterialTheme.typography.titleMedium)
                        Text("حساسيات هذا الفرد فقط", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("$allergyCount")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MemberFormScreen(
    member: FamilyMember?,
    onCancel: () -> Unit,
    onSave: suspend (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(member?.id) { mutableStateOf(member?.fullName.orEmpty()) }
    var dob by rememberSaveable(member?.id) { mutableStateOf(member?.dateOfBirth.orEmpty()) }
    var blood by rememberSaveable(member?.id) { mutableStateOf(member?.bloodGroup.orEmpty()) }
    var notes by rememberSaveable(member?.id) { mutableStateOf(member?.medicalNotes.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable(member?.id) { mutableStateOf("") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (member == null) "أدخل معلومات الفرد الجديد" else "تحديث معلومات ${member.fullName}",
            style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it; error = "" }, label = { Text("الاسم الكامل") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        DatePickerField("تاريخ الميلاد", dob, { dob = it }, Modifier.fillMaxWidth())
        OutlinedTextField(blood, { blood = it }, label = { Text("فصيلة الدم") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text("التاريخ والملاحظات الطبية") },
            modifier = Modifier.fillMaxWidth(), minLines = 4)
        Button(enabled = !saving && name.isNotBlank(), onClick = {
            scope.launch {
                saving = true
                error = ""
                try { onSave(name.trim(), dob, blood.trim(), notes.trim()) }
                catch (_: Exception) { error = "تعذّر حفظ البيانات. بقيت المعلومات المدخلة كما هي." }
                finally { saving = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "جارٍ الحفظ..." else "حفظ الملف") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text("تُحفظ ملفات الأفراد محليًا في قاعدة بيانات مشفّرة.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AllergyScreen(
    member: FamilyMember,
    records: List<AllergyRecord>,
    addAllergy: suspend (AllergyRecord) -> Unit,
    deleteAllergy: suspend (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable(member.id) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("حساسيات ${member.fullName}", style = MaterialTheme.typography.titleLarge)
                Text("هذا السجل خاص بهذا الفرد", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showDialog = true }) { Icon(Icons.Outlined.Add, null); Text("إضافة") }
        }
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Text("لا توجد حساسيات مسجلة لهذا الفرد.", Modifier.padding(20.dp)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.id }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.substance, style = MaterialTheme.typography.titleMedium)
                            Text("النوع: ${record.category}")
                            Text("الأعراض: ${record.reaction}")
                            Text("الشدة: ${record.severity}")
                            if (record.notes.isNotBlank()) Text(record.notes)
                            TextButton(onClick = { scope.launch { deleteAllergy(record.id) } }) { Text("حذف السجل") }
                        }
                    }
                }
            }
        }
    }
    if (showDialog) {
        AllergyDialog(member.id, onDismiss = { showDialog = false }, onSave = { record ->
            scope.launch { addAllergy(record) }
            showDialog = false
        })
    }
}

@Composable
private fun AllergyDialog(memberId: Long, onDismiss: () -> Unit, onSave: (AllergyRecord) -> Unit) {
    var category by rememberSaveable { mutableStateOf("دواء") }
    var substance by rememberSaveable { mutableStateOf("") }
    var reaction by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableStateOf("متوسطة") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حساسية") },
        text = {
            Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("التصنيف (دواء، طعام، بيئة)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(substance, { substance = it }, label = { Text("المادة المسببة") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(reaction, { reaction = it }, label = { Text("الأعراض أو رد الفعل") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(severity, { severity = it }, label = { Text("الشدة") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات إضافية") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = substance.isNotBlank(), onClick = {
                onSave(AllergyRecord(memberId = memberId,
                    category = category.trim().ifBlank { "غير محدد" }, substance = substance.trim(),
                    reaction = reaction.trim(), severity = severity.trim(), notes = notes.trim()))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(label: String, date: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(value = date.toDisplayDate(), onValueChange = {}, readOnly = true,
        label = { Text(label) }, placeholder = { Text("اختر التاريخ") },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "اختيار التاريخ")
            }
        }, modifier = modifier)
    if (showPicker) {
        val initialMillis = date.toUtcMillis() ?: System.currentTimeMillis()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إلغاء") } }
        ) { DatePicker(state = state, showModeToggle = true) }
    }
}

private fun String.toUtcMillis(): Long? = runCatching {
    LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

private fun String.toDisplayDate(): String {
    if (isBlank()) return ""
    return runCatching {
        val d = LocalDate.parse(this)
        "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
    }.getOrDefault(this)
}


@Composable
private fun SettingsHub(onMedications: () -> Unit, onAppointments: () -> Unit, onDocuments: () -> Unit, onReports: () -> Unit, onReminders: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إعدادات صحتي", style = MaterialTheme.typography.headlineSmall)
        Text("إدارة التطبيق والخدمات الصحية الحالية والمستقبلية.")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إدارة التطبيق", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("الأمان والقفل — سيتم ربط إعدادات PIN والبصمة هنا")
            Text("النسخ الاحتياطي والاستعادة — النسخ المشفر وحفظه على الجهاز")
            Text("المظهر واللغة — تخصيص الواجهة والخطوط")
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("الخدمات الصحية", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            FutureEntry("الأدوية والعلاجات", "سجل الأدوية والجرعات", onMedications)
            FutureEntry("المواعيد الطبية", "المراجعات والفحوصات", onAppointments)
            FutureEntry("الملفات الطبية", "الوثائق والصور الطبية", onDocuments)
            FutureEntry("التقارير الصحية", "ملخصات وتقارير لكل فرد", onReports)
            FutureEntry("التنبيهات والتذكيرات", "المواعيد والمتابعات القادمة", onReminders)
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("حول التطبيق", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("معلومات صحتي والإصدار وحقوق الإعداد — سيتم استكمالها لاحقًا")
        } }
    }
}

@Composable
private fun FutureEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("فتح", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FutureScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("القسم ضمن مسارات التطبيق", style = MaterialTheme.typography.titleMedium)
            Text(description)
            Text("سيتم تفعيل وظائفه وربطه بملفات أفراد العائلة في مرحلة التطوير الخاصة به.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}
