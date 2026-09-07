package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api

import android.net.Uri
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamResponse
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProvidersApiHttpException(val statusCode: Int) : IOException("Providers API HTTP $statusCode")

class ProvidersBackendUnavailableException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Minimal synchronous client for the local kiduyuTv_providers
 * (TMDB-Embed-API) server. Call every public method from Dispatchers.IO.
 *
 * The server is configured with `enableProxy=false`, so stream URLs in the
 * response point **directly** to the upstream CDN. Each stream object
 * carries its own `headers` (typically `Referer` + `User-Agent`) that the
 * player must attach when fetching the manifest and segments.
 *
 * Endpoints mounted at the KiduyuTV providers backend:
 *   - GET api/streams/{type}/{tmdbId}?token=...[&season=&episode=]         (aggregate)
 *   - GET api/streams/{provider}/{type}/{tmdbId}?token=...[&season=&episode=]  (single)
 *
 * Where:
 *   - `type` is "movie" or "series"
 *   - `provider` is one of the lowercased provider keys recognised by the
 *     server (see [com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamCatalog])
 *   - `season` / `episode` are required only when `type == "series"`
 */
object ProvidersApi {

    private const val TAG = "KiduyuLiteProvider"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val PROVIDERS_READ_TIMEOUT_MS = 30_000
    private const val BACKEND_HEALTH_TIMEOUT_SECONDS = 10L

    // Aggregate stream requests wait for several enabled providers on the
    // backend. Some valid scrapers need well over 30 seconds, so keep the
    // request bounded but do not fail while those providers are still working.
    private const val STREAMS_READ_TIMEOUT_MS = 180_000

    private const val baseUrl = "https://sflatransport.com/kiduyuTv_providers"
    private const val streamApiToken = BuildConfig.STREAM_API_TOKEN

