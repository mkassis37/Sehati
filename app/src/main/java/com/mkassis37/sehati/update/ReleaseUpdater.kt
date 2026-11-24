package com.mkassis37.sehati.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class SehatiRelease(val versionCode:Int,val versionName:String,val title:String,val notes:String,val apkUrl:String,val apkSha256:String)

class ReleaseUpdater(private val context:Context) {
    suspend fun checkForUpdate(installedCode:Int):SehatiRelease? = withContext(Dispatchers.IO) {
        val release=JSONObject(read("https://api.github.com/repos/mkassis37/Sehati/releases/latest"))
        if(release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val assets=release.optJSONArray("assets") ?: return@withContext null
        var metadataUrl:String?=null
        for(i in 0 until assets.length()) if(assets.getJSONObject(i).optString("name")=="update.json") metadataUrl=assets.getJSONObject(i).optString("browser_download_url")
        val meta=metadataUrl?.let { JSONObject(read(it)) } ?: return@withContext null
        val code=meta.optInt("versionCode",-1)
        val apk=meta.optString("apkUrl","")
        val hash=meta.optString("sha256","").lowercase()
        if(code<=installedCode || !apk.startsWith("https://github.com/") || !hash.matches(Regex("[a-f0-9]{64}"))) return@withContext null
        SehatiRelease(code,meta.optString("versionName",release.optString("tag_name")),release.optString("name","تحديث صحتي"),release.optString("body","يتوفر تحديث جديد."),apk,hash)
    }

    suspend fun downloadVerifyAndInstall(release:SehatiRelease):String = withContext(Dispatchers.IO) {
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return@withContext "اسمح بالتثبيت من هذا المصدر ثم أعد المحاولة."
        }
        val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request=DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("تحديث صحتي ${release.versionName}").setDescription("تنزيل والتحقق من التحديث")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"sehati-${release.versionCode}.apk")
        val id=dm.enqueue(request)
        var uri:Uri?=null
        while(uri==null) {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if(!c.moveToFirst()) throw IllegalStateException("تعذر متابعة التنزيل.")
                when(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> uri=dm.getUriForDownloadedFile(id)
                    DownloadManager.STATUS_FAILED -> throw IllegalStateException("فشل تنزيل التحديث.")
                }
            }
            if(uri==null) delay(700)
        }
        val actual=context.contentResolver.openInputStream(uri!!)?.use { input ->
            val md=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(8192)
            while(true) { val n=input.read(buffer); if(n<0) break; md.update(buffer,0,n) }
            md.digest().joinToString("") { "%02x".format(it) }
        } ?: throw IllegalStateException("تعذر قراءة ملف التحديث.")
        if(!MessageDigest.isEqual(actual.toByteArray(),release.apkSha256.toByteArray())) {
            dm.remove(id); throw SecurityException("فشل التحقق من سلامة APK.")
        }
        withContext(Dispatchers.Main) {
            ContextCompat.startActivity(context,Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri,"application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },null)
        }
        "تم التحقق من التحديث. أكمل التثبيت من شاشة أندرويد."
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
