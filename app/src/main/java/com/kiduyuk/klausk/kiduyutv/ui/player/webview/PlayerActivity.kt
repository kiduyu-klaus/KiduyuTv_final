package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import java.io.ByteArrayInputStream

class PlayerActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AdvancedAdBlocker.init(this)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        val seasonNumber = intent.getIntExtra("SEASON_NUMBER", 1)
        val episodeNumber = intent.getIntExtra("EPISODE_NUMBER", 1)
        val title = intent.getStringExtra("TITLE") ?: "Unknown"

        if (tmdbId == -1) {
            finish()
            return
        }

        val repository = TmdbRepository()
        repository.saveToWatchHistory(
            this,
            WatchHistoryItem(
                id = tmdbId,
                title = title,
                overview = intent.getStringExtra("OVERVIEW"),
                posterPath = intent.getStringExtra("POSTER_PATH"),
                backdropPath = intent.getStringExtra("BACKDROP_PATH"),
                voteAverage = intent.getDoubleExtra("VOTE_AVERAGE", 0.0),
                releaseDate = intent.getStringExtra("RELEASE_DATE"),
                isTv = isTv,
                seasonNumber = if (isTv) seasonNumber else null,
                episodeNumber = if (isTv) episodeNumber else null
            )
        )

        val url = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$seasonNumber/$episodeNumber?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {

                    val url = request?.url.toString()

                    if (AdvancedAdBlocker.shouldBlock(url)) {
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    val url = request?.url.toString()

                    // Block redirect traps
                    if (!request!!.hasGesture()) return true

                    if (AdvancedAdBlocker.shouldBlock(url)) return true

                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    val advancedJs = """
                        (function() {

                            function removeAdsAdvanced() {
                                const elements = document.querySelectorAll('*');

                                elements.forEach(el => {
                                    const text = (el.innerText || '').toLowerCase();
                                    const cls = (el.className || '').toString().toLowerCase();

                                    if (
                                        text.includes('advert') ||
                                        text.includes('sponsored') ||
                                        cls.includes('ad') ||
                                        cls.includes('popup')
                                    ) {
                                        el.remove();
                                    }
                                });
                            }

                            function blockRedirects() {
                                window.open = () => null;
                                window.location.assign = () => {};
                                window.location.replace = () => {};
                            }

                            function setMaxVolume() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    videos[i].volume = 1.0;
                                    videos[i].muted = false;
                                }
                            }

                            blockRedirects();
                            removeAdsAdvanced();
                            setMaxVolume();

                            setInterval(function() {
                                
                                setMaxVolume();
                            }, 3000);

                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(AdvancedAdBlocker.getCss(), null)
                    view?.evaluateJavascript(advancedJs, null)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false
            }

            loadUrl(url)
        }

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@PlayerActivity)
                    .setTitle("Exit Player")
                    .setMessage("Stop watching?")
                    .setPositiveButton("Yes") { _, _ -> finish() }
                    .setNegativeButton("No", null)
                    .show()
            }
        })
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