package com.kiduyuk.klausk.kiduyutv.util

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Data class representing APK information from GitHub releases.
 */
data class ApkInfo(
    val url: String,
    val fileName: String,
    val version: String?,
    val buildNumber: Int
)

object UpdateUtil {
    private const val TAG = "UpdateUtil"
    
    // GitHub API endpoints
    private const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/kiduyu-klaus/KiduyuTv_final/releases"
    
    // GitHub raw URL for version file (fallback)
    private const val VERSION_URL = "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/VERSION"
    
    // Device type constants
    private const val DEVICE_TYPE_TV = "tv"
    private const val DEVICE_TYPE_PHONE = "phone"
    
    // OkHttpClient for network requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the latest release version from GitHub Releases API using OkHttpClient.
     * Returns the tag_name from the latest release (e.g., "1.1.76" from "v1.1.76").
     */
    suspend fun fetchLatestGitHubReleaseVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KiduyuTV-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API request failed with code: ${response.code}")
                    return@withContext null
                }

                val jsonString = response.body?.string() ?: return@withContext null
                val releases = JSONArray(jsonString)

                // Find the latest non-prerelease version
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("prerelease", false)) continue
                    
                    val tagName = release.optString("tag_name", null)
                    if (!tagName.isNullOrBlank()) {
                        // Remove 'v' prefix if present
                        return@withContext tagName.removePrefix("v").trim()
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching GitHub release version", e)
            null
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Fetches remote version from the raw VERSION file.
     */
    suspend fun fetchRemoteVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_URL)
                .header("User-Agent", "KiduyuTV-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote version", e)
            null
        }
    }

    /**
     * Cleans a version string by removing platform suffixes and 'v' prefix.
     * Examples:
     * - "1.1.76-phone" -> "1.1.76"
     * - "1.1.76-tv" -> "1.1.76"
     * - "v1.1.76" -> "1.1.76"
     */
    fun cleanVersionString(version: String): String {
        return version
            .removePrefix("v")
            .split("-")[0]
            .trim()
    }

    /**
     * Checks if the remote version is newer than the local version.
     * Handles platform-specific suffixes and 'v' prefix.
     */
    fun isNewerVersion(remote: String, local: String = BuildConfig.VERSION_NAME): Boolean {
        return try {
            val cleanedRemote = cleanVersionString(remote)
            val cleanedLocal = cleanVersionString(local)

            // Helper to extract only numeric part from a version segment (e.g., "73-phone" -> 73)
            fun String.toNumericPart(): Int = this.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

            val remoteParts = cleanedRemote.split(".").map { it.toNumericPart() }
            val localParts = cleanedLocal.split(".").map { it.toNumericPart() }

            val maxLength = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLength) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (l > r) return false
            }
            false
        } catch (e: Exception) {
            // Fallback: strip everything but dots and digits for a basic comparison
            val rClean = cleanVersionString(remote).filter { it.isDigit() || it == '.' }
            val lClean = cleanVersionString(local).filter { it.isDigit() || it == '.' }
            rClean > lClean
        }
    }

    /**
     * Determines if the current device is a TV.
     * Uses UiModeManager as the primary (authoritative) check,
     * with system feature and model string checks as fallbacks.
     */
    fun isTvDevice(context: Context): Boolean {
        // Primary check: UiModeManager is the official Android API for this
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            Log.d(TAG, "TV detected via UiModeManager")
            return true
        }

        val packageManager = context.packageManager

        // Secondary: system feature flags
        if (packageManager.hasSystemFeature("amazon.hardware.fire_tv")) {
            Log.d(TAG, "TV detected via Fire TV feature")
            return true
        }
        if (packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("android.software.leanback_only") ||
            packageManager.hasSystemFeature("android.hardware.type.tv")
        ) {
            Log.d(TAG, "TV detected via leanback/tv feature")
            return true
        }

        // Tertiary: model string heuristics (least reliable, keep as last resort)
        val model = Build.MODEL
        val isTvBuild = model.contains("TV", ignoreCase = true) ||
                        model.contains("AFT", ignoreCase = true) ||
                        model.contains("Chromecast", ignoreCase = true)

        Log.d(TAG, "TV model heuristic for '$model': $isTvBuild")
        return isTvBuild
    }

    /**
     * Determines the appropriate device type string for APK matching.
     * Returns "tv" for TV devices, "phone" for mobile devices.
     */
    fun getDeviceType(context: Context): String {
        val isTv = isTvDevice(context)
        Log.i(TAG, "Detected device type: ${if (isTv) "TV" else "PHONE"}")
        return if (isTv) DEVICE_TYPE_TV else DEVICE_TYPE_PHONE
    }

    /**
     * Extracts the version string from an APK filename.
     * Example: "KiduyuTV-phone-release-1.1.76-phone-tv-build383.apk" -> "1.1.76"
     */
    private fun extractVersionFromApkName(fileName: String): String? {
        // Pattern: KiduyuTV-{device}-release-{version}-{device}-{type}-build{number}.apk
        // We need to extract the version part which comes after "release-"
        val releasePattern = Regex("release[-_]([0-9]+\\.[0-9]+\\.[0-9]+)")
        return releasePattern.find(fileName)?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts the build number from an APK filename.
     * Example: "KiduyuTV-phone-release-1.1.76-phone-tv-build383.apk" -> 383
     */
    private fun extractBuildNumber(fileName: String): Int {
        val buildPattern = Regex("build(\\d+)")
        return buildPattern.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    /**
     * Checks if an APK filename matches the current device type based on filename prefix.
     * TV APKs start with "KiduyuTV-tv-release"
     * Phone APKs start with "KiduyuTV-phone-release"
     * 
     * Note: Both APK names contain "-phone-tv-" in them, so we must check the prefix
     * instead of just searching for patterns within the filename.
     */
    private fun matchesDeviceType(fileName: String, deviceType: String): Boolean {
        val lowerName = fileName.lowercase()
        
        // Check the prefix to determine device type
        // TV APK: "kiduyutv-tv-release-..."
        // Phone APK: "kiduyutv-phone-release-..."
        return when (deviceType) {
            DEVICE_TYPE_TV -> lowerName.startsWith("kiduyutv-tv-release")
            DEVICE_TYPE_PHONE -> lowerName.startsWith("kiduyutv-phone-release")
            else -> true
        }
    }

    /**
     * Fetches the best APK info from GitHub releases based on device type.
     * For TV devices: Selects APKs starting with "KiduyuTV-tv-release", picks highest build number.
     * For Phone devices: Selects APKs starting with "KiduyuTV-phone-release", picks highest build number.
     * 
     * Returns ApkInfo containing URL, filename, version, and build number.
     * 
     * APK naming convention:
     * - TV: KiduyuTV-tv-release-X.X.X-phone-tv-buildYYY.apk
     * - Phone: KiduyuTV-phone-release-X.X.X-phone-tv-buildYYY.apk
     */
    suspend fun fetchBestApkInfo(context: Context): ApkInfo? = withContext(Dispatchers.IO) {
        try {
            val deviceType = getDeviceType(context)
            Log.i(TAG, "Fetching APK for device type: $deviceType")

            val request = Request.Builder()
                .url(GITHUB_RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KiduyuTV-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API request failed with code: ${response.code}")
                    return@withContext null
                }

                val jsonString = response.body?.string() ?: return@withContext null
                val releases = JSONArray(jsonString)

                var bestApkInfo: ApkInfo? = null
                var bestBuildNumber = -1

                // Iterate through releases to find the best APK
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    
                    // Skip prereleases
                    if (release.optBoolean("prerelease", false)) continue

                    val assets = release.getJSONArray("assets")
                    
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.optString("name", "")

                        // Skip non-APK files and debug builds
                        if (!name.lowercase().endsWith(".apk") ||
                            name.lowercase().contains("debug") ||
                            !name.lowercase().contains("release")) {
                            continue
                        }

                        // Check if APK matches the current device type by prefix
                        if (!matchesDeviceType(name, deviceType)) {
                            Log.d(TAG, "Skipping APK (device type mismatch): $name for device type: $deviceType")
                            continue
                        }

                        // Extract version and build number
                        val version = extractVersionFromApkName(name)
                        val buildNumber = extractBuildNumber(name)

                        Log.d(TAG, "Found matching APK: $name, version: $version, build: $buildNumber")

                        // Select the APK with the highest build number
                        if (buildNumber > bestBuildNumber) {
                            bestBuildNumber = buildNumber
                            val apkUrl = asset.optString("browser_download_url", null)
                            if (apkUrl != null) {
                                bestApkInfo = ApkInfo(
                                    url = apkUrl,
                                    fileName = name,
                                    version = version,
                                    buildNumber = buildNumber
                                )
                            }
                        }
                    }

                    // If we found a valid APK in this release, we're done
                    // (releases are ordered by date, so first match is likely the best)
                    if (bestApkInfo != null && bestBuildNumber > 0) {
                        Log.i(TAG, "Selected APK: ${bestApkInfo.fileName}, version=${bestApkInfo.version}, build=${bestApkInfo.buildNumber}")
                        break
                    }
                }

                bestApkInfo
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching best APK info", e)
            null
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Fetches the best APK URL from GitHub releases based on device type.
     */
    suspend fun fetchBestApkUrl(context: Context): String? {
        return fetchBestApkInfo(context)?.url
    }

    /**
     * Legacy method for backward compatibility.
     * Fetches the latest APK URL without device-specific filtering.
     */
    suspend fun fetchLatestApkUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KiduyuTV-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val jsonString = response.body?.string() ?: return@withContext null
                val releases = JSONArray(jsonString)

                var bestApkUrl: String? = null
                var maxBuildNumber = -1

                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("prerelease", false)) continue

                    val assets = release.getJSONArray("assets")
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.optString("name", "").lowercase()

                        if (name.endsWith(".apk") &&
                            name.contains("release") &&
                            !name.contains("debug")
                        ) {
                            val buildMatch = Regex("build(\\d+)").find(name)
                            val buildNumber = buildMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
                            
                            if (buildNumber > maxBuildNumber) {
                                maxBuildNumber = buildNumber
                                bestApkUrl = asset.optString("browser_download_url", null)
                            }
                        }
                    }
                    if (bestApkUrl != null) break
                }
                bestApkUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching APK URL", e)
            null
        }
    }

    /**
     * Downloads an APK file using OkHttpClient with progress reporting.
     * Shows download progress percentage to the user.
     * 
     * @param context Application context
     * @param apkInfo APK information including URL and filename
     * @param onProgress Callback with progress percentage and filename
     * @return Downloaded File or null if failed
     */
    suspend fun downloadApk(
        context: Context,
        apkInfo: ApkInfo,
        onProgress: (Int, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting download: ${apkInfo.fileName}")
            
            val request = Request.Builder()
                .url(apkInfo.url)
                .header("User-Agent", "KiduyuTV-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download request failed with code: ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()
                val outFile = File(context.getExternalFilesDir(null), "kiduyutv-update.apk")

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(pct.coerceIn(0, 100), apkInfo.fileName)
                                }
                            }
                        }
                    }
                }

                Log.i(TAG, "APK downloaded successfully: ${outFile.absolutePath}")
                outFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    /**
     * Legacy download method for backward compatibility.
     * Downloads an APK file using the URL string.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting download from URL: $apkUrl")
            
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "KiduyuTV-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download request failed with code: ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()
                val outFile = File(context.getExternalFilesDir(null), "kiduyutv-update.apk")

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(pct.coerceIn(0, 100))
                                }
                            }
                        }
                    }
                }

                Log.i(TAG, "APK downloaded successfully: ${outFile.absolutePath}")
                outFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    /**
     * Initiates the APK installation process using FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                        Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
            // Fallback to older method if FileProvider fails
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback install also failed", e2)
            }
        }
    }

    /**
     * Checks if the app has permission to install packages from unknown sources.
     * For Android 8.0+ (API 26), this permission must be granted at runtime.
     */
    fun checkPermissionAndInstall(context: Context, apkFile: File, onPermissionRequired: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                onPermissionRequired()
                return
            }
        }
        installApk(context, apkFile)
    }

    /**
     * Opens the system settings page where users can grant 
     * permission to install unknown apps.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            // For older Android versions, open app settings
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
