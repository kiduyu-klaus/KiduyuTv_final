package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * AdBlockerWebViewClient
 *
 * Two-layer ad & popup blocking:
 *
 * 1. shouldInterceptRequest (network layer)
 *    - Blocklist of known ad/tracker/analytics/popup-network hostnames.
 *    - Blocklist of known ad/tracking URL path fragments.
 *    - Pixel-beacon detection via tracker query keywords.
 *    - Everything else (including stream CDNs, HLS origins, mirror
 *      servers) passes through to super.shouldInterceptRequest.
 *    - Explicit allowlist for provider hosts so they can never be
 *      accidentally blocked.
 *
 * 2. onPageFinished (DOM layer)
 *    - Inject CSS that hides only very specific ad markers
 *      (ins.adsbygoogle, google_ads_* iframes, taboola/outbrain blocks).
 *    - Inject JS that:
 *        * Overrides window.open / alert / confirm / prompt so scripts
 *          can't pop windows or block the player with dialogs.
 *        * Walks the DOM looking for ad iframes (by src keyword) and
 *          ad elements (by content inspection — NOT by class name alone,
 *          so legitimate player UI like subtitle pickers and server
 *          selectors are preserved).
 *        * Detects and removes fullscreen clickjacking layers
 *          (fixed position + high z-index + covers > 90% of viewport).
 *        * Attaches a MutationObserver that runs the same cleanup on
 *          any DOM nodes injected later, with an auto-disconnect timer.
 *
 * The DOM layer is intentionally conservative. Many streaming players
 * legitimately use class/id names like "overlay", "modal", "popup",
 * "banner" for their own controls (subtitles, quality picker, episode
 * list, server selector, etc.), so the script does NOT remove elements
 * based on those names. It removes them only when they contain ad
 * network signatures or are fullscreen clickjackers.
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
        //    Subdomains are matched automatically via endsWith(".blocked").
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
            // Crypto miners
            "coinhive.com",
            "crypto-loot.com",
            "coin-have.com",
            "minero.cc",
            "coinhive-manager.com",
            // Other common offenders
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
        // 2. URL path fragments.
        //
        // These are SPECIFIC enough not to collide with legitimate
        // streaming URLs like /adaptive/, /adapter/, /address/, /advance/.
        // In particular, generic "/ad/" is intentionally absent — its
        // substring match would catch far too many real paths and
        // accidentally block HLS adaptive-bitrate playlists and similar.
        // ────────────────────────────────────────────────────────────────
        private val BLOCKED_PATH_FRAGMENTS: Set<String> = setOf(
            "/ads.js",
            "/ads/", "/ads?",
            "/advert",
            "/adserver", "/adtag", "/adunit", "/adbanner", "/adslot",
            "/banner", "/bannerad",
            "/popup", "/popunder", "/clickunder",
            "/doubleclick",
            "/googlesyndication", "/googletag",
            "/adsbygoogle", "/adsense", "/adscript",
            "/prebid", "/header-bidding",
            "/affiliate", "/sponsor"
        )

        // ────────────────────────────────────────────────────────────────
        // 3. Tracker query-string keywords. Only applied to short URLs
        //    (pixel beacons) so we don't break legitimate player params.
        // ────────────────────────────────────────────────────────────────
        private val BLOCKED_QUERY_KEYWORDS: Set<String> = setOf(
            "utm_source=", "utm_medium=", "utm_campaign=",
            "utm_term=", "utm_content=",
            "fbclid=", "gclid=", "msclkid=", "yclid=", "dclid=",
            "mc_eid=", "mc_cid=",
            "_ga=", "_gl="
        )

        // ────────────────────────────────────────────────────────────────
        // DOM cleanup script.
        //
        // Runs once on onPageFinished, plus a MutationObserver that
        // re-runs cleanup on nodes injected later.
        // ────────────────────────────────────────────────────────────────
        private const val POPUP_AND_AD_SCRIPT = """
        (function() {
            'use strict';
            try {
                // ---- 0. Block popup windows and dialogs at the JS level ----
                // (Player sites sometimes run window.open() from the parent
                // page itself; WebChromeClient.onCreateWindow only catches
                // windows requested from inside child frames.)
                try { window.open = function() { return null; }; } catch(e) {}
                try {
                    window.alert = function() {};
                    window.confirm = function() { return true; };
                    window.prompt = function() { return ""; };
                } catch(e) {}

                // Block target=_blank anchor clicks at the prototype level
                // so popup-window anchor hijacks don't fire.
                try {
                    var origAnchorClick = HTMLAnchorElement.prototype.click;
                    HTMLAnchorElement.prototype.click = function() {
                        if (this.target === '_blank') return;
                        return origAnchorClick.apply(this, arguments);
                    };
                } catch(e) {}

                // ---- 1. Inject CSS that hides only very specific ad markers ----
                // (Generic .overlay / .modal / .popup selectors are NOT used
                // here — players legitimately use those names for their own
                // controls like subtitle/quality/server pickers.)
                if (!document.getElementById('__adblock_css__')) {
                    var s = document.createElement('style');
                    s.id = '__adblock_css__';
                    s.innerHTML = [
                        'ins.adsbygoogle',
                        'iframe[id^="google_ads_"]',
                        'iframe[name^="google_ads_"]',
                        'iframe[id^="ad_iframe"]',
                        'div[id^="taboola-"]',
                        'div[id^="outbrain-"]'
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

                // ---- 2. Heuristics ----
                var AD_KEYWORDS = [
                    'doubleclick','googlesyndication','googletagservices',
                    'adservice.google','adsbygoogle','adsense',
                    'popads','popcash','propellerads','exoclick',
                    'adsterra','onclickads','juicyads','trafficjunky',
                    'taboola','outbrain','mgid','revcontent',
                    'clickadu','hilltopads','ad-maven','onclickmax',
                    'beachfront','zedo','adnxs','pubmatic',
                    'rubiconproject','openx.net','criteo',
                    'indexexchange','sovrn','33across',
                    'media.net','yahoo-syndication','prebid'
                ];

                var PLAYER_HOSTS = [
                    'embed','player','m3u8','stream','video','watch',
                    '/movie/','/tv/',
                    'vidlink','vidsrc','vidfast','vidking','vidnest',
                    'videasy','vidrock','vidup','flixer','vidcore',
                    'moviesapi','peachify','vaplayer','vidplus',
                    'cinesrc','vidzen','cinemaos','amri','zxcstream',
                    'vidlux','primesrc','lordflix','mapple',
                    'smashystream','autoembed','embedmaster','vidsync'
                ];

                function isPlayerFrame(frame) {
                    if (!frame) return false;
                    if (frame.tagName !== 'IFRAME') return false;
                    if (frame.id === 'player-frame') return true;
                    var src = (frame.src || '').toLowerCase();
                    if (!src) return true; // sandboxed/empty = assume player
                    for (var i = 0; i < PLAYER_HOSTS.length; i++) {
                        if (src.indexOf(PLAYER_HOSTS[i]) !== -1) return true;
                    }
                    return false;
                }

                function isAdIframe(frame) {
                    if (!frame || frame.tagName !== 'IFRAME') return false;
                    if (isPlayerFrame(frame)) return false;
                    var src = (frame.src || '').toLowerCase();
                    var id  = (frame.id || '').toLowerCase();
                    var nm  = (frame.name || '').toLowerCase();
                    for (var i = 0; i < AD_KEYWORDS.length; i++) {
                        if (src.indexOf(AD_KEYWORDS[i]) !== -1) return true;
                    }
                    if (id.indexOf('google_ads') !== -1) return true;
                    if (id.indexOf('ad_iframe') !== -1) return true;
                    if (nm.indexOf('google_ads') !== -1) return true;
                    if (nm.indexOf('ad_iframe') !== -1) return true;
                    return false;
                }

                function containsAdKeyword(html) {
                    if (!html) return false;
                    var cap = html.length > 5000 ? html.substring(0, 5000) : html;
                    for (var i = 0; i < AD_KEYWORDS.length; i++) {
                        if (cap.indexOf(AD_KEYWORDS[i]) !== -1) return true;
                    }
                    return false;
                }

                function isAdElement(node) {
                    if (!node || node.nodeType !== 1) return false;
                    if (node.tagName === 'BODY' || node.tagName === 'HTML') return false;
                    if (node.tagName === 'VIDEO') return false;
                    if (node.tagName === 'IFRAME') return isAdIframe(node);
                    if (containsPlayerFrame(node)) return false;

                    // Direct, very-specific ad markers
                    var id  = (node.id || '').toLowerCase();
                    var cls = (node.className && typeof node.className === 'string'
                                ? node.className : '').toLowerCase();
                    if (id.indexOf('google_ads') !== -1) return true;
                    if (id.indexOf('ad_iframe') !== -1) return true;
                    if (id.indexOf('adsense') !== -1) return true;
                    if (id.indexOf('taboola-') === 0) return true;
                    if (id.indexOf('outbrain-') === 0) return true;

                    if (node.tagName === 'INS' &&
                        cls.indexOf('adsbygoogle') !== -1) return true;

                    // Inspect HTML content for ad network signatures
                    var html = '';
                    try { html = (node.outerHTML || '').toLowerCase(); }
                    catch(e) {}
                    if (containsAdKeyword(html)) return true;

                    // Inspect visible text for ad copy (short text only)
                    var txt = '';
                    try { txt = (node.innerText || node.textContent || '').toLowerCase(); }
                    catch(e) {}
                    if (txt.length > 0 && txt.length < 200) {
                        if (txt.indexOf('skip ad') !== -1) return true;
                        if (txt.indexOf('sponsored') !== -1 &&
                            txt.indexOf('player') === -1) return true;
                    }

                    return false;
                }

                function containsPlayerFrame(node) {
                    if (!node || !node.querySelectorAll) return false;
                    try {
                        if (node.querySelector('video')) return true;
                    } catch(e) {}
                    var frames = node.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        if (isPlayerFrame(frames[i])) return true;
                    }
                    return false;
                }

                function isClickjackingLayer(node) {
                    try {
                        if (!node || node.nodeType !== 1) return false;
                        if (node.tagName === 'BODY' || node.tagName === 'HTML') return false;
                        if (node.tagName === 'IFRAME' && isPlayerFrame(node)) return false;
                        // If this node CONTAINS the player frame, it's the
                        // player container — not a clickjacker.
                        if (containsPlayerFrame(node)) return false;

                        var style = window.getComputedStyle(node);
                        if (!style) return false;
                        if (style.position !== 'fixed' &&
                            style.position !== 'absolute') return false;

                        var z = parseInt(style.zIndex, 10);
                        if (isNaN(z) || z < 999) return false;

                        var rect = node.getBoundingClientRect();
                        var w = window.innerWidth  || document.documentElement.clientWidth;
                        var h = window.innerHeight || document.documentElement.clientHeight;
                        if (!w || !h) return false;
                        if (rect.width  < w * 0.9) return false;
                        if (rect.height < h * 0.9) return false;

                        // Require an ad signature to be sure — pure geometry
                        // alone could still hit a fullscreen legitimate UI.
                        var html = '';
                        try { html = (node.outerHTML || '').toLowerCase(); }
                        catch(e) {}
                        if (containsAdKeyword(html)) return true;

                        // Or suspicious anchor text like "Click to continue"
                        var txt = '';
                        try { txt = (node.innerText || node.textContent || '').toLowerCase(); }
                        catch(e) {}
                        if (txt.length > 0 && txt.length < 300) {
                            if (txt.indexOf('click to continue') !== -1) return true;
                            if (txt.indexOf('click here to continue') !== -1) return true;
                        }

                        return false;
                    } catch(e) { return false; }
                }

                // ---- 3. Cleanup ----
                function nuke(root) {
                    if (!root || !root.querySelectorAll) return;
                    try {
                        // Ad iframes
                        var iframes = root.querySelectorAll('iframe');
                        for (var i = 0; i < iframes.length; i++) {
                            var f = iframes[i];
                            if (isAdIframe(f)) {
                                try { f.parentNode.removeChild(f); } catch(e) {}
                            }
                        }
                        // Ad elements (content-inspected, not class-matched)
                        var all = root.querySelectorAll('div, section, aside, span');
                        for (var j = 0; j < all.length; j++) {
                            var n = all[j];
                            if (isAdElement(n)) {
                                try { n.parentNode.removeChild(n); } catch(e) {}
                            }
                        }
                        // Fullscreen clickjacking layers
                        var cands = root.querySelectorAll('div, a');
                        for (var k = 0; k < cands.length; k++) {
                            var cj = cands[k];
                            if (isClickjackingLayer(cj)) {
                                try { cj.parentNode.removeChild(cj); } catch(e) {}
                            }
                        }
                    } catch(e) {}
                }

                nuke(document);

                // ---- 4. MutationObserver for late-injected overlays ----
                try {
                    var mo = new MutationObserver(function(muts) {
                        for (var i = 0; i < muts.length; i++) {
                            var added = muts[i].addedNodes;
                            for (var j = 0; j < added.length; j++) {
                                var nn = added[j];
                                if (nn && nn.nodeType === 1) nuke(nn);
                            }
                        }
                    });
                    mo.observe(document.documentElement, { childList: true, subtree: true });
                    // Most popup injections happen within the first ~20s.
                    setTimeout(function() { try { mo.disconnect(); } catch(e){} }, 20000);
                } catch(e) {}
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
        val req = request ?: return null
        val url = req.url.toString()
        val host = req.url.host?.lowercase().orEmpty()

        // Defensive: provider hosts are always allowed through.
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
