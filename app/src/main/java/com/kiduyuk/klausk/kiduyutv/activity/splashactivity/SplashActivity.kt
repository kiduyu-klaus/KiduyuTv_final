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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.compose.*
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.UpdateUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    // Compose-observable flag — when true, SplashScreen will NOT navigate to MainActivity
    private var updateAvailable by mutableStateOf(false)

    // Flag to ensure we only proceed after permission is handled (if applicable)
    private var permissionHandled by mutableStateOf(false)

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
            Log.i("SplashActivity", "Notification permission granted")
        } else {
            Log.i("SplashActivity", "Notification permission denied")
        }
        permissionHandled = true
    }

    private fun checkNotificationPermission() {
        when {
            // Fire TV: notification permission dialogs are not surfaced to the user
            // on most Fire OS builds — skip the request and proceed immediately.
            isFireTv() -> {
                Log.i("SplashActivity", "Fire TV detected — skipping notification permission request")
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

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val remoteVersion = UpdateUtil.fetchRemoteVersion()
            if (remoteVersion != null) {
                val localVersionName = BuildConfig.VERSION_NAME
                Log.i("SplashActivity", "Remote version: $remoteVersion, Local version: $localVersionName")
                if (UpdateUtil.isNewerVersion(remoteVersion, localVersionName)) {
                    updateAvailable = true   // gate the splash timeout BEFORE showing the dialog
                    showUpdateDialog(remoteVersion)
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showUpdateDialog(remoteVersion: String) {
        QuitDialog(
            context = this,
            title = "v$remoteVersion Update Available",
            message = "A newer version of Kiduyu TV (v$remoteVersion) is available. Would you like to download it now?",
            positiveButtonText = "Download",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() }, // User chose Exit, close the app
            onYes = {
                lifecycleScope.launch {
                    val apkUrl = UpdateUtil.fetchLatestApkUrl()
                    if (apkUrl != null) {
                        downloadAndInstallApk(apkUrl)
                    } else {
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

    private fun downloadAndInstallApk(apkUrl: String) {
        // ── Build the progress dialog ─────────────────────────────────────────
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 40, 72, 24)
        }
        val statusText = TextView(this).apply {
            text = "Starting download…"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
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
        layout.addView(statusText)
        layout.addView(progressBar)

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .create()
            .also { activeDialogs.add(it) }   // track before showing
        progressDialog.show()

        lifecycleScope.launch {
            val apkFile = UpdateUtil.downloadApk(this@SplashActivity, apkUrl) { pct ->
                progressBar.progress = pct
                statusText.text = "Downloading… $pct%"
            }

            // Dismiss and remove from tracker before showing the next dialog
            progressDialog.dismiss()
            activeDialogs.remove(progressDialog)

            // ── Post-download QuitDialog ──────────────────────────────────────
            if (apkFile != null) {
                QuitDialog(
                    context = this@SplashActivity,
                    title = "Download Complete",
                    message = "KiduyuTV has been downloaded.\nTap Install to apply the update.",
                    positiveButtonText = "Install",
                    negativeButtonText = "Exit",
                    lottieAnimRes = R.raw.splash_loading,  // swap for a success Lottie if available
                    onYes = { 
                        UpdateUtil.checkPermissionAndInstall(this@SplashActivity, apkFile) {
                            showPermissionDialog(apkFile)
                        }
                    },
                    onNo = { finish() } // Exit if user declines installation after download
                ).showTracked()
            } else {
                QuitDialog(
                    context = this@SplashActivity,
                    title = "Download Failed",
                    message = "Could not download the update.\nPlease check your connection and try again.",
                    positiveButtonText = "Retry",
                    negativeButtonText = "Exit",
                    lottieAnimRes = R.raw.exit,
                    onYes = { downloadAndInstallApk(apkUrl) },
                    onNo = { finish() } // Exit if user cancels after failure
                ).showTracked()
            }
        }
    }

    private fun showPermissionDialog(apkFile: File) {
        QuitDialog(
            context = this,
            title = "Permission Required",
            message = "To install the update, Kiduyu TV needs permission to install unknown apps. Please enable it in the settings.",
            positiveButtonText = "Settings",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() },
            onYes = {
                UpdateUtil.openInstallPermissionSettings(this)
                // They'll have to come back to the app manually or we can't easily track the result here,
                // or they can restart the app to trigger the check again.
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
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(200.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
            }
            
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = Color.DarkGray,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}
