package com.mkassis37.sehati.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
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
    }

    @Test
    fun backupPreviewCleanupRemovesOnlyStagedBackupFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureMedicalDocumentStore(context)
        val staged = store.createBackupPreviewFile()
        val unrelated = File(context.cacheDir, "sehati-unrelated-test.tmp")
        try {
            staged.writeBytes(byteArrayOf(1, 2, 3))
            unrelated.writeText("keep")
            assertTrue(staged.exists())
            store.clearBackupPreviewFiles()
            assertFalse(staged.exists())
            assertTrue(unrelated.exists())
        } finally {
            store.deleteBackupPreviewFile(staged.absolutePath)
            unrelated.delete()
        }
    }

    @Test
    fun cancelledStreamingRestoreDeletesPartialEncryptedAttachment() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureMedicalDocumentStore(context)
        val header = "%PDF-1.7\\nxyz".toByteArray()
        val payloadSize = 1024 * 1024
        val enteredRead = CountDownLatch(1)
        val continueRead = CountDownLatch(1)
        var payloadWritten = 0
        val source = object : InputStream() {
            private var headerOffset = 0

            override fun read(): Int {
                val one = ByteArray(1)
                val count = read(one, 0, 1)
                return if (count < 0) -1 else one[0].toInt() and 0xff
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (headerOffset < header.size) {
                    val count = minOf(length, header.size - headerOffset)
                    System.arraycopy(header, headerOffset, buffer, offset, count)
                    headerOffset += count
                    return count
                }
                enteredRead.countDown()
                check(continueRead.await(10, TimeUnit.SECONDS)) { "Test stream timed out." }
                if (payloadWritten >= payloadSize) return -1
                val count = minOf(length, payloadSize - payloadWritten)
                java.util.Arrays.fill(buffer, offset, offset + count, 0x41.toByte())
                payloadWritten += count
                return count
            }
        }
        val relativePath = "73/cancel-${System.nanoTime()}.shtd"
        val document = MedicalDocument(
            memberId = 73,
            displayName = "Synthetic cancellation.pdf",
            mimeType = "application/pdf",
            relativePath = relativePath,
            sizeBytes = header.size.toLong() + payloadSize
        )
        val target = File(context.filesDir, "medical_documents/$relativePath")
        try {
            val job = launch { store.restore(document, source, document.sizeBytes) }
            assertTrue("Restore did not reach the streaming read.", enteredRead.await(10, TimeUnit.SECONDS))
            job.cancel()
            continueRead.countDown()
            job.join()
            assertFalse("Partial encrypted attachment was left behind.", target.exists())
        } finally {
            continueRead.countDown()
            target.delete()
        }
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
