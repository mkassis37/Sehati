package com.mkassis37.sehati.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A PDF or medical document encrypted in the app-private files directory. */
@Entity(
    tableName = "medical_documents",
    indices = [Index(value = ["memberId", "addedAt"])]
)
data class MedicalDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val displayName: String,
    val relativePath: String,
    val mimeType: String = "application/pdf",
    val sizeBytes: Long,
    val relatedProcedureId: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)
