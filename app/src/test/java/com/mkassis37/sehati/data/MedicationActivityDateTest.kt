package com.mkassis37.sehati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicationActivityDateTest {
    private val today = LocalDate.parse("2026-11-28")

    private fun medication(
        start: String = "2026-11-28",
        end: String = "",
        active: Boolean = true,
        completed: Boolean = false
    ) = MedicationRecord(
        memberId = 1,
        name = "Test medication",
        startDate = start,
        endDate = end,
        isActive = active,
        completed = completed
    )

    @Test
    fun futureStartDateIsNotCurrent() {
        assertFalse(medication(start = "2026-11-29").isCurrentlyActiveOn(today))
    }

    @Test
    fun startDateTodayAndPastStartDateAreCurrent() {
        assertTrue(medication(start = "2026-11-28").isCurrentlyActiveOn(today))
        assertTrue(medication(start = "2026-11-01").isCurrentlyActiveOn(today))
    }

    @Test
    fun endDateIsInclusiveAndPastEndDateIsNotCurrent() {
        assertTrue(medication(end = "2026-11-28").isCurrentlyActiveOn(today))
        assertFalse(medication(end = "2026-11-27").isCurrentlyActiveOn(today))
    }

    @Test
    fun stoppedOrCompletedMedicationIsNotCurrent() {
        assertFalse(medication(active = false).isCurrentlyActiveOn(today))
        assertFalse(medication(completed = true).isCurrentlyActiveOn(today))
    }

    @Test
    fun blankLegacyDatesRemainSupported() {
        assertTrue(medication(start = "", end = "").isCurrentlyActiveOn(today))
    }

    @Test
    fun malformedNonblankDatesAreNotSilentlyAccepted() {
        assertFalse(medication(start = "not-a-date").isCurrentlyActiveOn(today))
        assertFalse(medication(end = "not-a-date").isCurrentlyActiveOn(today))
    }
}
