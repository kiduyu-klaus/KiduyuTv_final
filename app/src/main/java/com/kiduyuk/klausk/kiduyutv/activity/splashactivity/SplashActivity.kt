package com.kiduyuk.klausk.kiduyutv.activity.splashactivity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.compose.*
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.util.ApkInfo
import com.kiduyuk.klausk.kiduyutv.util.AuthManager
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.FirebaseSyncManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.UpdateUtil
import com.kiduyuk.klausk.kiduyutv.util.ConsentManager
import com.kiduyuk.klausk.kiduyutv.util.AdManager
import io.github.cutelibs.cutedialog.CuteDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import androidx.core.net.toUri

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DURATION_MS = 6000
        private const val SYNC_TIMEOUT_MS = 10000L // 10 seconds max for sync
        private const val VERSION_CHECK_TIMEOUT_MS = 5000L // 5 seconds max for version check
    }

    // Compose-observable flags
    private var updateAvailable by mutableStateOf(false)
    private var permissionHandled by mutableStateOf(false)
    private var syncCompleted by mutableStateOf(false)
    private var versionCheckHandled by mutableStateOf(false)
    private var adsConsentHandled by mutableStateOf(false)

    // Remote version shown in the status chip
    private var currentRemoteVersion by mutableStateOf<String?>(null)

    // Firebase sync state
    private var syncProgress by mutableStateOf(0)
    private var syncMessage by mutableStateOf("")
    private var mainNavigationStarted = false

    // Tracks every dialog shown so onDestroy can safely dismiss them all
    private val activeDialogs = mutableListOf<Dialog>()

    // APK file reference for installation result handling
    private var pendingApkFile: File? = null

    // Version code of the APK being installed (used to detect successful update)
    private var pendingVersionCode: Int = -1

    // Activity result launcher for APK installation
    private val installLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "Installation result received: ${result.resultCode}")
        // Clear pending file first to avoid processing twice
        pendingApkFile?.let {
            pendingApkFile = null
            pendingVersionCode = -1

            // On modern Android, self-updating kills the process.
            // If we are still here, the update either failed, was cancelled,
            // or we are running on a version that doesn't kill the app immediately.
            // UpdateReceiver.kt handles the successful restart via ACTION_MY_PACKAGE_REPLACED.
            Log.i(TAG, "Return from installation screen. If update was successful, app will restart via UpdateReceiver.")

            // Optional: You can check if version actually changed here for non-killing updates,
            // but for most cases, finishing is safer to allow the receiver to take over.
            finish()
        }
    }

    /**
     * Gets the version code of the currently installed package.
     */
    private fun getInstalledVersionCode(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Restarts the application by launching MainActivity and finishing SplashActivity.
     */
    private fun restartApp() {
        Log.i(TAG, "Restarting application...")
        // Clean up downloaded APK files before restarting
        cleanupDownloadedApk()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Deletes the downloaded APK file to free up storage space.
     */
    private fun cleanupDownloadedApk() {
        try {
            val apkFile = UpdateUtil.getLocalApkFile(this)
            if (apkFile.exists()) {
                val deleted = apkFile.delete()
                Log.i(TAG, "Downloaded APK cleanup: ${if (deleted) "success" else "failed"} - ${apkFile.absolutePath}")
            }
            // Also clear the cached metadata
            clearApkCacheMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up APK files", e)
        }
    }

    /**
     * Clears the APK cache metadata from SharedPreferences.
     */
    private fun clearApkCacheMetadata() {
        try {
            val prefs = getSharedPreferences("apk_cache_meta", MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.i(TAG, "APK cache metadata cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing APK cache metadata", e)
        }
    }

    private fun Dialog.showTracked() {
        activeDialogs.add(this)
        show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDialogs.forEach { if (it.isShowing) it.dismiss() }
        activeDialogs.clear()
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.i(TAG, "Notification permission ${if (isGranted) "granted" else "denied"}")
        permissionHandled = true
    }

    private fun checkNotificationPermission() {
        when {
            UpdateUtil.isTvDevice(this) -> {
                Log.i(TAG, "TV detected — skipping notification permission request")
                permissionHandled = true
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    permissionHandled = true
                } else {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> permissionHandled = true
        }
    }

    // ── Device detection ──────────────────────────────────────────────────────

    private fun getDeviceTypeString() = if (UpdateUtil.isTvDevice(this)) "TV" else "Phone/Tablet"

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetHomeDialogShownFlag()

        // ── First: Check if device version matches Firebase app_info ─────────────────
        checkDeviceVersionFromFirebase()

        // Set up the Compose UI first so Compose owns android.R.id.content before
        // any third-party SDK (UMP/ConsentManager, AdManager, Firebase) has a chance
        // to touch the window hierarchy.  Those SDKs can call through to the window
        // manager synchronously on some API levels, which leaves the content ViewGroup
        // in an unexpected state and causes ComponentActivityKt.setContent to NPE on
        // getChildAt(0).  All subsequent init is async / coroutine-based so moving it
        // after setContent has no behavioural effect.
        setContent {
            KiduyuTvTheme {
                SplashScreen(
                    updateAvailable = updateAvailable,
                    permissionHandled = permissionHandled,
                    remoteVersion = currentRemoteVersion,
                    syncCompleted = syncCompleted,
                    adsConsentHandled = adsConsentHandled,
                    syncProgress = syncProgress,
                    syncMessage = syncMessage,
                    onTimeout = {
                        // Navigate to MainActivity regardless of sync status
                        navigateToMain()
                    }
                )
            }
        }

        // Initialize FirebaseSyncManager and start data sync
        FirebaseSyncManager.init(this)
        startFirebaseSync()

        // Request GDPR consent before initializing ads
        // AdMob is initialized only after consent is resolved.
        // Other ad SDKs are currently paused.
        ConsentManager.requestConsent(this) {
            // AdManager.init schedules MobileAds.initialize and returns
            // immediately — the actual SDK-ready callback fires later.
            // We must wait for that callback before allowing the splash
            // to navigate, otherwise the bottom-nav banner is composed
            // before the SDK is ready and the request is dropped (or
            // queued for retry, see AdManager).
            AdManager.initAndAwait(this@SplashActivity) {

                // ── ADD TEST DEVICE CONFIGURATION HERE ───────────────────────────────────
                val testDeviceIds = listOf("D766D45CB08288501275F03EF6344980")
                val configuration = com.google.android.gms.ads.RequestConfiguration.Builder()
                    .setTestDeviceIds(testDeviceIds)
                    .build()
                com.google.android.gms.ads.MobileAds.setRequestConfiguration(configuration)

                // 2. Build a dummy request to verify the configuration
                val dummyRequest = com.google.android.gms.ads.AdRequest.Builder().build()
                val isTest = dummyRequest.isTestDevice(this@SplashActivity)
                android.util.Log.d("AdmobTest", "Is this device configured as a test device? $isTest")
                android.util.Log.i("AdmobTest", "Is this device configured as a test device? $isTest")
// ─────────────────────────────────────────────────────────────────────────
                adsConsentHandled = true
            }
        }

        checkForUpdates()
        checkNotificationPermission()
    }

    private fun resetHomeDialogShownFlag() {
        val dialogPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        dialogPrefs.edit()
            .putBoolean("trakt_announcement_shown", false)
            .apply()
    }

    // ── Device Version Check ────────────────────────────────────────────────────

    /**
     * Checks if the device type matches the app version stored in Firebase.
     * If TV device is running phone version (or vice versa), shows QuitDialog.
     *
     * Uses a coroutine + timeout so the splash navigation can proceed if Firebase
     * is slow or unreachable.  Sets [versionCheckHandled] in every terminal branch
     * so [navigateToMain] never races past this check.
     */
    private fun checkDeviceVersionFromFirebase() {
        val isTvDevice = UpdateUtil.isTvDevice(this)
        val expectedVersionKey = if (isTvDevice) "app_type_tv" else "app_type_phone"

        Log.i(TAG, "Checking device version: isTv=$isTvDevice, checking $expectedVersionKey")

        lifecycleScope.launch {
            try {
                val snapshot = withTimeoutOrNull(VERSION_CHECK_TIMEOUT_MS) {
                    FirebaseManager.getFirebaseDatabaseInstance()
                        .getReference("app_config/app_packagenames/$expectedVersionKey")
                        .get()
                        .await()
                }

                if (snapshot == null) {
                    Log.w(TAG, "Version check timed out after ${VERSION_CHECK_TIMEOUT_MS}ms — continuing with splash")
                    Toast.makeText(
                        this@SplashActivity,
                        "Version check unavailable",
                        Toast.LENGTH_SHORT
                    ).show()
                    versionCheckHandled = true
                    return@launch
                }

                // Use runtime packageName (not BuildConfig.APPLICATION_ID) so this
                // matches the dialog message and is robust to build-variant overrides.
                val expectedVersion = snapshot.getValue(String::class.java)
                val currentPackage = packageName

                Log.i(TAG, "Firebase $expectedVersionKey: $expectedVersion, Current package: $currentPackage")

                when {
                    expectedVersion == null -> {
                        Log.w(TAG, "Firebase $expectedVersionKey is missing or not a string — continuing")
                        Toast.makeText(
                            this@SplashActivity,
                            "Version check unavailable",
                            Toast.LENGTH_SHORT
                        ).show()
                        versionCheckHandled = true
                    }
                    expectedVersion != currentPackage -> {
                        Log.w(TAG, "Version mismatch! Expected: $expectedVersion, Current: $currentPackage")
                        // versionCheckHandled is flipped to true inside the dialog
                        // buttons so the splash stays paused until the user decides.
                        showVersionMismatchDialog(isTvDevice, expectedVersion)
                    }
                    else -> {
                        Log.i(TAG, "Version check passed - continuing with splash")
                        versionCheckHandled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch app_info from Firebase", e)
                Toast.makeText(
                    this@SplashActivity,
                    "Version check unavailable",
                    Toast.LENGTH_SHORT
                ).show()
                versionCheckHandled = true
            }
        }
    }

    /**
     * Shows QuitDialog when device version doesn't match Firebase configuration.
     * Sets [versionCheckHandled] in both branches so the splash navigation guard
     * never blocks the user's explicit choice to leave or open the browser.
     */
    private fun showVersionMismatchDialog(isTvDevice: Boolean, expectedVersion: String) {
        val deviceType = if (isTvDevice) "TV" else "phone"
        QuitDialog(
            context = this,
            title = "Wrong App Version",
            message = "This $deviceType app does not match the official version.\n\n" +
                    "Current: $packageName\n" +
                    "Required: $expectedVersion\n\n" +
                    "Please download the official APK from GitHub.",
            positiveButtonText = "Download",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = {
                versionCheckHandled = true
                finish()
            },
            onYes = {
                versionCheckHandled = true
                // Open GitHub releases page
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kiduyu-klaus/KiduyuTv_final/releases/latest"))
                )
                finish()
            }
        ).showTracked()
    }

    /**
     * Start Firebase data synchronization in the background.
     * Updates the splash screen with sync progress.
     * Only syncs if the user is logged in.
     */
    private fun startFirebaseSync() {
        // Check if user is logged in
        if (!AuthManager.isSignedIn.value) {
            Log.i(TAG, "User not logged in - skipping Firebase sync")
            syncCompleted = true
            return
        }

        Log.i(TAG, "User logged in - starting Firebase data sync...")
        val displayName = AuthManager.userDisplayName.value ?: "User"
        Toast.makeText(this, "Welcome back, $displayName!", Toast.LENGTH_SHORT).show()
        // Start sync and observe progress
        FirebaseSyncManager.startSync()

        // Observe sync state changes
        lifecycleScope.launch {
            FirebaseSyncManager.syncProgress.collect { progress ->
                syncProgress = progress
            }
        }

        lifecycleScope.launch {
            FirebaseSyncManager.syncMessage.collect { message ->
                syncMessage = message
            }
        }

        lifecycleScope.launch {
            FirebaseSyncManager.syncState.collect { state ->
                when (state) {
                    is FirebaseSyncManager.SyncState.Success -> {
                        Log.i(TAG, "Firebase sync completed with ${state.itemsSynced} items")
                        syncCompleted = true
                    }
                    is FirebaseSyncManager.SyncState.Error -> {
                        Log.w(TAG, "Firebase sync failed: ${state.message}")
                        // Continue anyway - sync failure shouldn't block app startup
                        syncCompleted = true
                    }
                    is FirebaseSyncManager.SyncState.Syncing -> {
                        Log.i(TAG, "Firebase sync in progress...")
                    }
                    is FirebaseSyncManager.SyncState.Idle -> {
                        Log.i(TAG, "Firebase sync idle")
                    }
                }
            }
        }
    }

    /**
     * Navigate to MainActivity and finish this splash screen.
     * Guards against navigation while any blocking condition is still active:
     * an update dialog is open, permissions haven't been resolved, sync is
     * still running, ad consent is pending, or the Firebase device-version check
     * has not completed.
     * The Compose layer also prevents onTimeout() from being called in those
     * states, but this is a second safety net at the Activity level so that
     * even a race-condition early call is silently dropped.
     */
    private fun navigateToMain() {
        if (isFinishing) return

        // Guard: do not navigate while a dialog is open or conditions are unmet
        if (updateAvailable) {
            Log.i(TAG, "Update dialog still active — suppressing navigation")
            return
        }
        if (!permissionHandled) {
            Log.i(TAG, "Permission dialog still active — suppressing navigation")
            return
        }
        if (!syncCompleted) {
            Log.i(TAG, "Firebase sync still in progress — suppressing navigation")
            return
        }
        if (!versionCheckHandled) {
            Log.i(TAG, "Device version check still in progress — suppressing navigation")
            return
        }
        if (!adsConsentHandled) {
            Log.i(TAG, "Ad consent still in progress — suppressing navigation")
            return
        }

        if (mainNavigationStarted) return
        mainNavigationStarted = true

        Log.i(TAG, "Navigating to MainActivity...")
        AdManager.showAppOpenIfAvailable(this) {
            openMainActivity()
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    // ── Update check ──────────────────────────────────────────────────────────

    private fun checkForUpdates() {
        lifecycleScope.launch {
            Log.i(TAG, "Starting update check for ${getDeviceTypeString()} device...")

            var remoteVersion = UpdateUtil.fetchLatestGitHubReleaseVersion()
            if (remoteVersion == null) {
                Log.w(TAG, "GitHub API failed, falling back to VERSION file")
                remoteVersion = UpdateUtil.fetchRemoteVersion()
            }

            if (remoteVersion != null) {
                val localVersionName = BuildConfig.VERSION_NAME
                val isNewer = UpdateUtil.isNewerVersion(remoteVersion, localVersionName)
                Log.i(TAG, "Remote: $remoteVersion | Local: $localVersionName | Newer: $isNewer")

                if (isNewer) {
                    currentRemoteVersion = remoteVersion
                    updateAvailable = true       // pause progress bar BEFORE showing dialog
                    showUpdateDialog(remoteVersion)
                }
            } else {
                Log.w(TAG, "Could not fetch remote version, skipping update check")
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showUpdateDialog(remoteVersion: String) {
        lifecycleScope.launch {
            val notes = UpdateUtil.fetchLatestReleaseAnnotated()
            QuitDialog(
                context = this@SplashActivity,
                title = "v$remoteVersion Update Available",
                message = "A newer version of Kiduyu TV (v$remoteVersion) is available for your ${getDeviceTypeString()}.\nWould you like to download it now?",
                annotatedMessage = notes,
                positiveButtonText = "Download",
                negativeButtonText = "Exit",
                lottieAnimRes = R.raw.exit,
                onNo = { finish() },
                onYes = {
                    lifecycleScope.launch {
                        val apkInfo = UpdateUtil.fetchBestApkInfo(this@SplashActivity)
                        if (apkInfo != null) {
                            val localFile = UpdateUtil.getLocalApkFile(this@SplashActivity)
                            when {
                                UpdateUtil.isLocalApkValid(this@SplashActivity, apkInfo) -> {
                                    // Cached file is the right version, right device type, right size
                                    Log.i(TAG, "Valid cached APK found, skipping download")
                                    showInstallPrompt(localFile, apkInfo)
                                }
                                else -> {
                                    // Stale, wrong device type, incomplete, or missing — delete and re-download
                                    if (localFile.exists()) {
                                        localFile.delete()
                                        Log.i(TAG, "Deleted stale cached APK")
                                    }
                                    downloadAndInstallApk(apkInfo)
                                }
                            }
                        } else {
                            Log.w(TAG, "No APK found for ${getDeviceTypeString()}, opening releases page")
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/kiduyu-klaus/KiduyuTv_final/releases/latest".toUri()
                                )
                            )
                        }
                    }
                }
            ).showTracked()
        }
    }

    private fun downloadAndInstallApk(apkInfo: ApkInfo) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 40, 72, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        }

        val statusText = TextView(this).apply {
            text = "Starting download..."
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
        }

        val fileNameText = TextView(this).apply {
            text = apkInfo.fileName
            textSize = 11f
            setTextColor(android.graphics.Color.GRAY)
            maxLines = 2
        }

        val progressBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        }

        val versionInfoText = TextView(this).apply {
            text = "${getDeviceTypeString()} version | Build ${apkInfo.buildNumber}"
            textSize = 10f
            setTextColor(android.graphics.Color.DKGRAY)
        }

        layout.addView(statusText)
        layout.addView(fileNameText)
        layout.addView(progressBar)
        layout.addView(versionInfoText)

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .create()
            .also { activeDialogs.add(it) }
        progressDialog.show()

        lifecycleScope.launch {
            val apkFile = UpdateUtil.downloadApk(this@SplashActivity, apkInfo) { pct, fileName ->
                progressBar.progress = pct
                statusText.text = "Downloading... $pct%"
                if (fileNameText.text != fileName) fileNameText.text = fileName
            }

            progressDialog.dismiss()
            activeDialogs.remove(progressDialog)

            if (apkFile != null) {
                UpdateUtil.saveDownloadedApkMeta(this@SplashActivity, apkInfo)
                showInstallPrompt(apkFile, apkInfo)
            } else {
                QuitDialog(
                    context = this@SplashActivity,
                    title = "Download Failed",
                    message = "Could not download the update.\nPlease check your connection and try again.",
                    positiveButtonText = "Retry",
                    negativeButtonText = "Exit",
                    lottieAnimRes = R.raw.exit,
                    onYes = { downloadAndInstallApk(apkInfo) },
                    onNo = { finish() }
                ).showTracked()
            }
        }
    }

    private fun showInstallPrompt(apkFile: File, apkInfo: ApkInfo) {
        // Extract version code from the APK being installed
        val versionCode = extractVersionCodeFromFileName(apkInfo.fileName)

        QuitDialog(
            context = this,
            title = "Download Complete",
            message = "${apkInfo.fileName}\n\nHas been downloaded.\nTap Install to apply the update.",
            positiveButtonText = "Install",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.splash_loading,
            onYes = {
                // Store the APK file reference for result handling
                pendingApkFile = apkFile

                // Check permission and launch installation
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (packageManager.canRequestPackageInstalls()) {
                        launchInstallation(apkFile, versionCode)
                    } else {
                        showPermissionDialog(apkFile)
                    }
                } else {
                    launchInstallation(apkFile, versionCode)
                }
            },
            onNo = { finish() }
        ).showTracked()
    }

    /**
     * Extracts the version code (build number) from the APK filename.
     * Example: "KiduyuTV-phone-release-1.1.76-phone-tv-build383.apk" -> 383
     */
    private fun extractVersionCodeFromFileName(fileName: String): Int {
        val buildPattern = Regex("build(\\d+)")
        return buildPattern.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    /**
     * Launches the APK installation intent using Activity Result API.
     */
    private fun launchInstallation(apkFile: File, versionCode: Int = -1) {
        // Store the expected version code for result verification
        pendingVersionCode = versionCode

        val installIntent = UpdateUtil.getInstallIntent(this, apkFile)
        if (installIntent != null) {
            installLauncher.launch(installIntent)
        } else {
            // Fallback to direct installation if intent creation fails
            UpdateUtil.installApk(this, apkFile)
            // For fallback, we rely on UpdateReceiver or user interaction
            Log.w(TAG, "Using fallback install method, app should restart via UpdateReceiver on success")
            finish()
        }
    }

    private fun showPermissionDialog(apkFile: File, versionCode: Int = -1) {
        QuitDialog(
            context = this,
            title = "Permission Required",
            message = "To install the update, Kiduyu TV needs permission to install unknown apps. Please enable it in the settings.",
            positiveButtonText = "Settings",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() },
            onYes = {
                // Store the APK file and version code before opening settings
                pendingApkFile = apkFile
                pendingVersionCode = versionCode
                UpdateUtil.openInstallPermissionSettings(this)
                // Don't finish - we'll check permission when user returns
            }
        ).showTracked()
    }

    override fun onResume() {
        super.onResume()
        // Check if we returned from settings and now have permission
        pendingApkFile?.let { apkFile ->
            val expectedVersion = pendingVersionCode
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                packageManager.canRequestPackageInstalls()) {
                Log.i(TAG, "Permission granted after returning from settings, launching installation")
                // Clear pending file first to avoid recursion
                pendingApkFile = null
                // Dismiss any active dialogs before launching installation
                activeDialogs.forEach { if (it.isShowing) it.dismiss() }
                activeDialogs.clear()
                // Launch installation with expected version
                launchInstallation(apkFile, expectedVersion)
            }
        }
    }

    // ── Composable ────────────────────────────────────────────────────────────

    @Composable
    fun SplashScreen(
        updateAvailable: Boolean,
        permissionHandled: Boolean,
        remoteVersion: String?,
        syncCompleted: Boolean,
        adsConsentHandled: Boolean,
        syncProgress: Int,
        syncMessage: String,
        onTimeout: () -> Unit
    ) {
        // Progress pauses while startup gates are pending.
        val isPaused = updateAvailable || !permissionHandled || !syncCompleted || !versionCheckHandled || !adsConsentHandled

        val barProgress = remember { Animatable(0f) }

        LaunchedEffect(isPaused) {
            if (isPaused) {
                barProgress.stop()
            } else {
                // If the bar already completed while we were paused (e.g. the update
                // dialog was open for a long time), do NOT call onTimeout() immediately.
                // That would navigate to MainActivity before the dialog has fully
                // dismissed.  Instead we bail out — the progress bar stays at 1f and
                // the user sees the splash for a brief extra moment until the Activity-
                // level guard in navigateToMain() is also clear.
                if (barProgress.value >= 1f) return@LaunchedEffect

                val remainingMs = ((1f - barProgress.value) * SPLASH_DURATION_MS)
                    .toInt().coerceAtLeast(100)
                barProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing)
                )
                onTimeout()
            }
        }

        // Fade the whole screen in on mount
        val screenAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            screenAlpha.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }

        // Lottie loading animation
        val lottieComposition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.splash_loading)
        )
        val lottieProgress by animateLottieCompositionAsState(
            composition = lottieComposition,
            iterations = LottieConstants.IterateForever
        )

        val (statusLabel, statusColor) = when {
            updateAvailable && remoteVersion != null ->
                "Update available: v$remoteVersion" to Color(0xFFFF4D57)
            updateAvailable ->
                "Update available" to Color(0xFFFF4D57)
            !permissionHandled ->
                "Requesting permissions..." to Color(0xFFBFC6D1)
            !versionCheckHandled ->
                "Verifying app version..." to Color(0xFFBFC6D1)
            !adsConsentHandled ->
                "Preparing ads..." to Color(0xFFBFC6D1)
            !syncCompleted ->
                "Syncing data..." to Color(0xFFBFC6D1)
            else ->
                "Opening KiduyuTV" to Color(0xFFBFC6D1)
        }
        val syncStatus = if (!syncCompleted && syncMessage.isNotBlank()) {
            val progress = syncProgress.coerceIn(0, 100)
            if (progress > 0) "$syncMessage $progress%" else syncMessage
        } else {
            null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050505),
                            Color(0xFF11151A),
                            Color(0xFF050505)
                        )
                    )
                )
                .alpha(screenAlpha.value)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(Color(0xFFE50914))
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x331C2430))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        color = Color(0xFF9AA3AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF20242B),
                                    Color(0xFF121418)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(30.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher11),
                        contentDescription = "KiduyuTV icon",
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Kiduyu",
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp
                    )
                    Text(
                        text = "TV",
                        color = Color(0xFFE50914),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp
                    )
                }

                Text(
                    text = "STREAMING SIMPLIFIED",
                    color = Color(0xFF8F98A3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                LottieAnimation(
                    composition = lottieComposition,
                    progress = { lottieProgress },
                    modifier = Modifier.size(84.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                syncStatus?.let { label ->
                    Box(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF171B21))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.07f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = Color(0xFF9AA3AE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xCC101318))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )

                        LinearProgressIndicator(
                            progress = { barProgress.value },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFE50914),
                            trackColor = Color(0xFF2B3037)
                        )
                    }
                }
            }
        }
    }
}