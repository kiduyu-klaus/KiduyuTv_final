package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager

object WebViewUtils {
    private const val TAG = "WebViewUtils"

    /**
     * Creates and configures a WebView instance optimized for the current device.
     */
    fun createWebView(context: Context, isFireTV: Boolean): WebView {
    val webView = WebView(context)

    // Set the layer type immediately at creation, before the first render happens.
    // Switching layer types later (e.g. in onPageFinished) forces Android to recreate
    // the rendering layer after the page has already drawn once, which just causes an
    // unnecessary redraw/flicker without fixing anything that went wrong on that first frame.
    //
    // Fire TV devices skip hardware acceleration entirely and always use software
    // rendering, regardless of whether it's available, due to compatibility issues
    // with Amazon's Chromium WebView.
    if (isFireTV) {
        //webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        Log.i(TAG, "[WebView] Fire TV device — forcing software rendering (hardware acceleration skipped)")
    } else {
        val isHardwareAccelerated = isHardwareAccelerationAvailable(context)
        if (isHardwareAccelerated) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            Log.i(TAG, "[WebView] Hardware acceleration enabled")
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            Log.w(TAG, "[WebView] Hardware acceleration unavailable, using software rendering")
        }
    }

    if (isFireTV) {
        val isAmazonChromium = isAmazonChromiumAvailable(context)

        Log.i(TAG, "[WebView] Fire TV detected")
        Log.i(TAG, "[WebView] Amazon Chromium WebView: $isAmazonChromium")

        if (isAmazonChromium) {
            Log.w(TAG, "[WebView] ⚠️ Running on Amazon Chromium WebView (com.amazon.webview.chromium). For best compatibility, install/activate com.google.android.webview.")
        } else {
            Log.i(TAG, "[WebView] ✅ Not using Amazon Chromium WebView")
        }
    } else {
        Log.i(TAG, "[WebView] Non-Fire TV device")
    }

    // Log detailed WebView implementation info for debugging
    logWebViewInfo(webView)

    return webView
}
    /**
     * Returns true if hardware-accelerated rendering is available for this application.
     */
    fun isHardwareAccelerationAvailable(context: Context): Boolean {
        return context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0
    }

    /**
     * Checks if Amazon's Chromium-based WebView is available on this device.
     *
     * Uses [WebViewCompat.getCurrentWebViewPackage] which is the modern, backward-compatible
     * replacement for the platform-level [WebView.getCurrentWebViewPackage] call (deprecated in
     * API 26 and inaccessible on some newer WebView implementations).
     */
    fun isAmazonChromiumAvailable(context: Context): Boolean {
        return try {
            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context)
            val isAmazon = webViewPackage?.packageName == "com.amazon.webview.chromium"

            if (isAmazon) {
                Log.d(TAG, "[WebView] Amazon Chromium WebView package detected")
            } else {
                Log.d(TAG, "[WebView] Current WebView package: ${webViewPackage?.packageName ?: "unknown"}")
            }

            isAmazon
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Error checking WebView package: ${e.message}")
            false
        }
    }

    /**
     * Logs comprehensive information about the current WebView implementation.
     *
     * Uses [WebViewCompat.getCurrentWebViewPackage] to retrieve the active WebView provider
     * package on all supported API levels.
     */
    fun logWebViewInfo(webView: WebView) {
        try {
            val webViewClass = webView.javaClass.name
            Log.i(TAG, "[WebView] Implementation class: $webViewClass")

            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(webView.context)

            if (webViewPackage != null) {
                val packageName = webViewPackage.packageName
                val versionName = webViewPackage.versionName ?: "unknown"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    webViewPackage.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    webViewPackage.versionCode.toString()
                }

                Log.i(TAG, "[WebView] Package: $packageName")
                Log.i(TAG, "[WebView] Version: $versionName (code: $versionCode)")
                Log.i(TAG, "[WebView] First installed: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(webViewPackage.firstInstallTime))}")
                Log.i(TAG, "[WebView] Last updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(webViewPackage.lastUpdateTime))}")

                // Identify the WebView provider
                when {
                    packageName == GOOGLE_WEBVIEW_PACKAGE -> {
                        Log.i(TAG, "[WebView] ✅ Google WebView (com.google.android.webview) - standard AOSP implementation")
                    }
                    packageName == AMAZON_WEBVIEW_PACKAGE -> {
                        Log.w(TAG, "[WebView] ⚠️ Amazon Chromium WebView detected. For best compatibility install com.google.android.webview and disable this package.")
                    }
                    packageName == CHROME_WEBVIEW_PACKAGE -> {
                        Log.i(TAG, "[WebView] ℹ️ Chrome WebView - using Chrome as WebView provider")
                    }
                    else -> {
                        Log.i(TAG, "[WebView] ℹ️ WebView provider: $packageName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Error logging WebView implementation details: ${e.message}")
        }
    }

    /**
     * Returns true if the active WebView provider is Google's official AOSP package.
     * Useful for logging and for callers that need to flag non-standard WebView
     * implementations (such as Amazon's Chromium on Fire TV).
     */
    fun isGoogleWebView(context: Context): Boolean {
        return try {
            val pkg = WebViewCompat.getCurrentWebViewPackage(context)
            pkg?.packageName == GOOGLE_WEBVIEW_PACKAGE
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Error querying Google WebView: ${e.message}")
            false
        }
    }

    /** Package name of Google's official AOSP WebView provider. */
    const val GOOGLE_WEBVIEW_PACKAGE = "com.google.android.webview"

    /** Package name of Amazon's Chromium-based WebView (shipped on Fire TV). */
    const val AMAZON_WEBVIEW_PACKAGE = "com.amazon.webview.chromium"

    /** Package name of Chrome (used as a WebView provider on some devices). */
    const val CHROME_WEBVIEW_PACKAGE = "com.android.chrome"

    /**
     * Detects the stream provider name from a given URL.
     */
    fun detectProviderFromUrl(url: String): String {
        val urlHost = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            return "VidLink"
        }

        StreamProviderManager.providers.forEach { provider ->
            try {
                val providerBaseUrl = StreamProviderManager.getBaseUrl(provider.name)
                    .lowercase()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removeSuffix("/")

                if (urlHost.contains(providerBaseUrl) || urlHost.endsWith(".$providerBaseUrl")) {
                    return provider.name
                }
            } catch (e: Exception) {
                // Continue to next provider
            }
        }

        return "VidLink"
    }

    /**
     * Extracts (tmdbId, season, episode) from a stream URL by matching it against
     * the TV URL template of the given provider. Providers structure their URLs
     * differently — path segments, dash-combined segments, or query params — so
     * this builds a regex directly from each provider's tvUrlTemplate rather than
     * assuming a single fixed shape.
     */
    fun parseEpisodeFromUrl(url: String, providerName: String): Triple<Int, Int, Int>? {
        val provider = StreamProviderManager.getProvider(providerName) ?: return null

        val pattern = Regex.escape(provider.tvUrlTemplate).replace("%d", "\\E(\\d+)\\Q")

        return try {
            val match = Regex(pattern).find(url) ?: return null
            val (tmdbId, season, episode) = match.destructured
            Triple(tmdbId.toInt(), season.toInt(), episode.toInt())
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Failed to parse episode from URL for $providerName: ${e.message}")
            null
        }
    }
}