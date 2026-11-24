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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.room.RoomDatabase
import com.mkassis37.sehati.data.AllergyRecord
import com.mkassis37.sehati.data.MedicalProfile
import com.mkassis37.sehati.data.SehatiDatabase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = SehatiDatabase.getInstance(applicationContext)
        setContent {
            MaterialTheme {
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
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("الملف الطبي") }
                )
                NavigationBarItem(
                    selected = section == AppSection.ALLERGIES,
                    onClick = { section = AppSection.ALLERGIES },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
                    label = { Text("الحساسيات") }
                )
            }
        }
    ) { padding ->
        when (section) {
            AppSection.PROFILE -> ProfileScreen(
                profile = profile,
                onSave = { updated -> 
                    // Database writes are dispatched from the screen's coroutine scope.
                },
                dao = dao,
                modifier = Modifier.padding(padding)
            )
            AppSection.ALLERGIES -> AllergyScreen(
                records = allergies,
                onAdd = { record -> },
                onDelete = { id -> },
                dao = dao,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ProfileScreen(
    profile: MedicalProfile?,
    onSave: (MedicalProfile) -> Unit,
    dao: com.mkassis37.sehati.data.MedicalDao,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var name by remember(profile) { mutableStateOf(profile?.fullName.orEmpty()) }
    var dob by remember(profile) { mutableStateOf(profile?.dateOfBirth.orEmpty()) }
    var blood by remember(profile) { mutableStateOf(profile?.bloodGroup.orEmpty()) }
    var notes by remember(profile) { mutableStateOf(profile?.medicalNotes.orEmpty()) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("المعلومات الأساسية", style = MaterialTheme.typography.titleLarge)
        Text("يمكنك حفظ معلوماتك وتعديلها لاحقًا.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(name, { name = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(dob, { dob = it }, label = { Text("تاريخ الميلاد") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("مثال: 1990-05-20") })
        OutlinedTextField(blood, { blood = it }, label = { Text("فصيلة الدم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text("التاريخ والملاحظات الطبية") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Button(
            onClick = {
                scope.launch {
                    dao.saveProfile(
                        MedicalProfile(
                            fullName = name.trim(),
                            dateOfBirth = dob.trim(),
                            bloodGroup = blood.trim(),
                            medicalNotes = notes.trim()
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("حفظ الملف الطبي") }
        Spacer(Modifier.height(8.dp))
        Text("تُحفظ المعلومات على هذا الجهاز داخل قاعدة بيانات مشفّرة.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AllergyScreen(
    records: List<AllergyRecord>,
    onAdd: (AllergyRecord) -> Unit,
    onDelete: (Long) -> Unit,
    dao: com.mkassis37.sehati.data.MedicalDao,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("سجل الحساسيات", style = MaterialTheme.typography.titleLarge)
                Text("أضف المواد والأعراض وردود الفعل.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showDialog = true }) {
                androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = null)
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
                            TextButton(onClick = { scope.launch { dao.deleteAllergy(record.id) } }) {
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
                scope.launch { dao.addAllergy(record) }
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
