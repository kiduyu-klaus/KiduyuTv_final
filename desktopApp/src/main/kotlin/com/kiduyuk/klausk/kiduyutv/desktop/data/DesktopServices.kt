package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kiduyuk.klausk.kiduyutv.desktop.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

private object DesktopBuildDefaults {
    private val properties: Properties by lazy {
        Properties().apply {
            DesktopBuildDefaults::class.java.classLoader
                .getResourceAsStream("kiduyutv-defaults.properties")
                ?.use { input -> load(input) }
        }
    }

    val tmdbBearerToken: String
        get() = properties.getProperty("tmdbBearerToken", "").trim()

    val streamApiToken: String
        get() = properties.getProperty("streamApiToken", "").trim()

    val traktClientId: String
        get() = properties.getProperty("traktClientId", "").trim()

    val traktClientSecret: String
        get() = properties.getProperty("traktClientSecret", "").trim()
}

class DesktopSettings {
    private val prefs = Preferences.userRoot().node("com/kiduyutv/desktop")

    var tmdbBearerToken: String
        get() = prefs.get("tmdb_bearer_token", "")
            .ifBlank { System.getenv("KIDUYUTV_TMDB_TOKEN").orEmpty() }
            .ifBlank { DesktopBuildDefaults.tmdbBearerToken }
        set(value) = prefs.put("tmdb_bearer_token", value.trim())

    var streamApiToken: String
        get() = prefs.get("stream_api_token", "")
            .ifBlank { System.getenv("KIDUYUTV_STREAM_API_TOKEN").orEmpty() }
            .ifBlank { DesktopBuildDefaults.streamApiToken }
        set(value) = prefs.put("stream_api_token", value.trim())

    var traktClientId: String
        get() = prefs.get("trakt_client_id", "")
            .ifBlank { System.getenv("KIDUYUTV_TRAKT_CLIENT_ID").orEmpty() }
            .ifBlank { DesktopBuildDefaults.traktClientId }
        set(value) = prefs.put("trakt_client_id", value.trim())

    var traktClientSecret: String
        get() = prefs.get("trakt_client_secret", "")
            .ifBlank { System.getenv("KIDUYUTV_TRAKT_CLIENT_SECRET").orEmpty() }
            .ifBlank { DesktopBuildDefaults.traktClientSecret }
        set(value) = prefs.put("trakt_client_secret", value.trim())

    var providersBaseUrl: String
        get() = prefs.get(
            "providers_base_url",
            "https://sflatransport.com/kiduyuTv_providers"
        )
        set(value) = prefs.put("providers_base_url", value.trim().trimEnd('/'))

    var directStreamEnabled: Boolean
        get() = prefs.getBoolean("direct_stream_enabled", true)
        set(value) = prefs.putBoolean("direct_stream_enabled", value)

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.putBoolean("dark_theme", value)

    var defaultProvider: String
        get() = prefs.get("default_provider", "")
        set(value) = prefs.put("default_provider", value)

    var playlistUrl: String
        get() = prefs.get("playlist_url", "")
        set(value) = prefs.put("playlist_url", value.trim())

    var epgUrl: String
        get() = prefs.get("epg_url", "")
        set(value) = prefs.put("epg_url", value.trim())

    var mpvPath: String
        get() = prefs.get("mpv_path", "mpv.exe")
        set(value) = prefs.put("mpv_path", value.trim())

    /** Cached working mpv GPU context; blank means probe the supported candidates. */
    var mpvGpuContext: String
        get() = prefs.get("mpv_gpu_context", "")
        set(value) = prefs.put("mpv_gpu_context", value.trim())

    var preferredSubtitleLanguage: String
        get() = prefs.get("subtitle_language", "en")
        set(value) = prefs.put("subtitle_language", value.trim())

    var traktAccessToken: String
        get() = prefs.get("trakt_access_token", "")
        set(value) = prefs.put("trakt_access_token", value)

    var traktRefreshToken: String
        get() = prefs.get("trakt_refresh_token", "")
        set(value) = prefs.put("trakt_refresh_token", value)

    var traktExpiresAtMs: Long
        get() = prefs.getLong("trakt_expires_at", 0L)
        set(value) = prefs.putLong("trakt_expires_at", value)

