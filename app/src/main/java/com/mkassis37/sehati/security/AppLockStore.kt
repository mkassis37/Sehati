package com.mkassis37.sehati.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** PIN verification uses a salted, deliberately expensive one-way derivation. */
class AppLockStore(context: Context) {
    private val prefs = context.getSharedPreferences("sehati_app_lock", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", false)
    fun setEnabled(enabled: Boolean) { prefs.edit().putBoolean("enabled", enabled).apply() }
    fun isBiometricEnabled(): Boolean = prefs.getBoolean("biometric", true)
    fun setBiometricEnabled(enabled: Boolean) { prefs.edit().putBoolean("biometric", enabled).apply() }
    fun timeoutMinutes(): Int = prefs.getInt("timeout_minutes", 1).coerceIn(1, 30)
    fun setTimeoutMinutes(value: Int) { prefs.edit().putInt("timeout_minutes", value.coerceIn(1, 30)).apply() }
    fun hasPin(): Boolean = prefs.contains("pin_cipher")

    fun setPin(pin: CharArray) {
        require(pin.size == 6 && pin.all { it in '0'..'9' }) { "PIN must contain six digits." }
        val salt = ByteArray(16).also(random::nextBytes)
        val derived = derive(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val encrypted = cipher.doFinal(derived)
        prefs.edit()
            .putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("pin_cipher", android.util.Base64.encodeToString(cipher.iv + encrypted, android.util.Base64.NO_WRAP))
            .putBoolean("enabled", true)
            .apply()
        pin.fill('\u0000')
        derived.fill(0)
    }

    fun verifyPin(pin: CharArray): Boolean {
        return try { verifyPinInternal(pin) } finally { pin.fill('\u0000') }
    }

    private fun verifyPinInternal(pin: CharArray): Boolean {
        if (!hasPin()) return false
        val salt = android.util.Base64.decode(prefs.getString("pin_salt", "")!!, android.util.Base64.NO_WRAP)
        val envelope = android.util.Base64.decode(prefs.getString("pin_cipher", "")!!, android.util.Base64.NO_WRAP)
        val iv = envelope.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(create = false), GCMParameterSpec(128, iv))
        val expected = cipher.doFinal(envelope.copyOfRange(12, envelope.size))
        val actual = derive(pin, salt)
        val valid = java.security.MessageDigest.isEqual(expected, actual)
        pin.fill('\u0000'); expected.fill(0); actual.fill(0)
        if (valid) clearFailures() else recordFailure()
        return valid
    }

    fun isTemporarilyLocked(): Boolean = System.currentTimeMillis() < prefs.getLong("lock_until", 0L)
    fun remainingLockMillis(): Long = (prefs.getLong("lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0)
    private fun recordFailure() {
        val count = prefs.getInt("failures", 0) + 1
        val delay = when {
            count >= 10 -> 15 * 60_000L
            count >= 7 -> 5 * 60_000L
            count >= 5 -> 60_000L
            else -> 0L
        }
        prefs.edit().putInt("failures", count).putLong("lock_until", System.currentTimeMillis() + delay).apply()
    }
    private fun clearFailures() { prefs.edit().putInt("failures", 0).putLong("lock_until", 0L).apply() }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun wrappingKey(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "The existing PIN verification key is unavailable." }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    companion object { private const val KEY_ALIAS = "sehati_pin_verification_key" }
}
