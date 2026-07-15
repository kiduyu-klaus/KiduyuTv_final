package com.kiduyuk.klausk.kiduyutv.application

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.FirebaseDatabase
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.network.AndroidApp
import com.kiduyuk.klausk.kiduyutv.network.NetworkConnectivityChecker
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds
import com.kiduyuk.klausk.kiduyutv.util.AppOpenAdObserver
import com.kiduyuk.klausk.kiduyutv.util.AuthManager
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.LogcatManager
import com.kiduyuk.klausk.kiduyutv.util.NotificationHelper
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager

/**
 * A Custom Application class for KiduyuTv.
 * This class handles app-wide initializations and provides a centralized
 * configuration for the Coil image loader.
 */
class KiduyuTvApp : MultiDexApplication(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database manager
        DatabaseManager.init(this)

        // Initialize MyListManager (now uses Room internally)
        MyListManager.init(this)

        // Initialize Ad Blocker (previously in PlayerActivity)
        //AdvancedAdBlocker.init(this)

        // Initialize notification channels
        NotificationHelper.createNotificationChannel(this)

        // Clean up expired cache on app start
        DatabaseManager.cleanExpiredCache()

        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this)

        // Initialize Firebase Realtime Database with persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Load Google ad unit IDs and test-ad flag from Firebase app_config.
        AdUnitIds.startFirebaseSync()

        // 1. Initialize AuthManager FIRST to restore persisted login from SharedPreferences
        // This ensures isSignedIn and currentUid are populated before Firebase services start
        Log.i("KiduyuTvApp", "Initializing AuthManager...")
        AuthManager.init(this, webClientId = "109926033937-dsl207opc1lsa3fnonim2sfmnc0o9hjk.apps.googleusercontent.com")

        // 2. Determine the correct user ID (authenticated UID or device ID)
        // CRITICAL: AuthManager.init() has already restored persisted login and updated StateFlows
        // We can now safely read isSignedIn.value which should reflect the persisted state
        val isSignedIn = AuthManager.isSignedIn.value
        val currentUid = AuthManager.currentUser?.uid ?: AuthManager.currentUid
        val deviceId = SettingsManager(this).getDeviceId()

        val userId = if (isSignedIn && currentUid != null) {
            Log.i("KiduyuTvApp", "User is signed in (persisted login restored). Using UID: $currentUid")
            currentUid
        } else {
            Log.i("KiduyuTvApp", "User not signed in. Using device ID: $deviceId")
            deviceId
        }

        // 3. Initialize FirebaseManager with the correct user ID
        FirebaseManager.init(userId)
        Log.i("KiduyuTvApp", "FirebaseManager initialized with userId: $userId")

        // Initialize Trakt auth state early so persisted sessions are available app-wide.
        TraktAuthManager.init(this)

        // Load stream provider configuration from Firebase, with local fallbacks.
        StreamProviderManager.startFirebaseSync()

        // Initialize AndroidApp reference for singleton access
        AndroidApp.instance = this

        // Ad SDKs are initialized from SplashActivity only after UMP consent resolves.
        AppOpenAdObserver.install(this)

        // Warm up the WebView process. Constructing (and shortly destroying) a hidden
        // WebView at app start pulls the chromium native libraries into memory so the
        // first playback in PlayerActivity does not pay the cold-start cost.
        warmUpWebViewProcess()

        // Register ActivityLifecycleCallback to track current Activity for dialog display
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {
                // Not needed for tracking
            }

            override fun onActivityStarted(activity: Activity) {
                // Not needed for tracking
            }

            override fun onActivityResumed(activity: Activity) {
                // Track the current resumed Activity for dialog display
                AndroidApp.setCurrentActivity(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                // Not needed for tracking
            }

            override fun onActivityStopped(activity: Activity) {
                // Clear the current Activity when app goes to background
                if (AndroidApp.getCurrentActivity() === activity) {
                    AndroidApp.setCurrentActivity(null)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {
                // Not needed for tracking
            }

            override fun onActivityDestroyed(activity: Activity) {
                // Clear if this was the current Activity
                if (AndroidApp.getCurrentActivity() === activity) {
                    AndroidApp.setCurrentActivity(null)
                }
            }
        })

        // Start network connectivity monitoring
        NetworkConnectivityChecker.startMonitoring(this)

        // Start logcat capture for debugging purposes
        LogcatManager.start(this)
    }

    override fun onTerminate() {
        super.onTerminate()

        // Stop network monitoring when app is terminated
        try {
            NetworkConnectivityChecker.stopMonitoring(this)
        } catch (e: Exception) {
            // Log warning
        }
    }

    /**
     * Builds a transient zero-size WebView on the main thread after a short delay so the
     * WebView native library is loaded once at process start. This is the cheapest way
     * to shave 200–600 ms off the first playback because it avoids paying the cost of
     * `WebView.<init>` (which dynamically loads libwebviewchromium) on the user's
     * "tap to play" gesture. The hidden view is destroyed after a few seconds; even
     * if the user opens a player before then, the warm WebView does not conflict with
     * the per-activity WebView.
     */
    private fun warmUpWebViewProcess() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val warm = WebView(applicationContext)
                warm.layoutParams = ViewGroup.LayoutParams(0, 0)
                warm.visibility = View.GONE
                warm.loadUrl("about:blank")
                Log.i("KiduyuTvApp", "[WebViewWarmup] Pre-loaded native libraries")

                // Release the warm WebView after a short grace period. Keeping it around
                // longer wastes memory; destroying it after the libraries are paged in is
                // enough to keep the process warm.
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { warm.destroy() }
                        .onFailure { Log.w("KiduyuTvApp", "[WebViewWarmup] destroy failed: ${it.message}") }
                }, WARMUP_HOLD_MS)
            } catch (e: Exception) {
                Log.w("KiduyuTvApp", "[WebViewWarmup] Failed: ${e.message}")
            }
        }, WARMUP_DELAY_MS)
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
                    .maxSizeBytes(30 * 1024 * 1024) // Reduced from 100MB to 30MB
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

    companion object {
        // Delay before kicking off the warm-up. Done slightly after onCreate so the splash
        // activity's first frame wins the main thread; the warm-up just needs the libraries
        // paged in before the first playback tap.
        private const val WARMUP_DELAY_MS = 1_500L
        // How long to keep the warm WebView alive before destroying it. Long enough that
        // typical "open app → tap to play" sequences still benefit, short enough to avoid
        // any memory pressure on low-end TVs.
        private const val WARMUP_HOLD_MS = 5_000L
    }
}
