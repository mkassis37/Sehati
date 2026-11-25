package com.mkassis37.sehati

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mkassis37.sehati.ui.theme.AppearanceSettings
import com.mkassis37.sehati.ui.theme.SehatiPalette
import com.mkassis37.sehati.ui.theme.ThemeMode

@Composable
internal fun AppearanceSettingsSection(
    settings: AppearanceSettings,
    onPaletteSelected: (SehatiPalette) -> Unit,
    onModeSelected: (ThemeMode) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المظهر والألوان", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("اختاري نظام الألوان ووضع العرض. يسري التغيير مباشرة ويُحفظ على هذا الجهاز.", style = MaterialTheme.typography.bodySmall)

            Text("الألوان", style = MaterialTheme.typography.titleSmall)
            SehatiPalette.entries.toList().chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { palette ->
                        PaletteChoice(
                            palette = palette,
                            selected = settings.palette == palette,
                            onClick = { onPaletteSelected(palette) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Text("وضع العرض", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.title) }
                    )
                }
            }
            Text("المحدد: ${settings.palette.title} • ${settings.mode.title}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("استعادة المظهر الافتراضي") }
        }
    }
}

@Composable
private fun PaletteChoice(palette: SehatiPalette, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = modifier
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(25.dp).clip(CircleShape).background(palette.primarySeed)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(palette.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
