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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.ui.navigation.NavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme

class MainActivity : ComponentActivity(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Memory cache: 25% of app memory
            .memoryCache {
                val maxMemory = Runtime.getRuntime().maxMemory()
                val cacheSize = minOf(
                    (maxMemory * 0.15).toLong(),   // 15% of app memory
                    50 * 1024 * 1024L              // hard cap at 50MB
                )
                MemoryCache.Builder(this)
                    .maxSizeBytes(cacheSize.toInt())
                    .build()
            }
            // Disk cache: 100MB
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB
                    .build()
            }
            // Network cache with OkHttp integration
            .okHttpClient {
                // Reuse the same client for consistent caching
                ApiClient
                    .createOkHttpClient(this@MainActivity)
            }
            // Enable crossfade for smooth image transitions
            .crossfade(true)
            // Respect cache headers
            .respectCacheHeaders(true)
            // Memory cache policy: cache first, then network
            .memoryCachePolicy(CachePolicy.ENABLED)
            // Disk cache policy: cache first, then network
            .diskCachePolicy(CachePolicy.ENABLED)
            // Network policy: prefer cache when available
            .networkCachePolicy(CachePolicy.ENABLED)
            // Logger for debugging (disable in release)
            .logger(DebugLogger())
            .build()
    }

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

        // Initialize Room database manager
        DatabaseManager.init(this)

        // Initialize MyListManager (now uses Room internally)
        MyListManager.init(this)

        // Schedule periodic cache cleanup (optional, can be removed if not needed)
        scheduleCacheCleanup()

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
                    modifier = Modifier.Companion
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

    /**
     * Schedules periodic cleanup of expired cache entries.
     * This helps maintain database size and performance.
     * Cache cleanup runs on app startup.
     */
    private fun scheduleCacheCleanup() {
        // Clean up expired cache on app start
        DatabaseManager.cleanExpiredCache()

        // You can also schedule periodic cleanup using WorkManager if needed:
        // val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
        //     6, TimeUnit.HOURS
        // ).build()
        // WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        //     "cache_cleanup",
        //     ExistingPeriodicWorkPolicy.KEEP,
        //     cleanupRequest
        // )
    }
}