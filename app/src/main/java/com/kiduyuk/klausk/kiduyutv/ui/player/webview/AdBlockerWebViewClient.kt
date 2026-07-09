package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * AdBlockerWebViewClient - Handles ad blocking, tracker blocking, and
 * popup/overlay removal.
 *
 * Strategy: **blocklist**, not whitelist.
 *
 * Why not whitelist?
 * ------------------
 * Stream providers (VidLink, VidSrc, Vidsrc.xyz, Vidfast, etc.) typically
 * resolve to multiple servers and CDNs at playback time — different subdomains,
 * HLS origins, mirror hosts, sometimes third-party video hosts (cloudfront,
 * bunnycdn, etc.). A strict whitelist of "vidlink.pro" would intercept and
 * empty-return every one of those legitimate requests and the player would
 * silently stall.
 *
 * So instead we:
 *   1. Hard-block known ad/tracker/analytics/popup-network hostnames.
 *   2. Hard-block URLs whose path or query matches ad/tracking patterns.
 *   3. Let everything else through, including any CDN the player picks.
 *   4. Always let [allowedHosts] through even if they happen to match (defensive).
 *   5. On page load (and via MutationObserver) inject CSS + DOM cleanup that
 *      hides and removes popup overlays, modals, banners, and ad iframes.
 */
