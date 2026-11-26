package com.mkassis37.sehati.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemberHealthDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SehatiDatabase::class.java,
        emptyList()
    )

    @Test
    fun editingLabResultKeepsItsOwnerAndAttachment() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java).build()
        try {
            val dao = database.medicalDao()
            val owner = dao.addMember(FamilyMember(fullName = "Synthetic owner"))
            val other = dao.addMember(FamilyMember(fullName = "Synthetic other"))
            val result = LabResult(memberId = owner, testName = "Synthetic test", value = 7.0,
                performedAt = "2026-11-01", source = "pdf_review", createdAt = 123)
            val id = dao.addLabResult(result)
            val documentId = database.addLinkedDocument(MedicalDocument(memberId = owner,
                displayName = "Synthetic.pdf", relativePath = "$owner/synthetic.shtd",
                sizeBytes = 20, relatedLabResultId = id))
            val corrected = result.copy(id = id, value = 8.0)

            assertFalse(database.updateMemberLabResult(corrected.copy(memberId = other), other))
            assertEquals(7.0, dao.observeLabResults(owner).first().single().value!!, 0.0)
            assertTrue(database.updateMemberLabResult(corrected, owner))
            assertEquals(corrected, dao.observeLabResults(owner).first().single())
            assertTrue(dao.observeLabResults(other).first().isEmpty())
            assertEquals(id, dao.findDocument(documentId, owner)?.relatedLabResultId)
        } finally {
            database.close()
        }
    }

    @Test
    fun memberRecordsRemainSeparateAndMemberDeletionRemovesOnlyTheirRows() = runBlocking {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("synthetic-test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.medicalDao()
            val firstId = dao.addMember(FamilyMember(fullName = "Synthetic Member One"))
            val secondId = dao.addMember(FamilyMember(fullName = "Synthetic Member Two"))
            val conditionId = dao.addCondition(ChronicCondition(memberId = firstId, name = "Synthetic condition"))
            dao.updateCondition(ChronicCondition(id = conditionId, memberId = firstId, name = "Synthetic condition edited", status = "تحت المتابعة"))
            dao.addProcedure(MedicalProcedure(memberId = secondId, name = "Synthetic procedure"))
            dao.addAllergy(AllergyRecord(memberId = firstId, category = "دواء", substance = "Synthetic allergen", reaction = "Synthetic reaction", severity = "خفيفة"))
            dao.addAllergy(AllergyRecord(memberId = secondId, category = "طعام", substance = "Synthetic second allergen", reaction = "Synthetic second reaction", severity = "متوسطة"))
            val firstAllergy = dao.observeAllergies(firstId).first().single()
            dao.updateAllergy(firstAllergy.id, firstId, "دواء", "Synthetic allergen edited", "Synthetic brand", "Synthetic ingredient", "Updated synthetic reaction", "شديدة", "Synthetic note")
            dao.updateAllergy(firstAllergy.id, secondId, "مسبب آخر", "Unauthorized overwrite", "", "", "", "", "")
            dao.addFamilyDiseaseHistory(FamilyDiseaseHistory(memberId = firstId, relative = "الأب", disease = "مرض افتراضي"))
            dao.addBodyMeasurement(BodyMeasurement(memberId = firstId, measuredOn = "2024-01-01", heightCm = 150.0))
            dao.addHistoryEvent(MedicalHistoryEvent(memberId = firstId, category = "إصابة مهمة", title = "حدث افتراضي"))
            dao.addDocument(MedicalDocument(memberId = firstId, displayName = "synthetic-procedure.pdf", relativePath = "$firstId/test.shtd", sizeBytes = 20, relatedProcedureId = 5))
            dao.addDocument(MedicalDocument(memberId = secondId, displayName = "synthetic-other.pdf", relativePath = "$secondId/test.shtd", sizeBytes = 20))
            dao.updateMember(dao.observeMember(firstId).first()!!.copy(
                sex = "أنثى", emergencyContactName = "Synthetic Contact", emergencyContactPhone = "0000000", emergencyShowNotes = true
            ))

            assertEquals(listOf("Synthetic condition edited"), dao.observeConditions(firstId).first().map { it.name })
            assertEquals("Synthetic Contact", dao.observeMember(firstId).first()!!.emergencyContactName)
            assertTrue(dao.observeConditions(secondId).first().isEmpty())
            assertEquals(1, dao.observeFamilyDiseaseHistory(firstId).first().size)
            assertEquals("Synthetic allergen edited", dao.observeAllergies(firstId).first().single().substance)
            assertEquals("Synthetic second allergen", dao.observeAllergies(secondId).first().single().substance)
            assertTrue(dao.observeFamilyDiseaseHistory(secondId).first().isEmpty())
            assertEquals(1, dao.observeBodyMeasurements(firstId).first().size)
            assertTrue(dao.observeBodyMeasurements(secondId).first().isEmpty())

            val deletedDocuments = database.deleteMemberAndRecords(firstId)
            assertEquals(1, deletedDocuments.size)

            assertFalse(dao.observeMembers().first().any { it.id == firstId })
            assertTrue(dao.observeConditions(firstId).first().isEmpty())
            assertTrue(dao.observeFamilyDiseaseHistory(firstId).first().isEmpty())
            assertTrue(dao.observeBodyMeasurements(firstId).first().isEmpty())
            assertEquals(listOf("Synthetic procedure"), dao.observeProcedures(secondId).first().map { it.name })
            assertEquals(listOf("synthetic-other.pdf"), dao.getAllDocuments().map { it.displayName })
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromVersionSixPreservesExistingMemberAndAddsNewFieldsAndTables() {
        val databaseName = "sehati-v6-to-v7-test"
        migrationHelper.createDatabase(databaseName, 6).apply {
            execSQL("INSERT INTO family_members (id, fullName, dateOfBirth, bloodGroup, medicalNotes, updatedAt) VALUES (41, 'Synthetic Legacy Member', '2000-01-01', 'O+', 'synthetic note', 1234)")
            execSQL("INSERT INTO allergy_records (memberId, category, substance, commercialName, activeIngredient, reaction, severity, notes, createdAt) VALUES (41, 'synthetic', 'synthetic substance', '', '', 'synthetic reaction', 'low', '', 5)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            SehatiDatabase.MIGRATION_6_7
        )
        migrated.query("SELECT fullName, bloodGroup, medicalNotes, sex, phone, emergencyShowAllergies FROM family_members WHERE id = 41").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Synthetic Legacy Member", cursor.getString(0))
            assertEquals("O+", cursor.getString(1))
            assertEquals("synthetic note", cursor.getString(2))
            assertEquals("", cursor.getString(3))
            assertEquals("", cursor.getString(4))
            assertEquals(1, cursor.getInt(5))
        }
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('chronic_conditions','medical_procedures','medical_history_events','family_disease_history','body_measurements')").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) count++
            assertEquals(5, count)
        }
        migrated.query("SELECT memberId, substance FROM allergy_records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(41L, cursor.getLong(0))
            assertEquals("synthetic substance", cursor.getString(1))
        }
        migrated.close()
    }
    @Test
    fun migrationFromVersionSevenPreservesNumericResultsAndAllowsTextResults() {
        val databaseName = "sehati-v7-to-v8-test"
        migrationHelper.createDatabase(databaseName, 7).use { legacy ->
            legacy.execSQL(
                """INSERT INTO lab_results
                   (id, memberId, testName, value, unit, referenceLow, referenceHigh, performedAt,
                    laboratory, source, notes, createdAt)
                   VALUES (15, 41, 'Synthetic numeric', 2.5, 'g/L', 1, 4, '2024-01-01',
                           'Synthetic lab', 'manual', 'fixture', 1234)"""
            )
        }
        migrationHelper.runMigrationsAndValidate(
            databaseName, 8, true, MedicalDatabaseMigrations.LAB_RESULTS_7_8
        ).use { migrated ->
            migrated.query("SELECT memberId, value, unit, referenceLow, referenceHigh, qualitativeValue FROM lab_results WHERE id = 15").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(41L, cursor.getLong(0))
                assertEquals(2.5, cursor.getDouble(1), 0.0)
                assertEquals("g/L", cursor.getString(2))
                assertEquals(1.0, cursor.getDouble(3), 0.0)
                assertEquals(4.0, cursor.getDouble(4), 0.0)
                assertTrue(cursor.isNull(5))
            }
            migrated.execSQL(
                """INSERT INTO lab_results
                   (memberId, testName, qualitativeValue, unit, performedAt, laboratory, source, notes, createdAt)
                   VALUES (42, 'Synthetic qualitative', 'Trace', '', '2024-01-02', '', 'manual', '', 1235)"""
            )
            migrated.query("SELECT value, qualitativeValue FROM lab_results WHERE memberId = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals("Trace", cursor.getString(1))
            }
        }
    }

    @Test
    fun appointmentEditsDeletionAndAttachmentsAreMemberScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java).build()
        try {
            val dao = database.medicalDao()
            val owner = dao.addMember(FamilyMember(fullName = "Synthetic owner"))
            val other = dao.addMember(FamilyMember(fullName = "Synthetic other"))
            val appointment = MedicalAppointment(
                memberId = owner, doctor = "Synthetic doctor", specialty = "", facility = "",
                date = "2026-12-01", time = "10:00", reason = "", notes = "",
                status = AppointmentStatus.UPCOMING.name, outcome = "", diagnosis = "", recommendations = ""
            )
            assertTrue(database.saveAppointment(appointment, owner))
            val saved = dao.observeAppointments(owner).first().single()
            assertTrue(dao.observeAppointments(other).first().isEmpty())
            assertFalse(database.saveAppointment(saved.copy(memberId = other, doctor = "Cross-member edit"), other))
            assertFalse(database.deleteAppointment(saved.id, other))
            assertEquals("Synthetic doctor", dao.observeAppointments(owner).first().single().doctor)

            val document = MedicalDocument(memberId = owner, displayName = "fixture.pdf", relativePath = "$owner/fixture.shtd", sizeBytes = 10)
            assertTrue(runCatching { database.addAppointmentDocument(document.copy(memberId = other), saved.id) }.isFailure)
            database.addAppointmentDocument(document, saved.id)
            assertEquals(saved.id, dao.getDocumentsForMember(owner).single().relatedAppointmentId)
            assertTrue(database.deleteAppointment(saved.id, owner))
            assertEquals(null, dao.getDocumentsForMember(owner).single().relatedAppointmentId)

            assertTrue(database.saveAppointment(appointment, owner))
            database.deleteMemberAndRecords(owner)
            assertTrue(dao.observeAppointments(owner).first().isEmpty())
            assertEquals(other, dao.observeMembers().first().single().id)
        } finally {
            database.close()
        }
    }

    @Test
    fun doseLogsAreOwnedByTheMedicationMemberAndCascadeOnDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java).build()
        try {
            val dao = database.medicalDao()
            val owner = dao.addMember(FamilyMember(fullName = "Synthetic owner"))
            val other = dao.addMember(FamilyMember(fullName = "Synthetic other"))
            val medicationId = dao.addMedication(MedicationRecord(memberId = owner,
                name = "Synthetic medication", startDate = "2026-11-25"))
            val original = DoseLog(medicationId, owner, "2026-11-25", "08:00", DoseStatus.MISSED.name, 1234)
            assertTrue(database.recordDose(original, owner))
            assertFalse(database.recordDose(original.copy(memberId = other, status = DoseStatus.TAKEN.name), other))
            assertTrue(dao.observeDoseLogs(other).first().isEmpty())
            assertEquals(original, dao.observeDoseLogs(owner).first().single())
            assertTrue(database.recordDose(original.copy(status = DoseStatus.TAKEN.name), owner))
            assertEquals(DoseStatus.TAKEN.name, dao.observeDoseLogs(owner).first().single().status)
            dao.deleteMedication(medicationId, owner)
            assertTrue(dao.observeDoseLogs(owner).first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun familySearchCanLimitMatchesToOneMember() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, SehatiDatabase::class.java).build()
        try {
            val dao = database.medicalDao()
            val first = dao.addMember(FamilyMember(fullName = "Synthetic first"))
            val second = dao.addMember(FamilyMember(fullName = "Synthetic second"))
            dao.addAllergy(AllergyRecord(memberId = first, category = "fixture", substance = "Shared keyword", reaction = "", severity = ""))
            dao.addMedication(MedicationRecord(memberId = second, name = "Shared keyword", startDate = "2026-11-25"))
            assertEquals(setOf(first, second), dao.searchMedicalRecords("shared keyword", null).first().map { it.memberId }.toSet())
            val scoped = dao.searchMedicalRecords("shared keyword", first).first().single()
            assertEquals(first, scoped.memberId)
            assertEquals("ALLERGY", scoped.kind)
            assertTrue(dao.searchMedicalRecords("%", null).first().isEmpty())
        } finally {
            database.close()
        }
    }

}
