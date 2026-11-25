package com.mkassis37.sehati.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.io.File
import java.security.MessageDigest

data class SehatiRelease(val versionCode:Int,val versionName:String,val title:String,val notes:String,val apkUrl:String,val apkSha256:String)

class ReleaseUpdater(private val context:Context) {
    suspend fun checkForUpdate(installedCode:Int):SehatiRelease? = withContext(Dispatchers.IO) {
        val release=JSONObject(read("https://api.github.com/repos/mkassis37/Sehati/releases/latest"))
        if(release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val assets=release.optJSONArray("assets") ?: return@withContext null
        var metadataUrl:String?=null
        for(i in 0 until assets.length()) if(assets.getJSONObject(i).optString("name")=="update.json") metadataUrl=assets.getJSONObject(i).optString("browser_download_url")
        val meta=metadataUrl?.takeIf(::isTrustedUpdateAsset)?.let { JSONObject(read(it)) } ?: return@withContext null
        val code=meta.optInt("versionCode",-1)
        val apk=meta.optString("apkUrl","")
        val hash=meta.optString("sha256","").lowercase()
        if(code<=installedCode || !isTrustedUpdateAsset(apk) || !hash.matches(Regex("[a-f0-9]{64}"))) return@withContext null
        SehatiRelease(code,meta.optString("versionName",release.optString("tag_name")),release.optString("name","تحديث صحتي"),release.optString("body","يتوفر تحديث جديد."),apk,hash)
    }

    suspend fun downloadVerifyAndInstall(release:SehatiRelease):String = withContext(Dispatchers.IO) {
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return@withContext "اسمح بالتثبيت من هذا المصدر ثم أعد المحاولة."
        }
        require(isTrustedUpdateAsset(release.apkUrl)) { "مصدر التحديث غير موثوق." }
        val dm = context.getSystemService(DownloadManager::class.java)
        val fileName = "sehati-${release.versionCode}-${java.util.UUID.randomUUID()}.apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("تحديث صحتي ${release.versionName}").setDescription("تنزيل والتحقق من التحديث")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = dm.enqueue(request)
        try {
            val uri = withTimeout(180_000L) {
                var completed: Uri? = null
                while (completed == null) {
                    dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        if (!cursor.moveToFirst()) error("تعذر متابعة التنزيل.")
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL ->
                                completed = checkNotNull(dm.getUriForDownloadedFile(id))
                            DownloadManager.STATUS_FAILED -> error("فشل تنزيل التحديث.")
                        }
                    }
                    if (completed == null) delay(700)
                }
                checkNotNull(completed)
            }
            val apk = File(checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), fileName)
            val actual = apk.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            check(MessageDigest.isEqual(actual.toByteArray(), release.apkSha256.toByteArray())) {
                "فشل التحقق من سلامة APK."
            }
            verifyPackage(apk, release.versionCode)
            withContext(Dispatchers.Main) {
                ContextCompat.startActivity(context, Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, null)
            }
            "تم التحقق من سلامة التحديث وتوقيعه. أكمل التثبيت من شاشة أندرويد."
        } catch (failure: Exception) {
            dm.remove(id)
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(apk: File, expectedVersion: Int) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val installed = manager.getPackageInfo(context.packageName, flags)
        val candidate = checkNotNull(manager.getPackageArchiveInfo(apk.path, flags)) { "ملف APK غير صالح." }
        val code = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        check(candidate.packageName == context.packageName && code == expectedVersion.toLong()) {
            "معرّف تطبيق التحديث أو رقمه لا يطابق الإصدار المتوقع."
        }
        fun signatures(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners.orEmpty().toSet()
        else info.signatures.orEmpty().toSet()
        val trusted = signatures(installed)
        check(trusted.isNotEmpty() && signatures(candidate) == trusted) {
            "توقيع APK لا يطابق توقيع التطبيق المثبت."
        }
    }

    private fun read(url:String):String {
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=15000; c.readTimeout=20000
        c.setRequestProperty("Accept","application/vnd.github+json")
        c.setRequestProperty("User-Agent","Sehati-Android-Updater")
        return try {
            if(c.responseCode !in 200..299) throw IllegalStateException("تعذر الاتصال بخدمة الإصدارات.")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { c.disconnect() }
    }
}

internal fun isTrustedUpdateAsset(value: String): Boolean = runCatching {
    val parsed = URI(value)
    parsed.scheme == "https" && parsed.host == "github.com" && parsed.userInfo == null &&
        (parsed.port == -1 || parsed.port == 443) &&
        parsed.normalize().path.startsWith("/mkassis37/Sehati/releases/download/")
}.getOrDefault(false)
