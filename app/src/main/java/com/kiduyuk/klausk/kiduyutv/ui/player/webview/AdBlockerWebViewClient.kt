package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

open class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit
) : WebViewClient() {

    private val adDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "advertising.com", "adsystem.com", "adserver.com",
        "rubiconproject.com", "openx.net", "pubmatic.com", "criteo.com",
        "moatads.com", "taboola.com", "outbrain.com", "adroll.com",
        "imrworldwide.com", "comscore.com", "quantserve.com",
        "popads.net", "popcash.net", "propellerads.com", "ad-maven.com",
        "onclickads.net", "adsterra.com", "exo-click.com", "juicyads.com",
        "trafficjunky.net", "exoclick.com", "mc.yandex.ru", "creativecdn.com",
        "serving-sys.com", "ads.yahoo.com", "contextweb.com",
        "adtechtraffic.com", "bet365.com", "1xbet.com", "cloud.mail.ru"
    )

    // Schemes that should never be handed to the OS from inside a video player WebView.
    private val blockedSchemes = setOf(
        "intent", "market", "whatsapp", "tel", "sms", "mailto",
        "geo", "vnd.youtube", "fb-messenger"
    )

    private fun hostMatchesAdDomain(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        return adDomains.any { host == it || host.endsWith(".$it") }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val host = request?.url?.host?.lowercase()

        if (hostMatchesAdDomain(host)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return super.shouldOverrideUrlLoading(view, request)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        // Block navigations to non-http(s) schemes entirely — these are almost always
        // malvertising redirects trying to open the Play Store, other apps, or dialers,
        // not something the player should ever follow.
        if (scheme != null && scheme != "http" && scheme != "https") {
            Log.i("AdblockWebview", "Blocked non-http navigation: $scheme://")
            return true
        }

        // Block top-level navigation to known ad/redirect domains.
        if (hostMatchesAdDomain(host)) {
            Log.i("AdblockWebview", "Blocked top-level navigation to ad domain: $host")
            return true
        }

        return false
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

                var ads = document.querySelectorAll('div[id^="ad"], div[class^="ad"], iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"]');
                ads.forEach(function(ad) { ad.remove(); });

                function removeOverlays() {
                    document.querySelectorAll('div').forEach(function(el) {
                        var s = getComputedStyle(el);
                        if (s.position === 'fixed' && parseInt(s.zIndex || '0', 10) >= 999999) {
                            el.remove();
                        }
                    });
                }

                removeOverlays();

                if (!window.__kiduyuOverlayObserver) {
                    window.__kiduyuOverlayObserver = new MutationObserver(removeOverlays);
                    window.__kiduyuOverlayObserver.observe(document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                }
            })();
            """.trimIndent(), null
        )
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            Log.i("AdblockWebview", "Received error: ${error?.description}")
            onError()
        }
    }
}