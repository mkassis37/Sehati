package com.mkassis37.sehati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionTest {
    @Test
    fun settingsHubContainsTenDistinctSections() {
        val sections = SettingsSection.entries
        assertEquals(10, sections.size)
        assertEquals(10, sections.map { it.title }.distinct().size)
        assertTrue(sections.all { it.title.isNotBlank() && it.subtitle.isNotBlank() })
    }
}
