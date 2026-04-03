package com.kiduyuk.klausk.kiduyutv.activity.splashactivity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.multidex.BuildConfig
import com.airbnb.lottie.compose.*
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    // Compose-observable flag — when true, SplashScreen will NOT navigate to MainActivity
    private var updateAvailable by mutableStateOf(false)

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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForUpdates()
        setContent {
            KiduyuTvTheme {
                SplashScreen(updateAvailable = updateAvailable) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    // ── Update check ──────────────────────────────────────────────────────────

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val remoteVersion = fetchRemoteVersion()
            if (remoteVersion != null) {
                val localVersionName = BuildConfig.VERSION_NAME
                if (isNewerVersion(remoteVersion, localVersionName)) {
                    updateAvailable = true   // gate the splash timeout BEFORE showing the dialog
                    showUpdateDialog()
                }
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
            Log.e("SplashActivity", "Error fetching remote version", e)
            null
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        return try {
            val remoteParts = remote.split(".").map { it.toInt() }
            val localParts = local.split(".").map { it.toInt() }
            val maxLength = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLength) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            false
        } catch (e: Exception) {
            remote > local   // fallback: lexicographic comparison
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showUpdateDialog() {
        QuitDialog(
            context = this,
            title = "Update Available",
            message = "A newer version of Kiduyu TV is available. Would you like to download it now?",
            positiveButtonText = "Download",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() }, // User chose Exit, close the app
            onYes = {
                lifecycleScope.launch {
                    val apkUrl = fetchLatestApkUrl()
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

        // ── Stream APK on IO, push progress to Main ───────────────────────────
        lifecycleScope.launch {
            val apkFile: File? = withContext(Dispatchers.IO) {
                try {
                    val connection = URL(apkUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.connect()

                    val totalBytes = connection.contentLengthLong
                    val outFile = File(getExternalFilesDir(null), "kiduyutv-update.apk")

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
                                        progressBar.progress = pct
                                        statusText.text = "Downloading… $pct%"
                                    }
                                }
                            }
                        }
                    }
                    outFile
                } catch (e: Exception) {
                    Log.e("SplashActivity", "APK download failed", e)
                    null
                }
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
                    onYes = { checkPermissionAndInstall(apkFile) },
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

    private fun checkPermissionAndInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                showPermissionDialog(apkFile)
                return
            }
        }
        installApk(apkFile)
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
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                // We finish here because once they leave to settings, the app state might be lost
                // or they can restart the app to trigger the check again.
                finish()
            }
        ).showTracked()
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        startActivity(intent)
    }

    // ── GitHub API — resolve direct APK download URL ──────────────────────────

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

            // Mirror the workflow: first prerelease with a "release" APK asset
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
                        return@withContext asset.optString("browser_download_url", null)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error fetching APK URL", e)
            null
        }
    }

// ── Composable ────────────────────────────────────────────────────────────────

    @Composable
    fun SplashScreen(updateAvailable: Boolean = false, onTimeout: () -> Unit) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash_loading))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = LottieConstants.IterateForever
        )

        LaunchedEffect(updateAvailable) {
            if (!updateAvailable) {
                delay(10_000)
                onTimeout()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher11),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.app_name).uppercase(),
                        color = Color(0xFFE65100),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.height(10.dp)
                )
            }
        }
    }
}
