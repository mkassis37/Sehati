package com.mkassis37.sehati.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalDao {
    @Query("""
        SELECT m.id AS memberId, m.fullName AS memberName, records.kind, records.recordId,
               records.title, records.detail
          FROM (
              SELECT id AS recordId, id AS ownerId, 'MEMBER' AS kind, fullName AS title,
                     '' AS detail, fullName || ' ' || medicalNotes || ' ' || additionalInfo AS terms FROM family_members
              UNION ALL SELECT id, memberId, 'ALLERGY', substance, category,
                     substance || ' ' || commercialName || ' ' || activeIngredient || ' ' || reaction || ' ' || notes FROM allergy_records
              UNION ALL SELECT id, memberId, 'LAB', testName, performedAt,
                     testName || ' ' || COALESCE(qualitativeValue, '') || ' ' || laboratory || ' ' || notes FROM lab_results
              UNION ALL SELECT id, memberId, 'MEDICATION', name, strength,
                     name || ' ' || strength || ' ' || prescribingDoctor || ' ' || instructions || ' ' || notes FROM medication_records
              UNION ALL SELECT id, memberId, 'VISIT', doctor, date,
                     doctor || ' ' || specialty || ' ' || facility || ' ' || reason || ' ' || outcome || ' ' || diagnosis || ' ' || recommendations || ' ' || notes FROM medical_appointments
              UNION ALL SELECT id, memberId, 'DOCUMENT', displayName, documentDate, displayName FROM medical_documents
              UNION ALL SELECT id, memberId, 'HISTORY', title, eventDate,
                     title || ' ' || category || ' ' || notes FROM medical_history_events
              UNION ALL SELECT id, memberId, 'CONDITION', name, diagnosedOn,
                     name || ' ' || diagnosingDoctor || ' ' || treatment || ' ' || notes FROM chronic_conditions
              UNION ALL SELECT id, memberId, 'FAMILY_HISTORY', disease, relative,
                     disease || ' ' || relative || ' ' || notes FROM family_disease_history
              UNION ALL SELECT id, memberId, 'PROCEDURE', name, performedOn,
                     name || ' ' || facility || ' ' || doctor || ' ' || reason || ' ' || complications || ' ' || notes FROM medical_procedures
              UNION ALL SELECT id, memberId, 'BODY_MEASUREMENT', 'قياس الطول والوزن', measuredOn,
                     'قياس الطول والوزن ' || measuredOn || ' ' || COALESCE(CAST(heightCm AS TEXT), '') || ' ' ||
                     COALESCE(CAST(weightKg AS TEXT), '') || ' ' || notes FROM body_measurements
              UNION ALL SELECT dl.rowid, dl.memberId, 'DOSE_LOG', 'جرعة: ' || med.name,
                     dl.date || ' ' || dl.time,
                     med.name || ' ' || dl.date || ' ' || dl.time || ' ' || dl.status
                FROM dose_logs AS dl JOIN medication_records AS med
                  ON med.id = dl.medicationId AND med.memberId = dl.memberId
          ) AS records JOIN family_members AS m ON records.ownerId = m.id
         WHERE (:memberId IS NULL OR m.id = :memberId)
           AND instr(lower(records.terms), lower(:query)) > 0
         ORDER BY CASE records.kind WHEN 'MEMBER' THEN 0 ELSE 1 END, m.fullName, records.kind, records.recordId DESC
         LIMIT 100
    """)
    fun searchMedicalRecords(query: String, memberId: Long?): Flow<List<MedicalSearchHit>>


    @Query("SELECT * FROM family_members ORDER BY fullName COLLATE NOCASE")
    fun observeMembers(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE id = :memberId LIMIT 1")
    fun observeMember(memberId: Long): Flow<FamilyMember?>

    @Query("SELECT EXISTS(SELECT 1 FROM family_members WHERE id = :memberId)")
    suspend fun memberExists(memberId: Long): Boolean

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

    @Update
    suspend fun updateLabResult(record: LabResult): Int

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

    @Query("SELECT * FROM dose_logs WHERE memberId = :memberId ORDER BY date DESC, time DESC")
    fun observeDoseLogs(memberId: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs ORDER BY medicationId, date, time")
    suspend fun getAllDoseLogs(): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE date >= :date")
    suspend fun getDoseLogsSince(date: String): List<DoseLog>

    @Insert suspend fun insertDoseLogs(rows: List<DoseLog>)
    @Upsert suspend fun saveDoseLog(row: DoseLog)

    @Query("SELECT * FROM medication_records WHERE id = :medicationId AND memberId = :memberId")
    suspend fun findMedication(medicationId: Long, memberId: Long): MedicationRecord?

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

    @Query("SELECT EXISTS(SELECT 1 FROM lab_results WHERE id = :id AND memberId = :memberId)")
    suspend fun ownsLabResult(id: Long, memberId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM medical_procedures WHERE id = :id AND memberId = :memberId)")
    suspend fun ownsProcedure(id: Long, memberId: Long): Boolean

    @Query("SELECT * FROM medical_documents WHERE id = :id AND memberId = :memberId")
    suspend fun findDocument(id: Long, memberId: Long): MedicalDocument?

    @Query("UPDATE medical_documents SET relatedLabResultId = NULL WHERE relatedLabResultId = :id AND memberId = :memberId")
    suspend fun unlinkLabDocuments(id: Long, memberId: Long)

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

    @Query("UPDATE medical_documents SET relatedProcedureId = NULL WHERE relatedProcedureId = :procedureId AND memberId = :memberId")
    suspend fun unlinkDocumentsFromProcedure(procedureId: Long, memberId: Long)

    @Query("DELETE FROM medical_documents")
    suspend fun clearDocuments()
}
