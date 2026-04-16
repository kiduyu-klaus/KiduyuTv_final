package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import androidx.compose.ui.text.AnnotatedString

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    /**
     * Load settings data including cache size and latest release title.
     */
    fun loadSettingsData(context: Context) {
        viewModelScope.launch {
            // Load default provider preference
            val settingsManager = SettingsManager(context)
            _uiState.update { it.copy(defaultProvider = settingsManager.getDefaultProvider()) }

            // Fetch cache size
            val size = withContext(Dispatchers.IO) {
                getFolderSize(context.cacheDir)
            }
            _uiState.update { it.copy(cacheSize = formatSize(size)) }

            // Fetch latest release title
            // Fetch release title
            val title = UpdateUtil.fetchLatestReleaseTitle()

            // Fetch formatted release notes
            val notes = UpdateUtil.fetchLatestReleaseAnnotated()

            _uiState.update {
                it.copy(
                    releaseTitle = title,
                    releaseNotes = notes
                )
            }
        }
    }

    /**
     * Persist and update the default stream provider preference.
     */
    fun setDefaultProvider(context: Context, provider: String) {
        SettingsManager(context).saveDefaultProvider(provider)
        
        // Sync preference to Firebase Realtime Database for mobile persistence
        // This ensures the setting is saved in the cloud even if local data is cleared
        com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.savePreference("default_provider", provider)
        
        _uiState.update { it.copy(defaultProvider = provider) }
    }

    /**
     * Refresh latest release title and notes used by "What's new?".
     */
    fun refreshWhatsNew() {
        viewModelScope.launch {
            val title = UpdateUtil.fetchLatestReleaseTitle()
            val notes = UpdateUtil.fetchLatestReleaseAnnotated()

            _uiState.update {
                it.copy(
                    releaseTitle = title,
                    releaseNotes = notes
                )
            }
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
     * Uses the same approach as SplashActivity for consistency.
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
                // Try GitHub API first (same as SplashActivity)
                var remoteVersion = UpdateUtil.fetchLatestGitHubReleaseVersion()
                if (remoteVersion == null) {
                    // Fallback to VERSION file
                    remoteVersion = UpdateUtil.fetchRemoteVersion()
                }

                val localVersionName = BuildConfig.VERSION_NAME
                Log.i("SettingsViewModel", "Remote version: $remoteVersion, Local version: $localVersionName")

                if (remoteVersion != null) {
                    val isNewer = UpdateUtil.isNewerVersion(remoteVersion, localVersionName)
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
     * Uses the same approach as SplashActivity for consistency.
     */
    fun downloadAndInstallUpdate(context: Context) {
        if (_uiState.value.isDownloadingUpdate) return

        _uiState.update { it.copy(isDownloadingUpdate = true, downloadProgress = 0) }

        viewModelScope.launch {
            try {
                // Get device-specific APK info (same as SplashActivity)
                val apkInfo = UpdateUtil.fetchBestApkInfo(context)
                if (apkInfo != null) {
                    // Check for existing cached APK before downloading (same as SplashActivity)
                    val localFile = UpdateUtil.getLocalApkFile(context)
                    when {
                        UpdateUtil.isLocalApkValid(context, apkInfo) -> {
                            // Cached file is valid - skip download
                            Log.i("SettingsViewModel", "Valid cached APK found, skipping download")
                            _uiState.update { it.copy(isDownloadingUpdate = false, downloadProgress = 0) }
                            UpdateUtil.checkPermissionAndInstall(context, localFile) {
                                showPermissionDialog(context, localFile)
                            }
                            return@launch
                        }
                        localFile.exists() -> {
                            // Delete stale cached APK
                            localFile.delete()
                            Log.i("SettingsViewModel", "Deleted stale cached APK")
                        }
                    }

                    // Download APK with progress
                    val apkFile = UpdateUtil.downloadApk(context, apkInfo) { progress, _ ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }

                    _uiState.update { it.copy(isDownloadingUpdate = false, downloadProgress = 0) }

                    if (apkFile != null) {
                        // Save APK metadata for future cache validation
                        UpdateUtil.saveDownloadedApkMeta(context, apkInfo)
                        
                        // Use basic install. Successful installation will trigger 
                        // UpdateReceiver (via ACTION_MY_PACKAGE_REPLACED) to restart the app.
                        UpdateUtil.checkPermissionAndInstall(context, apkFile) {
                            showPermissionDialog(context, apkFile)
                        }
                    } else {
                        _uiState.update { it.copy(updateCheckResult = "Download failed. Please try again.") }
                    }
                } else {
                    // Fallback: open releases page in browser
                    Log.w("SettingsViewModel", "No APK found for device, opening releases page")
                    _uiState.update { it.copy(isDownloadingUpdate = false) }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kiduyu-klaus/KiduyuTv_final/releases/latest"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error downloading update", e)
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateCheckResult = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun showPermissionDialog(context: Context, apkFile: File) {
        val dialog = QuitDialog(
            context = context,
            title = "Permission Required",
            message = "To install the update, Kiduyu TV needs permission to install unknown apps. Please enable it in the settings.",
            positiveButtonText = "Settings",
            negativeButtonText = "Cancel",
            lottieAnimRes = com.kiduyuk.klausk.kiduyutv.R.raw.exit,
            onYes = {
                UpdateUtil.openInstallPermissionSettings(context)
            },
            onNo = { }
        )
        dialog.show()
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
    // Default provider preference
    val defaultProvider: String = com.kiduyuk.klausk.kiduyutv.util.SettingsManager.AUTO,
    // Update check states
    val isCheckingForUpdates: Boolean = false,
    val updateCheckResult: String? = null,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Int = 0,
    val releaseTitle: String? = null,
    val releaseNotes: AnnotatedString? = null
)
