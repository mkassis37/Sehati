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

    @Query("DELETE FROM allergy_records WHERE memberId = :memberId")
    suspend fun deleteAllergiesForMember(memberId: Long)

    @Query("SELECT * FROM chronic_conditions WHERE memberId = :memberId ORDER BY diagnosedOn DESC, id DESC")
    fun observeConditions(memberId: Long): Flow<List<ChronicCondition>>
    @Query("SELECT * FROM chronic_conditions ORDER BY id")
    suspend fun getAllConditions(): List<ChronicCondition>
    @Insert suspend fun addCondition(record: ChronicCondition): Long
    @Insert suspend fun insertConditions(records: List<ChronicCondition>)
    @Update suspend fun updateCondition(record: ChronicCondition)
    @Query("DELETE FROM chronic_conditions WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteCondition(recordId: Long, memberId: Long)
    @Query("DELETE FROM chronic_conditions WHERE memberId = :memberId")
    suspend fun deleteConditionsForMember(memberId: Long)
    @Query("DELETE FROM chronic_conditions") suspend fun clearConditions()

    @Query("SELECT * FROM medical_procedures WHERE memberId = :memberId ORDER BY performedOn DESC, id DESC")
    fun observeProcedures(memberId: Long): Flow<List<MedicalProcedure>>
    @Query("SELECT * FROM medical_procedures ORDER BY id")
    suspend fun getAllProcedures(): List<MedicalProcedure>
    @Insert suspend fun addProcedure(record: MedicalProcedure): Long
    @Insert suspend fun insertProcedures(records: List<MedicalProcedure>)
    @Update suspend fun updateProcedure(record: MedicalProcedure)
    @Query("DELETE FROM medical_procedures WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteProcedure(recordId: Long, memberId: Long)
    @Query("DELETE FROM medical_procedures WHERE memberId = :memberId")
    suspend fun deleteProceduresForMember(memberId: Long)
    @Query("DELETE FROM medical_procedures") suspend fun clearProcedures()

    @Query("SELECT * FROM medical_history_events WHERE memberId = :memberId ORDER BY eventDate DESC, id DESC")
    fun observeHistoryEvents(memberId: Long): Flow<List<MedicalHistoryEvent>>
    @Query("SELECT * FROM medical_history_events ORDER BY id")
    suspend fun getAllHistoryEvents(): List<MedicalHistoryEvent>
    @Insert suspend fun addHistoryEvent(record: MedicalHistoryEvent): Long
    @Insert suspend fun insertHistoryEvents(records: List<MedicalHistoryEvent>)
    @Update suspend fun updateHistoryEvent(record: MedicalHistoryEvent)
    @Query("DELETE FROM medical_history_events WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteHistoryEvent(recordId: Long, memberId: Long)
    @Query("DELETE FROM medical_history_events WHERE memberId = :memberId")
    suspend fun deleteHistoryEventsForMember(memberId: Long)
    @Query("DELETE FROM medical_history_events") suspend fun clearHistoryEvents()

    @Query("SELECT * FROM family_disease_history WHERE memberId = :memberId ORDER BY relative COLLATE NOCASE, disease COLLATE NOCASE")
    fun observeFamilyDiseaseHistory(memberId: Long): Flow<List<FamilyDiseaseHistory>>
    @Query("SELECT * FROM family_disease_history ORDER BY id")
    suspend fun getAllFamilyDiseaseHistory(): List<FamilyDiseaseHistory>
    @Insert suspend fun addFamilyDiseaseHistory(record: FamilyDiseaseHistory): Long
    @Insert suspend fun insertFamilyDiseaseHistory(records: List<FamilyDiseaseHistory>)
    @Update suspend fun updateFamilyDiseaseHistory(record: FamilyDiseaseHistory)
    @Query("DELETE FROM family_disease_history WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteFamilyDiseaseHistory(recordId: Long, memberId: Long)
    @Query("DELETE FROM family_disease_history WHERE memberId = :memberId")
    suspend fun deleteFamilyDiseaseHistoryForMember(memberId: Long)
    @Query("DELETE FROM family_disease_history") suspend fun clearFamilyDiseaseHistory()

    @Query("SELECT * FROM body_measurements WHERE memberId = :memberId ORDER BY measuredOn DESC, id DESC")
    fun observeBodyMeasurements(memberId: Long): Flow<List<BodyMeasurement>>
    @Query("SELECT * FROM body_measurements ORDER BY id")
    suspend fun getAllBodyMeasurements(): List<BodyMeasurement>
    @Insert suspend fun addBodyMeasurement(record: BodyMeasurement): Long
    @Insert suspend fun insertBodyMeasurements(records: List<BodyMeasurement>)
    @Update suspend fun updateBodyMeasurement(record: BodyMeasurement)
    @Query("DELETE FROM body_measurements WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteBodyMeasurement(recordId: Long, memberId: Long)
    @Query("DELETE FROM body_measurements WHERE memberId = :memberId")
    suspend fun deleteBodyMeasurementsForMember(memberId: Long)
    @Query("DELETE FROM body_measurements") suspend fun clearBodyMeasurements()

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

    @Query("""UPDATE allergy_records SET category = :category, substance = :substance,
        commercialName = :commercialName, activeIngredient = :activeIngredient,
        reaction = :reaction, severity = :severity, notes = :notes
        WHERE id = :recordId AND memberId = :memberId""")
    suspend fun updateAllergy(
        recordId: Long, memberId: Long, category: String, substance: String,
        commercialName: String, activeIngredient: String, reaction: String,
        severity: String, notes: String
    )

    @Query("DELETE FROM allergy_records WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteAllergy(recordId: Long, memberId: Long)

    @Query("SELECT * FROM lab_results WHERE memberId = :memberId ORDER BY performedAt DESC, id DESC")
    fun observeLabResults(memberId: Long): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_results ORDER BY id")
    suspend fun getAllLabResults(): List<LabResult>

    @Insert
    suspend fun insertLabResults(records: List<LabResult>)

    @Insert
    suspend fun addLabResult(record: LabResult): Long

    @Query("DELETE FROM lab_results WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteLabResult(recordId: Long, memberId: Long)

    @Query("DELETE FROM lab_results WHERE memberId = :memberId")
    suspend fun deleteLabResultsForMember(memberId: Long)

    @Query("DELETE FROM lab_results")
    suspend fun clearLabResults()

    @Query("SELECT * FROM medical_appointments WHERE memberId = :memberId ORDER BY date, time, id")
    fun observeAppointments(memberId: Long): Flow<List<MedicalAppointment>>

    @Query("SELECT * FROM medical_appointments ORDER BY id")
    suspend fun getAllAppointments(): List<MedicalAppointment>

    @Insert suspend fun addAppointment(record: MedicalAppointment): Long
    @Insert suspend fun insertAppointments(records: List<MedicalAppointment>)

    @Query(
        """UPDATE medical_appointments SET doctor = :doctor, specialty = :specialty, facility = :facility,
           date = :date, time = :time, reason = :reason, notes = :notes, status = :status,
           outcome = :outcome, diagnosis = :diagnosis, recommendations = :recommendations
           WHERE id = :id AND memberId = :memberId"""
    )
    suspend fun updateAppointment(
        id: Long, memberId: Long, doctor: String, specialty: String, facility: String,
        date: String, time: String, reason: String, notes: String, status: String,
        outcome: String, diagnosis: String, recommendations: String
    ): Int

    @Query("DELETE FROM medical_appointments WHERE id = :id AND memberId = :memberId")
    suspend fun deleteAppointment(id: Long, memberId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM medical_appointments WHERE id = :id AND memberId = :memberId)")
    suspend fun ownsAppointment(id: Long, memberId: Long): Boolean

    @Query("UPDATE medical_documents SET relatedAppointmentId = NULL WHERE relatedAppointmentId = :id AND memberId = :memberId")
    suspend fun unlinkAppointmentDocuments(id: Long, memberId: Long)

    @Query("SELECT * FROM medication_records WHERE memberId = :memberId ORDER BY isActive DESC, name COLLATE NOCASE")
    fun observeMedications(memberId: Long): Flow<List<MedicationRecord>>

    @Insert
    suspend fun addMedication(record: MedicationRecord): Long

    @Update
    suspend fun updateMedication(record: MedicationRecord)

    @Query("DELETE FROM medication_records WHERE id = :recordId AND memberId = :memberId")
    suspend fun deleteMedication(recordId: Long, memberId: Long)

    @Query("DELETE FROM medication_records WHERE memberId = :memberId")
    suspend fun deleteMedicationsForMember(memberId: Long)

    @Query("SELECT * FROM medication_records ORDER BY id")
    suspend fun getAllMedications(): List<MedicationRecord>

    @Insert
    suspend fun insertMedications(records: List<MedicationRecord>)

    @Query("DELETE FROM medication_records")
    suspend fun clearMedications()

    @Query("SELECT * FROM medical_documents WHERE memberId = :memberId ORDER BY addedAt DESC")
    fun observeDocuments(memberId: Long): Flow<List<MedicalDocument>>

    @Query("SELECT * FROM medical_documents WHERE memberId = :memberId AND relatedProcedureId = :procedureId ORDER BY addedAt DESC")
    fun observeProcedureDocuments(memberId: Long, procedureId: Long): Flow<List<MedicalDocument>>

    @Query("SELECT * FROM medical_documents ORDER BY id")
    suspend fun getAllDocuments(): List<MedicalDocument>

    @Insert
    suspend fun insertDocuments(records: List<MedicalDocument>)

    @Insert
    suspend fun addDocument(record: MedicalDocument): Long

    @Query("DELETE FROM medical_documents WHERE id = :documentId AND memberId = :memberId")
    suspend fun deleteDocument(documentId: Long, memberId: Long)

    @Query("SELECT * FROM medical_documents WHERE memberId = :memberId ORDER BY id")
    suspend fun getDocumentsForMember(memberId: Long): List<MedicalDocument>

    @Query("DELETE FROM medical_documents WHERE memberId = :memberId")
    suspend fun deleteDocumentsForMember(memberId: Long)

    @Query("UPDATE medical_documents SET relatedProcedureId = NULL WHERE relatedProcedureId = :procedureId")
    suspend fun unlinkDocumentsFromProcedure(procedureId: Long)

    @Query("DELETE FROM medical_documents")
    suspend fun clearDocuments()
}
