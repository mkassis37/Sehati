package com.mkassis37.sehati.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A medication/treatment entry owned by one family member. */
@Entity(
    tableName = "medication_records",
    indices = [Index(value = ["memberId", "isActive", "name"])]
)
data class MedicationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val name: String,
    val strength: String = "",
    val dosage: String = "",
    val form: String = "",
    val frequency: String = "",
    /** Comma-separated local clock times, e.g. 08:00, 20:00. */
    val scheduledTimes: String = "",
    /** ISO-8601 yyyy-MM-dd dates. */
    val startDate: String,
    val endDate: String = "",
    val prescribingDoctor: String = "",
    val instructions: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false
)


/**
 * Whether this treatment is active on [date], considering its start and end dates.
 * Blank dates remain supported for legacy records; malformed nonblank dates are
 * excluded rather than being silently treated as current medication.
 */
fun MedicationRecord.isCurrentlyActiveOn(date: java.time.LocalDate): Boolean {
    if (!isActive || completed) return false
    val start = startDate.takeIf(String::isNotBlank)?.let {
        runCatching { java.time.LocalDate.parse(it) }.getOrNull() ?: return false
    }
    val end = endDate.takeIf(String::isNotBlank)?.let {
        runCatching { java.time.LocalDate.parse(it) }.getOrNull() ?: return false
    }
    return (start == null || !start.isAfter(date)) &&
        (end == null || !end.isBefore(date))
}
