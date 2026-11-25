package com.mkassis37.sehati

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.ui.theme.AppearanceSettings
import com.mkassis37.sehati.ui.theme.SehatiPalette
import com.mkassis37.sehati.ui.theme.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class SettingsSection(val title: String, val subtitle: String) {
    APPEARANCE("المظهر والتصميم", "الألوان ووضع العرض"),
    LANGUAGE("اللغة والمنطقة", "لغة التطبيق والتاريخ والوقت"),
    SECURITY("الأمان والتشفير", "قفل التطبيق وحماية الملفات"),
    BACKUP("النسخ الاحتياطي والاستعادة", "نسخة مشفرة تحفظها في المكان الذي تختاره"),
    DRIVE("Google Drive", "حالة النسخ السحابي"),
    NOTIFICATIONS("الإشعارات والتنبيهات", "حالة التنبيهات والأذونات"),
    MEDICATIONS("الأدوية والعلاجات", "الانتقال إلى سجل أدوية الفرد"),
    UPDATES("التحديثات والإصدار", "الإصدار الحالي وفحص التحديثات"),
    ABOUT("معلومات التطبيق", "النسخة والدعم والخصوصية"),
    DIAGNOSTICS("التطوير والتشخيص", "حالة الخدمات التقنية دون بيانات طبية")
}

@Composable
internal fun SettingsHomeScreen(onOpen: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("إعدادات صحتي", style = MaterialTheme.typography.headlineSmall)
        Text("اختر قسمًا لإدارة إعداداته.", style = MaterialTheme.typography.bodyMedium)
        SettingsSection.entries.forEach { section ->
            SettingsNavigationCard(section, onClick = { onOpen(section) })
        }
    }
}

@Composable
private fun SettingsNavigationCard(section: SettingsSection, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(section.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleSmall)
                Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.APPEARANCE -> Icons.Outlined.Palette
    SettingsSection.LANGUAGE -> Icons.Outlined.Language
    SettingsSection.SECURITY -> Icons.Outlined.Security
    SettingsSection.BACKUP -> Icons.Outlined.Backup
    SettingsSection.DRIVE -> Icons.Outlined.CloudOff
    SettingsSection.NOTIFICATIONS -> Icons.Outlined.Notifications
    SettingsSection.MEDICATIONS -> Icons.Outlined.Medication
    SettingsSection.UPDATES -> Icons.Outlined.SystemUpdateAlt
    SettingsSection.ABOUT -> Icons.Outlined.Info
    SettingsSection.DIAGNOSTICS -> Icons.Outlined.BugReport
}

@Composable
internal fun SettingsSectionScreen(
    section: SettingsSection,
    refreshToken: Int,
    appearance: AppearanceSettings,
    onPaletteSelected: (SehatiPalette) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onResetAppearance: () -> Unit,
    lockEnabled: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onLockNow: () -> Unit,
    timeoutMinutes: Int,
    hasPin: Boolean,
    onSetPin: () -> Unit,
    onLockEnabled: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onTimeout: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backupLastSuccessAt: Long?,
    backupLastSizeBytes: Long?,
    backupMessage: String,
    updateStatus: String,
    updateBusy: Boolean,
    onCheckUpdates: () -> Unit,
    selectedMemberName: String?,
    onOpenMedications: () -> Unit,
    onSelectMember: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val refresh = refreshToken
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (section) {
            SettingsSection.APPEARANCE -> AppearanceSettingsSection(
                settings = appearance,
                onPaletteSelected = onPaletteSelected,
                onModeSelected = onThemeModeSelected,
                onReset = onResetAppearance
            )
            SettingsSection.LANGUAGE -> LanguageRegionSection()
            SettingsSection.SECURITY -> SecuritySection(
                lockEnabled, biometricEnabled, biometricAvailable, onLockNow, timeoutMinutes, hasPin,
                onSetPin, onLockEnabled, onBiometric, onTimeout
            )
            SettingsSection.BACKUP -> BackupSection(
                backupLastSuccessAt, backupLastSizeBytes, backupMessage, onExport, onImport
            )
            SettingsSection.DRIVE -> StatusSection(
                title = "Google Drive غير متصل",
                description = "لا يوجد تكامل OAuth أو رفع سحابي مهيأ في هذا الإصدار. لا ترفع صحتي بيانات طبية إلى Drive حاليًا.",
                details = listOf("النسخ المحلي المشفر متاح من قسم النسخ الاحتياطي.", "يتطلب تفعيل Drive إعداد OAuth رسميًا وموافقة المستخدم.")
            )
            SettingsSection.NOTIFICATIONS -> StatusSection(
                title = "تنبيهات الجرعات والمواعيد غير مفعلة",
                description = "لا توجد حاليًا جدولة فعلية لجرعات الأدوية أو المواعيد؛ لذلك لا نعرض مفاتيح تشغيل أو طلب أذونات لا تستخدمها وظيفة.",
                details = listOf("تنبيهات Android ستظهر هنا بعد تنفيذ سجل الجرعات والمواعيد.")
            )
            SettingsSection.MEDICATIONS -> MedicationSettingsSection(selectedMemberName, onOpenMedications, onSelectMember)
            SettingsSection.UPDATES -> UpdatesSection(updateStatus, updateBusy, onCheckUpdates)
            SettingsSection.ABOUT -> AboutSection()
            SettingsSection.DIAGNOSTICS -> DiagnosticsSection(backupLastSuccessAt)
        }
    }
}

