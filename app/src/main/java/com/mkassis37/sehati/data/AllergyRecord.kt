package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allergy_records")
data class AllergyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val substance: String,
    val reaction: String,
    val severity: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
