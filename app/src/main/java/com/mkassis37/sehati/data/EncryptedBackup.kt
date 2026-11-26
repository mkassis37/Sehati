package com.mkassis37.sehati.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.util.Base64OutputStream
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Versioned, authenticated backup format. No cloud upload is performed by this class. */
class EncryptedBackup(private val database: SehatiDatabase, private val documentStore: SecureMedicalDocumentStore) {
    private data class BackupRows(
        val members: List<FamilyMember>, val allergies: List<AllergyRecord>, val labResults: List<LabResult>,
        val documents: List<MedicalDocument>, val medications: List<MedicationRecord>, val conditions: List<ChronicCondition>,
        val procedures: List<MedicalProcedure>, val historyEvents: List<MedicalHistoryEvent>,
        val familyHistory: List<FamilyDiseaseHistory>, val measurements: List<BodyMeasurement>,
        val appointments: List<MedicalAppointment>, val doses: List<DoseLog>
    )

    suspend fun write(resolver: ContentResolver, uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            backupLock.withLock { writeInternal(resolver, uri, password) }
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun writeInternal(resolver: ContentResolver, uri: Uri, password: CharArray) {
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
            .put("version", 7)
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
                documents.forEach { doc -> put(documentMetadata(doc)) }
            })

        writeVersionTwo(resolver, uri, payload, documents, password)
    }

    suspend fun preview(resolver: ContentResolver, uri: Uri, password: CharArray): BackupPreview = withContext(Dispatchers.IO) {
        var stagedFile: File? = null
        var stagedKey: ByteArray? = null
        try {
            val source = resolver.openInputStream(uri) ?: throw IOException("تعذر فتح ملف النسخة الاحتياطية.")
            val pushback = PushbackInputStream(source, 5)
            val prefix = ByteArray(5)
            readFully(pushback, prefix)
            pushback.unread(prefix)
            when (prefix[4].toInt() and 0xff) {
                LEGACY_CONTAINER_VERSION -> {
                    val root = decrypt(pushback, password)
                    require(root.optString("format") == "sehati-backup" && root.optInt("version") in 1..6) {
                        "Unsupported legacy backup file."
                    }
                    createPreview(root)
                }
                STREAMING_CONTAINER_VERSION -> {
                    val archive = documentStore.createBackupPreviewFile()
                    stagedFile = archive
                    pushback.use { input ->
                        FileOutputStream(archive).use { output ->
                            input.copyTo(BackupLimitOutput(output), STREAM_BUFFER_SIZE)
                        }
                    }
                    val parsed = readVersionTwoManifest(archive, password)
                    stagedKey = parsed.keyBytes
                    validateVersionTwoArchiveLength(archive, parsed.root, parsed.manifestCipherLength)
                    require(parsed.root.optString("format") == "sehati-backup" && parsed.root.optInt("version") == 7) {
                        "Unsupported streaming backup payload."
                    }
                    val result = createPreview(parsed.root).copy(
                        stagedArchivePath = archive.absolutePath,
                        archiveKeyBytes = parsed.keyBytes,
                        manifestDigest = parsed.manifestDigest
                    )
                    stagedFile = null
                    stagedKey = null
                    result
                }
                else -> throw IOException("صيغة ملف النسخة الاحتياطية غير معروفة.")
            }
        } catch (error: Exception) {
            stagedKey?.fill(0)
            stagedFile?.delete()
            throw error
        } finally {
            password.fill('\u0000')
        }
    }

    private fun createPreview(root: JSONObject): BackupPreview {
        val names = buildList {
            val members = root.getJSONArray("members")
            for (i in 0 until members.length()) add(members.getJSONObject(i).optString("fullName"))
        }
        return BackupPreview(names, root.getJSONArray("allergies").length(), root)
    }

    fun discardPreview(preview: BackupPreview) {
        preview.archiveKeyBytes?.fill(0)
        documentStore.deleteBackupPreviewFile(preview.stagedArchivePath)
    }

    fun clearStalePreviewFiles() {
        documentStore.clearBackupPreviewFiles()
    }
    suspend fun restore(preview: BackupPreview) = withContext(Dispatchers.IO) {
        backupLock.withLock { restoreInternal(preview) }
    }

    private suspend fun restoreInternal(preview: BackupPreview) {
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
        currentCoroutineContext().ensureActive()
        val createdFiles = mutableListOf<MedicalDocument>()
        var databaseCommitted = false
        try {
            if (preview.stagedArchivePath != null) {
                restoreVersionTwoDocuments(preview, documentRows, createdFiles)
            } else {
                documentRows.forEachIndexed { index, row ->
                    currentCoroutineContext().ensureActive()
                    val encoded = documentArray.getJSONObject(index).getString("content")
                    val plain = Base64.decode(encoded, Base64.DEFAULT)
                    try { createdFiles += documentStore.restore(row, plain) }
                    finally { plain.fill(0) }
                }
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val dao = database.medicalDao()
                val replacedDocuments = database.withTransaction {
                    val old = dao.getAllDocuments()
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
                    old
                }
                databaseCommitted = true
                replacedDocuments.forEach { old ->
                    runCatching {
                        documentStore.clearTemporaryDocument(old.id)
                        documentStore.delete(old)
                    }
                }
            }
        } catch (error: Exception) {
            if (!databaseCommitted) createdFiles.forEach { runCatching { documentStore.delete(it) } }
            throw error
        }
    }

    private data class V2Manifest(val root: JSONObject, val keyBytes: ByteArray, val manifestDigest: String, val manifestCipherLength: Int)

    private fun writeVersionTwo(resolver: ContentResolver, uri: Uri, payload: JSONObject, documents: List<MedicalDocument>, password: CharArray) {
        require(password.size >= 10)
        val manifest = payload.toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST_BYTES)
        val staging = documentStore.createBackupPreviewFile()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val keyBytes = deriveBytes(password, salt)
        try {
            FileOutputStream(staging).use { file ->
                val output = DataOutputStream(BackupLimitOutput(file))
                output.write(MAGIC)
                output.writeByte(STREAMING_CONTAINER_VERSION)
                output.write(salt)
                val manifestIv = ByteArray(12).also(SecureRandom()::nextBytes)
                val manifestCipher = Cipher.getInstance("AES/GCM/NoPadding")
                manifestCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, manifestIv))
                manifestCipher.updateAAD(MANIFEST_AAD)
                val encryptedManifest = manifestCipher.doFinal(manifest)
                output.writeInt(encryptedManifest.size)
                output.write(manifestIv)
                output.write(encryptedManifest)
                encryptedManifest.fill(0)
                manifestIv.fill(0)
                documents.forEachIndexed { index, document ->
                    require(document.sizeBytes in 1..MAX_DOCUMENT_BYTES)
                    val chunked = ChunkedGcmOutputStream(output, keyBytes, document, index)
                    try {
                        documentStore.copyPlaintextTo(document, chunked)
                        chunked.finish()
                    } finally {
                        chunked.wipe()
                    }
                }
                output.flush()
            }
            if (staging.length() > MAX_BACKUP_BYTES) throw BackupSizeLimitException()
            checkNotNull(resolver.openOutputStream(uri, "wt")).buffered().use { destination ->
                FileInputStream(staging).use { it.copyTo(BackupLimitOutput(destination), STREAM_BUFFER_SIZE) }
            }
        } finally {
            keyBytes.fill(0)
            salt.fill(0)
            manifest.fill(0)
            staging.delete()
        }
    }

    private fun readVersionTwoManifest(file: File, password: CharArray): V2Manifest {
        val input = DataInputStream(BufferedInputStream(FileInputStream(file)))
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        var keyBytes: ByteArray? = null
        var encryptedManifest: ByteArray? = null
        try {
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC) && input.readUnsignedByte() == STREAMING_CONTAINER_VERSION)
            input.readFully(salt)
            val cipherLength = input.readInt()
            require(cipherLength in (GCM_TAG_BYTES + 1)..(MAX_MANIFEST_BYTES + GCM_TAG_BYTES))
            input.readFully(iv)
            encryptedManifest = ByteArray(cipherLength)
            input.readFully(encryptedManifest)
            val derivedKey = deriveBytes(password, salt)
            keyBytes = derivedKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(MANIFEST_AAD)
            val plain = cipher.doFinal(encryptedManifest)
            try {
                val root = JSONObject(String(plain, Charsets.UTF_8))
                val digest = sha256(plain)
                keyBytes = null
                return V2Manifest(root, derivedKey, digest, cipherLength)
            } finally { plain.fill(0) }
        } catch (error: Exception) {
            throw IOException("تعذر التحقق من النسخة الاحتياطية أو كلمة المرور.", error)
        } finally {
            runCatching { input.close() }
            salt.fill(0)
            iv.fill(0)
            encryptedManifest?.fill(0)
            keyBytes?.fill(0)
        }
    }

    private fun validateVersionTwoArchiveLength(file: File, root: JSONObject, manifestCipherLength: Int) {
        val documents = root.optJSONArray("documents") ?: JSONArray()
        var expected = (4 + 1 + 16 + 4 + 12 + manifestCipherLength).toLong()
        for (index in 0 until documents.length()) {
            val row = documents.getJSONObject(index)
            val size = row.getLong("sizeBytes")
            require(size in 1..MAX_DOCUMENT_BYTES) { "Invalid attachment size in backup." }
            require(row.getString("mimeType") in SUPPORTED_DOCUMENT_MIME_TYPES)
            val chunks = (size + STREAM_CHUNK_BYTES - 1) / STREAM_CHUNK_BYTES
            expected += size + chunks * (4L + 12L + GCM_TAG_BYTES)
            if (expected > MAX_BACKUP_BYTES) throw BackupSizeLimitException()
        }
        require(expected == file.length()) { "Backup is truncated or contains trailing bytes." }
    }

    private suspend fun restoreVersionTwoDocuments(
        preview: BackupPreview,
        documentRows: List<MedicalDocument>,
        createdFiles: MutableList<MedicalDocument>
    ) {
        val archive = File(checkNotNull(preview.stagedArchivePath)).canonicalFile
        val keyBytes = checkNotNull(preview.archiveKeyBytes)
        require(keyBytes.size == 32 && archive.name.startsWith("sehati-backup-preview-") &&
            archive.parentFile?.name == BACKUP_PREVIEW_DIRECTORY)
        DataInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC) && input.readUnsignedByte() == STREAMING_CONTAINER_VERSION)
            val salt = ByteArray(16)
            input.readFully(salt)
            val manifestLength = input.readInt()
            require(manifestLength in (GCM_TAG_BYTES + 1)..(MAX_MANIFEST_BYTES + GCM_TAG_BYTES))
            val manifestIv = ByteArray(12)
            input.readFully(manifestIv)
            val encryptedManifest = ByteArray(manifestLength)
            input.readFully(encryptedManifest)
            val manifestCipher = Cipher.getInstance("AES/GCM/NoPadding")
            manifestCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, manifestIv))
            manifestCipher.updateAAD(MANIFEST_AAD)
            val manifest = manifestCipher.doFinal(encryptedManifest)
            try {
                require(sha256(manifest) == preview.manifestDigest)
                val verifiedRoot = JSONObject(String(manifest, Charsets.UTF_8))
                require(verifiedRoot.toString() == preview.payload.toString())
                validateVersionTwoArchiveLength(archive, verifiedRoot, manifestLength)
            } finally {
                salt.fill(0)
                manifestIv.fill(0)
                encryptedManifest.fill(0)
                manifest.fill(0)
            }
            require(documentRows.size == preview.documentCount)
            documentRows.forEachIndexed { index, document ->
                currentCoroutineContext().ensureActive()
                val decrypted = ChunkedGcmInputStream(input, keyBytes, document, index)
                try {
                    val restored = documentStore.restore(document, decrypted, document.sizeBytes)
                    require(decrypted.isAuthenticated) { "Attachment authentication failed." }
                    createdFiles += restored
                } finally {
                    decrypted.close()
                }
            }
            require(input.read() == -1) { "Backup contains unexpected trailing data." }
        }
    }

    private fun documentMetadata(doc: MedicalDocument): JSONObject = JSONObject()
        .put("id", doc.id).put("memberId", doc.memberId).put("displayName", doc.displayName)
        .put("mimeType", doc.mimeType).put("sizeBytes", doc.sizeBytes)
        .put("relatedProcedureId", doc.relatedProcedureId ?: JSONObject.NULL).put("addedAt", doc.addedAt)
        .put("relatedAppointmentId", doc.relatedAppointmentId ?: JSONObject.NULL)
        .put("relatedLabResultId", doc.relatedLabResultId ?: JSONObject.NULL)
        .put("category", doc.category).put("documentDate", doc.documentDate)

    private fun documentAad(index: Int, document: MedicalDocument, chunkIndex: Int, plainLength: Int): ByteArray =
        "sehati:shtb:v2:document:$index:${document.id}:${document.memberId}:${document.mimeType}:${document.sizeBytes}:chunk:$chunkIndex:$plainLength"
            .toByteArray(Charsets.UTF_8)

    private class ChunkedGcmOutputStream(
        private val output: DataOutputStream,
        private val keyBytes: ByteArray,
        private val document: MedicalDocument,
        private val documentIndex: Int
    ) : OutputStream() {
        private val buffer = ByteArray(STREAM_CHUNK_BYTES)
        private var buffered = 0
        private var total = 0L
        private var chunkIndex = 0
        private var finished = false

        override fun write(value: Int) {
            check(!finished) { "Attachment stream is already finished." }
            buffer[buffered++] = value.toByte()
            total++
            if (buffered == buffer.size) flushChunk()
        }

        override fun write(source: ByteArray, offset: Int, length: Int) {
            check(!finished) { "Attachment stream is already finished." }
            var position = offset
            var remaining = length
            while (remaining > 0) {
                val count = minOf(remaining, buffer.size - buffered)
                System.arraycopy(source, position, buffer, buffered, count)
                buffered += count
                total += count
                position += count
                remaining -= count
                if (buffered == buffer.size) flushChunk()
            }
        }

        private fun flushChunk() {
            if (buffered == 0) return
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(documentAad(documentIndex, document, chunkIndex, buffered))
            val encrypted = cipher.doFinal(buffer, 0, buffered)
            try {
                output.writeInt(buffered)
                output.write(iv)
                output.write(encrypted)
            } finally {
                iv.fill(0)
                encrypted.fill(0)
                buffer.fill(0)
                buffered = 0
                chunkIndex++
            }
        }

        fun finish() {
            check(!finished)
            require(total == document.sizeBytes) { "Attachment size changed during backup." }
            flushChunk()
            finished = true
        }

        fun wipe() {
            buffer.fill(0)
            buffered = 0
            finished = true
        }
    }

    private class ChunkedGcmInputStream(
        private val input: DataInputStream,
        private val keyBytes: ByteArray,
        private val document: MedicalDocument,
        private val documentIndex: Int
    ) : InputStream() {
        private var plainRemaining = document.sizeBytes
        private var chunkIndex = 0
        private var current: AuthenticatedRecordInputStream? = null
        private var currentLimit: LimitedInputStream? = null
        var isAuthenticated: Boolean = false
            private set

        override fun read(): Int {
            val single = ByteArray(1)
            val count = read(single, 0, 1)
            return if (count < 0) -1 else single[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            while (true) {
                val active = current
                if (active != null) {
                    val count = active.read(buffer, offset, length)
                    if (count >= 0) {
                        plainRemaining -= count
                        return count
                    }
                    require(active.isAuthenticated && currentLimit?.remaining == 0L) {
                        "Attachment chunk authentication failed."
                    }
                    active.close()
                    current = null
                    currentLimit = null
                    chunkIndex++
                }
                if (plainRemaining == 0L) {
                    isAuthenticated = true
                    return -1
                }
                val expectedLength = minOf(STREAM_CHUNK_BYTES.toLong(), plainRemaining).toInt()
                val actualLength = input.readInt()
                require(actualLength == expectedLength) { "Invalid attachment chunk length." }
                val iv = ByteArray(12)
                input.readFully(iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                cipher.updateAAD(documentAad(documentIndex, document, chunkIndex, expectedLength))
                iv.fill(0)
                val limited = LimitedInputStream(input, expectedLength.toLong() + GCM_TAG_BYTES)
                currentLimit = limited
                current = AuthenticatedRecordInputStream(limited, cipher)
            }
        }

        override fun close() {
            current?.close()
            current = null
            currentLimit = null
        }
    }

    private class AuthenticatedRecordInputStream(
        private val source: LimitedInputStream,
        private val cipher: Cipher
    ) : InputStream() {
        private var encryptedRemaining = source.remaining
        private var pending = ByteArray(0)
        private var pendingOffset = 0
        private var finalized = false
        var isAuthenticated: Boolean = false
            private set

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            while (pendingOffset >= pending.size) {
                if (encryptedRemaining > 0) {
                    val encrypted = ByteArray(minOf(STREAM_BUFFER_SIZE.toLong(), encryptedRemaining).toInt())
                    val count = source.read(encrypted)
                    if (count < 0) throw EOFException("Truncated encrypted attachment chunk.")
                    if (count == 0) continue
                    encryptedRemaining -= count
                    val output = cipher.update(encrypted, 0, count)
                    encrypted.fill(0)
                    if (output != null && output.isNotEmpty()) {
                        pending = output
                        pendingOffset = 0
                    }
                } else if (!finalized) {
                    finalized = true
                    try {
                        pending = cipher.doFinal()
                        pendingOffset = 0
                        isAuthenticated = true
                    } catch (error: Exception) {
                        throw IOException("Encrypted attachment authentication failed.", error)
                    }
                    if (pending.isEmpty()) return -1
                } else {
                    return -1
                }
            }
            val count = minOf(length, pending.size - pendingOffset)
            System.arraycopy(pending, pendingOffset, buffer, offset, count)
            pendingOffset += count
            if (pendingOffset == pending.size) {
                pending.fill(0)
                pending = ByteArray(0)
                pendingOffset = 0
            }
            return count
        }

        override fun close() {
            pending.fill(0)
            pending = ByteArray(0)
            source.close()
        }
    }

    private fun deriveBytes(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class LimitedInputStream(input: InputStream, length: Long) : FilterInputStream(input) {
        var remaining: Long = length
            private set
        override fun read(): Int {
            if (remaining == 0L) return -1
            val value = super.read()
            if (value < 0) throw EOFException("Truncated encrypted attachment.")
            remaining--
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count < 0) throw EOFException("Truncated encrypted attachment.")
            if (count > 0) remaining -= count
            return count
        }
        override fun close() = Unit
    }

    private fun decrypt(input: InputStream, password: CharArray): JSONObject {
        val plain = readAuthenticatedBackup(input, password)
        return try { JSONObject(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    private fun readAuthenticatedBackup(input: InputStream, password: CharArray): ByteArray {
        val encrypted = BackupCiphertext()
        return try {
            input.use { it.copyTo(BackupLimitOutput(encrypted)) }
            encrypted.authenticate(password)
        } finally {
            encrypted.wipe()
        }
    }

    private inner class BackupCiphertext : ByteArrayOutputStream() {
        fun authenticate(password: CharArray): ByteArray {
            require(count > 45 && MAGIC.indices.all { buf[it] == MAGIC[it] } && buf[4].toInt() == 1) {
                "Invalid backup file."
            }
            val key = derive(password, buf.copyOfRange(5, 21))
            password.fill('\u0000')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, buf, 21, 12))
            return cipher.doFinal(buf, 33, count - 33)
        }

        fun wipe() {
            buf.fill(0)
            reset()
        }
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw EOFException("Unexpected end of backup file.")
            if (count == 0) continue
            offset += count
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 310_000, 256)
        val bytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
    }

    data class BackupPreview(
        val memberNames: List<String>,
        val allergyCount: Int,
        internal val payload: JSONObject,
        internal val stagedArchivePath: String? = null,
        internal val archiveKeyBytes: ByteArray? = null,
        internal val manifestDigest: String? = null
    ) {
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
    private class BackupLimitOutput(output: OutputStream) : FilterOutputStream(output) {
        private var size = 0L

        override fun write(value: Int) {
            reserve(1)
            out.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            reserve(length)
            out.write(buffer, offset, length)
        }

        private fun reserve(length: Int) {
            if (size + length > MAX_BACKUP_BYTES) throw BackupSizeLimitException()
            size += length
        }
    }

    companion object {
        const val MAX_BACKUP_BYTES = 64L * 1024 * 1024
        private const val MAX_DOCUMENT_BYTES = 25L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
        private const val GCM_TAG_BYTES = 16
        private const val STREAM_BUFFER_SIZE = 32 * 1024
        private const val STREAM_CHUNK_BYTES = 64 * 1024
        private const val PBKDF2_ITERATIONS = 310_000
        private const val LEGACY_CONTAINER_VERSION = 1
        private const val STREAMING_CONTAINER_VERSION = 2
        private const val BACKUP_PREVIEW_DIRECTORY = "sehati-backup-previews"
        private val MANIFEST_AAD = "sehati:shtb:v2:manifest".toByteArray(Charsets.UTF_8)
        private val SUPPORTED_DOCUMENT_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")
        private val MAGIC = byteArrayOf(0x53, 0x48, 0x54, 0x42)
        private val backupLock = Mutex()
    }
}

class BackupSizeLimitException : IOException("Backup exceeds the 64 MiB limit.")
