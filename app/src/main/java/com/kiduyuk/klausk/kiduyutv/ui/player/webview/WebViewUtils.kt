package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.WebView
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager

object WebViewUtils {
    private const val TAG = "WebViewUtils"

    /**
     * Creates and configures a WebView instance optimized for the current device.
     */
    fun createWebView(context: Context, isFireTV: Boolean): WebView {
        val webView = WebView(context)

        // Check if hardware acceleration is available
        val isHardwareAccelerated =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

        if (isFireTV) {
            val isAmazonChromium = isAmazonChromiumAvailable()

            Log.i(TAG, "[WebView] Fire TV detected")
            Log.i(TAG, "[WebView] Amazon Chromium WebView: $isAmazonChromium")
            Log.i(TAG, "[WebView] Hardware acceleration available: $isHardwareAccelerated")

            if (isHardwareAccelerated) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                Log.i(TAG, "[WebView] Fire TV: hardware acceleration enabled")
            } else {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.w(TAG, "[WebView] Fire TV: hardware acceleration unavailable, using software rendering")
            }

            if (isAmazonChromium) {
                Log.i(TAG, "[WebView] ✅ Running on Amazon Chromium WebView (com.amazon.webview.chromium)")
            } else {
                Log.w(TAG, "[WebView] ⚠️ Amazon Chromium WebView not available, using fallback WebView")
            }
        } else {
            Log.i(TAG, "[WebView] Non-Fire TV device")
            Log.i(TAG, "[WebView] Hardware acceleration available: $isHardwareAccelerated")

            if (isHardwareAccelerated) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                Log.i(TAG, "[WebView] Hardware acceleration enabled")
            } else {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.w(TAG, "[WebView] Hardware acceleration unavailable, using software rendering")
            }
        }

        // Log detailed WebView implementation info for debugging
        logWebViewInfo(webView)

        return webView
    }

    /**
     * Checks if Amazon's Chromium-based WebView is available on this device.
     */
    fun isAmazonChromiumAvailable(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return try {
                val webViewPackage = WebView.getCurrentWebViewPackage()
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
        } else {
            Log.w(TAG, "[WebView] SDK version ${android.os.Build.VERSION.SDK_INT} < 26, cannot detect WebView package")
            return false
        }
    }

    /**
     * Logs comprehensive information about the current WebView implementation.
     */
    fun logWebViewInfo(webView: WebView) {
        try {
            val webViewClass = webView.javaClass.name
            Log.i(TAG, "[WebView] Implementation class: $webViewClass")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val webViewPackage = WebView.getCurrentWebViewPackage()

                if (webViewPackage != null) {
                    val packageName = webViewPackage.packageName
                    val versionName = webViewPackage.versionName ?: "unknown"
                    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
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
                        packageName == "com.amazon.webview.chromium" -> {
                            Log.i(TAG, "[WebView] ✅ Amazon Chromium WebView - optimized for Fire TV/Fire tablets")
                        }
                        packageName == "com.google.android.webview" -> {
                            Log.i(TAG, "[WebView] ℹ️ Google WebView - standard AOSP implementation")
                        }
                        packageName == "com.android.chrome" -> {
                            Log.i(TAG, "[WebView] ℹ️ Chrome WebView - using Chrome as WebView provider")
                        }
                        else -> {
                            Log.i(TAG, "[WebView] ℹ️ WebView provider: $packageName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[WebView] Error logging WebView implementation details: ${e.message}")
        }
    }

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
}
