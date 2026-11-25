package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medical_profile")
data class MedicalProfile(
    @PrimaryKey val id: Int = 1,
    val fullName: String = "",
    val dateOfBirth: String = "",
    val bloodGroup: String = "",
    val medicalNotes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
