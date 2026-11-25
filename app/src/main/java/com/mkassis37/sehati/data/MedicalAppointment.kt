package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medical_appointments",
    foreignKeys = [ForeignKey(
        entity = FamilyMember::class,
        parentColumns = ["id"],
        childColumns = ["memberId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["memberId", "date", "time"])]
)
data class MedicalAppointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val doctor: String,
    val specialty: String,
    val facility: String,
    val date: String,
    val time: String,
    val reason: String,
    val notes: String,
    val status: String,
    val outcome: String,
    val diagnosis: String,
    val recommendations: String,
    val createdAt: Long = System.currentTimeMillis()
)

internal enum class AppointmentStatus(val label: String) {
    UPCOMING("قادم"), COMPLETED("مكتمل"), CANCELLED("ملغى")
}
