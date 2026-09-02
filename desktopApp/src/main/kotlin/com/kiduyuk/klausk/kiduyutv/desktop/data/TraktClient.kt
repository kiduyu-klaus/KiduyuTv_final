package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kiduyuk.klausk.kiduyutv.desktop.model.MediaItem
import com.kiduyuk.klausk.kiduyutv.desktop.model.MediaType
import com.kiduyuk.klausk.kiduyutv.desktop.model.PlayRequest
import com.kiduyuk.klausk.kiduyutv.desktop.model.TraktDeviceCode
import com.kiduyuk.klausk.kiduyutv.desktop.model.TraktProfile
import com.kiduyuk.klausk.kiduyutv.desktop.model.TraktShelfType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class TraktClient(
    private val settings: DesktopSettings,
    private val tmdb: TmdbClient,
    private val client: OkHttpClient = DesktopHttp.client,
    private val gson: Gson = Gson()
) {
    private val tokenMutex = Mutex()

    val isAuthenticated: Boolean
        get() = settings.traktAccessToken.isNotBlank() || settings.traktRefreshToken.isNotBlank()

    suspend fun requestDeviceCode(): TraktDeviceCode = withContext(Dispatchers.IO) {
        requireCredentials()
        val json = postJson(
            "$API_BASE/oauth/device/code",
            gson.toJson(mapOf("client_id" to settings.traktClientId)),
            authenticated = false
        )
        val expiresIn = json.requiredInt("expires_in")
        TraktDeviceCode(
            deviceCode = json.requiredString("device_code"),
            userCode = json.requiredString("user_code"),
            verificationUrl = json.requiredString("verification_url"),
            intervalSeconds = json.requiredInt("interval").coerceAtLeast(1),
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1_000L
        )
    }

    /** Polls exactly as the Android TV device-code flow does. */
    suspend fun awaitDeviceAuthorization(code: TraktDeviceCode, onWaiting: suspend () -> Unit = {}): TraktProfile {
        var pollingIntervalMs = code.intervalSeconds * 1_000L
        while (System.currentTimeMillis() < code.expiresAtMs) {
            delay(pollingIntervalMs)
            val json = try {
                postJson(
                    "$API_BASE/oauth/device/token",
                    gson.toJson(
                        mapOf(
                            "code" to code.deviceCode,
                            "client_id" to settings.traktClientId,
                            "client_secret" to settings.traktClientSecret,
                            "redirect_uri" to REDIRECT_URI
                        )
                    ),
                    authenticated = false
                )
            } catch (error: TraktHttpException) {
                when (error.statusCode) {
                    400, 404 -> onWaiting()
                    409 -> throw IOException("This Trakt activation code has already been used. Please try again.")
                    410 -> throw IOException("The Trakt activation code expired. Please try again.")
                    418 -> throw IOException("Trakt authorization was declined.")
                    429 -> {
                        pollingIntervalMs += 1_000L
                        onWaiting()
                    }
                    else -> throw error
                }
                continue
            }
            saveTokens(json)
            return profile()
        }
        throw IOException("The Trakt activation code expired. Please try again.")
    }

    suspend fun profile(): TraktProfile {
        val json = getJson("users/me?extended=full").asJsonObject
        val avatar = json.obj("images")?.obj("avatar")?.string("full")
            ?: json.string("avatar_url")
        val profile = TraktProfile(
            username = json.string("username").orEmpty(),
            name = json.string("name"),
            about = json.string("about"),
            location = json.string("location"),
            avatarUrl = avatar
        )
        settings.traktUsername = profile.username
        settings.traktAvatarUrl = profile.avatarUrl.orEmpty()
        return profile
    }

    suspend fun shelf(type: TraktShelfType): List<MediaItem> = withContext(Dispatchers.IO) {
        val raw = when (type) {
            TraktShelfType.COLLECTION -> listOf("users/me/collection/movies", "users/me/collection/shows")
            TraktShelfType.WATCHLIST -> listOf("users/me/watchlist/movies?limit=100", "users/me/watchlist/shows?limit=100")
            TraktShelfType.RECOMMENDATIONS -> listOf("recommendations/movies", "recommendations/shows")
        }
        val identities = raw.flatMap { path ->
            val defaultType = if (path.contains("shows")) MediaType.SERIES else MediaType.MOVIE
            getJson(path).asJsonArray.mapNotNull { element ->
                val entry = element.asJsonObject
                val media = when (type) {
                    TraktShelfType.RECOMMENDATIONS -> entry
                    else -> entry.obj(if (defaultType == MediaType.MOVIE) "movie" else "show")
                } ?: return@mapNotNull null
                val tmdbId = media.obj("ids")?.int("tmdb") ?: return@mapNotNull null
                Triple(tmdbId, defaultType, media.string("title").orEmpty())
            }
        }.distinctBy { "${it.second}-${it.first}" }

        identities.map { (tmdbId, mediaType, traktTitle) ->
            runCatching {
                val detail = tmdb.details(tmdbId, mediaType)
                MediaItem(
                    id = tmdbId,
                    title = detail.title,
                    name = detail.name,
                    overview = detail.overview,
                    posterPath = detail.posterPath,
                    backdropPath = detail.backdropPath,
                    voteAverage = detail.voteAverage,
                    releaseDate = detail.releaseDate,
                    firstAirDate = detail.firstAirDate,
                    mediaType = if (mediaType == MediaType.MOVIE) "movie" else "tv"
                )
            }.getOrElse {
                MediaItem(
                    id = tmdbId,
                    title = traktTitle.takeIf { mediaType == MediaType.MOVIE },
                    name = traktTitle.takeIf { mediaType == MediaType.SERIES },
                    mediaType = if (mediaType == MediaType.MOVIE) "movie" else "tv"
                )
            }
        }
    }

    suspend fun scrobble(request: PlayRequest, positionMs: Long, durationMs: Long, action: String) {
        if (!isAuthenticated || durationMs <= 0L) return
        val progress = (positionMs.toDouble() * 100.0 / durationMs).coerceIn(0.0, 100.0)
        val payload = JsonObject().apply {
            if (request.mediaType == MediaType.MOVIE) {
                add("movie", JsonObject().apply {
                    add("ids", JsonObject().apply { addProperty("tmdb", request.tmdbId) })
                })
            } else {
                add("episode", JsonObject().apply {
                    add("ids", JsonObject().apply { addProperty("tmdb", request.tmdbId) })
                    addProperty("season", request.season ?: 1)
                    addProperty("episode", request.episode ?: 1)
                })
            }
            addProperty("progress", progress)
            addProperty("app_version", System.getProperty("kiduyutv.version", "1.0"))
        }
        runCatching { postJson("$API_BASE/scrobble/$action", gson.toJson(payload), authenticated = true) }
            .onFailure { DesktopLog.logger.warn("Trakt scrobble {} failed: {}", action, it.message) }
    }

    fun signOut() {
        settings.clearTraktAuth()
    }

    private suspend fun validToken(): String = tokenMutex.withLock {
        requireCredentials()
        val access = settings.traktAccessToken
        if (access.isNotBlank() && System.currentTimeMillis() < settings.traktExpiresAtMs - 300_000L) {
            return@withLock access
        }
        val refresh = settings.traktRefreshToken
        if (refresh.isBlank()) throw IOException("Not authenticated with Trakt.tv")
        val json = try {
            postJson(
                "$API_BASE/oauth/token",
                gson.toJson(
                    mapOf(
                        "refresh_token" to refresh,
                        "client_id" to settings.traktClientId,
                        "client_secret" to settings.traktClientSecret,
                        "redirect_uri" to REDIRECT_URI,
                        "grant_type" to "refresh_token"
                    )
                ),
                authenticated = false
            )
        } catch (error: Exception) {
            settings.clearTraktAuth()
            throw error
        }
        saveTokens(json)
        settings.traktAccessToken
    }

    private fun saveTokens(json: JsonObject) {
        settings.traktAccessToken = json.requiredString("access_token")
        settings.traktRefreshToken = json.requiredString("refresh_token")
        settings.traktExpiresAtMs = System.currentTimeMillis() + json.requiredInt("expires_in") * 1_000L
    }

    private fun requireCredentials() {
        if (settings.traktClientId.isBlank() || settings.traktClientSecret.isBlank()) {
            throw IllegalStateException("Add the Trakt client ID and client secret in Settings")
        }
    }

    private suspend fun getJson(path: String) = withContext(Dispatchers.IO) {
        val token = validToken()
        val request = Request.Builder()
            .url("$API_BASE/$path")
            .header("Authorization", "Bearer $token")
            .traktHeaders()
            .build()
        try {
            executeJson(request)
        } catch (error: TraktHttpException) {
            if (error.statusCode == 401) settings.clearTraktAuth()
            throw error
        }
    }

    private suspend fun postJson(url: String, json: String, authenticated: Boolean): JsonObject =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .traktHeaders()
            if (authenticated) builder.header("Authorization", "Bearer ${validToken()}")
            try {
                executeJson(builder.build()).asJsonObject
            } catch (error: TraktHttpException) {
                if (authenticated && error.statusCode == 401) settings.clearTraktAuth()
                throw error
            }
        }

    private fun executeJson(request: Request) = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw TraktHttpException(response.code, body)
        gson.fromJson(body, com.google.gson.JsonElement::class.java)
    }

    private fun Request.Builder.traktHeaders() = this
        .header("Content-Type", "application/json")
        .header("trakt-api-version", "2")
        .header("trakt-api-key", settings.traktClientId)
        .header("User-Agent", "KiduyuTV/${System.getProperty("kiduyutv.version", "1.0")} (Windows)")

    companion object {
        const val ACTIVATE_URL = "https://trakt.tv/activate"
        private const val API_BASE = "https://api.trakt.tv"
        private const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class TraktHttpException(val statusCode: Int, responseBody: String) :
    IOException("Trakt HTTP $statusCode${responseBody.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()}")

private fun JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

private fun JsonObject.requiredString(name: String): String =
    string(name) ?: throw IOException("Trakt response is missing $name")

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.asInt

private fun JsonObject.requiredInt(name: String): Int =
    int(name) ?: throw IOException("Trakt response is missing $name")

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject
