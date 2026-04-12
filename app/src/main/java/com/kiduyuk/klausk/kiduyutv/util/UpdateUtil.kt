package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateUtil {
    private const val TAG = "UpdateUtil"
    private const val VERSION_URL = "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/VERSION"
    private const val RELEASES_API_URL = "https://api.github.com/repos/kiduyu-klaus/KiduyuTv_final/releases"

    suspend fun fetchRemoteVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText().trim() }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote version", e)
            null
        }
    }

    fun isNewerVersion(remote: String, local: String = BuildConfig.VERSION_NAME): Boolean {
        return try {
            val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
            val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

            if (remoteParts.isEmpty() || localParts.isEmpty()) return remote > local

            val maxLength = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLength) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            false
        } catch (e: Exception) {
            remote > local
        }
    }

    suspend fun fetchLatestApkUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(json)

            var bestApkUrl: String? = null
            var maxBuildNumber = -1

            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (!release.optBoolean("prerelease", false)) continue

                val assets = release.getJSONArray("assets")
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name", "")

                    if (name.contains("release", ignoreCase = true) &&
                        !name.contains("debug", ignoreCase = true)
                    ) {
                        val buildMatch = Regex("build(\\d+)").find(name)
                        if (buildMatch != null) {
                            val buildNumber = buildMatch.groupValues[1].toInt()
                            if (buildNumber > maxBuildNumber) {
                                maxBuildNumber = buildNumber
                                bestApkUrl = asset.optString("browser_download_url", null)
                            }
                        } else if (bestApkUrl == null) {
                            bestApkUrl = asset.optString("browser_download_url", null)
                        }
                    }
                }
                if (bestApkUrl != null) break
            }
            bestApkUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching APK URL", e)
            null
        }
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.connect()

            val totalBytes = connection.contentLengthLong
            val outFile = File(context.getExternalFilesDir(null), "kiduyutv-update.apk")

            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8_192)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = (downloaded * 100 / totalBytes).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(intent)
    }

    fun checkPermissionAndInstall(context: Context, apkFile: File, onPermissionRequired: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                onPermissionRequired()
                return
            }
        }
        installApk(context, apkFile)
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