    private val backendHealthClient by lazy {
        OkHttpClient.Builder()
            // callTimeout bounds DNS, connection, TLS, request and response as
            // one operation. A pair of HttpURLConnection timeouts could take
            // up to twice the requested health-check limit.
            .callTimeout(BACKEND_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(BACKEND_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BACKEND_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Verifies that the configured providers backend is reachable and is the
     * expected KiduyuTV service. This deliberately uses a short timeout so a
     * dead host does not make the player wait for each provider request to
     * exhaust its much longer stream-extraction timeout.
     *
     * Call from Dispatchers.IO.
     */
    fun requireBackendAvailable() {
        val urlString = "$baseUrl/api/health"
        Log.i(TAG, "Checking providers backend health")
        val request = Request.Builder()
            .url(urlString)
            .get()
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Accept", "application/json")
            .header("User-Agent", "KiduyuTVLite/1.0 (Android)")
            .build()
        try {
            backendHealthClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ProvidersBackendUnavailableException(
                        "Providers backend health check returned HTTP ${response.code}"
                    )
                }

                val body = response.body?.string().orEmpty()
                val isHealthy = runCatching { JSONObject(body).optBoolean("ok", false) }
                    .getOrDefault(false)
                if (!isHealthy) {
                    throw ProvidersBackendUnavailableException(
                        "Providers backend returned an invalid health response"
                    )
                }
            }
            Log.i(TAG, "Providers backend health check passed")
        } catch (error: ProvidersBackendUnavailableException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ProvidersBackendUnavailableException(
                "Providers backend health check timed out after 10 seconds",
                error
            )
        } catch (error: Exception) {
            throw ProvidersBackendUnavailableException(
                "Providers backend is unavailable",
                error
            )
        }
    }

    /**
     * Returns the server-side keys of providers currently enabled by
     * `/api/providers`. Disabled entries are deliberately omitted.
     */
    fun enabledProviderNames(): List<String> {
        val urlString = "$baseUrl/api/providers"
        Log.i(TAG, "GET $urlString")
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = PROVIDERS_READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KiduyuTVLite/1.0 (Android)")
        }
        HttpCookieStore.applyTo(connection, urlString)
        return try {
            val status = connection.responseCode
            HttpCookieStore.captureFrom(connection, urlString)
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ProvidersApiHttpException(status)

            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                throw IOException("Providers API returned success=false")
            }
            val providers = json.optJSONArray("providers")
                ?: throw IOException("Providers API response has no providers array")
            buildList {
                for (index in 0 until providers.length()) {
                    val item = providers.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim().lowercase()
                    if (item.optBoolean("enabled", false) && name.isNotBlank()) add(name)
                }
            }.distinct().also {
                Log.i(TAG, "Enabled providers (${it.size}): ${it.joinToString()}")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun streams(
        type: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        provider: String? = null
    ): StreamResponse {
        require(type == "movie" || type == "series") { "invalid type: $type" }
        require(tmdbId > 0) { "invalid tmdbId: $tmdbId" }

        val pathSegment = if (provider.isNullOrBlank()) {
            "api/streams/$type/$tmdbId"
        } else {
            "api/streams/${provider.lowercase()}/$type/$tmdbId"
        }
        val urlBuilder = Uri.parse("$baseUrl/$pathSegment").buildUpon()
            .appendQueryParameter("token", streamApiToken)
        if (type == "series") {
            season?.let { urlBuilder.appendQueryParameter("season", it.toString()) }
            episode?.let { urlBuilder.appendQueryParameter("episode", it.toString()) }
        }
        val urlString = urlBuilder.build().toString()
        val providerLabel = provider?.takeIf { it.isNotBlank() } ?: "aggregate"
        Log.i(
            TAG,
            "Request provider=$providerLabel type=$type tmdbId=$tmdbId " +
                "season=${season ?: "-"} episode=${episode ?: "-"}"
        )
        // Do not log the complete URL because its query contains the bearer token.
        Log.i(TAG, "GET $baseUrl/$pathSegment (authenticated)")

        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = STREAMS_READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KiduyuTVLite/1.0 (Android)")
        }
        HttpCookieStore.applyTo(connection, urlString)

        return try {
            val status = connection.responseCode
            HttpCookieStore.captureFrom(connection, urlString)
            val body = (if (status in 200..299) connection.inputStream
                        else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                Log.w(TAG, "HTTP $status from providers API (provider=$providerLabel)")
                Log.w(TAG, "Body[0..200]=${body.take(200)}")
                throw ProvidersApiHttpException(status)
            }
            val response = parse(JSONObject(body))
            Log.i(
                TAG,
                "Response provider=$providerLabel tmdbId=${response.tmdbId} " +
                    "imdbId=${response.imdbId ?: "-"} count=${response.streams.size}"
            )
            response.streams.forEachIndexed { index, item ->
                val scheme = item.url.substringBefore(':').uppercase()
                Log.i(
                    TAG,
                    "  stream[$index] provider=${item.provider.ifBlank { "?" }} " +
                        "quality=${item.quality} scheme=$scheme " +
                        "host=${runCatching { android.net.Uri.parse(item.url).host }.getOrNull() ?: "?"} " +
                        "headers=${item.headers.size} url=${item.url}"
                )
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses the response JSON. Note the server sends `tmdbId` as a
     * string (e.g. `"550"`) even though it round-trips an int on the way
     * in, so we coerce it. `imdbId` may be JSON `null`, which `optString`
     * surfaces as the literal `"null"` — we treat that as absent.
     */
    private fun parse(json: JSONObject): StreamResponse {
        val tmdbId = json.optString("tmdbId")
            .toIntOrNull()
            ?: json.optInt("tmdbId", 0)
        val imdbId = json.optString("imdbId")
            .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        val arr = json.optJSONArray("streams") ?: return StreamResponse(tmdbId, imdbId, emptyList())
        val items = buildList {
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val url = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                val provider = s.optString("provider", "")
                    .ifBlank { json.optString("provider", "") }
                val type = s.optString("type", "")
                val isMovieBoxDirect = provider.equals("MovieBox", ignoreCase = true) &&
                    type.equals("direct", ignoreCase = true)
                if (isMovieBoxDirect) continue
                val isVixsrcHls = provider.equals("vixsrc", ignoreCase = true) &&
                    url.contains("vixsrc.to/playlist/", ignoreCase = true)
                val normalizedType = type.ifBlank {
                    if (isVixsrcHls) "hls" else ""
                }
                val mimeType = s.optString(
                    "mimeType",
                    s.optString("contentType", "")
                ).ifBlank {
                    if (isVixsrcHls) HLS_MIME_TYPE else ""
                }
                val headers = s.optJSONObject("headers")?.let { h ->
                    val map = LinkedHashMap<String, String>(h.length())
                    h.keys().forEach { k -> map[k] = h.optString(k) }
                    map
                } ?: linkedMapOf()
                if (provider.lowercase(Locale.ROOT) in PROVIDERS_WITHOUT_SOURCE_HEADERS) {
                    headers.keys.removeAll { headerName ->
                        headerName.equals("Origin", ignoreCase = true) ||
                            headerName.equals("Referer", ignoreCase = true) ||
                            headerName.equals("Referrer", ignoreCase = true)
                    }
                }
                val cookie = when {
                    s.optJSONObject("cookies") != null -> {
                        val cookies = s.getJSONObject("cookies")
                        buildList {
                            cookies.keys().forEach { name ->
                                val value = cookies.optString(name)
                                if (name.isNotBlank() && value.isNotBlank()) add("$name=$value")
                            }
                        }.joinToString("; ")
                    }
                    s.optString("cookie").isNotBlank() -> s.optString("cookie")
                    s.optString("cookies").isNotBlank() -> s.optString("cookies")
                    else -> ""
                }
                if (cookie.isNotBlank() && headers.keys.none { it.equals("Cookie", true) }) {
                    headers["Cookie"] = cookie
                }
                add(
                    StreamItem(
                        title = s.optString("title", s.optString("name", "Stream")),
                        name = s.optString("name"),
                        url = url,
                        quality = s.optString("quality", "Auto"),
                        provider = provider,
                        type = normalizedType,
                        mimeType = mimeType,
                        headers = headers
                    )
                )
            }
        }
        return StreamResponse(tmdbId, imdbId, items)
    }

    private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"

    private val PROVIDERS_WITHOUT_SOURCE_HEADERS = setOf(
        "cinesrc_provider",
        "uhdmovies",
        "vegamovies",
        "vidlove",
        "webstreamr"
    )
}
