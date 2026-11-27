package com.mkassis37.sehati.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.DataInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val fixture = ByteArray(6 * 1024 * 1024) { (it % 251).toByte() }.also {
            "%PDF-1.7\nSynthetic streamed attachment\n".toByteArray().copyInto(it)
        }
        val password = "synthetic-password-for-test"
        try {
            val sourceDao = source.medicalDao()
            val owner = sourceDao.addMember(FamilyMember(fullName = "Synthetic owner"))
            sourceDao.addAllergy(AllergyRecord(
                memberId = owner, category = "Synthetic category", substance = "Synthetic allergen",
                reaction = "Synthetic reaction", severity = "moderate"
            ))
            val labId = sourceDao.addLabResult(LabResult(
                memberId = owner, testName = "Synthetic laboratory test", value = 12.5,
                unit = "g/L", referenceLow = 10.0, referenceHigh = 15.0, performedAt = "2026-11-25"
            ))
            val medicationId = sourceDao.addMedication(MedicationRecord(
                memberId = owner, name = "Synthetic medication", startDate = "2026-11-25"
            ))
            val conditionId = sourceDao.addCondition(ChronicCondition(
                memberId = owner, name = "Synthetic condition", diagnosedOn = "2026-01-01"
            ))
            val procedureId = sourceDao.addProcedure(MedicalProcedure(
                memberId = owner, name = "Synthetic procedure", performedOn = "2026-02-01"
            ))
            val historyId = sourceDao.addHistoryEvent(MedicalHistoryEvent(
                memberId = owner, category = "Synthetic history", title = "Synthetic event"
            ))
            val familyHistoryId = sourceDao.addFamilyDiseaseHistory(FamilyDiseaseHistory(
                memberId = owner, relative = "Synthetic relative", disease = "Synthetic family condition"
            ))
            val measurementId = sourceDao.addBodyMeasurement(BodyMeasurement(
                memberId = owner, measuredOn = "2026-11-25", heightCm = 170.0, weightKg = 65.0
            ))
            val appointmentId = sourceDao.addAppointment(MedicalAppointment(
                memberId = owner, doctor = "Synthetic doctor", specialty = "Synthetic specialty",
                facility = "Synthetic clinic", date = "2026-12-01", time = "10:00",
                reason = "Synthetic reason", notes = "", status = AppointmentStatus.UPCOMING.name,
                outcome = "", diagnosis = "", recommendations = ""
            ))
            assertTrue(source.recordDose(DoseLog(
                medicationId = medicationId, memberId = owner, date = "2026-11-25", time = "08:00",
                status = DoseStatus.TAKEN.name, updatedAt = 1234
            ), owner))
            input.writeBytes(fixture)
            val attached = store.save(Uri.fromFile(input), owner, "Synthetic attachment.pdf")
            val documentId = source.addLinkedDocument(attached.copy(
                category = "LAB", documentDate = "2026-11-25",
                relatedProcedureId = procedureId, relatedAppointmentId = appointmentId,
                relatedLabResultId = labId
            ))
            target.medicalDao().addMember(FamilyMember(fullName = "Synthetic original"))
            val uri = Uri.fromFile(archive)
            EncryptedBackup(source, store).write(context.contentResolver, uri, password.toCharArray())
            val restore = EncryptedBackup(target, store)
            assertTrue(runCatching { restore.preview(context.contentResolver, uri, "wrong-test-password".toCharArray()) }.isFailure)
            assertEquals("Synthetic original", target.medicalDao().getAllMembers().single().fullName)
            val proof = restore.preview(context.contentResolver, uri, password.toCharArray())
            assertEquals(listOf("Synthetic owner"), proof.memberNames)
            assertEquals(1, proof.documentCount)
            assertFalse(proof.payload.getJSONArray("documents").getJSONObject(0).has("content"))
            archive.inputStream().use { stream ->
                val header = ByteArray(5)
                DataInputStream(stream).readFully(header)
                assertEquals(2, header[4].toInt() and 0xff)
            }

            val tampered = restore.preview(context.contentResolver, uri, password.toCharArray())
            val tamperedArchive = File(checkNotNull(tampered.stagedArchivePath))
            java.io.RandomAccessFile(tamperedArchive, "rw").use { file ->
                val offset = file.length() - 1
                file.seek(offset)
                val value = file.readByte()
                file.seek(offset)
                file.writeByte(value.toInt() xor 0x01)
            }
            assertTrue(runCatching { restore.restore(tampered) }.isFailure)
            assertEquals("Synthetic original", target.medicalDao().getAllMembers().single().fullName)
            restore.discardPreview(tampered)

            val invalidPayload = JSONObject(proof.payload.toString())
            val documents = invalidPayload.getJSONArray("documents")
            documents.put(JSONObject(documents.getJSONObject(0).toString())
                .put("id", Long.MAX_VALUE)
                .put("relatedLabResultId", Long.MAX_VALUE))
            assertTrue(runCatching { restore.restore(proof.copy(payload = invalidPayload)) }.isFailure)
            assertEquals("Synthetic original", target.medicalDao().getAllMembers().single().fullName)
            assertTrue(target.medicalDao().getAllDocuments().isEmpty())

            restore.restore(proof)
            val restoredDao = target.medicalDao()
            val restored = restoredDao.getAllDocuments().single()
            assertEquals(documentId, restored.id)
            assertEquals("LAB", restored.category)
            assertEquals("2026-11-25", restored.documentDate)
            assertEquals(labId, restored.relatedLabResultId)
            assertEquals(procedureId, restored.relatedProcedureId)
            assertEquals(appointmentId, restored.relatedAppointmentId)
            assertEquals("Synthetic allergen", restoredDao.getAllAllergies().single().substance)
            assertEquals(labId, restoredDao.getAllLabResults().single().id)
            assertEquals(medicationId, restoredDao.getAllMedications().single().id)
            assertEquals(conditionId, restoredDao.getAllConditions().single().id)
            assertEquals(procedureId, restoredDao.getAllProcedures().single().id)
            assertEquals(historyId, restoredDao.getAllHistoryEvents().single().id)
            assertEquals(familyHistoryId, restoredDao.getAllFamilyDiseaseHistory().single().id)
            assertEquals(measurementId, restoredDao.getAllBodyMeasurements().single().id)
            assertEquals(appointmentId, restoredDao.getAllAppointments().single().id)
            assertEquals(DoseStatus.TAKEN.name, restoredDao.getAllDoseLogs().single().status)
            val bytes = ByteArrayOutputStream().also { store.copyPlaintextTo(restored, it) }.toByteArray()
            assertArrayEquals(fixture, bytes)
            val stagedPreview = File(checkNotNull(proof.stagedArchivePath))
            assertTrue(stagedPreview.exists())
            restore.discardPreview(proof)
            assertFalse(stagedPreview.exists())
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
