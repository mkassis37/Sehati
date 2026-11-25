package com.mkassis37.sehati.data

import android.content.Context

/** Stores only backup metadata; it contains no medical record content. */
class BackupStatusStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("sehati_backup_status", Context.MODE_PRIVATE)

    val lastSuccessAt: Long?
        get() = preferences.getLong(KEY_LAST_SUCCESS_AT, 0L).takeIf { it > 0L }

    val lastSizeBytes: Long?
        get() = preferences.getLong(KEY_LAST_SIZE_BYTES, -1L).takeIf { it >= 0L }

    fun recordSuccess(timestampMillis: Long, sizeBytes: Long?) {
        preferences.edit()
            .putLong(KEY_LAST_SUCCESS_AT, timestampMillis)
            .apply {
                if (sizeBytes != null && sizeBytes >= 0L) putLong(KEY_LAST_SIZE_BYTES, sizeBytes)
                else remove(KEY_LAST_SIZE_BYTES)
            }
            .apply()
    }

    private companion object {
        const val KEY_LAST_SUCCESS_AT = "last_success_at"
        const val KEY_LAST_SIZE_BYTES = "last_size_bytes"
    }
}
