package com.kiduyuk.klausk.kiduyutv.lite

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.lite.databinding.ActivityPlayerBinding
import com.kiduyuk.klausk.kiduyutv.lite.playback.LiteStreamProviders
import com.kiduyuk.klausk.kiduyutv.lite.playback.PlayerAdBlocker
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * TV-first WebView player for the provider URL selected on the detail screen.
 *
 * Responsibilities are intentionally kept inside this activity:
 * - validate every top-level URL against [LiteStreamProviders];
 * - initialize the player-only [PlayerAdBlocker] before the first page load;
 * - apply hardened WebView settings and reject TLS failures;
 * - bridge remote-control actions to page video elements through injected JavaScript;
 * - manage provider fullscreen views and release all WebView resources on destruction.
 *
 * The activity receives a fully constructed HTTPS URL through [EXTRA_URL]. It never constructs
 * provider URLs itself and never falls back to an untrusted host.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playerAdBlocker: PlayerAdBlocker

    // Handler callbacks only control short-lived native overlays and the held-key skip ramp.
    private val uiHandler = Handler(Looper.getMainLooper())

    // WebView interception happens off the main thread, so the diagnostic count is atomic.
    private val blockedRequestCount = AtomicInteger(0)

    // Each overlay owns a separate callback so repeated actions reset only the relevant timer.
    private val hideBackRunnable = Runnable { binding.btnPlayerBack.visibility = View.GONE }
    private val hideSkipBackRunnable = Runnable { binding.skipBackOverlay.visibility = View.GONE }
    private val hideSkipForwardRunnable = Runnable { binding.skipFwdOverlay.visibility = View.GONE }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var skipDirection = 0
    private var skipHoldStart = 0L
    private var lastLoggedProgressBucket = -1

    /** Repeats seek operations while left/right remains held and ramps from 10 to 60 seconds. */
    private val skipTickRunnable = object : Runnable {
        /** Applies one ramped seek step, then schedules the next step while the key is held. */
        override fun run() {
            if (skipDirection == 0) return
            val heldMs = System.currentTimeMillis() - skipHoldStart
            val progress = (heldMs.toFloat() / SKIP_RAMP_DURATION_MS).coerceIn(0f, 1f)
            val seconds = (SKIP_SEC_MIN + (SKIP_SEC_MAX - SKIP_SEC_MIN) * progress).toInt()
            fireSkip(skipDirection * seconds)
            uiHandler.postDelayed(this, SKIP_REPEAT_MS)
        }
    }

    /**
     * Validates the requested provider URL, configures the player, initializes ad blocking, and
     * starts the first page load. A filter failure is reported in Logcat but does not stop playback.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Player activity created")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reject the activity request before creating a WebView session for an untrusted URL.
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank() || !isAllowedPlaybackUri(Uri.parse(url))) {
            Log.i(TAG, "Playback rejected: missing URL or host is not allowlisted")
            Toast.makeText(this, R.string.playback_link_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.i(TAG, "Starting playback host=${Uri.parse(url).host.orEmpty()}")
        Log.i(TAG, "Starting playback host=${Uri.parse(url)}")
        playerAdBlocker = PlayerAdBlocker(cacheDir)
        setupWebView()
        binding.btnPlayerBack.setOnClickListener { handleBack() }

        // A cached list returns quickly. On first use, playback waits for the bounded download so
        // the provider's initial subresource requests can be filtered as well.
        lifecycleScope.launch {
            val initialization = playerAdBlocker.initialize()
            Log.i(
                TAG,
                "AdBlock initialized source=${initialization.source} " +
                    "domains=${initialization.blockedDomainCount} " +
                    "error=${initialization.error.orEmpty()}"
            )
            binding.playerWebView.loadUrl(url)

            if (initialization.refreshRecommended) {
                // Stale cached rules already protect the active page while refresh runs.
                lifecycleScope.launch {
                    val refresh = playerAdBlocker.refresh()
                    Log.i(
                        TAG,
                        "AdBlock refresh source=${refresh.source} " +
                            "domains=${refresh.blockedDomainCount} " +
                            "error=${refresh.error.orEmpty()}"
                    )
                }
            }
        }
    }

    /** Resumes WebView media/timers and restores immersive TV presentation. */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "Player resumed")
        if (::binding.isInitialized) {
            binding.playerWebView.onResume()
            binding.playerWebView.resumeTimers()
        }
        makeFullscreen()
    }

    /** Pauses WebView media and timers while the activity is not visible. */
    override fun onPause() {
        Log.i(TAG, "Player paused")
        if (::binding.isInitialized) {
            binding.playerWebView.onPause()
            binding.playerWebView.pauseTimers()
        }
        super.onPause()
    }

    /** Cancels native callbacks, detaches the WebView, and releases its renderer resources. */
    override fun onDestroy() {
        Log.i(
            TAG,
            "Destroying player and clearing callbacks; " +
                "blockedRequests=${blockedRequestCount.get()}"
        )
        stopSkipRamp()
        uiHandler.removeCallbacksAndMessages(null)

        if (::binding.isInitialized) {
            // Detach the WebView before destroy() to prevent it retaining the activity hierarchy.
            binding.playerWebView.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                webChromeClient = null
                (parent as? ViewGroup)?.removeView(this)
                removeAllViews()
                destroy()
            }
        }
        Log.i(TAG, "Player cleanup complete")
        super.onDestroy()
    }

    /** Reapplies immersive flags after lifecycle and provider fullscreen transitions. */
    private fun makeFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    /** Delegates HTTPS host validation to the same registry used to construct playback URLs. */
    private fun isAllowedPlaybackUri(uri: Uri): Boolean {
        return LiteStreamProviders.isAllowedPlaybackUri(uri)
    }

    /** Configures the WebView, navigation policy, diagnostics, and fullscreen callbacks. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        Log.i(TAG, "Configuring hardened WebView")
        val webView = binding.playerWebView
        webView.settings.apply {
            // JavaScript and DOM storage are required by the external player applications.
            javaScriptEnabled = true
            domStorageEnabled = true

            // Provider pages must remain HTTPS-only and cannot read local app files/content URIs.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            userAgentString = WebSettings.getDefaultUserAgent(this@PlayerActivity) +
                    " KiduyuTVLite/${BuildConfig.VERSION_NAME}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // First-party provider state is supported without exposing third-party cookies.
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            /**
             * Runs on a WebView worker thread. Main-frame and provider-owned traffic are exempt;
             * only third-party subresources are evaluated against the downloaded domain set.
             */
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (
                    request.isForMainFrame ||
                    LiteStreamProviders.isAllowedPlaybackUri(request.url) ||
                    !::playerAdBlocker.isInitialized ||
                    !playerAdBlocker.shouldBlock(request.url)
                ) {
                    return null
                }

                val blockedCount = blockedRequestCount.incrementAndGet()
                Log.i(
                    TAG,
                    "AdBlock blocked request count=$blockedCount " +
                        "host=${request.url.host.orEmpty()}"
                )
                return blockedWebResourceResponse()
            }

            /** Blocks unexpected top-level hosts while allowing normal subresource loading. */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // Subresource policy belongs to shouldInterceptRequest; this guards navigation.
                if (!request.isForMainFrame) return false
                val allowed = isAllowedPlaybackUri(request.url)
                Log.i(
                    TAG,
                    "Main-frame navigation host=${request.url.host.orEmpty()} allowed=$allowed"
                )
                return !allowed
            }

            /** Injects TV/media helpers after the provider document finishes loading. */
            override fun onPageFinished(view: WebView, url: String) {
                Log.i(TAG, "Provider page finished host=${Uri.parse(url).host.orEmpty()}")
                // Injection is guarded in JavaScript so duplicate completion callbacks are safe.
                injectTvJavascript(view)
            }

            /** Shows the local error surface for main-frame network failures only. */
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.i(
                        TAG,
                        "Main-frame WebView error code=${error.errorCode} " +
                                "host=${request.url.host.orEmpty()} description=${error.description}"
                    )
                    showErrorPage(view, error.description.toString())
                }
            }

            /** Records main-frame HTTP status failures without replacing provider error pages. */
            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                // Log main-frame HTTP failures but leave provider-rendered error handling intact.
                if (request.isForMainFrame) {
                    Log.i(
                        TAG,
                        "Main-frame HTTP error status=${errorResponse.statusCode} " +
                                "host=${request.url.host.orEmpty()} " +
                                "reason=${errorResponse.reasonPhrase.orEmpty()}"
                    )
                }
            }

            /** Rejects certificate failures and replaces the page with a local secure-error view. */
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                Log.i(
                    TAG,
                    "TLS error rejected primaryError=${error.primaryError} " +
                            "host=${Uri.parse(error.url.orEmpty()).host.orEmpty()}"
                )
                // Never bypass invalid certificates for a third-party playback page.
                handler.cancel()
                showErrorPage(view, getString(R.string.secure_connection_error))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            /** Emits page-load diagnostics in 25 percent buckets to limit Logcat noise. */
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // Bucket progress to avoid one Logcat entry for every percentage point.
                val bucket = (newProgress.coerceIn(0, 100) / 25) * 25
                if (bucket != lastLoggedProgressBucket) {
                    lastLoggedProgressBucket = bucket
                    Log.i(TAG, "Provider page progress=$bucket%")
                }
            }

            /** Logs sanitized provider warnings/errors and suppresses duplicate default logging. */
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // Provider warnings/errors are useful, but URLs and token-like values are removed.
                if (
                    consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING ||
                    consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                ) {
                    Log.i(
                        TAG,
                        "Provider console level=${consoleMessage.messageLevel()} " +
                                "line=${consoleMessage.lineNumber()} " +
                                "message=${sanitizeConsoleMessage(consoleMessage.message())}"
                    )
                }
                return true
            }

            /** Moves the provider's fullscreen video surface into the native overlay container. */
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    Log.i(TAG, "Ignoring duplicate fullscreen custom view")
                    callback.onCustomViewHidden()
                    return
                }
                Log.i(TAG, "Entering provider fullscreen view")
                // WebChromeClient supplies the provider's fullscreen video surface.
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.playerWebView.visibility = View.GONE
                makeFullscreen()
            }

            /** Restores the normal WebView when the provider exits fullscreen. */
            override fun onHideCustomView() {
                Log.i(TAG, "Provider requested fullscreen exit")
                exitCustomView()
            }

            /** Explicitly denies geolocation requests because playback does not require location. */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                Log.i(TAG, "Geolocation permission denied for provider host=${Uri.parse(origin).host.orEmpty()}")
                // Playback has no legitimate need for device location.
                callback.invoke(origin, false, false)
            }
        }

        webView.requestFocus()
        Log.i(TAG, "WebView configuration complete")
    }

    /** Replaces a failed remote page with a small, local, HTML-escaped error surface. */
    private fun showErrorPage(view: WebView, message: String) {
        Log.i(TAG, "Showing local playback error page")
        view.loadDataWithBaseURL(
            null,
            errorHtml(message),
            "text/html",
            "UTF-8",
            null
        )
    }

    /** Empty 204 response returned for a matched ad-server subresource. */
    private fun blockedWebResourceResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        204,
        "No Content",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0))
    )

    /**
     * Adds TV focus navigation and lightweight media controls to the loaded provider page.
     *
     * The script can inspect a nested iframe only when browser same-origin rules permit access.
     * Cross-origin frames are caught and ignored; Android does not attempt to bypass that boundary.
     * Repeated calls are safe because the page-global injection flag exits after the first call.
     */
    private fun injectTvJavascript(view: WebView) {
        Log.i(TAG, "Injecting TV navigation, max-volume hook, and media controls")
        val javascript = """
            (function() {
                // onPageFinished can fire more than once for the same document.
                if (window.__kiduyuLiteInjected) return;
                window.__kiduyuLiteInjected = true;

                // Make the provider's currently focused control visible from TV distance.
                var style = document.createElement('style');
                style.textContent = ':focus{outline:3px solid #E50914!important;outline-offset:2px!important;}html{scroll-behavior:smooth;}';
                if (document.head) document.head.appendChild(style);

                // Unmute an accessible media element and request its maximum page-level volume.
                function setMaximumVolume(video) {
                    if (!video) return;
                    try {
                        video.defaultMuted = false;
                        video.muted = false;
                        video.volume = 1.0;
                    } catch (ignored) {}
                }

                // Attach the volume handler once per video element.
                function hookVideoVolume(video) {
                    if (!video || video.__kiduyuLiteVolumeHooked) return;
                    video.__kiduyuLiteVolumeHooked = true;
                    // `playing` is the real playback signal; readiness events can fire while paused.
                    video.addEventListener('playing', function() {
                        setMaximumVolume(video);
                    }, true);
                    if (!video.paused) setMaximumVolume(video);
                }

                // Discover videos below a document or newly added DOM node.
                function scanVideoElements(root) {
                    if (!root || typeof root.querySelectorAll !== 'function') return;
                    if (root.tagName === 'VIDEO') hookVideoVolume(root);
                    root.querySelectorAll('video').forEach(hookVideoVolume);
                }

                // Hook existing videos and watch for players mounted later by client-side scripts.
                scanVideoElements(document);
                var volumeObserverTarget = document.documentElement || document.body;
                if (volumeObserverTarget && typeof MutationObserver !== 'undefined') {
                    new MutationObserver(function(mutations) {
                        mutations.forEach(function(mutation) {
                            mutation.addedNodes.forEach(scanVideoElements);
                        });
                    }).observe(volumeObserverTarget, {childList: true, subtree: true});
                }

                // Periodically discover videos inside same-origin provider frames.
                function scanFrameVideos() {
                    document.querySelectorAll('iframe').forEach(function(frame) {
                        try {
                            scanVideoElements(frame.contentDocument);
                        } catch (ignored) {
                            // Cross-origin iframe content is intentionally inaccessible.
                        }
                    });
                }
                scanFrameVideos();
                setInterval(scanFrameVideos, 1000);

                // Return the first directly accessible video used by native seek/play controls.
                function directVideo() {
                    var video = document.querySelector('video');
                    if (video) {
                        hookVideoVolume(video);
                        return video;
                    }
                    var frames = document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        try {
                            var nested = frames[i].contentDocument.querySelector('video');
                            if (nested) {
                                hookVideoVolume(nested);
                                return nested;
                            }
                        } catch (ignored) {
                            // Cross-origin providers still receive native key events normally.
                        }
                    }
                    return null;
                }

                // Native left/right handlers call this function through evaluateJavascript.
                window.__kiduyuLiteSkip = function(seconds) {
                    var video = directVideo();
                    if (!video) return false;
                    video.currentTime = Math.max(0, video.currentTime + seconds);
                    return true;
                };

                // Prefer the focused page control; otherwise toggle the directly accessible video.
                window.__kiduyuLiteCenter = function() {
                    var active = document.activeElement;
                    if (active && active !== document.body && typeof active.click === 'function') {
                        active.click();
                        return 'clicked';
                    }
                    var video = directVideo();
                    if (!video) return 'no-video';
                    if (video.paused) video.play(); else video.pause();
                    return 'toggled';
                };

                // Candidate set used by the simple vertical spatial-focus search below.
                var selector = 'a[href],button,input,select,[tabindex]:not([tabindex="-1"]),[role="button"]';
                // Establish a usable initial focus target after the provider UI settles.
                function focusFirst() {
                    var element = document.querySelector('[class*="play"],[class*="hero"] button') || document.querySelector(selector);
                    if (element) {
                        element.focus();
                        element.scrollIntoView({behavior:'smooth', block:'center'});
                    }
                }

                // Move focus to the nearest visible control in the requested vertical direction.
                function navigate(direction) {
                    var focused = document.activeElement;
                    if (!focused || focused === document.body) {
                        focusFirst();
                        return;
                    }
                    var rect = focused.getBoundingClientRect();
                    var fromX = rect.left + rect.width / 2;
                    var fromY = rect.top + rect.height / 2;
                    var best = null;
                    var bestDistance = Infinity;

                    // Choose the nearest visible candidate above or below the focused element.
                    document.querySelectorAll(selector).forEach(function(element) {
                        if (element === focused) return;
                        var candidate = element.getBoundingClientRect();
                        if (!candidate.width || !candidate.height) return;
                        var x = candidate.left + candidate.width / 2;
                        var y = candidate.top + candidate.height / 2;
                        var valid = (direction === 'up' && y < fromY - 5) ||
                            (direction === 'down' && y > fromY + 5);
                        if (!valid) return;
                        var distance = Math.hypot(x - fromX, y - fromY);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = element;
                        }
                    });

                    if (best) {
                        best.focus();
                        best.scrollIntoView({behavior:'smooth', block:'nearest'});
                    }
                }

                // Horizontal keys remain native seek controls; only up/down is handled in-page.
                document.addEventListener('keydown', function(event) {
                    if (event.keyCode === 38) {
                        navigate('up');
                        event.preventDefault();
                    } else if (event.keyCode === 40) {
                        navigate('down');
                        event.preventDefault();
                    }
                }, true);

                setTimeout(focusFirst, 600);
            })();
        """.trimIndent()
        view.evaluateJavascript(javascript, null)
    }

    /** Sends Enter/DPAD-center to the focused page control or directly accessible video. */
    private fun handleCenterKey() {
        binding.playerWebView.evaluateJavascript(
            "window.__kiduyuLiteCenter && window.__kiduyuLiteCenter();"
        ) { result ->
            Log.i(TAG, "Center-key JavaScript result=$result")
        }
    }

    /** Performs one JavaScript seek and briefly displays the matching native overlay. */
    private fun fireSkip(seconds: Int) {
        binding.playerWebView.evaluateJavascript(
            "window.__kiduyuLiteSkip && window.__kiduyuLiteSkip($seconds);"
        ) { result ->
            if (result == "false" || result == "null") {
                Log.i(TAG, "Skip unavailable seconds=$seconds result=$result")
            }
        }
        if (seconds < 0) {
            uiHandler.removeCallbacks(hideSkipBackRunnable)
            binding.skipBackSeconds.text = getString(R.string.skip_seconds, -seconds)
            binding.skipBackOverlay.visibility = View.VISIBLE
            uiHandler.postDelayed(hideSkipBackRunnable, 600)
        } else {
            uiHandler.removeCallbacks(hideSkipForwardRunnable)
            binding.skipForwardSeconds.text = getString(R.string.skip_seconds, seconds)
            binding.skipFwdOverlay.visibility = View.VISIBLE
            uiHandler.postDelayed(hideSkipForwardRunnable, 600)
        }
    }

    /** Starts repeated seeking; [direction] is -1 for rewind and +1 for fast-forward. */
    private fun startSkipRamp(direction: Int) {
        if (skipDirection == direction) return
        stopSkipRamp()
        Log.i(TAG, "Skip ramp started direction=${if (direction < 0) "back" else "forward"}")
        skipDirection = direction
        skipHoldStart = System.currentTimeMillis()
        fireSkip(direction * SKIP_SEC_MIN)
        uiHandler.postDelayed(skipTickRunnable, SKIP_REPEAT_MS)
    }

    /** Stops held-key seeking and removes the scheduled repeat callback. */
    private fun stopSkipRamp() {
        if (skipDirection != 0) {
            Log.i(TAG, "Skip ramp stopped")
        }
        skipDirection = 0
        uiHandler.removeCallbacks(skipTickRunnable)
    }

    /**
     * Reserves left/right for seeking and center for provider activation/play-pause.
     * Key-up must be consumed for left/right so a released remote button always stops the ramp.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        showBackButton()
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleBack()
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) handleCenterKey()
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                return when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        startSkipRamp(-1)
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        stopSkipRamp()
                        true
                    }
                    else -> super.dispatchKeyEvent(event)
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                return when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        startSkipRamp(1)
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        stopSkipRamp()
                        true
                    }
                    else -> super.dispatchKeyEvent(event)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Shows the native back affordance and restarts its three-second hide timer. */
    private fun showBackButton() {
        uiHandler.removeCallbacks(hideBackRunnable)
        binding.btnPlayerBack.visibility = View.VISIBLE
        uiHandler.postDelayed(hideBackRunnable, 3_000)
    }

    /** Handles back in visual-stack order: fullscreen, WebView history, then activity. */
    private fun handleBack() {
        when {
            customView != null -> {
                Log.i(TAG, "Back action: exit fullscreen")
                exitCustomView()
            }
            binding.playerWebView.canGoBack() -> {
                Log.i(TAG, "Back action: WebView history")
                binding.playerWebView.goBack()
            }
            else -> {
                Log.i(TAG, "Back action: finish player")
                finish()
            }
        }
    }

    /** Restores the WebView after the provider's fullscreen surface is dismissed. */
    private fun exitCustomView() {
        Log.i(TAG, "Exiting provider fullscreen view")
        binding.fullscreenContainer.removeAllViews()
        binding.fullscreenContainer.visibility = View.GONE
        binding.playerWebView.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        makeFullscreen()
    }

    /** Builds the local error document; all dynamic text is escaped before insertion. */
    private fun errorHtml(message: String): String {
        val safeMessage = TextUtils.htmlEncode(message)
        val safeTitle = TextUtils.htmlEncode(getString(R.string.playback_error_title))
        return """
            <!doctype html>
            <html>
            <body style="background:#141414;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center">
                <div style="font-size:48px;margin-bottom:16px">&#9888;</div>
                <h2 style="color:#E50914;margin:0 0 12px">$safeTitle</h2>
                <p style="color:#B3B3B3;max-width:480px">$safeMessage</p>
            </body>
            </html>
        """.trimIndent()
    }

    /** Redacts likely URLs/tokens and bounds provider-controlled Logcat output. */
    private fun sanitizeConsoleMessage(message: String): String = message
        .replace(URL_PATTERN, "<url>")
        .replace(LONG_TOKEN_PATTERN, "<redacted>")
        .take(MAX_CONSOLE_MESSAGE_LENGTH)

    companion object {
        // Keep a stable tag so TV-device logs can be filtered with `adb logcat -s`.
        private const val TAG = "KiduyuLitePlayer"
        private const val MAX_CONSOLE_MESSAGE_LENGTH = 240
        private val URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        private val LONG_TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{40,}")
        const val EXTRA_URL = "playback_url"

        private const val SKIP_RAMP_DURATION_MS = 5_000L
        private const val SKIP_REPEAT_MS = 600L
        private const val SKIP_SEC_MIN = 10
        private const val SKIP_SEC_MAX = 60
    }
}
