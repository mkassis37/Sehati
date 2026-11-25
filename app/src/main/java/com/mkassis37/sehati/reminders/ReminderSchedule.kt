package com.mkassis37.sehati.reminders

import com.mkassis37.sehati.data.AppointmentStatus
import com.mkassis37.sehati.data.MedicalAppointment
import com.mkassis37.sehati.data.MedicationRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal enum class ReminderKind(val title: String, val body: String) {
    DOSE("تذكير الأدوية", "افتح صحتي لمراجعة جرعات الأدوية."),
    APPOINTMENT("تذكير المواعيد", "افتح صحتي لمراجعة توقيت موعدك الطبي."),
    TREATMENT_END("تذكير انتهاء العلاج", "افتح صحتي لمراجعة العلاج القريب من الانتهاء.")
}

private val clockTime = Regex("""([0-9]{1,2}):([0-9]{2})""")

internal fun parseDoseTimes(stored: String): List<LocalTime> =
    stored.split(',', '،', ';', '؛', '\n').mapNotNull { part ->
        val normalized = part.trim().map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
        val match = clockTime.matchEntire(normalized) ?: return@mapNotNull null
        runCatching { LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()
    }.distinct().sorted()

internal fun nextReminder(
    kind: ReminderKind,
    from: Instant,
    zone: ZoneId,
    medications: List<MedicationRecord>,
    appointments: List<MedicalAppointment>
): Instant? {
    val date = from.atZone(zone).toLocalDate()
    return when (kind) {
        ReminderKind.DOSE -> medications.asSequence().filter { it.isActive }.flatMap { medication ->
            val start = runCatching { LocalDate.parse(medication.startDate) }.getOrNull()
                ?: return@flatMap emptySequence()
            val end = if (medication.endDate.isBlank()) null
                else runCatching { LocalDate.parse(medication.endDate) }.getOrNull() ?: return@flatMap emptySequence()
            val first = maxOf(date, start)
            val times = parseDoseTimes(medication.scheduledTimes)
            sequenceOf(first, first.plusDays(1)).filter { end == null || it <= end }.flatMap { day ->
                times.asSequence().map { day.atTime(it).atZone(zone).toInstant() }
            }
        }
        ReminderKind.APPOINTMENT -> appointments.asSequence()
            .filter { it.status == AppointmentStatus.UPCOMING.name }
            .mapNotNull { appointment ->
                runCatching {
                    LocalDate.parse(appointment.date).atTime(LocalTime.parse(appointment.time))
                        .atZone(zone).toInstant().minusSeconds(30 * 60L)
                }.getOrNull()
            }
        ReminderKind.TREATMENT_END -> medications.asSequence().filter { it.isActive }
            .mapNotNull { medication ->
                runCatching {
                    val end = LocalDate.parse(medication.endDate)
                    val start = LocalDate.parse(medication.startDate)
                    maxOf(start, end.minusDays(1)).atTime(7, 0).atZone(zone).toInstant()
                }.getOrNull()
            }
    }.filter { it >= from }.minOrNull()
}
