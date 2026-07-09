package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
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
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // FIX: Loading overlay UI to give the user feedback while the iframe loads,
    // and to surface errors instead of leaving a silent black screen.
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingStatusText: TextView
    private lateinit var errorOverlay: FrameLayout
    private lateinit var errorText: TextView

    // FIX: Stall detector — if onPageFinished doesn't fire within 30s, treat as failure
    // and offer the user a fallback UI.
    private val stallTimeoutHandler = Handler(Looper.getMainLooper())
    private val stallTimeoutRunnable = Runnable {
        if (isPageLoading && !hasPageError) {
            Log.w(TAG, "[WebView] Page load stalled after 30s — showing fallback UI")
            hasPageError = true
            showErrorOverlay("Playback is taking too long. The stream may be blocked or unavailable.\n\nTap to retry.")
        }
    }
    private val STALL_TIMEOUT_MS = 30_000L

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
    private var currentDuration: Long = 0L

    // 15-second progress update handler
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = Runnable {
        updateWatchProgress()
    }
    private val repository = TmdbRepository()

    /**
     * Check if the device is an Amazon Fire TV or Fire Stick.
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

        // Fix: Missing Translucent Window Format
        // Fire TV's window manager often requires the Activity's window to be explicitly set
        // to a translucent format to correctly composite the video surface with the WebView UI.
        window.setFormat(PixelFormat.TRANSLUCENT)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        currentSeason = intent.getIntExtra("SEASON_NUMBER", 1)
        currentEpisode = intent.getIntExtra("EPISODE_NUMBER", 1)

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

        currentTmdbId = tmdbId
        currentIsTv = isTv
        currentTitle = contentTitle
        currentOverview = contentOverview
        currentPosterPath = contentPosterPath
        currentBackdropPath = contentBackdropPath
        currentVoteAverage = contentVoteAverage
        currentReleaseDate = contentReleaseDate

        // Check and add to watch history, timer will be started after check completes
        checkAndAddToWatchHistory()

        val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as UiModeManager
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

        val url: String = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$currentSeason/$currentEpisode?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        currentProviderName = WebViewUtils.detectProviderFromUrl(url)
        Log.i(TAG, "[Provider] Selected: $currentProviderName")

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── FIX: Loading overlay — shown by default, hidden when the iframe finishes loading ──
        loadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt()) // semi-transparent black
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        loadingProgressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        loadingStatusText = TextView(this).apply {
            text = "Loading…"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(0, 32, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ).apply {
                topMargin = (24 * resources.displayMetrics.density).toInt()
            }
        }
        loadingOverlay.addView(loadingProgressBar)
        loadingOverlay.addView(loadingStatusText)
        // NOTE: loadingOverlay is added to rootLayout AFTER the WebView (below)
        // so it stays on top of the WebView and remains visible while the iframe loads.

        // ── FIX: Error overlay — shown when the iframe fails to load ──
        // (created here; will be added to rootLayout AFTER the WebView so it stays on top)
        errorOverlay = FrameLayout(this).apply {
            setBackgroundColor(0xE6000000.toInt())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        errorText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        errorOverlay.addView(errorText)
        // Tap to retry: dismiss the error and reload the iframe
        errorOverlay.setOnClickListener {
            Log.i(TAG, "[WebView] User tapped error overlay — reloading")
            hasPageError = false
            isPageLoading = true
            errorOverlay.visibility = View.GONE
            loadingOverlay.visibility = View.VISIBLE
            loadingStatusText.text = "Retrying…"
            stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)
            stallTimeoutHandler.postDelayed(stallTimeoutRunnable, STALL_TIMEOUT_MS)
            webView.reload()
        }

        webView = WebViewUtils.createWebView(this, isFireTV).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Fix: Solid Background Color Overlapping Video
            // On many Fire OS versions, the video is rendered on a SurfaceView that sits behind
            // the WebView's main drawing layer. Setting a solid black background hides the video.
            setBackgroundColor(0x00000000) // Set to transparent

            // Fix: Amazon Chromium WebView vs. System WebView
            // Enable debugging for Amazon Chromium WebView optimizations
            // if (isFireTV) {
            //     WebView.setWebContentsDebuggingEnabled(true)
            // }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                

                // Fix: Media Playback User Gesture Restriction
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true

                // Viewport scaling
                loadWithOverviewMode = true
                useWideViewPort = true

                // FIX: Changed zoom constraints to allow video players to properly resize video layouts.
                // We disable visual buttons (displayZoomControls) so it stays clean.
                builtInZoomControls = false  // True on phones/tablets, False on TV (removes visual artifacts)
                displayZoomControls = false       // Keeps UI completely clean of ugly +/- buttons
                setSupportZoom(true)         // Allows standard devices to stretch cinematic views if needed

                // FIX: Multi-window support must be TRUE for standard HTML5 video elements
                // to scale up and trigger full-screen player states natively.
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true // Allows player scripts to execute properly

                // Security layer bypass for http:// streaming streams running on https:// pages
                if (Build.VERSION.SDK_INT >= 21) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                cacheMode = WebSettings.LOAD_DEFAULT // Utilizes the browser cache for buffering

                // FIX: Use the system's current WebView User-Agent instead of a hardcoded outdated one.
                // Providers increasingly reject outdated Chrome UAs (Chrome/120 from late 2023 is now stale),
                // which results in silent infinite-loading states instead of a visible error.
                userAgentString = WebSettings.getDefaultUserAgent(this@PlayerActivity)
            }

            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
            overScrollMode = View.OVER_SCROLL_NEVER

            // NOTE: webViewClient is set AFTER the iframeUrl/allowedHosts computation below
            // so the AdBlockerWebViewClient can be configured with a strict provider whitelist.

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


            // ── Updated: Unified structure loading with generateIframeHtml ──
            // FIX: Compute the actual iframe URL first, then derive the baseUrl from its origin.
            // Previously baseUrl was hardcoded to the provider's movieUrlTemplate host, which
            // didn't match the iframe's true origin when the provider serves from a different
            // domain. That mismatch caused X-Frame-Options/CSP "frame-ancestors" to silently
            // refuse the embed and disable postMessage tracking.
            val iframeUrl = if (intent.getStringExtra("IFRAME_HTML") == null) {
                StreamProviderManager.generateUrl(
                    providerName = currentProviderName,
                    tmdbId = currentTmdbId,
                    isTv = currentIsTv,
                    season = if (currentIsTv) currentSeason else null,
                    episode = if (currentIsTv) currentEpisode else null,
                    timestamp = currentPlaybackPosition / 1000L // Convert ms to seconds for provider URL parameters
                )
            } else {
                // IFRAME_HTML supplied: derive origin from a sensible provider base
                StreamProviderManager.getBaseUrl(currentProviderName)
            }
            val baseUrl = try {
                val parsed = android.net.Uri.parse(iframeUrl)
                val scheme = parsed.scheme ?: "https"
                val host = parsed.host ?: StreamProviderManager.getBaseUrl(currentProviderName)
                    .removePrefix("https://").removePrefix("http://").substringBefore("/")
                "$scheme://$host"
            } catch (e: Exception) {
                Log.w(TAG, "[WebView] Failed to parse iframe origin, falling back to provider base: ${e.message}")
                StreamProviderManager.getBaseUrl(currentProviderName)
            }
            Log.i(TAG, "[WebView] Loading iframe from origin: $baseUrl")

            // FIX: Extract the provider host from the actual iframe URL so the WebView's
            // ad-blocker can whitelist it (and only it). Any non-provider request will be
            // blocked at the network layer by AdBlockerWebViewClient.
            val providerHost: String? = try {
                android.net.Uri.parse(iframeUrl).host?.lowercase()
            } catch (e: Exception) {
                null
            }
            val allowedHosts: Set<String> = setOfNotNull(
                providerHost,
                // Also allow the provider's movie-template host as a safe fallback (subdomains, etc.)
                try {
                    StreamProviderManager.getBaseUrl(currentProviderName)
                        .removePrefix("https://").removePrefix("http://").substringBefore("/").lowercase()
                } catch (e: Exception) { null }
            )
            Log.i(TAG, "[WebView] Provider whitelist hosts: $allowedHosts")

            // FIX: Configure the WebView's ad-blocker with the provider whitelist so it
            // only permits requests to the provider URL (and its subdomains). Every other
            // request is intercepted and returned as an empty response, isolating the iframe
            // from trackers, ad networks, and analytics endpoints.
            webViewClient = AdBlockerWebViewClient(
                allowedHosts = allowedHosts,
                onPageFinished = {
                    isPageLoading = false
                    hasPageError = false
                    stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)
                    loadingOverlay.visibility = View.GONE
                    errorOverlay.visibility = View.GONE
                    Log.i(TAG, "[WebView] Page finished loading with AdBlocker")
                },
                onError = {
                    hasPageError = true
                    isPageLoading = false
                    stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)
                    Log.e(TAG, "[WebView] Error received with AdBlocker")
                    showErrorOverlay("Unable to load the player.\n\nTap to retry.")
                }
            )

            // FIX: Start the stall detector — fires after STALL_TIMEOUT_MS if the page never finishes.
            stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)
            stallTimeoutHandler.postDelayed(stallTimeoutRunnable, STALL_TIMEOUT_MS)

            val finalHtml = intent.getStringExtra("IFRAME_HTML") ?: StreamProviderManager.generateIframeHtml(
                providerName = currentProviderName,
                tmdbId = currentTmdbId,
                isTv = currentIsTv,
                season = if (currentIsTv) currentSeason else null,
                episode = if (currentIsTv) currentEpisode else null,
                timestamp = currentPlaybackPosition / 1000L // Convert ms to seconds for provider URL parameters
            )
            loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
        }

        // Add JavascriptInterface bridge for player events (must be called on webView, not Activity)
        webView.addJavascriptInterface(
            PlayerBridge { provider, positionSec, durationSec, season, episode ->
                runOnUiThread {
                    // positionSec is in seconds, convert to milliseconds for storage
                    currentPlaybackPosition = (positionSec * 1000).toLong()
                    // Store duration in milliseconds
                    currentDuration = (durationSec * 1000).toLong()
                    // Update season and episode if provided and they have changed
                    if (currentIsTv && season != null && episode != null) {
                        if (season != currentSeason || episode != currentEpisode) {
                            Log.i(TAG, "[Episode] Changed S${currentSeason}E${currentEpisode} -> S${season}E${episode}")
                            currentSeason = season
                            currentEpisode = episode
                        }
                    }
                }
            },
            "MavisInterface"
        )

        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(webView)
        // FIX: Add overlays AFTER the WebView so they sit on top and remain visible
        rootLayout.addView(loadingOverlay)
        rootLayout.addView(errorOverlay)
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

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        // FIX: Restart the stall timeout if the page is still loading when we resume
        if (isPageLoading && !hasPageError && ::webView.isInitialized) {
            stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)
            stallTimeoutHandler.postDelayed(stallTimeoutRunnable, STALL_TIMEOUT_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        stopProgressUpdateTimer()
        // FIX: Cancel the stall timer while paused so we don't falsely fire on resume
        stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)
    }

    override fun onDestroy() {
        stopProgressUpdateTimer()
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        stallTimeoutHandler.removeCallbacks(stallTimeoutRunnable)

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
        if (isCursorDisabled) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY = (cursorY - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY = (cursorY + moveSpeed).coerceAtMost(screenHeight.toFloat())
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX = (cursorX - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX = (cursorX + moveSpeed).coerceAtMost(screenWidth.toFloat())
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                simulateClick(cursorX, cursorY)
                showCursorAndResetTimer()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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

    /**
     * FIX: Show the error overlay with a retry-on-tap message.
     * Called from AdBlockerWebViewClient.onError and the stall-timeout detector.
     */
    private fun showErrorOverlay(message: String) {
        runOnUiThread {
            errorText.text = message
            errorOverlay.visibility = View.VISIBLE
            loadingOverlay.visibility = View.GONE
        }
    }

    private fun checkAndAddToWatchHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "[WatchHistory] Checking if media is in watch history: id=$currentTmdbId, isTv=$currentIsTv")

                // Check synchronously before deciding to add
                val alreadyInHistory = repository.isInWatchHistory(
                    this@PlayerActivity,
                    currentTmdbId,
                    currentIsTv
                )

                if (!alreadyInHistory) {
                    Log.d(TAG, "[WatchHistory] Adding to watch history: id=$currentTmdbId, title=$currentTitle, isTv=$currentIsTv, season=$currentSeason, episode=$currentEpisode")
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

                    repository.saveToWatchHistory(this@PlayerActivity, watchHistoryItem)
                    Log.d(TAG, "[WatchHistory] saveToWatchHistory called successfully")

                    // Start progress timer only after adding to history
                    withContext(Dispatchers.Main) {
                        startProgressUpdateTimer()
                    }

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
                } else {
                    Log.d(TAG, "[WatchHistory] Media already in watch history, starting timer anyway")
                    // Start timer even if already in history (for resume functionality)
                    withContext(Dispatchers.Main) {
                        startProgressUpdateTimer()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error checking/adding to watch history: ${e.message}", e)
                // Still start timer on error to enable progress tracking
                withContext(Dispatchers.Main) {
                    startProgressUpdateTimer()
                }
            }
        }
    }

    private fun startProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        // FIX: First sync at 5s instead of 15s for faster feedback when buffering.
        // Subsequent syncs continue at the 15s cadence in updateWatchProgress().
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 5000)
    }

    private fun persistWatchProgress() {
        if (currentTmdbId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.updatePlaybackPosition(
                    mediaId = currentTmdbId,
                    mediaType = if (currentIsTv) "tv" else "movie",
                    position = currentPlaybackPosition
                )

                if (currentIsTv) {
                    repository.updateEpisodeInfo(
                        mediaId = currentTmdbId,
                        mediaType = "tv",
                        seasonNumber = currentSeason,
                        episodeNumber = currentEpisode
                    )
                }

                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                    tmdbId = currentTmdbId,
                    isTv = currentIsTv,
                    seasonNumber = if (currentIsTv) currentSeason else null,
                    episodeNumber = if (currentIsTv) currentEpisode else null,
                    playbackPosition = currentPlaybackPosition,
                    duration = currentDuration,
                    title = currentTitle,
                    overview = currentOverview,
                    posterPath = currentPosterPath,
                    backdropPath = currentBackdropPath,
                    voteAverage = currentVoteAverage,
                    releaseDate = currentReleaseDate
                )
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error persisting progress: ${e.message}")
            }
        }
    }

    private fun updateWatchProgress() {
        // Timer-based sync - uses the current position already set by PlayerBridge
        persistWatchProgress()
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 15000)
    }

    private fun stopProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }
}