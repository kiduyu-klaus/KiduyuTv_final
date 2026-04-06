package com.kiduyuk.klausk.kiduyutv.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * Utility class to sniff streaming URLs from an embed URL using a WebView.
 * Integrates AdvancedAdBlocker to filter out unwanted requests.
 */
object UrlSniffer {
    private const val TAG = "UrlSniffer"

    private val STREAM_EXTENSIONS = listOf(".m3u8", ".mpd", ".mp4", ".webm", ".mkv")
    private val STREAM_KEYWORDS = listOf("playlist", "manifest", "master", "/hls/", "/dash/")

    /**
     * Sniffs a streaming URL from the provided [embedUrl].
     *
     * @param context The application context.
     * @param embedUrl The URL of the embed player to sniff.
     * @param timeoutMs Maximum time to wait for a link in milliseconds.
     * @return The detected streaming URL, or null if none found or timed out.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(
        context: Context,
        embedUrl: String,
        timeoutMs: Long = 30000L
    ): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            var isFinished = false
            val handler = android.os.Handler(android.os.Looper.getMainLooper())

            // Ensure AdBlocker is initialized
            AdvancedAdBlocker.init(context)

            val cleanup = {
                if (!isFinished) {
                    isFinished = true
                    // WebView methods MUST be called on the main thread.
                    // shouldInterceptRequest runs on a background thread, so we post to main.
                    handler.post {
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (e: Exception) {
                            Log.i(TAG, "Error during WebView cleanup: ${e.message}")
                        }
                    }
                }
            }

            val timeoutRunnable = Runnable {
                if (!isFinished) {
                    Log.i(TAG, "Sniffing timed out for: $embedUrl")
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }

            handler.postDelayed(timeoutRunnable, timeoutMs)

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null

                    // 1. Check AdBlocker first
                    if (AdvancedAdBlocker.shouldBlock(url)) {
                        Log.i(TAG, "Blocked by AdBlocker: $url")
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                    }

                    // 2. Check if it's a stream URL
                    if (isStreamUrl(url)) {
                        Log.i(TAG, "Stream URL detected: $url")
                        handler.removeCallbacks(timeoutRunnable)
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(url)
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // Inject AdBlocker CSS to hide elements
                    val adBlockCss = AdvancedAdBlocker.getCss()
                    if (adBlockCss.isNotEmpty()) {
                        view?.evaluateJavascript(adBlockCss, null)
                    }

                    // Some players require a click or JS execution to start loading the stream
                    view?.evaluateJavascript(
                        "(function() { " +
                                "var v = document.querySelector('video'); if(v) v.play(); " +
                                "var b = document.querySelector('button'); if(b) b.click(); " +
                                "})();",
                        null
                    )
                }
            }

            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                cleanup()
            }

            Log.i(TAG, "Starting sniffing for: $embedUrl")
            webView.loadUrl(embedUrl)
        }
    }

    private fun isStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return STREAM_EXTENSIONS.any { lower.contains(it) } //||
                //STREAM_KEYWORDS.any { lower.contains(it) }
    }
}
