package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
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
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import java.io.ByteArrayInputStream

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cursorView: MouseCursorView
    private var cursorX = 0f
    private var cursorY = 0f
    private val moveSpeed = 50f
    private var screenWidth = 0
    private var screenHeight = 0

    private var currentSeason = 1
    private var currentEpisode = 1
    private var isCursorDisabled = false
    private var currentProviderName: String = "VidLink"

    companion object {
        private const val TAG = "VideasyPlayer"
    }

    // JavaScript interface to receive messages from WebView (supports Videasy, VidKing, and VidLink)
    @Suppress("UNUSED")
    inner class VideasyJavaScriptInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            try {
                val json = org.json.JSONObject(message)

                if (json.has("type") && json.getString("type") == "PLAYER_EVENT" && json.has("data")) {
                    val data = json.getJSONObject("data")
                    // Process player events as needed
                } else {
                    // Process other message formats as needed
                }

                runOnUiThread {
                    // Update UI or save progress as needed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }
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
                            repository.updatePlaybackPosition(tmdbId, if (isTv) "tv" else "movie", currentTime.toLong())

                            if (isTv) {
                                repository.updateEpisodeInfo(tmdbId, "tv", currentSeason, currentEpisode)
                            }
                        }
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

        super.onCreate(savedInstanceState)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        currentSeason = intent.getIntExtra("SEASON_NUMBER", 1)
        currentEpisode = intent.getIntExtra("EPISODE_NUMBER", 1)
        val title = intent.getStringExtra("TITLE") ?: "Unknown"

        if (tmdbId == -1) {
            finish()
            return
        }

        val repository = TmdbRepository()

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
                cacheMode = WebSettings.LOAD_DEFAULT
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                useWideViewPort = true
                loadWithOverviewMode = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeSetSafeBrowsingEnabled(this, false)
                }
            }

            setLayerType(View.LAYER_TYPE_NONE, null)

            if (isTrackingEnabled) {
                addJavascriptInterface(VideasyJavaScriptInterface(), "VideasyInterface")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url.toString()
                    if (AdvancedAdBlocker.shouldBlock(reqUrl)) {
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    if (url != null) {
                        val cookieManager = CookieManager.getInstance()
                        val cookies = cookieManager.getCookie(url)
                        Log.i(TAG, "[Cookies] URL: $url")
                        Log.i(TAG, "[Cookies] Content: ${cookies ?: "No cookies found"}")
                    }

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
                }
            }

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

        rootLayout.addView(webView)
        rootLayout.addView(cursorView)

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
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.invalidate()
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