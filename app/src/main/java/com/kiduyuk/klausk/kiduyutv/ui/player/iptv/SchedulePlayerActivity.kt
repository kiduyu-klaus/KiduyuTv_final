package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.api.ScheduleApiService
import com.kiduyuk.klausk.kiduyutv.data.model.ChannelWatchPage
import com.kiduyuk.klausk.kiduyutv.data.model.PlayerOption
import com.kiduyuk.klausk.kiduyutv.data.repository.ScheduleRepository
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.AdBlockerWebViewClient
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.MouseCursorView
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import com.kiduyuk.klausk.kiduyutv.util.FullEasyListEngine
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schedule Player Activity for playing scheduled channels from dlive.sx.
 * Extends the existing PlayerActivity functionality with schedule-specific features
 * Takes an iframe HTML as intent extra and plays the scheduled channel in WebView
 * Uses an unobtrusive Material server control for source switching.
 * Uses ChannelWatchPage and playerOptions for handling multiple streams
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
    private var channelId: String = ""
    private val failedPlayerUrls = mutableSetOf<String>()
    private var adBlockerReady = false
    private var pendingStreamLoad = false
    private var volumeControllerEnabled = false
    private var pageLoadingDialog: androidx.appcompat.app.AlertDialog? = null

    // FIX: playerOptions and selectedPlayerIndex backed by mutableStateOf so
    // the Compose top bar recomposes automatically when these change.
    private var playerOptions by mutableStateOf<List<PlayerOption>>(emptyList())
    private var selectedPlayerIndex by mutableStateOf(0)

    private var channelWatchPage: ChannelWatchPage? = null

    // Direct iframe URLs passed from scraped channels
    private var iframeUrls: List<String> = emptyList()
    private var iframeNames: List<String> = emptyList()
    private var hasDirectIframeUrls: Boolean = false

    // UI State — backed by Compose state so the UI reacts to changes
    private val isTopBarVisible = mutableStateOf(true)
    private val isServerMenuExpanded = mutableStateOf(false)

    companion object {
        private const val TAG = "SchedulePlayer"
        private const val SCHEDULE_HOST = "dlive.sx"
        private const val DIRECT_IFRAME_LOADING_DELAY_MS = 1_200L

        // Intent extras
        const val EXTRA_CHANNEL_ID = "CHANNEL_ID"
        const val EXTRA_CHANNEL_NAME = "CHANNEL_NAME"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_SELECTED_PLAYER = "SELECTED_PLAYER"
        const val EXTRA_IFRAME_URLS = "IFRAME_URLS"
        const val EXTRA_IFRAME_NAMES = "IFRAME_NAMES"
        private val STREAM_PATHS = listOf(
            "/stream/stream-%s.php",
            "/cast/stream-%s.php",
            "/player/stream-%s.php",
            "/plus/stream-%s.php",
            "/watch/stream-%s.php",
            "/casting/stream-%s.php"
        )

        /**
         * Creates an intent to launch the SchedulePlayerActivity
         */
        fun createIntent(
            context: Context,
            channelId: String,
            channelName: String,
            eventTitle: String,
            iframeUrls: List<String> = emptyList(),
            iframeNames: List<String> = emptyList(),
            selectedPlayerIndex: Int = 0
        ) = android.content.Intent(context, SchedulePlayerActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
            putExtra(EXTRA_SELECTED_PLAYER, selectedPlayerIndex)
            putStringArrayListExtra(EXTRA_IFRAME_URLS, ArrayList(iframeUrls))
            putStringArrayListExtra(EXTRA_IFRAME_NAMES, ArrayList(iframeNames))
        }
    }

    private val iptvPlayerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == IptvPlayerActivity.RESULT_PLAYBACK_FAILED) {
            playerOptions.getOrNull(selectedPlayerIndex)?.url?.let(failedPlayerUrls::add)
            if (::webView.isInitialized) webView.stopLoading()
            if (!tryNextPlayer()) {
                showRetryOrCloseDialog(
                    message = "All available stream servers failed.",
                    onRetry = ::retryPlayback
                )
            }
        } else {
            finish()
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

        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Channel"
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event"
        selectedPlayerIndex = intent.getIntExtra(EXTRA_SELECTED_PLAYER, 0)

        val passedIframeUrls = intent.getStringArrayListExtra(EXTRA_IFRAME_URLS)
        if (!passedIframeUrls.isNullOrEmpty()) {
            iframeUrls = passedIframeUrls
            iframeNames = intent.getStringArrayListExtra(EXTRA_IFRAME_NAMES).orEmpty()
            hasDirectIframeUrls = true
            android.util.Log.i(TAG, "Received ${iframeUrls.size} iframe URLs from intent")
        }

        if (channelId.isEmpty() && iframeUrls.isEmpty()) {
            showRetryOrCloseDialog(
                message = "No channel ID or stream servers were provided.",
                onRetry = { recreate() }
            )
            return
        }

        detectDeviceType()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        setupLayout()
        showPageLoadingDialog()
        initializeAdBlocker()

        if (hasDirectIframeUrls) {
            setupWithDirectIframeUrls()
        } else {
            fetchChannelWatchPage(channelId)
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
                    playerOptions = withFallbackPlayerOptions(watchPage.playerOptions)

                    if (playerOptions.isEmpty()) {
                        showRetryOrCloseDialog(
                            message = "No stream servers are available for this channel.",
                            onRetry = { fetchChannelWatchPage(channelId) }
                        )
                        return@fold
                    }

                    val playerToUse = playerOptions.getOrNull(selectedPlayerIndex)
                        ?: playerOptions.find { it.isActive }
                        ?: playerOptions.first()

                    currentIframeHtml = generateIframeHtml(playerToUse.url)
                    selectedPlayerIndex = playerOptions.indexOf(playerToUse)

                    loadCurrentStream()
                    updateTopBar()
                },
                onFailure = { error ->
                    android.util.Log.e(TAG, "Failed to fetch watch page: ${error.message}")
                    currentIframeHtml = generateIframeHtml(
                        "${ScheduleApiService.BASE_URL}player/stream-$channelId.php"
                    )
                    loadCurrentStream()
                }
            )
        }
    }

    private fun showPageLoadingDialog() {
        if (isFinishing || isDestroyed) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24, 8, 24, 8)

            addView(ProgressBar(context), LinearLayout.LayoutParams(48, 48))
            addView(TextView(context).apply {
                text = "Please wait while the channel page loads…"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(20, 0, 0, 0)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        pageLoadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Loading player")
            .setView(content)
            .setCancelable(true)
            .create()
            .also { dialog ->
                dialog.setOnCancelListener {
                    if (::webView.isInitialized) webView.stopLoading()
                    finish()
                }
                dialog.show()
            }
    }

    private fun dismissPageLoadingDialog() {
        pageLoadingDialog?.dismiss()
        pageLoadingDialog = null
    }

    private fun loadCurrentStream() {
        // Do not let the first provider document run before the cached/downloaded
        // AdvancedAdBlocker snapshot is available to request interception.
        if (!adBlockerReady) {
            pendingStreamLoad = true
            return
        }
        currentIframeHtml?.let { html ->
            if (::webView.isInitialized) {
                webView.loadDataWithBaseURL(
                    ScheduleApiService.BASE_URL,
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun initializeAdBlocker() {
        lifecycleScope.launch {
            val fullEngineReady = FullEasyListEngine.initialize(applicationContext)
            android.util.Log.i(TAG, "[AdBlock] Full EasyList engine ready=$fullEngineReady")
            val result = AdvancedAdBlocker.initialize(applicationContext)
            android.util.Log.i(
                TAG,
                "[AdBlock] Schedule player initialized source=${result.source} " +
                    "domains=${result.blockedDomainCount} error=${result.error.orEmpty()}"
            )
            if (fullEngineReady && ::webView.isInitialized) {
                FullEasyListEngine.setupWebView(webView)
            }
            adBlockerReady = true
            if (pendingStreamLoad) {
                pendingStreamLoad = false
                loadCurrentStream()
            }
            if (result.refreshRecommended) {
                launch {
                    val refresh = AdvancedAdBlocker.refresh(applicationContext)
                    android.util.Log.i(
                        TAG,
                        "[AdBlock] Schedule player refresh source=${refresh.source} " +
                            "domains=${refresh.blockedDomainCount} error=${refresh.error.orEmpty()}"
                    )
                }
            }
        }
    }

    /** Applies the AdvancedAdBlocker DOM and popup guards to the wrapper document. */
    private fun installAdvancedAdGuards(view: android.webkit.WebView?) {
        view?.evaluateJavascript(
            """
            (function() {
                ${AdvancedAdBlocker.getBlockingJavaScript()}
                ${AdvancedAdBlocker.getCss()}

                if (window.__kiduyuScheduleAdGuardInstalled) return;
                window.__kiduyuScheduleAdGuardInstalled = true;
                    var adTerms = [
                    'instant cash', 'claim ${'$'}', 'claim money', 'missed video',
                    'join the video call', 'pending snaps', 'whatsapp', 'telegram',
                    'you have won', 'click continue', 'allow notifications',
                    'please confirm to continue'
                ];

                function isPlayerContainer(node) {
                    return !!(node && node.querySelector && node.querySelector('video, [class*="player"], [id*="player"]'));
                }

                function isAdOverlay(node) {
                    if (!node || node.nodeType !== 1 || isPlayerContainer(node)) return false;
                    var text = (node.innerText || node.textContent || '').toLowerCase();
                    var identity = ((node.id || '') + ' ' + (node.className || '')).toLowerCase();
                    var hasAdText = adTerms.some(function(term) { return text.indexOf(term) !== -1; });
                    var hasAdIdentity = /(?:ad|ads|popup|popunder|overlay|interstitial|notification|banner)/.test(identity);
                    var style = window.getComputedStyle(node);
                    var floatsAbovePage = (style.position === 'fixed' || style.position === 'absolute') &&
                        Number(style.zIndex || 0) > 9;
                    return hasAdText || (hasAdIdentity && floatsAbovePage);
                }

                function removeAdOverlays(root) {
                    if (!root || !root.querySelectorAll) return;
                    root.querySelectorAll('div, section, aside, dialog, iframe, a').forEach(function(node) {
                        if (isAdOverlay(node)) node.remove();
                    });
                }

                removeAdOverlays(document);
                new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (isAdOverlay(node)) {
                                node.remove();
                            } else {
                                removeAdOverlays(node);
                            }
                        });
                    });
                }).observe(document.documentElement, { childList: true, subtree: true });

                function removePopupTargets() {
                    document.querySelectorAll('a, form').forEach(function(node) {
                        var target = (node.getAttribute('target') || '').toLowerCase();
                        if (target === '_blank' || target === '_new' || target === '_popup') {
                            node.setAttribute('data-kiduyu-popup-target', '1');
                            node.removeAttribute('target');
                        }
                    });
                }
                removePopupTargets();
                setInterval(removePopupTargets, 1000);

                // Suppress click-triggered pop-under handlers before they can navigate the frame.
                document.addEventListener('click', function(event) {
                    var node = event.target && event.target.closest && event.target.closest('a, button, div, iframe');
                    if (isAdOverlay(node)) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                        node.remove();
                    }
                }, true);

                // The schedule player never needs a new browsing context. Prevent links in
                // server pages from opening a tab/window even when a provider restores a
                // window.open implementation after the generic guard has run.
                document.addEventListener('click', function(event) {
                    var link = event.target && event.target.closest && event.target.closest('a');
                    var target = link && (link.getAttribute('target') || '').toLowerCase();
                    if (link && (target === '_blank' || target === '_new' || target === '_popup' ||
                        link.getAttribute('data-kiduyu-popup-target') === '1')) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                    }
                }, true);
                document.addEventListener('submit', function(event) {
                    var form = event.target;
                    var target = form && (form.getAttribute('target') || '').toLowerCase();
                    if (target === '_blank' || target === '_new' || target === '_popup' ||
                        (form && form.getAttribute('data-kiduyu-popup-target') === '1')) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                    }
                }, true);
            })();
            """.trimIndent(),
            null
        )
    }

    private fun setupWithDirectIframeUrls() {
        if (iframeUrls.isEmpty()) {
            showRetryOrCloseDialog(
                message = "No stream servers are available for this channel.",
                onRetry = ::setupWithDirectIframeUrls
            )
            return
        }

        val totalIframes = iframeUrls.size
        Toast.makeText(
            this,
            "$totalIframes stream option(s) available for $channelName",
            Toast.LENGTH_LONG
        ).show()

        // FIX: assign to mutableStateOf-backed property
        playerOptions = withFallbackPlayerOptions(
            iframeUrls.mapIndexed { index, url ->
                PlayerOption(
                    playerNumber = index + 1,
                    url = url,
                    name = iframeNames.getOrNull(index).orEmpty().ifBlank { "Server ${index + 1}" },
                    isActive = index == selectedPlayerIndex
                )
            }
        )

        if (selectedPlayerIndex >= playerOptions.size) {
            selectedPlayerIndex = 0
        }

        if (playerOptions.isNotEmpty()) {
            currentIframeHtml = generateIframeHtml(playerOptions[selectedPlayerIndex].url)
            loadCurrentStream()
        }

        updateTopBar()
    }

    /** Generates the embedded player document used by direct sports streams. */
    private fun generateIframeHtml(streamUrl: String): String {
        val autoplayUrl = withAutoplayParameter(streamUrl)
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
                <iframe src="$autoplayUrl"
                    width="100%"
                    height="100%"
                    frameborder="0"
                    scrolling="no"
                    allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                    allowfullscreen="true"
                    loading="eager">
                </iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /** Requests autoplay from providers that support autoplay query parameters. */
    private fun withAutoplayParameter(streamUrl: String): String = runCatching {
        val uri = Uri.parse(streamUrl)
        val autoplayValues = uri.getQueryParameters("autoplay")
        val builder = uri.buildUpon()
        if (autoplayValues.none { it == "1" }) {
            builder.appendQueryParameter("autoplay", "1")
        }
        if (autoplayValues.none { it.equals("true", ignoreCase = true) }) {
            builder.appendQueryParameter("autoplay", "true")
        }
        builder.build().toString()
    }.getOrDefault(streamUrl)

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
        if (!::webView.isInitialized || volumeControllerEnabled) return
        val jsCode = """
            (function() {
                if (window.__kiduyuVolumeControllerEnabled) return;
                console.log('[VideoController] Initializing top-frame volume controller');

                function markVolumeEnabled() {
                    window.__kiduyuVolumeControllerEnabled = true;
                    try { Android.onVolumeEnabled(); } catch(e) {}
                }

                function forcePlayAndUnmute(video) {
                    try {
                        if (video.__kiduyuVolumeUnlockInstalled) return;
                        video.__kiduyuVolumeUnlockInstalled = true;
                        video.autoplay = true;
                        video.setAttribute('autoplay', '');
                        video.setAttribute('playsinline', '');

                        function enableVolume() {
                            try {
                                video.muted = false;
                                video.volume = 1;
                                markVolumeEnabled();
                                console.log('[VideoController] Unmuted');
                            } catch(e) {
                                console.warn('[VideoController] Unmute failed:', e.message);
                            }
                        }

                        function attemptPlay() {
                            try {
                                if (!video.paused) {
                                    enableVolume();
                                    return;
                                }
                                // Live players often attach MediaSource/HLS after the
                                // element is created. Do not permanently give up when
                                // play() runs before a source is ready.
                                var playResult = video.play();
                                if (playResult && playResult.catch) {
                                    playResult.catch(function(error) {
                                        console.warn('[VideoController] play deferred:', error && error.message);
                                        if (!video.__kiduyuMutedAutoplayTried) {
                                            video.__kiduyuMutedAutoplayTried = true;
                                            video.muted = true;
                                            setTimeout(attemptPlay, 0);
                                        }
                                    });
                                }
                            } catch(e) {}
                        }

                        video.addEventListener('playing', function() { setTimeout(enableVolume, 300); });
                        video.addEventListener('canplay', attemptPlay);
                        video.addEventListener('canplaythrough', attemptPlay);
                        video.addEventListener('loadedmetadata', attemptPlay);
                        video.addEventListener('durationchange', attemptPlay);
                        attemptPlay();
                        var attempts = 0;
                        var retryTimer = setInterval(function() {
                            if (!document.contains(video) || !video.paused) {
                                clearInterval(retryTimer);
                                return;
                            }
                            if (++attempts > 20) {
                                clearInterval(retryTimer);
                                return;
                            }
                            attemptPlay();
                        }, 500);
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

            })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    /**
     * Intercepts every HTML response (including nested iframes), strips the known
     * dlive.sx pop-under loaders, then injects the ad and autoplay guards directly
     * into that frame's <head>.
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

            val guardScript = """
                <script>
                (function() {
                    ${AdvancedAdBlocker.getBlockingJavaScript()}
                    ${AdvancedAdBlocker.getCss()}

                    if (window.__kiduyuScheduleFrameGuardInstalled) return;
                    window.__kiduyuScheduleFrameGuardInstalled = true;
                    var blockedHosts = [
                        'histats.com', 'profitableratecpmnetwork.com',
                        'piousshiners.com', 'burstyflavia.com', 'llvpn.com'
                    ];
                    function isBlocked(value) {
                        try {
                            var host = new URL(value, location.href).hostname.toLowerCase();
                            return blockedHosts.some(function(domain) {
                                return host === domain || host.endsWith('.' + domain);
                            });
                        } catch (_) { return false; }
                    }
                    function blockNewWindows() {
                        try {
                            window.open = function() { return null; };
                            window.showModalDialog = function() { return null; };
                            window.alert = function() {};
                            window.confirm = function() { return true; };
                            window.prompt = function() { return null; };
                        } catch (_) {}
                        try {
                            document.querySelectorAll('a, form').forEach(function(node) {
                                var target = (node.getAttribute('target') || '').toLowerCase();
                                if (target === '_blank' || target === '_new' || target === '_popup') {
                                    node.removeAttribute('target');
                                }
                            });
                        } catch (_) {}
                    }
                    blockNewWindows();
                    setInterval(blockNewWindows, 1000);
                    var appendChild = Node.prototype.appendChild;
                    Node.prototype.appendChild = function(node) {
                        if (node && node.tagName === 'SCRIPT' && isBlocked(node.src || '')) return node;
                        return appendChild.call(this, node);
                    };
                    document.addEventListener('click', function(event) {
                        var link = event.target && event.target.closest && event.target.closest('a');
                        var target = link && (link.getAttribute('target') || '').toLowerCase();
                        if (link && (target === '_blank' || target === '_new' ||
                            target === '_popup' || link.getAttribute('data-kiduyu-popup-target') === '1' ||
                            isBlocked(link.href || ''))) {
                            event.preventDefault();
                            event.stopImmediatePropagation();
                        }
                    }, true);
                    document.addEventListener('submit', function(event) {
                        var form = event.target;
                        var target = form && (form.getAttribute('target') || '').toLowerCase();
                        if (target === '_blank' || target === '_new' || target === '_popup' ||
                            (form && form.getAttribute('data-kiduyu-popup-target') === '1')) {
                            event.preventDefault();
                            event.stopImmediatePropagation();
                        }
                    }, true);
                    try { Notification.requestPermission = function() { return Promise.resolve('denied'); }; } catch (_) {}
                })();
                </script>
            """.trimIndent()

            val autoplayScript = """
                <script>
                (function() {
                    if (window.__kiduyuScheduleAutoplayInstalled) return;
                    window.__kiduyuScheduleAutoplayInstalled = true;

                    function unlock(v) {
                        try {
                            if (v.__kiduyuVolumeUnlockInstalled) return;
                            v.__kiduyuVolumeUnlockInstalled = true;
                            v.autoplay = true;
                            v.setAttribute('autoplay', '');
                            v.setAttribute('playsinline', '');

                            function enableVolume() {
                                try {
                                    v.muted = false;
                                    v.volume = 1;
                                    if (window.Android && Android.onVolumeEnabled) Android.onVolumeEnabled();
                                } catch(e) {}
                            }

                            function attemptPlay() {
                                try {
                                    if (!v.paused) {
                                        enableVolume();
                                        return;
                                    }
                                    var p = v.play();
                                    if (p && p.catch) {
                                        p.catch(function(e) {
                                            console.warn('[AutoplayInject] play deferred:', e && e.message);
                                            if (!v.__kiduyuMutedAutoplayTried) {
                                                v.__kiduyuMutedAutoplayTried = true;
                                                v.muted = true;
                                                setTimeout(attemptPlay, 0);
                                            }
                                        });
                                    }
                                } catch(e) {}
                            }

                            v.addEventListener('playing', function() { setTimeout(enableVolume, 300); });
                            v.addEventListener('canplay', attemptPlay);
                            v.addEventListener('canplaythrough', attemptPlay);
                            v.addEventListener('loadedmetadata', attemptPlay);
                            v.addEventListener('durationchange', attemptPlay);
                            attemptPlay();
                            var attempts = 0;
                            var retryTimer = setInterval(function() {
                                if (!document.contains(v) || !v.paused) {
                                    clearInterval(retryTimer);
                                    return;
                                }
                                if (++attempts > 20) {
                                    clearInterval(retryTimer);
                                    return;
                                }
                                attemptPlay();
                            }, 500);
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

            // The first schedule frame currently embeds these inline/loadable ad and
            // pop-under scripts. Remove them before WebView parses the document; DOM
            // cleanup alone would be too late because they execute while parsing.
            val sanitizedHtml = originalHtml
                .replace(
                    Regex(
                        """(?is)<script\b[^>]*\bsrc\s*=\s*["'][^"']*(?:histats|profitableratecpmnetwork|piousshiners|burstyflavia|llvpn)[^"']*["'][^>]*>.*?</script>"""
                    ),
                    ""
                )
                .replace(
                    Regex("""(?is)<script\b[^>]*>.*?(?:aclib\.runPop|popundersPerIP).*?</script>"""),
                    ""
                )

            val injectedScripts = "$guardScript$autoplayScript"
            val injected = when {
                sanitizedHtml.contains("</head>", ignoreCase = true) ->
                    sanitizedHtml.replace("</head>", "$injectedScripts</head>", ignoreCase = true)
                sanitizedHtml.contains("<body", ignoreCase = true) ->
                    sanitizedHtml.replace(
                        Regex("<body", RegexOption.IGNORE_CASE),
                        "$injectedScripts<body"
                    )
                else -> injectedScripts + sanitizedHtml
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

                // Playback does not require a second WebView. Keeping both disabled
                // prevents `window.open`, pop-unders, and ad-created dialog windows.
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false

                // Security layer bypass for http:// streaming streams running on https:// pages
                if (Build.VERSION.SDK_INT >= 21) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                cacheMode = WebSettings.LOAD_DEFAULT // Utilizes the browser cache for buffering
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

            webViewClient = object : AdBlockerWebViewClient(
                onPageFinished = {
                    android.util.Log.i(TAG, "[WebView] Page finished with AdBlocker")
                    injectVideoVolumeController()
                },
                onError = {
                    android.util.Log.e(TAG, "[WebView] Main frame error with AdBlocker")
                    if (hasDirectIframeUrls) {
                        tryNextStreamUrl()
                    } else {
                        tryNextPlayer()
                    }
                }
            ) {

                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    val uri = request?.url
                    // All actual stream hosts load in child iframes. If an ad tries to
                    // replace the wrapper itself, keep playback in the trusted dlive.sx
                    // document and discard that top-level navigation.
                    if (request?.isForMainFrame == true &&
                        (uri?.host?.equals(SCHEDULE_HOST, ignoreCase = true) != true ||
                            (uri != null && AdvancedAdBlocker.shouldBlock(uri)))
                    ) {
                        android.util.Log.i(TAG, "[AdBlock] Blocked top-frame navigation: $uri")
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun onPageCommitVisible(view: android.webkit.WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    // Install the popup guard before most delayed ad scripts execute.
                    installAdvancedAdGuards(view)
                }

                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()
                        ?: return super.shouldInterceptRequest(view, request)
                    val headers = request.requestHeaders

                    val adBlockerResponse = super.shouldInterceptRequest(view, request)
                    if (adBlockerResponse != null) return adBlockerResponse

                    // ── Autoplay Injection ────────────────────────────────────────
                    // Inject autoplay/unmute script into every HTML frame response,
                    // including cross-origin nested iframes.
                    val acceptHeader = headers?.get("Accept") ?: ""
                    if (acceptHeader.contains("text/html")) {
                        val injected = tryInjectAutoplayScript(url, headers)
                        if (injected != null) return injected
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (hasDirectIframeUrls) {
                        // TIMST URLs first load an outer HTML page and then create
                        // the actual player iframe dynamically. Keep the loading
                        // dialog visible during that handoff instead of dismissing
                        // it on the outer page's onPageFinished callback.
                        view?.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                dismissPageLoadingDialog()
                            }
                        }, DIRECT_IFRAME_LOADING_DELAY_MS)
                    } else {
                        dismissPageLoadingDialog()
                    }
                    android.util.Log.i(TAG, "[WebView] Schedule stream page finished: $url")
                    installAdvancedAdGuards(view)

                    // Inject CSS to suppress ad iframes and overlays that slip
                    // past the network block (e.g. JS-injected after page load).
                    // The MutationObserver catches dynamically added ad nodes.
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

                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onCreateWindow(
                    view: android.webkit.WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    android.util.Log.i(TAG, "[AdBlock] Blocked Schedule player popup window")
                    return false
                }

                override fun onJsAlert(
                    view: android.webkit.WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    android.util.Log.i(TAG, "[AdBlock] Blocked JavaScript alert from $url")
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(
                    view: android.webkit.WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    android.util.Log.i(TAG, "[AdBlock] Blocked JavaScript confirm from $url")
                    result?.confirm()
                    return true
                }

                override fun onJsPrompt(
                    view: android.webkit.WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: android.webkit.JsPromptResult?
                ): Boolean {
                    android.util.Log.i(TAG, "[AdBlock] Blocked JavaScript prompt from $url")
                    result?.cancel()
                    return true
                }

                override fun onProgressChanged(
                    view: android.webkit.WebView?,
                    newProgress: Int
                ) {
                    super.onProgressChanged(view, newProgress)
                }
            }
        }

        // JavaScript interface for toast messages
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun showToast(message: String) {
                runOnUiThread {
                    Toast.makeText(this@SchedulePlayerActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            @JavascriptInterface
            fun onVolumeEnabled() {
                runOnUiThread {
                    volumeControllerEnabled = true
                    android.util.Log.d(TAG, "[VideoController] Volume enabled; skipping future injections")
                }
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
                val controlsVisible by isTopBarVisible
                val serverMenuExpanded by isServerMenuExpanded
                MaterialTheme {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SchedulePlayerOverlay(
                            channelName = channelName,
                            eventTitle = eventTitle,
                            playerOptions = playerOptions,
                            selectedIndex = selectedPlayerIndex,
                            menuExpanded = serverMenuExpanded,
                            onMenuExpandedChange = { isServerMenuExpanded.value = it },
                            onSourceSelected = { index ->
                                if (index != selectedPlayerIndex && index in playerOptions.indices) {
                                    switchToPlayer(index)
                                }
                                isServerMenuExpanded.value = false
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
                "Switched to: ${player.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun tryNextPlayer(): Boolean {
        if (playerOptions.size > 1) {
            val nextIndex = (1 until playerOptions.size)
                .map { (selectedPlayerIndex + it) % playerOptions.size }
                .firstOrNull { playerOptions[it].url !in failedPlayerUrls }
                ?: return false
            val player = playerOptions[nextIndex]
            Toast.makeText(
                this,
                "Stream failed. Trying: ${player.name}",
                Toast.LENGTH_SHORT
            ).show()
            switchToPlayer(nextIndex)
            updateTopBar()
            return true
        }
        return false
    }

    private fun tryNextStreamUrl() {
        // Uniform logic for both direct iframe URLs and scraped ones
        playerOptions.getOrNull(selectedPlayerIndex)?.url?.let(failedPlayerUrls::add)
        tryNextPlayer()
    }

    private fun withFallbackPlayerOptions(discovered: List<PlayerOption>): List<PlayerOption> {
        val validDiscovered = discovered.filter { it.url.isNotBlank() }
        // dlive.sx exposes the complete player list on each watch page. Appending a
        // second hard-coded list created 12 visible entries for the six real servers.
        // Keep fallback URLs only for a failed/empty watch-page parse.
        if (validDiscovered.isNotEmpty()) {
            return validDiscovered
                .distinctBy { canonicalPlayerUrl(it.url) }
                .mapIndexed { index, option ->
                    option.copy(playerNumber = index + 1, isActive = index == selectedPlayerIndex)
                }
        }

        return STREAM_PATHS.map { path ->
            "${ScheduleApiService.BASE_URL}${path.removePrefix("/").format(channelId)}"
        }
            .filter { it.isNotBlank() }
            .distinctBy(::canonicalPlayerUrl)
            .mapIndexed { index, url ->
                PlayerOption(
                    playerNumber = index + 1,
                    url = url,
                    isActive = index == selectedPlayerIndex
                )
            }
    }

    private fun retryPlayback() {
        failedPlayerUrls.clear()
        selectedPlayerIndex = 0
        if (hasDirectIframeUrls) {
            setupWithDirectIframeUrls()
        } else if (channelId.isNotBlank()) {
            showPageLoadingDialog()
            fetchChannelWatchPage(channelId)
        } else {
            showRetryOrCloseDialog(
                message = "No channel ID or stream servers were provided.",
                onRetry = { recreate() }
            )
        }
    }

    private fun showRetryOrCloseDialog(message: String, onRetry: () -> Unit) {
        if (isFinishing || isDestroyed) return

        dismissPageLoadingDialog()
        MaterialAlertDialogBuilder(this)
            .setTitle("Unable to play channel")
            .setMessage(message)
            .setNegativeButton("Close") { _, _ -> finish() }
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun canonicalPlayerUrl(url: String): String = runCatching {
        val uri = Uri.parse(url)
        "${uri.path.orEmpty()}?${uri.query.orEmpty()}"
    }.getOrDefault(url)

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
        isServerMenuExpanded.value = false
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
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        dismissPageLoadingDialog()

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

    /**
     * Handle D-pad input before the WebView or an iframe can consume it.
     * WebView focus may change while playback is running, so relying only on
     * onKeyDown() leaves the hidden cursor unable to receive the next click.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isDpadKey(event)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                return onKeyDown(event.keyCode, event)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isDpadKeyCode(keyCode)) {
            isDpadNavigating = true
            showCursorAndResetTimer()
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressedDispatcher.onBackPressed()
            return true
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
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS
        )
    }

    private fun isDpadKeyCode(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,
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
        cursorView.visibility = View.VISIBLE
        cursorView.alpha = 1f
        cursorView.bringToFront()
        isCursorVisible = true
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }
}

// ============================================================================
// COMPOSE — Minimal player overlay and server picker
// ============================================================================

@Composable
private fun SchedulePlayerOverlay(
    channelName: String,
    eventTitle: String,
    playerOptions: List<PlayerOption>,
    selectedIndex: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSourceSelected: (Int) -> Unit,
    onBackPressed: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Surface(
                color = Color(0xE6191919),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp
            ) {
                IconButton(
                    onClick = { onMenuExpandedChange(!menuExpanded) },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Choose server",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                modifier = Modifier
                    .padding(top = 10.dp)
                    .widthIn(min = 280.dp, max = 340.dp),
                containerColor = Color(0xF01B1B1B),
                tonalElevation = 8.dp
            ) {
                ServerMenuHeader(
                    count = playerOptions.size,
                    selected = playerOptions.getOrNull(selectedIndex)?.name
                )
                playerOptions.forEachIndexed { index, option ->
                    ServerMenuItem(
                        option = option,
                        selected = index == selectedIndex,
                        onClick = { onSourceSelected(index) }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp),
            color = Color(0xB3000000),
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    text = channelName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (eventTitle.isNotBlank() && eventTitle != channelName) {
                    Text(
                        text = eventTitle,
                        color = Color(0xFFCACACA),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopEnd),
            color = Color(0xB3000000),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Stop playback"
                )
            }
        }
    }
}

@Composable
private fun ServerMenuHeader(count: Int, selected: String?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Playback servers",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (selected != null) {
                "$count available · $selected selected"
            } else {
                "$count available"
            },
            color = Color(0xFFBDBDBD),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ServerMenuItem(
    option: PlayerOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = option.name,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color(0xFF80CBC4) else Color.White
                )
                Text(
                    text = if (selected) "Playing now" else "Schedule stream",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Currently selected",
                    tint = Color(0xFF80CBC4)
                )
            }
        },
        onClick = onClick
    )
}
