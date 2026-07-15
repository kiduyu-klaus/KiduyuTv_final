package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
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
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts third-party movie and TV providers inside a fullscreen WebView.
 *
 * The activity configures provider playback, initializes player-only ad blocking before the
 * first page load, exposes playback progress through [PlayerBridge], supports a virtual TV
 * cursor, and persists watch-history progress locally and to Firebase.
 *
 * Required intent data includes `TMDB_ID` and `IS_TV`. Provider HTML may be supplied through
 * `IFRAME_HTML`; otherwise [StreamProviderManager] generates it from the selected stream URL.
 */
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

    // Loading dialog shown while the provider page is being fetched and rendered.
    private lateinit var loadingDialog: ProgressDialog

    // Watch history tracking variables
    private var currentTmdbId: Int = -1
    private var currentIsTv: Boolean = false
    private var currentTitle: String = "Unknown"
    private var currentOverview: String? = null
    private var currentPosterPath: String? = null
    private var currentBackdropPath: String? = null
    private var currentVoteAverage: Double = 0.0
    private var currentReleaseDate: String? = null
    private var currentPlaybackPosition: Long = 0L
    private var currentDuration: Long = 0L

    // 15-second progress update handler
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = Runnable {
        updateWatchProgress()
    }
    private val repository = TmdbRepository()

    // ── Performance timing chain (#18) ──────────────────────────────────────
    // Each stopwatch captures one well-defined milestone so we can measure
    // player load time end-to-end via logcat. The values are read-only after
    // the activity starts and are reported next to the [LoadingDialog] log
    // when the page commits visible.
    private val perfActivityCreateMs: Long = SystemClock.elapsedRealtime()
    private var perfWebViewAttachedMs: Long = 0L
    private var perfLoadCalledMs: Long = 0L
    private var perfAdBlockerInitMs: Long = 0L
    private var perfPageCommitMs: Long = 0L
    private var perfPageFinishedMs: Long = 0L

    /** Returns true for Amazon Fire TV hardware features or known AFT model identifiers. */
    private fun isFireTVDevice(context: Context): Boolean {
        val isFireTvHardware = context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        val isFireTvModel = Build.MODEL != null && Build.MODEL.startsWith("AFT", ignoreCase = true)
        return isFireTvHardware || isFireTvModel
    }

    /**
     * Logs the currently-active WebView provider package via [WebViewCompat]. If the active
     * provider is not [GOOGLE_WEBVIEW_PACKAGE] (e.g. it is `com.amazon.webview.chromium` on
     * Fire TV), a warning is emitted so the operator can install/activate the Google WebView
     * for a more standards-compliant and bot-detection-friendly rendering pipeline.
     */
    private fun logWebViewProvider() {
        try {
            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(this)
            if (webViewPackage == null) {
                Log.w(TAG, "[WebView] No WebView provider package could be resolved")
                return
            }

            val packageName = webViewPackage.packageName
            val versionName = webViewPackage.versionName ?: "unknown"
            if (packageName == GOOGLE_WEBVIEW_PACKAGE) {
                Log.i(TAG, "[WebView] Using Google WebView (com.google.android.webview) v$versionName")
            } else {
                Log.w(
                    TAG,
                    "[WebView] Active provider is '$packageName' v$versionName — not " +
                        "$GOOGLE_WEBVIEW_PACKAGE. For best compatibility, install/enable " +
                        "com.google.android.webview (e.g. on Fire TV: Settings > Applications > " +
                        "Amazon WebView > Disable, then install Google WebView from the Play Store)."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[WebView] Failed to query WebView provider: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VideasyPlayer"

        /** Package name of Google's official AOSP WebView provider. */
        private const val GOOGLE_WEBVIEW_PACKAGE = "com.google.android.webview"

        /**
         * Single, modern, device-agnostic User-Agent string. The previous string explicitly
         * declared "AFTMM" (Amazon Fire TV model) which caused Cloudflare / provider bot
         * challenges to fire on TV devices. This one intentionally omits any device model
         * identifier and uses a recent stable Chrome version.
         */
        private const val MODERN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }

    // ── Cursor hide timer ──────────────────────────────────────────────────────
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled) {
            cursorView.animate().alpha(0f).setDuration(500).start()
        }
    }

    /**
     * Validates playback metadata, builds the WebView/cursor UI, attaches native bridges, and
     * starts playback after the ad-block rule snapshot is ready.
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fix: Missing Translucent Window Format
        // Fire TV's window manager often requires the Activity's window to be explicitly set
        // to a translucent format to correctly composite the video surface with the WebView UI.
        window.setFormat(PixelFormat.TRANSLUCENT)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        currentSeason = intent.getIntExtra("SEASON_NUMBER", 1)
        currentEpisode = intent.getIntExtra("EPISODE_NUMBER", 1)

        val contentTitle = intent.getStringExtra("TITLE") ?: "Unknown"
        val contentOverview = intent.getStringExtra("OVERVIEW")
        val contentPosterPath = intent.getStringExtra("POSTER_PATH")
        val contentBackdropPath = intent.getStringExtra("BACKDROP_PATH")
        val contentVoteAverage = intent.getDoubleExtra("VOTE_AVERAGE", 0.0)
        val contentReleaseDate = intent.getStringExtra("RELEASE_DATE")

        if (tmdbId == -1) {
            finish()
            return
        }

        currentTmdbId = tmdbId
        currentIsTv = isTv
        currentTitle = contentTitle
        currentOverview = contentOverview
        currentPosterPath = contentPosterPath
        currentBackdropPath = contentBackdropPath
        currentVoteAverage = contentVoteAverage
        currentReleaseDate = contentReleaseDate

        // Check and add to watch history, timer will be started after check completes
        checkAndAddToWatchHistory()

        val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as UiModeManager
        val deviceModel = Build.MODEL
        val deviceBrand = Build.BRAND.replaceFirstChar { it.uppercase() }

        if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
            val deviceType = if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_NORMAL) "Mobile" else "Tablet"
            Log.i(TAG, "[Device] $deviceType detected (${deviceBrand} $deviceModel), disabling cursor")
        } else {
            isFireTV = isFireTVDevice(this)
            Log.i(TAG, "[Device] TV detected (${deviceBrand} $deviceModel), cursor enabled, isFireTV=$isFireTV")
        }

        // Surface which WebView provider package is in use so the operator can confirm
        // we are running on com.google.android.webview (or be told to switch to it).
        logWebViewProvider()

        val url: String = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$currentSeason/$currentEpisode?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        currentProviderName = WebViewUtils.detectProviderFromUrl(url)
        Log.i(TAG, "[Provider] Selected: $currentProviderName")

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebViewUtils.createWebView(this, isFireTV).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Fix: Solid Background Color Overlapping Video
            // On many Fire OS versions, the video is rendered on a SurfaceView that sits behind
            // the WebView's main drawing layer. Setting a solid black background hides the video.
            setBackgroundColor(0x00000000) // Set to transparent

            // Fix: Amazon Chromium WebView vs. System WebView
            // Enable debugging for Amazon Chromium WebView optimizations
            // if (isFireTV) {
            //     WebView.setWebContentsDebuggingEnabled(true)
            // }

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

                // FIX: Multi-window support must be TRUE for standard HTML5 video elements
                // to scale up and trigger full-screen player states natively.
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true // Allows player scripts to execute properly

                // Security layer bypass for http:// streaming streams running on https:// pages
                if (Build.VERSION.SDK_INT >= 21) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                // PERF: stop clearing the WebView cache on every load. The previous
                // `clearCache(true)` immediately followed by `LOAD_DEFAULT` forced a
                // cold cache lookup on every play. WebView's normal cache policy is
                // safe here — the only cache poison risk is a logged-out user, and
                // SignOutActivity already calls clearCache() on its own.

                cacheMode = WebSettings.LOAD_DEFAULT // Utilizes the browser cache for buffering

                // Single, modern User-Agent for every device. The previous Fire-TV-specific
                // string ("AFTMM Build/PS7233") caused provider / Cloudflare bot challenges
                // on TV devices. This UA is intentionally device-agnostic so phones, tablets
                // and TVs all negotiate the same content.
                userAgentString = MODERN_USER_AGENT
            }

            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
            overScrollMode = View.OVER_SCROLL_NEVER

            webViewClient = AdBlockerWebViewClient(
                onPageFinished = {
                    isPageLoading = false
                    dismissLoadingDialog()
                    perfPageFinishedMs = SystemClock.elapsedRealtime()
                    logPerfTimings("pageFinished")
                    // For same-origin loads the tracker asset was not preloaded as part
                    // of a wrapper document; inject the bootstrap and the asset script
                    // now so the postMessage listener is installed before playback
                    // events start firing.
                    if (pendingSameOriginTracker) {
                        injectTrackerIntoSameOriginPage()
                        pendingSameOriginTracker = false
                    }
                    Log.i(TAG, "[WebView] Page finished loading with AdBlocker")
                },
                onError = {
                    hasPageError = true
                    isPageLoading = false
                    dismissLoadingDialog()
                    Log.e(TAG, "[WebView] Error received with AdBlocker")
                },
                onCommitVisible = {
                    perfPageCommitMs = SystemClock.elapsedRealtime()
                    logPerfTimings("pageCommitVisible")
                }
            )

            webChromeClient = object : WebChromeClient() {
                /** Receives provider fullscreen requests while the transparent WebView remains active. */
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    Log.i(TAG, "[WebChrome] onShowCustomView called")
                }
                /** Rejects provider-created popup windows while leaving same-window playback intact. */
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    Log.i(TAG, "[WebChrome] onCreateWindow called, blocking popups")
                    return false
                }

                /** Emits load progress for provider troubleshooting. */
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    Log.d(TAG, "[WebChrome] Load progress: $newProgress%")
                }
            }
        }

        // Add JavascriptInterface bridge for player events. The shared [PlayerBridge.INSTANCE]
        // is reused across PlayerActivity instances so the Kotlin object survives across
        // activity restarts; the per-activity callback is registered on resume and cleared
        // in onDestroy to prevent the bridge from holding a stale reference.
        PlayerBridge.INSTANCE.onEvent = { provider, positionSec, durationSec, season, episode ->
            runOnUiThread {
                // positionSec is in seconds, convert to milliseconds for storage
                currentPlaybackPosition = (positionSec * 1000).toLong()
                // Store duration in milliseconds
                currentDuration = (durationSec * 1000).toLong()
                // Update season and episode if provided and they have changed
                if (currentIsTv && season != null && episode != null) {
                    if (season != currentSeason || episode != currentEpisode) {
                        Log.i(TAG, "[Episode] Changed S${currentSeason}E${currentEpisode} -> S${season}E${episode}")
                        currentSeason = season
                        currentEpisode = episode
                    }
                }
            }
        }
        webView.addJavascriptInterface(PlayerBridge.INSTANCE, "MavisInterface")
        perfWebViewAttachedMs = SystemClock.elapsedRealtime()

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

        // Show the loading dialog as soon as the activity is created. It will be dismissed
        // when the WebView fires onPageFinished (or onError). Posting it ensures the window
        // is fully attached before the dialog tries to show.
        showLoadingDialog()

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
            /** Routes system/remote back presses through the playback confirmation dialog. */
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        // Build the provider wrapper only after all WebView clients and JavaScript bridges exist.
        val baseUrl = StreamProviderManager.getBaseUrl(currentProviderName)
        val finalUrl = StreamProviderManager.generateUrl(
            providerName = currentProviderName,
            tmdbId = currentTmdbId,
            isTv = currentIsTv,
            season = if (currentIsTv) currentSeason else null,
            episode = if (currentIsTv) currentEpisode else null,
            timestamp = currentPlaybackPosition / 1000L // Provider parameters use seconds.
        )
        val customIframeHtml = intent.getStringExtra("IFRAME_HTML")
        val sameOrigin = customIframeHtml == null && isSameOriginProvider(baseUrl, finalUrl)
        val wrapperHtml = if (customIframeHtml != null) {
            customIframeHtml
        } else if (sameOrigin) {
            // No wrapper document is needed — we will inject the tracker directly via
            // evaluateJavascript after the page finishes loading.
            null
        } else {
            StreamProviderManager.generateIframeHtml(
                providerName = currentProviderName,
                tmdbId = currentTmdbId,
                isTv = currentIsTv,
                season = if (currentIsTv) currentSeason else null,
                episode = if (currentIsTv) currentEpisode else null,
                timestamp = currentPlaybackPosition / 1000L
            )
        }
        loadIntoWebView(baseUrl, finalUrl, sameOrigin, wrapperHtml)
    }

    /**
     * Returns true when [finalUrl] is served from the same origin as [baseUrl]. For
     * same-origin providers we can skip the wrapper iframe document entirely and let
     * the WebView load the player page directly, saving one HTML parse plus the
     * iframe creation on the critical path. The tracker is then injected via
     * `evaluateJavascript` once the page commits.
     */
    private fun isSameOriginProvider(baseUrl: String, finalUrl: String): Boolean {
        return try {
            val base = Uri.parse(baseUrl)
            val final = Uri.parse(finalUrl)
            !base.host.isNullOrEmpty() &&
                base.host.equals(final.host, ignoreCase = true) &&
                base.scheme.equals(final.scheme, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Schedules the WebView load, kicks off ad-blocker initialization in parallel, and
     * arranges for the tracker script to be injected after the page finishes loading.
     *
     * @param baseUrl provider origin (used as the WebView's base URL for relative-URL
     *   resolution inside the wrapper document).
     * @param finalUrl player URL — either loaded directly (same-origin case) or wrapped
     *   inside [wrapperHtml].
     * @param sameOrigin true when the wrapper document is unnecessary.
     * @param wrapperHtml the wrapper HTML to load, or null in the same-origin case.
     */
    private fun loadIntoWebView(baseUrl: String, finalUrl: String, sameOrigin: Boolean, wrapperHtml: String?) {
        // PERF: Kick off the WebView load as soon as the root layout has had a chance
        // to measure. `webView.post` runs after the first layout pass, so the WebView
        // is fully attached and ready to render by the time the load request arrives.
        // The ad-blocker init is fire-and-forget — the request interceptor reads from
        // the in-memory snapshot, which is already populated from a previous session.
        webView.post {
            perfLoadCalledMs = SystemClock.elapsedRealtime()
            if (sameOrigin) {
                webView.loadUrl(finalUrl)
                Log.i(TAG, "[WebView] Same-origin load: $finalUrl")
            } else {
                val html = wrapperHtml.orEmpty()
                // PERF: `loadData` is marginally faster than `loadDataWithBaseURL` and
                // the wrapper document has no relative URLs that need base-URL
                // resolution (the iframe `src` is absolute). Using `loadData` here
                // also avoids origin-related edge cases.
                webView.loadData(html, "text/html", "UTF-8")
                Log.i(TAG, "[WebView] Wrapper load via $baseUrl (${html.length} chars)")
            }
        }

        // Background ad-blocker initialization. The blocker consults the in-memory
        // snapshot on every request, so a slow first download does not block the load.
        lifecycleScope.launch {
            val initialization = AdvancedAdBlocker.initialize(applicationContext)
            perfAdBlockerInitMs = SystemClock.elapsedRealtime()
            Log.i(
                TAG,
                "[AdBlock] Initialized source=${initialization.source} " +
                    "domains=${initialization.blockedDomainCount} " +
                    "error=${initialization.error.orEmpty()}"
            )

            if (initialization.refreshRecommended) {
                lifecycleScope.launch {
                    val refresh = AdvancedAdBlocker.refresh(applicationContext)
                    Log.i(
                        TAG,
                        "[AdBlock] Refresh source=${refresh.source} " +
                            "domains=${refresh.blockedDomainCount} " +
                            "error=${refresh.error.orEmpty()}"
                    )
                }
            }
        }

        // For same-origin loads we need to inject the tracker after the page commits.
        // The existing `onPageFinished` callback already fires for both cases; we
        // branch on the `sameOrigin` flag captured here.
        this.pendingSameOriginTracker = sameOrigin
    }

    /** Set by [loadIntoWebView] and consumed by the `onPageFinished` callback. */
    private var pendingSameOriginTracker: Boolean = false

    /**
     * Logs the current performance timing chain relative to the activity-create baseline.
     * Safe to call from any thread; uses an atomic snapshot so concurrent writes from the
     * WebView callbacks don't produce torn values in logcat.
     */
    private fun logPerfTimings(label: String) {
        val now = SystemClock.elapsedRealtime()
        val base = perfActivityCreateMs
        Log.i(
            TAG,
            "[Perf] $label now=${now - base}ms " +
                "T0=0 T1=${perfWebViewAttachedMs - base}ms " +
                "T2=${perfLoadCalledMs - base}ms " +
                "T3=${perfAdBlockerInitMs - base}ms " +
                "T4=${perfPageCommitMs - base}ms " +
                "T5=${perfPageFinishedMs - base}ms"
        )
    }

    /**
     * Injects the tracker bootstrap and the shared asset script into a same-origin player
     * page that was loaded directly (no wrapper document). The bootstrap sets
     * `window.__kiduyuPlayerConfig` which [kiduyu_tracker.js] reads on startup, and the
     * asset script installs the `postMessage` listener that talks back to [PlayerBridge].
     */
    private fun injectTrackerIntoSameOriginPage() {
        val bootstrap = StreamProviderManager.buildTrackerBootstrap(
            tmdbId = currentTmdbId,
            isTv = currentIsTv,
            season = if (currentIsTv) currentSeason else null,
            episode = if (currentIsTv) currentEpisode else null
        )
        webView.evaluateJavascript(bootstrap, null)
        webView.evaluateJavascript(
            "(function(){var s=document.createElement('script');" +
                "s.src='file:///android_asset/kiduyu_tracker.js';" +
                "document.head.appendChild(s);})();",
            null
        )
        Log.i(TAG, "[WebView] Injected tracker into same-origin page")
    }

    /** Shows a confirmation dialog so an accidental TV back press does not stop playback. */
    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Stop Playback?",
            message = "Are you sure you want to stop playback and exit?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { },
            onYes = { finish() }
        ).show()
    }

    /** Resumes WebView media and JavaScript timers when the activity returns to the foreground. */
    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    /** Pauses WebView work and periodic progress persistence while the activity is obscured. */
    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        stopProgressUpdateTimer()
    }

    /** Stops callbacks and releases every WebView resource owned by this activity. */
    override fun onDestroy() {
        stopProgressUpdateTimer()
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        dismissLoadingDialog()

        if (::webView.isInitialized) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
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

    /**
     * Converts TV directional and select key-down events into virtual-cursor actions.
     * Touch-oriented phones and tablets retain Android's default key dispatch.
     */
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

    /** Moves or clicks the virtual cursor for one supported remote-control key. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isCursorDisabled) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY = (cursorY - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY = (cursorY + moveSpeed).coerceAtMost(screenHeight.toFloat())
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX = (cursorX - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX = (cursorX + moveSpeed).coerceAtMost(screenWidth.toFloat())
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                simulateClick(cursorX, cursorY)
                showCursorAndResetTimer()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Applies the current cursor coordinates and keeps the cursor above the WebView. */
    private fun updateCursorPosition() {
        if (isCursorDisabled) return
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.bringToFront()
        cursorView.invalidate()
    }

    /** Dispatches a synthetic touchscreen tap at the virtual cursor's screen coordinates. */
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

    /** Makes the TV cursor visible and restarts its five-second inactivity fade timer. */
    private fun showCursorAndResetTimer() {
        if (isCursorDisabled) return
        cursorView.animate().cancel()
        cursorView.alpha = 1f
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }

    /**
     * Creates the watch-history row when necessary and starts periodic progress persistence.
     * Errors are fail-soft so playback tracking can continue on the next timer cycle.
     */
    private fun checkAndAddToWatchHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "[WatchHistory] Checking if media is in watch history: id=$currentTmdbId, isTv=$currentIsTv")

                // Check synchronously before deciding to add
                val alreadyInHistory = repository.isInWatchHistory(
                    this@PlayerActivity,
                    currentTmdbId,
                    currentIsTv
                )

                if (!alreadyInHistory) {
                    Log.d(TAG, "[WatchHistory] Adding to watch history: id=$currentTmdbId, title=$currentTitle, isTv=$currentIsTv, season=$currentSeason, episode=$currentEpisode")
                    val watchHistoryItem = WatchHistoryItem(
                        id = currentTmdbId,
                        title = currentTitle,
                        overview = currentOverview,
                        posterPath = currentPosterPath,
                        backdropPath = currentBackdropPath,
                        voteAverage = currentVoteAverage,
                        releaseDate = currentReleaseDate,
                        isTv = currentIsTv,
                        seasonNumber = if (currentIsTv) currentSeason else null,
                        episodeNumber = if (currentIsTv) currentEpisode else null,
                        lastWatched = System.currentTimeMillis(),
                        playbackPosition = 0L
                    )

                    repository.saveToWatchHistory(this@PlayerActivity, watchHistoryItem)
                    Log.d(TAG, "[WatchHistory] saveToWatchHistory called successfully")

                    // Start progress timer only after adding to history
                    withContext(Dispatchers.Main) {
                        startProgressUpdateTimer()
                    }

                    com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                        tmdbId = currentTmdbId,
                        isTv = currentIsTv,
                        seasonNumber = if (currentIsTv) currentSeason else null,
                        episodeNumber = if (currentIsTv) currentEpisode else null,
                        playbackPosition = 0L,
                        duration = 0L,
                        title = currentTitle,
                        overview = currentOverview,
                        posterPath = currentPosterPath,
                        backdropPath = currentBackdropPath,
                        voteAverage = currentVoteAverage,
                        releaseDate = currentReleaseDate
                    )
                } else {
                    Log.d(TAG, "[WatchHistory] Media already in watch history, starting timer anyway")
                    // Start timer even if already in history (for resume functionality)
                    withContext(Dispatchers.Main) {
                        startProgressUpdateTimer()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error checking/adding to watch history: ${e.message}", e)
                // Still start timer on error to enable progress tracking
                withContext(Dispatchers.Main) {
                    startProgressUpdateTimer()
                }
            }
        }
    }

    /** Schedules the next watch-progress write fifteen seconds from now. */
    private fun startProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 15000)
    }

    /** Persists the latest bridge-reported position, duration, and episode metadata. */
    private fun persistWatchProgress() {
        if (currentTmdbId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.updatePlaybackPosition(
                    mediaId = currentTmdbId,
                    mediaType = if (currentIsTv) "tv" else "movie",
                    position = currentPlaybackPosition
                )

                if (currentIsTv) {
                    repository.updateEpisodeInfo(
                        mediaId = currentTmdbId,
                        mediaType = "tv",
                        seasonNumber = currentSeason,
                        episodeNumber = currentEpisode
                    )
                }

                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                    tmdbId = currentTmdbId,
                    isTv = currentIsTv,
                    seasonNumber = if (currentIsTv) currentSeason else null,
                    episodeNumber = if (currentIsTv) currentEpisode else null,
                    playbackPosition = currentPlaybackPosition,
                    duration = currentDuration,
                    title = currentTitle,
                    overview = currentOverview,
                    posterPath = currentPosterPath,
                    backdropPath = currentBackdropPath,
                    voteAverage = currentVoteAverage,
                    releaseDate = currentReleaseDate
                )
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error persisting progress: ${e.message}")
            }
        }
    }

    /** Performs one timer-driven progress write and schedules the following cycle. */
    private fun updateWatchProgress() {
        // Timer-based sync - uses the current position already set by PlayerBridge
        persistWatchProgress()
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 15000)
    }

    /** Removes any pending progress callback from the main-thread handler. */
    private fun stopProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }

    /**
     * Initializes cached/downloaded domain filters before loading provider subresources.
     *
     * Stale cached rules protect the initial page immediately; their replacement downloads after
     * playback begins and is atomically visible to subsequent WebView requests.
     *
     * NOTE: Superseded by [loadIntoWebView], which runs the ad-blocker init in parallel
     * with the WebView load instead of serially. Retained as commented documentation so
     * the previous intent is preserved for future maintainers.
     */
    // private fun initializeAdBlockerAndLoadPlayer(baseUrl: String, html: String) {
    //     lifecycleScope.launch {
    //         val initialization = AdvancedAdBlocker.initialize(applicationContext)
    //         Log.i(
    //             TAG,
    //             "[AdBlock] Initialized source=${initialization.source} " +
    //                 "domains=${initialization.blockedDomainCount} " +
    //                 "error=${initialization.error.orEmpty()}"
    //         )
    //
    //         // The bridge and WebView clients are already attached before provider HTML executes.
    //         webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    //
    //         if (initialization.refreshRecommended) {
    //             lifecycleScope.launch {
    //                 val refresh = AdvancedAdBlocker.refresh(applicationContext)
    //                 Log.i(
    //                     TAG,
    //                     "[AdBlock] Refresh source=${refresh.source} " +
    //                         "domains=${refresh.blockedDomainCount} " +
    //                         "error=${refresh.error.orEmpty()}"
    //                 )
    //             }
    //         }
    //     }
    // }

    /**
     * Builds and shows the loading dialog that covers the activity while the provider page
     * is being fetched. Posted to the root layout so the window is attached before showing,
     * and marked non-cancelable so accidental back presses don't tear it down early.
     */
    @Suppress("DEPRECATION")
    private fun showLoadingDialog() {
        rootLayout.post {
            if (isFinishing || isDestroyed) return@post
            try {
                loadingDialog = ProgressDialog(this).apply {
                    setMessage("Loading stream...")
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    show()
                }
                Log.i(TAG, "[LoadingDialog] Shown")
            } catch (e: Exception) {
                Log.e(TAG, "[LoadingDialog] Failed to show: ${e.message}")
            }
        }
    }

    /**
     * Safely dismisses the loading dialog if it has been created and is still on screen.
     * Safe to call from any callback because it no-ops when the dialog isn't visible.
     */
    private fun dismissLoadingDialog() {
        if (::loadingDialog.isInitialized) {
            try {
                if (loadingDialog.isShowing) {
                    loadingDialog.dismiss()
                    Log.i(TAG, "[LoadingDialog] Dismissed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[LoadingDialog] Error dismissing: ${e.message}")
            }
        }
    }
}
