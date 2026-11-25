package com.mkassis37.sehati.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.LocalTime
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class DrivePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("sehati_google_drive", Context.MODE_PRIVATE)

    val account: String? get() = prefs.getString("account", null)
    val automatic: Boolean get() = prefs.getBoolean("automatic", false)
    val hasPassword: Boolean get() = prefs.contains("backup_password")
    val generation: Long get() = prefs.getLong("generation", 0)
    val time: LocalTime get() = LocalTime.of(prefs.getInt("hour", 7), prefs.getInt("minute", 0))
    val lastSuccess: Long get() = prefs.getLong("last_success", 0)
    val lastAttempt: Long get() = prefs.getLong("last_attempt", 0)
    val status: String get() = prefs.getString("status", "").orEmpty()

    fun connect(email: String) {
        if (account != email) {
            disconnect()
            check(prefs.edit().putString("account", email).commit())
        }
    }

    fun disconnect() {
        check(prefs.edit().clear().putLong("generation", generation + 1).commit())
    }

    fun updateAutomatic(enabled: Boolean, time: LocalTime) {
        require(!enabled || (account != null && hasPassword))
        check(prefs.edit().putBoolean("automatic", enabled)
            .putInt("hour", time.hour).putInt("minute", time.minute)
            .putLong("generation", generation + 1).commit())
    }

    fun updatePassword(password: CharArray) {
        try {
            require(password.size >= 10)
            val bytes = password.concatToString().toByteArray(Charsets.UTF_8)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
                val wrapped = Base64.encodeToString(cipher.iv + cipher.doFinal(bytes), Base64.NO_WRAP)
                check(prefs.edit().putString("backup_password", wrapped).commit())
            } finally {
                bytes.fill(0)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    fun backupPassword(): CharArray {
        val envelope = Base64.decode(checkNotNull(prefs.getString("backup_password", null)), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, envelope, 0, 12))
        val decrypted = cipher.doFinal(envelope, 12, envelope.size - 12)
        return try { decrypted.toString(Charsets.UTF_8).toCharArray() } finally { decrypted.fill(0) }
    }

    fun recordAttempt() {
        prefs.edit().putLong("last_attempt", System.currentTimeMillis()).apply()
    }

    fun recordStatus(status: String, success: Boolean = false) {
        prefs.edit().putString("status", status).apply {
            if (success) putLong("last_success", System.currentTimeMillis())
        }.apply()
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        check(create) { "Drive backup password key is unavailable." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object { private const val KEY = "sehati_google_drive_backup_password" }
}
