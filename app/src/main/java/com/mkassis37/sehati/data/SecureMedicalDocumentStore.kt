package com.mkassis37.sehati.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** App-private PDF storage encrypted with an Android Keystore key. */
class SecureMedicalDocumentStore(private val context: Context) {
    private val directory = File(context.filesDir, "medical_documents")

    suspend fun save(uri: Uri, memberId: Long, displayName: String): MedicalDocument {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر فتح الملف المحدد.")
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$memberId/$id.shtd")
        target.parentFile?.mkdirs()
        var plainSize = 0L
        input.use { source ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            FileOutputStream(target).use { file ->
                file.write(MAGIC)
                file.write(cipher.iv)
                CipherOutputStream(file, cipher).use { encrypted ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        plainSize += count
                        require(plainSize <= MAX_DOCUMENT_BYTES) { "حجم PDF أكبر من الحد المسموح (25 MB)." }
                        encrypted.write(buffer, 0, count)
                    }
                }
            }
        }
        return MedicalDocument(memberId = memberId, displayName = displayName.take(180),
            relativePath = target.relativeTo(directory).path, sizeBytes = plainSize)
    }

    fun readPlaintextBytes(document: MedicalDocument): ByteArray =
        decryptFile(resolve(document.relativePath)).use { it.readBytes() }

    fun restore(document: MedicalDocument, plaintext: ByteArray): MedicalDocument {
        require(plaintext.size <= MAX_DOCUMENT_BYTES)
        val target = resolve(document.relativePath)
        target.parentFile?.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        FileOutputStream(target).use { file ->
            file.write(MAGIC)
            file.write(cipher.iv)
            CipherOutputStream(file, cipher).use { it.write(plaintext) }
        }
        return document.copy(sizeBytes = plaintext.size.toLong())
    }

    fun createTemporaryPdf(document: MedicalDocument): File {
        val output = File(context.cacheDir, "sehati-${document.id}.pdf")
        decryptFile(resolve(document.relativePath)).use { input ->
            FileOutputStream(output).use { input.copyTo(it) }
        }
        return output
    }

    fun delete(document: MedicalDocument) { resolve(document.relativePath).delete() }
    fun deleteAll() { directory.deleteRecursively() }

    private fun decryptFile(file: File): CipherInputStream {
        val input = FileInputStream(file)
        try {
            val magic = ByteArray(4)
            require(input.read(magic) == 4 && magic.contentEquals(MAGIC)) { "ملف طبي مشفر غير صالح." }
            val iv = ByteArray(12)
            require(input.read(iv) == 12) { "ملف طبي غير مكتمل." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            return CipherInputStream(input, cipher)
        } catch (error: Exception) {
            input.close()
            throw error
        }
    }

    private fun resolve(relativePath: String): File {
        val base = directory.canonicalFile
        val file = File(directory, relativePath).canonicalFile
        require(file.path.startsWith(base.path + File.separator)) { "مسار المرفق غير صالح." }
        return file
    }

    private fun getKey(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? java.security.Key)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "sehati_medical_documents_v1"
        private const val MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L
        private val MAGIC = byteArrayOf(0x53, 0x48, 0x44, 0x46)
    }
}