    var traktUsername: String
        get() = prefs.get("trakt_username", "")
        set(value) = prefs.put("trakt_username", value)

    var traktAvatarUrl: String
        get() = prefs.get("trakt_avatar_url", "")
        set(value) = prefs.put("trakt_avatar_url", value)

    fun clearTraktAuth() {
        listOf(
            "trakt_access_token",
            "trakt_refresh_token",
            "trakt_expires_at",
            "trakt_username",
            "trakt_avatar_url"
        ).forEach(prefs::remove)
    }
}

object DesktopHttp {
    private val cacheDir = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "KiduyuTV/cache/http"
    ).apply { mkdirs() }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(cacheDir, 64L * 1024L * 1024L))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

class TmdbClient(
    private val settings: DesktopSettings,
    private val client: OkHttpClient = DesktopHttp.client,
    private val gson: Gson = Gson()
) {
    suspend fun page(path: String, type: MediaType, page: Int = 1): List<MediaItem> {
        val result: TmdbPage<MediaItem> = get(path, mapOf("page" to page.toString()))
        return result.results.map { item ->
            if (item.mediaType != null) item else item.copy(mediaType = apiMediaType(type))
        }
    }

    suspend fun homeSections(history: List<WatchProgress>): List<Pair<String, List<MediaItem>>> =
        coroutineScope {
            val remote = listOf(
                Triple("Now Playing", "movie/now_playing", MediaType.MOVIE),
                Triple("TV Shows Trending Today", "trending/tv/day", MediaType.SERIES),
                Triple("Movies Trending This Week", "trending/movie/week", MediaType.MOVIE),
                Triple("Top Rated Movies", "movie/top_rated", MediaType.MOVIE),
                Triple("Top Rated TV Shows", "tv/top_rated", MediaType.SERIES)
            ).map { (name, path, type) ->
                async { name to page(path, type) }
            }.awaitAll().toMutableList()

            val continueWatching = history.sortedByDescending { it.updatedAt }.map {
                MediaItem(
                    id = it.tmdbId,
                    title = if (it.mediaType == MediaType.MOVIE) it.title else null,
                    name = if (it.mediaType == MediaType.SERIES) it.title else null,
                    posterPath = it.posterPath,
                    backdropPath = it.backdropPath,
                    mediaType = apiMediaType(it.mediaType)
                )
            }
            if (continueWatching.isNotEmpty()) remote.add(1, "Continue Watching" to continueWatching)

            val localLists = listOf(
                "2026 Oscar winners" to "oscar_winners_2026.json",
                "Marvel Cinematic Universe" to "marvel_cinematic_universe.json",
                "Harry Potter Collection" to "harry_potter_collection.json",
                "100 Anime to Watch Before You Die" to "anime_to_watch_before_you_die.json",
                "Popular Horror" to "popular_horror.json",
                "Shut Up and Watch" to "shut_up_and_watch.json",
                "James Bond Collection" to "james_bond_collection.json",
                "Pirates of the Caribbean" to "pirates_of_the_caribbean.json",
                "Hallmark Movies" to "hallmark_movies.json",
                "Movies Based on True Stories" to "true_story_movies.json",
                "Best Sitcoms Ever" to "best_sitcoms.json",
                "Best movie classics" to "best_classics.json",
                "CIA & Mossad Spies" to "cia_mossad_spies.json",
                "Jason Statham Movies" to "jason_statham_movies.json",
                "Time Travel Movies" to "time_travel_movies.json"
            ).mapNotNull { (name, file) ->
                loadEmbeddedList<MediaItem>(file).takeIf { it.isNotEmpty() }?.let { name to it }
            }
            remote + localLists
        }

    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val result: TmdbPage<MediaItem> = get("search/multi", mapOf("query" to query))
        return result.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }

    suspend fun details(id: Int, type: MediaType): MediaDetails = get(
        "${apiMediaType(type)}/$id",
        mapOf("append_to_response" to "credits,images,videos,recommendations")
    )

    suspend fun season(tvId: Int, season: Int): SeasonDetails =
        get("tv/$tvId/season/$season")

    suspend fun discover(type: MediaType, filterName: String, filterId: Int): List<MediaItem> {
        val queryKey = when (filterName) {
            "company" -> "with_companies"
            "network" -> "with_networks"
            else -> "with_genres"
        }
        val result: TmdbPage<MediaItem> = get(
            "discover/${apiMediaType(type)}",
            mapOf(queryKey to filterId.toString(), "sort_by" to "popularity.desc")
        )
        return result.results.map { it.copy(mediaType = apiMediaType(type)) }
    }

    suspend fun person(id: Int): PersonDetails =
        get("person/$id", mapOf("append_to_response" to "movie_credits,tv_credits,images"))

    suspend fun images(id: Int, type: MediaType): Images =
        get("${apiMediaType(type)}/$id/images", mapOf("include_image_language" to "en,null"))

    suspend fun videos(id: Int, type: MediaType): Videos =
        get("${apiMediaType(type)}/$id/videos")

    private inline fun <reified T> loadEmbeddedList(file: String): List<T> {
        val text = javaClass.getResourceAsStream("/lists/$file")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return emptyList()
        return runCatching {
            gson.fromJson<List<T>>(text, object : TypeToken<List<T>>() {}.type)
        }.getOrDefault(emptyList())
    }

    private suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap()
    ): T = withContext(Dispatchers.IO) {
        val token = settings.tmdbBearerToken
        if (token.isBlank()) throw IllegalStateException("Add a TMDB bearer token in Settings")
        val url = "https://api.themoviedb.org/3/".toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("User-Agent", "KiduyuTV/1.0 (Windows)")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("TMDB HTTP ${response.code}")
            gson.fromJson(body, object : TypeToken<T>() {}.type)
        }
    }

    private fun apiMediaType(type: MediaType): String =
        if (type == MediaType.MOVIE) "movie" else "tv"
}

