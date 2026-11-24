package com.mkassis37.sehati.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [FamilyMember::class, AllergyRecord::class, LabResult::class, MedicalDocument::class, MedicationRecord::class], version = 5, exportSchema = true)
abstract class SehatiDatabase : RoomDatabase() {
    abstract fun medicalDao(): MedicalDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}
