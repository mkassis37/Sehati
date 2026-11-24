package com.mkassis37.sehati

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
import com.mkassis37.sehati.security.AppLockStore
import com.mkassis37.sehati.data.EncryptedBackup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.foundation.Canvas
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
import com.mkassis37.sehati.data.LabResult
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.SehatiDatabase
import com.mkassis37.sehati.ui.theme.SehatiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.mkassis37.sehati.update.ReleaseUpdater
import com.mkassis37.sehati.update.SehatiRelease

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = SehatiDatabase.getInstance(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            SehatiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (showSplash) {
                        SehatiSplashScreen()
                        LaunchedEffect(Unit) {
                            delay(1800)
                            showSplash = false
                            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        }
                    } else {
                        SehatiApp(db)
                    }
                }
            }
        }
    }
}

private enum class Screen { MEMBERS, DETAILS, FORM, ALLERGIES, LAB_RESULTS, SETTINGS, MEDICATIONS, APPOINTMENTS, DOCUMENTS, REPORTS, REMINDERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SehatiApp(db: SehatiDatabase) {
    val dao = db.medicalDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lockStore = remember { AppLockStore(context.applicationContext) }
    val documentStore = remember { SecureMedicalDocumentStore(context.applicationContext) }
    val backup = remember { EncryptedBackup(db, documentStore) }
    val releaseUpdater = remember { ReleaseUpdater(context.applicationContext) }
    var availableRelease by remember { mutableStateOf<SehatiRelease?>(null) }
    var updateStatus by remember { mutableStateOf("لم يتم فحص التحديثات بعد.") }
    var updateBusy by remember { mutableStateOf(false) }
    var appInForeground by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(lockStore.isEnabled()) }
    var lastBackgroundAt by remember { mutableLongStateOf(0L) }
    var settingsRefresh by remember { mutableIntStateOf(0) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var backupMode by remember { mutableStateOf("export") }
    var backupPassword by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPreview by remember { mutableStateOf<EncryptedBackup.BackupPreview?>(null) }
    var backupMessage by remember { mutableStateOf("") }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { backupUri = uri; backupMode = "export"; showBackupPassword = true }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { backupUri = uri; backupMode = "preview"; showBackupPassword = true }
    }
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appInForeground = true
                    if (lockStore.isEnabled() && lastBackgroundAt > 0L &&
                        System.currentTimeMillis() - lastBackgroundAt >= lockStore.timeoutMinutes() * 60_000L) locked = true
                }
                Lifecycle.Event.ON_STOP -> {
                    appInForeground = false
                    lastBackgroundAt = System.currentTimeMillis()
                }
                else -> Unit
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            updateStatus = "جارٍ فحص الإصدارات..."
            try {
                availableRelease = releaseUpdater.checkForUpdate(BuildConfig.VERSION_CODE)
                updateStatus = if (availableRelease == null) "أنت تستخدم أحدث إصدار." else "يتوفر تحديث ${availableRelease?.versionName}."
            } catch (_: Exception) { updateStatus = "تعذر فحص التحديثات. تحقق من الإنترنت وحاول مجددًا." }
        }
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
    val labFlow = remember(selectedId) { selectedId?.let { dao.observeLabResults(it) } ?: flowOf(emptyList()) }
    val labResults by labFlow.collectAsState(initial = emptyList())
    val documentFlow = remember(selectedId) { selectedId?.let { dao.observeDocuments(it) } ?: flowOf(emptyList()) }
    val medicalDocuments by documentFlow.collectAsState(initial = emptyList())

    val title = when (screen) {
        Screen.MEMBERS -> "أفراد العائلة"
        Screen.DETAILS -> selected?.fullName ?: "الملف الطبي"
        Screen.FORM -> if (editingId == null) "إضافة فرد" else "تعديل البيانات"
        Screen.ALLERGIES -> "سجل الحساسيات"
        Screen.LAB_RESULTS -> "الفحوصات المخبرية"
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
            Screen.ALLERGIES, Screen.LAB_RESULTS -> Screen.DETAILS
            Screen.SETTINGS, Screen.MEDICATIONS, Screen.APPOINTMENTS, Screen.DOCUMENTS,
            Screen.REPORTS, Screen.REMINDERS -> Screen.MEMBERS
            Screen.MEMBERS -> Screen.MEMBERS
        }
    }
    BackHandler(enabled = screen != Screen.MEMBERS) { navigateBack() }

    if (locked && lockStore.isEnabled()) {
        LockScreen(
            store = lockStore,
            onUnlocked = { locked = false },
            onBiometric = {
                val host = activity
                if (host != null && lockStore.isBiometricEnabled() &&
                    BiometricManager.from(host).canAuthenticate((if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG)) == BiometricManager.BIOMETRIC_SUCCESS) {
                    val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
                        })
                    prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                        .setTitle("فتح صحتي").setSubtitle("تحقق من هويتك للمتابعة")
                        .setAllowedAuthenticators(if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build())
                }
            }
        )
        return
    }

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
                    { screen = Screen.ALLERGIES }, allergies.size,
                    { screen = Screen.LAB_RESULTS }, labResults.size, Modifier.padding(padding))
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

            Screen.LAB_RESULTS -> if (selected != null) {
                LabResultsScreen(
                    selected, labResults, medicalDocuments,
                    addResult = { dao.addLabResult(it) },
                    deleteResult = { dao.deleteLabResult(it, selected.id) },
                    saveDocument = { uri ->
                        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                            ?: "تقرير طبي.pdf"
                        val saved = documentStore.save(uri, selected.id, name)
                        dao.addDocument(saved)
                    },
                    openDocument = { document ->
                        val file = documentStore.createTemporaryPdf(document)
                        val contentUri = FileProvider.getUriForFile(context, "com.mkassis37.sehati.fileprovider", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(contentUri, "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    },
                    deleteDocument = { document ->
                        scope.launch {
                            dao.deleteDocument(document.id, selected.id)
                            documentStore.delete(document)
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            Screen.ALLERGIES -> if (selected != null) {
                AllergyScreen(selected, allergies,
                    addAllergy = { dao.addAllergy(it) },
                    deleteAllergy = { dao.deleteAllergy(it, selected.id) },
                    modifier = Modifier.padding(padding))
            }
            Screen.SETTINGS -> SettingsHub(
                refreshToken = settingsRefresh,
                lockEnabled = lockStore.isEnabled(),
                biometricEnabled = lockStore.isBiometricEnabled(),
                timeoutMinutes = lockStore.timeoutMinutes(),
                onSetPin = { showPinSetup = true },
                onLockEnabled = { enabled ->
                    if (enabled && !lockStore.hasPin()) showPinSetup = true
                    else { lockStore.setEnabled(enabled); locked = false; settingsRefresh++ }
                },
                onBiometric = { enabled -> lockStore.setBiometricEnabled(enabled); settingsRefresh++ },
                onTimeout = { lockStore.setTimeoutMinutes(it); settingsRefresh++ },
                onExport = { createBackup.launch("sehati-backup.shtb") },
                onImport = { openBackup.launch(arrayOf("*/*")) },
                onMedications = { screen = Screen.MEDICATIONS },
                onAppointments = { screen = Screen.APPOINTMENTS },
                onDocuments = { screen = Screen.DOCUMENTS },
                onReports = { screen = Screen.REPORTS },
                onReminders = { screen = Screen.REMINDERS },
                updateStatus = updateStatus,
                updateBusy = updateBusy,
                onCheckUpdates = {
                    scope.launch {
                        updateBusy = true
                        updateStatus = "جارٍ فحص الإصدارات..."
                        try {
                            availableRelease = releaseUpdater.checkForUpdate(BuildConfig.VERSION_CODE)
                            updateStatus = if (availableRelease == null) "أنت تستخدم أحدث إصدار." else "يتوفر تحديث ${availableRelease?.versionName}."
                        } catch (_: Exception) { updateStatus = "تعذر فحص التحديثات. حاول مجددًا." }
                        finally { updateBusy = false }
                    }
                },
                modifier = Modifier.padding(padding))
            Screen.MEDICATIONS -> FutureScreen("الأدوية والعلاجات", "سيُضاف هنا سجل الأدوية والجرعات لكل فرد.", Modifier.padding(padding))
            Screen.APPOINTMENTS -> FutureScreen("المواعيد الطبية", "سيُضاف هنا سجل المواعيد والزيارات الطبية.", Modifier.padding(padding))
            Screen.DOCUMENTS -> FutureScreen("الملفات الطبية", "سيُضاف هنا تنظيم الوثائق والصور الطبية.", Modifier.padding(padding))
            Screen.REPORTS -> FutureScreen("التقارير الصحية", "سيُضاف هنا إنشاء ملخصات وتقارير صحية.", Modifier.padding(padding))
            Screen.REMINDERS -> FutureScreen("التنبيهات والتذكيرات", "سيُضاف هنا تذكير المواعيد والمتابعات.", Modifier.padding(padding))
        }
    }

    availableRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { availableRelease = null },
            title = { Text("يتوفر تحديث جديد لصحتي") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(release.title, style = MaterialTheme.typography.titleMedium)
                Text("الإصدار ${release.versionName} (رقم البناء ${release.versionCode})")
                Text(release.notes.take(2500))
                Text("سيتم التحقق من سلامة الملف قبل فتح مثبت أندرويد. لا تحذف التطبيق أثناء التحديث.", style = MaterialTheme.typography.bodySmall)
                Text(updateStatus, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = !updateBusy, onClick = {
                scope.launch {
                    updateBusy = true
                    updateStatus = "جارٍ تنزيل التحديث والتحقق منه..."
                    try { updateStatus = releaseUpdater.downloadVerifyAndInstall(release) }
                    catch (_: Exception) { updateStatus = "تعذر تنزيل التحديث أو التحقق منه. لم يتم تشغيل المثبت." }
                    finally { updateBusy = false }
                }
            }) { Text(if (updateBusy) "جارٍ العمل..." else "تنزيل وتثبيت") } },
            dismissButton = { TextButton(onClick = { availableRelease = null }) { Text("لاحقًا") } }
        )
    }

    if (showPinSetup) PinSetupDialog(
        onDismiss = { showPinSetup = false },
        onSave = { pin ->
            lockStore.setPin(pin.toCharArray())
            lockStore.setEnabled(true)
            locked = false
            showPinSetup = false
            settingsRefresh++
        }
    )

    if (showBackupPassword) BackupPasswordDialog(
        mode = backupMode,
        value = backupPassword,
        onValue = { backupPassword = it },
        message = backupMessage,
        onDismiss = { showBackupPassword = false; backupPassword = "" },
        onSubmit = {
            val uri = backupUri
            val pass = backupPassword.toCharArray()
            showBackupPassword = false
            backupPassword = ""
            if (uri != null) {
                scope.launch {
                    try {
                        if (backupMode == "export") {
                            backup.write(context.contentResolver, uri, pass)
                            backupMessage = "تم إنشاء النسخة الاحتياطية المشفّرة."
                        } else {
                            backupPreview = backup.preview(context.contentResolver, uri, pass)
                            backupMessage = ""
                        }
                    } catch (e: Exception) {
                        backupMessage = "تعذّرت العملية. تأكد من كلمة المرور وسلامة الملف."
                    }
                }
            }
        }
    )

    backupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { backupPreview = null },
            title = { Text("معاينة النسخة الاحتياطية") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("عدد الأفراد: ${preview.memberNames.size}")
                preview.memberNames.forEach { Text("• $it") }
                Text("عدد سجلات الحساسية: ${preview.allergyCount}")
                Text("عدد نتائج الفحوصات: ${preview.labResultCount}")
                Text("عدد التقارير والمرفقات: ${preview.documentCount}")
                Text("استعادة النسخة ستستبدل البيانات الحالية بالكامل.")
            } },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { backup.restore(preview); backupMessage = "تمت استعادة النسخة الاحتياطية." }
                    catch (_: Exception) { backupMessage = "فشلت الاستعادة ولم تكتمل." }
                    finally { backupPreview = null }
                }
            }) { Text("استعادة واستبدال البيانات") } },
            dismissButton = { TextButton(onClick = { backupPreview = null }) { Text("إلغاء") } }
        )
    }

    if (backupMessage.isNotBlank()) AlertDialog(
        onDismissRequest = { backupMessage = "" }, title = { Text("النسخ الاحتياطي") },
        text = { Text(backupMessage) }, confirmButton = { TextButton(onClick = { backupMessage = "" }) { Text("حسنًا") } }
    )
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
    onLabResults: () -> Unit,
    labCount: Int,
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
        Card(Modifier.fillMaxWidth().clickable(onClick = onLabResults)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("الفحوصات المخبرية", style = MaterialTheme.typography.titleMedium)
                    Text("إدخال النتائج ومقارنتها عبر السنوات", style = MaterialTheme.typography.bodySmall)
                }
                Text(labCount.toString())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabResultsScreen(member: FamilyMember, records: List<LabResult>, documents: List<MedicalDocument>, addResult: suspend (LabResult) -> Long, deleteResult: suspend (Long) -> Unit, saveDocument: suspend (Uri) -> Unit, openDocument: (MedicalDocument) -> Unit, deleteDocument: (MedicalDocument) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable(member.id) { mutableStateOf(false) }
    var reviewCandidate by remember(member.id) { mutableStateOf<LabReportCandidate?>(null) }
    var selectedTest by rememberSaveable(member.id) { mutableStateOf("") }
    var years by rememberSaveable(member.id) { mutableIntStateOf(0) }
    val tests = records.map { it.testName }.distinct().sorted()
    val filtered = records.filter { selectedTest.isBlank() || it.testName == selectedTest }.filter { row -> years == 0 || runCatching { LocalDate.parse(row.performedAt).isAfter(LocalDate.now().minusYears(years.toLong()).minusDays(1)) }.getOrDefault(false) }
    val chronological = filtered.sortedBy { it.performedAt }
    val latest = chronological.lastOrNull()
    val previous = chronological.dropLast(1).lastOrNull()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("فحوصات " + member.fullName, style = MaterialTheme.typography.titleLarge)
        Text("تُحفظ كل نتيجة بتاريخها ومجالها المرجعي، وتبقى متاحة للمقارنة لسنوات.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة نتيجة فحص") }
        LabPdfReader(
            onExtractedText = { /* تتم المعاينة محليًا فقط؛ لا يُحفظ النص الخام */ },
            onCandidateSelected = { candidate -> reviewCandidate = candidate; showAdd = true },
            onSavePdf = saveDocument
        )
        Text("التقارير المحفوظة في ملف " + member.fullName, style = MaterialTheme.typography.titleMedium)
        if (documents.isEmpty()) {
            Text("لم يتم حفظ تقارير PDF لهذا الفرد بعد.", style = MaterialTheme.typography.bodySmall)
        } else {
            documents.forEach { document ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(document.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(String.format("%.1f KB", document.sizeBytes / 1024.0), style = MaterialTheme.typography.bodySmall)
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selectedTest.isBlank(), onClick = { selectedTest = "" }, label = { Text("الكل") })
                tests.forEach { name -> FilterChip(selected = selectedTest == name, onClick = { selectedTest = name }, label = { Text(name) }) }
            }
            Text("الفترة الزمنية")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 3, 5).forEach { count -> FilterChip(selected = years == count, onClick = { years = count }, label = { Text(if (count == 0) "كل السنوات" else count.toString() + " سنوات") }) }
            }
        }
        if (latest != null && selectedTest.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("أحدث نتيجة: " + latest.testName, style = MaterialTheme.typography.titleMedium)
                Text(latest.value.toString() + " " + latest.unit, style = MaterialTheme.typography.headlineSmall)
                Text("التاريخ: " + latest.performedAt.toDisplayDate())
                if (previous != null) Text("مقارنة بالسابقة: " + (latest.value - previous.value).toString() + " " + latest.unit + " (" + previous.performedAt.toDisplayDate() + ")") else Text("لا توجد نتيجة أقدم للمقارنة.")
                val low = latest.referenceLow; val high = latest.referenceHigh
                if (low != null || high != null) {
                    val status = when { low != null && latest.value < low -> "أقل من المجال المرجعي المسجل"; high != null && latest.value > high -> "أعلى من المجال المرجعي المسجل"; else -> "ضمن المجال المرجعي المسجل" }
                    Text(status, color = if (status.startsWith("ضمن")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text("المجال: " + (low?.toString() ?: "—") + " إلى " + (high?.toString() ?: "—") + " " + latest.unit)
                }
            } }
            if (chronological.size >= 2) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التغير عبر الزمن", style = MaterialTheme.typography.titleMedium)
                    val chartLineColor = MaterialTheme.colorScheme.primary
                    val chartPointColor = MaterialTheme.colorScheme.tertiary
                    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                        val values = chronological.map { it.value }; val min = values.minOrNull() ?: 0.0; val max = values.maxOrNull() ?: 1.0; val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                        val points = values.mapIndexed { index, value -> androidx.compose.ui.geometry.Offset(if (values.size == 1) size.width / 2 else size.width * index / (values.size - 1), size.height - ((value - min) / range).toFloat() * (size.height - 16.dp.toPx()) - 8.dp.toPx()) }
                        points.zipWithNext().forEach { pair -> drawLine(chartLineColor, pair.first, pair.second, strokeWidth = 4f) }
                        points.forEach { drawCircle(chartPointColor, 5f, it) }
                    }
                    Text(chronological.first().performedAt.toDisplayDate() + " ← " + chronological.last().performedAt.toDisplayDate(), style = MaterialTheme.typography.labelSmall)
                } }
            }
        }
        if (selectedTest.isBlank() && tests.isNotEmpty()) Text("اختاري فحصًا محددًا لعرض المقارنة والرسم البياني؛ لا نقارن فحوصات مختلفة أو بوحدات مختلفة.")
        Text("سجل النتائج (" + filtered.size + ")", style = MaterialTheme.typography.titleMedium)
        if (filtered.isEmpty()) Text("لا توجد نتائج ضمن الاختيار الحالي. أضف أول فحص للبدء.")
        filtered.forEach { row -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(row.testName, style = MaterialTheme.typography.titleSmall); Text(row.value.toString() + " " + row.unit); Text(row.performedAt.toDisplayDate() + " • " + row.laboratory.ifBlank { "المختبر غير محدد" }, style = MaterialTheme.typography.bodySmall); Text("المرجع: " + (row.referenceLow?.toString() ?: "—") + " إلى " + (row.referenceHigh?.toString() ?: "—"), style = MaterialTheme.typography.bodySmall); if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { scope.launch { deleteResult(row.id) } }) { Text("حذف") }
        } } }
    }
    if (showAdd) AddLabResultDialog(member.id, { showAdd = false; reviewCandidate = null }, reviewCandidate) { row -> scope.launch { addResult(row) }; showAdd = false; reviewCandidate = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLabResultDialog(memberId: Long, onDismiss: () -> Unit, initialCandidate: LabReportCandidate? = null, onSave: (LabResult) -> Unit) {
    var name by rememberSaveable(initialCandidate?.testName) { mutableStateOf(initialCandidate?.testName.orEmpty()) }; var result by rememberSaveable(initialCandidate?.value) { mutableStateOf(initialCandidate?.value?.toString().orEmpty()) }; var unit by rememberSaveable(initialCandidate?.unit) { mutableStateOf(initialCandidate?.unit.orEmpty()) }; var low by rememberSaveable(initialCandidate?.referenceLow) { mutableStateOf(initialCandidate?.referenceLow?.toString().orEmpty()) }; var high by rememberSaveable(initialCandidate?.referenceHigh) { mutableStateOf(initialCandidate?.referenceHigh?.toString().orEmpty()) }; var date by rememberSaveable(initialCandidate?.testName) { mutableStateOf(LocalDate.now().toString()) }; var lab by rememberSaveable(initialCandidate?.testName) { mutableStateOf("") }; var notes by rememberSaveable(initialCandidate?.sourceLine) { mutableStateOf(initialCandidate?.let { "اقتراح مستخرج من PDF. راجع النتيجة والوحدة والمجال والتاريخ مقابل التقرير الأصلي." }.orEmpty()) }; var error by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة نتيجة فحص") }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("اسم الفحص") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(result, { result = it }, label = { Text("النتيجة الرقمية") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(unit, { unit = it }, label = { Text("الوحدة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(low, { low = it }, label = { Text("الحد الأدنى المرجعي (اختياري)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(high, { high = it }, label = { Text("الحد الأعلى المرجعي (اختياري)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        DatePickerField("تاريخ الفحص", date, { date = it }, Modifier.fillMaxWidth())
        OutlinedTextField(lab, { lab = it }, label = { Text("اسم المختبر (اختياري)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    } }, confirmButton = { TextButton(onClick = { val number = result.toDoubleOrNull(); val lower = low.takeIf { it.isNotBlank() }?.toDoubleOrNull(); val upper = high.takeIf { it.isNotBlank() }?.toDoubleOrNull(); if (name.isBlank() || number == null || (low.isNotBlank() && lower == null) || (high.isNotBlank() && upper == null) || (lower != null && upper != null && lower > upper)) error = "تحقق من اسم الفحص والنتيجة والمجال المرجعي." else onSave(LabResult(memberId = memberId, testName = name.trim(), value = number, unit = unit.trim(), referenceLow = lower, referenceHigh = upper, performedAt = date, laboratory = lab.trim(), source = if (initialCandidate != null) "pdf_review" else "manual", notes = notes.trim())) }) { Text("حفظ النتيجة") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
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
private fun SettingsHub(
    refreshToken: Int,
    lockEnabled: Boolean, biometricEnabled: Boolean, timeoutMinutes: Int,
    onSetPin: () -> Unit, onLockEnabled: (Boolean) -> Unit, onBiometric: (Boolean) -> Unit,
    onTimeout: (Int) -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onMedications: () -> Unit, onAppointments: () -> Unit, onDocuments: () -> Unit,
    onReports: () -> Unit, onReminders: () -> Unit,
    updateStatus: String, updateBusy: Boolean, onCheckUpdates: () -> Unit, modifier: Modifier = Modifier
) {
    val refresh = refreshToken
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إعدادات صحتي", style = MaterialTheme.typography.headlineSmall)
        Text("إدارة التطبيق والخدمات الصحية الحالية والمستقبلية.")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إدارة التطبيق", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("قفل التطبيق"); Text("رمز PIN من 6 أرقام", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = lockEnabled, onCheckedChange = onLockEnabled)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("تغيير أو إعداد PIN"); Text("الرمز الاحتياطي لفتح التطبيق", style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = onSetPin) { Text("إعداد") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("فتح بالبصمة أو قفل الجهاز"); Text("عند دعم الجهاز", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = biometricEnabled, onCheckedChange = onBiometric)
            }
            Text("القفل بعد مغادرة التطبيق: $timeoutMinutes دقيقة")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 30).forEach { minutes ->
                    FilterChip(selected = timeoutMinutes == minutes, onClick = { onTimeout(minutes) }, label = { Text("$minutes د") })
                }
            }
            Divider()
            Text("النسخ الاحتياطي المشفّر", style = MaterialTheme.typography.titleSmall)
            Text("كلمة مرور مستقلة عن PIN، لا تقل عن 10 أحرف. لا يتم رفع أي بيانات تلقائيًا.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport) { Text("إنشاء نسخة") }
                OutlinedButton(onClick = onImport) { Text("استعادة نسخة") }
            }
            Divider()
            Text("تحديث التطبيق", style = MaterialTheme.typography.titleSmall)
            Text(updateStatus, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !updateBusy, onClick = onCheckUpdates) { Text("فحص التحديثات الآن") }
            Text("يتم التحقق من بصمة SHA-256 قبل فتح مثبت أندرويد.", style = MaterialTheme.typography.bodySmall)
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


@Composable
private fun LockScreen(store: AppLockStore, onUnlocked: () -> Unit, onBiometric: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("صحتي", style = MaterialTheme.typography.headlineLarge)
        Text("التطبيق مقفل لحماية الملفات الطبية", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it; error = "" },
            label = { Text("رمز PIN من 6 أرقام") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (store.isTemporarilyLocked()) Text("محاولات كثيرة. حاول بعد " + (store.remainingLockMillis()/1000) + " ثانية.", color = MaterialTheme.colorScheme.error)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = pin.length == 6 && !store.isTemporarilyLocked(), onClick = {
            if (store.verifyPin(pin.toCharArray())) { pin = ""; onUnlocked() }
            else { pin = ""; error = "الرمز غير صحيح." }
        }, modifier = Modifier.fillMaxWidth()) { Text("فتح التطبيق") }
        TextButton(onClick = onBiometric) { Text("استخدام البصمة / قفل الجهاز") }
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إعداد رمز PIN") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("اختر رمزًا من 6 أرقام وكرره للتأكيد.")
            OutlinedTextField(first, { if (it.length <= 6 && it.all(Char::isDigit)) first = it }, label = { Text("PIN الجديد") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(second, { if (it.length <= 6 && it.all(Char::isDigit)) second = it }, label = { Text("تأكيد PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        if (first.length != 6 || first != second) error = "تأكد من إدخال الرمز نفسه مرتين."
        else onSave(first)
    }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun BackupPasswordDialog(
    mode: String, value: String, onValue: (String) -> Unit, message: String,
    onDismiss: () -> Unit, onSubmit: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (mode == "export") "تشفير النسخة الاحتياطية" else "فتح النسخة الاحتياطية") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (mode == "export") "أدخل كلمة مرور مستقلة لا تقل عن 10 أحرف. احتفظ بها؛ لا يمكن استعادة النسخة بدونها." else "أدخل كلمة المرور التي استُخدمت عند إنشاء النسخة.")
            OutlinedTextField(value, onValue, label = { Text("كلمة مرور النسخة") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = value.length >= (if (mode == "export") 10 else 1), onClick = onSubmit) { Text("متابعة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
