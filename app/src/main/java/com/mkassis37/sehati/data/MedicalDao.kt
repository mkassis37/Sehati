package com.mkassis37.sehati.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalDao {
    @Query("SELECT * FROM family_members ORDER BY fullName COLLATE NOCASE")
    fun observeMembers(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE id = :memberId LIMIT 1")
    fun observeMember(memberId: Long): Flow<FamilyMember?>

    @Query("SELECT * FROM family_members ORDER BY id")
    suspend fun getAllMembers(): List<FamilyMember>

    @Insert
    suspend fun insertMembers(members: List<FamilyMember>)

    @Query("DELETE FROM family_members")
    suspend fun clearMembers()

    @Insert
    suspend fun addMember(member: FamilyMember): Long

    @Update
    suspend fun updateMember(member: FamilyMember)

    @Query("DELETE FROM family_members WHERE id = :memberId")
    suspend fun deleteMember(memberId: Long)

    @Query("SELECT * FROM allergy_records WHERE memberId = :memberId ORDER BY createdAt DESC")
    fun observeAllergies(memberId: Long): Flow<List<AllergyRecord>>

    @Query("SELECT * FROM allergy_records ORDER BY id")
    suspend fun getAllAllergies(): List<AllergyRecord>

    @Insert
    suspend fun insertAllergies(records: List<AllergyRecord>)

    @Query("DELETE FROM allergy_records")
    suspend fun clearAllergies()

    @Insert
    suspend fun addAllergy(record: AllergyRecord)

    @Query("DELETE FROM allergy_records WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteAllergy(recordId: Long, memberId: Long)
}
