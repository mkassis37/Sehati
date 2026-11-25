package com.mkassis37.sehati.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores a random SQLCipher passphrase encrypted by a non-exportable Android Keystore key.
 * The encrypted envelope lives in noBackupFilesDir and is excluded from device/cloud backup.
 */
internal class SecurePassphraseStore(private val context: Context) {
    private val envelopeFile = File(context.noBackupFilesDir, "sehati-db-key.bin")
    private val alias = "sehati_database_wrapping_key"

    @Synchronized
    fun getOrCreate(): ByteArray {
        if (envelopeFile.exists()) return decrypt(envelopeFile.readBytes())

        check(!context.getDatabasePath("sehati.db").exists()) {
            "Database key is missing while an existing database is present. Refusing to replace the key."
        }

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val encrypted = cipher.doFinal(passphrase)
        val iv = cipher.iv
        val envelope = byteArrayOf(iv.size.toByte()) + iv + encrypted
        envelopeFile.writeBytes(envelope)
        return passphrase
    }

    private fun decrypt(envelope: ByteArray): ByteArray {
        require(envelope.isNotEmpty()) { "Database key envelope is empty." }
        val ivLength = envelope[0].toInt() and 0xff
        require(ivLength in 12..16 && envelope.size > ivLength + 1) {
            "Database key envelope is invalid."
        }
        val iv = envelope.copyOfRange(1, 1 + ivLength)
        val ciphertext = envelope.copyOfRange(1 + ivLength, envelope.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