open class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit,
    private val allowedHosts: Set<String> = emptySet()
) : WebViewClient() {

    companion object {
        private const val TAG = "AdBlockerWebView"

        // ────────────────────────────────────────────────────────────────
        // 1. Known ad / tracking / analytics / popup-network hostnames.
        //    A request is blocked if its host equals one of these or is a
        //    subdomain of one (e.g. "www.googletagmanager.com" matches
        //    "googletagmanager.com").
        // ────────────────────────────────────────────────────────────────
        private val BLOCKED_HOSTS: Set<String> = setOf(
            // Google ad / tracking
            "doubleclick.net",
            "googlesyndication.com",
            "googletagservices.com",
            "googletagmanager.com",
            "google-analytics.com",
            "googleadservices.com",
            "adservice.google.com",
            "adservice.google.com.au",
            "pagead2.googlesyndication.com",
            "ads.google.com",
            "stats.g.doubleclick.net",
            // Facebook / Meta
            "connect.facebook.net",
            "facebook.com",
            "facebook.net",
            // Major ad exchanges & SSPs
            "adnxs.com",
            "adsrvr.org",
            "adform.net",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "mgid.com",
            "media.net",
            "yahoo-syndication.com",
            "moatads.com",
            "doubleverify.com",
            "ias.net",
            "mathtag.com",
            "adsymptotic.com",
            "adcolony.com",
            "unityads.unity3d.com",
            // Analytics / telemetry
            "scorecardresearch.com",
            "quantserve.com",
            "chartbeat.com",
            "chartbeat.net",
            "hotjar.com",
            "mixpanel.com",
            "segment.io",
            "newrelic.com",
            "sentry.io",
            "amplitude.com",
            "fullstory.com",
            "bugsnag.com",
            "datadoghq.com",
            "mouseflow.com",
            "clicky.com",
            // Attribution / SDK trackers
            "branch.io",
            "appsflyer.com",
            "adjust.com",
            "kochava.com",
            // Popup / popunder / click-under networks
            "propellerads.com",
            "popads.net",
            "popcash.net",
            "exoclick.com",
            "trafficjunky.net",
            "juicyads.com",
            "clickadu.com",
            "adsterra.com",
            "hilltopads.com",
            "revcontent.com",
            "zedo.com",
            // Crypto miners (often injected via compromised ad scripts)
            "coinhive.com",
            "crypto-loot.com",
            "coin-have.com",
            "minero.cc",
            "coinhive-manager.com",
            // Other common offenders
            "adsterra.com",
            "ad-maven.com",
            "onclickads.net",
            "onclickmax.com",
            "beachfront.com",
            "smartadserver.com",
            "yieldmo.com",
            "indexexchange.com",
            "sovrn.com",
            "33across.com"
        )

        // ────────────────────────────────────────────────────────────────
        // 2. URL path fragments that strongly suggest an ad/tracker hit.
        //    Matched as substrings (case-insensitive).
        // ────────────────────────────────────────────────────────────────
        private val BLOCKED_PATH_FRAGMENTS: Set<String> = setOf(
            "/ads/", "/ad/", "/adv/", "/advert/", "/adserver/", "/adserve/",
            "/adtag/", "/adunit/", "/adslot/", "/adbanner/",
            "/banner/", "/banners/",
            "/popup/", "/pop-up/", "/popunder/", "/pop-under/", "/clickunder/",
            "/tracker/", "/tracking/", "/track/", "/pixel/", "/beacon/",
            "/analytics/", "/telemetry/", "/tagmanager/",
            "/affiliate/", "/sponsor/", "/promo/",
            "/adsbygoogle", "/adsense/", "/adscript/",
            "/prebid/", "/header-bidding/"
        )

        // ────────────────────────────────────────────────────────────────
        // 3. Query-string keywords that are almost exclusively used by
        //    trackers. We only fire this rule for short requests (typical
        //    pixel beacons) so we don't break legitimate player URLs.
        // ────────────────────────────────────────────────────────────────
        private val BLOCKED_QUERY_KEYWORDS: Set<String> = setOf(
            "utm_source=", "utm_medium=", "utm_campaign=",
            "utm_term=", "utm_content=",
            "fbclid=", "gclid=", "msclkid=", "yclid=", "dclid=",
            "mc_eid=", "mc_cid=",
            "_ga=", "_gl="
        )

        // Script that hides popups / overlays / banners via CSS, then walks
        // the DOM to remove ad iframes and known overlay nodes, and finally
        // attaches a MutationObserver to catch overlays injected later.
        private const val POPUP_AND_AD_SCRIPT = """
            (function() {
                try {
                    // ---- 1. Inject CSS that hides every popup/overlay/banner shape ----
                    if (!document.getElementById('__adblock_css__')) {
                        var s = document.createElement('style');
                        s.id = '__adblock_css__';
                        s.innerHTML = [
                            // Direct ad-prefix matches
                            'div[id^="ad-"]', 'div[class^="ad-"]',
                            'div[id^="ad_"]', 'div[class^="ad_"]',
                            'div[id^="ad "]', 'div[class^="ad "]',
                            // Loose "contains" matches
                            '[id*="ads" i]',   '[class*="ads" i]',
                            '[id*="advert" i]', '[class*="advert" i]',
                            '[id*="banner" i]', '[class*="banner" i]',
                            '[id*="popup" i]',  '[class*="popup" i]',
                            '[id*="overlay" i]', '[class*="overlay" i]',
                            '[id*="modal" i]',  '[class*="modal" i]',
                            '[id*="sponsor" i]', '[class*="sponsor" i]',
                            '[id*="promo" i]',  '[class*="promo" i]',
                            '[id*="interstitial" i]', '[class*="interstitial" i]',
                            // AdSense / Google ad slots
                            'ins.adsbygoogle',
                            'iframe[id^="google_ads_"]',
                            'iframe[name^="google_ads_"]',
                            // Taboola / Outbrain content blocks
                            'div[id^="taboola-"]', 'div[id^="outbrain-"]'
                        ].join(',') + '{' +
                            'display:none!important;' +
                            'visibility:hidden!important;' +
                            'opacity:0!important;' +
                            'pointer-events:none!important;' +
                            'width:0!important;height:0!important;' +
                            'position:absolute!important;' +
                            'left:-99999px!important;top:-99999px!important;' +
                        '}';
                        (document.head || document.documentElement).appendChild(s);
                    }

                    // ---- 2. DOM cleanup helper ----
                    function isPlayerFrame(n) {
                        if (!n) return false;
                        if (n.id === 'player-frame') return true;
                        if (n.tagName === 'IFRAME' && n.id === 'player-frame') return true;
                        return false;
                    }

                    function nuke(root) {
                        if (!root || !root.querySelectorAll) return;
                        try {
                            var sel = [
                                '[id*="ads" i]', '[class*="ads" i]',
                                '[id*="advert" i]', '[class*="advert" i]',
                                '[id*="banner" i]', '[class*="banner" i]',
                                '[id*="popup" i]', '[class*="popup" i]',
                                '[id*="overlay" i]', '[class*="overlay" i]',
                                '[id*="modal" i]', '[class*="modal" i]',
                                '[id*="sponsor" i]', '[class*="sponsor" i]',
                                '[id*="promo" i]', '[class*="promo" i]',
                                '[id*="interstitial" i]', '[class*="interstitial" i]'
                            ].join(',');
                            var nodes = root.querySelectorAll(sel);
                            for (var i = 0; i < nodes.length; i++) {
                                var n = nodes[i];
                                if (isPlayerFrame(n)) continue;
                                // Don't nuke <body> or <html>
                                if (n === document.body || n === document.documentElement) continue;
                                try { n.parentNode.removeChild(n); } catch(e) {}
                            }

                            // Drop ad/popup iframes by src keyword
                            var iframes = root.querySelectorAll('iframe[src]');
                            for (var j = 0; j < iframes.length; j++) {
                                var f = iframes[j];
                                if (isPlayerFrame(f)) continue;
                                var src = (f.src || '').toLowerCase();
                                if (
                                    src.indexOf('doubleclick') !== -1 ||
                                    src.indexOf('googlesyndication') !== -1 ||
                                    src.indexOf('googletagservices') !== -1 ||
                                    src.indexOf('adservice') !== -1 ||
                                    src.indexOf('popads') !== -1 ||
                                    src.indexOf('popcash') !== -1 ||
                                    src.indexOf('propellerads') !== -1 ||
                                    src.indexOf('exoclick') !== -1 ||
                                    src.indexOf('outbrain') !== -1 ||
                                    src.indexOf('taboola') !== -1 ||
                                    src.indexOf('/ads/') !== -1 ||
                                    src.indexOf('/ad/') !== -1 ||
                                    src.indexOf('adsbygoogle') !== -1
                                ) {
                                    try { f.parentNode.removeChild(f); } catch(e) {}
                                }
                            }
                        } catch(e) { /* ignore */ }
                    }

                    nuke(document);

                    // ---- 3. Watch for overlays injected after first paint ----
                    try {
                        var mo = new MutationObserver(function(muts) {
                            for (var i = 0; i < muts.length; i++) {
                                var added = muts[i].addedNodes;
                                for (var j = 0; j < added.length; j++) {
                                    var n = added[j];
                                    if (n && n.nodeType === 1) nuke(n);
                                }
                            }
                        });
                        mo.observe(document.documentElement, { childList: true, subtree: true });
                        // Most popups are injected within the first ~20s; tear
                        // the observer down after that to avoid overhead.
                        setTimeout(function() { try { mo.disconnect(); } catch(e){} }, 20000);
                    } catch(e) { /* MutationObserver unavailable */ }
                } catch(err) { /* never let cleanup throw */ }
            })();
        """
    }

    private fun isBlockedHost(host: String): Boolean {
        if (host.isEmpty()) return false
        return BLOCKED_HOSTS.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    private fun isBlockedUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (BLOCKED_PATH_FRAGMENTS.any { it in lower }) return true
        // Pixel-beacon style requests: short URLs that only carry tracker params.
        if (lower.length < 220 && BLOCKED_QUERY_KEYWORDS.any { it in lower }) {
            return true
        }
        return false
    }

    private fun isExplicitlyAllowed(host: String): Boolean {
        if (host.isEmpty() || allowedHosts.isEmpty()) return false
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        val host = request.url.host?.lowercase().orEmpty()

        // Defensive: even if a provider host ever collides with a blocked
        // pattern, never intercept it.
        if (isExplicitlyAllowed(host)) {
            return super.shouldInterceptRequest(view, request)
        }

        if (isBlockedHost(host)) {
            Log.d(TAG, "Blocked ad/tracker host: $host")
            return emptyResponse()
        }
        if (isBlockedUrl(url)) {
            Log.d(TAG, "Blocked ad/tracker URL: $url")
            return emptyResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()
        view?.evaluateJavascript(POPUP_AND_AD_SCRIPT, null)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            Log.w(TAG, "Main-frame error: ${error?.description}")
            onError()
        }
    }
}