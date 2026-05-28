package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.ChannelWatchPage
import com.kiduyuk.klausk.kiduyutv.data.model.PlayerOption
import com.kiduyuk.klausk.kiduyutv.data.repository.ScheduleRepository
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.MouseCursorView
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.CoroutineScope
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

    // Player sources - now uses ChannelWatchPage and PlayerOption
    private var playerOptions: List<PlayerOption> = emptyList()
    private var selectedPlayerIndex: Int = 0
    private var channelWatchPage: ChannelWatchPage? = null

    // Direct iframe URLs passed from scraped channels
    private var iframeUrls: List<String> = emptyList()
    private var hasDirectIframeUrls: Boolean = false

    // UI State â€” backed by Compose state so the UI reacts to changes
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
         * Uses iframeUrls list for playing multiple stream options
         *
         * @param context Application context
         * @param channelId The channel ID (for reference)
         * @param channelName Display name for the channel
         * @param eventTitle Display name for the current event
         * @param iframeUrls List of iframe URLs for different stream players
         * @param selectedPlayerIndex Initial player index to select
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

    // Unified idle timer â€” hides both cursor and top bar
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled && isCursorVisible) {
            cursorView.animate().alpha(0f).setDuration(500).start()
            isCursorVisible = false
        }
        // Hide top bar together with cursor
        hideTopBar()
    }
    private var isCursorVisible = false

    // Top bar auto-hide timer (kept for legacy reference but unified into cursor timer)
    private val topBarHideHandler = Handler(Looper.getMainLooper())
    private val topBarHideRunnable = Runnable {
        if (isTopBarVisible.value && !isDpadNavigating) {
            hideTopBar()
        }
    }
    private var isDpadNavigating = false
    private val TOPBAR_HIDE_DELAY_MS = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get intent extras
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Channel"
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event"
        selectedPlayerIndex = intent.getIntExtra(EXTRA_SELECTED_PLAYER, 0)

        // Get iframe URLs passed directly from scraped channels
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

        // Detect device type
        detectDeviceType()

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        // Setup layout with Compose top bar
        setupLayout()

        // Use direct iframe URLs if available, otherwise fetch from channel page
        if (hasDirectIframeUrls) {
            setupWithDirectIframeUrls()
        } else {
            fetchChannelWatchPage(channelId)
        }
    }

    /**
     * Fetches the ChannelWatchPage to get player options and iframe URL
     */
    private fun fetchChannelWatchPage(channelId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                ScheduleRepository.getInstance().fetchChannelWatchPage(channelId)
            }

            result.fold(
                onSuccess = { watchPage ->
                    channelWatchPage = watchPage
                    playerOptions = watchPage.playerOptions

                    // Find initial player to use
                    if (playerOptions.isEmpty()) {
                        // No player options, use default iframe
                        currentIframeHtml = ScheduleRepository.getInstance()
                            .generateIframeHtml(watchPage.defaultIframeUrl)
                    } else {
                        // Use selected player or first active player
                        val playerToUse = playerOptions.getOrNull(selectedPlayerIndex)
                            ?: playerOptions.find { it.isActive }
                            ?: playerOptions.first()

                        currentIframeHtml = ScheduleRepository.getInstance()
                            .generateIframeHtml(playerToUse.url)

                        // Update selected index
                        selectedPlayerIndex = playerOptions.indexOf(playerToUse)
                    }

                    // Load the stream
                    loadCurrentStream()

                    // Update the top bar
                    updateTopBar()
                },
                onFailure = { error ->
                    android.util.Log.e(TAG, "Failed to fetch watch page: ${error.message}")
                    // Fall back to default stream URL
                    currentIframeHtml = ScheduleRepository.getInstance()
                        .generateIframeHtml("https://dlhd.pk/player/stream-$channelId.php")
                    loadCurrentStream()
                }
            )
        }
    }

    /**
     * Loads the current stream into WebView
     */
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

    /**
     * Sets up the player with direct iframe URLs passed from scraped channels
     * Shows toast with total number of iframes and loads the first one
     */
    private fun setupWithDirectIframeUrls() {
        if (iframeUrls.isEmpty()) {
            Toast.makeText(this, "No stream URLs available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Show toast with total number of iframes
        val totalIframes = iframeUrls.size
        Toast.makeText(
            this,
            "$totalIframes stream option(s) available for $channelName",
            Toast.LENGTH_LONG
        ).show()

        android.util.Log.i(TAG, "Setting up with $totalIframes direct iframe URLs")

        // Convert iframe URLs to PlayerOptions
        playerOptions = iframeUrls.mapIndexed { index, url ->
            PlayerOption(
                playerNumber = index + 1,
                url = url,
                isActive = index == selectedPlayerIndex
            )
        }

        // Ensure selected index is valid
        if (selectedPlayerIndex >= playerOptions.size) {
            selectedPlayerIndex = 0
        }

        // Load the first iframe
        if (iframeUrls.isNotEmpty()) {
            currentIframeHtml = generateIframeHtml(iframeUrls[selectedPlayerIndex])
            loadCurrentStream()
        }

        // Update top bar with player options
        updateTopBar()
    }

    /**
     * Generates iframe HTML for the given stream URL
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
                <iframe src="$streamUrl" width="100%" height="100%" style="border:0;" allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Updates the top bar with current player options.
     * The ComposeView recomposes automatically when Compose state changes.
     */
    private fun updateTopBar() {
        // Trigger recomposition by resetting top bar visibility
        isTopBarVisible.value = true
        scheduleTopBarHide()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLayout() {
        // Create root layout
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Create WebView
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
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    android.util.Log.i(TAG, "[WebView] Page finished: $url")

                    // Inject CSS to hide ads
                    view?.evaluateJavascript("""
                        (function() {
                            var style = document.createElement('style');
                            style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay, iframe[src*="ads"] { display: none !important; }';
                            document.head.appendChild(style);
                        })();
                    """.trimIndent(), null)

                    // After 3 seconds, attempt autoplay and max volume on any video
                    // inside the page or its nested iframes
                    Handler(Looper.getMainLooper()).postDelayed({
                        view?.evaluateJavascript("""
                            (function() {
                                function activateVideos(doc) {
                                    try {
                                        var videos = doc.querySelectorAll('video');
                                        videos.forEach(function(v) {
                                            v.volume = 1;
                                            v.muted = false;
                                            if (v.paused) {
                                                v.play().catch(function(e) {
                                                    // Autoplay blocked â€” try muted first then unmute
                                                    v.muted = true;
                                                    v.play().then(function() {
                                                        v.muted = false;
                                                        v.volume = 1;
                                                    }).catch(function() {});
                                                });
                                            } else {
                                                v.volume = 1;
                                                v.muted = false;
                                            }
                                        });
                                    } catch(e) {}
                                }

                                // Target the top-level document
                                activateVideos(document);

                                // Target any same-origin iframes
                                var iframes = document.querySelectorAll('iframe');
                                iframes.forEach(function(f) {
                                    try {
                                        if (f.contentDocument) activateVideos(f.contentDocument);
                                    } catch(e) {}
                                });
                            })();
                        """.trimIndent(), null)
                    }, 3000)
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        android.util.Log.e(TAG, "[WebView] Error: ${error?.description}")
                        // Try next player/stream if current fails
                        if (hasDirectIframeUrls) {
                            tryNextStreamUrl()
                        } else {
                            tryNextPlayer()
                        }
                    }
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    android.util.Log.i(TAG, "[WebChrome] onShowCustomView called")
                }

                override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    android.util.Log.d(TAG, "[WebChrome] Progress: $newProgress%")
                }
            }
        }

        // Create cursor view for TV navigation
        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Create ComposeView for top bar with player source options
        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 0
            }
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

        // Add views to layout (order matters - WebView at bottom, Compose on top)
        rootLayout.addView(webView)
        rootLayout.addView(composeView)
        if (!isCursorDisabled) {
            rootLayout.addView(cursorView)
            cursorView.bringToFront()
        }

        setContentView(rootLayout)

        // Make root focusable
        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        // Get screen dimensions after layout
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

        // Schedule top bar auto-hide
        scheduleTopBarHide()
    }

    /**
     * Switches to the specified player index
     */
    private fun switchToPlayer(index: Int) {
        if (index in playerOptions.indices) {
            selectedPlayerIndex = index
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

    /**
     * Tries to load the next available player
     */
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

    /**
     * Tries to load the next available stream URL (for direct iframe URLs)
     */
    private fun tryNextStreamUrl() {
        if (iframeUrls.size > 1) {
            val nextIndex = (selectedPlayerIndex + 1) % iframeUrls.size
            Toast.makeText(
                this,
                "Stream failed. Trying: Player ${nextIndex + 1}",
                Toast.LENGTH_SHORT
            ).show()
            selectedPlayerIndex = nextIndex
            currentIframeHtml = generateIframeHtml(iframeUrls[nextIndex])
            loadCurrentStream()
        }
    }

    private fun detectDeviceType() {
        val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager

        if (uiModeManager.currentModeType != android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
            android.util.Log.i(TAG, "[Device] Non-TV detected, cursor disabled")
        } else {
            android.util.Log.i(TAG, "[Device] TV detected, cursor enabled")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: android.content.Context): android.webkit.WebView {
        val webView = android.webkit.WebView(context)

        // Check hardware acceleration
        val isHardwareAccelerated = context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

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

    // â”€â”€ Top bar visibility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun showTopBar() {
        isTopBarVisible.value = true
        scheduleTopBarHide()
    }

    private fun hideTopBar() {
        isTopBarVisible.value = false
    }

    private fun scheduleTopBarHide() {
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        topBarHideHandler.postDelayed(topBarHideRunnable, TOPBAR_HIDE_DELAY_MS)
    }

    private fun resetDpadNavigation() {
        isDpadNavigating = false
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        topBarHideHandler.postDelayed(topBarHideRunnable, TOPBAR_HIDE_DELAY_MS)
    }

    // â”€â”€ Lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ D-pad input â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Track dpad navigation activity â€” unified into showCursorAndResetTimer
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
        // Unified: show both cursor and top bar, reset shared idle timer
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
        return event.source and android.view.InputDevice.SOURCE_DPAD == android.view.InputDevice.SOURCE_DPAD ||
                event.keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS
        )
    }

    private fun isDpadKeyCode(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS
        )
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

        val downEvent = android.view.MotionEvent.obtain(downTime, eventTime, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = android.view.MotionEvent.obtain(downTime, eventTime + 100, android.view.MotionEvent.ACTION_UP, x, y, 0)

        downEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN

        window.decorView.dispatchTouchEvent(downEvent)
        window.decorView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    /**
     * Unified idle timer reset â€” shows both cursor and top bar, then hides both after 5 seconds.
     * Works on TV (cursor + top bar) and non-TV (top bar only) devices.
     */
    private fun showCursorAndResetTimer() {
        // Always show top bar and cancel any pending top bar hide
        isTopBarVisible.value = true
        topBarHideHandler.removeCallbacks(topBarHideRunnable)

        if (isCursorDisabled) {
            // Non-TV: no cursor, but still drive top bar hide via the cursor timer slot
            cursorHideHandler.removeCallbacks(cursorHideRunnable)
            cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
            return
        }

        // TV: show cursor and reset shared idle timer
        cursorView.animate().cancel()
        cursorView.alpha = 1f
        isCursorVisible = true
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }
}

// ============================================================================
// COMPOSE â€” Player Source Selection Top Bar
// ============================================================================

/**
 * Composable top bar showing channel info and a focusable row of player source options.
 * Designed for TV navigation with D-pad controls.
 * Uses PlayerOption from ChannelWatchPage for handling multiple streams.
 * Focus automatically shifts to the currently selected source button.
 */
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
            .background(
                color = Color(0xCC000000), // Semi-transparent black
            )
            .padding(vertical = 8.dp)
    ) {
        // Channel info row with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button and channel info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button - always focusable
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

            // "Sources" label and player options count
            if (playerOptions.isNotEmpty()) {
                Text(
                    text = "${playerOptions.size} Players",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Player source options row (always visible when players are available)
        if (playerOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            // "Sources:" label
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

            // Player buttons in a LazyRow for better TV navigation
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

/**
 * Back button composable - always focusable for TV navigation
 */
@Composable
private fun BackButton(
    onBackPressed: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
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

/**
 * A focusable button for selecting a player option.
 * Styled similar to the image reference - dark background with blue border when focused.
 * Focus is programmatically set when this button's index matches the selected index.
 */
@Composable
private fun PlayerOptionButton(
    playerNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Determine button colors based on state (matching image reference style)
    val backgroundColor = when {
        isSelected -> Color(0xFF2196F3) // Blue for selected
        isFocused -> Color(0xFF2D2D2D) // Dark gray for focused
        else -> Color(0xFF1A1A1A)      // Darker default
    }

    val textColor = when {
        isSelected -> Color.White
        isFocused -> Color(0xFF448AFF) // Accent blue for focused
        else -> Color(0xFFE0E0E0)      // Light gray for default
    }

    val borderColor = when {
        isSelected -> Color(0xFFFF1744) // Red border for selected (like in image)
        isFocused -> Color(0xFF448AFF)  // Blue border for focused
        else -> Color(0xFF404040)       // Subtle border for default
    }

    Surface(
        modifier = Modifier
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
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
            // Player number indicator
            Text(
                text = "P$playerNumber",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            // Checkmark icon for selected state (like in image)
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