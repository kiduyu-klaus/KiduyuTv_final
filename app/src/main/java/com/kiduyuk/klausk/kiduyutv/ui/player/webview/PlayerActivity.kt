package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
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
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.util.Collections

class PlayerActivity : AppCompatActivity() {

    // DoH DNS Resolver (unchanged)
    private object DohDnsResolver { ... } // keep as is

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
    private var currentProviderName: String = "VidLink"

    // Metadata for Firebase sync (unchanged)
    private var contentTitle: String = "Unknown"
    private var contentOverview: String? = null
    private var contentPosterPath: String? = null
    private var contentBackdropPath: String? = null
    private var contentVoteAverage: Double = 0.0
    private var contentReleaseDate: String? = null

    // Playback tracking (unchanged)
    private var latestTimestamp: Long = 0L
    private var latestDuration: Long = 0L
    private var latestProgress: Double = 0.0
    private var latestSeason: Int = 1
    private var latestEpisode: Int = 1
    private var latestContentType: String = "movie"
    private var latestContentId: Int = -1
    private val dnsWarmedHosts = Collections.synchronizedSet(mutableSetOf<String>())

    // Error detection state
    private var isVideoLoaded = false
    private var isPageLoaded = false
    private var hasShownError = false
    private var videoLoadCheckHandler: Handler? = null
    private var videoLoadCheckRunnable: Runnable? = null

    // Retry state
    private var streamRetryCount = 0
    private val maxStreamRetries = 4
    private var originalStreamUrl: String = ""

    // ───────────────────────── Ad Blocking Domains ─────────────────────────
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

    companion object {
        private const val TAG = "VideasyPlayer"
        private const val PROGRESS_UPDATE_INTERVAL = 15_000L
    }

    // JavaScript interface (unchanged)
    inner class VideasyJavaScriptInterface { ... }

    private fun processPlayerProgressData(data: org.json.JSONObject) { ... }

    // ───────────────────────── Video Load Timeout ─────────────────────────
    private fun startVideoLoadTimeoutCheck() {
        cancelVideoLoadTimeoutCheck()
        videoLoadCheckHandler = Handler(Looper.getMainLooper())
        videoLoadCheckRunnable = Runnable {
            checkVideoLoadStatus()
        }
        videoLoadCheckHandler?.postDelayed(videoLoadCheckRunnable!!, 3000)
    }

    private fun cancelVideoLoadTimeoutCheck() {
        videoLoadCheckRunnable?.let { runnable ->
            videoLoadCheckHandler?.removeCallbacks(runnable)
        }
        videoLoadCheckHandler = null
        videoLoadCheckRunnable = null
    }

