package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
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
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.SingletonDnsResolver
import java.io.ByteArrayInputStream
import java.util.Collections

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
    private var currentProviderName: String = "VidLink"

    // Track content metadata for Firebase sync
    private var contentTitle: String = "Unknown"
    private var contentOverview: String? = null
    private var contentPosterPath: String? = null
    private var contentBackdropPath: String? = null
    private var contentVoteAverage: Double = 0.0
    private var contentReleaseDate: String? = null

    // Track latest playback info from player messages
    private var latestTimestamp: Long = 0L
    private var latestDuration: Long = 0L
    private var latestProgress: Double = 0.0
    private var latestSeason: Int = 1
    private var latestEpisode: Int = 1
    private var latestContentType: String = "movie"
    private var latestContentId: Int = -1
    private val dnsWarmedHosts = Collections.synchronizedSet(mutableSetOf<String>())

    companion object {
        private const val TAG = "VideasyPlayer"
        private const val PROGRESS_UPDATE_INTERVAL = 15_000L
    }

    @Suppress("UNUSED")
    inner class VideasyJavaScriptInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            try {
                val json = org.json.JSONObject(message)
                when {
                    json.has("type") && json.getString("type") == "PLAYER_EVENT" && json.has("data") -> {
                        val data = json.getJSONObject("data")
                        processPlayerProgressData(data)
                    }
                    json.has("progress") && json.has("timestamp") -> {
                        processPlayerProgressData(json)
                    }
                    json.has("currentTime") -> {
                        processPlayerProgressData(json)
                    }
                    else -> {
                        Log.i(TAG, "[JS Message] Unrecognized message format, attempting generic parse")
                        if (json.has("progress") || json.has("timestamp") || json.has("currentTime")) {
                            processPlayerProgressData(json)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[JS Message] Error parsing message: ${e.message}")
            }
        }
    }

    private fun processPlayerProgressData(data: org.json.JSONObject) {
        try {
            if (data.has("id")) latestContentId = data.getInt("id")
            if (data.has("type")) latestContentType = data.getString("type")

            latestProgress = if (data.has("progress")) {
                data.getDouble("progress")
            } else if (data.has("currentTime") && data.has("duration")) {
                val currentTime = data.getDouble("currentTime")
                val duration = data.getDouble("duration")
                if (duration > 0) (currentTime / duration) * 100 else 0.0
            } else 0.0

            latestTimestamp = if (data.has("timestamp")) {
                data.getLong("timestamp")
            } else if (data.has("currentTime")) {
                data.getDouble("currentTime").toLong()
            } else 0L

            latestDuration = if (data.has("duration")) data.getLong("duration") else 0L

            if (data.has("season")) latestSeason = data.getInt("season")
            if (data.has("episode")) latestEpisode = data.getInt("episode")

            Log.i(
                TAG, String.format(
                    "[Player Progress] id=%d type=%s progress=%.1f%% timestamp=%ds duration=%ds season=%d episode=%d",
                    latestContentId, latestContentType, latestProgress, latestTimestamp,
                    latestDuration, latestSeason, latestEpisode
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Player Progress] Error processing data: ${e.message}")
        }
    }

    // ── Cursor hide timer ──────────────────────────────────────────────────────
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled) {
            cursorView.animate().alpha(0f).setDuration(500).start()
        }
    }

    // ── 15-second progress saver ───────────────────────────────────────────────
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val tmdbId = intent.getIntExtra("TMDB_ID", -1)
            val isTv = intent.getBooleanExtra("IS_TV", false)

            if (tmdbId != -1 && latestTimestamp > 0) {
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

                    Log.d(TAG, "Syncing watch history to Firebase: tmdbId=$tmdbId, isTv=$isTvContent, season=$seasonToSync, episode=$episodeToSync, position=${playbackPosition}s")

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

                    val seasonToSave = if (latestSeason > 0 && (mediaType == "tv" || mediaType == "anime")) latestSeason else currentSeason
                    val episodeToSave = if (latestEpisode > 0 && (mediaType == "tv" || mediaType == "anime")) latestEpisode else currentEpisode

                    if (mediaType == "tv" || mediaType == "anime" || isTv) {
                        repository.updateEpisodeInfo(tmdbId, mediaType, seasonToSave, episodeToSave)
                        Log.i(TAG, String.format("[Progress Save] position=%ds (%.1f%%), S%dE%d saved", playbackPosition, latestProgress, seasonToSave, episodeToSave))
                    } else {
                        Log.i(TAG, String.format("[Progress Save] position=%ds (%.1f%%) saved for movie", playbackPosition, latestProgress))
                    }

                    if (seasonToSave > 0) currentSeason = seasonToSave
                    if (episodeToSave > 0) currentEpisode = episodeToSave

                } catch (e: Exception) {
                    Log.e(TAG, "[Progress Save] Error saving progress: ${e.message}")
                }
            } else {
                Log.i(TAG, "[Progress Save] No valid timestamp received yet ")
            }

            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
        }
    }

    // ── Fullscreen helper ──────────────────────────────────────────────────────
    private fun enableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableFullscreen()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply fullscreen immediately, before any layout is set
        enableFullscreen()

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

        // Detect device type — cursor disabled only on mobile/tablet
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
            Log.i(TAG, "[Device] Mobile/Tablet detected, disabling cursor")
        } else {
            Log.i(TAG, "[Device] TV detected, cursor enabled")
        }

        val existsInHistory = repository.isInWatchHistory(this, tmdbId, isTv)

        if (existsInHistory) {
            Log.i(TAG, "[WatchHistory] Item exists, updating season $currentSeason episode $currentEpisode")
            repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
        } else {
            Log.i(TAG, "[WatchHistory] New item, saving to history")
            repository.saveToWatchHistory(
                this,
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

        val url = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$currentSeason/$currentEpisode?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        val isVideasyPlayer = url.startsWith("https://player.videasy.net")
        val isVidKingPlayer = url.startsWith("https://www.vidking.net") || url.startsWith("https://vidking.")
        val isVidLinkPlayer = url.startsWith("https://vidlink.pro")
        val isTrackingEnabled = isVideasyPlayer || isVidKingPlayer || isVidLinkPlayer

        currentProviderName = when {
            isVidLinkPlayer -> "VidLink"
            isVidKingPlayer -> "VidKing"
            isVideasyPlayer -> "Videasy"
            url.contains("vidfast", ignoreCase = true) -> "VidFast"
            url.contains("vidsrc", ignoreCase = true) -> "VidSrc"
            url.contains("mapple", ignoreCase = true) -> "Mapple"
            url.contains("flixer", ignoreCase = true) -> "Flixer"
            else -> "VidLink"
        }

        Log.i(TAG, "[Provider] Selected: $currentProviderName")
        warmUpDnsForUrl(url)

        // ── Layout ────────────────────────────────────────────────────────────
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                useWideViewPort = true
                loadWithOverviewMode = true
                allowContentAccess = true
                databaseEnabled = true
                allowFileAccess = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeSetSafeBrowsingEnabled(this, false)
                }
            }

            if (isCursorDisabled) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                // TV / FireTV — keep NONE so SurfaceView can punch through
                setLayerType(View.LAYER_TYPE_NONE, null)
            }

            if (isTrackingEnabled) {
                addJavascriptInterface(VideasyJavaScriptInterface(), "VideasyInterface")
            }

            // ================= REPLACED WebViewClient BLOCK START =================
            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {

                    val reqUrl = request?.url.toString()

                    request?.url?.host?.let { warmUpDnsHost(it) }

                    return if (AdvancedAdBlocker.shouldBlock(reqUrl)) {
                        Log.d("AdBlocker", "Blocked request: $reqUrl")
                        WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    val url = request?.url?.toString() ?: return false

                    // 🚀 Allow navigation unless blocked
                    return AdvancedAdBlocker.shouldBlock(url).also { blocked ->
                        if (blocked) {
                            Log.d("AdBlocker", "Blocked navigation: $url")
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    if (url != null) {
                        val cookieManager = CookieManager.getInstance()
                        val cookies = cookieManager.getCookie(url)

                        Log.i(TAG, "[Cookies] URL: $url")
                        Log.i(TAG, "[Cookies] Content: ${cookies ?: "No cookies found"}")
                    }

                    // ✅ Inject CSS FIRST (important timing fix)
                    view?.evaluateJavascript(AdvancedAdBlocker.getCss(), null)

                    // Your existing JS stays unchanged
                    val advancedJs = """
                        (function() {
                            function removeAdsAdvanced() {
                                const elements = document.querySelectorAll('*');
                                elements.forEach(el => {
                                    const text = (el.innerText || '').toLowerCase();
                                    const cls = (el.className || '').toString().toLowerCase();
                                    if (
                                        text.includes('advert') ||
                                        text.includes('sponsored') ||
                                        cls.includes('ad') ||
                                        cls.includes('popup')
                                    ) {
                                        el.remove();
                                    }
                                });
                            }

                            function blockRedirects() {
                                window.open = () => null;
                                window.location.assign = () => {};
                                window.location.replace = () => {};
                            }

                            function setupMessageListener() {
                                console.log('Player message listener initialized');

                                (function() {
                                    var originalPostMessage = window.postMessage;
                                    window.postMessage = function(message, targetOrigin, transfer) {
                                        try {
                                            if (window.VideasyInterface) {
                                                if (typeof message === 'string') {
                                                    window.VideasyInterface.postMessage(message);
                                                } else {
                                                    window.VideasyInterface.postMessage(JSON.stringify(message));
                                                }
                                            }
                                        } catch (e) {}
                                        return originalPostMessage.apply(this, arguments);
                                    };
                                })();

                                window.addEventListener('message', function(event) {
                                    try {
                                        if (window.VideasyInterface) {
                                            if (typeof event.data === 'string') {
                                                window.VideasyInterface.postMessage(event.data);
                                            } else {
                                                window.VideasyInterface.postMessage(JSON.stringify(event.data));
                                            }
                                        }
                                    } catch (e) {}
                                });

                                function getContentInfo() {
                                    var info = { type: 'movie', id: null, season: 1, episode: 1 };
                                    try {
                                        var url = window.location.href;
                                        var match;

                                        match = url.match(/\/tv\/(\d+)\/(\d+)\/(\d+)/);
                                        if (match) {
                                            info.type = 'tv';
                                            info.id = parseInt(match[1]);
                                            info.season = parseInt(match[2]);
                                            info.episode = parseInt(match[3]);
                                            return info;
                                        }

                                        match = url.match(/\/movie\/(\d+)/);
                                        if (match) {
                                            info.type = 'movie';
                                            info.id = parseInt(match[1]);
                                            return info;
                                        }

                                        match = url.match(/\/anime\/(\d+)\/(\d+)\/(\d+)/);
                                        if (match) {
                                            info.type = 'anime';
                                            info.id = parseInt(match[1]);
                                            info.season = parseInt(match[2]);
                                            info.episode = parseInt(match[3]);
                                            return info;
                                        }
                                    } catch (e) {}
                                    return info;
                                }

                                function sendVideoProgress() {
                                    var videos = document.getElementsByTagName('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        var v = videos[i];
                                        if (v.duration > 0 && !isNaN(v.duration)) {
                                            var contentInfo = getContentInfo();
                                            var progressData = {
                                                progress: (v.currentTime / v.duration) * 100,
                                                timestamp: Math.floor(v.currentTime),
                                                duration: Math.floor(v.duration),
                                                currentTime: v.currentTime,
                                                paused: v.paused,
                                                ended: v.ended
                                            };
                                            if (contentInfo) {
                                                progressData.id = contentInfo.id;
                                                progressData.type = contentInfo.type;
                                                progressData.season = contentInfo.season;
                                                progressData.episode = contentInfo.episode;
                                            }
                                            if (window.VideasyInterface) {
                                                window.VideasyInterface.postMessage(JSON.stringify(progressData));
                                            }
                                            break;
                                        }
                                    }
                                }

                                function enforceVolume(video) {
                                    video.volume = 1.0;
                                    video.muted = false;
                                    video.addEventListener('volumechange', function() {
                                        if (video.volume < 1.0 || video.muted) {
                                            video.volume = 1.0;
                                            video.muted = false;
                                        }
                                    });
                                }

                                function monitorVideoEvents() {
                                    const videos = document.querySelectorAll('video');
                                    videos.forEach(video => {
                                        if (video._monitored) return;
                                        video._monitored = true;
                                        video.addEventListener('loadedmetadata', () => sendVideoProgress());
                                        video.addEventListener('ended', () => sendVideoProgress());
                                        video.addEventListener('timeupdate', function() {
                                            if (!video._lastProgressUpdate || Date.now() - video._lastProgressUpdate > 1000) {
                                                sendVideoProgress();
                                                video._lastProgressUpdate = Date.now();
                                            }
                                        });
                                        enforceVolume(video);
                                    });
                                }

                                function observeVideoElements() {
                                    const observer = new MutationObserver(() => {
                                        monitorVideoEvents();
                                    });
                                    observer.observe(document.body, { childList: true, subtree: true });
                                }

                                monitorVideoEvents();
                                observeVideoElements();
                                setInterval(monitorVideoEvents, 10000);
                                setInterval(sendVideoProgress, 15000);
                            }

                            blockRedirects();
                            removeAdsAdvanced();
                            setupMessageListener();
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(advancedJs, null)
                }
            }
            // ================= REPLACED WebViewClient BLOCK END =================

            webChromeClient = object : WebChromeClient() {
                private var customView: View? = null
                private var customViewCallback: CustomViewCallback? = null

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        onHideCustomView()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    rootLayout.addView(
                        customView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    webView.visibility = View.GONE

                    // Keep cursor visible and on top during fullscreen video
                    if (!isCursorDisabled) {
                        cursorView.bringToFront()
                        cursorView.visibility = View.VISIBLE
                    }

                    enableFullscreen()
                }

                override fun onHideCustomView() {
                    if (customView == null) return
                    rootLayout.removeView(customView)
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    webView.visibility = View.VISIBLE

                    // Restore cursor on top after exiting fullscreen video
                    if (!isCursorDisabled) {
                        cursorView.bringToFront()
                        cursorView.visibility = View.VISIBLE
                    }

                    enableFullscreen()
                }

                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
            }

            loadUrl(url)
        }

        // ── Cursor ────────────────────────────────────────────────────────────
        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(webView)
        if (!isCursorDisabled) {
            rootLayout.addView(cursorView)
            cursorView.bringToFront() // Ensure cursor is always above video layer
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
            onNo = { /* dismiss — dialog closes itself */ },
            onYes = {
                savePlaybackPosition()
                finish()
            }
        ).show()
    }

    private fun warmUpDnsForUrl(url: String) {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return
        warmUpDnsHost(host)
    }

    private fun warmUpDnsHost(host: String) {
        if (host.isBlank()) return
        if (!dnsWarmedHosts.add(host)) return
        Thread {
            runCatching { SingletonDnsResolver.getDns().lookup(host) }
                .onSuccess { addresses -> Log.i(TAG, "[DNS] DoH resolved $host -> ${addresses.joinToString()}") }
                .onFailure { error -> Log.w(TAG, "[DNS] DoH resolve failed for $host: ${error.message}") }
        }.start()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        progressHandler.postDelayed(progressRunnable, 15_000L)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun safeSetSafeBrowsingEnabled(settings: WebSettings, enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set safe browsing: ${e.message}")
        }
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        cursorHideHandler.removeCallbacks(cursorHideRunnable)

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

    private fun updateCursorPosition() {
        if (isCursorDisabled) return
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.bringToFront() // Keep cursor above video layer on every move
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

    private fun savePlaybackPosition() {
        webView.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video');
                if (v && v.duration > 0 && !isNaN(v.duration)) {
                    return v.currentTime;
                }
                return null;
            })();
            """.trimIndent()
        ) { result ->
            if (result != null && result != "null") {
                try {
                    val currentTime = result.toDouble()
                    val tmdbId = intent.getIntExtra("TMDB_ID", -1)
                    val isTv = intent.getBooleanExtra("IS_TV", false)
                    if (tmdbId != -1) {
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
                        Log.i(TAG, "Final playback position saved: ${currentTime}s (S$currentSeason E$currentEpisode) to local and Firebase")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving final playback position: ${e.message}")
                }
            }
        }
    }
}
