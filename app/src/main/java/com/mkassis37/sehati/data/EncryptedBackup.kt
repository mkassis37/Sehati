package com.mkassis37.sehati.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned, authenticated backup format. No cloud upload is performed by this class. */
class EncryptedBackup(private val database: SehatiDatabase, private val documentStore: SecureMedicalDocumentStore) {
    private data class BackupRows(
        val members: List<FamilyMember>, val allergies: List<AllergyRecord>, val labResults: List<LabResult>,
        val documents: List<MedicalDocument>, val medications: List<MedicationRecord>, val conditions: List<ChronicCondition>,
        val procedures: List<MedicalProcedure>, val historyEvents: List<MedicalHistoryEvent>,
        val familyHistory: List<FamilyDiseaseHistory>, val measurements: List<BodyMeasurement>,
        val appointments: List<MedicalAppointment>, val doses: List<DoseLog>
    )

    suspend fun write(resolver: ContentResolver, uri: Uri, password: CharArray) {
        require(password.size >= 10) { "Backup password must be at least 10 characters." }
        val rows = database.withTransaction {
            val dao = database.medicalDao()
            BackupRows(
                members = dao.getAllMembers(), allergies = dao.getAllAllergies(), labResults = dao.getAllLabResults(),
                documents = dao.getAllDocuments(), medications = dao.getAllMedications(), conditions = dao.getAllConditions(),
                procedures = dao.getAllProcedures(), historyEvents = dao.getAllHistoryEvents(),
                familyHistory = dao.getAllFamilyDiseaseHistory(), measurements = dao.getAllBodyMeasurements(),
                appointments = dao.getAllAppointments(), doses = dao.getAllDoseLogs()
            )
        }
        val members = rows.members
        val allergies = rows.allergies
        val labResults = rows.labResults
        val documents = rows.documents
        val medications = rows.medications
        val conditions = rows.conditions
        val procedures = rows.procedures
        val historyEvents = rows.historyEvents
        val familyHistory = rows.familyHistory
        val measurements = rows.measurements
        val payload = JSONObject()
            .put("format", "sehati-backup")
            .put("version", when {
                documents.any { it.category != "OTHER" || it.documentDate.isNotBlank() || it.relatedLabResultId != null } -> 6
                rows.doses.isNotEmpty() || medications.any { it.completed } -> 5
                rows.appointments.isNotEmpty() -> 4
                labResults.any { it.qualitativeValue != null } -> 3
                else -> 2
            })
            .put("createdAt", System.currentTimeMillis())
            .put("members", JSONArray().apply {
                members.forEach { m -> put(JSONObject()
                    .put("id", m.id).put("fullName", m.fullName).put("dateOfBirth", m.dateOfBirth)
                    .put("bloodGroup", m.bloodGroup).put("medicalNotes", m.medicalNotes)
                    .put("sex", m.sex).put("phone", m.phone).put("additionalInfo", m.additionalInfo)
                    .put("emergencyContactName", m.emergencyContactName).put("emergencyContactPhone", m.emergencyContactPhone)
                    .put("emergencyContactRelation", m.emergencyContactRelation)
                    .put("emergencyShowAllergies", m.emergencyShowAllergies)
                    .put("emergencyShowMedications", m.emergencyShowMedications)
                    .put("emergencyShowConditions", m.emergencyShowConditions)
                    .put("emergencyShowNotes", m.emergencyShowNotes).put("updatedAt", m.updatedAt)) }
            })
            .put("allergies", JSONArray().apply {
                allergies.forEach { a -> put(JSONObject()
                    .put("id", a.id).put("memberId", a.memberId).put("category", a.category)
                    .put("substance", a.substance).put("commercialName", a.commercialName)
                    .put("activeIngredient", a.activeIngredient).put("reaction", a.reaction).put("severity", a.severity)
                    .put("notes", a.notes).put("createdAt", a.createdAt)) }
            })
            .put("labResults", JSONArray().apply {
                labResults.forEach { r -> put(JSONObject()
                    .put("id", r.id).put("memberId", r.memberId).put("testName", r.testName)
                    .put("value", r.value ?: JSONObject.NULL).put("unit", r.unit)
                    .put("qualitativeValue", r.qualitativeValue ?: JSONObject.NULL)
                    .put("referenceLow", r.referenceLow ?: JSONObject.NULL)
                    .put("referenceHigh", r.referenceHigh ?: JSONObject.NULL)
                    .put("performedAt", r.performedAt).put("laboratory", r.laboratory)
                    .put("source", r.source).put("notes", r.notes).put("createdAt", r.createdAt)) }
            })
            .put("medications", JSONArray().apply {
                medications.forEach { m -> put(JSONObject()
                    .put("id", m.id).put("memberId", m.memberId).put("name", m.name)
                    .put("strength", m.strength).put("dosage", m.dosage).put("form", m.form)
                    .put("frequency", m.frequency).put("scheduledTimes", m.scheduledTimes)
                    .put("startDate", m.startDate).put("endDate", m.endDate)
                    .put("prescribingDoctor", m.prescribingDoctor).put("instructions", m.instructions)
                    .put("notes", m.notes).put("isActive", m.isActive).put("completed", m.completed)
                    .put("createdAt", m.createdAt).put("updatedAt", m.updatedAt)) }
            })
            .put("chronicConditions", JSONArray().apply { conditions.forEach { r -> put(JSONObject()
                .put("id", r.id).put("memberId", r.memberId).put("name", r.name).put("diagnosedOn", r.diagnosedOn)
                .put("diagnosingDoctor", r.diagnosingDoctor).put("status", r.status).put("treatment", r.treatment)
                .put("notes", r.notes).put("createdAt", r.createdAt)) } })
            .put("medicalProcedures", JSONArray().apply { procedures.forEach { r -> put(JSONObject()
                .put("id", r.id).put("memberId", r.memberId).put("name", r.name).put("performedOn", r.performedOn)
                .put("facility", r.facility).put("doctor", r.doctor).put("reason", r.reason)
                .put("complications", r.complications).put("notes", r.notes).put("createdAt", r.createdAt)) } })
            .put("historyEvents", JSONArray().apply { historyEvents.forEach { r -> put(JSONObject()
                .put("id", r.id).put("memberId", r.memberId).put("category", r.category).put("title", r.title)
                .put("eventDate", r.eventDate).put("notes", r.notes).put("createdAt", r.createdAt)) } })
            .put("familyDiseaseHistory", JSONArray().apply { familyHistory.forEach { r -> put(JSONObject()
                .put("id", r.id).put("memberId", r.memberId).put("relative", r.relative).put("disease", r.disease)
                .put("notes", r.notes).put("createdAt", r.createdAt)) } })
            .put("bodyMeasurements", JSONArray().apply { measurements.forEach { r -> put(JSONObject()
                .put("id", r.id).put("memberId", r.memberId).put("measuredOn", r.measuredOn)
                .put("heightCm", r.heightCm ?: JSONObject.NULL).put("weightKg", r.weightKg ?: JSONObject.NULL)
                .put("notes", r.notes).put("createdAt", r.createdAt)) } })
            .put("doseLogs", JSONArray().apply {
                rows.doses.forEach { dose -> put(JSONObject()
                    .put("medicationId", dose.medicationId).put("memberId", dose.memberId)
                    .put("date", dose.date).put("time", dose.time).put("status", dose.status)
                    .put("updatedAt", dose.updatedAt).put("snoozedUntil", dose.snoozedUntil ?: JSONObject.NULL)) }
            })
            .put("appointments", JSONArray().apply {
                rows.appointments.forEach { a -> put(JSONObject()
                    .put("id", a.id).put("memberId", a.memberId).put("doctor", a.doctor)
                    .put("specialty", a.specialty).put("facility", a.facility)
                    .put("date", a.date).put("time", a.time).put("reason", a.reason)
                    .put("notes", a.notes).put("status", a.status).put("outcome", a.outcome)
                    .put("diagnosis", a.diagnosis).put("recommendations", a.recommendations)
                    .put("createdAt", a.createdAt)) }
            })
            .put("documents", JSONArray().apply {
                documents.forEach { doc ->
                    val plain = documentStore.readPlaintextBytes(doc)
                    try {
                        put(JSONObject().put("id", doc.id).put("memberId", doc.memberId)
                            .put("displayName", doc.displayName).put("mimeType", doc.mimeType)
                            .put("sizeBytes", doc.sizeBytes).put("relatedProcedureId", doc.relatedProcedureId ?: JSONObject.NULL)
                            .put("addedAt", doc.addedAt)
                            .put("relatedAppointmentId", doc.relatedAppointmentId ?: JSONObject.NULL)
                            .put("relatedLabResultId", doc.relatedLabResultId ?: JSONObject.NULL)
                            .put("category", doc.category).put("documentDate", doc.documentDate)
                            .put("content", Base64.encodeToString(plain, Base64.NO_WRAP)))
                    } finally { plain.fill(0) }
                }
            }).toString().toByteArray(Charsets.UTF_8)

        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val key = derive(password, salt)
        password.fill('\u0000')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(payload)
        val envelope = MAGIC + byteArrayOf(1) + salt + cipher.iv + encrypted
        resolver.openOutputStream(uri, "wt")!!.use { it.write(envelope) }
        payload.fill(0)
    }

