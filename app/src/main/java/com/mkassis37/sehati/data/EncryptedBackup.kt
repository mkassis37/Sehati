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
class EncryptedBackup(private val database: SehatiDatabase) {
    suspend fun write(resolver: ContentResolver, uri: Uri, password: CharArray) {
        require(password.size >= 10) { "Backup password must be at least 10 characters." }
        val dao = database.medicalDao()
        val members = dao.getAllMembers()
        val allergies = dao.getAllAllergies()
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
        require(allergyRows.all { row -> memberRows.any { it.id == row.memberId } }) {
            "Backup contains allergy records without a matching family member."
        }
        database.withTransaction {
            val dao = database.medicalDao()
            dao.clearAllergies()
            dao.clearMembers()
            dao.insertMembers(memberRows)
            dao.insertAllergies(allergyRows)
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

    data class BackupPreview(val memberNames: List<String>, val allergyCount: Int, internal val payload: JSONObject)
    companion object { private val MAGIC = byteArrayOf(0x53, 0x48, 0x54, 0x42) }
}