@Composable
private fun LanguageRegionSection() {
    InfoCard("اللغة والمنطقة") {
        InfoRow("لغة الواجهة", "العربية")
        InfoRow("دعم الإنجليزية", "غير مكتمل؛ لا يوجد خيار لغة حتى تكتمل الترجمة")
        InfoRow("تنسيق التاريخ", "يوم/شهر/سنة")
        InfoRow("المنطقة الزمنية", ZoneId.systemDefault().id)
        InfoRow("لغة الجهاز", Locale.getDefault().toLanguageTag())
    }
}

@Composable
private fun SecuritySection(
    lockEnabled: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onLockNow: () -> Unit,
    timeoutMinutes: Int,
    hasPin: Boolean,
    onSetPin: () -> Unit,
    onLockEnabled: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onTimeout: (Int) -> Unit
) {
    InfoCard("قفل التطبيق") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("قفل التطبيق", style = MaterialTheme.typography.titleSmall)
                Text(if (hasPin) "رمز PIN محفوظ" else "أنشئ PIN قبل تفعيل القفل", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = lockEnabled, enabled = hasPin || !lockEnabled, onCheckedChange = onLockEnabled)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (hasPin) "تغيير PIN" else "إعداد PIN", style = MaterialTheme.typography.titleSmall)
                Text("رمز من 6 أرقام", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSetPin) { Text(if (hasPin) "تغيير" else "إعداد") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("البصمة أو قفل الجهاز", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (biometricAvailable) "متاحة على هذا الجهاز" else "المصادقة غير متاحة على الجهاز حاليًا",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = biometricEnabled && biometricAvailable, enabled = biometricAvailable, onCheckedChange = onBiometric)
        }
        Text("إعادة القفل بعد مغادرة التطبيق: $timeoutMinutes دقيقة")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 5, 10, 30).forEach { minutes ->
                FilterChip(selected = timeoutMinutes == minutes, onClick = { onTimeout(minutes) }, label = { Text("$minutes د") })
            }
        }
    }
    if (lockEnabled) OutlinedButton(onClick = onLockNow) { Text("قفل التطبيق الآن") }
    InfoCard("حالة التشفير") {
        InfoRow("قاعدة البيانات", "SQLCipher مشفر")
        InfoRow("مفتاح قاعدة البيانات", "محمي بواسطة Android Keystore")
        InfoRow("المرفقات الطبية", "AES-GCM بمفتاح Android Keystore")
        Text("لا يؤدي إيقاف قفل الواجهة إلى تعطيل تشفير قاعدة البيانات أو المرفقات.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BackupSection(
    lastSuccessAt: Long?,
    lastSizeBytes: Long?,
    message: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    InfoCard("النسخ المحلي المشفر") {
        Text("تُنشأ النسخة بكلمة مرور مستقلة وتُحفظ في الموقع الذي تختاره. الاستعادة تعرض محتويات النسخة وتطلب تأكيد الاستبدال.")
        HorizontalDivider()
        Text(
            lastSuccessAt?.let { "آخر نسخة ناجحة: ${formatTimestamp(it)}" } ?: "لم تُسجل نسخة ناجحة من هذا الجهاز بعد.",
            style = MaterialTheme.typography.titleSmall
        )
        lastSizeBytes?.let { Text("حجم آخر نسخة: ${formatSize(it)}", style = MaterialTheme.typography.bodySmall) }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text("إنشاء نسخة مشفرة") }
            OutlinedButton(onClick = onImport) { Text("استعادة نسخة") }
        }
        Text("لا يوجد رفع تلقائي أو ربط Google Drive في هذا الإصدار.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MedicationSettingsSection(selectedMemberName: String?, onOpen: () -> Unit, onSelectMember: () -> Unit) {
    InfoCard("الأدوية والعلاجات") {
        if (selectedMemberName == null) {
            Text("اختر فردًا من العائلة أولًا لفتح سجل أدويته.")
            Button(onClick = onSelectMember) { Text("اختيار فرد") }
        } else {
            Text("الفرد المحدد: $selectedMemberName")
            Text("إدارة سجل الدواء متاحة من شاشة الأدوية. التنبيهات وسجل الجرعات غير منفذين بعد.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onOpen) { Icon(Icons.Outlined.Medication, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("فتح سجل الأدوية") }
        }
    }
}

@Composable
private fun UpdatesSection(status: String, busy: Boolean, onCheck: () -> Unit) {
    InfoCard("الإصدار والتحديث") {
        InfoRow("اسم التطبيق", "صحتي")
        InfoRow("الإصدار", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow("القناة", if (BuildConfig.DEBUG) "تجريبية (Debug)" else "رسمية (Release)")
        InfoRow("Commit", BuildConfig.SOURCE_COMMIT.ifBlank { "غير متاح لهذا البناء" })
        Text(status, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !busy, onClick = onCheck) { Text(if (busy) "جارٍ الفحص…" else "فحص التحديثات") }
        Text("يتحقق المثبت من SHA-256 قبل فتح ملف التحديث. فحص الإصدارات متاح في بناء Release.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AboutSection() {
    InfoCard("حول صحتي") {
        InfoRow("التطبيق", "صحتي — إدارة الملفات الصحية العائلية")
        InfoRow("الإصدار", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow("سياسة الخصوصية", "لا توجد صفحة منشورة ضمن التطبيق حاليًا")
        InfoRow("الدعم", "لا توجد قناة دعم مدمجة حاليًا")
        Text("يحفظ التطبيق السجلات الطبية محليًا على الجهاز. راجع إعداد النسخ الاحتياطي لحفظ نسخة مشفرة في موقع تختاره.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DiagnosticsSection(lastBackupAt: Long?) {
    InfoCard("حالة الخدمات") {
        InfoRow("قاعدة البيانات المشفرة", "مفعلة — Room مع SQLCipher")
        InfoRow("تشفير المرفقات", "مفعّل — AES-GCM وKeystore")
        InfoRow("نسخ Android التلقائي", "معطل لبيانات التطبيق الخاصة")
        InfoRow("آخر نسخة يدوية", lastBackupAt?.let(::formatTimestamp) ?: "لا توجد نسخة مسجلة")
        InfoRow("Google Drive", "غير متصل وغير مهيأ")
        InfoRow("التنبيهات والزيارات", "غير مكتملة")
        InfoRow("التقارير الطبية PDF", "غير مكتملة")
        Text("لا تعرض شاشة التشخيص أي أسماء أو قيم طبية.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusSection(title: String, description: String, details: List<String>) {
    InfoCard(title) {
        Text(description)
        details.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTimestamp(timestampMillis: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestampMillis))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes بايت"
    else -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
}
