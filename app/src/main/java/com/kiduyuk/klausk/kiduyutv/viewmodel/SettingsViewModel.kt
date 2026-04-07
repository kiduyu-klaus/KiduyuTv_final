package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    /**
     * Load the current cache size.
     */
    fun loadCacheSize(context: Context) {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                getFolderSize(context.cacheDir)
            }
            _uiState.value = _uiState.value.copy(cacheSize = formatSize(size))
        }
    }

    /**
     * Clear all application cache including database and internal files.
     */
    fun clearCache(context: Context) {
        if (_uiState.value.isClearingCache) return

        _uiState.value = _uiState.value.copy(isClearingCache = true, cacheClearSuccess = false)

        viewModelScope.launch {
            // 1. Clear Database Cache
            DatabaseManager.clearAllCache()

            // 2. Clear File Cache (Coil, OkHttp, etc.)
            withContext(Dispatchers.IO) {
                try {
                    deleteDir(context.cacheDir)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Artificial delay for better UX feedback
            delay(1000)

            _uiState.value = _uiState.value.copy(
                isClearingCache = false,
                cacheClearSuccess = true,
                cacheSize = "0 B"
            )

            // Show restart dialog to recommend app restart
            val restartDialog = QuitDialog(
                context = context,
                title = "Cache Cleared",
                message = "Cache has been cleared successfully. It is recommended to restart the app for the changes to take full effect.",
                positiveButtonText = "Restart",
                negativeButtonText = "Later",
                lottieAnimRes = com.kiduyuk.klausk.kiduyutv.R.raw.exit,
                onYes = {
                    // Restart the app
                    val packageManager = context.packageManager
                    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        // Kill the current activity
                        if (context is android.app.Activity) {
                            context.finishAffinity()
                        }
                    }
                },
                onNo = {
                    // Reset success message after user dismisses dialog
                    viewModelScope.launch {
                        delay(500)
                        _uiState.value = _uiState.value.copy(cacheClearSuccess = false)
                    }
                }
            )
            restartDialog.show()
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.exists()) {
            val files = file.listFiles()
            if (files != null) {
                for (f in files) {
                    size += if (f.isDirectory) {
                        getFolderSize(f)
                    } else {
                        f.length()
                    }
                }
            }
        }
        return size
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun deleteDir(dir: File?): Boolean {
        return if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
            dir.delete()
        } else if (dir != null && dir.isFile) {
            dir.delete()
        } else {
            false
        }
    }

    fun clearMyList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingMyList = true, myListClearSuccess = false) }
            DatabaseManager.clearMyList()
            delay(600)
            _uiState.update { it.copy(isClearingMyList = false, myListClearSuccess = true) }
            delay(3000)
            _uiState.update { it.copy(myListClearSuccess = false) }
        }
    }

    fun clearCompanies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCompanies = true, companiesClearSuccess = false) }
            MyListManager.clearByType("company")
            delay(600)
            _uiState.update { it.copy(isClearingCompanies = false, companiesClearSuccess = true) }
            delay(3000)
            _uiState.update { it.copy(companiesClearSuccess = false) }
        }
    }

    fun clearNetworks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingNetworks = true, networksClearSuccess = false) }
            MyListManager.clearByType("network")
            delay(600)
            _uiState.update { it.copy(isClearingNetworks = false, networksClearSuccess = true) }
            delay(3000)
            _uiState.update { it.copy(networksClearSuccess = false) }
        }
    }

    fun clearCasts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCasts = true, castsClearSuccess = false) }
            MyListManager.clearByType("cast")
            delay(600)
            _uiState.update { it.copy(isClearingCasts = false, castsClearSuccess = true) }
            delay(3000)
            _uiState.update { it.copy(castsClearSuccess = false) }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingWatchHistory = true, watchHistoryClearSuccess = false) }
            DatabaseManager.clearWatchHistory()
            delay(600)
            _uiState.update { it.copy(isClearingWatchHistory = false, watchHistoryClearSuccess = true) }
            delay(3000)
            _uiState.update { it.copy(watchHistoryClearSuccess = false) }
        }
    }

    // ── Update Check Functions ──────────────────────────────────────────────────

    /**
     * Check for app updates.
     */
    fun checkForUpdates(context: Context) {
        if (_uiState.value.isCheckingForUpdates) return

        _uiState.update {
            it.copy(
                isCheckingForUpdates = true,
                updateCheckResult = null,
                updateAvailable = false
            )
        }

        viewModelScope.launch {
            try {
                val remoteVersion = fetchRemoteVersion()
                val localVersionName = BuildConfig.VERSION_NAME
                Log.i("SettingsViewModel", "Remote version: $remoteVersion, Local version: $localVersionName")

                if (remoteVersion != null) {
                    val isNewer = isNewerVersion(remoteVersion, localVersionName)
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            updateCheckResult = if (isNewer) {
                                "Update available: v$remoteVersion (current: v$localVersionName)"
                            } else {
                                "You're on the latest version (v$localVersionName)"
                            },
                            updateAvailable = isNewer,
                            latestVersion = remoteVersion
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            updateCheckResult = "Unable to check for updates. Please try again later."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error checking for updates", e)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false,
                        updateCheckResult = "Error checking for updates: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Download and install the latest update.
     */
    fun downloadAndInstallUpdate(context: Context) {
        if (_uiState.value.isDownloadingUpdate) return

        _uiState.update { it.copy(isDownloadingUpdate = true) }

        viewModelScope.launch {
            val apkUrl = fetchLatestApkUrl()
            if (apkUrl != null) {
                downloadApk(context, apkUrl)
            } else {
                // Fallback: open releases page in browser
                _uiState.update { it.copy(isDownloadingUpdate = false) }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kiduyu-klaus/KiduyuTv_final/releases/latest"))
                context.startActivity(intent)
            }
        }
    }

    private suspend fun fetchRemoteVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/VERSION"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText().trim() }
            } else null
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error fetching remote version", e)
            null
        }
    }

    private suspend fun fetchLatestApkUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/kiduyu-klaus/KiduyuTv_final/releases")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = org.json.JSONArray(json)

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
            Log.e("SettingsViewModel", "Error fetching APK URL", e)
            null
        }
    }

    private suspend fun downloadApk(context: Context, apkUrl: String) = withContext(Dispatchers.IO) {
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
                                _uiState.update { it.copy(downloadProgress = pct) }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isDownloadingUpdate = false, downloadProgress = 0) }
                installApk(context, outFile)
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "APK download failed", e)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        downloadProgress = 0,
                        updateCheckResult = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                showPermissionDialog(context)
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(intent)
    }

    private fun showPermissionDialog(context: Context) {
        val dialog = QuitDialog(
            context = context,
            title = "Permission Required",
            message = "To install the update, Kiduyu TV needs permission to install unknown apps. Please enable it in the settings.",
            positiveButtonText = "Settings",
            negativeButtonText = "Cancel",
            lottieAnimRes = com.kiduyuk.klausk.kiduyutv.R.raw.exit,
            onYes = {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
            onNo = { }
        )
        dialog.show()
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
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

    /**
     * Reset update check result to allow re-checking.
     */
    fun resetUpdateCheckResult() {
        _uiState.update { it.copy(updateCheckResult = null) }
    }
}

data class SettingsUiState(
    val isClearingCache: Boolean = false,
    val cacheClearSuccess: Boolean = false,
    val cacheSize: String = "Calculating...",
    val isClearingMyList: Boolean = false,
    val myListClearSuccess: Boolean = false,
    val isClearingCompanies: Boolean = false,
    val companiesClearSuccess: Boolean = false,
    val isClearingNetworks: Boolean = false,
    val networksClearSuccess: Boolean = false,
    val isClearingCasts: Boolean = false,
    val castsClearSuccess: Boolean = false,
    val isClearingWatchHistory: Boolean = false,
    val watchHistoryClearSuccess: Boolean = false,
    // Update check states
    val isCheckingForUpdates: Boolean = false,
    val updateCheckResult: String? = null,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Int = 0
)
