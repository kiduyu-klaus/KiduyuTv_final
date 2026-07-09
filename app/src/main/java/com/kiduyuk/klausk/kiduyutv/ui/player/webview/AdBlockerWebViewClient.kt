package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * AdBlockerWebViewClient - Handles ad blocking and page lifecycle events.
 *
 * Uses a **whitelist** strategy: only requests whose host matches one of the
 * configured [allowedHosts] (or is a subdomain thereof) are passed through.
 * Every other request is intercepted and returned as an empty response, so
 * the iframe is fully isolated from third-party trackers, ad networks, and
 * analytics endpoints that aren't part of the provider's own infrastructure.
 */
open class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit,
    private val allowedHosts: Set<String> = emptySet()
) : WebViewClient() {

    companion object {
        private const val TAG = "AdBlockerWebView"
    }

    /**
     * Returns true if [host] is exactly one of [allowedHosts] or is a subdomain
     * of one (e.g. "cdn.vidlink.pro" is allowed when "vidlink.pro" is allowed).
     */
    private fun isHostAllowed(host: String): Boolean {
        if (allowedHosts.isEmpty()) return false
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val host = request?.url?.host?.lowercase() ?: return null

        // FIX: Whitelist — block every request that isn't from the provider URL
        // (or one of its subdomains). This is far stricter than a domain blacklist
        // and stops new ad/tracker domains the moment they appear.
        if (!isHostAllowed(host)) {
            Log.d(TAG, "Blocked non-provider request: $host (allowed=$allowedHosts)")
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()

        view?.evaluateJavascript(
            """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay { display: none !important; }';
                document.head.appendChild(style);

                var ads = document.querySelectorAll('div[id^="ad"], div[class^="ad"], iframe[src*="doubleclick"], iframe[src*="google"]');
                ads.forEach(function(ad) { ad.remove(); });
            })();
            """.trimIndent(), null
        )
    }



    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            Log.w(TAG, "Main-frame error: ${error?.description}")
            onError()
        }
    }

}