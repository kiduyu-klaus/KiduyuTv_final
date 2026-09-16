package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Probes each [StreamItem] to confirm the upstream is reachable and returns
 * a successful response. A stream is considered "ok" when the server replies
 * with a 2xx status code, either to a HEAD request or, when HEAD is
 * unsupported, to a small Range GET (first 8 KiB).
 *
 * Why this exists:
 *  - Providers frequently return a stale or rotated URL that resolves
 *    successfully at the manifest level but yields 4xx when the player
 *    starts downloading segments. Probing before playback surfaces those
 *    failures up front so the user can pick a working source from the
 *    stream selection dialog.
 *  - The probe runs in parallel per-stream with a short timeout to keep the
 *    "Choose Stream" UI responsive even on slow CDNs.
 */
object StreamValidator {

    private const val TAG = "StreamValidator"

    /** How long any individual probe may run before being aborted. */
    private const val PROBE_TIMEOUT_SECONDS = 6L

    /** Number of bytes requested when we have to fall back from HEAD to GET. */
    private const val RANGE_FALLBACK_BYTES = "8192"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Probe every stream in [streams] in parallel. Each item is mutated
     * in place: [StreamItem.isChecking] is toggled on/off and
     * [StreamItem.isValid] is set to the probe result.
     *
     * @return the same list with validation flags populated, for chaining.
     */
    suspend fun validateAll(streams: List<StreamItem>): List<StreamItem> =
        withContext(Dispatchers.IO) {
            if (streams.isEmpty()) return@withContext streams
            // Mark every entry as "currently being checked" before fanning out
            // so the UI can render a pending state if it happens to be open.
            streams.forEach {
                it.isChecking = true
                it.isFailed = false
            }
            try {
                coroutineScope {
                    streams.map { stream ->
                        async {
                            val ok = probe(stream)
                            stream.isValid = ok
                            stream.isChecking = false
                            Log.i(
                                TAG,
                                "stream ok=${ok} provider=${stream.provider.ifBlank { "?" }} " +
                                    "quality=${stream.quality} url=${stream.url}"
                            )
                        }
                    }.awaitAll()
                }

                streams.filter { it.httpStatusCode == 404 }.forEach { stream ->
                    Log.i(
                        TAG,
                        "Removing stream after HTTP 404 " +
                            "provider=${stream.provider.ifBlank { "?" }} " +
                            "quality=${stream.quality} url=${stream.url}"
                    )
                }
            } catch (error: Throwable) {
                Log.w(TAG, "validateAll failed: ${error.message}")
            } finally {
                streams.forEach { it.isChecking = false }
            }
            streams.filterNot { it.httpStatusCode == 404 }
        }