    private fun checkVideoLoadStatus() {
        if (hasShownError) return

        webView.evaluateJavascript(
            """
            (function() {
                if (window.getVideoStatus) {
                    return window.getVideoStatus();
                }
                return JSON.stringify({ hasVideo: false, isPlaying: false, error: true, message: 'Check script not loaded' });
            })();
            """.trimIndent()
        ) { result ->
            if (hasShownError) return@evaluateJavascript
            try {
                val json = org.json.JSONObject(result)
                val hasVideo = json.optBoolean("hasVideo", false)
                val isPlaying = json.optBoolean("isPlaying", false)
                val hasError = json.optBoolean("hasError", false)
                val message = json.optString("message", "")

                if (hasVideo && isPlaying) {
                    isVideoLoaded = true
                    cancelVideoLoadTimeoutCheck()
                    // Reset retry counter on successful playback
                    streamRetryCount = 0
                    Log.i(TAG, "[Video Check] Video loaded and playing successfully")
                    return@evaluateJavascript
                }

                if (hasError) {
                    if (!hasShownError) {
                        hasShownError = true
                        runOnUiThread {
                            showVideoErrorDialog("Video Error", message)
                        }
                    }
                    return@evaluateJavascript
                }

                if (!isVideoLoaded) {
                    videoLoadCheckHandler?.postDelayed(videoLoadCheckRunnable!!, 3000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Video Check] Error parsing: ${e.message}")
                if (!hasShownError && videoLoadCheckHandler != null) {
                    videoLoadCheckHandler?.postDelayed(videoLoadCheckRunnable!!, 5000)
                }
            }
        }
    }

    private fun showVideoErrorDialog(title: String, message: String) { ... } // unchanged

    // ───────────────────────── Stream Retry Handler ─────────────────────────
    private fun handleStreamError(errorDescription: String) {
        if (hasShownError) return

        if (streamRetryCount < maxStreamRetries) {
            streamRetryCount++
            Log.w(TAG, "[Retry] Attempt $streamRetryCount/$maxStreamRetries — reloading")
            cancelVideoLoadTimeoutCheck()
            isPageLoaded = false
            isVideoLoaded = false
            webView.stopLoading()
            webView.loadUrl(originalStreamUrl)
        } else {
            Log.e(TAG, "[Retry] Max retries reached")
            hasShownError = true
            runOnUiThread {
                showVideoErrorDialog("Stream Unavailable", errorDescription)
            }
        }
    }

    // ───────────────────────── Cursor & UI Helpers ──────────────────────────
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable { if (!isCursorDisabled) cursorView.animate().alpha(0f).setDuration(500).start() }

    // ───────────────────────── Progress Saver (15s) ─────────────────────────
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            savePlaybackPositionToDb()   // background call
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
        }
    }

    // ───────────────────────── onCreate ─────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        currentSeason = intent.getIntExtra("SEASON_NUMBER", 1)
        currentEpisode = intent.getIntExtra("EPISODE_NUMBER", 1)

        contentTitle = intent.getStringExtra("TITLE") ?: "Unknown"
        contentOverview = intent.getStringExtra("OVERVIEW")
        contentPosterPath = intent.getStringExtra("POSTER_PATH")
        contentBackdropPath = intent.getStringExtra("BACKDROP_PATH")
        contentVoteAverage = intent.getDoubleExtra("VOTE_AVERAGE", 0.0)
        contentReleaseDate = intent.getStringExtra("RELEASE_DATE")

        if (tmdbId == -1) {
            finish()
            return
        }

        val repository = TmdbRepository()

        // ─── Off‑main‑thread DB operations (watch history) ───
        lifecycleScope.launch(Dispatchers.IO) {
            val existsInHistory = repository.isInWatchHistory(this@PlayerActivity, tmdbId, isTv)
            if (existsInHistory) {
                Log.i(TAG, "[WatchHistory] Updating episode info")
                repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
            } else {
                Log.i(TAG, "[WatchHistory] Saving new item")
                repository.saveToWatchHistory(
                    this@PlayerActivity,
                    WatchHistoryItem(
                        id = tmdbId,
                        title = contentTitle,
                        overview = contentOverview,
                        posterPath = contentPosterPath,
                        backdropPath = contentBackdropPath,
                        voteAverage = contentVoteAverage,
                        releaseDate = contentReleaseDate,
                        isTv = isTv,
                        seasonNumber = if (isTv) currentSeason else null,
                        episodeNumber = if (isTv) currentEpisode else null
                    )
                )
            }
        }

        // Build stream URL
        val url = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$currentSeason/$currentEpisode?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        // Provider detection (unchanged)
        currentProviderName = when {
            url.startsWith("https://vidlink.pro") -> "VidLink"
            url.startsWith("https://www.vidking.net") -> "VidKing"
            url.startsWith("https://player.videasy.net") -> "Videasy"
            else -> "VidLink"
        }

        warmUpDnsForUrl(url)
        originalStreamUrl = url

        // Layout setup
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setOnApplyWindowInsetsListener { _, insets -> insets }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                allowFileAccess = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeSetSafeBrowsingEnabled(this, false)
                }
            }

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY)

            if (isCursorDisabled) setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Conditionally add JS interface
            if (url.startsWith("https://player.videasy.net") || url.contains("vidking") || url.contains("vidlink.pro")) {
                addJavascriptInterface(VideasyJavaScriptInterface(), "VideasyInterface")
            }

            webViewClient = createAdBlockingWebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
            }

            val iframeHtml = intent.getStringExtra("IFRAME_HTML")
            if (iframeHtml != null) {
                Toast.makeText(this@PlayerActivity, "Loading via IFRAME mode", Toast.LENGTH_SHORT).show()
                val baseUrl = com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager.getBaseUrl(currentProviderName)
                loadDataWithBaseURL(baseUrl, iframeHtml, "text/html", "UTF-8", null)
            } else {
                loadUrl(url)
            }
        }

        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
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

        setupImmersiveMode()
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
            override fun handleOnBackPressed() = showExitConfirmationDialog()
        })
    }

    // ───────────────────────── Ad‑Blocking WebViewClient ─────────────────────
    private fun createAdBlockingWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase() ?: return null
                if (adDomains.any { url.contains(it) }) {
                    Log.d(TAG, "[AdBlock] Blocked: $url")
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                request?.url?.host?.let { warmUpDnsHost(it) }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (adDomains.any { url.lowercase().contains(it) }) {
                    Log.d(TAG, "[AdBlock] Blocked navigation: $url")
                    return true
                }
                if (url.contains("streamingnow.mov", ignoreCase = true) ||
                    url.contains("multiembed.mov", ignoreCase = true)) {
                    originalStreamUrl = url
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = false
                isVideoLoaded = false
                url?.let {
                    if (it.contains("streamingnow.mov", ignoreCase = true) ||
                        it.contains("multiembed.mov", ignoreCase = true)) {
                        originalStreamUrl = it
                    }
                }
                Log.i(TAG, "[WebView] Page started: $url")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Only main frame errors should trigger a retry
                if (request?.isForMainFrame == true) {
                    val description = error?.description?.toString() ?: "Unknown error"
                    Log.e(TAG, "[WebView] Main frame error: $description")
                    handleStreamError(description)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && errorResponse?.statusCode in 400..599) {
                    Log.e(TAG, "[WebView] HTTP error on main frame: ${errorResponse.statusCode}")
                    handleStreamError("HTTP ${errorResponse.statusCode}")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoaded = true
                Log.i(TAG, "[WebView] Page finished: $url")

                // Inject ad‑blocking CSS and JS
                injectAdBlockingScripts(view)

                // Inject video detection (must be done before timeout check)
                injectVideoDetectionScript(view)
                injectStreamProtectionScript(view)
                injectAdvancedPlayerScripts(view)

                // Now start the video load timeout
                startVideoLoadTimeoutCheck()
            }
        }
    }

    // ───────────────────────── Script Injection Helpers ──────────────────────
    private fun injectAdBlockingScripts(view: WebView?) {
        val cssJs = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay, iframe[src*="doubleclick"], iframe[src*="googlead"] { display: none !important; }';
                document.head.appendChild(style);
                var ads = document.querySelectorAll('div[id^="ad"], div[class^="ad"], iframe[src*="doubleclick"], iframe[src*="google"]');
                ads.forEach(function(ad) { ad.remove(); });
            })();
        """.trimIndent()
        view?.evaluateJavascript(cssJs, null)
    }

    private fun injectVideoDetectionScript(view: WebView?) { /* unchanged from original, keep */ }
    private fun injectStreamProtectionScript(view: WebView?) { /* unchanged */ }
    private fun injectAdvancedPlayerScripts(view: WebView?) { /* unchanged */ }

    // ───────────────────────── Save Position (Background) ────────────────────
    private fun savePlaybackPositionToDb() {
        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        if (tmdbId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = TmdbRepository()
                val mediaType = when {
                    latestContentType.isNotEmpty() && latestContentType != "null" -> latestContentType
                    isTv -> "tv"
                    else -> "movie"
                }
                val playbackPosition = if (latestTimestamp > 0) {
                    latestTimestamp
                } else if (latestDuration > 0 && latestProgress > 0) {
                    ((latestProgress / 100.0) * latestDuration).toLong()
                } else 0L

                repository.updatePlaybackPosition(tmdbId, mediaType, playbackPosition)

                val isTvContent = mediaType == "tv" || mediaType == "anime" || isTv
                val seasonToSync = if (isTvContent) (if (latestSeason > 0) latestSeason else currentSeason) else null
                val episodeToSync = if (isTvContent) (if (latestEpisode > 0) latestEpisode else currentEpisode) else null

                FirebaseManager.syncWatchHistory(
                    tmdbId = tmdbId,
                    isTv = isTvContent,
                    seasonNumber = seasonToSync,
                    episodeNumber = episodeToSync,
                    playbackPosition = playbackPosition,
                    duration = latestDuration,
                    title = contentTitle,
                    overview = contentOverview,
                    posterPath = contentPosterPath,
                    backdropPath = contentBackdropPath,
                    voteAverage = contentVoteAverage,
                    releaseDate = contentReleaseDate
                )

                if (isTvContent) {
                    repository.updateEpisodeInfo(tmdbId, mediaType, seasonToSync ?: currentSeason, episodeToSync ?: currentEpisode)
                }
                Log.i(TAG, "[Progress Save] Saved ${playbackPosition}s")
            } catch (e: Exception) {
                Log.e(TAG, "[Progress Save] Error: ${e.message}")
            }
        }
    }

    // Called on exit or back button
    private fun saveFinalPlaybackPosition() {
        webView.evaluateJavascript(
            "(function(){ var v=document.querySelector('video'); return v&&v.duration>0?v.currentTime:null; })();"
        ) { result ->
            if (result != null && result != "null") {
                val currentTime = result.toDoubleOrNull() ?: return@evaluateJavascript
                val tmdbId = intent.getIntExtra("TMDB_ID", -1)
                val isTv = intent.getBooleanExtra("IS_TV", false)
                if (tmdbId != -1) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val repository = TmdbRepository()
                        repository.updatePlaybackPosition(tmdbId, if (isTv) "tv" else "movie", currentTime.toLong())
                        if (isTv) {
                            repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
                        }
                        FirebaseManager.syncWatchHistory(
                            tmdbId = tmdbId,
                            isTv = isTv,
                            seasonNumber = if (isTv) currentSeason else null,
                            episodeNumber = if (isTv) currentEpisode else null,
                            playbackPosition = currentTime.toLong(),
                            duration = latestDuration,
                            title = contentTitle,
                            overview = contentOverview,
                            posterPath = contentPosterPath,
                            backdropPath = contentBackdropPath,
                            voteAverage = contentVoteAverage,
                            releaseDate = contentReleaseDate
                        )
                        Log.i(TAG, "[Final Save] Position $currentTime seconds")
                    }
                }
            }
        }
    }

    // ───────────────────────── Lifecycle Overrides ───────────────────────────
    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        progressHandler.postDelayed(progressRunnable, PROGRESS_UPDATE_INTERVAL)
        setupImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        saveFinalPlaybackPosition()   // crucial: save before possible process death
        webView.onPause()
        webView.pauseTimers()
        progressHandler.removeCallbacks(progressRunnable)
        cancelVideoLoadTimeoutCheck()
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cancelVideoLoadTimeoutCheck()
        // WebView cleanup unchanged
        if (::webView.isInitialized) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
                    removeJavascriptInterface("VideasyInterface")
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
                Log.e(TAG, "WebView cleanup error: ${e.message}")
            }
        }
        super.onDestroy()
    }

    // ───────────────────────── Remaining Helpers ─────────────────────────────
    private fun safeSetSafeBrowsingEnabled(settings: WebSettings, enabled: Boolean) { ... }
    private fun setupImmersiveMode() { ... }
    private fun showExitConfirmationDialog() { ... }
    private fun warmUpDnsForUrl(url: String) { ... }
    private fun warmUpDnsHost(host: String) { ... }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean { ... }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { ... }
    private fun updateCursorPosition() { ... }
    private fun simulateClick(x: Float, y: Float) { ... }
    private fun showCursorAndResetTimer() { ... }
}