package com.mkassis37.sehati.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object MedicalDatabaseMigrations {
    val LAB_RESULTS_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE lab_results_v8 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    memberId INTEGER NOT NULL, testName TEXT NOT NULL,
                    value REAL, unit TEXT NOT NULL, referenceLow REAL, referenceHigh REAL,
                    performedAt TEXT NOT NULL, laboratory TEXT NOT NULL, source TEXT NOT NULL,
                    notes TEXT NOT NULL, createdAt INTEGER NOT NULL, qualitativeValue TEXT
                )"""
            )
            db.execSQL(
                """INSERT INTO lab_results_v8
                    (id, memberId, testName, value, unit, referenceLow, referenceHigh,
                     performedAt, laboratory, source, notes, createdAt)
                    SELECT id, memberId, testName, value, unit, referenceLow, referenceHigh,
                           performedAt, laboratory, source, notes, createdAt FROM lab_results"""
            )
            db.execSQL("DROP TABLE lab_results")
            db.execSQL("ALTER TABLE lab_results_v8 RENAME TO lab_results")
            db.execSQL(
                "CREATE INDEX index_lab_results_memberId_testName_performedAt ON lab_results(memberId, testName, performedAt)"
            )
        }
    }

    val APPOINTMENTS_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE medical_appointments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    memberId INTEGER NOT NULL, doctor TEXT NOT NULL,
                    specialty TEXT NOT NULL, facility TEXT NOT NULL,
                    date TEXT NOT NULL, time TEXT NOT NULL, reason TEXT NOT NULL,
                    notes TEXT NOT NULL, status TEXT NOT NULL, outcome TEXT NOT NULL,
                    diagnosis TEXT NOT NULL, recommendations TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    FOREIGN KEY(memberId) REFERENCES family_members(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL("CREATE INDEX index_medical_appointments_memberId_date_time ON medical_appointments(memberId, date, time)")
            db.execSQL("ALTER TABLE medical_documents ADD COLUMN relatedAppointmentId INTEGER")
        }
    }

    val DOSES_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medication_records ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """CREATE TABLE dose_logs (
                    medicationId INTEGER NOT NULL, memberId INTEGER NOT NULL,
                    date TEXT NOT NULL, time TEXT NOT NULL, status TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL, snoozedUntil INTEGER,
                    PRIMARY KEY(medicationId, date, time),
                    FOREIGN KEY(medicationId) REFERENCES medication_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL("CREATE INDEX index_dose_logs_memberId_date ON dose_logs(memberId, date)")
        }
    }

    val DOCUMENTS_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medical_documents ADD COLUMN relatedLabResultId INTEGER")
            db.execSQL("ALTER TABLE medical_documents ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
            db.execSQL("ALTER TABLE medical_documents ADD COLUMN documentDate TEXT NOT NULL DEFAULT ''")
        }
    }
}
