package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.app.UiModeManager
import android.content.Context
import android.widget.Toast
import android.content.res.Configuration
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cursorView: MouseCursorView
    private lateinit var rootLayout: FrameLayout
    private var cursorX = 0f
    private var cursorY = 0f
    private val moveSpeed = 50f
    private var screenWidth = 0
    private var screenHeight = 0

    private var currentSeason = 1
    private var currentEpisode = 1
    private var isCursorDisabled = false
    private var isFireTV = false
    private var currentProviderName: String = "VidLink"

    // Loading and error state for AdBlockerWebViewClient
    private var isPageLoading = true
    private var hasPageError = false

    // Watch history tracking variables
    private var currentTmdbId: Int = -1
    private var currentIsTv: Boolean = false
    private var currentTitle: String = "Unknown"
    private var currentOverview: String? = null
    private var currentPosterPath: String? = null
    private var currentBackdropPath: String? = null
    private var currentVoteAverage: Double = 0.0
    private var currentReleaseDate: String? = null
    private var currentPlaybackPosition: Long = 0L
    private var isMediaInWatchHistory: Boolean = false

    // 15-second progress update handler
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = Runnable {
        updateWatchProgress()
    }
    private val repository = TmdbRepository()

    /**
     * Check if the device is an Amazon Fire TV or Fire Stick.
     * Uses two methods for maximum compatibility:
     * 1. Checks for amazon.hardware.fire_tv system feature
     * 2. Falls back to checking if Build.MODEL starts with "AFT"
     */
    private fun isFireTVDevice(context: Context): Boolean {
        val isFireTvHardware = context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        val isFireTvModel = Build.MODEL != null && Build.MODEL.startsWith("AFT", ignoreCase = true)
        return isFireTvHardware || isFireTvModel
    }

    companion object {
        private const val TAG = "VideasyPlayer"
    }

    // ── Cursor hide timer ──────────────────────────────────────────────────────
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled) {
            cursorView.animate().alpha(0f).setDuration(500).start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        currentSeason = intent.getIntExtra("SEASON_NUMBER", 1)
        currentEpisode = intent.getIntExtra("EPISODE_NUMBER", 1)

        // Intent extras still read (available for future use)
        val contentTitle = intent.getStringExtra("TITLE") ?: "Unknown"
        val contentOverview = intent.getStringExtra("OVERVIEW")
        val contentPosterPath = intent.getStringExtra("POSTER_PATH")
        val contentBackdropPath = intent.getStringExtra("BACKDROP_PATH")
        val contentVoteAverage = intent.getDoubleExtra("VOTE_AVERAGE", 0.0)
        val contentReleaseDate = intent.getStringExtra("RELEASE_DATE")

        if (tmdbId == -1) {
            finish()
            return
        }

        // Store media info for watch history tracking
        currentTmdbId = tmdbId
        currentIsTv = isTv
        currentTitle = contentTitle
        currentOverview = contentOverview
        currentPosterPath = contentPosterPath
        currentBackdropPath = contentBackdropPath
        currentVoteAverage = contentVoteAverage
        currentReleaseDate = contentReleaseDate

        // Check if media is already in watch history and add if not
        checkAndAddToWatchHistory()

        // Start 15-second progress update timer
        startProgressUpdateTimer()

        // Detect device type and show appropriate toast with device information
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val deviceModel = Build.MODEL
        val deviceBrand = Build.BRAND.replaceFirstChar { it.uppercase() }

        if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
            val deviceType = if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_NORMAL) "Mobile" else "Tablet"
            Log.i(TAG, "[Device] $deviceType detected (${deviceBrand} $deviceModel), disabling cursor")

            val toastMessage = "Device: $deviceType | Brand: $deviceBrand | Model: $deviceModel"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@PlayerActivity, toastMessage, Toast.LENGTH_LONG).show()
            }
        } else {
            isFireTV = isFireTVDevice(this)
            Log.i(TAG, "[Device] TV detected (${deviceBrand} $deviceModel), cursor enabled, isFireTV=$isFireTV")

            val deviceLabel = if (isFireTV) "Fire TV (Amazon WebView)" else "Android TV (WebView)"
            val toastMessage = "Device: $deviceLabel | Brand: $deviceBrand | Model: $deviceModel"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@PlayerActivity, toastMessage, Toast.LENGTH_LONG).show()
            }
        }

        val url = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$currentSeason/$currentEpisode?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        // Detect provider using StreamProviderManager
        currentProviderName = detectProviderFromUrl(url)
        Log.i(TAG, "[Provider] Selected: $currentProviderName")

        // ── Layout ────────────────────────────────────────────────────────────
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = createWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            setBackgroundColor(0xFF000000.toInt())

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = if (isFireTV) {
                    "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                } else {
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
            }

            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
            overScrollMode = View.OVER_SCROLL_NEVER

            if (isCursorDisabled) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_NONE, null)
            }

            webViewClient = AdBlockerWebViewClient(
                onPageFinished = {
                    isPageLoading = false
                    Log.i(TAG, "[WebView] Page finished loading with AdBlocker")
                },
                onError = {
                    hasPageError = true
                    isPageLoading = false
                    Log.e(TAG, "[WebView] Error received with AdBlocker")
                }
            )

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    Log.i(TAG, "[WebChrome] onShowCustomView called")
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    Log.i(TAG, "[WebChrome] onCreateWindow called, blocking popups")
                    return false
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    Log.d(TAG, "[WebChrome] Load progress: $newProgress%")
                }
            }

            val iframeHtml = intent.getStringExtra("IFRAME_HTML")
            if (iframeHtml != null) {
                Toast.makeText(this@PlayerActivity, "Loading via IFRAME mode", Toast.LENGTH_SHORT).show()
                val baseUrl = StreamProviderManager.getBaseUrl(currentProviderName)
                loadDataWithBaseURL(baseUrl, iframeHtml, "text/html", "UTF-8", null)
            } else {
                Toast.makeText(this@PlayerActivity, "Loading via DIRECT URL mode", Toast.LENGTH_SHORT).show()
                loadUrl(url)
            }
        }

        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(webView)
        if (!isCursorDisabled) {
            rootLayout.addView(cursorView)
            cursorView.bringToFront()
        }

        setContentView(rootLayout)
        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        rootLayout.post {
            screenWidth = rootLayout.width
            screenHeight = rootLayout.height
            if (!isCursorDisabled) {
                cursorX = screenWidth / 2f
                cursorY = screenHeight / 2f
                updateCursorPosition()
                showCursorAndResetTimer()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Stop Playback?",
            message = "Are you sure you want to stop playback and exit?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { },
            onYes = { finish() }
        ).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        // Stop progress updates when player is paused
        stopProgressUpdateTimer()
    }

    override fun onDestroy() {
        // Stop progress updates when player is destroyed
        stopProgressUpdateTimer()
        cursorHideHandler.removeCallbacks(cursorHideRunnable)

        if (::webView.isInitialized) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
                    stopLoading()
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    clearHistory()
                    clearCache(true)
                    loadUrl("about:blank")
                    onPause()
                    removeAllViews()
                    destroy()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during WebView cleanup: ${e.message}")
            }
        }
        super.onDestroy()
    }

    // ── D-pad input ───────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isCursorDisabled) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> return onKeyDown(event.keyCode, event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                showCursorAndResetTimer()
                cursorY = (cursorY - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showCursorAndResetTimer()
                cursorY = (cursorY + moveSpeed).coerceAtMost(screenHeight.toFloat())
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                showCursorAndResetTimer()
                cursorX = (cursorX - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showCursorAndResetTimer()
                cursorX = (cursorX + moveSpeed).coerceAtMost(screenWidth.toFloat())
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showCursorAndResetTimer()
                simulateClick(cursorX, cursorY)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates and configures a WebView instance optimized for the current device.
     *
     * For Fire TV devices:
     * - Detects if Amazon Chromium WebView (com.amazon.webview.chromium) is available
     * - Applies hardware or software rendering based on device capabilities
     *
     * For non-Fire TV devices:
     * - Uses standard WebView with hardware acceleration when available
     */
    private fun createWebView(context: Context): WebView {
        val webView = WebView(context)

        // Check if hardware acceleration is available
        val isHardwareAccelerated =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

        if (isFireTV) {
            val isAmazonChromium = isAmazonChromiumAvailable()

            Log.i(TAG, "[WebView] Fire TV detected")
            Log.i(TAG, "[WebView] Amazon Chromium WebView: $isAmazonChromium")
            Log.i(TAG, "[WebView] Hardware acceleration available: $isHardwareAccelerated")

            if (isHardwareAccelerated) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                Log.i(TAG, "[WebView] Fire TV: hardware acceleration enabled")
            } else {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.w(TAG, "[WebView] Fire TV: hardware acceleration unavailable, using software rendering")
            }

            if (isAmazonChromium) {
                Log.i(TAG, "[WebView] ✅ Running on Amazon Chromium WebView (com.amazon.webview.chromium)")
            } else {
                Log.w(TAG, "[WebView] ⚠️ Amazon Chromium WebView not available, using fallback WebView")
            }
        } else {
            Log.i(TAG, "[WebView] Non-Fire TV device")
            Log.i(TAG, "[WebView] Hardware acceleration available: $isHardwareAccelerated")

            if (isHardwareAccelerated) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                Log.i(TAG, "[WebView] Hardware acceleration enabled")
            } else {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.w(TAG, "[WebView] Hardware acceleration unavailable, using software rendering")
            }
        }

        // Log detailed WebView implementation info for debugging
        logWebViewInfo(webView)

        return webView
    }

    /**
     * Checks if Amazon's Chromium-based WebView is available on this device.
     *
     * Amazon's WebView (com.amazon.webview.chromium) is the optimized WebView
     * implementation that ships with Fire TV and Fire tablet devices. It provides
     * better performance and hardware integration compared to the standard AOSP WebView.
     *
     * Uses the official Android API WebView.getCurrentWebViewPackage() which is
     * available from API 26 (Android 8.0) onwards.
     *
     * @return true if Amazon Chromium WebView is the current WebView provider
     */
    private fun isAmazonChromiumAvailable(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return try {
                val webViewPackage = WebView.getCurrentWebViewPackage()
                val isAmazon = webViewPackage?.packageName == "com.amazon.webview.chromium"

                if (isAmazon) {
                    Log.d(TAG, "[WebView] Amazon Chromium WebView package detected")
                } else {
                    Log.d(TAG, "[WebView] Current WebView package: ${webViewPackage?.packageName ?: "unknown"}")
                }

                isAmazon
            } catch (e: Exception) {
                Log.w(TAG, "[WebView] Error checking WebView package: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "[WebView] SDK version ${android.os.Build.VERSION.SDK_INT} < 26, cannot detect WebView package")
            return false
        }
    }

    /**
     * Logs comprehensive information about the current WebView implementation.
     *
     * This is useful for debugging WebView-related issues and verifying which
     * WebView engine is actually being used at runtime. It logs:
     * - Implementation class name
     * - Package name
     * - Version information
     * - First install and last update timestamps
     * - Whether it's Amazon's Chromium WebView or another provider
     *
     * Note: Detailed package info requires API 26+. On older versions,
     * only the implementation class name is logged.
     */
    private fun logWebViewInfo(webView: WebView) {
        try {
            val webViewClass = webView.javaClass.name
            Log.i(TAG, "[WebView] Implementation class: $webViewClass")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val webViewPackage = WebView.getCurrentWebViewPackage()

                if (webViewPackage != null) {
                    val packageName = webViewPackage.packageName
                    val versionName = webViewPackage.versionName ?: "unknown"
                    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        webViewPackage.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        webViewPackage.versionCode.toString()
                    }

                    Log.i(TAG, "[WebView] Package: $packageName")
                    Log.i(TAG, "[WebView] Version: $versionName (code: $versionCode)")
                    Log.i(TAG, "[WebView] First installed: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(webViewPackage.firstInstallTime))}")
                    Log.i(TAG, "[WebView] Last updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(webViewPackage.lastUpdateTime))}")

                    // Identify the WebView provider
                    when {
                        packageName == "com.amazon.webview.chromium" -> {
                            Log.i(TAG, "[WebView] ✅ Amazon Chromium WebView - optimized for Fire TV/Fire tablets")
                            Log.i(TAG, "[WebView] → Hardware-accelerated video playback supported")
                            Log.i(TAG, "[WebView] → Amazon-specific optimizations active")
                        }
                        packageName == "com.google.android.webview" -> {
                            Log.i(TAG, "[WebView] ℹ️ Google WebView - standard AOSP implementation")
                        }
                        packageName == "com.android.chrome" -> {
                            Log.i(TAG, "[WebView] ℹ️ Chrome WebView - using Chrome as WebView provider")
                        }
                        packageName.contains("google") -> {
                            Log.i(TAG, "[WebView] ℹ️ Google-based WebView: $packageName")
                        }
                        packageName.contains("android") -> {
                            Log.i(TAG, "[WebView] ℹ️ AOSP-based WebView: $packageName")
                        }
                        else -> {
                            Log.i(TAG, "[WebView] ℹ️ Custom/Unknown WebView provider: $packageName")
                        }
                    }
                } else {
                    Log.w(TAG, "[WebView] Could not determine WebView package (getCurrentWebViewPackage returned null)")
                    Log.w(TAG, "[WebView] This may indicate a custom ROM or modified Android system")
                }
            } else {
                Log.w(TAG, "[WebView] SDK version ${android.os.Build.VERSION.SDK_INT} < 26, limited WebView info available")
                Log.i(TAG, "[WebView] Implementation class: $webViewClass")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Error logging WebView implementation details: ${e.message}", e)
        }
    }

    private fun detectProviderFromUrl(url: String): String {
        val urlHost = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            return "VidLink"
        }

        StreamProviderManager.providers.forEach { provider ->
            try {
                val providerBaseUrl = StreamProviderManager.getBaseUrl(provider.name)
                    .lowercase()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removeSuffix("/")

                if (urlHost.contains(providerBaseUrl) || urlHost.endsWith(".$providerBaseUrl")) {
                    return provider.name
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return "VidLink"
    }

    private fun updateCursorPosition() {
        if (isCursorDisabled) return
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.bringToFront()
        cursorView.invalidate()
    }

    private fun simulateClick(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = MotionEvent.obtain(downTime, eventTime + 100, MotionEvent.ACTION_UP, x, y, 0)

        downEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN

        window.decorView.dispatchTouchEvent(downEvent)
        window.decorView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    private fun showCursorAndResetTimer() {
        if (isCursorDisabled) return
        cursorView.animate().cancel()
        cursorView.alpha = 1f
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }

    // ── Watch History Management ─────────────────────────────────────────────

    /**
     * Checks if the media is already in watch history and adds it if not.
     * Also syncs the new item to Firebase for cross-device access.
     */
    private fun checkAndAddToWatchHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if media already exists in watch history
                isMediaInWatchHistory = repository.isInWatchHistory(
                    this@PlayerActivity,
                    currentTmdbId,
                    currentIsTv
                )

                if (!isMediaInWatchHistory) {
                    // Add new item to watch history
                    val watchHistoryItem = WatchHistoryItem(
                        id = currentTmdbId,
                        title = currentTitle,
                        overview = currentOverview,
                        posterPath = currentPosterPath,
                        backdropPath = currentBackdropPath,
                        voteAverage = currentVoteAverage,
                        releaseDate = currentReleaseDate,
                        isTv = currentIsTv,
                        seasonNumber = if (currentIsTv) currentSeason else null,
                        episodeNumber = if (currentIsTv) currentEpisode else null,
                        lastWatched = System.currentTimeMillis(),
                        playbackPosition = 0L
                    )

                    // Save to local Room database
                    repository.saveToWatchHistory(this@PlayerActivity, watchHistoryItem)

                    Log.i(TAG, "[WatchHistory] Added to local database: $currentTitle (ID: $currentTmdbId)")

                    // Sync to Firebase for cross-device access
                    com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                        tmdbId = currentTmdbId,
                        isTv = currentIsTv,
                        seasonNumber = if (currentIsTv) currentSeason else null,
                        episodeNumber = if (currentIsTv) currentEpisode else null,
                        playbackPosition = 0L,
                        duration = 0L,
                        title = currentTitle,
                        overview = currentOverview,
                        posterPath = currentPosterPath,
                        backdropPath = currentBackdropPath,
                        voteAverage = currentVoteAverage,
                        releaseDate = currentReleaseDate
                    )

                    Log.i(TAG, "[WatchHistory] Synced to Firebase: $currentTitle (ID: $currentTmdbId)")
                } else {
                    Log.i(TAG, "[WatchHistory] Media already in watch history: $currentTitle (ID: $currentTmdbId)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error checking/adding to watch history: ${e.message}")
            }
        }
    }

    /**
     * Starts the 15-second periodic progress update timer.
     * Updates watch progress in both local database and Firebase.
     */
    private fun startProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 15000) // 15 seconds
    }

    /**
     * Updates the watch progress for the current media.
     * Called every 15 seconds while playback is active.
     * Updates both local Room database and Firebase for cross-device sync.
     */
    private fun updateWatchProgress() {
        if (currentTmdbId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Increment playback position (simulated for WebView - in a real implementation,
                // this would be obtained from the video player's actual position)
                currentPlaybackPosition += 15000 // 15 seconds in milliseconds

                // Update local Room database
                repository.updatePlaybackPosition(
                    mediaId = currentTmdbId,
                    mediaType = if (currentIsTv) "tv" else "movie",
                    position = currentPlaybackPosition
                )

                // If this is a TV show, also update episode info
                if (currentIsTv) {
                    repository.updateEpisodeInfo(
                        mediaId = currentTmdbId,
                        mediaType = "tv",
                        seasonNumber = currentSeason,
                        episodeNumber = currentEpisode
                    )
                }

                Log.d(TAG, "[WatchHistory] Updated progress for $currentTitle: $currentPlaybackPosition ms")

                // Sync to Firebase for cross-device access
                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                    tmdbId = currentTmdbId,
                    isTv = currentIsTv,
                    seasonNumber = if (currentIsTv) currentSeason else null,
                    episodeNumber = if (currentIsTv) currentEpisode else null,
                    playbackPosition = currentPlaybackPosition,
                    duration = 0L, // Duration would need to be obtained from the player
                    title = currentTitle,
                    overview = currentOverview,
                    posterPath = currentPosterPath,
                    backdropPath = currentBackdropPath,
                    voteAverage = currentVoteAverage,
                    releaseDate = currentReleaseDate
                )

                Log.d(TAG, "[WatchHistory] Synced progress to Firebase for $currentTitle")
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error updating progress: ${e.message}")
            }
        }

        // Schedule next update
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 15000)
    }

    /**
     * Stops the progress update timer when leaving the player.
     */
    private fun stopProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }
}

