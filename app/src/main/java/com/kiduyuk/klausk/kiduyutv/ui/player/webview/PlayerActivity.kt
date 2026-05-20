package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
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
    private var currentProviderName: String = "VidLink"

    // Loading and error state for AdBlockerWebViewClient
    private var isPageLoading = true
    private var hasPageError = false

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

    private var originalStreamUrl: String = ""

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
                Log.i(TAG, "[Progress Save] No valid timestamp received yet from player")
            }

            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
        }
    }

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

        // Detect provider using StreamProviderManager
        currentProviderName = detectProviderFromUrl(url)
        val isTrackingEnabled = currentProviderName in listOf("Videasy", "VidKing", "VidLink", "StreamingNow")

        Log.i(TAG, "[Provider] Selected: $currentProviderName")

        originalStreamUrl = url

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

            // Add window insets listener to prevent system UI from affecting WebView layout
            setOnApplyWindowInsetsListener { _, insets ->
                // Return the insets without consuming them to maintain fullscreen behavior
                // This prevents the WebView from being inset by status/navigation bars
                insets
            }

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
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            // Disable scrollbars
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY)

            if (isCursorDisabled) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_NONE, null)
            }

            if (isTrackingEnabled) {
                addJavascriptInterface(VideasyJavaScriptInterface(), "VideasyInterface")
            }

            // Use AdBlockerWebViewClient for ad blocking
            webViewClient = AdBlockerWebViewClient(
                onPageFinished = {
                    isPageLoading = false
                    Log.i(TAG, "[WebView] Page finished loading with AdBlocker")
                    injectVideoDetectionScript(this)
                    // injectAdvancedPlayerScripts(this)
                    injectAutoplayScript(this)
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
                    resultMsg: android.os.Message?
                ): Boolean {
                    Log.i(TAG, "[WebChrome] onCreateWindow called, blocking popups")
                    return false // Popups blocked
                }
            }

            val iframeHtml = intent.getStringExtra("IFRAME_HTML")
            if (iframeHtml != null) {
                Toast.makeText(this@PlayerActivity, "Loading via IFRAME mode", Toast.LENGTH_SHORT).show()
                // Use the provider's base URL for correct security context and relative links
                val baseUrl = com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager.getBaseUrl(currentProviderName)
                loadDataWithBaseURL(baseUrl, iframeHtml, "text/html", "UTF-8", null)
            } else {
                Toast.makeText(this@PlayerActivity, "Loading via DIRECT URL mode", Toast.LENGTH_SHORT).show()
                loadUrl(url)
            }
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
            cursorView.bringToFront()
        }

        setContentView(rootLayout)
        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        // ── Full-screen immersive mode ──
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
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    /**
     * ★ Inject video detection JavaScript
     */
    private fun injectVideoDetectionScript(view: WebView?) {
        val videoDetectionJs = """
        (function() {
            function checkVideoStatus() {
                var videos = document.getElementsByTagName('video');
                if (videos.length === 0) {
                    return JSON.stringify({ hasVideo: false, isPlaying: false, error: true, message: 'No video element found' });
                }
                var v = videos[0];
                var hasError = v.error !== null && v.error !== undefined;
                var isPlaying = !v.paused && !v.ended && v.readyState >= 3;
                var hasSource = v.readyState >= 1;
                return JSON.stringify({
                    hasVideo: true,
                    isPlaying: isPlaying,
                    hasError: hasError,
                    hasSource: hasSource,
                    readyState: v.readyState,
                    networkState: v.networkState,
                    errorCode: v.error ? v.error.code : 0,
                    message: hasError ? 'Video error: ' + (v.error ? v.error.message : 'Unknown') : (isPlaying ? 'Video is playing' : 'Video not playing')
                });
            }
            window.getVideoStatus = checkVideoStatus;
        })();
        """.trimIndent()

        view?.evaluateJavascript(videoDetectionJs, null)
    }

    /**
     * ★ Inject advanced player scripts
     */
    private fun injectAdvancedPlayerScripts(view: WebView?) {
        val advancedJs = """
        (function() {
            function removeAdsAdvanced() {
                const elements = document.querySelectorAll('*');
                elements.forEach(el => {
                    const text = (el.innerText || '').toLowerCase();
                    const cls = (el.className || '').toString().toLowerCase();
                    const id = (el.id || '').toLowerCase();
                    if (
                        text.includes('advert') || text.includes('sponsored') ||
                        cls.includes('ad') || cls.includes('popup') ||
                        id.includes('ad') || id.includes('popup')
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

                function setMaxVolume() {
                    var videos = document.getElementsByTagName('video');
                    for (var i = 0; i < videos.length; i++) {
                        videos[i].volume = 1.0;
                        videos[i].muted = false;
                    }
                }
                // Run setMaxVolume 3 times at 3 second intervals
                setMaxVolume();
                setTimeout(setMaxVolume, 3000);
                setTimeout(setMaxVolume, 6000);

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

    // ★ Inject autoplay script
    private fun injectAutoplayScript(view: WebView?) {
        val autoplayJs = """
        (function() {

            function forcePlay(video) {
                if (!video) return false;

                try {
                    video.autoplay = true;
                    video.controls = true;
                    video.playsInline = true;
                    video.volume = 1.0;

                    const playPromise = video.play();

                    if (playPromise !== undefined) {
                        playPromise
                            .then(() => {
                                console.log('[AutoPlay] Playback started');
                            })
                            .catch(err => {
                                console.log('[AutoPlay] Initial play failed:', err);

                                video.muted = true;
                                video.play().catch(() => {});
                            });
                    }

                    return true;
                } catch(e) {
                    console.log('[AutoPlay] Error:', e);
                    return false;
                }
            }

            function searchAndPlay() {
                let video = document.querySelector('video');

                if (video) {
                    return forcePlay(video);
                }

                const iframes = document.querySelectorAll('iframe');

                for (let iframe of iframes) {
                    try {
                        const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
                        const iframeVideo = iframeDoc.querySelector('video');

                        if (iframeVideo) {
                            return forcePlay(iframeVideo);
                        }
                    } catch(e) {}
                }

                return false;
            }

            let attempts = 0;
            const maxAttempts = 3;

            const interval = setInterval(() => {
                attempts++;

                console.log('[AutoPlay] Attempt ' + attempts + '/' + maxAttempts);

                const success = searchAndPlay();

                if (success || attempts >= maxAttempts) {
                    clearInterval(interval);

                    if (!success) {
                        console.log('[AutoPlay] No playable video found after retries');
                    }
                }

            }, 5000);

        })();
    """.trimIndent()

        view?.evaluateJavascript(autoplayJs, null)
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
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
            onYes = {
                savePlaybackPosition()
                finish()
            }
        ).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        progressHandler.postDelayed(progressRunnable, 15_000L)
        setupImmersiveMode()
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

    /**
     * Detect provider name from URL using StreamProviderManager
     */
    private fun detectProviderFromUrl(url: String): String {
        val urlHost = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            return "VidLink" // Default fallback
        }

        // Match against all providers from StreamProviderManager
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

        return "VidLink" // Default fallback
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

/**
 * AdBlockerWebViewClient - Handles ad blocking and page lifecycle events
 */
private class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit
) : WebViewClient() {

    // Common ad domains to block
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
        
        // Check if the URL contains any blocked domains
        if (adDomains.any { url.contains(it) }) {
            // Return an empty response to block the request
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
        }
        
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()
        
        // Inject JS to remove ad elements
        view?.evaluateJavascript(
            """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay { display: none !important; }';
                document.head.appendChild(style);
                
                // Remove existing ad elements
                var ads = document.querySelectorAll('div[id^="ad"], div[class^="ad"], iframe[src*="doubleclick"], iframe[src*="google"]');
                ads.forEach(function(ad) { ad.remove(); });
            })();
            """.trimIndent(), null
        )
    }
    
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
         // Don't trigger error for blocked ads (subresources)
        if (request?.isForMainFrame == true) {
            onError()
        }
    }
}