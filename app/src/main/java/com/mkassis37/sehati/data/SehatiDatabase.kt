package com.mkassis37.sehati.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [MedicalProfile::class, AllergyRecord::class],
    version = 1,
    exportSchema = true
)
abstract class SehatiDatabase : RoomDatabase() {
    abstract fun medicalDao(): MedicalDao

    companion object {
        @Volatile private var INSTANCE: SehatiDatabase? = null

        fun getInstance(context: Context): SehatiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): SehatiDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = SecurePassphraseStore(context).getOrCreate()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context,
                SehatiDatabase::class.java,
                "sehati.db"
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
