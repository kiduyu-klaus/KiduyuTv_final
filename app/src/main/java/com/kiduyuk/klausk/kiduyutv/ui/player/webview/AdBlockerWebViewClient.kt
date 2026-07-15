package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import java.io.ByteArrayInputStream

open class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit,
    private val onCommitVisible: () -> Unit = {}
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url

        // Never replace the top-level document; evaluate only player subresources.
        if (request?.isForMainFrame != true && uri != null && AdvancedAdBlocker.shouldBlock(uri)) {
            Log.d("AdblockWebview", "Intercepted network request to ad host: ${uri.host.orEmpty()}")
            // Return an empty JS script comment if it's a script request to prevent script execution exceptions
            val path = uri.path?.lowercase() ?: ""
            return if (path.endsWith(".js")) {
                WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream("/*blocked*/".toByteArray()))
            } else {
                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return super.shouldOverrideUrlLoading(view, request)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (scheme != null && scheme != "http" && scheme != "https") {
            Log.i("AdblockWebview", "Blocked non-http navigation: $scheme://")
            return true
        }

        if (AdvancedAdBlocker.shouldBlock(uri)) {
            Log.i("AdblockWebview", "Blocked top-level navigation to ad domain: $host")
            return true
        }

        return false
    }

    // Crucial: Run the purging engine early, the moment the page structure becomes visible
    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        executeAntiAdScript(view, isFirstCommit = true)
        onCommitVisible()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()
        // Run again on finish to catch lazy-loaded assets
        executeAntiAdScript(view, isFirstCommit = false)
    }

    /**
     * Injects the anti-ad stylesheet and, only when the document actually contains a
     * `<video>` element reachable from this script, runs the DOM walker and installs
     * the mutation observer. Wrapper documents built by `StreamProviderManager` only
     * contain a single cross-origin `<iframe>` and have no DOM we can touch, so the
     * expensive walker is skipped for them — that path is the most common case in
     * the app and is where the perf win comes from.
     */
    private fun executeAntiAdScript(view: WebView?, isFirstCommit: Boolean) {
        val script = if (isFirstCommit) ANTI_AD_COMMIT_SCRIPT else ANTI_AD_FINISHED_SCRIPT
        view?.evaluateJavascript(script, null)
    }

    private val ANTI_AD_COMMIT_SCRIPT: String = """
        (function() {
            try {
                var existingStyle = document.getElementById('kiduyu-anti-ad-css');
                if (!existingStyle) {
                    var style = document.createElement('style');
                    style.id = 'kiduyu-anti-ad-css';
                    style.textContent = '[class*="social-bar"], [id*="social-bar"], .ad-notification, #pop-container, div[id^="ad"], div[class^="ad"], .popup, .overlay, iframe[src*="histats.com"], iframe[src*="aidthewallowtoh.org"] { display: none !important; pointer-events: none !important; opacity: 0 !important; width: 0px !important; height: 0px !important; }';
                    (document.head || document.documentElement).appendChild(style);
                }

                // Cheap probe: does this document host a <video> we can see?
                // For cross-origin iframe wrappers the answer is always no, so we
                // skip the heavy DOM walker and MutationObserver entirely.
                if (document.querySelector('video')) {
                    installOverlayCleaner();
                }
            } catch (e) {}

            function installOverlayCleaner() {
                if (window.__kiduyuOverlayObserver) return;
                var scheduled = false;
                function scheduleClean() {
                    if (scheduled) return;
                    scheduled = true;
                    (window.requestIdleCallback || function(cb){return setTimeout(cb,50);})(function(){
                        scheduled = false;
                        cleanPlayerDOM();
                    });
                }
                function cleanPlayerDOM() {
                    try {
                        var videoEl = document.querySelector('video');
                        var elements = document.querySelectorAll('div, section, iframe, aside, a, span');
                        elements.forEach(function(el) {
                            try {
                                var s = window.getComputedStyle(el);
                                var zIndex = parseInt(s.zIndex || '0', 10);
                                if ((s.position === 'fixed' || s.position === 'absolute') && zIndex > 9) {
                                    if (videoEl && el.contains(videoEl)) return;
                                    var text = (el.textContent || el.innerText || '').toLowerCase();
                                    var identity = (el.id + ' ' + el.className).toLowerCase();
                                    var badKeywords = ['video call', 'missed call', 'missed video', 'join the video', 'snapchat', 'pending snaps', 'whatsapp'];
                                    var hasBadText = badKeywords.some(function(k) { return text.includes(k); });
                                    var hasAdIdentity = identity.includes('notification') || identity.includes('popup') || identity.includes('alert') || identity.includes('social-bar');
                                    var isAdIframe = el.tagName.toLowerCase() === 'iframe' && !identity.includes('player') && !(el.getAttribute('src') || '').includes('player');
                                    if (hasBadText || hasAdIdentity || isAdIframe) {
                                        el.remove();
                                        return;
                                    }
                                    var rect = el.getBoundingClientRect();
                                    if (rect.width > 0 && rect.height > 0 &&
                                        (rect.width < window.innerWidth * 0.85 || rect.height < window.innerHeight * 0.85)) {
                                        if (!identity.includes('player') && !identity.includes('video') && !identity.includes('control')) {
                                            el.remove();
                                        }
                                    }
                                }
                            } catch (e) {}
                        });
                    } catch (globalErr) {}
                }
                window.__kiduyuOverlayObserver = new MutationObserver(function(mutations) {
                    var shouldClean = mutations.some(function(m) { return m.addedNodes.length > 0; });
                    if (shouldClean) scheduleClean();
                });
                window.__kiduyuOverlayObserver.observe(document.documentElement, {
                    childList: true,
                    subtree: true
                });
                cleanPlayerDOM();
            }
        })();
    """.trimIndent()

    /**
     * Lighter script used on `onPageFinished`. Only re-runs the cleaner for documents
     * that already opted in (i.e. host a reachable `<video>`), so wrapper documents
     * still pay almost nothing.
     */
    private val ANTI_AD_FINISHED_SCRIPT: String = """
        (function() {
            try {
                if (document.querySelector('video') && window.__kiduyuOverlayObserver) {
                    (window.requestIdleCallback || function(cb){return setTimeout(cb,0);})(function(){
                        var ev = new Event('kiduyu-rerun-cleaner');
                        window.dispatchEvent(ev);
                    });
                }
            } catch (e) {}
        })();
    """.trimIndent()

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            Log.i("AdblockWebview", "Received error: ${error?.description}")
            onError()
        }
    }
}
