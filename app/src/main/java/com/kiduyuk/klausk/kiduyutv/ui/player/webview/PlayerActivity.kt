package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import android.os.Environment

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cursorView: MouseCursorView
    private var cursorX = 0f
    private var cursorY = 0f
    private val moveSpeed = 50f
    private var screenWidth = 0
    private var screenHeight = 0

    // ── Next Episode Variables ────────────────────────────────────────────────
    private var currentSeason = 1
    private var currentEpisode = 1
    private var totalEpisodesInSeason = 0
    private var totalSeasons = 1
    private var nextEpisodeButton: View? = null
    private var showNextEpisodeButton = false
    private var isCursorDisabled = false
    private var currentProviderUrl: String? = null
    private var currentProviderName: String = "VidLink"
    private var episodeInfoLoaded = false
    private var currentBaseUrl: String = "" // Store the base URL for the current provider

    /**
     * Extract the base URL (protocol + host) from a full URL.
     * Example: "https://vidlink.pro/tv/123/1/1?autoplay=true" -> "https://vidlink.pro"
     */
    private fun extractBaseUrl(fullUrl: String): String {
        return try {
            val urlObj = java.net.URL(fullUrl)
            "${urlObj.protocol}://${urlObj.host}"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract base URL from: $fullUrl, using fallback")
            // Fallback: extract manually
            val startIndex = if (fullUrl.startsWith("https://")) 8 else if (fullUrl.startsWith("http://")) 7 else 0
            val endIndex = fullUrl.indexOf('/', startIndex)
            if (endIndex > 0) fullUrl.substring(0, endIndex) else fullUrl
        }
    }

    /**
     * Build the TV show URL for the current provider with the given season and episode.
     * Uses the current provider's base URL and constructs the correct path pattern.
     */
    private fun buildProviderUrl(tmdbId: Int, season: Int, episode: Int): String {
        val baseUrl = currentBaseUrl.ifEmpty { extractBaseUrl(currentProviderUrl ?: "") }

        // Build URL based on provider name
        return when (currentProviderName) {
            "VidLink" -> "$baseUrl/tv/$tmdbId/$season/$episode?autoplay=true"
            "Videasy" -> "$baseUrl/tv/$tmdbId/$season/$episode?nextEpisode=true&autoplayNextEpisode=true&episodeSelector=true&overlay=true&color=8B5CF6"
            "VidFast" -> "$baseUrl/tv/$tmdbId/$season/$episode?autoPlay=true&nextButton=true&autoNext=true"
            "VidKing" -> "$baseUrl/embed/tv/$tmdbId/$season/$episode?autoPlay=true&nextEpisode=true&episodeSelector=true"
            "VidSrc" -> "$baseUrl/embed/tv/$tmdbId/$season/$episode"
            "Mapple" -> "$baseUrl/watch/tv/$tmdbId-$season-$episode"
            "Flixer" -> "$baseUrl/watch/tv/$tmdbId/$season/$episode"
            else -> "$baseUrl/tv/$tmdbId/$season/$episode?autoplay=true"
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "VideasyPlayer"
        private const val NEXT_EPISODE_THRESHOLD_SECONDS = 60L // Show button when 60 seconds remain

        // ── Stream sniffer ────────────────────────────────────────────────────
        private const val SNIFFER_TAG = "StreamSniffer"
        private val STREAM_EXTENSIONS = listOf(".m3u8", ".mpd", ".ts", ".mp4", ".webm", ".mkv")
        private val STREAM_KEYWORDS   = listOf("playlist", "manifest", "master", "/hls/", "/dash/", "chunklist")
    }

    // JavaScript interface class to receive messages from WebView (supports Videasy, VidKing, and VidLink)
    @Suppress("UNUSED")
    inner class VideasyJavaScriptInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            //Log.i(TAG, "PostMessage received: $message")
            try {
                val json = org.json.JSONObject(message)

                if (json.has("type") && json.getString("type") == "PLAYER_EVENT" && json.has("data")) {
                    val data = json.getJSONObject("data")
                    val id = if (data.has("mtmdbId")) {
                        data.get("mtmdbId").toString()
                    } else {
                        data.optString("id", "unknown")
                    }
                    val mediaType = data.optString("mediaType", "unknown")
                    val progress = data.optDouble("progress", 0.0)
                    val currentTime = data.optDouble("currentTime", 0.0)
                    val duration = data.optDouble("duration", 0.0)
                    val season = data.optInt("season", 0)
                    val episode = data.optInt("episode", 0)
                    val event = data.optString("event", "unknown")

                    val playerType = if (data.has("mtmdbId")) "VidLink" else "VidKing"
                } else {
                    val id = json.optString("id", json.optString("contentId", json.optString("movieId", json.optString("tvId", "unknown"))))
                    val type = json.optString("type", json.optString("contentType", json.optString("playerType", "unknown")))
                    val progress = json.optDouble("progress", json.optDouble("percent", 0.0))
                    val timestamp = json.optDouble("timestamp", json.optDouble("currentTime", json.optDouble("time", 0.0)))
                    val duration = json.optDouble("duration", json.optDouble("totalDuration", json.optDouble("totalTime", 0.0)))
                    val season = json.optInt("season", json.optInt("seasonNumber", 0))
                    val episode = json.optInt("episode", json.optInt("episodeNumber", 0))
                }

                runOnUiThread {
                    // Update UI or save progress as needed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }
    }

    // ── Stream sniffer JS bridge ──────────────────────────────────────────────
    /** Receives stream URLs detected by the injected XHR / fetch hooks. */
    @Suppress("UNUSED")
    inner class StreamSnifferInterface {
        @JavascriptInterface
        fun onStreamFound(url: String, source: String) {
            Log.i(SNIFFER_TAG, "[JS-$source] Stream URL detected: $url")
            appendUrlToFile(url, source)
        }

        private fun appendUrlToFile(url: String, source: String) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, "AllSniffedUrls.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

                FileOutputStream(file, true).use { fos ->
                    PrintWriter(fos).use { pw ->
                        pw.println("[$timestamp] [JS-$source] $url")
                    }
                }
            } catch (e: Exception) {
                Log.e(SNIFFER_TAG, "Error writing to AllSniffedUrls.txt: ${e.message}")
            }
        }
    }

    /** Returns true when [url] looks like a media stream. */
    private fun isStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return STREAM_EXTENSIONS.any { lower.contains(it) } ||
                STREAM_KEYWORDS.any  { lower.contains(it) }
    }

    // ── Cursor hide timer ──────────────────────────────────────────────────────
    private val cursorHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled) {
            cursorView.animate().alpha(0f).setDuration(500).start()
        }
    }

    // ── 15-second Kotlin-side progress logger ─────────────────────────────────
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            webView.evaluateJavascript("""
                (function() {
                    var v = document.querySelector('video');
                    if (v && v.duration > 0 && !isNaN(v.duration)) {
                        return JSON.stringify({
                            currentTime: v.currentTime,
                            duration:    v.duration,
                            progress:    (v.currentTime / v.duration) * 100,
                            paused:      v.paused,
                            ended:       v.ended
                        });
                    }
                    return null;
                })();
            """.trimIndent()) { result ->
                if (result != null && result != "null") {
                    try {
                        // evaluateJavascript wraps string results in extra quotes
                        val clean = result.trim('"').replace("\\\"", "\"")
                        val json = org.json.JSONObject(clean)
                        val progress = json.getDouble("progress")
                        val currentTime = json.getDouble("currentTime")
                        val duration = json.getDouble("duration")
                        val paused = json.getBoolean("paused")
                        val ended = json.getBoolean("ended")

                        Log.i(TAG, String.format(
                            "[Progress] %.1f%% — %.1fs / %.1fs | paused=%b ended=%b",
                            progress, currentTime, duration, paused, ended
                        ))

                        // Save progress to watch history every 15 seconds
                        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
                        val isTv = intent.getBooleanExtra("IS_TV", false)
                        if (tmdbId != -1) {
                            val repository = TmdbRepository()
                            // Convert seconds to milliseconds for playbackPosition
                            repository.updatePlaybackPosition(tmdbId, if (isTv) "tv" else "movie", currentTime.toLong())

                            // If it's a TV show, also ensure the current season and episode are up to date
                            // This fixes the issue where clicking "Next Episode" would leave the old episode number in the database
                            if (isTv) {
                                repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
                            }
                        }

                        // ── Next Episode Logic ──────────────────────────────────
                        val remainingSeconds = duration - currentTime
                        checkAndShowNextEpisodeButton(remainingSeconds, ended)
                        // ──────────────────────────────────────────────────────
                    } catch (e: Exception) {
                        Log.w(TAG, "Progress parse error: ${e.message}")
                    }
                } else {
                    Log.i(TAG, "[Progress] No video element found yet")
                }
            }
            progressHandler.postDelayed(this, 15_000L)
        }
    }

    /**
     * Check if we should show the next episode button.
     * Called when video progress is updated.
     */
    private fun checkAndShowNextEpisodeButton(remainingSeconds: Double, hasEnded: Boolean) {
        val isTv = intent.getBooleanExtra("IS_TV", false)

        // Only process for TV shows
        if (!isTv) return

        // If episode has ended, load next episode automatically
        if (hasEnded) {
            Log.i(TAG, "[NextEpisode] Episode ended, loading next episode")
            loadNextEpisode()
            return
        }

        // Check if we should show the button (when 60 seconds remain)
        val shouldShowButton = remainingSeconds <= NEXT_EPISODE_THRESHOLD_SECONDS && remainingSeconds > 0

        if (shouldShowButton && !showNextEpisodeButton) {
            Log.i(TAG, "[NextEpisode] Showing next episode button, $remainingSeconds seconds remaining")
            showNextEpisodeButton = true
            runOnUiThread {
                showNextEpisodeButtonUI()
            }
        }
    }

    /**
     * Load season details to get episode count and info.
     */
    private fun loadSeasonInfo() {
        if (episodeInfoLoaded) return

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)

        if (!isTv || tmdbId == -1) return

        lifecycleScope.launch {
            try {
                val repository = TmdbRepository()

                // Get season detail to count episodes
                val seasonDetail = repository.getSeasonDetail(tmdbId, currentSeason).getOrNull()
                if (seasonDetail != null) {
                    totalEpisodesInSeason = seasonDetail.episodes?.size ?: 0
                    Log.i(TAG, "[NextEpisode] Season $currentSeason has $totalEpisodesInSeason episodes")
                }

                // Get TV show detail to know total seasons
                val tvDetail = repository.getTvShowDetail(tmdbId).getOrNull()
                if (tvDetail != null) {
                    totalSeasons = tvDetail.numberOfSeasons ?: 1
                    Log.i(TAG, "[NextEpisode] TV show has $totalSeasons seasons")
                }

                episodeInfoLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "[NextEpisode] Error loading season info", e)
            }
        }
    }

    /**
     * Show the next episode button UI and disable cursor.
     */
    private fun showNextEpisodeButtonUI() {
        // Disable cursor
        isCursorDisabled = true
        cursorView.visibility = View.GONE

        // Show the next episode button
        nextEpisodeButton?.let { button ->
            button.visibility = View.VISIBLE
            button.requestFocus()
            Log.i(TAG, "[NextEpisode] Button shown and focused")
        }
    }

    /**
     * Hide the next episode button UI and enable cursor.
     */
    private fun hideNextEpisodeButtonUI() {
        // Enable cursor
        isCursorDisabled = false
        cursorView.visibility = View.VISIBLE

        // Hide the button
        nextEpisodeButton?.visibility = View.GONE
        showNextEpisodeButton = false
    }

    /**
     * Load the next episode.
     * If current episode is the last in the season, try to load first episode of next season.
     */
    private fun loadNextEpisode() {
        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        if (tmdbId == -1) return

        val isTv = intent.getBooleanExtra("IS_TV", false)
        if (!isTv) return

        var nextSeason = currentSeason
        var nextEp = currentEpisode + 1

        Log.i(TAG, "[NextEpisode] Current: S$currentSeason E$currentEpisode, TotalSeasons: $totalSeasons, TotalEpisodes: $totalEpisodesInSeason")

        // Check if we need to go to next season
        // If totalEpisodesInSeason is 0 (not loaded yet), we'll just try to load the next episode
        if (totalEpisodesInSeason > 0 && nextEp > totalEpisodesInSeason) {
            if (nextSeason < totalSeasons) {
                // Move to next season
                nextSeason++
                nextEp = 1
                Log.i(TAG, "[NextEpisode] Moving to Season $nextSeason, Episode 1")
            } else {
                // No more seasons, we're done
                Log.i(TAG, "[NextEpisode] No more episodes available")
                return
            }
        }

        // Update current episode info
        currentSeason = nextSeason
        currentEpisode = nextEp

        Log.i(TAG, "[NextEpisode] Loading: S$currentSeason E$currentEpisode")

        // Update intent extras
        intent.putExtra("SEASON_NUMBER", currentSeason)
        intent.putExtra("EPISODE_NUMBER", currentEpisode)

        // Save current position before switching
        savePlaybackPosition()

        // Build the new URL dynamically based on current provider
        val finalUrl = buildProviderUrl(tmdbId, currentSeason, currentEpisode)
        val timestamp = 0L // Start from beginning for new episode

        // Apply provider-specific timestamp parameter if needed
        val urlWithTimestamp = if (timestamp > 0) {
            when (currentProviderName) {
                "VidLink" -> "$finalUrl&startAt=$timestamp"
                "VidKing" -> "$finalUrl&progress=$timestamp"
                "Videasy" -> "$finalUrl&progress=$timestamp"
                "VidFast" -> "$finalUrl&startAt=$timestamp"
                else -> finalUrl
            }
        } else {
            finalUrl
        }

        // Hide button and reset cursor
        hideNextEpisodeButtonUI()

        // Reset episode info so it gets reloaded for the new season
        episodeInfoLoaded = false

        // Save to watch history
        val repository = TmdbRepository()
        repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)

        // Reload with new episode
        Log.i(TAG, "[NextEpisode] Loading Season $currentSeason, Episode $currentEpisode with URL: $urlWithTimestamp")
        webView.loadUrl(urlWithTimestamp)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fix for black screen with hardware acceleration on some Android TV devices
        // This ensures the window surface is ready for video content
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

        super.onCreate(savedInstanceState)

        // Note: AdvancedAdBlocker is now initialized in KiduyuTvApp

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        currentSeason = intent.getIntExtra("SEASON_NUMBER", 1)
        currentEpisode = intent.getIntExtra("EPISODE_NUMBER", 1)
        val title = intent.getStringExtra("TITLE") ?: "Unknown"

        if (tmdbId == -1) {
            finish()
            return
        }

        // Load season info for TV shows
        if (isTv) {
            loadSeasonInfo()
        }

        val repository = TmdbRepository()

        // Check if this media is already in watch history
        val existsInHistory = repository.isInWatchHistory(this, tmdbId, isTv)

        if (existsInHistory) {
            // Item already exists - just update season and episode info
            Log.i(TAG, "[WatchHistory] Item exists, updating season $currentSeason episode $currentEpisode")
            repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
        } else {
            // New item - save to watch history
            Log.i(TAG, "[WatchHistory] New item, saving to history")
            repository.saveToWatchHistory(
                this,
                WatchHistoryItem(
                    id = tmdbId,
                    title = title,
                    overview = intent.getStringExtra("OVERVIEW"),
                    posterPath = intent.getStringExtra("POSTER_PATH"),
                    backdropPath = intent.getStringExtra("BACKDROP_PATH"),
                    voteAverage = intent.getDoubleExtra("VOTE_AVERAGE", 0.0),
                    releaseDate = intent.getStringExtra("RELEASE_DATE"),
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

        // Store the provider URL and extract base URL for next episode
        currentProviderUrl = url
        currentBaseUrl = extractBaseUrl(url)

        val isVideasyPlayer = url.startsWith("https://player.videasy.net")
        val isVidKingPlayer = url.startsWith("https://www.vidking.net") || url.startsWith("https://vidking.")
        val isVidLinkPlayer = url.startsWith("https://vidlink.pro")
        val isTrackingEnabled = isVideasyPlayer || isVidKingPlayer || isVidLinkPlayer

        // Determine provider name
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

        Log.i(TAG, "[Provider] Selected: $currentProviderName, Base URL: $currentBaseUrl")

        // ── Layout ────────────────────────────────────────────────────────────
        val rootLayout = FrameLayout(this).apply {
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

                // Hardware Acceleration and Performance Settings
                cacheMode = WebSettings.LOAD_DEFAULT
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                useWideViewPort = true
                loadWithOverviewMode = true

                // Fix for FireTV/Chromium IllegalStateException: Warning: Router objects should be explicitly closed.
                // Disabling AndroidOverlay prevents the WebView from trying to create a separate
                // hardware surface for video, which is the source of the leaked Mojo Router.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeSetSafeBrowsingEnabled(this, false)
                }
            }

            // On some Android TV devices, LAYER_TYPE_HARDWARE causes a black screen
            // while audio plays. Using LAYER_TYPE_NONE allows the WebView to
            // manage its own internal hardware acceleration for the video
            // while keeping the view surface compatible.
            setLayerType(View.LAYER_TYPE_NONE, null)

            if (isTrackingEnabled) {
                addJavascriptInterface(VideasyJavaScriptInterface(), "VideasyInterface")
            }
            // Always register the stream sniffer so XHR/fetch hooks can report back
            addJavascriptInterface(StreamSnifferInterface(), "StreamSniffer")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url.toString()
                    if (AdvancedAdBlocker.shouldBlock(url)) {
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    // ── Stream sniffer (network level) ────────────────────────
                    if (isStreamUrl(url)) {
                        Log.i(SNIFFER_TAG, "[Network] Stream URL detected: $url")
                    }
                    // ─────────────────────────────────────────────────────────
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // Block all navigation attempts to keep the user on the initial stream URL
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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
                            function setMaxVolume() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    videos[i].volume = 1.0;
                                    videos[i].muted = false;
                                }
                            }
                            
                            ${if (isTrackingEnabled) """
                            function setupMessageListener() {
                                console.log('Player message listener initialized');
                                
                                window.addEventListener('message', function(event) {
                                    console.log('Message event received:', typeof event.data === 'string' ? event.data : JSON.stringify(event.data));
                                    try {
                                        if (window.VideasyInterface) {
                                            if (typeof event.data === 'string') {
                                                window.VideasyInterface.postMessage(event.data);
                                            } else {
                                                window.VideasyInterface.postMessage(JSON.stringify(event.data));
                                            }
                                        }
                                    } catch (e) {
                                        console.log('Error processing message: ' + e);
                                    }
                                });
                                
                                (function() {
                                    var originalPostMessage = window.postMessage;
                                    window.postMessage = function(message, targetOrigin, transfer) {
                                        console.log('postMessage intercepted:', typeof message === 'string' ? message : JSON.stringify(message));
                                        try {
                                            if (window.VideasyInterface) {
                                                if (typeof message === 'string') {
                                                    window.VideasyInterface.postMessage(message);
                                                } else {
                                                    window.VideasyInterface.postMessage(JSON.stringify(message));
                                                }
                                            }
                                        } catch (e) {
                                            console.log('Error capturing postMessage: ' + e);
                                        }
                                        return originalPostMessage.apply(this, arguments);
                                    };
                                })();
                                
                                // Monitor for video element events (metadata/timeupdate debug only)
                                function monitorVideoEvents() {
                                    var videos = document.getElementsByTagName('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        (function(video) {
                                            if (video._monitored) return;
                                            video._monitored = true;
                                            video.addEventListener('loadedmetadata', function() {
                                                console.log('Video loaded: duration=' + video.duration);
                                            });
                                        })(videos[i]);
                                    }
                                }
                                monitorVideoEvents();
                                setInterval(monitorVideoEvents, 3000);
                            }
                            """ else """
                            function setupMessageListener() {
                                console.log('Non-tracking player, skipping message listener');
                            }
                            """}
                            
                            blockRedirects();
                            removeAdsAdvanced();
                            setMaxVolume();
                            setupMessageListener();
                            setInterval(function() {
                                setMaxVolume();
                            }, 3000);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(AdvancedAdBlocker.getCss(), null)
                    view?.evaluateJavascript(advancedJs, null)

                    // ── Stream sniffer (JS / XHR / fetch level) ───────────────
                    val streamSnifferJs = """
                        (function() {
                            if (window.__streamSnifferInstalled) return;
                            window.__streamSnifferInstalled = true;

                            var STREAM_EXTS    = ['.m3u8', '.mpd', '.ts', '.mp4', '.webm', '.mkv'];
                            var STREAM_KEYS    = ['playlist', 'manifest', 'master', '/hls/', '/dash/', 'chunklist'];

                            function looksLikeStream(url) {
                                if (!url || typeof url !== 'string') return false;
                                var lower = url.toLowerCase();
                                for (var i = 0; i < STREAM_EXTS.length; i++) {
                                    if (lower.indexOf(STREAM_EXTS[i]) !== -1) return true;
                                }
                                for (var j = 0; j < STREAM_KEYS.length; j++) {
                                    if (lower.indexOf(STREAM_KEYS[j]) !== -1) return true;
                                }
                                return false;
                            }

                            function report(url, src) {
                                if (looksLikeStream(url) && window.StreamSniffer) {
                                    window.StreamSniffer.onStreamFound(url, src);
                                }
                            }

                            /* Hook XMLHttpRequest.open */
                            var origXhrOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                report(url, 'XHR');
                                return origXhrOpen.apply(this, arguments);
                            };

                            /* Hook fetch */
                            var origFetch = window.fetch;
                            window.fetch = function(input, init) {
                                var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                                report(url, 'fetch');
                                return origFetch.apply(this, arguments);
                            };

                            /* Hook MediaSource.addSourceBuffer (MSE / blob streams) */
                            if (window.MediaSource) {
                                var origAddSourceBuffer = MediaSource.prototype.addSourceBuffer;
                                MediaSource.prototype.addSourceBuffer = function(mimeType) {
                                    if (window.StreamSniffer) {
                                        window.StreamSniffer.onStreamFound('MSE:' + mimeType, 'MSE');
                                    }
                                    return origAddSourceBuffer.apply(this, arguments);
                                };
                            }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(streamSnifferJs, null)
                    // ─────────────────────────────────────────────────────────
                } // onPageFinished
            } // webViewClient

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false
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

        // ── Next Episode Button ────────────────────────────────────────────────
        val greenColor = android.graphics.Color.parseColor("#4CAF50")
        val redColor = android.graphics.Color.parseColor("#F44336")

        nextEpisodeButton = android.widget.Button(this).apply {
            text = "Next Episode"
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(greenColor)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            setPadding(60, 30, 60, 30)
            // Set minimum width for better visibility
            minimumWidth = 300
            setOnClickListener {
                Log.i(TAG, "[NextEpisode] Button clicked")
                loadNextEpisode()
            }
            // Change background to red when focused
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) redColor else greenColor)
                Log.i(TAG, "[NextEpisode] Button focus: $hasFocus")
            }
        }

        // Position the next episode button at bottom right
        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            bottomMargin = 200
            marginEnd = 50
        }

        rootLayout.addView(webView)
        rootLayout.addView(cursorView)
        rootLayout.addView(nextEpisodeButton, buttonParams)

        // Clean, no legacy fallback needed for minSdk 28+
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

        setContentView(rootLayout)
        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        rootLayout.post {
            screenWidth = rootLayout.width
            screenHeight = rootLayout.height
            cursorX = screenWidth / 2f
            cursorY = screenHeight / 2f
            updateCursorPosition()
            showCursorAndResetTimer()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()            }
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

    /**
     * Helper to safely set Safe Browsing.
     */
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
        // Stop progress tracking
        progressHandler.removeCallbacks(progressRunnable)
        cursorHideHandler.removeCallbacks(cursorHideRunnable)

        // Thoroughly clean up the WebView to prevent memory leaks and IllegalStateException
        if (::webView.isInitialized) {
            try {
                // Remove from parent layout
                (webView.parent as? ViewGroup)?.removeView(webView)

                // Clear state and stop loading
                webView.apply {
                    // Remove interfaces first
                    removeJavascriptInterface("VideasyInterface")
                    removeJavascriptInterface("StreamSniffer")

                    stopLoading()
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()

                    clearHistory()
                    //clearCache(true)
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
        // If next episode button is shown, handle D-pad for the button
        if (showNextEpisodeButton && nextEpisodeButton?.visibility == View.VISIBLE) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    // Let the button handle the key
                    return nextEpisodeButton?.dispatchKeyEvent(event) ?: false
                }
            }
        }

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
        // Don't handle D-pad when next episode button is shown
        if (showNextEpisodeButton && isCursorDisabled) {
            return false
        }

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
        if (!isCursorDisabled) {
            cursorView.x = cursorX
            cursorView.y = cursorY
            cursorView.invalidate()
        }
    }

    private fun simulateClick(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_DOWN, x, y, 0
        )
        val upEvent = MotionEvent.obtain(
            downTime, eventTime + 100,
            MotionEvent.ACTION_UP, x, y, 0
        )

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
        webView.evaluateJavascript("""
            (function() {
                var v = document.querySelector('video');
                if (v && v.duration > 0 && !isNaN(v.duration)) {
                    return v.currentTime;
                }
                return null;
            })();
        """.trimIndent()) { result ->
            if (result != null && result != "null") {
                try {
                    val currentTime = result.toDouble()
                    val tmdbId = intent.getIntExtra("TMDB_ID", -1)
                    val isTv = intent.getBooleanExtra("IS_TV", false)
                    if (tmdbId != -1) {
                        val repository = TmdbRepository()
                        repository.updatePlaybackPosition(tmdbId, if (isTv) "tv" else "movie", currentTime.toLong())

                        // If it's a TV show, also ensure the current season and episode are saved
                        if (isTv) {
                            repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
                        }

                        Log.i(TAG, "Final playback position saved: ${currentTime}s (S$currentSeason E$currentEpisode)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving final playback position: ${e.message}")
                }
            }
        }
    }
}
