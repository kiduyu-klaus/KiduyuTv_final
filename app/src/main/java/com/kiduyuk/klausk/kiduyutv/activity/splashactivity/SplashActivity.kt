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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.compose.*
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.util.ApkInfo
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.UpdateUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

    // Compose-observable flag — when true, SplashScreen will NOT navigate to MainActivity
    private var updateAvailable by mutableStateOf(false)

    // Flag to ensure we only proceed after permission is handled (if applicable)
    private var permissionHandled by mutableStateOf(false)

    // Current remote version for display in update dialog
    private var currentRemoteVersion: String? = null

    // Current APK info for display during download
    private var currentApkInfo: ApkInfo? = null

    // Tracks every dialog shown so onDestroy can safely dismiss them all
    private val activeDialogs = mutableListOf<Dialog>()

    // Extension: show a dialog and register it for cleanup
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
        if (isGranted) {
            Log.i(TAG, "Notification permission granted")
        } else {
            Log.i(TAG, "Notification permission denied")
        }
        permissionHandled = true
    }

    private fun checkNotificationPermission() {
        when {
            // Fire TV: notification permission dialogs are not surfaced to the user
            // on most Fire OS builds — skip the request and proceed immediately.
            isFireTv() -> {
                Log.i(TAG, "Fire TV detected — skipping notification permission request")
                permissionHandled = true
            }

            // Android 13+ phones/tablets: request POST_NOTIFICATIONS at runtime
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        permissionHandled = true
                    }
                    else -> {
                        requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Below Android 13: permission is granted implicitly
            else -> permissionHandled = true
        }
    }

    // ── Device detection ──────────────────────────────────────────────────────────

    private fun isFireTv(): Boolean =
        packageManager.hasSystemFeature("amazon.hardware.fire_tv")

    /**
     * Determines the device type string for logging and display purposes.
     */
    private fun getDeviceTypeString(): String {
        return if (isFireTv() || UpdateUtil.isTvDevice(this)) "TV" else "Phone/Tablet"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForUpdates()
        checkNotificationPermission()
        setContent {
            KiduyuTvTheme {
                SplashScreen(
                    updateAvailable = updateAvailable,
                    permissionHandled = permissionHandled
                ) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    // ── Update check ──────────────────────────────────────────────────────────

    /**
     * Enhanced update check using GitHub Releases API with OkHttpClient.
     * Fetches the latest release version and compares with local version.
     */
    private fun checkForUpdates() {
        lifecycleScope.launch {
            Log.i(TAG, "Starting update check for ${getDeviceTypeString()} device...")
            
            // Try the new GitHub Releases API first
            var remoteVersion = UpdateUtil.fetchLatestGitHubReleaseVersion()
            
            // Fallback to legacy VERSION file if GitHub API fails
            if (remoteVersion == null) {
                Log.w(TAG, "GitHub API failed, falling back to VERSION file")
                remoteVersion = UpdateUtil.fetchRemoteVersion()
            }
            
            if (remoteVersion != null) {
                val localVersionName = BuildConfig.VERSION_NAME
                val isNewer = UpdateUtil.isNewerVersion(remoteVersion, localVersionName)
                
                Log.i(TAG, "Remote version: $remoteVersion, Local version: $localVersionName, Is newer: $isNewer")
                
                if (isNewer) {
                    currentRemoteVersion = remoteVersion
                    updateAvailable = true   // gate the splash timeout BEFORE showing the dialog
                    showUpdateDialog(remoteVersion)
                }
            } else {
                Log.w(TAG, "Could not fetch remote version, skipping update check")
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    /**
     * Shows the update available dialog to the user.
     * User can choose to download the update or exit the app.
     */
    private fun showUpdateDialog(remoteVersion: String) {
        QuitDialog(
            context = this,
            title = "v$remoteVersion Update Available",
            message = "A newer version of Kiduyu TV (v$remoteVersion) is available for your ${getDeviceTypeString()}.\nWould you like to download it now?",
            positiveButtonText = "Download",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() }, // User chose Exit, close the app
            onYes = {
                lifecycleScope.launch {
                    // Use device-specific APK fetching with full APK info
                    val apkInfo = UpdateUtil.fetchBestApkInfo(this@SplashActivity)
                    if (apkInfo != null) {
                        currentApkInfo = apkInfo
                        Log.i(TAG, "Found APK for download: ${apkInfo.fileName}")
                        downloadAndInstallApk(apkInfo)
                    } else {
                        Log.w(TAG, "Could not find APK for this device type (${UpdateUtil.getDeviceTypeString(this@SplashActivity)}), opening releases page")
                        // Fallback: open releases page in browser
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

    /**
     * Downloads the APK file and shows progress to the user.
     * Displays the APK filename during download.
     * After successful download, prompts user to install.
     */
    private fun downloadAndInstallApk(apkInfo: ApkInfo) {
        // ── Build the progress dialog ─────────────────────────────────────────
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
        
        // Filename display
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
            .also { activeDialogs.add(it) }   // track before showing
        progressDialog.show()

        lifecycleScope.launch {
            val apkFile = UpdateUtil.downloadApk(this@SplashActivity, apkInfo) { pct, fileName ->
                progressBar.progress = pct
                statusText.text = "Downloading... $pct%"
                // Update filename if it changes (though it shouldn't)
                if (fileNameText.text != fileName) {
                    fileNameText.text = fileName
                }
            }

            // Dismiss and remove from tracker before showing the next dialog
            progressDialog.dismiss()
            activeDialogs.remove(progressDialog)

            // ── Post-download QuitDialog ──────────────────────────────────────
            if (apkFile != null) {
                Log.i(TAG, "Download complete: ${apkInfo.fileName}")
                QuitDialog(
                    context = this@SplashActivity,
                    title = "Download Complete",
                    message = "${apkInfo.fileName}\n\nHas been downloaded.\nTap Install to apply the update.",
                    positiveButtonText = "Install",
                    negativeButtonText = "Exit",
                    lottieAnimRes = R.raw.splash_loading,
                    onYes = { 
                        UpdateUtil.checkPermissionAndInstall(this@SplashActivity, apkFile) {
                            showPermissionDialog(apkFile)
                        }
                    },
                    onNo = { finish() } // Exit if user declines installation after download
                ).showTracked()
            } else {
                Log.e(TAG, "Download failed")
                QuitDialog(
                    context = this@SplashActivity,
                    title = "Download Failed",
                    message = "Could not download the update.\nPlease check your connection and try again.",
                    positiveButtonText = "Retry",
                    negativeButtonText = "Exit",
                    lottieAnimRes = R.raw.exit,
                    onYes = { downloadAndInstallApk(apkInfo) },
                    onNo = { finish() } // Exit if user cancels after failure
                ).showTracked()
            }
        }
    }

    /**
     * Shows a dialog guiding the user to enable unknown app installation.
     * Required for Android 8.0+ to install APKs outside the Play Store.
     */
    private fun showPermissionDialog(apkFile: File) {
        QuitDialog(
            context = this,
            title = "Permission Required",
            message = "To install the update, Kiduyu TV needs permission to install unknown apps.\n\nPlease enable 'Install unknown apps' for Kiduyu TV in Settings.",
            positiveButtonText = "Settings",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() },
            onYes = {
                UpdateUtil.openInstallPermissionSettings(this)
                // User needs to manually grant permission and return to app
                // or restart to trigger the check again
                finish()
            }
        ).showTracked()
    }

// ── Composable ────────────────────────────────────────────────────────────────

    @Composable
    fun SplashScreen(
        updateAvailable: Boolean = false,
        permissionHandled: Boolean = true,
        onTimeout: () -> Unit
    ) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash_loading))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = LottieConstants.IterateForever
        )

        // Reduce wait time if update is available (user may want to proceed to download)
        LaunchedEffect(updateAvailable, permissionHandled) {
            if (!updateAvailable && permissionHandled) {
                delay(6000)
                onTimeout()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Kiduyu TV",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Streaming Simplified",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(200.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
                
                if (updateAvailable && currentRemoteVersion != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Update available: v$currentRemoteVersion",
                        color = Color(0xFFE65100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
