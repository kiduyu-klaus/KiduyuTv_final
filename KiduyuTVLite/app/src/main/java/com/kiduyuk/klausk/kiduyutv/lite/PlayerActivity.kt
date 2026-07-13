package com.kiduyuk.klausk.kiduyutv.lite

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kiduyuk.klausk.kiduyutv.lite.databinding.ActivityPlayerBinding
import com.kiduyuk.klausk.kiduyutv.lite.playback.LiteStreamProviders

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val uiHandler = Handler(Looper.getMainLooper())

    private val hideBackRunnable = Runnable { binding.btnPlayerBack.visibility = View.GONE }
    private val hideSkipBackRunnable = Runnable { binding.skipBackOverlay.visibility = View.GONE }
    private val hideSkipForwardRunnable = Runnable { binding.skipFwdOverlay.visibility = View.GONE }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var skipDirection = 0
    private var skipHoldStart = 0L

    private val skipTickRunnable = object : Runnable {
        override fun run() {
            if (skipDirection == 0) return
            val heldMs = System.currentTimeMillis() - skipHoldStart
            val progress = (heldMs.toFloat() / SKIP_RAMP_DURATION_MS).coerceIn(0f, 1f)
            val seconds = (SKIP_SEC_MIN + (SKIP_SEC_MAX - SKIP_SEC_MIN) * progress).toInt()
            fireSkip(skipDirection * seconds)
            uiHandler.postDelayed(this, SKIP_REPEAT_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank() || !isAllowedPlaybackUri(Uri.parse(url))) {
            Toast.makeText(this, R.string.playback_link_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupWebView()
        binding.btnPlayerBack.setOnClickListener { handleBack() }
        binding.playerWebView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            binding.playerWebView.onResume()
            binding.playerWebView.resumeTimers()
        }
        makeFullscreen()
    }

    override fun onPause() {
        if (::binding.isInitialized) {
            binding.playerWebView.onPause()
            binding.playerWebView.pauseTimers()
        }
        super.onPause()
    }

    override fun onDestroy() {
        stopSkipRamp()
        uiHandler.removeCallbacksAndMessages(null)

        if (::binding.isInitialized) {
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
        super.onDestroy()
    }

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

    private fun isAllowedPlaybackUri(uri: Uri): Boolean {
        return LiteStreamProviders.isAllowedPlaybackUri(uri)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.playerWebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) return false
                return !isAllowedPlaybackUri(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectTvJavascript(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showErrorPage(view, error.description.toString())
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                showErrorPage(view, getString(R.string.secure_connection_error))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
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

            override fun onHideCustomView() {
                exitCustomView()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, false, false)
            }
        }

        webView.requestFocus()
    }

    private fun showErrorPage(view: WebView, message: String) {
        view.loadDataWithBaseURL(
            null,
            errorHtml(message),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun injectTvJavascript(view: WebView) {
        val javascript = """
            (function() {
                if (window.__kiduyuLiteInjected) return;
                window.__kiduyuLiteInjected = true;

                var style = document.createElement('style');
                style.textContent = ':focus{outline:3px solid #E50914!important;outline-offset:2px!important;}html{scroll-behavior:smooth;}';
                if (document.head) document.head.appendChild(style);

                function directVideo() {
                    var video = document.querySelector('video');
                    if (video) return video;
                    var frames = document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        try {
                            var nested = frames[i].contentDocument.querySelector('video');
                            if (nested) return nested;
                        } catch (ignored) {}
                    }
                    return null;
                }

                window.__kiduyuLiteSkip = function(seconds) {
                    var video = directVideo();
                    if (!video) return false;
                    video.currentTime = Math.max(0, video.currentTime + seconds);
                    return true;
                };

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

                var selector = 'a[href],button,input,select,[tabindex]:not([tabindex="-1"]),[role="button"]';
                function focusFirst() {
                    var element = document.querySelector('[class*="play"],[class*="hero"] button') || document.querySelector(selector);
                    if (element) {
                        element.focus();
                        element.scrollIntoView({behavior:'smooth', block:'center'});
                    }
                }

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

    private fun handleCenterKey() {
        binding.playerWebView.evaluateJavascript(
            "window.__kiduyuLiteCenter && window.__kiduyuLiteCenter();",
            null
        )
    }

    private fun fireSkip(seconds: Int) {
        binding.playerWebView.evaluateJavascript(
            "window.__kiduyuLiteSkip && window.__kiduyuLiteSkip($seconds);",
            null
        )
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

    private fun startSkipRamp(direction: Int) {
        if (skipDirection == direction) return
        stopSkipRamp()
        skipDirection = direction
        skipHoldStart = System.currentTimeMillis()
        fireSkip(direction * SKIP_SEC_MIN)
        uiHandler.postDelayed(skipTickRunnable, SKIP_REPEAT_MS)
    }

    private fun stopSkipRamp() {
        skipDirection = 0
        uiHandler.removeCallbacks(skipTickRunnable)
    }

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

    private fun showBackButton() {
        uiHandler.removeCallbacks(hideBackRunnable)
        binding.btnPlayerBack.visibility = View.VISIBLE
        uiHandler.postDelayed(hideBackRunnable, 3_000)
    }

    private fun handleBack() {
        when {
            customView != null -> exitCustomView()
            binding.playerWebView.canGoBack() -> binding.playerWebView.goBack()
            else -> finish()
        }
    }

    private fun exitCustomView() {
        binding.fullscreenContainer.removeAllViews()
        binding.fullscreenContainer.visibility = View.GONE
        binding.playerWebView.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        makeFullscreen()
    }

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

    companion object {
        const val EXTRA_URL = "playback_url"

        private const val SKIP_RAMP_DURATION_MS = 5_000L
        private const val SKIP_REPEAT_MS = 600L
        private const val SKIP_SEC_MIN = 10
        private const val SKIP_SEC_MAX = 60
    }
}
