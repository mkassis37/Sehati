package com.mkassis37.sehati.reminders

import com.mkassis37.sehati.data.AppointmentStatus
import com.mkassis37.sehati.data.DoseLog
import com.mkassis37.sehati.data.DoseStatus
import com.mkassis37.sehati.data.MedicalAppointment
import com.mkassis37.sehati.data.MedicationRecord
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderScheduleTest {
    private val zone = ZoneId.of("Asia/Amman")

    @Test
    fun parsesArabicClockTimesAndIgnoresAmbiguousLegacyText() {
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), parseDoseTimes("٨:٠٠، 20:00; 24:30; بعد الغداء"))
    }

    @Test
    fun selectsNextLocalDoseAndSkipsStoppedOrExpiredTreatments() {
        val current = Instant.parse("2026-11-25T05:10:00Z")
        val medication = MedicationRecord(memberId = 1, name = "Synthetic medication",
            scheduledTimes = "08:00، 20:00", startDate = "2026-11-25", endDate = "2026-11-25")
        assertEquals(Instant.parse("2026-11-25T17:00:00Z"),
            nextReminder(ReminderKind.DOSE, current, zone, listOf(medication), emptyList()))
        assertNull(nextReminder(ReminderKind.DOSE, current, zone, listOf(medication.copy(isActive = false)), emptyList()))
        assertNull(nextReminder(ReminderKind.DOSE, current.plusSeconds(86400), zone, listOf(medication), emptyList()))
    }

    @Test
    fun doseRemindersRespectFutureStartAndPastEndDates() {
        val current = Instant.parse("2026-11-25T05:10:00Z")
        val futureStart = MedicationRecord(memberId = 1, name = "Future treatment",
            scheduledTimes = "08:00", startDate = "2026-11-26")
        val expired = MedicationRecord(memberId = 1, name = "Expired treatment",
            scheduledTimes = "08:00", startDate = "2026-11-20", endDate = "2026-11-24")
        assertNull(nextReminder(ReminderKind.DOSE, current, zone, listOf(futureStart), emptyList()))
        assertNull(nextReminder(ReminderKind.DOSE, current, zone, listOf(expired), emptyList()))
    }

    @Test
    fun doseTimeParserAcceptsValidArabicDigitsAndRejectsInvalidValues() {
        assertEquals(listOf(LocalTime.of(8, 0)), parseDoseTimes("٨:٠٠"))
        assertEquals(emptyList<LocalTime>(), parseDoseTimes("24:00"))
        assertEquals(emptyList<LocalTime>(), parseDoseTimes("بعد الغداء"))
    }

    @Test
    fun appointmentReminderIsThirtyMinutesEarlierAndCancelledVisitsAreExcluded() {
        val current = Instant.parse("2026-11-25T05:00:00Z")
        val visit = MedicalAppointment(memberId = 1, doctor = "Synthetic office", specialty = "",
            facility = "", date = "2026-11-25", time = "10:00", reason = "", notes = "",
            status = AppointmentStatus.UPCOMING.name, outcome = "", diagnosis = "", recommendations = "")
        assertEquals(Instant.parse("2026-11-25T06:30:00Z"),
            nextReminder(ReminderKind.APPOINTMENT, current, zone, emptyList(), listOf(visit)))
        assertNull(nextReminder(ReminderKind.APPOINTMENT, current, zone, emptyList(),
            listOf(visit.copy(status = AppointmentStatus.CANCELLED.name))))
    }
    @Test
    fun snoozedDoseUsesNewTimeAndStopsWhenTreatmentCompletes() {
        val current = Instant.parse("2026-11-25T04:55:00Z")
        val snoozedAt = Instant.parse("2026-11-25T05:10:00Z")
        val medication = MedicationRecord(id = 7, memberId = 3, name = "Synthetic reminder",
            scheduledTimes = "08:00", startDate = "2026-11-25")
        val log = DoseLog(7, 3, "2026-11-25", "08:00", DoseStatus.SNOOZED.name,
            Instant.parse("2026-11-25T05:00:00Z").toEpochMilli(), snoozedAt.toEpochMilli())
        assertEquals(snoozedAt, nextReminder(ReminderKind.DOSE, current, zone, listOf(medication), emptyList(), listOf(log)))
        assertNull(nextReminder(ReminderKind.DOSE, current, zone, listOf(medication.copy(completed = true)), emptyList(), listOf(log)))
    }

}