/**
 * AdBlockerWebViewClient - Handles ad blocking and page lifecycle events
 */
private class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit
) : WebViewClient() {

    private val adDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "advertising.com", "adsystem.com", "adserver.com",
        "rubiconproject.com", "openx.net", "pubmatic.com", "criteo.com",
        "moatads.com", "taboola.com", "outbrain.com", "adroll.com",
        "imrworldwide.com", "comscore.com", "quantserve.com",
        "popads.net", "popcash.net", "propellerads.com", "ad-maven.com",
        "onclickads.net", "adsterra.com", "exo-click.com", "juicyads.com",
        "trafficjunky.net", "exoclick.com", "mc.yandex.ru", "creativecdn.com",
        "serving-sys.com", "ads.yahoo.com", "contextweb.com",
        "adtechtraffic.com", "bet365.com", "1xbet.com", "cloud.mail.ru"
    )

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString()?.lowercase() ?: return null

        if (adDomains.any { url.contains(it) }) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()

        view?.evaluateJavascript(
            """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay { display: none !important; }';
                document.head.appendChild(style);

                var ads = document.querySelectorAll('div[id^="ad"], div[class^="ad"], iframe[src*="doubleclick"], iframe[src*="google"]');
                ads.forEach(function(ad) { ad.remove(); });
            })();
            """.trimIndent(), null
        )
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            onError()
        }
    }
}
