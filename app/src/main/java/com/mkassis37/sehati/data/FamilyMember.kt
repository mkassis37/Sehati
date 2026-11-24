package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val dateOfBirth: String = "",
    val bloodGroup: String = "",
    val medicalNotes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
