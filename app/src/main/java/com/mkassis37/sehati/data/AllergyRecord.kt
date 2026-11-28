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

/**
 * True when the stored severity explicitly identifies a life-threatening allergy.
 * Handles known Arabic and English variants so older/custom records remain visible.
 */
fun String.isLifeThreateningAllergySeverity(): Boolean {
    val normalized = normalizeAllergySeverity(this)
    val phrases = listOf(
        "مهددة للحياة", "مهدده للحياه", "مهددة للحياه", "مهدده للحياة",
        "مهدد للحياة", "مهدد للحياه", "تهدد الحياة", "تهدد الحياه",
        "خطر على الحياة", "life threatening", "life-threatening", "life_threatening",
        "anaphylaxis", "anaphylactic"
    )
    return phrases.any(normalized::contains)
}

/** True for severe or life-threatening labels, including legacy English/custom values. */
fun String.isSevereAllergySeverity(): Boolean {
    val normalized = normalizeAllergySeverity(this)
    return isLifeThreateningAllergySeverity() ||
        normalized.contains("شديد") ||
        normalized in setOf("severe", "high", "critical", "very severe")
}

private fun normalizeAllergySeverity(value: String): String =
    value.trim()
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("\\s+"), " ")

