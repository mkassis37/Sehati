package com.mkassis37.sehati.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "allergy_records", indices = [Index(value = ["memberId"])])
data class AllergyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val memberId: Long = 1,
    val category: String,
    val substance: String,
    @ColumnInfo(defaultValue = "''") val commercialName: String = "",
    @ColumnInfo(defaultValue = "''") val activeIngredient: String = "",
    val reaction: String,
    val severity: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
