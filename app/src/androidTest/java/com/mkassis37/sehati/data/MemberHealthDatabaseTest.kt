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
}
