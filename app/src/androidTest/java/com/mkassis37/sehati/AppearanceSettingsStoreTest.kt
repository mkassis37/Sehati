package com.mkassis37.sehati

import androidx.test.core.app.ApplicationProvider
import com.mkassis37.sehati.data.AppearanceSettingsStore
import com.mkassis37.sehati.ui.theme.AppearanceSettings
import com.mkassis37.sehati.ui.theme.SehatiPalette
import com.mkassis37.sehati.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsStoreTest {
    @Test
    fun selectedAppearanceIsReadableFromANewStoreAndCanBeReset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val firstStore = AppearanceSettingsStore(context)
        val expected = AppearanceSettings(SehatiPalette.VIOLET, ThemeMode.DARK)

        firstStore.update(expected)

        assertEquals(expected, AppearanceSettingsStore(context).settings.first())
        firstStore.reset()
        assertEquals(AppearanceSettings(), firstStore.settings.first())
    }
}
