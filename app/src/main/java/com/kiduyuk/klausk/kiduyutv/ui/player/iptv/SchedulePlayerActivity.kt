package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.focusable
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
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.MouseCursorView
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
 *
 * Fixes applied:
 * - playerOptions and selectedPlayerIndex are now mutableStateOf so Compose reacts to changes
 * - lifecycleScope used instead of raw CoroutineScope to prevent coroutine leaks
 * - isDpadNavigating is reset to false after the auto-hide timer fires
 * - generateIframeHtml is consolidated (no more duplicate)
 * - PlayerOption.isActive flag is kept in sync when switching players
 * - Autoplay/unmute works via shouldInterceptRequest HTML injection into every iframe frame,
 *   bypassing cross-origin restrictions that defeat JS injection into the top frame
 */
class SchedulePlayerActivity : ComponentActivity() {

    private lateinit var webView: android.webkit.WebView
    private lateinit var cursorView: MouseCursorView
    private lateinit var rootLayout: FrameLayout
    private var cursorX = 0f
    private var cursorY = 0f
    private val moveSpeed = 50f
    private var screenWidth = 0
    private var screenHeight = 0

    private var isCursorDisabled = false
    private var currentIframeHtml: String? = null
    private var channelName: String = "Channel"
    private var eventTitle: String = "Channel"

    // Prevent multiple activity launches when a stream is sniffed
    private var isStreamSniffed = false

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

