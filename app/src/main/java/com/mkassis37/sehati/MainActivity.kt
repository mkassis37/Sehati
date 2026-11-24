package com.mkassis37.sehati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.AllergyRecord
import com.mkassis37.sehati.data.MedicalProfile
import com.mkassis37.sehati.data.SehatiDatabase
import com.mkassis37.sehati.ui.theme.SehatiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = SehatiDatabase.getInstance(applicationContext)
        setContent {
            SehatiTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    SehatiApp(database)
                }
            }
        }
    }
}

private enum class AppSection { PROFILE, ALLERGIES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SehatiApp(database: SehatiDatabase) {
    var section by rememberSaveable { mutableStateOf(AppSection.PROFILE) }
    val dao = database.medicalDao()
    val profile by dao.observeProfile().collectAsState(initial = null)
    val allergies by dao.observeAllergies().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("صحتي")
                        Text("ملفك الصحي في مكان واحد", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == AppSection.PROFILE,
                    onClick = { section = AppSection.PROFILE },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("الملف الطبي") }
                )
                NavigationBarItem(
                    selected = section == AppSection.ALLERGIES,
                    onClick = { section = AppSection.ALLERGIES },
                    icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
                    label = { Text("الحساسيات") }
                )
            }
        }
    ) { padding ->
        when (section) {
            AppSection.PROFILE -> ProfileScreen(
                profile = profile,
                saveProfile = { dao.saveProfile(it) },
                modifier = Modifier.padding(padding)
            )
            AppSection.ALLERGIES -> AllergyScreen(
                records = allergies,
                addAllergy = { dao.addAllergy(it) },
                deleteAllergy = { dao.deleteAllergy(it) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ProfileScreen(
    profile: MedicalProfile?,
    saveProfile: suspend (MedicalProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var blood by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by rememberSaveable { mutableStateOf("") }

    // Load an existing profile without wiping text the user is currently entering.
    LaunchedEffect(profile?.updatedAt) {
        profile?.let {
            name = it.fullName
            dob = it.dateOfBirth
            blood = it.bloodGroup
            notes = it.medicalNotes
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("المعلومات الأساسية", style = MaterialTheme.typography.titleLarge)
        Text("أدخل بياناتك ثم اضغط حفظ الملف الطبي.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; saveMessage = "" },
            label = { Text("الاسم الكامل") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        DatePickerField(
            label = "تاريخ الميلاد",
            date = dob,
            onDateSelected = { dob = it; saveMessage = "" },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = blood,
            onValueChange = { blood = it; saveMessage = "" },
            label = { Text("فصيلة الدم") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it; saveMessage = "" },
            label = { Text("التاريخ والملاحظات الطبية") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        Button(
            enabled = !isSaving,
            onClick = {
                scope.launch {
                    isSaving = true
                    saveMessage = ""
                    try {
                        saveProfile(
                            MedicalProfile(
                                fullName = name.trim(),
                                dateOfBirth = dob,
                                bloodGroup = blood.trim(),
                                medicalNotes = notes.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        saveMessage = "تم حفظ الملف الطبي بنجاح."
                    } catch (_: Exception) {
                        saveMessage = "تعذّر حفظ الملف. لم تُحذف البيانات المدخلة؛ حاول مرة أخرى."
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSaving) "جارٍ الحفظ..." else "حفظ الملف الطبي")
        }
        if (saveMessage.isNotBlank()) {
            Text(
                saveMessage,
                color = if (saveMessage.startsWith("تم")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "تُحفظ المعلومات على هذا الجهاز داخل قاعدة بيانات مشفّرة.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Shared date field for all screens: opens the Material date picker.
 * Tapping the month/year heading allows direct year selection; month arrows
 * remain available for quick month-to-month navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    date: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = date.toDisplayDate(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("اختر التاريخ") },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "اختيار التاريخ")
            }
        },
        modifier = modifier
    )
    if (showPicker) {
        val initialMillis = date.toUtcMillis() ?: System.currentTimeMillis()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onDateSelected(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            )
                        }
                        showPicker = false
                    }
                ) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = state, showModeToggle = true)
        }
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
private fun AllergyScreen(
    records: List<AllergyRecord>,
    addAllergy: suspend (AllergyRecord) -> Unit,
    deleteAllergy: suspend (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("سجل الحساسيات", style = MaterialTheme.typography.titleLarge)
                Text("أضف المواد والأعراض وردود الفعل.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("إضافة")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("لا توجد حساسيات مسجلة حتى الآن.", modifier = Modifier.padding(20.dp))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.id }) { record ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.substance, style = MaterialTheme.typography.titleMedium)
                            Text("النوع: ${record.category}")
                            Text("الأعراض: ${record.reaction}")
                            Text("الشدة: ${record.severity}")
                            if (record.notes.isNotBlank()) Text(record.notes)
                            TextButton(onClick = { scope.launch { deleteAllergy(record.id) } }) {
                                Text("حذف السجل")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AllergyDialog(
            onDismiss = { showDialog = false },
            onSave = { record ->
                scope.launch { addAllergy(record) }
                showDialog = false
            }
        )
    }
}

@Composable
private fun AllergyDialog(onDismiss: () -> Unit, onSave: (AllergyRecord) -> Unit) {
    var category by rememberSaveable { mutableStateOf("دواء") }
    var substance by rememberSaveable { mutableStateOf("") }
    var reaction by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableStateOf("متوسطة") }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حساسية") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(category, { category = it }, label = { Text("التصنيف (دواء، طعام، بيئة)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(substance, { substance = it }, label = { Text("المادة المسببة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(reaction, { reaction = it }, label = { Text("الأعراض أو رد الفعل") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(severity, { severity = it }, label = { Text("الشدة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات إضافية") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                enabled = substance.isNotBlank(),
                onClick = {
                    onSave(
                        AllergyRecord(
                            category = category.trim().ifBlank { "غير محدد" },
                            substance = substance.trim(),
                            reaction = reaction.trim(),
                            severity = severity.trim(),
                            notes = notes.trim()
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
