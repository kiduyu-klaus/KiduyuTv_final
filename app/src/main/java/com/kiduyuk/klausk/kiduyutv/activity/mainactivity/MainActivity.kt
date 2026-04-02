package com.kiduyuk.klausk.kiduyutv.activity.mainactivity

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.kiduyuk.klausk.kiduyutv.ui.navigation.NavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Note: DatabaseManager and MyListManager are now initialized in KiduyuTvApp

        checkAndRequestStoragePermissions()
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
                    NavGraph(navController = navController)
                }
            }
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit KiduyuTv?")
            .setPositiveButton("Exit") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("KiduyuTv needs storage access to cache images and provide a smoother experience. Please grant the following permissions.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(permissions)
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage("Storage permissions are essential for KiduyuTv to function correctly. Would you like to try again or exit the app?")
            .setPositiveButton("Try Again") { _, _ ->
                checkAndRequestStoragePermissions()
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