    // Unified idle timer — hides both cursor and top bar
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled && isCursorVisible) {
            cursorView.animate().alpha(0f).setDuration(500).start()
            isCursorVisible = false
        }
        hideTopBar()
    }
    private var isCursorVisible = false

    // Top bar auto-hide timer
    private val topBarHideHandler = Handler(Looper.getMainLooper())
    private val topBarHideRunnable = Runnable {
        // FIX: reset isDpadNavigating here so future timer callbacks are not permanently blocked
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
            android.util.Log.i(TAG, "Received ${iframeUrls.size} iframe URLs from intent")
        }

        if (channelId.isEmpty() && iframeUrls.isEmpty()) {
            Toast.makeText(this, "No channel ID or stream URLs provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        detectDeviceType()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        setupLayout()

        if (hasDirectIframeUrls) {
            setupWithDirectIframeUrls()
        } else {
            fetchChannelWatchPage(channelId)
        }
    }

    /**
     * Unified handler for successfully sniffing a stream.
     * Routes the extracted URL to the native ExoPlayer and tears down the WebView.
     */
    private fun handleSniffedStream(streamUrl: String) {
        if (isStreamSniffed) return
        isStreamSniffed = true

        android.util.Log.i(TAG, "[StreamSniffer] SUCCESS! Routing to native player: $streamUrl")

        runOnUiThread {
            Toast.makeText(this, "Stream detected! Launching native player...", Toast.LENGTH_SHORT).show()

            val intent = IptvPlayerActivity.createIntent(
                context = this@SchedulePlayerActivity,
                channelName = channelName,
                streamUrl = streamUrl,
                channelLogo = null,
                tvgId = intent.getStringExtra(EXTRA_CHANNEL_ID),
                tvgName = eventTitle,
                group = "Scheduled Events"
            )

            startActivity(intent)

            if (::webView.isInitialized) {
                webView.loadUrl("about:blank")
            }
            finish()
        }
    }

    private fun fetchChannelWatchPage(channelId: String) {
        // FIX: use lifecycleScope instead of raw CoroutineScope to avoid leaks
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ScheduleRepository.getInstance().fetchChannelWatchPage(channelId)
            }

            result.fold(
                onSuccess = { watchPage ->
                    channelWatchPage = watchPage

                    // FIX: assign to mutableStateOf-backed property so Compose reacts
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
                    android.util.Log.e(TAG, "Failed to fetch watch page: ${error.message}")
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

        // FIX: assign to mutableStateOf-backed property
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

    /**
     * FIX: Single source of truth for iframe HTML generation.
     * Removed the duplicate in setupWithDirectIframeUrls/fetchChannelWatchPage
     * that previously called ScheduleRepository.generateIframeHtml separately.
     *
     * Added allow="autoplay; encrypted-media; fullscreen" which is required for
     * Chromium to permit autoplay inside nested iframes.
     */
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

    /**
     * FIX: Autoplay/unmute injection.
     *
     * The old approach injected JS into the top frame via onPageFinished, which can
     * never reach videos inside cross-origin iframes (silent DOMException).
     *
     * The new approach intercepts each HTML response at the network level inside
     * shouldInterceptRequest and injects the autoplay script directly into that
     * frame's <head> before it parses — so it runs in the correct origin context
     * regardless of nesting depth.
     *
     * This method is kept as a fallback for same-origin frames or the top-level page.
     */
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

                // Process top-level document
                processVideos(document);

                // Same-origin iframes only — cross-origin iframes are handled via
                // shouldInterceptRequest injection at the network level.
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

                // Watch for dynamically added videos in top frame
                new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes.forEach(function(node) {
                            if (node.nodeName === 'VIDEO') forcePlayAndUnmute(node);
                            if (node.querySelectorAll) processVideos(node);
                        });
                    });
                }).observe(document.body, { childList: true, subtree: true });

                // Retry loop — clears itself after 10 iterations
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

    /**
     * FIX: Intercepts every HTML response (including nested iframes) and injects
     * the autoplay/unmute script directly into that frame's <head>.
     *
     * This is the only approach that reliably reaches videos inside cross-origin
     * iframes, because the script executes in the iframe's own origin context.
     *
     * Falls back gracefully (returns null) on any network or parse error so the
     * WebView loads the page normally.
     */
    private fun tryInjectAutoplayScript(
        url: String,
        headers: Map<String, String>?
    ): WebResourceResponse? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            // Forward the original request headers so the server doesn't reject us
            headers?.forEach { (k, v) ->
                try { connection.setRequestProperty(k, v) } catch (e: Exception) { /* skip restricted headers */ }
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
            android.util.Log.w(TAG, "[AutoplayInject] Failed for $url: ${e.message}")
            null // Graceful fallback — WebView loads the URL normally
        }
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
                // FIX: setSupportMultipleWindows(false) prevents popup-style players
                // that open a new window and break the autoplay context
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // FIX: disable cache so stale player configs don't interfere
                cacheMode = WebSettings.LOAD_NO_CACHE
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

            webViewClient = object : android.webkit.WebViewClient() {

                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()
                        ?: return super.shouldInterceptRequest(view, request)
                    val headers = request.requestHeaders

                    // ── Stream Sniffer ────────────────────────────────────────────
                    if (!isStreamSniffed) {
                        val isKnownExtension = url.contains(".m3u8") ||
                                url.contains(".mpd") ||
                                url.contains("master.m3u8") ||
                                url.contains("playlist.m3u8")

                        val isMediaChunk = url.contains("chunk") &&
                                !url.endsWith(".js") &&
                                !url.endsWith(".css")

                        val acceptHeader = headers?.get("Accept") ?: ""
                        val isVideoRequest = acceptHeader.contains("video/") ||
                                acceptHeader.contains("application/x-mpegURL") ||
                                acceptHeader.contains("application/dash+xml")

                        if (isKnownExtension || isMediaChunk || isVideoRequest) {
                            handleSniffedStream(url)
                            // Return null so the WebView still loads the resource normally
                            // while ExoPlayer takes over — avoids a blank screen flash
                            return null
                        }
                    }

                    // ── Autoplay Injection ────────────────────────────────────────
                    // FIX: intercept every HTML response and inject the autoplay script
                    // directly into that frame's DOM — the only reliable way to reach
                    // videos inside cross-origin nested iframes.
                    val acceptHeader = headers?.get("Accept") ?: ""
                    if (acceptHeader.contains("text/html")) {
                        val injected = tryInjectAutoplayScript(url, headers)
                        if (injected != null) return injected
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    android.util.Log.i(TAG, "[WebView] Page finished: $url")

                    // Inject CSS to suppress common ad overlays
                    view?.evaluateJavascript("""
                        (function() {
                            var style = document.createElement('style');
                            style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay, iframe[src*="ads"] { display: none !important; }';
                            document.head.appendChild(style);
                        })();
                    """.trimIndent(), null)

                    // Top-frame JS injection as a complementary fallback
                    injectVideoVolumeController()

                    // Fetch/XHR interceptor for blob/dynamic streams
                    val blobSnifferJs = """
                        (function() {
                            console.log('[StreamSniffer] Injecting Fetch/XHR interceptors...');

                            const originalFetch = window.fetch;
                            window.fetch = async function(...args) {
                                const reqUrl = typeof args[0] === 'string' ? args[0] : args[0]?.url;
                                if (reqUrl && (reqUrl.includes('.m3u8') || reqUrl.includes('.mpd'))) {
                                    if (window.Android && window.Android.onStreamSniffed) {
                                        window.Android.onStreamSniffed(reqUrl);
                                    }
                                }
                                return originalFetch.apply(this, args);
                            };

                            const originalOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, reqUrl) {
                                if (reqUrl && (reqUrl.includes('.m3u8') || reqUrl.includes('.mpd'))) {
                                    if (window.Android && window.Android.onStreamSniffed) {
                                        window.Android.onStreamSniffed(reqUrl);
                                    }
                                }
                                originalOpen.apply(this, arguments);
                            };
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(blobSnifferJs, null)
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        android.util.Log.e(TAG, "[WebView] Error: ${error?.description}")
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

        // JavaScript interface for toast messages and blob stream sniffing
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun showToast(message: String) {
                runOnUiThread {
                    Toast.makeText(this@SchedulePlayerActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            @JavascriptInterface
            fun onStreamSniffed(url: String) {
                handleSniffedStream(url)
            }
        }, "Android")

        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 0
            }
            setContent {
                // FIX: playerOptions and selectedPlayerIndex are now mutableStateOf,
                // so reading them here automatically subscribes to their changes
                // and triggers recomposition when switchToPlayer() updates them.
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

        scheduleTopBarHide()
    }

    /**
     * FIX: Updates selectedPlayerIndex and playerOptions together, keeping isActive in sync.
     * Previously, isActive flags on PlayerOption were never updated when switching sources,
     * causing the "active" concept to diverge from selectedPlayerIndex.
     */
    private fun switchToPlayer(index: Int) {
        if (index in playerOptions.indices) {
            isStreamSniffed = false
            selectedPlayerIndex = index
            // FIX: rebuild the list with the correct isActive flags
            playerOptions = playerOptions.mapIndexed { i, option ->
                option.copy(isActive = i == index)
            }
            val player = playerOptions[index]
            currentIframeHtml = generateIframeHtml(player.url)
            loadCurrentStream()
            Toast.makeText(
                this,
                "Switched to: Player ${player.playerNumber}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun tryNextPlayer() {
        if (playerOptions.size > 1) {
            val nextIndex = (selectedPlayerIndex + 1) % playerOptions.size
            Toast.makeText(
                this,
                "Stream failed. Trying: Player ${playerOptions[nextIndex].playerNumber}",
                Toast.LENGTH_SHORT
            ).show()
            switchToPlayer(nextIndex)
        }
    }

    private fun tryNextStreamUrl() {
        if (iframeUrls.size > 1) {
            isStreamSniffed = false
            val nextIndex = (selectedPlayerIndex + 1) % iframeUrls.size
            Toast.makeText(
                this,
                "Stream failed. Trying: Player ${nextIndex + 1}",
                Toast.LENGTH_SHORT
            ).show()
            selectedPlayerIndex = nextIndex
            // FIX: keep playerOptions isActive in sync here too
            playerOptions = playerOptions.mapIndexed { i, option ->
                option.copy(isActive = i == nextIndex)
            }
            currentIframeHtml = generateIframeHtml(iframeUrls[nextIndex])
            loadCurrentStream()
        }
    }

    private fun detectDeviceType() {
        val uiModeManager =
            getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        if (uiModeManager.currentModeType != android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
        }
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
        // Clear JS interval before destroying WebView
        if (::webView.isInitialized) {
            try {
                webView.evaluateJavascript(
                    "if(window.__videoControllerInterval) clearInterval(window.__videoControllerInterval);",
                    null
                )
            } catch (e: Exception) { /* ignore — WebView may already be paused */ }
        }

        cursorHideHandler.removeCallbacks(cursorHideRunnable)
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
                android.util.Log.e(TAG, "Error during WebView cleanup: ${e.message}")
            }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isDpadKey(event)) {
            isDpadNavigating = true
            showCursorAndResetTimer()
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
        if (isDpadKeyCode(keyCode)) {
            isDpadNavigating = true
            showCursorAndResetTimer()
        }

        if (isCursorDisabled) return super.onKeyDown(keyCode, event)

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
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                showCursorAndResetTimer()
                simulateClick(cursorX, cursorY)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun isDpadKey(event: KeyEvent): Boolean {
        return event.source and android.view.InputDevice.SOURCE_DPAD ==
                android.view.InputDevice.SOURCE_DPAD ||
                event.keyCode in listOf(
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS
                )
    }

    private fun isDpadKeyCode(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS
        )
    }

    private fun updateCursorPosition() {
        if (isCursorDisabled) return
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.bringToFront()
        cursorView.invalidate()
    }

    private fun simulateClick(x: Float, y: Float) {
        val downTime = android.os.SystemClock.uptimeMillis()
        val eventTime = android.os.SystemClock.uptimeMillis()

        val downEvent = android.view.MotionEvent.obtain(
            downTime, eventTime,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0
        )
        val upEvent = android.view.MotionEvent.obtain(
            downTime, eventTime + 100,
            android.view.MotionEvent.ACTION_UP, x, y, 0
        )

        downEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN

        window.decorView.dispatchTouchEvent(downEvent)
        window.decorView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    private fun showCursorAndResetTimer() {
        isTopBarVisible.value = true
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        topBarHideHandler.postDelayed(topBarHideRunnable, TOPBAR_HIDE_DELAY_MS)

        if (isCursorDisabled) {
            cursorHideHandler.removeCallbacks(cursorHideRunnable)
            cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
            return
        }

        cursorView.animate().cancel()
        cursorView.alpha = 1f
        isCursorVisible = true
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
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
                    text = "${playerOptions.size} Players",
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
                    text = "Sources:",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
            .focusable()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
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
            .focusable()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .clickable { onClick() },
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
                text = "P$playerNumber",
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