class ProvidersHttpException(val statusCode: Int) : IOException("Providers API HTTP $statusCode")

class ProvidersClient(
    private val settings: DesktopSettings,
    private val client: OkHttpClient = DesktopHttp.client,
    private val gson: Gson = Gson()
) {
    suspend fun enabledProviders(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${settings.providersBaseUrl.trimEnd('/')}/api/providers")
            .header("Accept", "application/json")
            .header("User-Agent", "KiduyuTV/1.0 (Windows)")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProvidersHttpException(response.code)
            val parsed = gson.fromJson(body, ProvidersResponse::class.java)
            if (!parsed.success) throw IOException("Providers API returned success=false")
            parsed.providers
                .filter { it.enabled }
                .map { it.name.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
        }
    }

    suspend fun streams(
        request: PlayRequest,
        provider: String? = null,
        onProviderProgress: suspend (index: Int, total: Int, providerName: String) -> Unit = { _, _, _ -> },
        onProviderRetry: suspend (index: Int, total: Int, providerName: String) -> Unit = { _, _, _ -> }
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val providerNames = if (provider.isNullOrBlank()) {
            enabledProviders()
        } else {
            listOf(provider.trim().lowercase())
        }
        if (providerNames.isEmpty()) return@withContext emptyList()

        val allStreams = mutableListOf<StreamItem>()
        providerNames.forEachIndexed { index, providerName ->
            onProviderProgress(index + 1, providerNames.size, providerName)
            var streams = emptyList<StreamItem>()
            var lastError: Throwable? = null
            var attempt = 0
            while (attempt < 2 && streams.isEmpty()) {
                try {
                    streams = fetchProviderStreams(request, providerName)
                    lastError = null
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    lastError = error
                }
                if (streams.isEmpty() && attempt == 0) {
                    onProviderRetry(index + 1, providerNames.size, providerName)
                }
                attempt++
            }
            if (lastError != null) {
                // Continue so one unavailable provider cannot hide other results.
                return@forEachIndexed
            }
            allStreams += streams
        }
        allStreams.distinctBy { "${it.provider.lowercase()}|${it.url}" }
    }

    private suspend fun fetchProviderStreams(request: PlayRequest, provider: String): List<StreamItem> =
        withContext(Dispatchers.IO) {
            val token = settings.streamApiToken
            if (token.isBlank()) throw IllegalStateException("Add the stream API token in Settings")
            val base = settings.providersBaseUrl.toHttpUrl().newBuilder()
                .addPathSegment("api")
                .addPathSegment("streams")
                .addPathSegment(provider)
                .addPathSegment(request.mediaType.apiValue)
                .addPathSegment(request.tmdbId.toString())
                .addQueryParameter("token", token)
                .apply {
                    if (request.mediaType == MediaType.SERIES) {
                        addQueryParameter("season", requireNotNull(request.season).toString())
                        addQueryParameter("episode", requireNotNull(request.episode).toString())
                    }
                }.build()
            val call = Request.Builder()
                .url(base)
                .header("Accept", "application/json")
                .header("User-Agent", "KiduyuTV/1.0 (Windows)")
                .build()
            client.newCall(call).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ProvidersHttpException(response.code)
                gson.fromJson(body, StreamResponse::class.java)
                    .streams.filter { it.url.isHttpUrl() }
            }
        }

    private fun String.isHttpUrl(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
    }.getOrDefault(false)
}

