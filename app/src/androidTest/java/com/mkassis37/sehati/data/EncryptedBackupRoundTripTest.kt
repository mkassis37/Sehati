package com.mkassis37.sehati.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupRoundTripTest {
    @Test
    fun preservesSyntheticAttachmentAndRejectsWrongPasswordBeforeRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java).build()
        val store = SecureMedicalDocumentStore(context)
        val input = File.createTempFile("sehati-fixture-", ".pdf", context.cacheDir)
        val archive = File.createTempFile("sehati-fixture-", ".shtb", context.cacheDir)
        val fixture = "%PDF-1.4\nSynthetic attachment fixture\n".toByteArray()
        val password = "synthetic-password-for-test"
        try {
            val owner = source.medicalDao().addMember(FamilyMember(fullName = "Synthetic owner"))
            input.writeBytes(fixture)
            val attached = store.save(Uri.fromFile(input), owner, "Synthetic attachment.pdf")
            source.addLinkedDocument(attached.copy(category = "LAB", documentDate = "2026-11-25"))
            target.medicalDao().addMember(FamilyMember(fullName = "Synthetic original"))
            val uri = Uri.fromFile(archive)
            EncryptedBackup(source, store).write(context.contentResolver, uri, password.toCharArray())
            val restore = EncryptedBackup(target, store)
            assertTrue(runCatching { restore.preview(context.contentResolver, uri, "wrong-test-password".toCharArray()) }.isFailure)
            assertEquals("Synthetic original", target.medicalDao().getAllMembers().single().fullName)
            val proof = restore.preview(context.contentResolver, uri, password.toCharArray())
            assertEquals(listOf("Synthetic owner"), proof.memberNames)
            assertEquals(1, proof.documentCount)

            val invalidPayload = JSONObject(proof.payload.toString())
            val documents = invalidPayload.getJSONArray("documents")
            documents.put(JSONObject(documents.getJSONObject(0).toString())
                .put("id", Long.MAX_VALUE)
                .put("content", "aW52YWxpZA=="))
            assertTrue(runCatching { restore.restore(proof.copy(payload = invalidPayload)) }.isFailure)
            assertEquals("Synthetic original", target.medicalDao().getAllMembers().single().fullName)
            assertTrue(target.medicalDao().getAllDocuments().isEmpty())

            restore.restore(proof)
            val restored = target.medicalDao().getAllDocuments().single()
            assertEquals("LAB", restored.category)
            assertEquals("2026-11-25", restored.documentDate)
            val bytes = ByteArrayOutputStream().also { store.copyPlaintextTo(restored, it) }.toByteArray()
            assertArrayEquals(fixture, bytes)
        } finally {
            source.medicalDao().getAllDocuments().forEach(store::delete)
            target.medicalDao().getAllDocuments().forEach(store::delete)
            source.close()
            target.close()
            input.delete()
            archive.delete()
        }
    }
}
