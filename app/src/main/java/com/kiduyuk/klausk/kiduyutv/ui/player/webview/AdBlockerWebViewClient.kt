package com.kiduyuk.klausk.kiduyutv.ui.player.webview

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
        
        // Streaming Hijack, Social Bar, & Analytics Tracking Nodes
        "histats.com", "oundhertobeconsist.org", "aidthewallowtoh.org", "ghabovethec.info"
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

        // Block transitions to non-http/s schemas (e.g., intent://, market://) which break focus
        if (scheme != null && scheme != "http" && scheme != "https") {
            Log.i("AdblockWebview", "Blocked non-http navigation: $scheme://")
            return true
        }

        // Block top-level redirection loading attempts straight to ad networks
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
                // 1. Inject an aggressive CSS rule to instantly blind structural ad wrappers
                var style = document.createElement('style');
                style.innerHTML = '[class*="social-bar"], [id*="social-bar"], .ad-notification, #pop-container, div[id^="ad"], div[class^="ad"], .popup, .overlay { display: none !important; pointer-events: none !important; opacity: 0 !important; width: 0px !important; height: 0px !important; }';
                document.head.appendChild(style);

                function cleanPlayerDOM() {
                    try {
                        // 2. Query elements that can behave as floating view overlays
                        var elements = document.querySelectorAll('div, section, iframe, aside, a, span');
                        
                        elements.forEach(function(el) {
                            try {
                                var s = window.getComputedStyle(el);
                                var zIndex = parseInt(s.zIndex || '0', 10);
                                
                                // Identify viewport-relative layout blocks
                                if ((s.position === 'fixed' || s.position === 'absolute') && zIndex > 9) {
                                    var rect = el.getBoundingClientRect();
                                    
                                    // SIZE FILTER: If the layout element is compact (not the fullscreen stream itself),
                                    // process it safely without risking structural harm to the core media components.
                                    if (rect.width > 0 && rect.height > 0 && 
                                        (rect.width < window.innerWidth * 0.85 || rect.height < window.innerHeight * 0.85)) {
                                        
                                        // Target A: Floating cross-origin frame layers (bypasses string boundaries)
                                        if (el.tagName.toLowerCase() === 'iframe' || el.querySelector('iframe')) {
                                            if (!el.querySelector('video')) {
                                                el.remove();
                                                return;
                                            }
                                        }
                                        
                                        // Target B: Native DOM text tracking targets mimicking system alerts
                                        var text = (el.textContent || el.innerText || '').toLowerCase();
                                        var badKeywords = ['video call', 'missed call', 'missed video', 'join the video', 'snapchat', 'pending snaps'];
                                        if (badKeywords.some(function(k) { return text.includes(k); })) {
                                            el.remove();
                                            return;
                                        }
                                        
                                        // Target C: Naming profiles matching ad layout classes
                                        var identity = (el.id + ' ' + el.className).toLowerCase();
                                        if (identity.includes('notification') || identity.includes('popup') || identity.includes('alert')) {
                                            el.remove();
                                            return;
                                        }
                                    }
                                }
                            } catch (e) {}
                        });
                    } catch (globalErr) {}
                }

                // Run structural purge sweep immediately on load
                cleanPlayerDOM();

                // 3. Keep layout mutation traps active for dynamically injected objects
                if (!window.__kiduyuOverlayObserver) {
                    window.__kiduyuOverlayObserver = new MutationObserver(function(mutations) {
                        var shouldClean = mutations.some(function(m) { return m.addedNodes.length > 0; });
                        if (shouldClean) {
                            cleanPlayerDOM();
                        }
                    });
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
