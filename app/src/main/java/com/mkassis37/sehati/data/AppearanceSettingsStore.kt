package com.mkassis37.sehati.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkassis37.sehati.ui.theme.AppearanceSettings
import com.mkassis37.sehati.ui.theme.SehatiPalette
import com.mkassis37.sehati.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearancePreferences by preferencesDataStore(name = "sehati_appearance")

/** Stores non-medical appearance preferences separately from the encrypted health database. */
class AppearanceSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.appearancePreferences

    val settings: Flow<AppearanceSettings> = dataStore.data
        .map { preferences ->
            AppearanceSettings(
                palette = SehatiPalette.fromStoredValue(preferences[PALETTE_KEY]),
                mode = ThemeMode.fromStoredValue(preferences[MODE_KEY])
            )
        }
        .catch { emit(AppearanceSettings()) }

    suspend fun update(settings: AppearanceSettings) {
        dataStore.edit { preferences ->
            preferences[PALETTE_KEY] = settings.palette.name
            preferences[MODE_KEY] = settings.mode.name
        }
    }

    suspend fun updatePalette(palette: SehatiPalette) {
        dataStore.edit { preferences -> preferences[PALETTE_KEY] = palette.name }
    }

    suspend fun updateMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[MODE_KEY] = mode.name }
    }

    suspend fun reset() = update(AppearanceSettings())

    private companion object {
        val PALETTE_KEY = stringPreferencesKey("palette")
        val MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
