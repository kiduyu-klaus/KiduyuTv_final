package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.content.res.Configuration
import android.webkit.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
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
    private var isFireTV = false
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

    /**
     * Check if the device is an Amazon Fire TV or Fire Stick.
     * Uses two methods for maximum compatibility:
     * 1. Checks for amazon.hardware.fire_tv system feature
     * 2. Falls back to checking if Build.MODEL starts with "AFT"
     */
    private fun isFireTVDevice(context: Context): Boolean {
        // Check for Amazon's system feature identifier
        val isFireTvHardware = context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        
        // Fallback: Check if the Build.MODEL starts with "AFT" (used by Fire TVs and Fire Sticks)
        val isFireTvModel = Build.MODEL != null && Build.MODEL.startsWith("AFT", ignoreCase = true)
        
        return isFireTvHardware || isFireTvModel
    }

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

    // ── WebView sync handler ───────────────────────────────────────────────────
    private val syncHandler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            syncWebViewMediaState()
            syncHandler.postDelayed(this, 10000) // Sync every 10 seconds
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

        // Detect device type and show appropriate toast with device information
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val deviceManufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val deviceModel = Build.MODEL
        val deviceBrand = Build.BRAND.replaceFirstChar { it.uppercase() }

        if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
            val deviceType = if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_NORMAL) "Mobile" else "Tablet"
            Log.i(TAG, "[Device] $deviceType detected (${deviceBrand} $deviceModel), disabling cursor")

            // Show detailed toast for mobile/tablet devices
            val toastMessage = "Device: $deviceType | Brand: $deviceBrand | Model: $deviceModel"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@PlayerActivity, toastMessage, Toast.LENGTH_LONG).show()
            }
        } else {
            isFireTV = isFireTVDevice(this)
            Log.i(TAG, "[Device] TV detected (${deviceBrand} $deviceModel), cursor enabled, isFireTV=$isFireTV")

            // Show detailed toast for TV devices
            val deviceLabel = if (isFireTV) {
                "Fire TV (Amazon WebView)"
            } else {
                "Android TV (WebView)"
            }
            val toastMessage = "Device: $deviceLabel | Brand: $deviceBrand | Model: $deviceModel"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@PlayerActivity, toastMessage, Toast.LENGTH_LONG).show()
            }
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

        webView = createWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            setOnApplyWindowInsetsListener { _, insets -> insets }

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
                    // Fire TV desktop UA — streaming sites serve the correct player layout
                    "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                } else {
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
            }

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

            webViewClient = AdBlockerWebViewClient(
                onPageFinished = {
                    isPageLoading = false
                    Log.i(TAG, "[WebView] Page finished loading with AdBlocker")
                    injectVideoDetectionScript(this)
                    injectAdvancedPlayerScripts(this)
                    
                    // Run autoplay injection 3 times, every 4 seconds with WebView updates
                    runAutoplayWithRetry(3, 4000, this)
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
            }

            val iframeHtml = intent.getStringExtra("IFRAME_HTML")
            if (iframeHtml != null) {
                Toast.makeText(this@PlayerActivity, "Loading via IFRAME mode", Toast.LENGTH_SHORT).show()
                val baseUrl = com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager.getBaseUrl(currentProviderName)
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
     * ★ Inject advanced player scripts with enhanced ad blocking
     */
    private fun injectAdvancedPlayerScripts(view: WebView?) {
        val advancedJs = """
        (function() {
            function removeAdsAdvanced() {
                function killPopups() {
                    var closeSelectors = [
                        '[class*="close"]', '[id*="close"]',
                        '[class*="dismiss"]', '[aria-label="Close"]',
                        'button[class*="cancel"]', '.modal-close',
                        '[data-dismiss="modal"]'
                    ];
                    closeSelectors.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(btn) {
                            try { btn.click(); } catch(e) {}
                        });
                    });

                    document.querySelectorAll('div, section, aside').forEach(function(el) {
                        try {
                            var style = window.getComputedStyle(el);
                            var zIndex = parseInt(style.zIndex) || 0;
                            var pos = style.position;
                            if (
                                (pos === 'fixed' || pos === 'absolute') &&
                                zIndex > 100 &&
                                el.offsetWidth > 200 &&
                                el.offsetHeight > 100 &&
                                !el.querySelector('video') &&
                                !el.contains(document.querySelector('video'))
                            ) {
                                el.remove();
                            }
                        } catch(e) {}
                    });

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

                    document.body.style.overflow = 'auto';
                    document.documentElement.style.overflow = 'auto';
                }

                var style = document.createElement('style');
                style.innerHTML = `
                    [class*="overlay"],[id*="overlay"],
                    [class*="modal"],[id*="modal"],
                    [class*="popup"],[id*="popup"],
                    [class*="dialog"],[id*="dialog"],
                    [class*="interstitial"],[id*="interstitial"],
                    div[style*="position: fixed"],
                    div[style*="position:fixed"] {
                        display: none !important;
                        visibility: hidden !important;
                        pointer-events: none !important;
                    }
                    body { overflow: auto !important; }
                `;
                document.head && document.head.appendChild(style);

                killPopups();

                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        if (m.addedNodes.length > 0) {
                            setTimeout(killPopups, 300);
                        }
                    });
                });
                observer.observe(document.body, { childList: true, subtree: true });

                setInterval(killPopups, 3000);

                window.alert = function() { return undefined; };
                window.confirm = function() { return true; };
                window.prompt = function() { return ''; };
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
                    try {
                        if (video.hasAttribute('muted')) {
                            video.removeAttribute('muted');
                        }
                        video.setAttribute('muted', 'false');
                        video.volume = 1.0;
                        video.muted = false;
                        
                        if (video.paused) {
                            video.play().catch(function(e) {});
                        }
                        
                        video.addEventListener('volumechange', function() {
                            if (video.volume < 1.0 || video.muted) {
                                if (video.hasAttribute('muted')) {
                                    video.removeAttribute('muted');
                                }
                                video.setAttribute('muted', 'false');
                                video.volume = 1.0;
                                video.muted = false;
                            }
                        });
                    } catch(e) {}
                }

                function setMaxVolume() {
                    var videos = document.getElementsByTagName('video');
                    for (var i = 0; i < videos.length; i++) {
                        enforceVolume(videos[i]);
                    }
                    
                    var iframes = document.querySelectorAll('iframe');
                    for (var j = 0; j < iframes.length; j++) {
                        try {
                            var iframeVideos = iframes[j].contentDocument?.querySelectorAll('video');
                            if (iframeVideos) {
                                for (var k = 0; k < iframeVideos.length; k++) {
                                    enforceVolume(iframeVideos[k]);
                                }
                            }
                        } catch(e) {}
                    }
                }
                
                setMaxVolume();
                setTimeout(setMaxVolume, 2000);
                setTimeout(setMaxVolume, 5000);
                setTimeout(setMaxVolume, 10000);

                function monitorVideoEvents() {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(video => {
                        if (video._monitored) return;
                        video._monitored = true;
                        enforceVolume(video);
                        video.addEventListener('loadedmetadata', () => {
                            sendVideoProgress();
                            enforceVolume(video);
                        });
                        video.addEventListener('ended', () => sendVideoProgress());
                        video.addEventListener('timeupdate', function() {
                            if (!video._lastProgressUpdate || Date.now() - video._lastProgressUpdate > 1000) {
                                sendVideoProgress();
                                video._lastProgressUpdate = Date.now();
                            }
                        });
                        video.addEventListener('play', function() {
                            enforceVolume(video);
                        });
                    });
                }

                function observeVideoElements() {
                    const observer = new MutationObserver(() => {
                        setMaxVolume();
                        monitorVideoEvents();
                    });
                    observer.observe(document.body, { childList: true, subtree: true });
                }

                monitorVideoEvents();
                observeVideoElements();
                setInterval(monitorVideoEvents, 5000);
                setInterval(() => setMaxVolume(), 5000);
                setInterval(sendVideoProgress, 15000);
            }

            blockRedirects();
            removeAdsAdvanced();
            setupMessageListener();
        })();
        """.trimIndent()

        view?.evaluateJavascript(advancedJs, null)
    }

    /**
     * ★ Inject autoplay script with forced execution
     */
    private fun injectAutoplayScript(view: WebView?) {
        val autoplayJs = """
        (function() {
            console.log('[AutoPlay] Initializing forced autoplay');
            
            function hideOverlaysAndUpdateView() {
                // Hide all overlay elements that block video visibility
                var overlaySelectors = [
                    '.fixed.inset-0.bg-black\\/60',  // Dark overlay behind play button
                    '[class*="overlay"][class*="bg-black"]',
                    '[class*="overlay"]:not(video)',
                    '.bg-black\\/60',
                    '.bg-opacity-60',
                    '.absolute.inset-0:not(video)',
                    'div[class*="centered"]:not(video)',
                    'div[class*="play-overlay"]',
                    '[class*="player-overlay"]',
                    '.video-controls-overlay',
                    // Hide play buttons and big play buttons
                    '.rounded-full.bg-white\\/95',
                    'button[class*="rounded-full"][class*="bg-white"]',
                    '.vjs-big-play-button',
                    '.jw-play-button',
                    '.plyr__play',
                    '[class*="play-button"]:not(video)',
                    // Hide thumbnail/preview
                    'video ~ div:not(video):first-of-type',
                    '[class*="poster"]:not(video)',
                    '[class*="thumbnail"]'
                ];
                
                overlaySelectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) {
                            // Don't hide the actual video element
                            if (el.tagName !== 'VIDEO' && el.tagName !== 'IFRAME') {
                                // Check if element is over a playing video
                                var isOverVideo = false;
                                var parent = el.parentElement;
                                while (parent) {
                                    if (parent.querySelector && parent.querySelector('video[paused=false]')) {
                                        isOverVideo = true;
                                        break;
                                    }
                                    parent = parent.parentElement;
                                }
                                
                                // Only hide if video is actually playing beneath
                                var videos = document.querySelectorAll('video');
                                for (var v = 0; v < videos.length; v++) {
                                    if (!videos[v].paused && videos[v].currentTime > 0) {
                                        el.style.display = 'none';
                                        el.style.visibility = 'hidden';
                                        el.style.opacity = '0';
                                        el.style.pointerEvents = 'none';
                                        console.log('[AutoPlay] Hidden overlay:', selector);
                                        break;
                                    }
                                }
                            }
                        });
                    } catch(e) {}
                });
                
                // Force video to be visible and in front
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    if (!v.paused) {
                        // Ensure video has proper z-index
                        v.style.position = 'relative';
                        v.style.zIndex = '1';
                        
                        // Force show video element
                        v.style.display = 'block';
                        v.style.visibility = 'visible';
                        
                        // Find and hide parent overlays
                        var parent = v.parentElement;
                        while (parent && parent !== document.body) {
                            var computedStyle = window.getComputedStyle(parent);
                            if (computedStyle.position === 'absolute' || computedStyle.position === 'fixed') {
                                // Check if this is an overlay by checking background opacity
                                var bgColor = computedStyle.backgroundColor;
                                if (bgColor && bgColor.includes('rgba') && bgColor.includes(', 0.')) {
                                    parent.style.display = 'none';
                                    console.log('[AutoPlay] Hidden parent overlay');
                                }
                            }
                            parent = parent.parentElement;
                        }
                        
                        // Dispatch events to update UI
                        v.dispatchEvent(new Event('playing'));
                        v.dispatchEvent(new Event('progress'));
                    }
                });
            }
            
            function clickPlayButton() {
                var selectors = [
                    'button.rounded-full.bg-white\\/95',
                    'button[class*="rounded-full"][class*="bg-white"]',
                    'button[class*="w-10"][class*="h-10"]',
                    'button.rounded-full.bg-white',
                    'button svg[viewBox="0 0 24 24"] path[d="M8 5v14l11-7z"]',
                    'button svg path[d*="M8 5v14l11-7z"]',
                    'button:has(svg[viewBox="0 0 24 24"])',
                    '[class*="play"] button',
                    'button[aria-label*="play" i]',
                    '.vjs-big-play-button',
                    '.jw-icon-playback',
                    '.plyr__play',
                    '.mejs__play button',
                    // Additional selectors for VidLink/Stremio style players
                    'button[class*="play"][class*="center"]',
                    '[class*="rounded"][class*="white"]:has(svg)',
                    'div[role="button"]:has(svg path[d*="M8"])'
                ];
                
                for (var selector of selectors) {
                    try {
                        var buttons = document.querySelectorAll(selector);
                        for (var i = 0; i < buttons.length; i++) {
                            var btn = buttons[i];
                            var rect = btn.getBoundingClientRect();
                            // Only click visible, reasonably sized buttons
                            if (rect.width > 20 && rect.height > 20 && rect.width < 200 && rect.height < 200) {
                                if (btn && btn.click) {
                                    btn.click();
                                    console.log('[AutoPlay] Clicked play button:', selector);
                                    
                                    // Immediately try to hide any overlays
                                    setTimeout(hideOverlaysAndUpdateView, 500);
                                    
                                    return true;
                                }
                            }
                        }
                    } catch(e) {}
                }
                
                // Fallback: find any button with play icon SVG
                var allButtons = document.querySelectorAll('button');
                for (var i = 0; i < allButtons.length; i++) {
                    var btn = allButtons[i];
                    var svgs = btn.querySelectorAll('svg');
                    for (var j = 0; j < svgs.length; j++) {
                        var svgHtml = svgs[j].innerHTML;
                        if (svgHtml.includes('M8 5v14l11-7z') || svgHtml.includes('play')) {
                            var rect = btn.getBoundingClientRect();
                            if (rect.width > 20 && rect.height > 20) {
                                btn.click();
                                console.log('[AutoPlay] Clicked button with play SVG');
                                
                                // Immediately try to hide any overlays
                                setTimeout(hideOverlaysAndUpdateView, 500);
                                
                                return true;
                            }
                        }
                    }
                }
                
                return false;
            }
            
            function forcePlay(video) {
                if (!video) return false;

                try {
                    // Remove any overlay blocking the video
                    var parent = video.parentElement;
                    while (parent && parent !== document.body) {
                        var style = window.getComputedStyle(parent);
                        if (style.backgroundColor && style.backgroundColor.includes('rgba')) {
                            // This is likely an overlay
                            var alpha = style.backgroundColor.match(/[\d.]+(?=,)/);
                            if (alpha && parseFloat(alpha) > 0.3) {
                                parent.style.display = 'none';
                            }
                        }
                        parent = parent.parentElement;
                    }
                    
                    if (video.hasAttribute('muted')) {
                        video.removeAttribute('muted');
                    }
                    video.setAttribute('muted', 'false');
                    video.muted = false;
                    video.volume = 1.0;
                    video.autoplay = true;
                    video.playsInline = true;
                    
                    var playPromise = video.play();
                    
                    if (playPromise !== undefined) {
                        playPromise
                            .then(() => {
                                console.log('[AutoPlay] Playback started successfully');
                                
                                // Hide overlays immediately when playback starts
                                hideOverlaysAndUpdateView();
                                
                                if (window.VideasyInterface) {
                                    window.VideasyInterface.postMessage(JSON.stringify({
                                        type: 'autoplay_success',
                                        timestamp: Date.now()
                                    }));
                                }
                            })
                            .catch(err => {
                                console.log('[AutoPlay] Initial play failed:', err);
                                video.muted = true;
                                video.setAttribute('muted', 'true');
                                video.play().then(() => {
                                    console.log('[AutoPlay] Playback started muted');
                                    
                                    // Hide overlays even with muted autoplay
                                    hideOverlaysAndUpdateView();
                                    
                                    setTimeout(function() {
                                        video.muted = false;
                                        video.removeAttribute('muted');
                                        video.volume = 1.0;
                                        hideOverlaysAndUpdateView();
                                    }, 1000);
                                }).catch(() => {});
                            });
                    }
                    return true;
                } catch(e) {
                    console.log('[AutoPlay] Error:', e);
                    return false;
                }
            }

            function searchAndPlay() {
                // First, hide any existing overlays
                hideOverlaysAndUpdateView();
                
                if (clickPlayButton()) {
                    setTimeout(hideOverlaysAndUpdateView, 300);
                    return true;
                }
                
                let video = document.querySelector('video');
                if (video) {
                    // Check if video is already playing
                    if (!video.paused && video.currentTime > 0) {
                        hideOverlaysAndUpdateView();
                        return true;
                    }
                    
                    if (forcePlay(video)) {
                        setTimeout(hideOverlaysAndUpdateView, 500);
                        return true;
                    }
                }
                
                // Try iframes
                const iframes = document.querySelectorAll('iframe');
                for (let iframe of iframes) {
                    try {
                        const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
                        const iframeVideo = iframeDoc.querySelector('video');
                        if (iframeVideo) {
                            if (!iframeVideo.paused && iframeVideo.currentTime > 0) {
                                hideOverlaysAndUpdateView();
                                return true;
                            }
                            
                            if (forcePlay(iframeVideo)) {
                                // Hide iframe parent overlays
                                var iframeParent = iframe.parentElement;
                                while (iframeParent && iframeParent !== document.body) {
                                    var style = window.getComputedStyle(iframeParent);
                                    if (style.backgroundColor && style.backgroundColor.includes('rgba')) {
                                        iframeParent.style.display = 'none';
                                    }
                                    iframeParent = iframeParent.parentElement;
                                }
                                setTimeout(hideOverlaysAndUpdateView, 500);
                                return true;
                            }
                        }
                        
                        const iframeButtons = iframeDoc.querySelectorAll('button');
                        for (let btn of iframeButtons) {
                            if (btn.className && (btn.className.includes('rounded-full') || btn.className.includes('play'))) {
                                var rect = btn.getBoundingClientRect();
                                if (rect.width > 20 && rect.height > 20) {
                                    btn.click();
                                    console.log('[AutoPlay] Clicked button in iframe');
                                    setTimeout(hideOverlaysAndUpdateView, 500);
                                    return true;
                                }
                            }
                        }
                    } catch(e) {}
                }
                return false;
            }
            
            searchAndPlay();
            
            let attempts = 0;
            const maxAttempts = 15;
            const delays = [100, 300, 500, 800, 1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 8000, 10000, 12000];
            
            function attemptPlay() {
                if (attempts >= maxAttempts) {
                    console.log('[AutoPlay] Max attempts reached');
                    // Final attempt to hide overlays even if play didn't trigger
                    hideOverlaysAndUpdateView();
                    return;
                }
                
                const success = searchAndPlay();
                attempts++;
                
                // After each attempt, also try to hide overlays
                hideOverlaysAndUpdateView();
                
                if (!success && attempts < maxAttempts) {
                    const delay = delays[attempts] || 3000;
                    console.log('[AutoPlay] Attempt ' + attempts + '/' + maxAttempts + ' failed, retrying in ' + delay + 'ms');
                    setTimeout(attemptPlay, delay);
                } else if (success) {
                    console.log('[AutoPlay] Success on attempt ' + attempts);
                    // Keep checking and hiding overlays for a while after success
                    for (var i = 1; i <= 5; i++) {
                        setTimeout(hideOverlaysAndUpdateView, i * 1000);
                    }
                }
            }
            
            setTimeout(attemptPlay, delays[0]);
            
            // Periodic overlay check - keep hiding overlays for the entire session
            setInterval(hideOverlaysAndUpdateView, 2000);
            
            const observer = new MutationObserver(function(mutations) {
                let shouldAttempt = false;
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        for (let i = 0; i < mutation.addedNodes.length; i++) {
                            let node = mutation.addedNodes[i];
                            if (node.tagName === 'VIDEO' || 
                                (node.tagName === 'BUTTON' && node.className && 
                                 (node.className.includes('rounded-full') || node.className.includes('play')))) {
                                shouldAttempt = true;
                                break;
                            }
                        }
                    }
                });
                if (shouldAttempt) {
                    console.log('[AutoPlay] New content detected, attempting play');
                    setTimeout(searchAndPlay, 100);
                }
                
                // Also trigger overlay hide on any DOM change
                hideOverlaysAndUpdateView();
            });
            
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
            
            let periodicChecks = 0;
            const periodicInterval = setInterval(function() {
                periodicChecks++;
                if (searchAndPlay() || periodicChecks >= 15) {
                    clearInterval(periodicInterval);
                }
            }, 2000);
            
        })();
        """.trimIndent()

        view?.evaluateJavascript(autoplayJs, null)
    }

    /**
     * Force autoplay when page finishes loading with WebView state sync
     */
    private fun forceAutoplayOnPageLoad(view: WebView?) {
        val forceAutoplayJs = """
        (function() {
            console.log('[ForceAutoplay] Starting forced autoplay on page load');
            
            function updateWebViewState() {
                var event = new Event('resize');
                window.dispatchEvent(event);
                
                window.scrollBy(0, 1);
                window.scrollBy(0, -1);
                
                var customEvent = new CustomEvent('webviewUpdate', { detail: { forceUpdate: true } });
                document.dispatchEvent(customEvent);
            }
            
            function findAndPlayVideo() {
                var played = false;
                
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    var video = videos[i];
                    if (tryPlayVideo(video)) {
                        console.log('[ForceAutoplay] Successfully played direct video');
                        played = true;
                    }
                }
                
                var iframes = document.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var iframeDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        var iframeVideos = iframeDoc.querySelectorAll('video');
                        for (var j = 0; j < iframeVideos.length; j++) {
                            if (tryPlayVideo(iframeVideos[j])) {
                                console.log('[ForceAutoplay] Successfully played iframe video');
                                played = true;
                            }
                        }
                    } catch(e) {}
                }
                
                var playButtonSelectors = [
                    'button[class*="play"]', 'button[id*="play"]',
                    'button[aria-label*="Play" i]', 'button[aria-label*="play" i]',
                    'button[title*="Play" i]', 'button[title*="play" i]',
                    '[class*="play-button"]', '[class*="play-btn"]', '[class*="play_btn"]',
                    'button.rounded-full.bg-white', 'button[class*="rounded-full"][class*="bg-white"]',
                    'button svg[viewBox*="24"] path[d*="M8 5v14l11-7z"]',
                    '.plyr__controls button[data-plyr="play"]', '.video-js .vjs-big-play-button',
                    '.jwplayer .jw-icon-playback'
                ];
                
                for (var selector of playButtonSelectors) {
                    try {
                        var buttons = document.querySelectorAll(selector);
                        for (var i = 0; i < buttons.length; i++) {
                            var btn = buttons[i];
                            if (btn && btn.click) {
                                btn.click();
                                console.log('[ForceAutoplay] Clicked play button with selector:', selector);
                                played = true;
                                var clickEvent = new MouseEvent('click', {
                                    view: window,
                                    bubbles: true,
                                    cancelable: true
                                });
                                btn.dispatchEvent(clickEvent);
                            }
                        }
                    } catch(e) {}
                }
                
                if (played) {
                    updateWebViewState();
                    
                    setTimeout(function() {
                        var mediaElements = document.querySelectorAll('video, audio');
                        mediaElements.forEach(function(media) {
                            var playEvent = new Event('play');
                            media.dispatchEvent(playEvent);
                            
                            if (media.paused === false) {
                                var timeUpdateEvent = new Event('timeupdate');
                                media.dispatchEvent(timeUpdateEvent);
                            }
                        });
                        
                        window.dispatchEvent(new Event('orientationchange'));
                        document.body.dispatchEvent(new Event('touchstart'));
                    }, 100);
                }
                
                return played;
            }
            
            function tryPlayVideo(video) {
                if (!video) return false;
                
                try {
                    if (video.hasAttribute('muted')) {
                        video.removeAttribute('muted');
                    }
                    video.setAttribute('muted', 'false');
                    video.muted = false;
                    video.volume = 1.0;
                    video.autoplay = true;
                    video.playsInline = true;
                    
                    var playPromise = video.play();
                    
                    if (playPromise !== undefined) {
                        playPromise.then(function() {
                            console.log('[ForceAutoplay] Video playing successfully');
                            
                            var playingEvent = new Event('playing');
                            video.dispatchEvent(playingEvent);
                            
                            var canPlayEvent = new Event('canplaythrough');
                            video.dispatchEvent(canPlayEvent);
                            
                            if (navigator.mediaSession) {
                                navigator.mediaSession.playbackState = 'playing';
                            }
                            
                            return true;
                        }).catch(function(error) {
                            console.log('[ForceAutoplay] Play failed:', error);
                            video.muted = true;
                            video.setAttribute('muted', 'true');
                            video.play().then(function() {
                                console.log('[ForceAutoplay] Playback started muted');
                                setTimeout(function() {
                                    video.muted = false;
                                    video.removeAttribute('muted');
                                    video.volume = 1.0;
                                    var unmuteEvent = new Event('volumechange');
                                    video.dispatchEvent(unmuteEvent);
                                }, 1000);
                            }).catch(function(e) {});
                            return false;
                        });
                        return true;
                    }
                } catch(e) {
                    console.log('[ForceAutoplay] Error playing video:', e);
                }
                return false;
            }
            
            function wakeUpWebView() {
                var touchStart = new TouchEvent('touchstart', {
                    bubbles: true,
                    cancelable: true,
                    touches: [{ identifier: 0, target: document.body }]
                });
                document.body.dispatchEvent(touchStart);
                
                var touchEnd = new TouchEvent('touchend', {
                    bubbles: true,
                    cancelable: true,
                    touches: []
                });
                document.body.dispatchEvent(touchEnd);
                
                window.focus();
                document.body.focus();
                document.body.offsetHeight;
                
                var mediaElements = document.querySelectorAll('video');
                mediaElements.forEach(function(media) {
                    if (!media.paused) {
                        var timeUpdate = new Event('timeupdate');
                        media.dispatchEvent(timeUpdate);
                    }
                });
            }
            
            var played = findAndPlayVideo();
            wakeUpWebView();
            
            if (!played) {
                console.log('[ForceAutoplay] Initial attempt failed, scheduling retries');
                
                var retries = [100, 300, 500, 1000, 2000, 3000, 5000, 8000];
                retries.forEach(function(delay, index) {
                    setTimeout(function() {
                        console.log('[ForceAutoplay] Retry ' + (index + 1) + '/' + retries.length);
                        findAndPlayVideo();
                        wakeUpWebView();
                        
                        var visibleButtons = document.querySelectorAll('button');
                        for (var i = 0; i < visibleButtons.length; i++) {
                            var btn = visibleButtons[i];
                            var rect = btn.getBoundingClientRect();
                            if (rect.width > 0 && rect.height > 0 && rect.width < 200 && rect.height < 200) {
                                btn.click();
                                console.log('[ForceAutoplay] Clicked visible button');
                                break;
                            }
                        }
                    }, delay);
                });
            } else {
                setTimeout(wakeUpWebView, 200);
            }
            
            var observer = new MutationObserver(function(mutations) {
                var needsPlay = false;
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        for (var i = 0; i < mutation.addedNodes.length; i++) {
                            var node = mutation.addedNodes[i];
                            if (node.tagName === 'VIDEO' || 
                                (node.tagName === 'BUTTON' && (node.className || '').includes('rounded-full'))) {
                                needsPlay = true;
                                break;
                            }
                        }
                    }
                });
                if (needsPlay) {
                    console.log('[ForceAutoplay] New video/button detected, attempting play');
                    setTimeout(function() {
                        findAndPlayVideo();
                        wakeUpWebView();
                    }, 100);
                }
            });
            
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
            
            var checkCount = 0;
            var maxChecks = 20;
            var interval = setInterval(function() {
                checkCount++;
                var videos = document.querySelectorAll('video');
                var isPlaying = false;
                
                for (var i = 0; i < videos.length; i++) {
                    if (!videos[i].paused && videos[i].currentTime > 0) {
                        isPlaying = true;
                        break;
                    }
                }
                
                if (!isPlaying && checkCount < maxChecks) {
                    console.log('[ForceAutoplay] Video not playing, retrying...');
                    findAndPlayVideo();
                    wakeUpWebView();
                } else if (isPlaying) {
                    console.log('[ForceAutoplay] Video is playing, sending progress update');
                    videos.forEach(function(video) {
                        if (!video.paused) {
                            var timeUpdate = new Event('timeupdate');
                            video.dispatchEvent(timeUpdate);
                        }
                    });
                    // Trigger immediate progress save for Continue Watching feature
                    if (typeof sendVideoProgress === 'function') {
                        sendVideoProgress();
                    } else if (window.VideasyInterface) {
                        // Fallback: directly send video state to native layer
                        var v = videos[0];
                        if (v && v.duration > 0) {
                            var contentInfo = { type: 'tv', id: null, season: 1, episode: 1 };
                            try {
                                var url = window.location.href;
                                var match = url.match(/\/tv\/(\d+)\/(\d+)\/(\d+)/);
                                if (match) {
                                    contentInfo.type = 'tv';
                                    contentInfo.id = parseInt(match[1]);
                                    contentInfo.season = parseInt(match[2]);
                                    contentInfo.episode = parseInt(match[3]);
                                }
                            } catch(e) {}
                            
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
                            window.VideasyInterface.postMessage(JSON.stringify(progressData));
                        }
                    }
                }
                
                if (checkCount >= maxChecks) {
                    clearInterval(interval);
                }
            }, 2000);
            
            var updateInterval = setInterval(function() {
                var videos = document.querySelectorAll('video');
                var anyPlaying = false;
                videos.forEach(function(video) {
                    if (!video.paused) {
                        anyPlaying = true;
                        var timeUpdate = new Event('timeupdate');
                        video.dispatchEvent(timeUpdate);
                    }
                });
                
                // Also trigger progress save during periodic updates
                if (anyPlaying && typeof sendVideoProgress === 'function') {
                    sendVideoProgress();
                }
                
                if (!anyPlaying) {
                    clearInterval(updateInterval);
                }
            }, 5000);
            
        })();
        """.trimIndent()
        
        view?.evaluateJavascript(forceAutoplayJs, null)
    }

    /**
     * Force WebView to update its internal state
     */
    private fun forceWebViewUpdate() {
        webView.post {
            try {
                webView.invalidate()
                webView.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null)
                webView.requestFocus()
                webView.onResume()
                Log.d(TAG, "[WebView] Forced WebView state update")
            } catch (e: Exception) {
                Log.e(TAG, "[WebView] Error forcing update: ${e.message}")
            }
        }
    }

    /**
     * Sync WebView media state with JavaScript
     */
    private fun syncWebViewMediaState() {
        webView.evaluateJavascript(
            """
            (function() {
                var videos = document.querySelectorAll('video');
                var state = {
                    hasVideo: videos.length > 0,
                    isPlaying: false,
                    currentTime: 0,
                    duration: 0
                };
                
                if (videos.length > 0) {
                    var v = videos[0];
                    state.isPlaying = !v.paused && !v.ended;
                    state.currentTime = v.currentTime;
                    state.duration = v.duration;
                    
                    if (state.isPlaying) {
                        var timeUpdate = new Event('timeupdate');
                        v.dispatchEvent(timeUpdate);
                        var playingEvent = new Event('playing');
                        v.dispatchEvent(playingEvent);
                    }
                }
                
                return JSON.stringify(state);
            })();
            """.trimIndent()
        ) { result ->
            try {
                Log.d(TAG, "[WebView Sync] Media state: $result")
            } catch (e: Exception) {
                Log.e(TAG, "[WebView Sync] Error: ${e.message}")
            }
        }
    }

    /**
     * Check if autoplay was successful and retry if needed
     */
    private fun verifyAutoplaySuccess() {
        Handler(Looper.getMainLooper()).postDelayed({
            webView.evaluateJavascript(
                """
                (function() {
                    var videos = document.querySelectorAll('video');
                    if (videos.length > 0) {
                        var isPlaying = !videos[0].paused && !videos[0].ended && videos[0].currentTime > 0;
                        return isPlaying ? 'playing' : 'not_playing';
                    }
                    return 'no_video';
                })();
                """.trimIndent()
            ) { result ->
                when (result) {
                    "playing" -> {
                        Log.i(TAG, "[Autoplay] Verification: Video is playing")
                        // Force WebView UI to update since video started playing via autoplay
                        // This ensures the play button overlay is updated to pause state
                        forceWebViewUpdate()
                        syncWebViewMediaState()
                    }
                    "not_playing" -> {
                        Log.w(TAG, "[Autoplay] Verification: Video not playing, forcing again")
                        forceAutoplayOnPageLoad(webView)
                        forceWebViewUpdate()
                    }
                    else -> Log.w(TAG, "[Autoplay] Verification: No video element found")
                }
            }
        }, 5000)
    }

    /**
     * Run autoplay injection multiple times with intervals and WebView updates.
     * This ensures the video player is properly initialized and started.
     * 
     * @param maxAttempts Number of times to run autoplay (default: 3)
     * @param intervalMs Interval between attempts in milliseconds (default: 4000ms)
     * @param view The WebView instance to inject scripts into
     */
    private fun runAutoplayWithRetry(maxAttempts: Int = 3, intervalMs: Long = 4000, view: WebView?) {
        var currentAttempt = 0
        
        fun runAttempt() {
            if (currentAttempt >= maxAttempts) {
                Log.i(TAG, "[Autoplay] All $maxAttempts attempts completed")
                verifyAutoplaySuccess()
                return
            }
            
            currentAttempt++
            Log.i(TAG, "[Autoplay] Running attempt $currentAttempt of $maxAttempts")
            
            // Inject autoplay scripts
            injectAutoplayScript(view)
            forceAutoplayOnPageLoad(view)
            
            // Force WebView update to ensure UI reflects the injected scripts
            forceWebViewUpdate()
            
            // Dispatch events to update the WebView state
            view?.evaluateJavascript(
                """
                (function() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        if (!video.paused) {
                            video.dispatchEvent(new Event('playing'));
                            video.dispatchEvent(new Event('timeupdate'));
                            video.dispatchEvent(new Event('progress'));
                        }
                    });
                    window.dispatchEvent(new Event('resize'));
                })();
                """.trimIndent(), null
            )
            
            // Schedule next attempt if not the last one
            if (currentAttempt < maxAttempts) {
                Handler(Looper.getMainLooper()).postDelayed({
                    runAttempt()
                }, intervalMs)
            } else {
                // After final attempt, verify and do final WebView update
                Handler(Looper.getMainLooper()).postDelayed({
                    verifyAutoplaySuccess()
                    forceWebViewUpdate()
                }, 2000)
            }
        }
        
        // Start the first attempt immediately
        runAttempt()
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
        syncHandler.postDelayed(syncRunnable, 5000)
        setupImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        progressHandler.removeCallbacks(progressRunnable)
        syncHandler.removeCallbacks(syncRunnable)
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        syncHandler.removeCallbacks(syncRunnable)
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
     * Returns AmazonWebView on Fire TV devices, standard WebView everywhere else.
     * AmazonWebView is a system class on Fire OS — accessed via reflection so the
     * app compiles and runs normally on non-Amazon devices.
     * 
     * For Fire TV, hardware and software acceleration is enabled for optimal video playback.
     */
    private fun createWebView(context: Context): WebView {
        var webView: WebView
        
        val isHardwareAccelerated =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

        if (isFireTV) {
            try {
                val clazz = Class.forName("com.amazon.android.webkit.AmazonWebView")
                val constructor = clazz.getConstructor(Context::class.java)
                val instance = constructor.newInstance(context) as WebView
                webView = instance
            } catch (e: Exception) {
                Log.w(TAG, "[WebView] AmazonWebView unavailable, falling back to standard WebView: ${e.message}")
                webView = WebView(context)
            }

            // Fire TV: always use software rendering regardless of the hardware acceleration flag.
            // With LAYER_TYPE_HARDWARE the video surface (SurfaceView) renders on a separate
            // hardware overlay that does not composite with the WebView GPU layer, producing a
            // black screen on certain streams. Software rendering composites everything onto one
            // canvas and eliminates the issue.
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            Log.i(TAG, "[WebView] Fire TV: software rendering forced to prevent black screen")
        } else {
            webView = WebView(context)

            if (isHardwareAccelerated) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                Log.i(TAG, "[WebView] Hardware acceleration enabled")
            } else {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.w(TAG, "[WebView] Hardware acceleration unavailable, falling back to software rendering")
            }
        }
        
        return webView
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