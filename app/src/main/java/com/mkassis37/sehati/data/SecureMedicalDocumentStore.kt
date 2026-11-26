package com.mkassis37.sehati.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-private PDF storage encrypted with an Android Keystore key. */
class SecureMedicalDocumentStore(private val context: Context) {
    private val directory = File(context.filesDir, "medical_documents")

    suspend fun save(uri: Uri, memberId: Long, displayName: String): MedicalDocument = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر فتح الملف المحدد.")
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$memberId/$id.shtd")
        target.parentFile?.mkdirs()
        var plainSize = 0L
        var detectedMime = ""
        try {
            input.use { source ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getKey())
                FileOutputStream(target).use { file ->
                    file.write(MAGIC)
                    file.write(cipher.iv)
                    CipherOutputStream(file, cipher).use { encrypted ->
                        val header = readAtMost(source, 12)
                        detectedMime = detectMime(header)
                            ?: throw IllegalArgumentException("الصيغ المدعومة هي PDF وJPG وPNG وWebP فقط.")
                        plainSize += header.size
                        encrypted.write(header)
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
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        MedicalDocument(memberId = memberId, displayName = displayName.take(180), mimeType = detectedMime,
            relativePath = target.relativeTo(directory).path, sizeBytes = plainSize)
    }

    internal fun copyPlaintextTo(document: MedicalDocument, output: OutputStream) {
        decryptFile(resolve(document.relativePath)).use { it.copyTo(output) }
    }

    suspend fun restore(document: MedicalDocument, plaintext: ByteArray): MedicalDocument =
        restore(document, plaintext.inputStream(), plaintext.size.toLong())

    /**
     * Streams authenticated backup attachment plaintext straight into this store's
     * device-key-encrypted file. The caller's stream is fully consumed so an upstream
     * authenticated stream can verify its final GCM tag before this method succeeds.
     */
    suspend fun restore(document: MedicalDocument, plaintext: InputStream, expectedSize: Long): MedicalDocument =
        withContext(Dispatchers.IO) {
            require(expectedSize in 1..MAX_DOCUMENT_BYTES) {
                "حجم المرفق غير صالح في النسخة الاحتياطية."
            }
            val source = PushbackInputStream(plaintext, 12)
            val header = readAtMost(source, 12)
            source.unread(header)
            require(detectMime(header) == document.mimeType) {
                "صيغة المرفق غير متوافقة مع بيانات النسخة الاحتياطية."
            }
            val target = resolve(document.relativePath)
            target.parentFile?.mkdirs()
            var copied = 0L
            try {
                source.use { input ->
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, getKey())
                    FileOutputStream(target).use { file ->
                        file.write(MAGIC)
                        file.write(cipher.iv)
                        CipherOutputStream(file, cipher).use { encrypted ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                copied += count
                                require(copied <= expectedSize && copied <= MAX_DOCUMENT_BYTES) {
                                    "حجم المرفق أكبر من الحجم المعلن."
                                }
                                encrypted.write(buffer, 0, count)
                            }
                        }
                    }
                }
                require(copied == expectedSize) { "حجم المرفق لا يطابق بيانات النسخة الاحتياطية." }
                document.copy(sizeBytes = copied)
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }

    suspend fun createTemporaryFile(document: MedicalDocument): File = withContext(Dispatchers.IO) {
        val extension = when (document.mimeType) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("نوع مرفق غير مدعوم.")
        }
        val output = File.createTempFile("sehati-preview-${document.id}-", ".$extension", medicalShareDirectory(context))
        try {
            decryptFile(resolve(document.relativePath)).use { input ->
                FileOutputStream(output).use { input.copyTo(it) }
            }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    fun delete(document: MedicalDocument) {
        val file = resolve(document.relativePath)
        if (!file.delete() && file.exists()) throw java.io.IOException("تعذر تنظيف المرفق المشفر.")
    }

    fun deleteAll() { directory.deleteRecursively() }

    internal fun createBackupPreviewFile(): File {
        val directory = backupPreviewDirectory()
        return File.createTempFile("sehati-backup-preview-", ".shtb", directory)
    }

    internal fun deleteBackupPreviewFile(path: String?) {
        if (path.isNullOrBlank()) return
        val directory = backupPreviewDirectory().canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == directory && file.name.startsWith("sehati-backup-preview-")) {
            file.delete()
        }
    }

    internal fun clearBackupPreviewFiles() {
        val directory = File(context.cacheDir, BACKUP_PREVIEW_DIRECTORY)
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("sehati-backup-preview-") }
            ?.forEach { it.delete() }
    }

    private fun backupPreviewDirectory(): File =
        File(context.cacheDir, BACKUP_PREVIEW_DIRECTORY).apply {
            if (!isDirectory && !mkdirs() && !isDirectory) {
                throw java.io.IOException("تعذر تجهيز مساحة معاينة النسخة الاحتياطية.")
            }
        }

    fun clearTemporaryPdfs() {
        // Older builds stored these files directly in cache.
        context.cacheDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("sehati-") && it.extension.lowercase() in setOf("pdf", "jpg", "png", "webp")
        }?.forEach { it.delete() }
        medicalShareDirectory(context).listFiles()?.filter {
            it.isFile && System.currentTimeMillis() - it.lastModified() > 3_600_000
        }?.forEach { it.delete() }
    }

    fun clearTemporaryPdf(documentId: Long) = clearTemporaryDocument(documentId)

    fun clearTemporaryDocument(documentId: Long) {
        medicalShareDirectory(context).listFiles()?.filter {
            it.isFile && it.name.startsWith("sehati-preview-$documentId-")
        }?.forEach { it.delete() }
    }

    private fun decryptFile(file: File): CipherInputStream {
        val input = FileInputStream(file)
        try {
            val magic = readExactly(input, 4)
            require(magic.contentEquals(MAGIC)) { "ملف طبي مشفر غير صالح." }
            val iv = readExactly(input, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            return CipherInputStream(input, cipher)
        } catch (error: Exception) {
            input.close()
            throw error
        }
    }

    private fun readExactly(input: java.io.InputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(bytes, offset, size - offset)
            if (count < 0) throw IllegalArgumentException("ملف طبي غير مكتمل.")
            offset += count
        }
        return bytes
    }

    private fun readAtMost(input: java.io.InputStream, maxSize: Int): ByteArray {
        val buffer = ByteArray(maxSize)
        var count = 0
        while (count < maxSize) {
            val read = input.read(buffer, count, maxSize - count)
            if (read < 0) break
            if (read == 0) continue
            count += read
        }
        if (count == 0) throw IllegalArgumentException("الملف فارغ.")
        return buffer.copyOf(count)
    }

    private fun detectMime(header: ByteArray): String? = when {
        header.size >= 5 && String(header, 0, 5, Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
        header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "image/jpeg"
        header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
        header.size >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
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
        private const val BACKUP_PREVIEW_DIRECTORY = "sehati-backup-previews"
        private const val MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L
        private val MAGIC = byteArrayOf(0x53, 0x48, 0x44, 0x46)
    }
}

internal fun medicalShareDirectory(context: Context): File = File(context.cacheDir, "medical-share").apply {
    if (!isDirectory && !mkdirs() && !isDirectory) throw java.io.IOException("تعذر تجهيز المعاينة الطبية.")
}
