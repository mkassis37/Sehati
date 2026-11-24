package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One laboratory measurement belonging to exactly one family member. */
@Entity(
    tableName = "lab_results",
    indices = [Index(value = ["memberId", "testName", "performedAt"])]
)
data class LabResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val testName: String,
    val value: Double,
    val unit: String = "",
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
    /** ISO-8601 date (yyyy-MM-dd), kept as text for stable chronological sorting. */
    val performedAt: String,
    val laboratory: String = "",
    val source: String = "manual",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
