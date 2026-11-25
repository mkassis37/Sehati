package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "dose_logs",
    primaryKeys = ["medicationId", "date", "time"],
    foreignKeys = [ForeignKey(
        entity = MedicationRecord::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["memberId", "date"])]
)
data class DoseLog(
    val medicationId: Long,
    val memberId: Long,
    val date: String,
    val time: String,
    val status: String,
    val updatedAt: Long,
    val snoozedUntil: Long? = null
)

internal enum class DoseStatus(val label: String) {
    TAKEN("مأخوذة"), MISSED("فائتة"), SNOOZED("مؤجلة")
}
