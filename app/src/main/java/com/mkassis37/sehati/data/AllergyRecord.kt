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
 * Normalizes Arabic spelling/diacritics and punctuation, and supports common
 * Arabic and English labels used by current and legacy records.
 */
fun String.isLifeThreateningAllergySeverity(): Boolean {
    val normalized = normalizeAllergySeverity(this)
    if (normalized.isBlank()) return false

    // Explicit negation must not be promoted to a life-threatening warning.
    val negations = listOf(
        "غير مهددة للحياة", "غير مهدده للحياه", "ليست مهددة للحياة",
        "ليست مهدده للحياه", "not life threatening", "non life threatening",
        "not anaphylactic", "no anaphylaxis"
    )
    if (negations.any(normalized::contains)) return false

    val phrases = listOf(
        "مهددة للحياة", "مهدده للحياه", "مهددة للحياه", "مهدده للحياة",
        "مهدد للحياة", "مهدد للحياه", "تهدد الحياة", "تهدد الحياه",
        "خطر على الحياة", "صدمة تحسسية", "صدمة الحساسية", "تأق",
        "life threatening", "anaphylaxis", "anaphylactic shock", "anaphylactic"
    )
    return phrases.any(normalized::contains)
}

/** True for severe or life-threatening labels, including legacy English/custom values. */
fun String.isSevereAllergySeverity(): Boolean {
    val normalized = normalizeAllergySeverity(this)
    return isLifeThreateningAllergySeverity() ||
        (normalized.contains("شديد") && !normalized.contains("غير شديد")) ||
        normalized in setOf("severe", "high", "critical", "very severe")
}

private fun normalizeAllergySeverity(value: String): String =
    value.trim()
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[\\p{P}\\p{S}_]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
