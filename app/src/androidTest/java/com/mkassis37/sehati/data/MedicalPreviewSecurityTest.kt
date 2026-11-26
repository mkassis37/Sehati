package com.mkassis37.sehati.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicalPreviewSecurityTest {
    @Test
    fun previewIsIsolatedAndTamperedCiphertextLeavesNoPlaintext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = context.packageName + ".fileprovider"
        val store = SecureMedicalDocumentStore(context)
        val plain = "%PDF-1.4\nSynthetic attachment\n".toByteArray()
        val source = File.createTempFile("synthetic-", ".pdf", context.cacheDir)
        try {
            source.writeBytes(plain)
            assertTrue(runCatching { FileProvider.getUriForFile(context, authority, source) }.isFailure)
            val document = store.save(Uri.fromFile(source), 42, "Synthetic.pdf").copy(id = 42)
            try {
                val encrypted = File(context.filesDir, "medical_documents/" + document.relativePath)
                val ciphertext = encrypted.readBytes()
                assertFalse(plain.contentEquals(ciphertext))
                val preview = store.createTemporaryFile(document)
                assertArrayEquals(plain, preview.readBytes())
                assertTrue(FileProvider.getUriForFile(context, authority, preview).scheme == "content")
                store.clearTemporaryDocument(document.id)
                assertFalse(preview.exists())
                ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
                encrypted.writeBytes(ciphertext)
                assertTrue(runCatching { store.createTemporaryFile(document) }.isFailure)
                assertTrue(medicalShareDirectory(context).listFiles().orEmpty().none { it.name.startsWith("sehati-preview-42-") })
            } finally {
                store.delete(document)
            }
        } finally {
            source.delete()
        }
    @Test
    fun orphanCleanupKeepsReferencedEncryptedDocuments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureMedicalDocumentStore(context)
        val source = File.createTempFile("synthetic-orphan-", ".pdf", context.cacheDir)
        val plaintext = "%PDF-1.7\nOrphan cleanup fixture\n".toByteArray()
        try {
            source.writeBytes(plaintext)
            val retained = store.save(Uri.fromFile(source), 71, "Retained.pdf")
            val orphan = store.save(Uri.fromFile(source), 72, "Orphan.pdf")
            val retainedFile = File(context.filesDir, "medical_documents/" + retained.relativePath)
            val orphanFile = File(context.filesDir, "medical_documents/" + orphan.relativePath)
            assertTrue(retainedFile.exists())
            assertTrue(orphanFile.exists())

            store.cleanupOrphanedDocuments(setOf(retained.relativePath))

            assertTrue(retainedFile.exists())
            assertFalse(orphanFile.exists())
            store.delete(retained)
        } finally {
            source.delete()
        }
    }
    }
}
