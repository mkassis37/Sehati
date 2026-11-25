package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ongoing or monitored diagnosis; this is a record, not an automated diagnosis. */
@Entity(tableName = "chronic_conditions", indices = [Index(value = ["memberId", "status", "diagnosedOn"])])
data class ChronicCondition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val name: String,
    val diagnosedOn: String = "",
    val diagnosingDoctor: String = "",
    val status: String = "نشط",
    val treatment: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "medical_procedures", indices = [Index(value = ["memberId", "performedOn"])])
data class MedicalProcedure(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val name: String,
    val performedOn: String = "",
    val facility: String = "",
    val doctor: String = "",
    val reason: String = "",
    val complications: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Prior illness, important injury, hospitalization, or other historical event. */
@Entity(tableName = "medical_history_events", indices = [Index(value = ["memberId", "eventDate"])])
data class MedicalHistoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val category: String,
    val title: String,
    val eventDate: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Relative disease history. It must never be presented as the member's own diagnosis. */
@Entity(tableName = "family_disease_history", indices = [Index(value = ["memberId", "relative", "disease"])])
data class FamilyDiseaseHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val relative: String,
    val disease: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Dated body measurements, retained as a timeline rather than overwriting older values. */
@Entity(tableName = "body_measurements", indices = [Index(value = ["memberId", "measuredOn"])])
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val measuredOn: String,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
