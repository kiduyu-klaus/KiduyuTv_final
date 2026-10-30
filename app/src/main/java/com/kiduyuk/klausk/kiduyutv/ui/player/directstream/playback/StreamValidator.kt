package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /** Maximum body read when checking a provider's 429 lock page. */
    private const val LOCK_PAGE_PROBE_BYTES = 128 * 1024

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
     * DASH is the one stream family where a successful HTTP response is not
     * enough. Media3 will parse the response as XML, so a provider endpoint
     * that returns HTML/JSON/progressive media under a stale `dash` hint
     * becomes ERROR_CODE_PARSING_MANIFEST_MALFORMED (3002).
     */
    fun isDashCandidate(stream: StreamItem): Boolean =
        stream.type.equals("dash", ignoreCase = true) ||
            stream.mimeType.equals("application/dash+xml", ignoreCase = true) ||
            stream.url.substringBefore('?').endsWith(".mpd", ignoreCase = true)

    /** Performs a body-aware DASH manifest check before automatic playback. */
    suspend fun validateDashManifest(stream: StreamItem): Boolean =
        withContext(Dispatchers.IO) {
            if (!isDashCandidate(stream)) return@withContext true
            probe(stream)
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
                            val isVidsrc = stream.provider.equals("vidsrc", ignoreCase = true)
                            if (isVidsrc) {
                                // Vidsrc manifests use short-lived, generated
                                // URLs. A preliminary HEAD/Range request can
                                // return 403 before PlayerEngine refreshes the
                                // token, so probing it would incorrectly make
                                // the picker show Unverified/failed.
                                stream.isValid = true
                                stream.isFailed = false
                                stream.isChecking = false
                                stream.hasBeenValidated = true
                                return@async
                            }
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

                streams.filter { it.httpStatusCode == 403 || it.httpStatusCode == 404 }.forEach { stream ->
                    Log.i(
                        TAG,
                        "Keeping probe-rejected stream selectable HTTP ${stream.httpStatusCode} " +
                            "provider=${stream.provider.ifBlank { "?" }} quality=${stream.quality}"
                    )
                }
            } catch (error: Throwable) {
                Log.w(TAG, "validateAll failed: ${error.message}")
            } finally {
                streams.forEach {
                    it.isChecking = false
                    it.hasBeenValidated = true
                }
            }
            // Provider/CDN probe results are advisory. Do not silently remove
            // a backend-returned source: signed URLs can reject a preliminary
            // HEAD/Range request yet succeed when Media3 opens the manifest.
            streams
        }

    /**
     * Run a single probe against [stream]. HLS streams use a GET-first
     * playlist-and-segment check because signed CDNs frequently reject HEAD or
     * return headers that do not describe the actual media. Other streams use
     * HEAD followed by a small Range GET.
     *
     * The most recent HTTP response code is written back to
     * [StreamItem.httpStatusCode] so callers can distinguish 403 (Cloudflare
     * challenge) from other 4xx/5xx failures without re-issuing the request.
     *
     * [StreamItem.isFailed] is set to `true` only when the server replied 2xx
     * with a clearly non-media document body, such as an HTML or JSON error
     * page. Ambiguous CDN headers are kept playable because valid media
     * endpoints often omit Content-Length or use generic text/octet-stream
     * content types.
     */
    private suspend fun probe(stream: StreamItem): Boolean {
        if (stream.url.isBlank()) {
            stream.httpStatusCode = -1
            return false
        }
        if (isHlsCandidate(stream)) return probeHls(stream)
        val isFebboxFid = stream.provider.trim().equals("Febbox FID", ignoreCase = true) ||
            stream.name.trim().equals("Febbox FID", ignoreCase = true)
        val isVidsrc = stream.provider.trim().equals("Vidsrc", ignoreCase = true) ||
            stream.name.trim().equals("Vidsrc", ignoreCase = true)
        val acceptsAnyHttp200 = isFebboxFid || isVidsrc
        val requiresDashManifest = isDashCandidate(stream)
        val baseBuilder = Request.Builder().url(stream.url)
        stream.headers.forEach { (key, value) ->
            runCatching { baseBuilder.header(key, value) }
        }
        val head = runCatching { client.newCall(baseBuilder.head().build()).execute() }.getOrNull()
        if (head != null) {
            head.use { response ->
                stream.httpStatusCode = response.code
                when {
                    acceptsAnyHttp200 && response.code == 200 -> {
                        stream.isFailed = false
                        Log.i(
                            TAG,
                            "${stream.provider.ifBlank { stream.name }} URL returned HTTP 200; " +
                                "marking stream valid"
                        )
                        return true
                    }
                    response.isSuccessful -> {
                        // HEAD cannot prove that a DASH endpoint returned an
                        // MPD. Always follow it with a small GET for DASH.
                        if (!requiresDashManifest && hasVideoStreamHeaders(response)) {
                            return true
                        }
                        Log.i(
                            TAG,
                            "HEAD 2xx inconclusive; retrying ranged GET for ${stream.url} " +
                                "contentType=${response.header("Content-Type")} " +
                                "contentLength=${response.header("Content-Length")} " +
                                "acceptRanges=${response.header("Accept-Ranges")}"
                        )
                    }
                    else -> {
                        Log.w(
                            TAG,
                            "HEAD ${stream.url} -> HTTP ${response.code}; " +
                                "retrying with Range GET"
                        )
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
                if (!response.isSuccessful) {
                    stream.isFailed = true
                    Log.w(
                        TAG,
                        "Range-GET ${stream.url} -> HTTP ${response.code}"
                    )
                    return@use false
                }
                if (acceptsAnyHttp200 && response.code == 200) {
                    stream.isFailed = false
                    Log.i(
                        TAG,
                        "${stream.provider.ifBlank { stream.name }} URL returned HTTP 200; " +
                            "marking stream valid"
                    )
                    return@use true
                }

                val hasHeaders = hasVideoStreamHeaders(response)
                if (requiresDashManifest && !hasDashManifestSignature(response)) {
                    stream.isFailed = true
                    val contentType = response.header("Content-Type").orEmpty()
                    Log.w(
                        TAG,
                        "DASH candidate returned no MPD manifest; rejecting provider=" +
                            "${stream.provider.ifBlank { "?" }} quality=${stream.quality} " +
                            "contentType=$contentType"
                    )
                    return@use false
                }
                val hasSignature = if (hasHeaders) {
                    !requiresDashManifest || hasDashManifestSignature(response)
                } else {
                    hasPlayableMediaSignature(response)
                }

                // A successful CDN response is not necessarily self-describing.
                // Many valid HLS/progressive endpoints return text/plain or
                // application/octet-stream, omit Content-Length, ignore Range,
                // or put a BOM/whitespace before the playlist signature. Media3
                // can still resolve these streams successfully. Only mark a
                // stream failed when the response is clearly an HTML/JSON/text
                // error page; an ambiguous 2xx response should remain playable.
                if (!hasHeaders && !hasSignature && isClearlyNonMediaResponse(response)) {
                    Log.w(
                        TAG,
                        "probe Range-GET 2xx returned a clear non-media response for ${stream.url} " +
                            "contentType=${response.header("Content-Type")}"
                    )
                    stream.isFailed = true
                    return@use false
                }

                if (!hasHeaders && !hasSignature) {
                    Log.i(
                        TAG,
                        "probe Range-GET 2xx has ambiguous media headers; keeping stream playable " +
                            "for ${stream.url} contentType=${response.header("Content-Type")}"
                    )
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun isHlsCandidate(stream: StreamItem): Boolean {
        val value = stream.url.lowercase()
        return stream.type.equals("hls", ignoreCase = true) ||
            stream.mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
            stream.mimeType.equals("application/x-mpegurl", ignoreCase = true) ||
            value.contains(".m3u8") ||
            value.contains("/m3u8-proxy") ||
            value.contains("/m3u8_proxy")
    }

    /**
     * Validate HLS the same way playback consumes it: fetch the playlist, then
     * fetch its first variant or media segment with the provider headers.
     */
    private fun probeHls(stream: StreamItem): Boolean {
        val url = stream.url.toHttpUrlOrNull() ?: return false
        return probeHlsManifest(url, stream.headers, depth = 0, stream = stream)
    }

    private fun probeHlsManifest(
        url: HttpUrl,
        headers: Map<String, String>,
        depth: Int,
        stream: StreamItem
    ): Boolean {
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (key, value) -> runCatching { header(key, value) } }
        }.build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                stream.httpStatusCode = response.code
                if (!response.isSuccessful) {
                    stream.isFailed = true
                    Log.w(TAG, "HLS playlist ${url.host} -> HTTP ${response.code}")
                    return@use false
                }
                val body = response.body?.string().orEmpty()
                if (!body.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                        .startsWith("#EXTM3U")
                ) {
                    stream.isFailed = true
                    Log.w(TAG, "HLS response was not an M3U8 playlist for ${stream.url}")
                    return@use false
                }
                val firstEntry = body.lineSequence()
                    .map(String::trim)
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    ?: run {
                        stream.isFailed = true
                        return@use false
                    }
                val nextUrl = url.resolve(firstEntry) ?: return@use false
                val nestedPlaylist = firstEntry.contains(".m3u8", ignoreCase = true) ||
                    body.contains("#EXT-X-STREAM-INF", ignoreCase = true)
                if (nestedPlaylist && depth < 2) {
                    probeHlsManifest(nextUrl, headers, depth + 1, stream)
                } else {
                    probeHlsSegment(nextUrl, headers, stream)
                }
            }
        }.getOrElse {
            Log.w(TAG, "HLS probe failed for ${stream.url}: ${it.message}")
            false
        }
    }

    private fun probeHlsSegment(
        url: HttpUrl,
        headers: Map<String, String>,
        stream: StreamItem
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-${RANGE_FALLBACK_BYTES.toInt() - 1}")
            .apply { headers.forEach { (key, value) -> runCatching { header(key, value) } } }
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                stream.httpStatusCode = response.code
                if (!response.isSuccessful) {
                    stream.isFailed = true
                    return@use false
                }
                if (response.peekBody(64 * 1024L).bytes().isEmpty()) {
                    stream.isFailed = true
                    return@use false
                }
                stream.isFailed = false
                stream.isValid = true
                Log.i(TAG, "HLS playlist and first segment validated for ${stream.url}")
                true
            }
        }.getOrElse {
            Log.w(TAG, "HLS segment probe failed for ${url.host}: ${it.message}")
            false
        }
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
     * Checks the first bytes returned by a ranged request. This covers CDNs
     * that report application/octet-stream or omit useful media headers while
     * still returning a valid Matroska, MP4, HLS, or DASH resource.
     */
    private fun hasPlayableMediaSignature(response: okhttp3.Response): Boolean {
        val prefix = runCatching { response.peekBody(8 * 1024L).bytes() }
            .getOrDefault(ByteArray(0))
        val count = prefix.size
        if (count <= 0) return false

        val isMatroska = count >= 4 && prefix[0] == 0x1A.toByte() &&
            prefix[1] == 0x45.toByte() &&
            prefix[2] == 0xDF.toByte() &&
            prefix[3] == 0xA3.toByte()
        val isMp4 = count >= 8 && prefix.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
        val textPrefix = prefix.copyOf(count).toString(Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val isHls = textPrefix.startsWith("#EXTM3U", ignoreCase = false)
        val isDash = DASH_MPD_TAG.containsMatchIn(textPrefix)

        val playable = isMatroska || isMp4 || isHls || isDash
        if (playable) {
            Log.i(
                TAG,
                "Range-GET media signature detected: " +
                    "matroska=$isMatroska mp4=$isMp4 hls=$isHls dash=$isDash"
            )
        }
        return playable
    }

    private fun hasDashManifestSignature(response: okhttp3.Response): Boolean {
        val prefix = runCatching { response.peekBody(8 * 1024L).string() }
            .getOrDefault("")
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return DASH_MPD_TAG.containsMatchIn(prefix)
    }

    /**
     * Returns true only when the response looks like an error/document body,
     * rather than merely having incomplete or provider-specific media headers.
     * `peekBody` does not consume the response body, so the normal Media3-style
     * probe remains safe for callers and the response can still be closed.
     */
    private fun isClearlyNonMediaResponse(response: okhttp3.Response): Boolean {
        val contentType = response.header("Content-Type", "")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: ""
        val prefix = runCatching {
            response.peekBody(8 * 1024L)
                .string()
                .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                .lowercase()
        }.getOrDefault("")

        val clearlyTextContentType = contentType in setOf(
            "text/html",
            "application/json",
            "application/xml",
            "text/xml",
            "text/css",
            "application/javascript",
            "text/javascript"
        )
        val clearlyDocumentBody = prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.startsWith("{\"") ||
            prefix.startsWith("[{\"") ||
            prefix.startsWith("{\n")

        return clearlyTextContentType || clearlyDocumentBody
    }

    /**
     * Fetches a small response body for provider error pages. This is used
     * after Media3 reports HTTP 429 to distinguish a CFOK locked-download
     * page from an ordinary rate-limit response.
     */
    suspend fun responseBodyContains(stream: StreamItem, marker: String): Pair<Int?, Boolean> {
        if (stream.url.isBlank()) return null to false
        val requestBuilder = Request.Builder()
            .url(stream.url)
            .get()
            .header("Range", "bytes=0-${LOCK_PAGE_PROBE_BYTES - 1}")
        stream.headers.forEach { (key, value) ->
            runCatching { requestBuilder.header(key, value) }
        }
        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.source()
                val bytes = body?.let { source ->
                    source.request(LOCK_PAGE_PROBE_BYTES.toLong())
                    source.buffer.clone().readByteArray()
                } ?: ByteArray(0)
                response.code to bytes.toString(Charsets.UTF_8).contains(marker, ignoreCase = false)
            }
        }.getOrElse { null to false }
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

    private val DASH_MPD_TAG = Regex("<(?:(?:[A-Za-z_][\\w.-]*):)?MPD\\b", RegexOption.IGNORE_CASE)
}
