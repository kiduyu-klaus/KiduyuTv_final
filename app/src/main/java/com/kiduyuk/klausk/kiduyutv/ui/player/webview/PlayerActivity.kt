package com.kiduyuk.klausk.kiduyutv.ui.player.webview
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.io.ByteArrayInputStream

class PlayerActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // A list of common ad-serving domains to block
    private val adDomains = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "moatads.com",
        "adnxs.com",
        "adroll.com",
        "openx.net",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "adsterra.com",
        "brightadnetwork.com",
        "onclickads.net",
        "ad-maven.com",
        "zeropark.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        val seasonNumber = intent.getIntExtra("SEASON_NUMBER", 1)
        val episodeNumber = intent.getIntExtra("EPISODE_NUMBER", 1)

        if (tmdbId == -1) {
            finish()
            return
        }
        // Keep screen on while watching
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val baseUrl = if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$seasonNumber/$episodeNumber"
        } else {
            "https://vidlink.pro/movie/$tmdbId"
        }
        val url = "$baseUrl?autoplay=true"

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

                // Block popups natively
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                allowContentAccess         = true
                allowFileAccess            = false            // not needed, reduce attack surface
                databaseEnabled            = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode           = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportMultipleWindows(true)               // required to intercept window.open()
                useWideViewPort            = true
                loadWithOverviewMode       = true
                builtInZoomControls        = false
                displayZoomControls        = false
                cacheMode                  = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {

                // Block ad requests based on domain
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val requestUrl = request?.url?.toString() ?: ""
                    for (domain in adDomains) {
                        if (requestUrl.contains(domain)) {
                            // Return an empty response for blocked domains
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // Inject JavaScript for:
                    // 1. Setting volume to max
                    // 2. Removing potential ad overlays or popunder scripts
                    // 3. Overriding window.open to prevent popups
                    // 4. Custom D-pad navigation helper
                    val js = """
                        (function() {
                            // 1. Volume Control
                            function setMaxVolume() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    videos[i].volume = 1.0;
                                    videos[i].muted = false;
                                }
                            }
                            
                            // 2. Popup Blocker Override
                            window.open = function() { return null; };
                            
                            // 3. Ad Element Removal
                            function removeAds() {
                                var selectors = [
                                    'iframe[src*="ads"]', 
                                    'div[class*="ads"]', 
                                    'div[id*="ads"]',
                                    'div[class*="popup"]',
                                    'div[id*="popup"]',
                                    '.ad-banner',
                                    '.ad-container'
                                ];
                                selectors.forEach(function(selector) {
                                    var elements = document.querySelectorAll(selector);
                                    elements.forEach(function(el) { el.remove(); });
                                });
                            }

                            // 4. Media Key Helper Functions
                            window.playerPlay = function() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    videos[i].play();
                                }
                            };
                            window.playerPause = function() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    videos[i].pause();
                                }
                            };
                            window.playerToggle = function() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    if (videos[i].paused) {
                                        videos[i].play();
                                    } else {
                                        videos[i].pause();
                                    }
                                }
                            };

                            setMaxVolume();
                            removeAds();
                            
                            // Continuous monitoring for dynamic ads
                            setInterval(function() {
                                setMaxVolume();
                                removeAds();
                            }, 3000);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Prevent any new windows from being created (additional popup layer)
                override fun onCreateWindow(
                    view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
                ): Boolean = false  // false = block the window
            }

            // Enable mouse support/touch events for navigation
            isFocusable = true
            isFocusableInTouchMode = true

            loadUrl(url)
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }



    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Player")
            .setMessage("Are you sure you want to stop watching and go back?")
            .setPositiveButton("Yes") { _, _ ->
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}