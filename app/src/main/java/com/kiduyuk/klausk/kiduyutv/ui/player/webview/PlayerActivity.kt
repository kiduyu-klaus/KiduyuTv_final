package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import java.io.ByteArrayInputStream

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var isManualFullscreen = false

    companion object {
        private const val TAG = "PlayerActivity"

        const val EXTRA_TMDB_ID          = "TMDB_ID"
        const val EXTRA_IS_TV            = "IS_TV"
        const val EXTRA_SEASON_NUMBER    = "SEASON_NUMBER"
        const val EXTRA_EPISODE_NUMBER   = "EPISODE_NUMBER"
        const val EXTRA_STREAM_URL       = "STREAM_URL"
        const val EXTRA_IFRAME_HTML      = "IFRAME_HTML"
        const val EXTRA_TITLE            = "TITLE"
        const val EXTRA_OVERVIEW         = "OVERVIEW"
        const val EXTRA_POSTER_PATH      = "POSTER_PATH"
        const val EXTRA_BACKDROP_PATH    = "BACKDROP_PATH"
        const val EXTRA_VOTE_AVERAGE     = "VOTE_AVERAGE"
        const val EXTRA_RELEASE_DATE     = "RELEASE_DATE"
    }

    private var tmdbId              = -1
    private var isTv                = false
    private var currentSeason       = 1
    private var currentEpisode      = 1
    private var streamUrl           = ""
    private var iframeHtml: String?  = null
    private var contentTitle        = "Unknown"
    private var contentOverview: String?     = null
    private var contentPosterPath: String?   = null
    private var contentBackdropPath: String? = null
    private var contentVoteAverage  = 0.0
    private var contentReleaseDate: String?  = null

    private val isStartFullscreen = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readIntentExtras()

        if (tmdbId == -1) { finish(); return }

        buildLayout()
        hideSystemUi()
        configureWebView()

        // Enter fullscreen after brief delay to ensure WebView is ready
        webView.postDelayed({
            if (isStartFullscreen && !isManualFullscreen) {
                enterFullscreen()
            }
        }, 500)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenView != null || isManualFullscreen) exitFullscreen() else showExitDialog()
            }
        })
    }

    private fun readIntentExtras() {
        tmdbId              = intent.getIntExtra(EXTRA_TMDB_ID, -1)
        isTv                = intent.getBooleanExtra(EXTRA_IS_TV, false)
        currentSeason       = intent.getIntExtra(EXTRA_SEASON_NUMBER, 1)
        currentEpisode      = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)
        iframeHtml          = intent.getStringExtra(EXTRA_IFRAME_HTML)
        contentTitle        = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
        contentOverview     = intent.getStringExtra(EXTRA_OVERVIEW)
        contentPosterPath   = intent.getStringExtra(EXTRA_POSTER_PATH)
        contentBackdropPath = intent.getStringExtra(EXTRA_BACKDROP_PATH)
        contentVoteAverage  = intent.getDoubleExtra(EXTRA_VOTE_AVERAGE, 0.0)
        contentReleaseDate  = intent.getStringExtra(EXTRA_RELEASE_DATE)

        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: if (isTv)
            "https://vidlink.pro/tv/$tmdbId/$currentSeason/$currentEpisode?autoplay=true"
        else
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"

        Log.i(TAG, "[Intent] tmdbId=$tmdbId isTv=$isTv season=$currentSeason episode=$currentEpisode")
        Log.i(TAG, "[Intent] title=$contentTitle voteAverage=$contentVoteAverage releaseDate=$contentReleaseDate")
        Log.i(TAG, "[Intent] streamUrl=$streamUrl iframeHtml=${iframeHtml != null}")
    }

    private fun buildLayout() {
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            visibility = View.VISIBLE
            keepScreenOn = true
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled                     = true
            domStorageEnabled                     = true
            mediaPlaybackRequiresUserGesture      = false
            mixedContentMode                      = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort                       = true
            loadWithOverviewMode                  = true
            builtInZoomControls                   = false
            displayZoomControls                   = false
            setSupportZoom(false)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled   = false
        webView.overScrollMode               = View.OVER_SCROLL_NEVER

        // Create WebViewClient with both ad blocking AND page finished callback
        webView.webViewClient = object : WebViewClient() {
            
            private val adDomains = setOf(
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "adnxs.com", "advertising.com", "adsystem.com", "adserver.com",
                "rubiconproject.com", "openx.net", "pubmatic.com", "criteo.com",
                "moatads.com", "taboola.com", "outbrain.com", "adroll.com",
                "popads.net", "popcash.net", "propellerads.com", "ad-maven.com",
                "onclickads.net", "adsterra.com", "exoclick.com",
                "trafficjunky.net", "mc.yandex.ru", "creativecdn.com",
                "serving-sys.com", "contextweb.com", "bet365.com", "1xbet.com"
            )

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "[WebView] page finished - triggering video fullscreen JS")
                
                view?.postDelayed({
                    view?.evaluateJavascript(
                        "document.querySelector('video')?.requestFullscreen();", 
                        null
                    )
                }, 1000)
            }

            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase() ?: return null
                if (adDomains.any { url.contains(it) }) {
                    return WebResourceResponse(
                        "text/plain", "utf-8", ByteArrayInputStream("".toByteArray())
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "[WebView] main frame error: ${error?.description}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                // Clear manual fullscreen state when website requests real fullscreen
                if (isManualFullscreen) {
                    Log.i(TAG, "[Fullscreen] clearing manual fullscreen for website fullscreen")
                    isManualFullscreen = false
                    fullscreenView = null
                    fullscreenCallback = null
                }
                
                if (fullscreenView != null) {
                    // Don't exit, just hide the new request
                    callback?.onCustomViewHidden()
                    return
                }
                
                fullscreenView     = view
                fullscreenCallback = callback
                rootLayout.addView(
                    fullscreenView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenView?.bringToFront()
                hideSystemUi()
                Log.i(TAG, "[Fullscreen] entered (via onShowCustomView) with view: ${view?.javaClass?.simpleName}")
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean,
                isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                Log.i(TAG, "[WebChrome] popup blocked")
                return false
            }
        }

        val html = iframeHtml
        val baseUrl = streamUrl.toBaseUrl()
        val forceDirectLoad = baseUrl.contains("vidsrc.wtf") ||
            baseUrl.contains("vaplayer.ru") ||
            baseUrl.contains("autoembed.co")

        if (html != null && !forceDirectLoad) {
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        } else {
            webView.loadUrl(streamUrl)
        }
    }

    // ── Fullscreen ────────────────────────────────────────────────────────────

    // Enable immersive fullscreen mode (no custom view)
    private fun enterFullscreen() {
        if (isManualFullscreen) return

        isManualFullscreen = true

        webView.bringToFront()
        webView.requestFocus()

        hideSystemUi()

        Log.i(TAG, "[Fullscreen] immersive mode enabled")
    }

    private fun exitFullscreen() {
        fullscreenView?.let {
            rootLayout.removeView(it)
        }

        fullscreenView = null
        fullscreenCallback = null
        isManualFullscreen = false

        webView.bringToFront()
        webView.requestFocus()
        showSystemUi()

        Log.i(TAG, "[Fullscreen] exited")
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN      or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    @Suppress("DEPRECATION")
    private fun showSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        webView.bringToFront()
        webView.requestFocus()
        
        // Re-enter fullscreen if we were in manual fullscreen mode
        if (isStartFullscreen && !isManualFullscreen && fullscreenView == null) {
            webView.postDelayed({ enterFullscreen() }, 300)
        }
        
        Log.i(TAG, "[Lifecycle] onResume - WebView brought to front")
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onDestroy() {
        exitFullscreen()
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient   = WebViewClient()
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "WebView cleanup error: ${e.message}")
        }
        super.onDestroy()
    }

    // ── Dialog ────────────────────────────────────────────────────────────────

    private fun showExitDialog() {
        QuitDialog(
            context            = this,
            title              = "Stop Playback?",
            message            = "Are you sure you want to stop playback and exit?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes      = R.raw.exit,
            onNo               = {},
            onYes              = { finish() }
        ).show()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun String.toBaseUrl(): String {
    val uri    = android.net.Uri.parse(this)
    val scheme = uri.scheme ?: "https"
    val host   = uri.host   ?: return this
    val port   = if (uri.port != -1) ":${uri.port}" else ""
    return "$scheme://$host$port"
}