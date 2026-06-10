package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiClient
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import com.kiduyuk.klausk.kiduyutv.util.WatchProgressTracker
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
    private var storedPlaybackPosition: Long = 0L
    private var isMediaInWatchHistory: Boolean = false
    private var lastTraktScrobbleProgress: Double = -1.0
    private var lastTraktScrobbleAt: Long = 0L
    private var lastTraktScrobbleSeason: Int = -1
    private var lastTraktScrobbleEpisode: Int = -1
    private var lastTraktScrobbleContentId: Int = -1

    private val repository = TmdbRepository()
    private val traktRepository by lazy {
        TraktRepository(
            TraktApiClient.apiService,
            TraktAuthManager.getInstance(this)
        )
    }

    companion object {
        private const val TAG = "VideasyPlayer"
        private const val JS_INTERFACE_NAME = "AndroidProgressCallback"
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
        val savedPosition = intent.getLongExtra("PLAYBACK_POSITION", 0L)

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
        storedPlaybackPosition = savedPosition

        // Check if media is already in watch history and add if not
        checkAndAddToWatchHistory()

        // Detect device type and show appropriate toast with device information
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val deviceModel = Build.MODEL
        val deviceBrand = Build.BRAND.replaceFirstChar { it.uppercase() }

        if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
            val deviceType = if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_NORMAL) "Mobile" else "Tablet"
            Log.i(TAG, "[Device] $deviceType detected (${deviceBrand} $deviceModel), disabling cursor")


        } else {
            isFireTV = isFireTVDevice(this)
            Log.i(TAG, "[Device] TV detected (${deviceBrand} $deviceModel), cursor enabled, isFireTV=$isFireTV")


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

            //setBackgroundColor(0x00000000)  transparent

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

            // Note: layer type is intentionally NOT set. We let WebView pick its
            // own rendering path (HW on capable devices, SW otherwise). Manually
            // setting LAYER_TYPE_NONE/HARDWARE/SOFTWARE on Fire TV's WebView has
            // been a source of black-screen bugs, so we trust the platform here.

            // Add JavaScript interface for receiving progress updates from the player
            addJavascriptInterface(WebAppInterface(), JS_INTERFACE_NAME)

            webViewClient = AdBlockerWebViewClient(
                onPageFinished = {
                    isPageLoading = false
                    Log.i(TAG, "[WebView] Page finished loading with AdBlocker")
                    // Inject progress tracking script after page loads
                    injectProgressTrackingScript()
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
            // In updateCursorPosition(), add this:
            cursorView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
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

    // fixAWVSurfaceViewZOrder removed: manual z-order tweaking caused black-screen
    // issues on Fire TV. WebView's own compositing path is now trusted.

    /**
     * JavaScript interface for receiving progress updates from the WebView player.
     * The injected JavaScript calls this method when progress data is available.
     */
    inner class WebAppInterface {
        @android.webkit.JavascriptInterface
        fun onProgressUpdate(jsonData: String) {
            Log.d(TAG, "[JSInterface] Received progress update: $jsonData")

            val progressData = WatchProgressTracker.parseProgressData(jsonData)
            if (progressData != null) {
                handleProgressUpdate(progressData)
            }
        }
    }

    /**
     * Injects the unified progress tracking script into the WebView after page loads.
     * This script listens to postMessage events from all supported streaming platforms.
     */
    private fun injectProgressTrackingScript() {
        val listenerScript = WatchProgressTracker.generateUnifiedListenerScript()
        webView.evaluateJavascript(listenerScript) { result ->
            Log.i(TAG, "[ProgressTracker] Listener injection result: $result")
        }

        // Start polling immediately — generateStartPollingScript waits for
        // DOMContentLoaded internally if the document isn't ready yet, so no
        // arbitrary fixed delay is needed here.
        val startPollingScript = WatchProgressTracker.generateStartPollingScript(currentTmdbId, currentIsTv)
        webView.evaluateJavascript(startPollingScript) { result ->
            Log.i(TAG, "[ProgressTracker] Start polling result: $result")
        }
    }

    /**
     * Handles progress updates received from the WebView player.
     *
     * For TV Shows:
     * - Compares the received season/episode with current values
     * - If season/episode match: only updates the playback position
     * - If season/episode changed: updates both episode info AND playback position
     *
     * For Movies:
     * - Just updates the playback position
     */
    private fun handleProgressUpdate(progressData: WatchProgressTracker.ProgressData) {
        Log.d(TAG, "[Progress] Received update: pos=${progressData.currentPosition}ms, " +
                "duration=${progressData.duration}ms, season=${progressData.season}, ep=${progressData.episode}")

        if (currentTmdbId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (currentIsTv) {
                    // TV Show handling with season/episode comparison
                    val newSeason = progressData.season ?: currentSeason
                    val newEpisode = progressData.episode ?: currentEpisode

                    // Check if season/episode has changed
                    if (newSeason != currentSeason || newEpisode != currentEpisode) {
                        Log.i(TAG, "[Progress] Episode changed: S${currentSeason}E${currentEpisode} -> S${newSeason}E${newEpisode}")

                        // Update stored season/episode
                        currentSeason = newSeason
                        currentEpisode = newEpisode

                        // Update watch history with new episode info AND progress
                        repository.updateEpisodeInfo(
                            mediaId = currentTmdbId,
                            mediaType = "tv",
                            seasonNumber = currentSeason,
                            episodeNumber = currentEpisode
                        )
                        repository.updatePlaybackPosition(
                            mediaId = currentTmdbId,
                            mediaType = "tv",
                            position = progressData.currentPosition
                        )

                        Log.d(TAG, "[Progress] Updated episode info and progress for $currentTitle")
                    } else {
                        // Same episode - just update progress
                        repository.updatePlaybackPosition(
                            mediaId = currentTmdbId,
                            mediaType = "tv",
                            position = progressData.currentPosition
                        )

                        Log.d(TAG, "[Progress] Updated progress for S${currentSeason}E${currentEpisode}: ${progressData.currentPosition}ms")
                    }
                } else {
                    // Movie handling - just update progress
                    repository.updatePlaybackPosition(
                        mediaId = currentTmdbId,
                        mediaType = "movie",
                        position = progressData.currentPosition
                    )

                    Log.d(TAG, "[Progress] Updated movie progress for $currentTitle: ${progressData.currentPosition}ms")
                }

                // Sync to Firebase for cross-device access
                syncToFirebase(progressData.currentPosition)

                // Sync to Trakt.tv when the user has an active session.
                syncToTrakt(progressData)

            } catch (e: Exception) {
                Log.e(TAG, "[Progress] Error handling progress update: ${e.message}")
            }
        }
    }

    /**
     * Syncs watch progress to Firebase for cross-device access.
     */
    private fun syncToFirebase(position: Long) {
        com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
            tmdbId = currentTmdbId,
            isTv = currentIsTv,
            seasonNumber = if (currentIsTv) currentSeason else null,
            episodeNumber = if (currentIsTv) currentEpisode else null,
            playbackPosition = position,
            duration = 0L,
            title = currentTitle,
            overview = currentOverview,
            posterPath = currentPosterPath,
            backdropPath = currentBackdropPath,
            voteAverage = currentVoteAverage,
            releaseDate = currentReleaseDate
        )
    }

    /**
     * Throttles Trakt scrobble updates so we only send meaningful changes.
     */
    private suspend fun syncToTrakt(progressData: WatchProgressTracker.ProgressData) {
        if (!TraktAuthManager.isTraktAuthenticated.value) return

        val progressPercent = progressData.progressPercent.coerceIn(0.0, 100.0)
        if (progressPercent <= 0.0) return

        if (progressData.contentType == "tv") {
            val season = progressData.season ?: return
            val episode = progressData.episode ?: return

            val targetChanged = currentTmdbId != lastTraktScrobbleContentId ||
                season != lastTraktScrobbleSeason ||
                episode != lastTraktScrobbleEpisode

            if (targetChanged) {
                lastTraktScrobbleProgress = -1.0
                lastTraktScrobbleAt = 0L
            }

            if (!shouldSendTraktUpdate(progressPercent)) return

            val result = traktRepository.scrobbleEpisode(
                tmdbId = currentTmdbId,
                season = season,
                episode = episode,
                progress = progressPercent.toFloat(),
                status = traktStatusFor(progressPercent)
            )

            if (result.isSuccess) {
                rememberTraktScrobble(progressPercent, season, episode)
            }
        } else {
            val targetChanged = currentTmdbId != lastTraktScrobbleContentId

            if (targetChanged) {
                lastTraktScrobbleProgress = -1.0
                lastTraktScrobbleAt = 0L
            }

            if (!shouldSendTraktUpdate(progressPercent)) return

            val result = traktRepository.scrobbleMovie(
                tmdbId = currentTmdbId,
                progress = progressPercent.toFloat(),
                status = traktStatusFor(progressPercent)
            )

            if (result.isSuccess) {
                rememberTraktScrobble(progressPercent)
            }
        }
    }

    private fun shouldSendTraktUpdate(progressPercent: Double): Boolean {
        val now = SystemClock.elapsedRealtime()
        val percentDelta = kotlin.math.abs(progressPercent - lastTraktScrobbleProgress)
        val timeDelta = now - lastTraktScrobbleAt

        return when {
            lastTraktScrobbleProgress < 0.0 -> true
            progressPercent >= 90.0 && lastTraktScrobbleProgress < 90.0 -> true
            percentDelta >= 10.0 -> true
            timeDelta >= 60_000L -> true
            else -> false
        }
    }

    private fun traktStatusFor(progressPercent: Double): String {
        return when {
            progressPercent >= 90.0 -> "stop"
            lastTraktScrobbleProgress < 0.0 -> "start"
            else -> "pause"
        }
    }

    private fun rememberTraktScrobble(
        progressPercent: Double,
        season: Int? = null,
        episode: Int? = null
    ) {
        lastTraktScrobbleProgress = progressPercent
        lastTraktScrobbleAt = SystemClock.elapsedRealtime()
        lastTraktScrobbleContentId = currentTmdbId
        lastTraktScrobbleSeason = season ?: -1
        lastTraktScrobbleEpisode = episode ?: -1
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
    }

    override fun onDestroy() {
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
     * - Applies hardware or software rendering based on device capabilities
     *
     * For non-Fire TV devices:
     * - Uses standard WebView with hardware acceleration when available
     */
    private fun createWebView(context: Context): WebView {
        val webView = WebView(context)

        val isHardwareAccelerated =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

        if (isFireTV) {
            Log.i(TAG, "[WebView] Fire TV detected")
            Log.i(TAG, "[WebView] Hardware acceleration available: $isHardwareAccelerated")

            // No setLayerType call — let WebView choose its own rendering path.
            // Opaque black background so the video surface composites on top
            // of a known-good base layer on Fire TV's WebView.
            webView.setBackgroundColor(android.graphics.Color.BLACK)
        } else {
            Log.i(TAG, "[WebView] Non-Fire TV device")
            Log.i(TAG, "[WebView] Hardware acceleration available: $isHardwareAccelerated")

            // Same here: no setLayerType — trust the platform default.
            webView.setBackgroundColor(android.graphics.Color.BLACK)
        }

        logWebViewInfo(webView)

        return webView
    }

    /**
     * Logs comprehensive information about the current WebView implementation
     * for debugging purposes. Includes package name, version, and timestamps.
     * Only available on API 26+.
     */
    private fun logWebViewInfo(webView: WebView) {
        try {
            val webViewClass = webView.javaClass.name
            Log.i(TAG, "[WebView] Implementation class: $webViewClass")

            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(webView.context)

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

                when {
                    packageName == "com.amazon.webview.chromium" ->
                        Log.i(TAG, "[WebView] ✅ Amazon Chromium WebView - optimized for Fire TV/Fire tablets")
                    packageName == "com.google.android.webview" ->
                        Log.i(TAG, "[WebView] ℹ️ Google WebView - standard AOSP implementation")
                    packageName == "com.android.chrome" ->
                        Log.i(TAG, "[WebView] ℹ️ Chrome WebView - using Chrome as WebView provider")
                    packageName.contains("google") ->
                        Log.i(TAG, "[WebView] ℹ️ Google-based WebView: $packageName")
                    packageName.contains("android") ->
                        Log.i(TAG, "[WebView] ℹ️ AOSP-based WebView: $packageName")
                    else ->
                        Log.i(TAG, "[WebView] ℹ️ Custom/Unknown WebView provider: $packageName")
                }
            } else {
                Log.w(TAG, "[WebView] Could not determine WebView package (getCurrentWebViewPackage returned null)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Error logging WebView implementation details: ${e.message}", e)
        }
    }

    /**
     * Check if the device is an Amazon Fire TV or Fire Stick.
     * Uses two methods for maximum compatibility:
     * 1. Checks for amazon.hardware.fire_TV system feature
     * 2. Falls back to checking if Build. MODEL starts with "AFT"
     */
    private fun isFireTVDevice(context: Context): Boolean {
        val isFireTvHardware = context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        val isFireTvModel = Build.MODEL != null && Build.MODEL.startsWith("AFT", ignoreCase = true)
        return isFireTvHardware || isFireTvModel
    }

    private fun detectProviderFromUrl(url: String): String {
        val urlHost = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            Log.i(TAG, "[Provider] Error parsing URL: ${e.message}")
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
        lifecycleScope.launch(Dispatchers.IO) {
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
                        playbackPosition = storedPlaybackPosition
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
                        playbackPosition = storedPlaybackPosition,
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

                    // Restore saved playback position from watch history
                    val savedPosition = repository.getPlaybackPosition(
                        this@PlayerActivity,
                        currentTmdbId,
                        currentIsTv
                    )
                    if (savedPosition > 0) {
                        storedPlaybackPosition = savedPosition
                        Log.i(TAG, "[WatchHistory] Restored position: $savedPosition ms")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error checking/adding to watch history: ${e.message}")
            }
        }
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
