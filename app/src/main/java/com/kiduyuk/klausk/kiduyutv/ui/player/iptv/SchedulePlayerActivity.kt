package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.ChannelWatchPage
import com.kiduyuk.klausk.kiduyutv.data.model.PlayerOption
import com.kiduyuk.klausk.kiduyutv.data.repository.ScheduleRepository
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schedule Player Activity for playing scheduled channels from dlhd.pk
 * Extends the existing PlayerActivity functionality with schedule-specific features
 * Takes an iframe HTML as intent extra and plays the scheduled channel in WebView
 * Includes a focusable row of player source options at the top for easy stream switching
 * Uses ChannelWatchPage and playerOptions for handling multiple streams
 */
class SchedulePlayerActivity : ComponentActivity() {

    private lateinit var webView: android.webkit.WebView
    private lateinit var rootLayout: FrameLayout

    private var currentIframeHtml: String? = null
    private var channelName: String = "Channel"
    private var eventTitle: String = "Channel"

    // FIX: playerOptions and selectedPlayerIndex backed by mutableStateOf so
    // the Compose top bar recomposes automatically when these change.
    private var playerOptions by mutableStateOf<List<PlayerOption>>(emptyList())
    private var selectedPlayerIndex by mutableStateOf(0)

    private var channelWatchPage: ChannelWatchPage? = null

    // Direct iframe URLs passed from scraped channels
    private var iframeUrls: List<String> = emptyList()
    private var hasDirectIframeUrls: Boolean = false

    // UI State — backed by Compose state so the UI reacts to changes
    private val isTopBarVisible = mutableStateOf(true)

    companion object {
        private const val TAG = "SchedulePlayer"

        // Intent extras
        const val EXTRA_CHANNEL_ID = "CHANNEL_ID"
        const val EXTRA_CHANNEL_NAME = "CHANNEL_NAME"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_SELECTED_PLAYER = "SELECTED_PLAYER"
        const val EXTRA_IFRAME_URLS = "IFRAME_URLS"

        /**
         * Creates an intent to launch the SchedulePlayerActivity
         */
        fun createIntent(
            context: Context,
            channelId: String,
            channelName: String,
            eventTitle: String,
            iframeUrls: List<String> = emptyList(),
            selectedPlayerIndex: Int = 0
        ) = android.content.Intent(context, SchedulePlayerActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
            putExtra(EXTRA_SELECTED_PLAYER, selectedPlayerIndex)
            putStringArrayListExtra(EXTRA_IFRAME_URLS, ArrayList(iframeUrls))
        }
    }

    // Top bar auto-hide timer
    private val topBarHideHandler = Handler(Looper.getMainLooper())
    private val topBarHideRunnable = Runnable {
        isDpadNavigating = false
        hideTopBar()
    }
    private var isDpadNavigating = false
    private val TOPBAR_HIDE_DELAY_MS = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Channel"
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event"
        selectedPlayerIndex = intent.getIntExtra(EXTRA_SELECTED_PLAYER, 0)

        val passedIframeUrls = intent.getStringArrayListExtra(EXTRA_IFRAME_URLS)
        if (!passedIframeUrls.isNullOrEmpty()) {
            iframeUrls = passedIframeUrls
            hasDirectIframeUrls = true
            Log.i(TAG, "Received ${iframeUrls.size} iframe URLs from intent")
        }

