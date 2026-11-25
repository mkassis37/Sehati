package com.mkassis37.sehati.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [FamilyMember::class, AllergyRecord::class, LabResult::class, MedicalDocument::class, MedicationRecord::class, ChronicCondition::class, MedicalProcedure::class, MedicalHistoryEvent::class, FamilyDiseaseHistory::class, BodyMeasurement::class], version = 8, exportSchema = true)
abstract class SehatiDatabase : RoomDatabase() {
    abstract fun medicalDao(): MedicalDao

    /** Removes a member and every related row atomically, returning document paths for secure file cleanup. */
    suspend fun deleteMemberAndRecords(memberId: Long): List<MedicalDocument> = withTransaction {
        val dao = medicalDao()
        val documents = dao.getDocumentsForMember(memberId)
        dao.deleteAllergiesForMember(memberId)
        dao.deleteConditionsForMember(memberId)
        dao.deleteProceduresForMember(memberId)
        dao.deleteHistoryEventsForMember(memberId)
        dao.deleteFamilyDiseaseHistoryForMember(memberId)
        dao.deleteBodyMeasurementsForMember(memberId)
        dao.deleteLabResultsForMember(memberId)
        dao.deleteMedicationsForMember(memberId)
        dao.deleteDocumentsForMember(memberId)
        dao.deleteMember(memberId)
        documents
    }

    suspend fun deleteProcedureAndUnlinkAttachments(procedureId: Long, memberId: Long) = withTransaction {
        medicalDao().unlinkDocumentsFromProcedure(procedureId)
        medicalDao().deleteProcedure(procedureId, memberId)
    }

    companion object {
        @Volatile private var INSTANCE: SehatiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS family_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fullName TEXT NOT NULL,
                    dateOfBirth TEXT NOT NULL,
                    bloodGroup TEXT NOT NULL,
                    medicalNotes TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO family_members
                    (id, fullName, dateOfBirth, bloodGroup, medicalNotes, updatedAt)
                    SELECT id, fullName, dateOfBirth, bloodGroup, medicalNotes, updatedAt
                    FROM medical_profile WHERE id = 1""")
                db.execSQL("ALTER TABLE allergy_records ADD COLUMN memberId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_allergy_records_memberId ON allergy_records(memberId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS lab_results (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    memberId INTEGER NOT NULL,
                    testName TEXT NOT NULL,
                    value REAL NOT NULL,
                    unit TEXT NOT NULL,
                    referenceLow REAL,
                    referenceHigh REAL,
                    performedAt TEXT NOT NULL,
                    laboratory TEXT NOT NULL,
                    source TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_results_memberId_testName_performedAt ON lab_results(memberId, testName, performedAt)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS medical_documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    memberId INTEGER NOT NULL,
                    displayName TEXT NOT NULL,
                    relativePath TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    addedAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_documents_memberId_addedAt ON medical_documents(memberId, addedAt)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS medication_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    memberId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    strength TEXT NOT NULL,
                    dosage TEXT NOT NULL,
                    form TEXT NOT NULL,
                    frequency TEXT NOT NULL,
                    scheduledTimes TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    endDate TEXT NOT NULL,
                    prescribingDoctor TEXT NOT NULL,
                    instructions TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_records_memberId_isActive_name ON medication_records(memberId, isActive, name)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE allergy_records ADD COLUMN commercialName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE allergy_records ADD COLUMN activeIngredient TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "sex TEXT NOT NULL DEFAULT ''",
                    "phone TEXT NOT NULL DEFAULT ''",
                    "additionalInfo TEXT NOT NULL DEFAULT ''",
                    "emergencyContactName TEXT NOT NULL DEFAULT ''",
                    "emergencyContactPhone TEXT NOT NULL DEFAULT ''",
                    "emergencyContactRelation TEXT NOT NULL DEFAULT ''",
                    "emergencyShowAllergies INTEGER NOT NULL DEFAULT 1",
                    "emergencyShowMedications INTEGER NOT NULL DEFAULT 1",
                    "emergencyShowConditions INTEGER NOT NULL DEFAULT 1",
                    "emergencyShowNotes INTEGER NOT NULL DEFAULT 0"
                ).forEach { db.execSQL("ALTER TABLE family_members ADD COLUMN $it") }
                db.execSQL("ALTER TABLE medical_documents ADD COLUMN relatedProcedureId INTEGER")
                db.execSQL("""CREATE TABLE IF NOT EXISTS chronic_conditions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, memberId INTEGER NOT NULL,
                    name TEXT NOT NULL, diagnosedOn TEXT NOT NULL, diagnosingDoctor TEXT NOT NULL,
                    status TEXT NOT NULL, treatment TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chronic_conditions_memberId_status_diagnosedOn ON chronic_conditions(memberId, status, diagnosedOn)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS medical_procedures (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, memberId INTEGER NOT NULL,
                    name TEXT NOT NULL, performedOn TEXT NOT NULL, facility TEXT NOT NULL, doctor TEXT NOT NULL,
                    reason TEXT NOT NULL, complications TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_procedures_memberId_performedOn ON medical_procedures(memberId, performedOn)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS medical_history_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, memberId INTEGER NOT NULL,
                    category TEXT NOT NULL, title TEXT NOT NULL, eventDate TEXT NOT NULL,
                    notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_history_events_memberId_eventDate ON medical_history_events(memberId, eventDate)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS family_disease_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, memberId INTEGER NOT NULL,
                    relative TEXT NOT NULL, disease TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_family_disease_history_memberId_relative_disease ON family_disease_history(memberId, relative, disease)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS body_measurements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, memberId INTEGER NOT NULL,
                    measuredOn TEXT NOT NULL, heightCm REAL, weightKg REAL,
                    notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_body_measurements_memberId_measuredOn ON body_measurements(memberId, measuredOn)")
            }
        }
        fun getInstance(context: Context): SehatiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): SehatiDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = SecurePassphraseStore(context).getOrCreate()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, SehatiDatabase::class.java, "sehati.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MedicalDatabaseMigrations.LAB_RESULTS_7_8)
                .build()
        }
    }
}
