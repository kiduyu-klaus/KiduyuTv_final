package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api

import android.net.Uri
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.network.AndroidApp
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamSegment
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamResponse
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.SubtitleItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.normalizeStreamLanguage
import com.kiduyuk.klausk.kiduyutv.util.UrlUtils
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
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
    private const val ANIMATION_GENRE_ID = 16
    private const val MOVIES_TV_CATEGORY = "moviestv"
    private const val ANIME_CATEGORY = "anime"
    private const val ANIME_STREAM_TYPE = "anime"

    // Aggregate stream requests wait for several enabled providers on the
    // backend. Some valid scrapers need well over 30 seconds, so keep the
    // request bounded but do not fail while those providers are still working.
    private const val STREAMS_READ_TIMEOUT_MS = 180_000

    private const val streamApiToken = BuildConfig.STREAM_API_TOKEN

    /** Resolve the selected endpoint on every request so Settings changes apply immediately. */
    private fun currentBaseUrl(): String = runCatching {
        SettingsManager(AndroidApp.instance).getDirectStreamEndpoint()
    }.getOrDefault(SettingsManager.FALLBACK_DIRECT_STREAM_ENDPOINT)

    private fun resolveBaseUrl(endpoint: String?): String =
        endpoint?.trimEnd('/')?.takeIf { it in SettingsManager.DIRECT_STREAM_ENDPOINTS }
            ?: currentBaseUrl()

    /** The Cloudflare worker has a compatible payload but a different route layout. */
    private fun isCloudflareStreamWorker(endpoint: String): Boolean =
        runCatching {
            Uri.parse(endpoint).host.equals(CLOUDFLARE_STREAM_WORKER_HOST, ignoreCase = true)
        }.getOrDefault(false)

    data class ProviderRoute(
        val providerNames: List<String>,
        /** The backend path type: movie, series, or anime. */
        val streamType: String,
        val category: String
    )

    /** An enabled provider as advertised by the live backend catalog. */
    data class EnabledProvider(
        val name: String,
        val categories: List<String>
    ) {
        val category: String
            get() = categories.firstOrNull().orEmpty()
    }

    private data class MediaClassification(
        val isAnime: Boolean,
        val originalLanguage: String?
    )

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
    fun requireBackendAvailable(endpoint: String? = null) {
        val baseUrl = resolveBaseUrl(endpoint)
        val healthPath = if (isCloudflareStreamWorker(baseUrl)) "health" else "api/health"
        val urlString = "$baseUrl/$healthPath"
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
     * Reads the authoritative provider catalog from the backend. Provider
     * categories are kept backend-driven so new provider assignments apply
     * without requiring an app release.
     */
    private fun enabledProviderCatalog(endpoint: String? = null): List<EnabledProvider> {
        val baseUrl = resolveBaseUrl(endpoint)
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
            if (!json.optBoolean("success", false)) throw IOException("Providers API returned success=false")
            val providers = json.optJSONArray("providers")
                ?: throw IOException("Providers API response has no providers array")
            val catalog = buildList {
                for (index in 0 until providers.length()) {
                    val item = providers.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim().lowercase(Locale.ROOT)
                    val legacyCategory = item.optString("category").trim()
                    val categories = buildList {
                        item.optJSONArray("categories")?.let { categoryArray ->
                            for (categoryIndex in 0 until categoryArray.length()) {
                                categoryArray.optString(categoryIndex).trim()
                                    .takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                        if (legacyCategory.isNotBlank()) add(legacyCategory)
                    }.map(::normalizeCategory).distinct()
                    if (item.optBoolean("enabled", false) && name.isNotBlank() && categories.isNotEmpty()) {
                        add(EnabledProvider(name, categories))
                    }
                }
            }.groupBy { it.name }.map { (name, entries) ->
                EnabledProvider(name, entries.flatMap { it.categories }.distinct())
            }.let { entries ->
                val movieBox = entries.firstOrNull { it.name.equals("moviebox", ignoreCase = true) }
                if (movieBox == null) {
                    entries + EnabledProvider("moviebox", listOf(MOVIES_TV_CATEGORY, ANIME_CATEGORY))
                } else {
                    entries.map { provider ->
                        if (provider.name.equals("moviebox", ignoreCase = true)) {
                            provider.copy(categories = (provider.categories + MOVIES_TV_CATEGORY + ANIME_CATEGORY)
                                .map(::normalizeCategory).distinct())
                        } else provider
                    }
                }
            }
            Log.i(TAG, "Enabled providers (${catalog.size}): " +
                catalog.joinToString { "${it.name}:${it.categories.joinToString("/")}" })
            catalog
        } finally {
            connection.disconnect()
        }
    }

    fun enabledProviders(endpoint: String? = null): List<EnabledProvider> = enabledProviderCatalog(endpoint)

    suspend fun providerRouteForMedia(type: String, tmdbId: Int, endpoint: String? = null): ProviderRoute {
        require(type == "movie" || type == "series") { "invalid type: $type" }
        require(tmdbId > 0) { "invalid tmdbId: $tmdbId" }
        val baseUrl = resolveBaseUrl(endpoint)
        val classification = classifyMedia(type, tmdbId)
        if (classification.isAnime && isCloudflareStreamWorker(baseUrl)) {
            Log.i(TAG, "Cloudflare Stream Worker does not expose an anime route; skipping anime provider requests")
            return ProviderRoute(emptyList(), ANIME_STREAM_TYPE, ANIME_CATEGORY)
        }
        val category = if (classification.isAnime) ANIME_CATEGORY else MOVIES_TV_CATEGORY
        val selected = enabledProviderCatalog(endpoint)
            .filter { provider -> provider.categories.any { normalizeCategory(it) == normalizeCategory(category) } }
            .map { it.name }
        Log.i(TAG, "Provider selection type=$type tmdbId=$tmdbId originalLanguage=" +
            "${classification.originalLanguage ?: "-"} anime=${classification.isAnime} " +
            "category=$category selected=${selected.joinToString()}")
        return ProviderRoute(selected, if (classification.isAnime) ANIME_STREAM_TYPE else type, category)
    }

    private suspend fun classifyMedia(type: String, tmdbId: Int): MediaClassification = runCatching {
        val (genres, originalLanguage) = if (type == "movie") {
            ApiClient.tmdbApiService.getMovieDetail(tmdbId).let { it.genres to it.originalLanguage }
        } else {
            ApiClient.tmdbApiService.getTvShowDetail(tmdbId).let { it.genres to it.originalLanguage }
        }
        val normalizedLanguage = originalLanguage?.lowercase(Locale.ROOT)
        MediaClassification(
            isAnime = genres.orEmpty().any { it.id == ANIMATION_GENRE_ID } &&
                normalizedLanguage in ANIME_ORIGINAL_LANGUAGES,
            originalLanguage = normalizedLanguage
        )
    }.onFailure { error -> Log.w(TAG, "Could not read TMDB metadata for $type/$tmdbId; using MoviesTv providers", error) }
        .getOrDefault(MediaClassification(false, null))

    private fun normalizeCategory(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun streams(
        type: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        provider: String? = null,
        endpoint: String? = null
    ): StreamResponse {
        require(type == "movie" || type == "series" || type == ANIME_STREAM_TYPE) {
            "invalid type: $type"
        }
        require(tmdbId > 0) { "invalid tmdbId: $tmdbId" }

        val baseUrl = resolveBaseUrl(endpoint)
        val cloudflareWorker = isCloudflareStreamWorker(baseUrl)

        val pathSegment = if (provider.isNullOrBlank()) {
            if (cloudflareWorker) {
                "api/streams/all/$type/$tmdbId"
            } else {
                "api/streams/$type/$tmdbId"
            }
        } else {
            if (cloudflareWorker) {
                "api/streams/provider/${provider.lowercase()}/$type/$tmdbId"
            } else {
                "api/streams/${provider.lowercase()}/$type/$tmdbId"
            }
        }
        val urlBuilder = Uri.parse("$baseUrl/$pathSegment").buildUpon()
            .appendQueryParameter("token", streamApiToken)
        if (type == "series" || type == ANIME_STREAM_TYPE) {
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
            val response = parse(
                json = JSONObject(body),
                acceptsAnimeSegments = type == ANIME_STREAM_TYPE
            )
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
    private fun parse(
        json: JSONObject,
        acceptsAnimeSegments: Boolean = false
    ): StreamResponse {
        val tmdbId = json.optString("tmdbId")
            .toIntOrNull()
            ?: json.optInt("tmdbId", 0).takeIf { it > 0 }
            ?: json.optString("id").toIntOrNull()
            ?: json.optInt("id", 0)
        val imdbId = json.optString("imdbId")
            .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        val arr = json.optJSONArray("streams") ?: return StreamResponse(tmdbId, imdbId, emptyList())
        val items = buildList {
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val url = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                val provider = s.optString("provider", "")
                    .ifBlank { json.optString("provider", "") }
                val isVidsrc = provider.equals("vidsrc", ignoreCase = true)
                val type = s.optString("type", "")
                val isVixsrcHls = provider.equals("vixsrc", ignoreCase = true) &&
                    url.contains("vixsrc.to/playlist/", ignoreCase = true)
                // Some providers disguise HLS playlists with a `.gif` URL
                // suffix. The response is still an M3U8 playlist, so force
                // Media3 onto its HLS path instead of treating it as a GIF
                // or progressive stream.
                val isGifPlaylist = url.substringBefore('?').endsWith(".gif", ignoreCase = true)
                // A number of CDNs return a pathless/signed HLS URL with
                // ".m3u8" only in its query string (for example
                // "?t.m3u8") and falsely advertise it as image/jpeg. The
                // URL is a stronger signal here: force HlsMediaSource and
                // its MIME type before Media3 sees the bad HTTP header.
                val isHlsUrlHint = url.contains(".m3u8", ignoreCase = true) ||
                    url.contains("/m3u8-proxy", ignoreCase = true) ||
                    url.contains("/m3u8_proxy", ignoreCase = true)
                val rawMimeType = s.optString(
                    "mimeType",
                    s.optString("contentType", "")
                )
                val normalizedType = when {
                    isVixsrcHls || isGifPlaylist || isHlsUrlHint -> "hls"
                    else -> type
                }
                val mimeType = when {
                    isVixsrcHls || isGifPlaylist || isHlsUrlHint -> HLS_MIME_TYPE
                    else -> rawMimeType
                }
                if (isHlsUrlHint && rawMimeType.equals("image/jpeg", ignoreCase = true)) {
                    Log.i(TAG, "Overriding image/jpeg MIME hint with HLS for provider=${provider.ifBlank { "?" }}")
                }
                val headers = s.optJSONObject("headers")?.let { h ->
                    val map = LinkedHashMap<String, String>(h.length())
                    h.keys().forEach { k -> map[k] = h.optString(k) }
                    map
                } ?: linkedMapOf()
                if (provider.equals("vidlink", ignoreCase = true) &&
                    !hasVidlinkSignature(url, headers)
                ) {
                    Log.w(
                        TAG,
                        "Dropping unsigned Vidlink stream " +
                            "quality=${s.optString("quality", "?")} " +
                            "host=${runCatching { Uri.parse(url).host }.getOrNull() ?: "?"}"
                    )
                    continue
                }
                if (isGoogleusercontentHost(url)) {
                    // These are controlled by OkHttp/Media3. Forwarding
                    // provider-supplied values can break signed direct-file
                    // requests, range seeking, or connection reuse.
                    headers.keys.removeAll { headerName ->
                        headerName.equals("Connection", ignoreCase = true) ||
                            headerName.equals("Host", ignoreCase = true) ||
                            headerName.equals("Content-Length", ignoreCase = true) ||
                            headerName.equals("Range", ignoreCase = true)
                    }
                }
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
                val title = s.optString("title", s.optString("name", "Stream"))
                val subtitles = parseSubtitles(
                    s.optJSONArray("subtitles") ?: s.optJSONArray("captions")
                )
                val intro = if (acceptsAnimeSegments) parseAnimeSegment(s.optJSONObject("intro")) else null
                val outro = if (acceptsAnimeSegments) parseAnimeSegment(s.optJSONObject("outro")) else null
                add(
                    StreamItem(
                        title = title,
                        name = s.optString("name"),
                        url = UrlUtils.normalize(url),
                        quality = s.optString("quality", "Auto"),
                        language = extractStreamLanguage(s, provider, title),
                        provider = provider,
                        type = normalizedType,
                        mimeType = mimeType,
                        headers = headers,
                        subtitles = subtitles,
                        intro = intro,
                        outro = outro,
                        // Vidsrc URLs are signed/generated dynamically and may
                        // reject a preliminary HEAD or range probe even though
                        // PlayerEngine can refresh the token before playback.
                        // Treat them as verified as soon as the provider returns
                        // them so the picker never shows them as Unverified.
                        isValid = isVidsrc,
                        isChecking = false,
                        isFailed = false,
                        )
                )
            }
        }
        return StreamResponse(tmdbId, imdbId, items)
    }

    /** Convert anime intro/outro offsets from seconds to validated milliseconds. */
    private fun parseAnimeSegment(segment: JSONObject?): StreamSegment? {
        val startSeconds = segment?.optDouble("start", Double.NaN) ?: return null
        val endSeconds = segment.optDouble("end", Double.NaN)
        if (!startSeconds.isFinite() || !endSeconds.isFinite() || startSeconds < 0.0 || endSeconds <= startSeconds) {
            return null
        }
        return StreamSegment((startSeconds * 1_000).toLong(), (endSeconds * 1_000).toLong())
    }

    /**
     * Reads the language reported by the provider, while retaining MovieBox's
     * legacy title convention when no explicit JSON field is present.
     */
    private fun extractStreamLanguage(
        stream: JSONObject,
        provider: String,
        title: String
    ): String {
        val explicitLanguage = listOf(
            "language",
            "lang",
            "audioLanguage",
            "audio_language",
            "originalLanguage",
            "original_language"
        ).firstNotNullOfOrNull { key ->
            stream.optString(key).trim().takeIf { it.isNotBlank() }
        }
        if (explicitLanguage != null) return normalizeStreamLanguage(explicitLanguage)

        if (provider.equals("moviesdrive1providers", ignoreCase = true)) {
            return "English . Hindi"
        }

        if (provider.equals("moviebox", ignoreCase = true)) {
            val titleLanguage = Regex("\\(([^()]*)\\)")
                .find(title)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()
            if (titleLanguage.isNotBlank()) return normalizeStreamLanguage(titleLanguage)
        }
        return "Original"
    }

    /**
     * Vidlink currently returns signed DASH URLs in two forms:
     * query-signed URLs with `sign` and `t`, or CloudFront-signed URLs whose
     * policy, signature, and key-pair values are supplied in a Cookie header.
     * The latter is used by `sacdn.hakunaymatata.com` responses.
     */
    private fun hasVidlinkSignature(url: String, headers: Map<String, String>): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        if (!host.endsWith(".hakunaymatata.com")) return false

        val querySigned = !uri.getQueryParameter("sign").isNullOrBlank() &&
            !uri.getQueryParameter("t").isNullOrBlank()
        if (querySigned) return true

        val cookie = headers.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            .orEmpty()
            .lowercase(Locale.ROOT)
        val cloudFrontCookieNames = listOf(
            "cloudfront-policy=",
            "cloudfront-signature=",
            "cloudfront-key-pair-id="
        )
        return cloudFrontCookieNames.all { cookieName -> cookie.contains(cookieName) }
    }

    private fun isGoogleusercontentHost(url: String): Boolean =
        runCatching { Uri.parse(url).host.orEmpty() }
            .getOrDefault("")
            .equals(GOOGLE_DOWNLOADS_HOST, ignoreCase = true)

    /**
     * Normalizes the subtitle shapes emitted by the provider plugins. MovieBox
     * currently returns `{ url, name, language, headers }`, while other
     * providers may use `file`, `label`, `lang`, `format`, or `mimeType`.
     * Media3 can reliably side-load SRT and WebVTT, so discard unsupported
     * formats rather than adding a track that fails only after playback starts.
     */
    private fun parseSubtitles(array: org.json.JSONArray?): List<SubtitleItem> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val subtitle = array.optJSONObject(index) ?: continue
            val url = subtitle.optString("url")
                .ifBlank { subtitle.optString("file") }
                .trim()
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) continue

            val mimeType = subtitleMimeType(
                subtitle.optString("mimeType")
                    .ifBlank { subtitle.optString("contentType") }
                    .ifBlank { subtitle.optString("format") },
                url
            ) ?: continue
            val headers = subtitle.optJSONObject("headers")?.let { rawHeaders ->
                LinkedHashMap<String, String>(rawHeaders.length()).apply {
                    rawHeaders.keys().forEach { key ->
                        rawHeaders.optString(key).takeIf { it.isNotBlank() }?.let { value ->
                            put(key, value)
                        }
                    }
                }
            } ?: linkedMapOf()
            val cookie = subtitle.optString("cookie")
                .ifBlank { subtitle.optString("cookies") }
                .trim()
            if (cookie.isNotBlank() && headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                headers["Cookie"] = cookie
            }
            add(
                SubtitleItem(
                    url = UrlUtils.normalize(url),
                    mimeType = mimeType,
                    language = subtitle.optString("language")
                        .ifBlank { subtitle.optString("lang") }
                        .ifBlank { subtitle.optString("language_name") }
                        .takeIf { it.isNotBlank() },
                    label = subtitle.optString("label")
                        .ifBlank { subtitle.optString("name") }
                        .ifBlank { subtitle.optString("title") }
                        .takeIf { it.isNotBlank() },
                    headers = headers
                )
            )
        }
    }.distinctBy { it.url }

    private fun subtitleMimeType(rawMimeType: String, url: String): String? {
        val value = rawMimeType.lowercase(Locale.ROOT)
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            value.contains("vtt") || path.endsWith(".vtt") -> "text/vtt"
            value.contains("srt") || value.contains("subrip") || path.endsWith(".srt") ->
                "application/x-subrip"
            else -> null
        }
    }

    private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
    private const val MATROSKA_MIME_TYPE = "video/x-matroska"
    private const val GOOGLE_DOWNLOADS_HOST = "video-downloads.googleusercontent.com"
    private const val CLOUDFLARE_STREAM_WORKER_HOST =
        "cloudflare-stream-worker.muigakiduyu1995.workers.dev"
    private val ANIME_ORIGINAL_LANGUAGES = setOf("ja", "zh", "ko")

    private val PROVIDERS_WITHOUT_SOURCE_HEADERS = setOf(
        // "cinesrc_provider",
        // "uhdmovies",
        "vegamovies",
        "webstreamr"
    )
}
