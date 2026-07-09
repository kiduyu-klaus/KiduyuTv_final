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
        // Core Ad Networks & Common Trackers
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "advertising.com", "adsystem.com", "adserver.com",
        "rubiconproject.com", "openx.net", "pubmatic.com", "criteo.com",
        "moatads.com", "taboola.com", "outbrain.com", "adroll.com",
        "imrworldwide.com", "comscore.com", "quantserve.com",
        "popads.net", "popcash.net", "propellerads.com", "ad-maven.com",
        "onclickads.net", "adsterra.com", "exo-click.com", "juicyads.com",
        "trafficjunky.net", "exoclick.com", "mc.yandex.ru", "creativecdn.com",
        "serving-sys.com", "ads.yahoo.com", "contextweb.com",
        "adtechtraffic.com", "bet365.com", "1xbet.com", "cloud.mail.ru",
        
        // Newly Identified Streaming Hijack & Analytics Domains
        "histats.com", "oundhertobeconsist.org", "aidthewallowtoh.org", "ghabovethec.info"
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
            Log.d("AdblockWebview", "Intercepted network request to ad host: $host")
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return super.shouldOverrideUrlLoading(view, request)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        // Block navigations to non-http(s) schemes entirely — these are almost always
        // malvertising redirects trying to open the Play Store, other apps, or dialers
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

        // Comprehensive DOM cleaner optimizing for Android TV D-pad focus safety
        view?.evaluateJavascript(
            """
            (function() {
                // 1. Inject CSS standard class/id ad-hiding rules
                var style = document.createElement('style');
                style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay { display: none !important; }';
                document.head.appendChild(style);

                function cleanPlayerDOM() {
                    // Remove typical iframe structural matches
                    var legacyAds = document.querySelectorAll('iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"]');
                    legacyAds.forEach(function(ad) { ad.remove(); });

                    // 2. High z-index Overlay Blanket Destroyer
                    document.querySelectorAll('div').forEach(function(el) {
                        var s = window.getComputedStyle(el);
                        var zIndex = parseInt(s.zIndex || '0', 10);
                        // Upper bounds catch transparent tracking layers without breaking native wrappers 
                        if ((s.position === 'fixed' || s.position === 'absolute') && zIndex > 9999) {
                            // Safety Check: Never accidentally delete an element wrapping the active stream
                            if (!el.querySelector('video')) {
                                el.remove();
                            }
                        }
                    });

                    // 3. Keyword Scanner targeting Social Engineering UI Ads (Snapchat / Fake Notifications)
                    var targetElements = document.querySelectorAll('div, a, span, section');
                    var targetKeywords = [
                        'snapchat', 'pending snaps', 'video call', 
                        'missed call', 'missed video', 'join the video'
                    ];

                    targetElements.forEach(function(el) {
                        var nodeText = (el.textContent || el.innerText || '').toLowerCase();
                        
                        if (targetKeywords.some(function(keyword) { return nodeText.includes(keyword); })) {
                            var floatingContainer = el;
                            
                            // Traverse upstream to find the absolute/fixed block wrapping the deceptive notification
                            while (floatingContainer.parentElement && floatingContainer.parentElement !== document.body) {
                                var parentStyle = window.getComputedStyle(floatingContainer);
                                if (parentStyle.position === 'fixed' || parentStyle.position === 'absolute') {
                                    break;
                                }
                                floatingContainer = floatingContainer.parentElement;
                            }
                            floatingContainer.remove();
                        }
                    });
                }

                // Run structural purge immediately
                cleanPlayerDOM();

                // 4. Fallback MutationObserver to catch asynchronous or delayed ad-network payloads
                if (!window.__kiduyuOverlayObserver) {
                    window.__kiduyuOverlayObserver = new MutationObserver(cleanPlayerDOM);
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
