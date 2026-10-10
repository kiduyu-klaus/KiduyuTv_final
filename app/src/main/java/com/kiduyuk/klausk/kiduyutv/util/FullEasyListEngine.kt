package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.github.edsuns.adfilter.AdFilter

/**
 * Process-wide full EasyList-compatible filtering for WebViews.
 *
 * The native engine evaluates the complete WebResourceRequest, including the
 * document origin and resource context. It also supports cosmetic filters and
 * scriptlets, which the legacy domain-only AdvancedAdBlocker cannot parse.
 * AdvancedAdBlocker remains available as a fallback for callers that have not
 * migrated to this API yet.
 */
object FullEasyListEngine {
    private const val TAG = "FullEasyListEngine"
    private const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
    private const val EASYLIST_NAME = "EasyList"

    @Volatile
    private var engine: AdFilter? = null

    @Synchronized
    fun initialize(context: Context): Boolean {
        if (engine != null) return true

        return runCatching {
            val created = AdFilter.create(context.applicationContext)
            if (!created.hasInstallation) {
                val subscription = created.viewModel.addFilter(EASYLIST_NAME, EASYLIST_URL)
                created.viewModel.download(subscription.id)
                Log.i(TAG, "Queued full EasyList download")
            }
            engine = created
            true
        }.onFailure { error ->
            Log.w(TAG, "Full EasyList engine unavailable; using fallback", error)
        }.getOrDefault(false)
    }

    fun isAvailable(): Boolean = engine != null

    fun setupWebView(webView: WebView) {
        engine?.setupWebView(webView)
    }

    fun shouldIntercept(
        webView: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val activeEngine = engine ?: return null
        return runCatching {
            activeEngine.shouldIntercept(webView, request).resourceResponse
        }.onFailure { error ->
            Log.w(TAG, "Full EasyList request matching failed for ${request.url}", error)
        }.getOrNull()
    }

    fun shouldBlock(webView: WebView, request: WebResourceRequest): Boolean {
        val activeEngine = engine ?: return false
        return runCatching {
            activeEngine.shouldIntercept(webView, request).shouldBlock
        }.onFailure { error ->
            Log.w(TAG, "Full EasyList navigation matching failed for ${request.url}", error)
        }.getOrDefault(false)
    }

    fun performScript(webView: WebView?, url: String?) {
        val activeEngine = engine ?: return
        runCatching {
            activeEngine.performScript(webView, url)
        }.onFailure { error ->
            Log.w(TAG, "Full EasyList page filtering failed for $url", error)
        }
    }
}
