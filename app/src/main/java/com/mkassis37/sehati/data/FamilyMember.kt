package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val dateOfBirth: String = "",
    val bloodGroup: String = "",
    val medicalNotes: String = "",
    @ColumnInfo(defaultValue = "''") val sex: String = "",
    @ColumnInfo(defaultValue = "''") val phone: String = "",
    @ColumnInfo(defaultValue = "''") val additionalInfo: String = "",
    @ColumnInfo(defaultValue = "''") val emergencyContactName: String = "",
    @ColumnInfo(defaultValue = "''") val emergencyContactPhone: String = "",
    @ColumnInfo(defaultValue = "''") val emergencyContactRelation: String = "",
    @ColumnInfo(defaultValue = "1") val emergencyShowAllergies: Boolean = true,
    @ColumnInfo(defaultValue = "1") val emergencyShowMedications: Boolean = true,
    @ColumnInfo(defaultValue = "1") val emergencyShowConditions: Boolean = true,
    @ColumnInfo(defaultValue = "0") val emergencyShowNotes: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