    suspend fun preview(resolver: ContentResolver, uri: Uri, password: CharArray): BackupPreview {
        val root = decrypt(resolver.openInputStream(uri)!!, password)
        require(root.optString("format") == "sehati-backup" && root.optInt("version") in 1..6) {
            "Unsupported backup file."
        }
        val names = buildList {
            val members = root.getJSONArray("members")
            for (i in 0 until members.length()) add(members.getJSONObject(i).optString("fullName"))
        }
        return BackupPreview(names, root.getJSONArray("allergies").length(), root)
    }

    suspend fun restore(preview: BackupPreview) {
        val root = preview.payload
        val members = root.getJSONArray("members")
        val allergies = root.getJSONArray("allergies")
        val labResults = root.optJSONArray("labResults") ?: JSONArray()
        val memberRows = (0 until members.length()).map { i ->
            val o = members.getJSONObject(i)
            FamilyMember(id = o.getLong("id"), fullName = o.getString("fullName"),
                dateOfBirth = o.optString("dateOfBirth"), bloodGroup = o.optString("bloodGroup"),
                medicalNotes = o.optString("medicalNotes"), sex = o.optString("sex"), phone = o.optString("phone"),
                additionalInfo = o.optString("additionalInfo"), emergencyContactName = o.optString("emergencyContactName"),
                emergencyContactPhone = o.optString("emergencyContactPhone"), emergencyContactRelation = o.optString("emergencyContactRelation"),
                emergencyShowAllergies = o.optBoolean("emergencyShowAllergies", true),
                emergencyShowMedications = o.optBoolean("emergencyShowMedications", true),
                emergencyShowConditions = o.optBoolean("emergencyShowConditions", true),
                emergencyShowNotes = o.optBoolean("emergencyShowNotes", false), updatedAt = o.optLong("updatedAt"))
        }
        val allergyRows = (0 until allergies.length()).map { i ->
            val o = allergies.getJSONObject(i)
            AllergyRecord(id = o.getLong("id"), memberId = o.getLong("memberId"),
                category = o.getString("category"), substance = o.getString("substance"),
                commercialName = o.optString("commercialName"), activeIngredient = o.optString("activeIngredient"),
                reaction = o.optString("reaction"), severity = o.optString("severity"),
                notes = o.optString("notes"), createdAt = o.optLong("createdAt"))
        }
        val labRows = (0 until labResults.length()).map { i ->
            val o = labResults.getJSONObject(i)
            LabResult(id = o.getLong("id"), memberId = o.getLong("memberId"),
                testName = o.getString("testName"),
                value = if (o.isNull("value")) null else o.getDouble("value"),
                qualitativeValue = if (o.isNull("qualitativeValue")) null else o.getString("qualitativeValue"),
                unit = o.optString("unit"),
                referenceLow = if (o.isNull("referenceLow")) null else o.getDouble("referenceLow"),
                referenceHigh = if (o.isNull("referenceHigh")) null else o.getDouble("referenceHigh"),
                performedAt = o.getString("performedAt"), laboratory = o.optString("laboratory"),
                source = o.optString("source", "manual"), notes = o.optString("notes"),
                createdAt = o.optLong("createdAt"))
        }
        val medicationArray = root.optJSONArray("medications") ?: JSONArray()
        val medicationRows = (0 until medicationArray.length()).map { i ->
            val o = medicationArray.getJSONObject(i)
            MedicationRecord(id = o.getLong("id"), memberId = o.getLong("memberId"),
                name = o.getString("name"), strength = o.optString("strength"),
                dosage = o.optString("dosage"), form = o.optString("form"),
                frequency = o.optString("frequency"), scheduledTimes = o.optString("scheduledTimes"),
                startDate = o.optString("startDate"), endDate = o.optString("endDate"),
                prescribingDoctor = o.optString("prescribingDoctor"), instructions = o.optString("instructions"),
                notes = o.optString("notes"), isActive = o.optBoolean("isActive", true),
                completed = o.optBoolean("completed", false),
                createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"))
        }
        fun array(name: String) = root.optJSONArray(name) ?: JSONArray()
        val conditionRows = (0 until array("chronicConditions").length()).map { i ->
            val o = array("chronicConditions").getJSONObject(i)
            ChronicCondition(id = o.getLong("id"), memberId = o.getLong("memberId"), name = o.getString("name"),
                diagnosedOn = o.optString("diagnosedOn"), diagnosingDoctor = o.optString("diagnosingDoctor"),
                status = o.optString("status", "نشط"), treatment = o.optString("treatment"),
                notes = o.optString("notes"), createdAt = o.optLong("createdAt"))
        }
        val procedureRows = (0 until array("medicalProcedures").length()).map { i ->
            val o = array("medicalProcedures").getJSONObject(i)
            MedicalProcedure(id = o.getLong("id"), memberId = o.getLong("memberId"), name = o.getString("name"),
                performedOn = o.optString("performedOn"), facility = o.optString("facility"), doctor = o.optString("doctor"),
                reason = o.optString("reason"), complications = o.optString("complications"),
                notes = o.optString("notes"), createdAt = o.optLong("createdAt"))
        }
        val historyRows = (0 until array("historyEvents").length()).map { i ->
            val o = array("historyEvents").getJSONObject(i)
            MedicalHistoryEvent(id = o.getLong("id"), memberId = o.getLong("memberId"), category = o.getString("category"),
                title = o.getString("title"), eventDate = o.optString("eventDate"), notes = o.optString("notes"),
                createdAt = o.optLong("createdAt"))
        }
        val familyHistoryRows = (0 until array("familyDiseaseHistory").length()).map { i ->
            val o = array("familyDiseaseHistory").getJSONObject(i)
            FamilyDiseaseHistory(id = o.getLong("id"), memberId = o.getLong("memberId"), relative = o.getString("relative"),
                disease = o.getString("disease"), notes = o.optString("notes"), createdAt = o.optLong("createdAt"))
        }
        val measurementRows = (0 until array("bodyMeasurements").length()).map { i ->
            val o = array("bodyMeasurements").getJSONObject(i)
            BodyMeasurement(id = o.getLong("id"), memberId = o.getLong("memberId"), measuredOn = o.getString("measuredOn"),
                heightCm = if (o.isNull("heightCm")) null else o.getDouble("heightCm"),
                weightKg = if (o.isNull("weightKg")) null else o.getDouble("weightKg"),
                notes = o.optString("notes"), createdAt = o.optLong("createdAt"))
        }
        val documentArray = root.optJSONArray("documents") ?: JSONArray()
        val doseArray = root.optJSONArray("doseLogs") ?: JSONArray()
        val doseRows = (0 until doseArray.length()).map { index ->
            val row = doseArray.getJSONObject(index)
            val date = java.time.LocalDate.parse(row.getString("date")).toString()
            val time = java.time.LocalTime.parse(row.getString("time")).toString()
            val status = DoseStatus.valueOf(row.getString("status"))
            val snoozedUntil = if (row.isNull("snoozedUntil")) null else row.getLong("snoozedUntil")
            val updatedAt = row.getLong("updatedAt")
            require(if (status == DoseStatus.SNOOZED) snoozedUntil != null && snoozedUntil > updatedAt else snoozedUntil == null)
            DoseLog(row.getLong("medicationId"), row.getLong("memberId"), date, time, status.name, updatedAt, snoozedUntil)
        }
        val appointmentArray = root.optJSONArray("appointments") ?: JSONArray()
        val appointmentRows = (0 until appointmentArray.length()).map { index ->
            val row = appointmentArray.getJSONObject(index)
            val date = row.getString("date").also { java.time.LocalDate.parse(it) }
            val time = row.getString("time").also { java.time.LocalTime.parse(it) }
            MedicalAppointment(
                id = row.getLong("id"), memberId = row.getLong("memberId"),
                doctor = row.getString("doctor"), specialty = row.optString("specialty"),
                facility = row.optString("facility"), date = date, time = time,
                reason = row.optString("reason"), notes = row.optString("notes"),
                status = AppointmentStatus.valueOf(row.getString("status")).name,
                outcome = row.optString("outcome"), diagnosis = row.optString("diagnosis"),
                recommendations = row.optString("recommendations"), createdAt = row.optLong("createdAt")
            )
        }
        val documentRows = (0 until documentArray.length()).map { i ->
            val o = documentArray.getJSONObject(i)
            val memberId = o.getLong("memberId")
            val id = o.optLong("id")
            val relativePath = "$memberId/restore-$id-${System.nanoTime()}.shtd"
            MedicalDocument(id = id, memberId = memberId,
                displayName = o.getString("displayName"), relativePath = relativePath,
                mimeType = o.optString("mimeType", "application/pdf"),
                sizeBytes = o.optLong("sizeBytes"),
                relatedProcedureId = if (o.isNull("relatedProcedureId")) null else o.optLong("relatedProcedureId"),
                addedAt = o.optLong("addedAt"),
                relatedAppointmentId = if (o.isNull("relatedAppointmentId")) null else o.getLong("relatedAppointmentId"),
                relatedLabResultId = if (o.isNull("relatedLabResultId")) null else o.getLong("relatedLabResultId"),
                category = o.optString("category", "OTHER"), documentDate = o.optString("documentDate"))
        }
        require(labRows.all { row ->
            val number = row.value
            val text = row.qualitativeValue
            (number != null && number.isFinite() && text == null) ||
                (number == null && !text.isNullOrBlank())
        }) { "Backup contains an invalid laboratory result." }
        require(allergyRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            labRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            medicationRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            documentRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            conditionRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            procedureRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            historyRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            familyHistoryRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            measurementRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            doseRows.all { row -> medicationRows.any { it.id == row.medicationId && it.memberId == row.memberId } } &&
            appointmentRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            documentRows.all { row -> row.relatedAppointmentId == null || appointmentRows.any { it.id == row.relatedAppointmentId && it.memberId == row.memberId } } &&
            documentRows.all { row -> row.relatedLabResultId == null || labRows.any { it.id == row.relatedLabResultId && it.memberId == row.memberId } } &&
            documentRows.all { row -> row.relatedProcedureId == null || procedureRows.any { it.id == row.relatedProcedureId && it.memberId == row.memberId } }) {
            "Backup contains records without a matching family member."
        }
        val createdFiles = mutableListOf<MedicalDocument>()
        var databaseCommitted = false
        val oldDocuments = database.medicalDao().getAllDocuments()
        try {
            documentRows.forEachIndexed { index, row ->
                val encoded = documentArray.getJSONObject(index).getString("content")
                val plain = Base64.decode(encoded, Base64.DEFAULT)
                try { createdFiles += documentStore.restore(row, plain) }
                finally { plain.fill(0) }
            }
            val dao = database.medicalDao()
            database.withTransaction {
                dao.clearAllergies()
                dao.clearConditions()
                dao.clearProcedures()
                dao.clearHistoryEvents()
                dao.clearFamilyDiseaseHistory()
                dao.clearBodyMeasurements()
                dao.clearLabResults()
                dao.clearDocuments()
                dao.clearMedications()
                dao.clearMembers()
                dao.insertMembers(memberRows)
                dao.insertAllergies(allergyRows)
                dao.insertAppointments(appointmentRows)
                dao.insertLabResults(labRows)
                dao.insertMedications(medicationRows)
                dao.insertDoseLogs(doseRows)
                dao.insertConditions(conditionRows)
                dao.insertProcedures(procedureRows)
                dao.insertHistoryEvents(historyRows)
                dao.insertFamilyDiseaseHistory(familyHistoryRows)
                dao.insertBodyMeasurements(measurementRows)
                dao.insertDocuments(createdFiles)
            }
            databaseCommitted = true
        } catch (error: Exception) {
            if (!databaseCommitted) createdFiles.forEach { runCatching { documentStore.delete(it) } }
            throw error
        }
        // File cleanup after the DB commit is best effort; never delete newly restored files on cleanup failure.
        oldDocuments.forEach { runCatching { documentStore.delete(it) } }
    }

    private fun decrypt(input: InputStream, password: CharArray): JSONObject {
        val bytes = input.use { it.readBytes() }
        require(bytes.size > 45 && bytes.copyOfRange(0, 4).contentEquals(MAGIC) && bytes[4].toInt() == 1) {
            "Invalid backup file."
        }
        val salt = bytes.copyOfRange(5, 21)
        val iv = bytes.copyOfRange(21, 33)
        val key = derive(password, salt)
        password.fill('\u0000')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(bytes.copyOfRange(33, bytes.size))
        return try { JSONObject(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 310_000, 256)
        val bytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
    }

    data class BackupPreview(val memberNames: List<String>, val allergyCount: Int, internal val payload: JSONObject) {
        val labResultCount: Int get() = payload.optJSONArray("labResults")?.length() ?: 0
        val conditionCount: Int get() = payload.optJSONArray("chronicConditions")?.length() ?: 0
        val procedureCount: Int get() = payload.optJSONArray("medicalProcedures")?.length() ?: 0
        val historyEventCount: Int get() = payload.optJSONArray("historyEvents")?.length() ?: 0
        val familyHistoryCount: Int get() = payload.optJSONArray("familyDiseaseHistory")?.length() ?: 0
        val measurementCount: Int get() = payload.optJSONArray("bodyMeasurements")?.length() ?: 0
        val doseLogCount: Int get() = payload.optJSONArray("doseLogs")?.length() ?: 0
        val appointmentCount: Int get() = payload.optJSONArray("appointments")?.length() ?: 0
        val documentCount: Int get() = payload.optJSONArray("documents")?.length() ?: 0
        val medicationCount: Int get() = payload.optJSONArray("medications")?.length() ?: 0
        val documentNames: List<String> get() = buildList {
            val docs = payload.optJSONArray("documents") ?: return@buildList
            for (i in 0 until docs.length()) add(docs.getJSONObject(i).optString("displayName"))
        }
    }
    companion object { private val MAGIC = byteArrayOf(0x53, 0x48, 0x54, 0x42) }
}
