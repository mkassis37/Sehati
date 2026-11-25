package com.mkassis37.sehati

import com.mkassis37.sehati.ui.theme.SehatiPalette
import com.mkassis37.sehati.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun offersEightDistinctNamedPalettesAndFallsBackForUnknownStoredValues() {
        assertEquals(8, SehatiPalette.entries.size)
        assertEquals(8, SehatiPalette.entries.map { it.title }.distinct().size)
        assertEquals(SehatiPalette.MEDICAL_TEAL, SehatiPalette.fromStoredValue("removed_palette"))
    }

    @Test
    fun allDisplayModesResolveCorrectlyAgainstSystemTheme() {
        assertFalse(ThemeMode.LIGHT.useDarkTheme(systemIsDark = true))
        assertTrue(ThemeMode.DARK.useDarkTheme(systemIsDark = false))
        assertTrue(ThemeMode.SYSTEM.useDarkTheme(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.useDarkTheme(systemIsDark = false))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("invalid_mode"))
    }
}
