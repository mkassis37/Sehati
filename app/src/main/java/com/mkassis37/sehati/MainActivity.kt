package com.mkassis37.sehati

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
import com.mkassis37.sehati.security.AppLockStore
import com.mkassis37.sehati.reminders.MedicalReminders
import com.mkassis37.sehati.reminders.ReminderSettings
import com.mkassis37.sehati.reports.MedicalReportScreen
import com.mkassis37.sehati.reports.rememberMedicalReportActions
import com.mkassis37.sehati.reports.reportRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.mkassis37.sehati.data.EncryptedBackup
import com.mkassis37.sehati.data.BackupSizeLimitException
import com.mkassis37.sehati.data.BackupStatusStore
import com.mkassis37.sehati.drive.rememberDriveAuthorization
import com.mkassis37.sehati.drive.scheduleDriveBackup
import com.mkassis37.sehati.data.AppearanceSettingsStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.room.invalidationTrackerFlow
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.data.AllergyRecord
import com.mkassis37.sehati.data.AppointmentStatus
import com.mkassis37.sehati.data.displayValue
import com.mkassis37.sehati.data.FamilyMember
import com.mkassis37.sehati.data.MedicalDocument
import com.mkassis37.sehati.data.MedicationRecord
import com.mkassis37.sehati.data.SecureMedicalDocumentStore
import com.mkassis37.sehati.data.SehatiDatabase
import com.mkassis37.sehati.ui.theme.SehatiTheme
import com.mkassis37.sehati.ui.theme.AppearanceSettings
import com.mkassis37.sehati.ui.theme.SehatiPalette
import com.mkassis37.sehati.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.mkassis37.sehati.update.ReleaseUpdater
import com.mkassis37.sehati.update.SehatiRelease

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = runCatching { SehatiDatabase.getInstance(applicationContext) }.getOrNull()
        if (db == null) {
            setContent {
                SehatiTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center) {
                            Text("تعذر فتح البيانات المشفرة", style = MaterialTheme.typography.headlineSmall)
                            Text("افتح قفل الهاتف وأعد تشغيل صحتي. إذا بقي المفتاح غير متاح، استخدم نسخة احتياطية مشفرة لاستعادة سجلاتك على تثبيت منفصل.")
                        }
                    }
                }
            }
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContent {
            val appearanceStore = remember { AppearanceSettingsStore(applicationContext) }
            val appearance by appearanceStore.settings.collectAsState(initial = AppearanceSettings())
            val appearanceScope = rememberCoroutineScope()
            var showSplash by remember { mutableStateOf(savedInstanceState == null) }
            SehatiTheme(appearance = appearance) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (showSplash) {
                        SehatiSplashScreen()
                        LaunchedEffect(Unit) {
                            delay(1800)
                            showSplash = false
                            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        }
                    } else {
                        SehatiApp(
                            db = db,
                            appearance = appearance,
                            onPaletteSelected = { palette -> appearanceScope.launch { appearanceStore.updatePalette(palette) } },
                            onThemeModeSelected = { mode -> appearanceScope.launch { appearanceStore.updateMode(mode) } },
                            onResetAppearance = { appearanceScope.launch { appearanceStore.reset() } }
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { MEMBERS, DETAILS, FORM, HEALTH_RECORDS, ALLERGIES, LAB_RESULTS, SETTINGS, SEARCH, MEDICATIONS, APPOINTMENTS, DOCUMENTS, REPORTS, REMINDERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SehatiApp(
    db: SehatiDatabase,
    appearance: AppearanceSettings,
    onPaletteSelected: (SehatiPalette) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onResetAppearance: () -> Unit
) {
    val dao = db.medicalDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val reminders = remember(context, db) { MedicalReminders(context, db) }
    LaunchedEffect(reminders) {
        db.invalidationTrackerFlow("medication_records", "medical_appointments", "dose_logs").collect {
            try {
                reminders.updateAlarms()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The notification settings provide a retry and status.
            }
        }
    }
    val activity = context as? FragmentActivity
    val authenticators = if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
    val biometricAvailable = activity != null &&
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    val lockStore = remember { AppLockStore(context.applicationContext) }
    val documentStore = remember { SecureMedicalDocumentStore(context.applicationContext) }
    val backup = remember { EncryptedBackup(db, documentStore) }
    val backupStatusStore = remember { BackupStatusStore(context.applicationContext) }
    val releaseUpdater = remember { ReleaseUpdater(context.applicationContext) }
    var availableRelease by remember { mutableStateOf<SehatiRelease?>(null) }
    var updateStatus by remember { mutableStateOf("لم يتم فحص التحديثات بعد.") }
    var updateBusy by remember { mutableStateOf(false) }
    var appInForeground by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(lockStore.isEnabled()) }
    var lastBackgroundAt by remember { mutableLongStateOf(0L) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var settingsRefresh by remember { mutableIntStateOf(0) }
    var screen by rememberSaveable { mutableStateOf(Screen.MEMBERS) }
    var settingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var medicationOpenedFromSettings by rememberSaveable { mutableStateOf(false) }
    var lastBackupAt by remember { mutableStateOf(backupStatusStore.lastSuccessAt) }
    var lastBackupSizeBytes by remember { mutableStateOf(backupStatusStore.lastSizeBytes) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var backupMode by remember { mutableStateOf("export") }
    var backupPassword by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPreview by remember { mutableStateOf<EncryptedBackup.BackupPreview?>(null) }
    var restoringBackup by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf("") }
    fun discardCurrentBackupPreview() {
        backupPreview?.let(backup::discardPreview)
        backupPreview = null
    }
    val driveAuthorization = rememberDriveAuthorization(backup) {
        settingsSection = SettingsSection.DRIVE
        screen = Screen.SETTINGS
    }
    LaunchedEffect(context) {
        if (BuildConfig.GOOGLE_DRIVE_ENABLED) scheduleDriveBackup(context)
    }
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
                    lastInteractionAt = System.currentTimeMillis()
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
        documentStore.clearTemporaryPdfs()
        backup.clearStalePreviewFiles()
    }
    LaunchedEffect(locked) {
        if (locked) discardCurrentBackupPreview()
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
    var searchText by rememberSaveable { mutableStateOf("") }
    var searchMemberId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusedRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusFromTimeline by rememberSaveable { mutableStateOf(false) }
    var focusedRecordKind by rememberSaveable { mutableStateOf<String?>(null) }
    var quickAddLab by rememberSaveable { mutableStateOf(false) }
    var quickDoseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var healthInitialTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(lockStore.isEnabled(), lockStore.timeoutMinutes(), appInForeground, locked) {
        while (lockStore.isEnabled() && appInForeground && !locked) {
            delay(1_000)
            if (System.currentTimeMillis() - lastInteractionAt >= lockStore.timeoutMinutes() * 60_000L) {
                // Keep the current member and destination behind the lock screen.
                // After authentication, return to the same medical section.
                locked = true
                break
            }
        }
    }
    // Keep the current medical section and selected family member when the app
    // temporarily leaves the foreground (for example, to open a PDF picker).
    // FLAG_SECURE remains enabled, and the configured app lock still covers the UI
    // after its timeout; unlocking restores this same destination.
    val selected = members.firstOrNull { it.id == selectedId }
    val allergyFlow = remember(selectedId) {
        selectedId?.let { dao.observeAllergies(it) } ?: flowOf(emptyList())
    }
    val allergies by key(selectedId) { allergyFlow.collectAsState(initial = emptyList()) }
    val labFlow = remember(selectedId) { selectedId?.let { dao.observeLabResults(it) } ?: flowOf(emptyList()) }
    val labResults by key(selectedId) { labFlow.collectAsState(initial = emptyList()) }
    val documentFlow = remember(selectedId) { selectedId?.let { dao.observeDocuments(it) } ?: flowOf(emptyList()) }
    val medicalDocuments by key(selectedId) { documentFlow.collectAsState(initial = emptyList()) }
    val appointmentFlow = remember(selectedId) {
        selectedId?.let { dao.observeAppointments(it) } ?: flowOf(emptyList())
    }
    val appointments by key(selectedId) { appointmentFlow.collectAsState(initial = emptyList()) }
    val medicationFlow = remember(selectedId) { selectedId?.let { dao.observeMedications(it) } ?: flowOf(emptyList()) }
    val doseLogFlow = remember(selectedId) { selectedId?.let { dao.observeDoseLogs(it) } ?: flowOf(emptyList()) }
    val doseLogs by key(selectedId) { doseLogFlow.collectAsState(initial = emptyList()) }
    val medications by key(selectedId) { medicationFlow.collectAsState(initial = emptyList()) }
    val conditionFlow = remember(selectedId) { selectedId?.let { dao.observeConditions(it) } ?: flowOf(emptyList()) }
    val conditions by key(selectedId) { conditionFlow.collectAsState(initial = emptyList()) }
    val procedureFlow = remember(selectedId) { selectedId?.let { dao.observeProcedures(it) } ?: flowOf(emptyList()) }
    val procedures by key(selectedId) { procedureFlow.collectAsState(initial = emptyList()) }
    val historyFlow = remember(selectedId) { selectedId?.let { dao.observeHistoryEvents(it) } ?: flowOf(emptyList()) }
    val historyEvents by key(selectedId) { historyFlow.collectAsState(initial = emptyList()) }
    val familyHistoryFlow = remember(selectedId) { selectedId?.let { dao.observeFamilyDiseaseHistory(it) } ?: flowOf(emptyList()) }
    val familyHistory by key(selectedId) { familyHistoryFlow.collectAsState(initial = emptyList()) }
    val measurementFlow = remember(selectedId) { selectedId?.let { dao.observeBodyMeasurements(it) } ?: flowOf(emptyList()) }
    val measurements by key(selectedId) { measurementFlow.collectAsState(initial = emptyList()) }

    val appointmentAttachments = rememberAppointmentAttachments(db, documentStore) { memberId ->
        if (selectedId == null || selectedId == memberId) {
            selectedId = memberId
            screen = Screen.APPOINTMENTS
        }
    }

    val documentLibrary = rememberDocumentLibrary(db, documentStore) { memberId ->
        if (selectedId == null || selectedId == memberId) {
            selectedId = memberId
            screen = Screen.DOCUMENTS
        }
    }

    fun documentName(uri: Uri, fallbackName: String): String =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: fallbackName

    suspend fun saveMemberDocument(
        uri: Uri, memberId: Long, fallbackName: String, category: DocumentCategory,
        procedureId: Long? = null
    ) {
        withContext(Dispatchers.IO + NonCancellable) {
            val name = documentName(uri, fallbackName)
            val document = documentStore.save(uri, memberId, name)
            try {
                db.addLinkedDocument(document.copy(category = category.name, relatedProcedureId = procedureId))
            } catch (error: Exception) {
                documentStore.delete(document)
                throw error
            }
        }
    }

    fun deleteMemberDocument(document: MedicalDocument) {
        scope.launch {
            try {
                documentLibrary.delete(document)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "تعذر حذف المرفق.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val reportActions = rememberMedicalReportActions()

    fun <T> matchingRecords(kind: String, records: List<T>, recordId: (T) -> Long): List<T> {
        val wanted = focusedRecordId
        return if (wanted == null || focusedRecordKind != kind) records else records.filter { recordId(it) == wanted }
    }

    fun openRecord(kind: String, id: Long, fromTimeline: Boolean) {
        focusedRecordId = id
        focusedRecordKind = kind
        focusFromTimeline = fromTimeline
        screen = when (kind) {
            "ALLERGY" -> Screen.ALLERGIES
            "LAB" -> Screen.LAB_RESULTS
            "MEDICATION" -> Screen.MEDICATIONS
            "VISIT" -> Screen.APPOINTMENTS
            "DOCUMENT" -> Screen.DOCUMENTS
            "CONDITION", "PROCEDURE", "HISTORY", "FAMILY_HISTORY", "MEASUREMENT" -> {
                healthInitialTab = when (kind) {
                    "CONDITION" -> 1
                    "PROCEDURE" -> 2
                    "HISTORY" -> 3
                    "FAMILY_HISTORY" -> 4
                    else -> 5
                }
                Screen.HEALTH_RECORDS
            }
            else -> Screen.DETAILS
        }
    }

    val title = when (screen) {
        Screen.MEMBERS -> "أفراد العائلة"
        Screen.DETAILS -> selected?.fullName ?: "الملف الطبي"
        Screen.FORM -> if (editingId == null) "إضافة فرد" else "تعديل البيانات"
        Screen.HEALTH_RECORDS -> "الملف الصحي"
        Screen.ALLERGIES -> "سجل الحساسيات"
        Screen.LAB_RESULTS -> "الفحوصات المخبرية"
        Screen.SETTINGS -> settingsSection?.title ?: "الإعدادات"
        Screen.SEARCH -> "البحث الشامل"
        Screen.MEDICATIONS -> "الأدوية والعلاجات"
        Screen.APPOINTMENTS -> "المواعيد الطبية"
        Screen.DOCUMENTS -> "الملفات الطبية"
        Screen.REPORTS -> "التقارير الصحية"
        Screen.REMINDERS -> "التنبيهات والتذكيرات"
    }

    // Keep Android system-back inside the app instead of finishing the Activity.
    // Details returns to the family list so another member can be selected.
    val navigateBack: () -> Unit = {
        quickAddLab = false
        quickDoseId = null
        if (focusedRecordId != null) {
            focusedRecordId = null
            focusedRecordKind = null
            if (focusFromTimeline) {
                healthInitialTab = 7
                screen = Screen.HEALTH_RECORDS
            } else screen = Screen.SEARCH
            focusFromTimeline = false
        } else screen = when (screen) {
            Screen.FORM -> if (editingId == null) Screen.MEMBERS else Screen.DETAILS
            Screen.DETAILS -> Screen.MEMBERS
            Screen.ALLERGIES, Screen.LAB_RESULTS, Screen.HEALTH_RECORDS -> Screen.DETAILS
            Screen.MEDICATIONS -> if (medicationOpenedFromSettings) {
                medicationOpenedFromSettings = false
                Screen.SETTINGS
            } else Screen.DETAILS
            Screen.SETTINGS -> if (settingsSection != null) {
                settingsSection = null
                Screen.SETTINGS
            } else Screen.MEMBERS
            Screen.APPOINTMENTS -> if (selectedId != null) Screen.DETAILS else Screen.MEMBERS
            Screen.DOCUMENTS -> if (selectedId != null) Screen.DETAILS else Screen.MEMBERS
            Screen.REPORTS -> if (selectedId != null) Screen.DETAILS else Screen.MEMBERS
            Screen.REMINDERS, Screen.SEARCH -> Screen.MEMBERS
            Screen.MEMBERS -> Screen.MEMBERS
        }
    }
    BackHandler(enabled = screen != Screen.MEMBERS) { navigateBack() }

    if (locked && lockStore.isEnabled()) {
        LockScreen(
            store = lockStore,
            onUnlocked = { lastInteractionAt = System.currentTimeMillis(); locked = false },
            biometricAvailable = biometricAvailable && lockStore.isBiometricEnabled(),
            onBiometric = {
                val host = activity
                if (host != null && biometricAvailable && lockStore.isBiometricEnabled()) {
                    val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                lastInteractionAt = System.currentTimeMillis()
                                locked = false
                            }
                        })
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("فتح صحتي")
                        .setSubtitle("تحقق من هويتك للمتابعة")
                        .setAllowedAuthenticators(authenticators)
                    if (Build.VERSION.SDK_INT < 30) promptInfo.setNegativeButtonText("استخدام PIN")
                    prompt.authenticate(promptInfo.build())
                }
            }
        )
        return
    }

    Scaffold(modifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent()
                lastInteractionAt = System.currentTimeMillis()
            }
        }
    }, topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(title)
                    if (screen == Screen.MEMBERS) Text("ملف صحي مستقل لكل فرد", style = MaterialTheme.typography.labelSmall)
                }
            },
            actions = {
                if (focusedRecordId != null) {
                    TextButton(onClick = { focusedRecordId = null; focusedRecordKind = null }) { Text("عرض الكل") }
                }
                if (screen == Screen.MEMBERS) {
                    IconButton(onClick = { screen = Screen.SEARCH }) {
                        Icon(Icons.Outlined.Search, contentDescription = "البحث الشامل")
                    }
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

            Screen.SEARCH -> MedicalSearchScreen(
                members = members,
                text = searchText,
                onTextChange = { searchText = it },
                chosenMember = searchMemberId,
                onChooseMember = { searchMemberId = it },
                search = dao::searchMedicalRecords,
                open = { result ->
                    selectedId = result.memberId
                    openRecord(result.kind, result.recordId, fromTimeline = false)
                },
                modifier = Modifier.padding(padding)
            )

            Screen.DETAILS -> if (selected != null) {
                MemberDetailsScreen(selected, { editingId = selected.id; screen = Screen.FORM },
                    { healthInitialTab = 0; screen = Screen.HEALTH_RECORDS },
                    { healthInitialTab = 6; screen = Screen.HEALTH_RECORDS },
                    { screen = Screen.ALLERGIES }, allergies.size,
                    { screen = Screen.LAB_RESULTS }, labResults.size,
                    { screen = Screen.MEDICATIONS }, medications.size,
                    onAppointments = { screen = Screen.APPOINTMENTS },
                    upcomingAppointment = appointments.firstOrNull {
                        it.status == AppointmentStatus.UPCOMING.name &&
                            (it.date + "T" + it.time) >= java.time.LocalDateTime.now().toString().take(16)
                    }?.let { "${it.doctor} · ${it.date.toDisplayDate()} · ${it.time}" },
                    activeMedications = medications.filter {
                        it.isActive && !it.completed && (it.startDate.isBlank() || it.startDate <= LocalDate.now().toString()) &&
                            (it.endDate.isBlank() || it.endDate >= LocalDate.now().toString())
                    },
                    latestLab = labResults.firstOrNull()?.let { "${it.testName} · ${it.displayValue}" },
                    onAddLab = { quickAddLab = true; screen = Screen.LAB_RESULTS },
                    onRecordDose = { quickDoseId = it; screen = Screen.MEDICATIONS },
                    appointmentCount = appointments.size,
                    onDocuments = { screen = Screen.DOCUMENTS },
                    onReports = { screen = Screen.REPORTS },
                    onDelete = {
                        scope.launch {
                            try {
                                val removedDocuments = db.deleteMemberAndRecords(selected.id)
                                selectedId = null
                                screen = Screen.MEMBERS
                                val cleanupFailed = removedDocuments.map { document ->
                                    runCatching {
                                        documentStore.clearTemporaryPdf(document.id)
                                        documentStore.delete(document)
                                    }.isFailure
                                }.any { it }
                                val message = if (cleanupFailed) "حُذف الملف وسجلاته، لكن تعذر تنظيف مرفق مشفر واحد على الأقل." else "تم حذف الملف الطبي وبياناته المرتبطة."
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "تعذر حذف الملف الطبي. لم تُحذف سجلاته.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }, modifier = Modifier.padding(padding))
            } else {
                MembersScreen(members, { editingId = null; screen = Screen.FORM },
                    { selectedId = it; screen = Screen.DETAILS }, Modifier.padding(padding))
            }

            Screen.HEALTH_RECORDS -> if (selected != null) {
                MedicalHealthScreen(
                    member = selected, initialTab = healthInitialTab, allergies = allergies, medications = medications, labResults = labResults, documents = medicalDocuments,
                    conditions = matchingRecords("CONDITION", conditions) { it.id },
                    procedures = matchingRecords("PROCEDURE", procedures) { it.id },
                    history = matchingRecords("HISTORY", historyEvents) { it.id },
                    appointments = appointments,
                    familyHistory = matchingRecords("FAMILY_HISTORY", familyHistory) { it.id },
                    measurements = matchingRecords("MEASUREMENT", measurements) { it.id },
                    onSaveMember = { dao.updateMember(it) },
                    onAddCondition = { dao.addCondition(it); Unit }, onUpdateCondition = { dao.updateCondition(it) },
                    onDeleteCondition = { dao.deleteCondition(it, selected.id) },
                    onAddProcedure = { dao.addProcedure(it); Unit }, onUpdateProcedure = { dao.updateProcedure(it) },
                    onDeleteProcedure = { db.deleteProcedureAndUnlinkAttachments(it, selected.id) },
                    onAttachProcedure = { procedureId, uri ->
                        saveMemberDocument(uri, selected.id, "مرفق العملية", DocumentCategory.PROCEDURE, procedureId)
                    },
                    onAddHistory = { dao.addHistoryEvent(it); Unit }, onUpdateHistory = { dao.updateHistoryEvent(it) },
                    onDeleteHistory = { dao.deleteHistoryEvent(it, selected.id) },
                    onAddFamilyHistory = { dao.addFamilyDiseaseHistory(it); Unit }, onUpdateFamilyHistory = { dao.updateFamilyDiseaseHistory(it) },
                    onDeleteFamilyHistory = { dao.deleteFamilyDiseaseHistory(it, selected.id) },
                    onAddMeasurement = { dao.addBodyMeasurement(it); Unit }, onUpdateMeasurement = { dao.updateBodyMeasurement(it) },
                    onDeleteMeasurement = { dao.deleteBodyMeasurement(it, selected.id) },
                    onOpenDocument = documentLibrary.open,
                    onDeleteDocument = ::deleteMemberDocument,
                    onOpenTimelineRecord = { kind, id -> openRecord(kind, id, fromTimeline = true) },
                    modifier = Modifier.padding(padding)
                )
            }

            Screen.FORM -> {
                val editing = members.firstOrNull { it.id == editingId }
                if (editingId != null && editing == null) {
                    Text("بيانات الفرد غير متاحة.", Modifier.padding(padding).padding(16.dp))
                } else {
                    MemberFormScreen(editing,
                        onCancel = { screen = if (editingId == null) Screen.MEMBERS else Screen.DETAILS },
                        onSave = { name, dob, blood, notes, sex, phone, additionalInfo ->
                            if (editing == null) {
                                selectedId = dao.addMember(FamilyMember(
                                    fullName = name, dateOfBirth = dob, bloodGroup = blood,
                                    medicalNotes = notes, sex = sex, phone = phone, additionalInfo = additionalInfo,
                                    updatedAt = System.currentTimeMillis()
                                ))
                            } else {
                                dao.updateMember(editing.copy(
                                    fullName = name, dateOfBirth = dob, bloodGroup = blood,
                                    medicalNotes = notes, sex = sex, phone = phone, additionalInfo = additionalInfo,
                                    updatedAt = System.currentTimeMillis()
                                ))
                                selectedId = editing.id
                            }
                            screen = Screen.DETAILS
                        }, Modifier.padding(padding))
                }
            }

            Screen.LAB_RESULTS -> if (selected != null) {
                LabResultsScreen(
                    selected, matchingRecords("LAB", labResults) { it.id },
                    if (focusedRecordKind == "LAB") medicalDocuments.filter { it.relatedLabResultId == focusedRecordId } else medicalDocuments,
                    initiallyAdd = quickAddLab,
                    focused = focusedRecordKind == "LAB",
                    saveResult = { record, source ->
                        when {
                            record.id != 0L -> db.updateMemberLabResult(record, selected.id)
                            source == null -> dao.addLabResult(record) > 0
                            else -> withContext(Dispatchers.IO + NonCancellable) {
                                val document = documentStore.save(source, selected.id, documentName(source, "تقرير مختبر"))
                                try {
                                    db.addLabResultWithSource(record, document.copy(
                                        category = DocumentCategory.LAB.name,
                                        documentDate = record.performedAt
                                    )) > 0
                                } catch (error: Exception) {
                                    documentStore.delete(document)
                                    throw error
                                }
                            }
                        }
                    },
                    deleteResult = { db.deleteLabResultAndUnlinkAttachments(it, selected.id) },
                    saveDocument = { uri -> saveMemberDocument(uri, selected.id, "تقرير طبي.pdf", DocumentCategory.LAB) },
                    openDocument = documentLibrary.open,
                    deleteDocument = ::deleteMemberDocument,
                    modifier = Modifier.padding(padding)
                )
            }
            Screen.ALLERGIES -> if (selected != null) {
                AllergyScreen(selected, matchingRecords("ALLERGY", allergies) { it.id },
                    addAllergy = { dao.addAllergy(it) },
                    updateAllergy = { record -> dao.updateAllergy(record.id, selected.id, record.category, record.substance, record.commercialName, record.activeIngredient, record.reaction, record.severity, record.notes) },
                    deleteAllergy = { dao.deleteAllergy(it, selected.id) },
                    modifier = Modifier.padding(padding))
            }
            Screen.MEDICATIONS -> if (selected != null) {
                MedicationScreen(
                    member = selected,
                    records = matchingRecords("MEDICATION", medications) { it.id },
                    doseLogs = doseLogs,
                    initiallyShowInactive = focusedRecordKind == "MEDICATION",
                    initialDoseId = quickDoseId,
                    onRecordDose = { db.recordDose(it, selected.id) },
                    addMedication = { dao.addMedication(it); Unit },
                    updateMedication = { dao.updateMedication(it) },
                    deleteMedication = { dao.deleteMedication(it.id, selected.id) },
                    modifier = Modifier.padding(padding)
                )
            } else {
                Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اختر فردًا لعرض سجل الأدوية والعلاجات.")
                    Button(onClick = { screen = Screen.MEMBERS }) { Text("اختيار فرد") }
                }
            }
            Screen.SETTINGS -> if (settingsSection == null) {
                SettingsHomeScreen(onOpen = { settingsSection = it }, modifier = Modifier.padding(padding))
            } else {
                SettingsSectionScreen(
                    section = settingsSection!!,
                    refreshToken = settingsRefresh,
                    appearance = appearance,
                    reminders = reminders,
                    backup = backup,
                    driveAuthorization = driveAuthorization,
                    onDrivePreview = {
                        discardCurrentBackupPreview()
                        backupPreview = it
                    },
                    onPaletteSelected = onPaletteSelected,
                    onThemeModeSelected = onThemeModeSelected,
                    onResetAppearance = onResetAppearance,
                    lockEnabled = lockStore.isEnabled(),
                    biometricEnabled = lockStore.isBiometricEnabled(),
                    biometricAvailable = biometricAvailable,
                    onLockNow = {
                        discardCurrentBackupPreview()
                        backupPassword = ""
                        showBackupPassword = false
                        locked = true
                    },
                    timeoutMinutes = lockStore.timeoutMinutes(),
                    hasPin = lockStore.hasPin(),
                    onSetPin = { showPinSetup = true },
                    onLockEnabled = { enabled ->
                        if (enabled && !lockStore.hasPin()) showPinSetup = true
                        else { lockStore.setEnabled(enabled); locked = false; settingsRefresh++ }
                    },
                    onBiometric = { enabled -> lockStore.setBiometricEnabled(enabled); settingsRefresh++ },
                    onTimeout = { lockStore.setTimeoutMinutes(it); settingsRefresh++ },
                    onExport = { createBackup.launch("sehati-backup.shtb") },
                    onImport = { openBackup.launch(arrayOf("*/*")) },
                    backupLastSuccessAt = lastBackupAt,
                    backupLastSizeBytes = lastBackupSizeBytes,
                    backupMessage = backupMessage,
                    updateStatus = updateStatus,
                    updateBusy = updateBusy,
                    onCheckUpdates = {
                        if (BuildConfig.DEBUG) {
                            updateStatus = "فحص التحديثات متاح في النسخة المثبتة من Release فقط."
                        } else {
                            scope.launch {
                                updateBusy = true
                                updateStatus = "جارٍ فحص الإصدارات..."
                                try {
                                    availableRelease = releaseUpdater.checkForUpdate(BuildConfig.VERSION_CODE)
                                    updateStatus = if (availableRelease == null) "أنت تستخدم أحدث إصدار." else "يتوفر تحديث ${availableRelease?.versionName}."
                                } catch (_: Exception) { updateStatus = "تعذر فحص التحديثات. حاول مجددًا." }
                                finally { updateBusy = false }
                            }
                        }
                    },
                    selectedMemberName = selected?.fullName,
                    onOpenMedications = {
                        if (selected != null) {
                            medicationOpenedFromSettings = true
                            settingsSection = null
                            screen = Screen.MEDICATIONS
                        }
                    },
                    onSelectMember = { settingsSection = null; screen = Screen.MEMBERS },
                    modifier = Modifier.padding(padding)
                )
            }
            Screen.APPOINTMENTS -> if (selected != null) AppointmentScreen(
                member = selected,
                appointments = matchingRecords("VISIT", appointments) { it.id },
                focusedId = focusedRecordId.takeIf { focusedRecordKind == "VISIT" },
                documents = medicalDocuments,
                onAttach = appointmentAttachments.attach,
                onOpenDocument = documentLibrary.open,
                onSave = { db.saveAppointment(it, selected.id) },
                onDelete = { db.deleteAppointment(it.id, selected.id) },
                modifier = Modifier.padding(padding)
            )
            Screen.DOCUMENTS -> if (selected != null) DocumentLibraryScreen(
                member = selected,
                documents = matchingRecords("DOCUMENT", medicalDocuments) { it.id },
                focused = focusedRecordKind == "DOCUMENT",
                links = buildList {
                    appointments.forEach { add(DocumentLink("appointment", it.id, "موعد · ${it.doctor} · ${it.date.toDisplayDate()}")) }
                    labResults.forEach { add(DocumentLink("lab", it.id, "فحص · ${it.testName} · ${it.performedAt.toDisplayDate()}")) }
                    procedures.forEach { add(DocumentLink("procedure", it.id, "إجراء · ${it.name} · ${it.performedOn.toDisplayDate()}")) }
                },
                actions = documentLibrary,
                modifier = Modifier.padding(padding)
            )
            Screen.REPORTS -> if (selected != null) key(selected.id) {
                MedicalReportScreen(
                    person = selected.fullName,
                    records = reportRecords(selected, allergies, labResults, medications, appointments,
                        medicalDocuments, conditions, procedures, historyEvents, familyHistory, measurements),
                    actions = reportActions,
                    modifier = Modifier.padding(padding)
                )
            }
            Screen.REMINDERS -> Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) { ReminderSettings(reminders) }
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
            val entered = pin.toCharArray()
            try {
                lockStore.setPin(entered)
                locked = false
                showPinSetup = false
                settingsRefresh++
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "تعذر حفظ رمز PIN عبر مفتاح الجهاز.", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                entered.fill('\u0000')
            }
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
                            val savedAt = System.currentTimeMillis()
                            val savedSize = runCatching {
                                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                                    descriptor.statSize.takeIf { it >= 0L }
                                }
                            }.getOrNull()
                            backupStatusStore.recordSuccess(savedAt, savedSize)
                            lastBackupAt = savedAt
                            lastBackupSizeBytes = savedSize
                        } else {
                            discardCurrentBackupPreview()
                            discardCurrentBackupPreview()
                            backupPreview = backup.preview(context.contentResolver, uri, pass)
                            backupMessage = ""
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: BackupSizeLimitException) {
                        backupMessage = "حجم النسخة يتجاوز الحد المسموح (64 ميبيبايت)."
                    } catch (_: Exception) {
                        backupMessage = "تعذّرت العملية. تأكد من كلمة المرور وسلامة الملف."
                    } finally {
                        pass.fill('\u0000')
                    }
                }
            }
        }
    )

    backupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!restoringBackup) discardCurrentBackupPreview() },
            title = { Text("معاينة النسخة الاحتياطية") },
            text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("عدد الأفراد: ${preview.memberNames.size}")
                preview.memberNames.forEach { Text("• $it") }
                Text("عدد سجلات الحساسية: ${preview.allergyCount}")
                Text("عدد نتائج الفحوصات: ${preview.labResultCount}")
                Text("عدد الأدوية والعلاجات: ${preview.medicationCount}")
                Text("عدد المواعيد والزيارات: ${preview.appointmentCount}")
                Text("عدد الجرعات المسجلة: ${preview.doseLogCount}")
                Text("الحالات المزمنة: ${preview.conditionCount}")
                Text("العمليات والإجراءات: ${preview.procedureCount}")
                Text("أحداث التاريخ السابق: ${preview.historyEventCount}")
                Text("أمراض العائلة: ${preview.familyHistoryCount}")
                Text("قياسات الطول والوزن: ${preview.measurementCount}")
                Text("عدد التقارير والمرفقات: ${preview.documentCount}")
                preview.documentNames.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Text("استعادة النسخة ستستبدل البيانات الحالية بالكامل.")
            } },
            confirmButton = { TextButton(enabled = !restoringBackup, onClick = {
                if (!restoringBackup) {
                    restoringBackup = true
                    scope.launch {
                        try {
                            backup.restore(preview)
                            selectedId = null
                            editingId = null
                            backupMessage = "تمت استعادة النسخة الاحتياطية."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            backupMessage = "فشلت الاستعادة ولم تكتمل."
                        } finally {
                            backup.discardPreview(preview)
                            if (backupPreview === preview) backupPreview = null
                            restoringBackup = false
                        }
                    }
                }
            }) { Text(if (restoringBackup) "جارٍ الاستعادة…" else "استعادة واستبدال البيانات") } },
            dismissButton = { TextButton(enabled = !restoringBackup, onClick = { discardCurrentBackupPreview() }) { Text("إلغاء") } }
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
                                    member.dateOfBirth.ageOnDate()?.let { "العمر: $it سنة" },
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
    onHealthRecords: () -> Unit,
    onEmergency: () -> Unit,
    onAllergies: () -> Unit,
    allergyCount: Int,
    onLabResults: () -> Unit,
    labCount: Int,
    onMedications: () -> Unit,
    medicationCount: Int,
    onAppointments: () -> Unit,
    upcomingAppointment: String?,
    activeMedications: List<MedicationRecord>,
    latestLab: String?,
    onAddLab: () -> Unit,
    onRecordDose: (Long) -> Unit,
    appointmentCount: Int,
    onDocuments: () -> Unit,
    onReports: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPersonalDetails by rememberSaveable(member.id) { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable(member.id) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ملخص ${member.fullName}", style = MaterialTheme.typography.titleLarge)
                DetailLine("الموعد القادم", upcomingAppointment ?: "لا يوجد موعد قادم")
                DetailLine("الأدوية الحالية", if (activeMedications.isEmpty()) "لا توجد أدوية حالية" else
                    activeMedications.take(3).joinToString("، ") { it.name } +
                        if (activeMedications.size > 3) " (+${activeMedications.size - 3})" else "")
                DetailLine("آخر تحليل", latestLab ?: "لا توجد نتائج محفوظة")
                Button(onClick = onAddLab, modifier = Modifier.fillMaxWidth()) { Text("إضافة تحليل") }
                if (activeMedications.isNotEmpty()) {
                    val onlyMedication = activeMedications.singleOrNull()
                    OutlinedButton(onClick = {
                        if (onlyMedication == null) onMedications() else onRecordDose(onlyMedication.id)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (onlyMedication == null) "فتح سجل الجرعات" else "تسجيل جرعة · ${onlyMedication.name}")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { showPersonalDetails = !showPersonalDetails }) {
                    Text(if (showPersonalDetails) "إخفاء البيانات الشخصية" else "عرض البيانات الشخصية")
                }
                if (showPersonalDetails) {
                    DetailLine("تاريخ الميلاد", member.dateOfBirth.toDisplayDate().ifBlank { "غير مسجل" })
                    member.dateOfBirth.ageOnDate()?.let { DetailLine("العمر", "$it سنة") }
                    DetailLine("الجنس", member.sex.ifBlank { "غير مسجل" })
                    DetailLine("فصيلة الدم", member.bloodGroup.ifBlank { "غير مسجلة" })
                    DetailLine("رقم التواصل", member.phone.ifBlank { "غير مسجل" })
                    if (member.additionalInfo.isNotBlank()) DetailLine("معلومات إضافية", member.additionalInfo)
                    DetailLine("التاريخ والملاحظات الطبية", member.medicalNotes.ifBlank { "لا توجد ملاحظات" })
                }
                Button(onClick = onEdit, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, null); Text("تعديل بيانات الفرد")
                }
            }
        }
        Card(Modifier.fillMaxWidth().clickable(onClick = onHealthRecords)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("الملف الصحي المتكامل", style = MaterialTheme.typography.titleMedium)
                Text("الحالات والعمليات والتاريخ السابق والقياسات وبطاقة الطوارئ", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = onAppointments, modifier = Modifier.fillMaxWidth()) {
            Text("المواعيد والزيارات ($appointmentCount)")
        }
        OutlinedButton(onClick = onDocuments, modifier = Modifier.fillMaxWidth()) { Text("مكتبة الملفات الطبية") }
        OutlinedButton(onClick = onReports, modifier = Modifier.fillMaxWidth()) { Text("التقارير الطبية PDF") }
        OutlinedButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth()) { Text("بطاقة الطوارئ") }
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
        Card(Modifier.fillMaxWidth().clickable(onClick = onMedications)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("الأدوية والعلاجات", style = MaterialTheme.typography.titleMedium)
                    Text("الجرعات والفترات العلاجية المسجلة لهذا الفرد", style = MaterialTheme.typography.bodySmall)
                }
                Text(medicationCount.toString())
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
        OutlinedButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text("حذف الملف الطبي لهذا الفرد")
        }
    }
    if (showDeleteConfirmation) AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        title = { Text("تأكيد حذف الملف الطبي") },
        text = { Text("سيُحذف ملف ${member.fullName} وسجلات حساسياته ونتائجه وأدويته ومرفقاته نهائيًا من هذا الجهاز. لا يمكن التراجع عن الحذف.") },
        confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; onDelete() }) { Text("حذف نهائي") } },
        dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberFormScreen(
    member: FamilyMember?,
    onCancel: () -> Unit,
    onSave: suspend (String, String, String, String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(member?.id) { mutableStateOf(member?.fullName.orEmpty()) }
    var dob by rememberSaveable(member?.id) { mutableStateOf(member?.dateOfBirth.orEmpty()) }
    var blood by rememberSaveable(member?.id) { mutableStateOf(member?.bloodGroup.orEmpty()) }
    var notes by rememberSaveable(member?.id) { mutableStateOf(member?.medicalNotes.orEmpty()) }
    var sex by rememberSaveable(member?.id) { mutableStateOf(member?.sex.orEmpty()) }
    var phone by rememberSaveable(member?.id) { mutableStateOf(member?.phone.orEmpty()) }
    var additionalInfo by rememberSaveable(member?.id) { mutableStateOf(member?.additionalInfo.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable(member?.id) { mutableStateOf("") }
    var bloodMenuExpanded by remember { mutableStateOf(false) }
    var sexMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (member == null) "أدخل معلومات الفرد الجديد" else "تحديث معلومات ${member.fullName}",
            style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it; error = "" }, label = { Text("الاسم الكامل") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        DatePickerField("تاريخ الميلاد", dob, { dob = it }, Modifier.fillMaxWidth())
        dob.ageOnDate()?.let { age -> Text("العمر: $age سنة", style = MaterialTheme.typography.bodyMedium) }
        ExposedDropdownMenuBox(expanded = sexMenuExpanded, onExpandedChange = { sexMenuExpanded = it }) {
            OutlinedTextField(value = sex, onValueChange = {}, readOnly = true,
                label = { Text("الجنس (اختياري)") }, placeholder = { Text("اختاري ذكر أو أنثى") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
            ExposedDropdownMenu(expanded = sexMenuExpanded, onDismissRequest = { sexMenuExpanded = false }) {
                listOf("ذكر", "أنثى").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { sex = option; sexMenuExpanded = false })
                }
            }
        }
        OutlinedTextField(phone, { phone = it }, label = { Text("رقم التواصل (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        ExposedDropdownMenuBox(expanded = bloodMenuExpanded, onExpandedChange = { bloodMenuExpanded = it }) {
            OutlinedTextField(
                value = blood,
                onValueChange = {},
                readOnly = true,
                label = { Text("فصيلة الدم") },
                placeholder = { Text("اختاري فصيلة الدم") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(expanded = bloodMenuExpanded, onDismissRequest = { bloodMenuExpanded = false }) {
                (listOf("غير محددة") + listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")).forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group) },
                        onClick = { blood = if (group == "غير محددة") "" else group; bloodMenuExpanded = false }
                    )
                }
            }
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("التاريخ والملاحظات الطبية") },
            modifier = Modifier.fillMaxWidth(), minLines = 4)
        OutlinedTextField(additionalInfo, { additionalInfo = it }, label = { Text("معلومات إضافية اختيارية") },
            modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(enabled = !saving && name.isNotBlank(), onClick = {
            scope.launch {
                saving = true
                error = ""
                try { onSave(name.trim(), dob, blood.trim(), notes.trim(), sex.trim(), phone.trim(), additionalInfo.trim()) }
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
    updateAllergy: suspend (AllergyRecord) -> Unit,
    deleteAllergy: suspend (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable(member.id) { mutableStateOf(false) }
    var editingId by rememberSaveable(member.id) { mutableStateOf<Long?>(null) }
    val recordToEdit = records.firstOrNull { it.id == editingId }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("حساسيات ${member.fullName}", style = MaterialTheme.typography.titleLarge)
                Text("هذا السجل خاص بهذا الفرد", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editingId = null; showDialog = true }) { Icon(Icons.Outlined.Add, null); Text("إضافة") }
        }
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Text("لا توجد حساسيات مسجلة لهذا الفرد.", Modifier.padding(20.dp)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.id }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.commercialName.ifBlank { record.substance }, style = MaterialTheme.typography.titleMedium)
                            if (record.category.contains("دواء", ignoreCase = true)) {
                                if (record.commercialName.isNotBlank()) Text("الاسم التجاري: ${record.commercialName}")
                                if (record.activeIngredient.isNotBlank()) Text("المركب العلمي: ${record.activeIngredient}")
                                if (record.substance.isNotBlank()) Text("المادة المسجلة: ${record.substance}")
                            }
                            Text("النوع: ${record.category}")
                            Text("الأعراض: ${record.reaction}")
                            Text("الشدة: ${record.severity}")
                            if (record.notes.isNotBlank()) Text(record.notes)
                            Row {
                                TextButton(onClick = { editingId = record.id; showDialog = true }) { Text("تعديل") }
                                TextButton(onClick = { scope.launch { deleteAllergy(record.id) } }) { Text("حذف السجل") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDialog && (editingId == null || recordToEdit != null)) {
        AllergyDialog(member.id, initial = recordToEdit, onDismiss = { showDialog = false }, onSave = { record ->
            scope.launch { if (recordToEdit == null) addAllergy(record) else updateAllergy(record) }
            showDialog = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllergyDialog(memberId: Long, initial: AllergyRecord?, onDismiss: () -> Unit, onSave: (AllergyRecord) -> Unit) {
    val knownCategories = listOf("دواء", "طعام", "حساسية أخرى", "مسبب آخر", "مادة بيئية", "لسعة أو لدغة حشرة")
    val knownSeverities = listOf("خفيفة", "متوسطة", "شديدة", "مهددة للحياة", "غير معروفة")
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category?.takeIf { it in knownCategories } ?: if (initial == null) "دواء" else "أخرى") }
    var categoryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var customCategory by rememberSaveable(initial?.id) { mutableStateOf(initial?.category?.takeIf { it !in knownCategories }.orEmpty()) }
    var substance by rememberSaveable(initial?.id) { mutableStateOf(initial?.substance.orEmpty()) }
    var commercialName by rememberSaveable(initial?.id) { mutableStateOf(initial?.commercialName.orEmpty()) }
    var activeIngredient by rememberSaveable(initial?.id) { mutableStateOf(initial?.activeIngredient.orEmpty()) }
    var reaction by rememberSaveable(initial?.id) { mutableStateOf(initial?.reaction.orEmpty()) }
    var severity by rememberSaveable(initial?.id) { mutableStateOf(initial?.severity?.takeIf { it in knownSeverities } ?: if (initial == null) "متوسطة" else "أخرى") }
    var severityMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var customSeverity by rememberSaveable(initial?.id) { mutableStateOf(initial?.severity?.takeIf { it !in knownSeverities }.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة حساسية" else "تعديل الحساسية") },
        text = {
            Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(value = category, onValueChange = {}, readOnly = true,
                        label = { Text("تصنيف الحساسية") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
                    ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        (knownCategories + "أخرى (كتابة التصنيف)").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                category = if (option.startsWith("أخرى")) "أخرى" else option
                                categoryMenuExpanded = false
                            })
                        }
                    }
                }
                if (category == "أخرى") OutlinedTextField(customCategory, { customCategory = it }, label = { Text("اكتبي التصنيف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(substance, { substance = it }, label = { Text("المادة المسببة / اسم الحساسية") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (category.contains("دواء")) {
                    OutlinedTextField(commercialName, { commercialName = it },
                        label = { Text("الاسم التجاري للدواء") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(activeIngredient, { activeIngredient = it },
                        label = { Text("اسم المركب العلمي (المادة الفعالة)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("يمكن أن تختلف الأسماء التجارية بينما يبقى المركب العلمي نفسه.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(reaction, { reaction = it }, label = { Text("الأعراض أو رد الفعل") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                ExposedDropdownMenuBox(expanded = severityMenuExpanded, onExpandedChange = { severityMenuExpanded = it }) {
                    OutlinedTextField(value = severity, onValueChange = {}, readOnly = true,
                        label = { Text("شدة التفاعل") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
                    ExposedDropdownMenu(expanded = severityMenuExpanded, onDismissRequest = { severityMenuExpanded = false }) {
                        (knownSeverities + "أخرى (كتابة الشدة)").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                severity = if (option.startsWith("أخرى")) "أخرى" else option
                                severityMenuExpanded = false
                            })
                        }
                    }
                }
                if (severity == "أخرى") OutlinedTextField(customSeverity, { customSeverity = it }, label = { Text("اكتبي تصنيف الشدة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات إضافية") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = substance.isNotBlank() || commercialName.isNotBlank() || activeIngredient.isNotBlank(), onClick = {
                onSave(AllergyRecord(id = initial?.id ?: 0, memberId = memberId,
                    category = if (category == "أخرى") customCategory.trim().ifBlank { "أخرى" } else category.trim().ifBlank { "غير محدد" }, substance = substance.trim(),
                    commercialName = commercialName.trim(), activeIngredient = activeIngredient.trim(),
                    reaction = reaction.trim(), severity = if (severity == "أخرى") customSeverity.trim().ifBlank { "أخرى" } else severity.trim(), notes = notes.trim(), createdAt = initial?.createdAt ?: System.currentTimeMillis()))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(label: String, date: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
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

internal fun String.toDisplayDate(): String {
    if (isBlank()) return ""
    return runCatching {
        val d = LocalDate.parse(this)
        "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
    }.getOrDefault(this)
}

internal fun String.ageOnDate(today: LocalDate = LocalDate.now()): Int? = runCatching {
    val birthday = LocalDate.parse(this)
    Period.between(birthday, today).years.takeIf { birthday <= today && it >= 0 }
}.getOrNull()


@Composable
private fun LockScreen(store: AppLockStore, onUnlocked: () -> Unit, biometricAvailable: Boolean, onBiometric: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var remainingLock by remember { mutableLongStateOf(store.remainingLockMillis()) }
    LaunchedEffect(Unit) { while (true) { remainingLock = store.remainingLockMillis(); delay(1_000) } }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("صحتي", style = MaterialTheme.typography.headlineLarge)
        Text("التطبيق مقفل لحماية الملفات الطبية", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it.map { digit -> digit.digitToInt().digitToChar() }.joinToString(""); error = "" },
            label = { Text("رمز PIN من 6 أرقام") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (remainingLock > 0L) Text("محاولات كثيرة. حاول بعد " + (remainingLock/1000) + " ثانية.", color = MaterialTheme.colorScheme.error)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = pin.length == 6 && remainingLock <= 0L, onClick = {
            val verified = runCatching { store.verifyPin(pin.toCharArray()) }
            pin = ""
            when {
                verified.getOrDefault(false) -> onUnlocked()
                verified.isFailure -> error = "تعذر فتح مفتاح PIN. حاول مجددًا بعد فتح قفل الهاتف."
                else -> error = "الرمز غير صحيح."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("فتح التطبيق") }
        if (biometricAvailable) TextButton(onClick = onBiometric) { Text("استخدام البصمة / قفل الجهاز") }
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
            OutlinedTextField(first, { if (it.length <= 6 && it.all(Char::isDigit)) first = it.map { digit -> digit.digitToInt().digitToChar() }.joinToString("") }, label = { Text("PIN الجديد") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(second, { if (it.length <= 6 && it.all(Char::isDigit)) second = it.map { digit -> digit.digitToInt().digitToChar() }.joinToString("") }, label = { Text("تأكيد PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
