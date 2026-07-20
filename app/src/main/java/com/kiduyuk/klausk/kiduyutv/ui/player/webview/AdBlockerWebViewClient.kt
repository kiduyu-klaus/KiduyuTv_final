package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.graphics.Bitmap
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
    private val onError: () -> Unit
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
        executeAntiAdScript(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()
        // Run again on finish to catch lazy-loaded assets
        //executeAntiAdScript(view)
    }

    private fun executeAntiAdScript(view: WebView?) {
        view?.evaluateJavascript(
            """
            (function() {
                // 1. Inject CSS rules globally to keep known elements hidden
                var style = document.createElement('style');
                style.innerHTML = '[class*="social-bar"], [id*="social-bar"], .ad-notification, #pop-container, div[id^="ad"], div[class^="ad"], .popup, .overlay, iframe[src*="histats.com"], iframe[src*="aidthewallowtoh.org"] { display: none !important; pointer-events: none !important; opacity: 0 !important; width: 0px !important; height: 0px !important; }';
                document.head.appendChild(style);

                function cleanPlayerDOM() {
                    try {
                        var videoEl = document.querySelector('video');
                        var elements = document.querySelectorAll('div, section, iframe, aside, a, span');
                        
                        elements.forEach(function(el) {
                            try {
                                var s = window.getComputedStyle(el);
                                var zIndex = parseInt(s.zIndex || '0', 10);
                                
                                // Look for elements explicitly designed to float or layer over content
                                if ((s.position === 'fixed' || s.position === 'absolute') && zIndex > 9) {
                                    
                                    // SECURITY RULE: Never delete any element that houses the active video player stream
                                    if (videoEl && el.contains(videoEl)) {
                                        return;
                                    }

                                    var text = (el.textContent || el.innerText || '').toLowerCase();
                                    var identity = (el.id + ' ' + el.className).toLowerCase();
                                    var badKeywords = ['video call', 'missed call', 'missed video', 'join the video', 'snapchat', 'pending snaps', 'whatsapp'];
                                    
                                    // Match 1: Text patterns matching sneaky notifications
                                    var hasBadText = badKeywords.some(function(k) { return text.includes(k); });
                                    
                                    // Match 2: Layout structural matching signatures
                                    var hasAdIdentity = identity.includes('notification') || identity.includes('popup') || identity.includes('alert') || identity.includes('social-bar');
                                    
                                    // Match 3: Iframes that are clearly not the video stream container
                                    var isAdIframe = el.tagName.toLowerCase() === 'iframe' && !identity.includes('player') && !el.src.includes('player');

                                    // If any signature matches, drop the entire element tree immediately regardless of its size wrapper
                                    if (hasBadText || hasAdIdentity || isAdIframe) {
                                        el.remove();
                                        return;
                                    }

                                    // Fallback Size Check: For general non-branded floating clutter
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

                // Initial purge execution
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
