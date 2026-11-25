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
