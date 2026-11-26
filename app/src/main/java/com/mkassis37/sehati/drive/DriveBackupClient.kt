package com.mkassis37.sehati.drive

import android.accounts.Account
import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.mkassis37.sehati.data.EncryptedBackup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject

internal data class DriveFile(
    val id: String,
    val name: String,
    val created: String,
    val size: Long,
    val sha256: String,
    val uploadedSha256: String
) {
    val hasVerifiedUpload: Boolean
        get() = size in 1..MAX_DRIVE_BYTES && sha256.isNotBlank() &&
            sha256.equals(uploadedSha256, ignoreCase = true)
}

internal class DriveNeedsConsent : Exception()
internal class DriveRequestFailed(val status: Int) : IOException("Google Drive request failed")

internal class DriveBackupClient(context: Context, private val backup: EncryptedBackup) {
    private val app = context.applicationContext

    suspend fun grant(email: String?): String {
        val result = Identity.getAuthorizationClient(app).authorize(driveAuthorization(email)).awaitDrive()
        if (result.hasResolution()) throw DriveNeedsConsent()
        return checkNotNull(result.accessToken)
    }

    suspend fun emailFor(token: String): String = withContext(Dispatchers.IO) {
        request("https://www.googleapis.com/oauth2/v2/userinfo", token) { connection ->
            connection.requireSuccess()
            val email = JSONObject(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)).getString("email")
            check(email.isNotBlank())
            email
        }
    }

    suspend fun list(token: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val query = Uri.parse(DRIVE_FILES).buildUpon()
            .appendQueryParameter("spaces", "appDataFolder")
            .appendQueryParameter("q", "trashed = false and name contains 'sehati-backup-'")
            .appendQueryParameter("orderBy", "createdTime desc")
            .appendQueryParameter("pageSize", "100")
            .appendQueryParameter("fields", "files(id,name,createdTime,size,sha256Checksum,appProperties)")
            .build().toString()
        request(query, token) { connection ->
            connection.requireSuccess()
            val body = JSONObject(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
            val items = body.optJSONArray("files") ?: JSONArray()
            (0 until items.length()).map { index ->
                val file = items.getJSONObject(index)
                DriveFile(file.getString("id"), file.getString("name"), file.optString("createdTime"),
                    file.optLong("size"), file.optString("sha256Checksum"),
                    file.optJSONObject("appProperties")?.optString(UPLOAD_SHA256).orEmpty())
            }
        }
    }

    suspend fun upload(token: String, password: CharArray, name: String): DriveFile = withContext(Dispatchers.IO) {
        val encrypted = File.createTempFile("sehati-drive-", ".shtb", app.cacheDir)
        try {
            backup.write(app.contentResolver, Uri.fromFile(encrypted), password)
            val size = encrypted.length()
            require(size in 1..MAX_DRIVE_BYTES)
            val sha256 = encrypted.sha256()
            val boundary = UUID.randomUUID().toString()
            val metadata = JSONObject().put("name", name).put("parents", JSONArray().put("appDataFolder"))
                .put("appProperties", JSONObject().put(UPLOAD_SHA256, sha256))
            val header = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray(Charsets.UTF_8)
            val end = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val uploadedId = request("$DRIVE_UPLOAD?uploadType=multipart&fields=id,size,sha256Checksum", token) { connection ->
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                connection.setFixedLengthStreamingMode(header.size + size + end.size)
                connection.outputStream.use { out ->
                    out.write(header)
                    encrypted.inputStream().use { it.copyTo(out) }
                    out.write(end)
                }
                connection.requireSuccess()
                val stored = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                verifyDriveBackup(stored.optLong("size", -1), stored.optString("sha256Checksum"), size, sha256)
                stored.getString("id")
            }
            // Verify that the encrypted object is actually listed in the app's Drive folder.
            val verified = list(token).firstOrNull { it.id == uploadedId }
                ?: throw IOException("Uploaded backup was not found in appDataFolder.")
            if (verified.size != size || !verified.uploadedSha256.equals(sha256, ignoreCase = true)) {
                throw IOException("Uploaded backup metadata does not match the encrypted source.")
            }
            verified
        } finally {
            password.fill('\u0000')
            encrypted.delete()
        }
    }

    suspend fun preview(token: String, cloud: DriveFile, password: CharArray): EncryptedBackup.BackupPreview =
        withContext(Dispatchers.IO) {
            val encrypted = File.createTempFile("sehati-drive-", ".shtb", app.cacheDir)
            try {
                require(cloud.size <= MAX_DRIVE_BYTES)
                val url = Uri.parse(DRIVE_FILES).buildUpon().appendPath(cloud.id)
                    .appendQueryParameter("alt", "media").build().toString()
                request(url, token) { connection ->
                    connection.requireSuccess()
                    connection.inputStream.use { input ->
                        encrypted.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var size = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                size += read
                                require(size <= MAX_DRIVE_BYTES)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                if (cloud.size > 0 && encrypted.length() != cloud.size) {
                    throw IOException("Drive backup download size differs from metadata")
                }
                if (cloud.sha256.isNotBlank()) {
                    verifyDriveBackup(cloud.size, cloud.sha256, encrypted.length(), encrypted.sha256())
                }
                backup.preview(app.contentResolver, Uri.fromFile(encrypted), password)
            } finally {
                password.fill('\u0000')
                encrypted.delete()
            }
        }

    private inline fun <T> request(address: String, token: String, block: (HttpURLConnection) -> T): T {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            return block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.requireSuccess() {
        if (responseCode !in 200..299) throw DriveRequestFailed(responseCode)
    }
}

internal fun driveAuthorization(email: String?): AuthorizationRequest =
    AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("https://www.googleapis.com/auth/userinfo.email")))
        .setOptOutIncludingGrantedScopes(true)
        .apply { if (email != null) setAccount(Account(email, "com.google")) }
        .build()

internal suspend fun <T> Task<T>.awaitDrive(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

internal fun driveBackupName(): String = "sehati-backup-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    .replace(':', '-') + ".shtb"

private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
private const val MAX_DRIVE_BYTES = 64L * 1024 * 1024
private const val UPLOAD_SHA256 = "sehatiSha256"

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
