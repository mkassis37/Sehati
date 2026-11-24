package com.mkassis37.sehati.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalDao {
    @Query("SELECT * FROM medical_profile WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<MedicalProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: MedicalProfile)

    @Query("SELECT * FROM allergy_records ORDER BY createdAt DESC")
    fun observeAllergies(): Flow<List<AllergyRecord>>

    @Insert
    suspend fun addAllergy(record: AllergyRecord)

    @Query("DELETE FROM allergy_records WHERE id = :recordId")
    suspend fun deleteAllergy(recordId: Long)
}
