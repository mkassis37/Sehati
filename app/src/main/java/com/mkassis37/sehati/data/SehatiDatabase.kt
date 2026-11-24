package com.mkassis37.sehati.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [FamilyMember::class, AllergyRecord::class], version = 2, exportSchema = true)
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
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
