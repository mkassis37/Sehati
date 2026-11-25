package com.mkassis37.sehati.data

data class MedicalSearchHit(
    val memberId: Long,
    val memberName: String,
    val kind: String,
    val recordId: Long,
    val title: String,
    val detail: String
)
