package com.kiduyuk.klausk.kiduyutv.activity.mainactivity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.kiduyuk.klausk.kiduyutv.ui.navigation.NavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.MobileNavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.network.NetworkConnectivityChecker
import com.kiduyuk.klausk.kiduyutv.network.NetworkConnectivityObserver
import com.kiduyuk.klausk.kiduyutv.network.NetworkState
import com.kiduyuk.klausk.kiduyutv.network.NetworkStateDialog
import com.kiduyuk.klausk.kiduyutv.ui.components.BannerAdView
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.BuildConfig

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            showPermissionDeniedDialog()
        }
    }

    @androidx.media3.common.util.UnstableApi
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Note: DatabaseManager and MyListManager are now initialized in KiduyuTvApp

        checkAndRequestStoragePermissions()

        // Perform initial network check to prevent crashes during eager repository initialization
        val initialNetworkState = NetworkConnectivityChecker.networkState.value
        if (initialNetworkState is NetworkState.NotConnected || initialNetworkState is NetworkState.ConnectedNoInternet) {
            NetworkStateDialog.showIfNeeded(this, initialNetworkState) {
                // Retry: force refresh and check again
                NetworkConnectivityChecker.forceRefresh(this)
            }
        }

        setContent {
            KiduyuTvTheme {
                val navController = rememberNavController()
                var currentRoute by remember { mutableStateOf(Screen.Home.route) }

                // Track navigation changes
                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        currentRoute = entry.destination.route ?: Screen.Home.route
                    }
                }

                // Handle deep-linking from notifications
                LaunchedEffect(navController) {
                    handleNotificationIntent(navController)
                }

                // Handle back press for exit confirmation
                DisposableEffect(navController) {
                    val callback = object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            // If we can pop back stack, do it. Otherwise, show exit dialog.
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                showExitConfirmationDialog()
                            }
                        }
                    }
                    onBackPressedDispatcher.addCallback(callback)
                    onDispose { callback.remove() }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    // Network connectivity observer at root level
                    NetworkConnectivityObserver()

                    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

                    if (isTv) {
                        NavGraph(navController = navController)
                    } else {
                        MobileNavGraph(navController = navController)
                    }
                }
            }
        }
    }

    /**
     * Handles navigation when the app is opened via a notification deep-link.
     */
    private fun handleNotificationIntent(navController: NavHostController) {
        val mediaId = intent.getIntExtra("NOTIFICATION_MEDIA_ID", -1)
        val mediaType = intent.getStringExtra("NOTIFICATION_MEDIA_TYPE")

        if (mediaId != -1 && mediaType != null) {
            val route = when (mediaType) {
                "movie" -> Screen.MovieDetail.createRoute(mediaId)
                "tv" -> Screen.TvShowDetail.createRoute(mediaId)
                else -> null
            }

            route?.let {
                // Navigate to the detail screen, clearing the backstack up to home if needed
                navController.navigate(it) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                }
            }

            // Clear the intent extras to avoid re-triggering navigation on configuration changes
            intent.removeExtra("NOTIFICATION_MEDIA_ID")
            intent.removeExtra("NOTIFICATION_MEDIA_TYPE")
        }
    }

    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Quit Kiduyu TV?",
            message = "Are you sure you want to exit the app?",
            positiveButtonText = "Yes",
            negativeButtonText = "No",
            lottieAnimRes = com.kiduyuk.klausk.kiduyutv.R.raw.exit,
            onNo = { /* dismiss — dialog closes itself */ },
            onYes = { finish() }
        ).show()
    }

    private fun checkAndRequestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            showPermissionExplanationDialog(permissionsToRequest.toTypedArray())
        }
    }

    private fun showPermissionExplanationDialog(permissions: Array<String>) {
        QuitDialog(
            context = this,
            title = "Storage Permission Required",
            message = "KiduyuTv needs storage access to cache images and provide a smoother experience. Please grant the following permissions.",
            positiveButtonText = "Grant",
            negativeButtonText = "Exit App",
            lottieAnimRes = com.kiduyuk.klausk.kiduyutv.R.raw.exit,
            onNo = { finish() },
            onYes = { requestPermissionLauncher.launch(permissions) }
        ).show()
    }

    private fun showPermissionDeniedDialog() {
        QuitDialog(
            context = this,
            title = "Permissions Denied",
            message = "Storage permissions are essential for KiduyuTv to function correctly. Would you like to try again or exit the app?",
            positiveButtonText = "Try Again",
            negativeButtonText = "Exit App",
            lottieAnimRes = com.kiduyuk.klausk.kiduyutv.R.raw.exit,
            onNo = { finish() },
            onYes = { checkAndRequestStoragePermissions() }
        ).show()
    }
}

