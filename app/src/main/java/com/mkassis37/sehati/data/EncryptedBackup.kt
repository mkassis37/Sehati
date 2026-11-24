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
    suspend fun write(resolver: ContentResolver, uri: Uri, password: CharArray) {
        require(password.size >= 10) { "Backup password must be at least 10 characters." }
        val dao = database.medicalDao()
        val members = dao.getAllMembers()
        val allergies = dao.getAllAllergies()
        val labResults = dao.getAllLabResults()
        val documents = dao.getAllDocuments()
        val payload = JSONObject()
            .put("format", "sehati-backup")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("members", JSONArray().apply {
                members.forEach { m -> put(JSONObject()
                    .put("id", m.id).put("fullName", m.fullName).put("dateOfBirth", m.dateOfBirth)
                    .put("bloodGroup", m.bloodGroup).put("medicalNotes", m.medicalNotes).put("updatedAt", m.updatedAt)) }
            })
            .put("allergies", JSONArray().apply {
                allergies.forEach { a -> put(JSONObject()
                    .put("id", a.id).put("memberId", a.memberId).put("category", a.category)
                    .put("substance", a.substance).put("reaction", a.reaction).put("severity", a.severity)
                    .put("notes", a.notes).put("createdAt", a.createdAt)) }
            })
            .put("labResults", JSONArray().apply {
                labResults.forEach { r -> put(JSONObject()
                    .put("id", r.id).put("memberId", r.memberId).put("testName", r.testName)
                    .put("value", r.value).put("unit", r.unit)
                    .put("referenceLow", r.referenceLow ?: JSONObject.NULL)
                    .put("referenceHigh", r.referenceHigh ?: JSONObject.NULL)
                    .put("performedAt", r.performedAt).put("laboratory", r.laboratory)
                    .put("source", r.source).put("notes", r.notes).put("createdAt", r.createdAt)) }
            })
            .put("documents", JSONArray().apply {
                documents.forEach { doc ->
                    val plain = documentStore.readPlaintextBytes(doc)
                    try {
                        put(JSONObject().put("id", doc.id).put("memberId", doc.memberId)
                            .put("displayName", doc.displayName).put("mimeType", doc.mimeType)
                            .put("sizeBytes", doc.sizeBytes).put("addedAt", doc.addedAt)
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
        payload.fill(0); key.encoded.fill(0)
    }

    suspend fun preview(resolver: ContentResolver, uri: Uri, password: CharArray): BackupPreview {
        val root = decrypt(resolver.openInputStream(uri)!!, password)
        require(root.optString("format") == "sehati-backup" && root.optInt("version") == 1) {
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
                medicalNotes = o.optString("medicalNotes"), updatedAt = o.optLong("updatedAt"))
        }
        val allergyRows = (0 until allergies.length()).map { i ->
            val o = allergies.getJSONObject(i)
            AllergyRecord(id = o.getLong("id"), memberId = o.getLong("memberId"),
                category = o.getString("category"), substance = o.getString("substance"),
                reaction = o.optString("reaction"), severity = o.optString("severity"),
                notes = o.optString("notes"), createdAt = o.optLong("createdAt"))
        }
        val labRows = (0 until labResults.length()).map { i ->
            val o = labResults.getJSONObject(i)
            LabResult(id = o.getLong("id"), memberId = o.getLong("memberId"),
                testName = o.getString("testName"), value = o.getDouble("value"),
                unit = o.optString("unit"),
                referenceLow = if (o.isNull("referenceLow")) null else o.getDouble("referenceLow"),
                referenceHigh = if (o.isNull("referenceHigh")) null else o.getDouble("referenceHigh"),
                performedAt = o.getString("performedAt"), laboratory = o.optString("laboratory"),
                source = o.optString("source", "manual"), notes = o.optString("notes"),
                createdAt = o.optLong("createdAt"))
        }
        val documentArray = root.optJSONArray("documents") ?: JSONArray()
        val documentRows = (0 until documentArray.length()).map { i ->
            val o = documentArray.getJSONObject(i)
            val memberId = o.getLong("memberId")
            val id = o.optLong("id")
            val relativePath = "$memberId/restore-$id-${System.nanoTime()}.shtd"
            MedicalDocument(id = id, memberId = memberId,
                displayName = o.getString("displayName"), relativePath = relativePath,
                mimeType = o.optString("mimeType", "application/pdf"),
                sizeBytes = o.optLong("sizeBytes"), addedAt = o.optLong("addedAt"))
        }
        require(allergyRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            labRows.all { row -> memberRows.any { it.id == row.memberId } } &&
            documentRows.all { row -> memberRows.any { it.id == row.memberId } }) {
            "Backup contains records without a matching family member."
        }
        val createdFiles = mutableListOf<MedicalDocument>()
        try {
            documentRows.forEachIndexed { index, row ->
                val encoded = documentArray.getJSONObject(index).getString("content")
                val plain = Base64.decode(encoded, Base64.DEFAULT)
                try { createdFiles += documentStore.restore(row, plain) }
                finally { plain.fill(0) }
            }
            val dao = database.medicalDao()
            val oldDocuments = dao.getAllDocuments()
            database.withTransaction {
                dao.clearAllergies()
                dao.clearLabResults()
                dao.clearDocuments()
                dao.clearMembers()
                dao.insertMembers(memberRows)
                dao.insertAllergies(allergyRows)
                dao.insertLabResults(labRows)
                dao.insertDocuments(createdFiles)
            }
            oldDocuments.forEach(documentStore::delete)
        } catch (error: Exception) {
            createdFiles.forEach(documentStore::delete)
            throw error
        }
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
        return SecretKeySpec(bytes, "AES")
    }

    data class BackupPreview(val memberNames: List<String>, val allergyCount: Int, internal val payload: JSONObject) {
        val labResultCount: Int get() = payload.optJSONArray("labResults")?.length() ?: 0
        val documentCount: Int get() = payload.optJSONArray("documents")?.length() ?: 0
    }
    companion object { private val MAGIC = byteArrayOf(0x53, 0x48, 0x54, 0x42) }
}
