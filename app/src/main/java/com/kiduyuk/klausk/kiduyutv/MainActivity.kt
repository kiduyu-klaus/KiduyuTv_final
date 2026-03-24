package com.kiduyuk.klausk.kiduyutv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.kiduyuk.klausk.kiduyutv.ui.navigation.NavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme

class MainActivity : ComponentActivity(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Memory cache: 25% of app memory
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
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
                com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