        if (channelId.isEmpty() && iframeUrls.isEmpty()) {
            Toast.makeText(this, "No channel ID or stream URLs provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        installWindowKeyInterceptor()
        setupLayout()

        if (hasDirectIframeUrls) {
            setupWithDirectIframeUrls()
        } else {
            fetchChannelWatchPage(channelId)
        }
    }

    private fun fetchChannelWatchPage(channelId: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ScheduleRepository.getInstance().fetchChannelWatchPage(channelId)
            }

            result.fold(
                onSuccess = { watchPage ->
                    channelWatchPage = watchPage

                    playerOptions = watchPage.playerOptions

                    if (playerOptions.isEmpty()) {
                        currentIframeHtml = generateIframeHtml(watchPage.defaultIframeUrl)
                    } else {
                        val playerToUse = playerOptions.getOrNull(selectedPlayerIndex)
                            ?: playerOptions.find { it.isActive }
                            ?: playerOptions.first()

                        currentIframeHtml = generateIframeHtml(playerToUse.url)
                        selectedPlayerIndex = playerOptions.indexOf(playerToUse)
                    }

                    loadCurrentStream()
                    updateTopBar()
                },
                onFailure = { error ->
                   Log.e(TAG, "Failed to fetch watch page: ${error.message}")
                    currentIframeHtml = generateIframeHtml(
                        "https://dlhd.pk/player/stream-$channelId.php"
                    )
                    loadCurrentStream()
                }
            )
        }
    }

    private fun loadCurrentStream() {
        currentIframeHtml?.let { html ->
            if (::webView.isInitialized) {
                webView.loadDataWithBaseURL(
                    "https://dlhd.pk",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun setupWithDirectIframeUrls() {
        if (iframeUrls.isEmpty()) {
            Toast.makeText(this, "No stream URLs available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val totalIframes = iframeUrls.size
        Toast.makeText(
            this,
            "$totalIframes stream option(s) available for $channelName",
            Toast.LENGTH_LONG
        ).show()

        playerOptions = iframeUrls.mapIndexed { index, url ->
            PlayerOption(
                playerNumber = index + 1,
                url = url,
                isActive = index == selectedPlayerIndex
            )
        }

        if (selectedPlayerIndex >= playerOptions.size) {
            selectedPlayerIndex = 0
        }

        if (iframeUrls.isNotEmpty()) {
            currentIframeHtml = generateIframeHtml(iframeUrls[selectedPlayerIndex])
            loadCurrentStream()
        }

        updateTopBar()
    }

    private fun generateIframeHtml(streamUrl: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                    iframe { width: 100%; height: 100%; border: 0; }
                </style>
            </head>
            <body>
                <iframe
                    src="$streamUrl"
                    width="100%"
                    height="100%"
                    scrolling="no"
                    frameborder="0"
                    allow="autoplay; encrypted-media; fullscreen"
                    allowfullscreen="true"
                    allowtransparency="true"
                    id="thatframe">
                </iframe>
            </body>
            </html>
        """.trimIndent()
    }

    private fun updateTopBar() {
        isTopBarVisible.value = true
        scheduleTopBarHide()
    }

    private fun injectVideoVolumeController() {
        if (!::webView.isInitialized) return
        val jsCode = """
            (function() {
                console.log('[VideoController] Initializing top-frame volume controller');

                function forcePlayAndUnmute(video) {
                    try {
                        if (video.paused) {
                            video.muted = true;
                            video.play().catch(function(e) {
                                console.warn('[VideoController] play() blocked:', e.message);
                            });
                        }
                        setTimeout(function() {
                            try {
                                video.muted = false;
                                video.volume = 1;
                                console.log('[VideoController] Unmuted');
                            } catch(e) {
                                console.warn('[VideoController] Unmute failed:', e.message);
                            }
                        }, 800);
                    } catch(e) {}
                }

                function processVideos(root) {
                    try {
                        root.querySelectorAll('video').forEach(function(v) {
                            forcePlayAndUnmute(v);
                        });
                    } catch(e) {}
                }

                processVideos(document);

                document.querySelectorAll('iframe').forEach(function(iframe) {
                    try {
                        if (iframe.contentDocument) {
                            processVideos(iframe.contentDocument);
                            new MutationObserver(function() {
                                processVideos(iframe.contentDocument);
                            }).observe(iframe.contentDocument.body, { childList: true, subtree: true });
                        }
                    } catch(e) {
                        console.log('[VideoController] Cross-origin iframe — handled via network injection');
                    }
                });

                new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes.forEach(function(node) {
                            if (node.nodeName === 'VIDEO') forcePlayAndUnmute(node);
                            if (node.querySelectorAll) processVideos(node);
                        });
                    });
                }).observe(document.body, { childList: true, subtree: true });

                var retryCount = 0;
                var retryInterval = setInterval(function() {
                    processVideos(document);
                    if (++retryCount >= 10) clearInterval(retryInterval);
                }, 1000);

                window.__videoControllerInterval = retryInterval;
            })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    private fun tryInjectAutoplayScript(
        url: String,
        headers: Map<String, String>?
    ): WebResourceResponse? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            headers?.forEach { (k, v) ->
                try { connection.setRequestProperty(k, v) } catch (e: Exception) {
                    Log.i(TAG, "Failed to set header $k: ${e.message}")
                }
            }
            connection.connect()

            val contentType = connection.contentType ?: return null
            if (!contentType.contains("html", ignoreCase = true)) return null

            val charset = Regex("charset=([\\w-]+)")
                .find(contentType)?.groupValues?.get(1) ?: "UTF-8"

            val originalHtml = connection.inputStream.bufferedReader(
                charset(charset)
            ).readText()

            val autoplayScript = """
                <script>
                (function() {
                    function unlock(v) {
                        try {
                            v.muted = true;
                            var p = v.play();
                            if (p && p.then) {
                                p.then(function() {
                                    setTimeout(function() {
                                        try { v.muted = false; v.volume = 1; } catch(e) {}
                                    }, 800);
                                }).catch(function(e) {
                                    console.warn('[AutoplayInject] play() blocked:', e.message);
                                });
                            } else {
                                setTimeout(function() {
                                    try { v.muted = false; v.volume = 1; } catch(e) {}
                                }, 800);
                            }
                        } catch(e) {}
                    }
                    function scan(root) {
                        try { (root || document).querySelectorAll('video').forEach(unlock); } catch(e) {}
                    }
                    new MutationObserver(function() { scan(document); })
                        .observe(document.documentElement, { childList: true, subtree: true });
                    document.addEventListener('DOMContentLoaded', function() { scan(document); });
                    setTimeout(function() { scan(document); }, 500);
                    setTimeout(function() { scan(document); }, 2000);
                    setTimeout(function() { scan(document); }, 4000);
                })();
                </script>
            """.trimIndent()

            val injected = when {
                originalHtml.contains("</head>", ignoreCase = true) ->
                    originalHtml.replace("</head>", "$autoplayScript</head>", ignoreCase = true)
                originalHtml.contains("<body", ignoreCase = true) ->
                    originalHtml.replace(
                        Regex("<body", RegexOption.IGNORE_CASE),
                        "$autoplayScript<body"
                    )
                else -> autoplayScript + originalHtml
            }

            WebResourceResponse(
                "text/html",
                charset,
                injected.byteInputStream(charset(charset))
            )
        } catch (e: Exception) {
            Log.w(TAG, "[AutoplayInject] Failed for $url: ${e.message}")
            null
        }
    }

    private fun isAdRequest(url: String): Boolean {
        val adPatterns = listOf(
            "adbanner", "rs4k-ad", "rs4k",
            "aa-ads", "adasia",
            "doubleclick.net", "googlesyndication.com", "googletagservices.com",
            "adservice.google", "pagead2.googlesyndication",
            "adnxs.com", "adsrvr.org",
            "exoclick.com", "juicyads.com", "adskeeper.com",
            "hilltopads.net", "adsterra.com", "propellerads.com",
            "trafficjunky.net", "trafficstars.com",
            "popunder", "pop-up", "popcash",
            "clickadu.com", "adcash.com", "bidvertiser.com"
        )
        return adPatterns.any { url.contains(it, ignoreCase = true) }
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLayout() {
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
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
            overScrollMode = View.OVER_SCROLL_NEVER

            webViewClient = object : android.webkit.WebViewClient() {

                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()
                        ?: return super.shouldInterceptRequest(view, request)
                    val headers = request.requestHeaders

                    if (isAdRequest(url)) {
                       Log.d(TAG, "[AdBlock] Blocked: $url")
                        return emptyResponse()
                    }

                    val acceptHeader = headers?.get("Accept") ?: ""
                    if (acceptHeader.contains("text/html")) {
                        val injected = tryInjectAutoplayScript(url, headers)
                        if (injected != null) return injected
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.i(TAG, "[WebView] Page finished: $url")

                    view?.evaluateJavascript("""
                        (function() {
                            var style = document.createElement('style');
                            style.innerHTML = `
                                [data-aa],
                                [class*="site-ad"], [class*="ad-banner"], [class*="ad-wrap"],
                                [id*="ad-"], [id*="banner"],
                                [class*="popup"], [class*="overlay"], [class*="interstitial"],
                                iframe[src*="adbanner"], iframe[src*="rs4k"],
                                iframe[src*="popunder"], iframe[src*="pop-up"],
                                div[id^="ad"], div[class^="ad"] {
                                    display: none !important;
                                    visibility: hidden !important;
                                    pointer-events: none !important;
                                    width: 0 !important;
                                    height: 0 !important;
                                }
                            `;
                            document.head.appendChild(style);

                            new MutationObserver(function(mutations) {
                                mutations.forEach(function(m) {
                                    m.addedNodes.forEach(function(node) {
                                        if (node.nodeName !== 'IFRAME') return;
                                        var src = node.getAttribute('src') || '';
                                        var hasDataAa = node.hasAttribute('data-aa');
                                        var cls = node.className || '';
                                        if (hasDataAa ||
                                            src.includes('adbanner') ||
                                            src.includes('rs4k') ||
                                            src.includes('popunder') ||
                                            cls.includes('site-ad') ||
                                            cls.includes('ad-banner')) {
                                            node.style.cssText = 'display:none!important;width:0!important;height:0!important;';
                                            node.removeAttribute('src');
                                        }
                                    });
                                });
                            }).observe(document.documentElement, { childList: true, subtree: true });
                        })();
                    """.trimIndent(), null)

                    injectVideoVolumeController()
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    val url = request?.url?.toString() ?: return
                   Log.e(TAG, "[WebView] Error on $url: ${error?.description}")

                    // Trigger fallback for the main wrapper page OR for any sub-frame
                    // whose URL matches one of our known stream URLs — this covers the
                    // case where the outer HTML loads fine but the embedded stream iframe fails.
                    val isStreamFrame = iframeUrls.any { url.contains(it, ignoreCase = true) }
                            || playerOptions.any { url.contains(it.url, ignoreCase = true) }

                    if (request.isForMainFrame || isStreamFrame) {
                        if (hasDirectIframeUrls) {
                            tryNextStreamUrl()
                        } else {
                            tryNextPlayer()
                        }
                    }
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(
                    view: android.webkit.WebView?,
                    newProgress: Int
                ) {
                    super.onProgressChanged(view, newProgress)
                }
            }
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun showToast(message: String) {
                runOnUiThread {
                    Toast.makeText(this@SchedulePlayerActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }, "Android")

        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 0
            }
            isFocusable = true
            isFocusableInTouchMode = true
            setContent {
                val topBarVisible by isTopBarVisible
                MaterialTheme {
                    AnimatedVisibility(
                        visible = topBarVisible,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it }
                    ) {
                        PlayerSourceTopBar(
                            channelName = channelName,
                            eventTitle = eventTitle,
                            playerOptions = playerOptions,
                            selectedIndex = selectedPlayerIndex,
                            onSourceSelected = { index ->
                                if (index != selectedPlayerIndex && index in playerOptions.indices) {
                                    switchToPlayer(index)
                                }
                            },
                            onBackPressed = { showExitConfirmationDialog() }
                        )
                    }
                }
            }
        }

        rootLayout.addView(webView)
        rootLayout.addView(composeView)

        setContentView(rootLayout)

        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true

        scheduleTopBarHide()
    }

    private fun switchToPlayer(index: Int) {
        if (index in playerOptions.indices) {
            selectedPlayerIndex = index
            playerOptions = playerOptions.mapIndexed { i, option ->
                option.copy(isActive = i == index)
            }
            val player = playerOptions[index]
            currentIframeHtml = generateIframeHtml(player.url)
            loadCurrentStream()
            Toast.makeText(
                this,
                "Switched to: Server ${player.playerNumber}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun tryNextPlayer() {
        if (playerOptions.size <= 1) {
            Toast.makeText(this, "Stream unavailable. No other servers to try.", Toast.LENGTH_LONG).show()
            return
        }
        val nextIndex = (selectedPlayerIndex + 1) % playerOptions.size
        Toast.makeText(
            this,
            "Stream failed. Trying: Server ${playerOptions[nextIndex].playerNumber}",
            Toast.LENGTH_SHORT
        ).show()
        switchToPlayer(nextIndex)
    }

    private fun tryNextStreamUrl() {
        if (iframeUrls.size <= 1) {
            Toast.makeText(this, "Stream unavailable. No other servers to try.", Toast.LENGTH_LONG).show()
            return
        }
        val nextIndex = (selectedPlayerIndex + 1) % iframeUrls.size
        Toast.makeText(
            this,
            "Stream failed. Trying: Server ${nextIndex + 1}",
            Toast.LENGTH_SHORT
        ).show()
        selectedPlayerIndex = nextIndex
        playerOptions = playerOptions.mapIndexed { i, option ->
            option.copy(isActive = i == nextIndex)
        }
        currentIframeHtml = generateIframeHtml(iframeUrls[nextIndex])
        loadCurrentStream()
    }

    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Stop Playback?",
            message = "Are you sure you want to stop watching $channelName?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { },
            onYes = { finish() }
        ).show()
    }

    private fun hideTopBar() {
        isTopBarVisible.value = false
    }

    private fun scheduleTopBarHide() {
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        topBarHideHandler.postDelayed(topBarHideRunnable, TOPBAR_HIDE_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            try {
                webView.evaluateJavascript(
                    "if(window.__videoControllerInterval) clearInterval(window.__videoControllerInterval);",
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during video controller cleanup: ${e.message}")
            }
        }

        topBarHideHandler.removeCallbacks(topBarHideRunnable)

        if (::webView.isInitialized) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
                    stopLoading()
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = android.webkit.WebViewClient()
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

    /**
     * Wraps the Window.Callback so we can intercept key events before the
     * WebView (or any child view) consumes them — without touching the
     * restricted ComponentActivity.dispatchKeyEvent API.
     *
     * Installed once in onCreate(); the original callback is always called
     * for every event we do not explicitly consume.
     */
    private fun installWindowKeyInterceptor() {
        val original = window.callback
        window.callback = object : android.view.Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            event.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    toggleTopBar()
                    return true // consumed — WebView never sees this event
                }
                return original.dispatchKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS -> {
                isDpadNavigating = true
                showTopBarAndResetTimer()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Toggles the top bar:
     * - If hidden  → show it and start the 5-second auto-hide timer.
     * - If visible → hide it immediately and cancel any pending timer.
     */
    private fun toggleTopBar() {
        if (isTopBarVisible.value) {
            topBarHideHandler.removeCallbacks(topBarHideRunnable)
            hideTopBar()
        } else {
            showTopBarAndResetTimer()
        }
    }

    private fun detectDeviceType() {
        val uiModeManager =
            getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager

    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: android.content.Context): android.webkit.WebView {
        val webView = android.webkit.WebView(context)
        val isHardwareAccelerated =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

        if (isHardwareAccelerated) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        return webView
    }

    private fun showTopBarAndResetTimer() {
        isTopBarVisible.value = true
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        topBarHideHandler.postDelayed(topBarHideRunnable, TOPBAR_HIDE_DELAY_MS)
    }
}

// ============================================================================
// COMPOSE — Player Source Selection Top Bar
// ============================================================================

@Composable
fun PlayerSourceTopBar(
    channelName: String,
    eventTitle: String,
    playerOptions: List<PlayerOption>,
    selectedIndex: Int,
    onSourceSelected: (Int) -> Unit,
    onBackPressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xCC000000))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBackPressed = onBackPressed)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = channelName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = eventTitle,
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                }
            }

            if (playerOptions.isNotEmpty()) {
                Text(
                    text = "${playerOptions.size} Servers",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        if (playerOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Servers:",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                itemsIndexed(playerOptions) { index, playerOption ->
                    PlayerOptionButton(
                        playerNumber = playerOption.playerNumber,
                        isSelected = index == selectedIndex,
                        onClick = { onSourceSelected(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackButton(
    onBackPressed: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onBackPressed() },
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color(0xFF424242) else Color.Transparent,
        border = if (isFocused) BorderStroke(1.dp, Color(0xFF448AFF)) else null
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = if (isFocused) Color(0xFF448AFF) else Color.White,
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp)
        )
    }
}

@Composable
private fun PlayerOptionButton(
    playerNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isSelected -> Color(0xFF2196F3)
        isFocused  -> Color(0xFF2D2D2D)
        else       -> Color(0xFF1A1A1A)
    }

    val textColor = when {
        isSelected -> Color.White
        isFocused  -> Color(0xFF448AFF)
        else       -> Color(0xFFE0E0E0)
    }

    val borderColor = when {
        isSelected -> Color(0xFFFF1744)
        isFocused  -> Color(0xFF448AFF)
        else       -> Color(0xFF404040)
    }

    Surface(
        modifier = Modifier
            // onFocusChanged must wrap clickable's node from the outside so it
            // observes the correct (single) focus node that clickable creates.
            // No bare .focusable() needed — clickable already marks the node focusable.
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                onClick = onClick,
                onClickLabel = "Select Server $playerNumber"
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Server $playerNumber",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}