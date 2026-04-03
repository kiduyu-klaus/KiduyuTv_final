package com.kiduyuk.klausk.kiduyutv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.FirebaseDatabase

/**
 * Custom Application class for KiduyuTv.
 * This class handles app-wide initializations and provides a centralized
 * configuration for the Coil image loader.
 */
class KiduyuTvApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database manager
        DatabaseManager.init(this)

        // Initialize MyListManager (now uses Room internally)
        MyListManager.init(this)

        // Initialize Ad Blocker (previously in PlayerActivity)
        AdvancedAdBlocker.init(this)

        // Clean up expired cache on app start
        DatabaseManager.cleanExpiredCache()

        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this)

        // Initialize Firebase Realtime Database with persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }

    /**
     * Provides a singleton ImageLoader instance for the entire application.
     * This configuration is moved from MainActivity to ensure consistency
     * and better resource management.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Memory cache: 15% of app memory, capped at 50MB
            .memoryCache {
                val maxMemory = Runtime.getRuntime().maxMemory()
                val cacheSize = minOf(
                    (maxMemory * 0.15).toLong(),
                    50 * 1024 * 1024L
                )
                MemoryCache.Builder(this)
                    .maxSizeBytes(cacheSize.toInt())
                    .build()
            }
            // Disk cache: 100MB
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024)
                    .build()
            }
            // Network cache with OkHttp integration
            .okHttpClient {
                ApiClient.createOkHttpClient(this@KiduyuTvApp)
            }
            .crossfade(true)
            .respectCacheHeaders(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .logger(DebugLogger())
            .build()
    }
}