    /**
     * Run a single probe against [stream]. The probe first tries a HEAD
     * request (cheap: no body transfer) and, if the server rejects HEAD with
     * 405/501, falls back to a tiny Range GET so we still see a real status
     * code without downloading the entire media.
     *
     * The most recent HTTP response code is written back to
     * [StreamItem.httpStatusCode] so callers can distinguish 403 (Cloudflare
     * challenge) from other 4xx/5xx failures without re-issuing the request.
     *
     * [StreamItem.isFailed] is set to `true` when the server replied 2xx but
     * the response headers do not indicate a playable video stream (no
     * recognized Content-Type, no Accept-Ranges, and zero Content-Length).
     */
    private suspend fun probe(stream: StreamItem): Boolean {
        if (stream.url.isBlank()) {
            stream.httpStatusCode = -1
            return false
        }
        val baseBuilder = Request.Builder().url(stream.url)
        stream.headers.forEach { (key, value) ->
            runCatching { baseBuilder.header(key, value) }
        }
        val head = runCatching { client.newCall(baseBuilder.head().build()).execute() }.getOrNull()
        if (head != null) {
            head.use { response ->
                stream.httpStatusCode = response.code
                when {
                    response.isSuccessful -> {
                        if (!hasVideoStreamHeaders(response)) {
                            Log.w(
                                TAG,
                                "probe 2xx but no video stream headers for ${stream.url} " +
                                    "contentType=${response.header("Content-Type")} " +
                                    "contentLength=${response.header("Content-Length")} " +
                                    "acceptRanges=${response.header("Accept-Ranges")}"
                            )
                            stream.isFailed = true
                            return false
                        }
                        return true
                    }
                    response.code == 405 || response.code == 501 -> {
                        // Method not allowed/implemented — retry with a Range GET.
                    }
                    else -> {
                        Log.w(
                            TAG,
                            "HEAD ${stream.url} -> HTTP ${response.code}"
                        )
                        return false
                    }
                }
            }
        }
        val getRequest = baseBuilder
            .get()
            .header("Range", "bytes=0-${RANGE_FALLBACK_BYTES.toInt() - 1}")
            .build()
        return runCatching {
            client.newCall(getRequest).execute().use { response ->
                stream.httpStatusCode = response.code
                if (response.isSuccessful && !hasVideoStreamHeaders(response)) {
                    Log.w(
                        TAG,
                        "probe Range-GET 2xx but no video stream headers for ${stream.url} " +
                            "contentType=${response.header("Content-Type")} " +
                            "contentLength=${response.header("Content-Length")} " +
                            "acceptRanges=${response.header("Accept-Ranges")}"
                    )
                    stream.isFailed = true
                    return@use false
                }
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    /**
     * Returns `true` when [response] carries at least one signal that it is a
     * playable video stream (as opposed to an HTML error page or redirect body):
     *   - Content-Type starting with "video/" (e.g. video/mp4), or matching
     *     application/x-mpegurl or application/vnd.apple.mpegurl
     *   - Accept-Ranges: bytes  (indicates a seekable binary stream)
     *   - Content-Length > 0  (body is not empty)
     *
     * A 2xx response that matches none of these signals is likely a
     * provider-level error page that happens to return HTTP 200.
     */
    private fun hasVideoStreamHeaders(response: okhttp3.Response): Boolean {
        val contentType = response.header("Content-Type", "")
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
            ?: ""

        val isVideoMime = contentType in setOf(
            // Standard video MIME types
            "video/mp4",
            "video/mpeg",
            "video/webm",
            "video/ogg",
            "video/x-matroska",
            "video/x-msvideo",
            "video/x-ms-wmv",
            "video/quicktime",
            "video/x-flv",
            "video/x-m4v",
            "video/3gpp",
            "video/3gpp2",
            "video/mp2t",
            "video/h264",
            "video/h265",
            "video/hevc",
            "video/av1",

            // HLS
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "application/x-mpeg-url",

            // DASH
            "application/dash+xml",

            // Common generic media/container types
            "application/octet-stream",
            "application/mp4",
            "application/matroska",
            "application/x-matroska",
            "application/x-tar"
        ) || contentType.startsWith("video/")

        if (isVideoMime) return true

        // Some servers return a generic Content-Type for a valid media file.
        val acceptRanges = response.header("Accept-Ranges", "")
            ?.trim()
            ?.lowercase()
            ?: ""

        if (acceptRanges == "bytes") return true

        // A positive Content-Length is useful for progressive media, but do
        // not treat it as sufficient by itself for clearly text/HTML/JSON.
        val contentLength = response.header("Content-Length")
            ?.trim()
            ?.toLongOrNull()
            ?: 0L

        val clearlyNonMedia = contentType in setOf(
            "text/html",
            "text/plain",
            "application/json",
            "application/xml",
            "text/xml",
            "text/css",
            "application/javascript",
            "text/javascript"
        )

        if (contentLength > 0L && !clearlyNonMedia) return true

        return false
    }

    /**
     * Issue a one-shot probe of [stream] and return the raw HTTP status code
     * that the server replied with. `null` is returned when the probe cannot
     * complete (DNS failure, connection refused, timeout, etc.).
     *
     * This is a lighter-weight alternative to [validateAll]: it does not
     * mutate [stream.isValid] / [stream.isChecking] because it's intended to
     * be used as a quick pre-flight check on a single candidate (e.g. "is
     * this stream gated by Cloudflare?") right before playback starts.
     */
    suspend fun probeStatus(stream: StreamItem): Int? {
        if (stream.url.isBlank()) return null
        val baseBuilder = Request.Builder().url(stream.url)
        stream.headers.forEach { (key, value) ->
            runCatching { baseBuilder.header(key, value) }
        }
        val head = runCatching { client.newCall(baseBuilder.head().build()).execute() }.getOrNull()
        if (head != null) {
            head.use { response ->
                if (response.code != 405 && response.code != 501) {
                    stream.httpStatusCode = response.code
                    return response.code
                }
            }
        }
        val getRequest = baseBuilder
            .get()
            .header("Range", "bytes=0-${RANGE_FALLBACK_BYTES.toInt() - 1}")
            .build()
        return runCatching {
            client.newCall(getRequest).execute().use { response ->
                stream.httpStatusCode = response.code
                response.code
            }
        }.getOrNull()
    }
}
