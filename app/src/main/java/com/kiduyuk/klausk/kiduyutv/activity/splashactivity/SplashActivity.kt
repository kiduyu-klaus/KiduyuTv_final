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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.kiduyuk.klausk.kiduyutv.util.FirebaseSyncManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.UpdateUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DURATION_MS = 6000
        private const val SYNC_TIMEOUT_MS = 10000L // 10 seconds max for sync
    }

    // Compose-observable flags
    private var updateAvailable by mutableStateOf(false)
    private var permissionHandled by mutableStateOf(false)
    private var syncCompleted by mutableStateOf(false)

    // Remote version shown in the status chip
    private var currentRemoteVersion by mutableStateOf<String?>(null)
    
    // Firebase sync state
    private var syncProgress by mutableStateOf(0)
    private var syncMessage by mutableStateOf("")

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
        
        // Initialize FirebaseSyncManager and start data sync
        FirebaseSyncManager.init(this)
        startFirebaseSync()
        
        checkForUpdates()
        checkNotificationPermission()
        setContent {
            KiduyuTvTheme {
                SplashScreen(
                    updateAvailable = updateAvailable,
                    permissionHandled = permissionHandled,
                    remoteVersion = currentRemoteVersion,
                    syncCompleted = syncCompleted,
                    syncProgress = syncProgress,
                    syncMessage = syncMessage,
                    onTimeout = {
                        // Navigate to MainActivity regardless of sync status
                        navigateToMain()
                    }
                )
            }
        }
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
                        // Enable real-time sync after initial sync completes
                        FirebaseSyncManager.enableRealTimeSync()
                        syncCompleted = true
                    }
                    is FirebaseSyncManager.SyncState.Error -> {
                        Log.w(TAG, "Firebase sync failed: ${state.message}")
                        // Continue anyway - sync failure shouldn't block app startup
                        syncCompleted = true
                    }
                    is FirebaseSyncManager.SyncState.Syncing -> {
                        Log.d(TAG, "Firebase sync in progress...")
                    }
                    is FirebaseSyncManager.SyncState.Idle -> {
                        Log.d(TAG, "Firebase sync idle")
                    }
                }
            }
        }
    }
    
    /**
     * Navigate to MainActivity and finish this splash screen.
     */
    private fun navigateToMain() {
        if (isFinishing) return // Prevent navigation if already finishing
        Log.i(TAG, "Navigating to MainActivity...")
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
        QuitDialog(
            context = this,
            title = "v$remoteVersion Update Available",
            message = "A newer version of Kiduyu TV (v$remoteVersion) is available for your ${getDeviceTypeString()}.\nWould you like to download it now?",
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
                                Uri.parse("https://github.com/kiduyu-klaus/KiduyuTv_final/releases/latest")
                            )
                        )
                    }
                }
            }
        ).showTracked()
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
        syncProgress: Int,
        syncMessage: String,
        onTimeout: () -> Unit
    ) {
        // Progress pauses when a dialog is open, permission hasn't been resolved yet,
        // or sync hasn't completed yet.
        val isPaused = updateAvailable || !permissionHandled || !syncCompleted

        val barProgress = remember { Animatable(0f) }

        LaunchedEffect(isPaused) {
            if (isPaused) {
                barProgress.stop()
            } else {
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

        // Slowly pulsing background glow
        val glowTransition = rememberInfiniteTransition(label = "glow")
        val glowAlpha by glowTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )

        // Lottie loading animation
        val lottieComposition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.splash_loading)
        )
        val lottieProgress by animateLottieCompositionAsState(
            composition = lottieComposition,
            iterations = LottieConstants.IterateForever
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
                .alpha(screenAlpha.value)
        ) {

            // ── Soft radial glow ─────────────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .size(440.dp)
                    .align(Alignment.Center)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE50914).copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                )
            }

            // ── Centre logo block ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App icon
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher11),
                    contentDescription = "KiduyuTV icon",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                )

                Spacer(Modifier.height(10.dp))

                // "KiduyuTV" — white + red split
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Kiduyu",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    )
                    Text(
                        text = "TV",
                        color = Color(0xFFE50914),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.5).sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "STREAMING SIMPLIFIED",
                    color = Color(0xFF606060),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(10.dp))

                // Small Lottie indicator below the wordmark
                LottieAnimation(
                    composition = lottieComposition,
                    progress = { lottieProgress },
                    modifier = Modifier.size(72.dp)
                )
            }

            // ── Bottom: status chip + progress bar + version ─────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Firebase Sync Status Chip
                if (!syncCompleted && syncMessage.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = syncMessage,
                            color = Color(0xFF808080),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                // Status chip — only visible while progress is paused
                if (isPaused) {
                    val (chipLabel, chipColor) = when {
                        updateAvailable && remoteVersion != null ->
                            "Update available: v$remoteVersion" to Color(0xFFE50914)
                        updateAvailable ->
                            "Update available" to Color(0xFFE50914)
                        !permissionHandled ->
                            "Requesting permissions…" to Color(0xFF808080)
                        !syncCompleted ->
                            "Syncing data..." to Color(0xFF808080)
                        else ->
                            "Loading..." to Color(0xFF808080)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chipLabel,
                            color = chipColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                // Animated progress bar (2dp thin, red fill)
                LinearProgressIndicator(
                    progress = { barProgress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = Color(0xFFE50914),
                    trackColor = Color(0xFF2A2A2A)
                )

                // Version
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = Color(0xFF3A3A3A),
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
