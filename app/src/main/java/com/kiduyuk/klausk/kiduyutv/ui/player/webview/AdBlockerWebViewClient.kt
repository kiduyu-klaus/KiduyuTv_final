package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * AdBlockerWebViewClient - Handles ad blocking and page lifecycle events
 */
open class AdBlockerWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onError: () -> Unit
) : WebViewClient() {

    // Ad networks
    private val adDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",

        "propellerads.com",
        "adsterra.com",
        "popads.net",
        "popcash.net",
        "onclickads.net",
        "trafficjunky.net",
        "juicyads.com",
        "exoclick.com",
        "hilltopads.net",
        "mgid.com",
        "taboola.com",
        "outbrain.com",
        "push.house",
        "pushwelcome.com"
    )

    // Analytics / tracking — blocked for privacy, not because they're ads
    private val trackingDomains = setOf(
        "googletagmanager.com",
        "google-analytics.com",
        "histats.com",
        "s10.histats.com"
    )

    // Push-notification prompt SDKs — blocked to suppress permission popups
    private val notificationDomains = setOf(
        "onesignal.com"
    )

    // "Social bar" / fake notification ad networks — these mimic native app
    // notifications (Snapchat, WhatsApp, etc.) as bait to redirect to ad pages.
    // Domain names are throwaway/rotating, so this list needs periodic upkeep.
    private val socialBarDomains = setOf(
        "oundhertobeconsist.org",
        "aidthewallowtoh.org",
        "ghabovethec.info"
    )

    // NOTE: firebaseinstallations.googleapis.com is intentionally NOT blocked here.
    // It's Google's Firebase Installations API (used by Firebase Auth, Analytics,
    // Crashlytics, Remote Config to issue install IDs) — not an ad domain. Blocking
    // it can silently break Firebase sync elsewhere in the app.
    private val blockedDomains = adDomains + trackingDomains + notificationDomains + socialBarDomains

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val host = request?.url?.host?.lowercase() ?: return super.shouldInterceptRequest(view, request)

        if (blockedDomains.any { host == it || host.endsWith(".$it") }) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()

        try {
            view?.evaluateJavascript(
                """
                (function() {
                    var style = document.createElement('style');
                    style.innerHTML = 'div[id*="advert"], div[class*="advert"], div[id*="-ad-"], div[class*="-ad-"], .popup, .overlay { display: none !important; } div[style*="2147483647"] { display: none !important; }';
                    document.head.appendChild(style);

                    var ads = document.querySelectorAll('div[id*="advert"], div[class*="advert"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"]');
                    ads.forEach(function(ad) { ad.remove(); });
                })();
                """.trimIndent(), null
            )
        } catch (e: Exception) {
            Log.w("AdblockWebview", "evaluateJavascript failed: ${e.message}")
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            Log.i("AdblockWebview", "Received error: ${error?.description}")
            onError()
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        if (request?.isForMainFrame == true) {
            Log.i("AdblockWebview", "Received HTTP error: ${errorResponse?.statusCode}")
            onError()
        }
    }
}
