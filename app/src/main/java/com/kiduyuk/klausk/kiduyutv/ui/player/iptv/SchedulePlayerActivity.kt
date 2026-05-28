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
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.MouseCursorView
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

/**
 * Schedule Player Activity for playing scheduled channels from dlhd.pk
 * Extends the existing PlayerActivity functionality with schedule-specific features
 * Takes an iframe HTML as intent extra and plays the scheduled channel in WebView
 * Includes a focusable row of player source options at the top for easy stream switching
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
    private var eventTitle: String = "Event"

    // Player sources
    private var playerSources: List<PlayerSource> = emptyList()
    private var selectedSourceIndex: Int = 0

    // UI State
    private var isTopBarVisible = true

    companion object {
        private const val TAG = "SchedulePlayer"

        // Intent extras
        const val EXTRA_IFRAME_HTML = "IFRAME_HTML"
        const val EXTRA_CHANNEL_NAME = "CHANNEL_NAME"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_SOURCES = "PLAYER_SOURCES"
        const val EXTRA_SELECTED_SOURCE = "SELECTED_SOURCE"

        /**
         * Data class representing a player source option
         */
        data class PlayerSource(
            val name: String,
            val iframeHtml: String
        )

        /**
         * Creates an intent to launch the SchedulePlayerActivity
         */
        fun createIntent(
            context: Context,
            iframeHtml: String,
            channelName: String,
            eventTitle: String,
            sources: List<PlayerSource> = emptyList(),
            selectedSourceIndex: Int = 0
        ) = android.content.Intent(context, SchedulePlayerActivity::class.java).apply {
            putExtra(EXTRA_IFRAME_HTML, iframeHtml)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
            // Serialize sources
            val names = sources.map { it.name }.toTypedArray()
            val htmls = sources.map { it.iframeHtml }.toTypedArray()
            putExtra(EXTRA_SOURCES + "_names", names)
            putExtra(EXTRA_SOURCES + "_htmls", htmls)
            putExtra(EXTRA_SELECTED_SOURCE, selectedSourceIndex)
        }
    }

    // Cursor hide timer
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled && isCursorVisible) {
            cursorView.animate().alpha(0f).setDuration(500).start()
            isCursorVisible = false
        }
    }
    private var isCursorVisible = false

    // Top bar auto-hide timer
    private val topBarHideHandler = Handler(Looper.getMainLooper())
    private val topBarHideRunnable = Runnable {
        if (isTopBarVisible && !isDpadNavigating) {
            hideTopBar()
        }
    }
    private var isDpadNavigating = false
    private val TOPBAR_HIDE_DELAY_MS = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get intent extras
        currentIframeHtml = intent.getStringExtra(EXTRA_IFRAME_HTML)
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Channel"
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event"
        selectedSourceIndex = intent.getIntExtra(EXTRA_SELECTED_SOURCE, 0)

        // Parse player sources
        val names = intent.getStringArrayExtra(EXTRA_SOURCES + "_names") ?: emptyArray()
        val htmls = intent.getStringArrayExtra(EXTRA_SOURCES + "_htmls") ?: emptyArray()
        playerSources = names.indices.mapNotNull { index ->
            if (index < htmls.size) {
                PlayerSource(name = names[index], iframeHtml = htmls[index])
            } else null
        }
        // If no sources provided, create default from current iframe
        if (playerSources.isEmpty() && !currentIframeHtml.isNullOrEmpty()) {
            playerSources = listOf(PlayerSource("Default", currentIframeHtml!!))
        }

        if (currentIframeHtml.isNullOrEmpty() && playerSources.isEmpty()) {
            Toast.makeText(this, "No stream URL provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Detect device type
        detectDeviceType()

        // Setup layout with Compose top bar
        setupLayout()

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
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
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        android.util.Log.e(TAG, "[WebView] Error: ${error?.description}")
                        // Try next source if current fails
                        tryNextSource()
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

            // Load iframe HTML
            loadIframeAtIndex(selectedSourceIndex)
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
                MaterialTheme {
                    PlayerSourceTopBar(
                        channelName = channelName,
                        eventTitle = eventTitle,
                        sources = playerSources,
                        selectedIndex = selectedSourceIndex,
                        onSourceSelected = { index ->
                            if (index != selectedSourceIndex && index in playerSources.indices) {
                                switchToSource(index)
                            }
                        },
                        onBackPressed = { showExitConfirmationDialog() }
                    )
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
     * Loads the iframe at the specified source index
     */
    private fun loadIframeAtIndex(index: Int) {
        if (index in playerSources.indices) {
            currentIframeHtml = playerSources[index].iframeHtml
        }
        currentIframeHtml?.let { html ->
            val baseUrl = "https://dlhd.pk"
            webView.loadDataWithBaseURL(
                baseUrl,
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    /**
     * Switches to the specified source index
     */
    private fun switchToSource(index: Int) {
        if (index in playerSources.indices) {
            selectedSourceIndex = index
            loadIframeAtIndex(index)
            Toast.makeText(
                this,
                "Switched to: ${playerSources[index].name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Tries to load the next available source
     */
    private fun tryNextSource() {
        if (playerSources.size > 1) {
            val nextIndex = (selectedSourceIndex + 1) % playerSources.size
            Toast.makeText(
                this,
                "Stream failed. Trying: ${playerSources[nextIndex].name}",
                Toast.LENGTH_SHORT
            ).show()
            switchToSource(nextIndex)
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

    // ── Top bar visibility ────────────────────────────────────────────────────

    private fun showTopBar() {
        isTopBarVisible = true
        scheduleTopBarHide()
    }

    private fun hideTopBar() {
        isTopBarVisible = false
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

    // ── D-pad input ───────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Track dpad navigation activity
        if (isDpadKey(event)) {
            isDpadNavigating = true
            showTopBar()
            scheduleTopBarHide()
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
        // Track dpad navigation
        if (isDpadKeyCode(keyCode)) {
            isDpadNavigating = true
            showTopBar()
            scheduleTopBarHide()
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private fun showCursorAndResetTimer() {
        if (isCursorDisabled) return
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

/**
 * Composable top bar showing channel info and a focusable row of player source options.
 * Designed for TV navigation with D-pad controls.
 */
@Composable
fun PlayerSourceTopBar(
    channelName: String,
    eventTitle: String,
    sources: List<SchedulePlayerActivity.PlayerSource>,
    selectedIndex: Int,
    onSourceSelected: (Int) -> Unit,
    onBackPressed: () -> Unit
) {
    // Focus requester for the first source button
    val firstSourceFocusRequester = remember { FocusRequester() }

    // Request focus on first source when composable is first displayed
    LaunchedEffect(Unit) {
        if (sources.isNotEmpty()) {
            firstSourceFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xCC000000), // Semi-transparent black
            )
            .padding(vertical = 8.dp)
    ) {
        // Channel info row
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
                // Back icon (text-based for simplicity)
                Text(
                    text = "←",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .focusable()
                        .padding(8.dp)
                )

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

            // "Sources" label
            if (sources.size > 1) {
                Text(
                    text = "Sources",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Player source options row (only show if more than 1 source)
        if (sources.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                itemsIndexed(sources) { index, source ->
                    PlayerSourceButton(
                        name = source.name,
                        isSelected = index == selectedIndex,
                        isFirst = index == 0,
                        focusRequester = if (index == 0) firstSourceFocusRequester else null,
                        onClick = { onSourceSelected(index) }
                    )
                }
            }
        }
    }
}

/**
 * A focusable button for selecting a player source.
 */
@Composable
fun PlayerSourceButton(
    name: String,
    isSelected: Boolean,
    isFirst: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Determine button colors based on state
    val backgroundColor = when {
        isSelected -> Color(0xFF2196F3) // Blue for selected
        isFocused -> Color(0xFF424242) // Dark gray for focused
        else -> Color(0xFF2D2D2D) // Default dark gray
    }

    val textColor = when {
        isSelected -> Color.White
        isFocused -> Color(0xFF448AFF) // Accent blue for focused
        else -> Color(0xFFE0E0E0) // Light gray for default
    }

    val borderColor = when {
        isFocused -> Color(0xFF448AFF) // Accent blue border when focused
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .focusable(
                interactionSource = interactionSource,
                indication = null
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = if (borderColor != Color.Transparent) {
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        } else null
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}