class IptvClient(private val client: OkHttpClient = DesktopHttp.client) {
    suspend fun load(url: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext emptyList()
        val request = Request.Builder().url(url).header("User-Agent", "KiduyuTV/1.0 (Windows)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Playlist HTTP ${response.code}")
            parseM3u(response.body?.string().orEmpty())
        }
    }

    internal fun parseM3u(text: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var pending: Map<String, String>? = null
        text.lineSequence().map(String::trim).filter(String::isNotBlank).forEach { line ->
            if (line.startsWith("#EXTINF", true)) {
                val attrs = Regex("([\\w-]+)=\"([^\"]*)\"").findAll(line)
                    .associate { it.groupValues[1] to it.groupValues[2] }
                    .toMutableMap()
                attrs["display-name"] = line.substringAfterLast(',', attrs["tvg-name"].orEmpty()).trim()
                pending = attrs
            } else if (!line.startsWith('#') && pending != null) {
                val attrs = pending.orEmpty()
                channels += IptvChannel(
                    name = attrs["display-name"].orEmpty().ifBlank { attrs["tvg-name"] ?: "Channel" },
                    url = line,
                    logo = attrs["tvg-logo"],
                    group = attrs["group-title"].orEmpty().ifBlank { "Other" },
                    tvgId = attrs["tvg-id"],
                    tvgName = attrs["tvg-name"]
                )
                pending = null
            }
        }
        return channels
    }
}

class LocalLibrary(private val gson: Gson = Gson()) {
    private val prefs = Preferences.userRoot().node("com/kiduyutv/desktop/library")
    private val mediaType = object : TypeToken<List<MediaItem>>() {}.type
    private val historyType = object : TypeToken<List<WatchProgress>>() {}.type

    @Synchronized
    fun favorites(): List<MediaItem> = runCatching {
        gson.fromJson<List<MediaItem>>(prefs.get("favorites", "[]"), mediaType)
    }.getOrDefault(emptyList())

    @Synchronized
    fun toggleFavorite(item: MediaItem): Boolean {
        val items = favorites().toMutableList()
        val index = items.indexOfFirst { it.id == item.id && it.resolvedType == item.resolvedType }
        val added = index < 0
        if (added) items += item else items.removeAt(index)
        prefs.put("favorites", gson.toJson(items))
        return added
    }

    fun isFavorite(id: Int, type: MediaType): Boolean =
        favorites().any { it.id == id && it.resolvedType == type }

    @Synchronized
    fun history(): List<WatchProgress> = runCatching {
        gson.fromJson<List<WatchProgress>>(prefs.get("history", "[]"), historyType)
    }.getOrDefault(emptyList())

    @Synchronized
    fun progress(request: PlayRequest): WatchProgress? = history()
        .filter {
            it.tmdbId == request.tmdbId && it.mediaType == request.mediaType &&
                (request.mediaType == MediaType.MOVIE ||
                    it.season == request.season && it.episode == request.episode)
        }.maxByOrNull { it.updatedAt }

    @Synchronized
    fun saveProgress(progress: WatchProgress) {
        val entries = history().toMutableList()
        entries.removeAll {
            it.tmdbId == progress.tmdbId && it.mediaType == progress.mediaType &&
                (progress.mediaType == MediaType.MOVIE ||
                    it.season == progress.season && it.episode == progress.episode)
        }
        entries += progress
        prefs.put("history", gson.toJson(entries.sortedByDescending { it.updatedAt }.take(250)))
    }
}
