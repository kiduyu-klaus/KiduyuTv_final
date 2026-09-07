package com.kiduyuk.klausk.kiduyutv.ui.player.webviewsniffer

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import java.util.concurrent.atomic.AtomicBoolean

data class SniffedStream(
    val url: String,
    val headers: Map<String, String>,
    val cookie: String?,
    val type: String,
    val mimeType: String
)

data class SniffedSubtitle(
    val url: String,
    val headers: Map<String, String>,
    val cookie: String?,
    val mimeType: String
)

/**
 * Detects manifest and direct-media requests made by a provider WebView.
 * A sniffer instance captures only the first playable request so manifests,
 * segments, and retries cannot launch multiple native players.
 */
class WebViewStreamSniffer(
    private val onStreamCaptured: (SniffedStream) -> Unit,
    private val onSubtitleCaptured: (SniffedSubtitle) -> Unit,
    private val shouldIgnoreStream: (String) -> Boolean = { false }
) {
    private val captured = AtomicBoolean(false)
    private val capturedSubtitles = mutableSetOf<String>()

    fun inspect(request: WebResourceRequest?) {
        val requestValue = request ?: return
        if (requestValue.isForMainFrame) return
        val url = requestValue.url?.toString().orEmpty()
        if (url.contains(IGNORED_DEMO_VIDEO, ignoreCase = true)) return
        val headers = requestHeaders(requestValue)
        val cookie = cookieFor(url)
        // Detect playable media before subtitles. Some provider URLs contain
        // format-like query parameters in signed tokens; an HLS manifest must
        // never be handed to Media3 as a WebVTT subtitle.
        val mediaType = detectMediaType(url)
        if (mediaType != null) {
            if (shouldIgnoreStream(url)) return
            if (!captured.compareAndSet(false, true)) return

            onStreamCaptured(
                SniffedStream(
                    url = url,
                    headers = headers,
                    cookie = cookie,
                    type = mediaType.first,
                    mimeType = mediaType.second
                )
            )
            return
        }
        detectSubtitleMimeType(url)?.let { mimeType ->
            synchronized(capturedSubtitles) {
                if (!capturedSubtitles.add(url)) return
            }
            onSubtitleCaptured(SniffedSubtitle(url, headers, cookie, mimeType))
            return
        }
    }

    private fun requestHeaders(request: WebResourceRequest): Map<String, String> =
        LinkedHashMap<String, String>().apply {
            putAll(request.requestHeaders.orEmpty())
        }

    private fun cookieFor(url: String): String? = runCatching {
        CookieManager.getInstance().getCookie(url)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun detectSubtitleMimeType(url: String): String? {
        val normalized = url.lowercase()
        // An HLS URL can contain misleading query text in signed parameters;
        // never classify it as a subtitle request.
        if (normalized.contains(".m3u8") || normalized.contains("/m3u8-proxy")) {
            return null
        }
        val path = normalized.substringBefore('?')
        return when {
            path.endsWith(".vtt") || ".vtt" in normalized || "format=vtt" in normalized ->
                "text/vtt"
            path.endsWith(".srt") || ".srt" in normalized || "format=srt" in normalized ->
                "application/x-subrip"
            else -> null
        }
    }

    private fun detectMediaType(url: String): Pair<String, String>? {
        val normalized = url.lowercase()
        return when {
            ".m3u8" in normalized || "/m3u8-proxy" in normalized ->
                "hls" to "application/vnd.apple.mpegurl"
            ".mpd" in normalized ->
                "dash" to "application/dash+xml"
            normalized.substringBefore('?').endsWith(".mp4") ->
                "direct" to "video/mp4"
            normalized.substringBefore('?').endsWith(".mkv") ->
                "direct" to "video/x-matroska"
            normalized.substringBefore('?').endsWith(".webm") ->
                "direct" to "video/webm"
            else -> null
        }
    }

    private companion object {
        const val IGNORED_DEMO_VIDEO = "demo-video.mp4"
    }
}
