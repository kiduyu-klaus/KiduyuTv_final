package com.kiduyuk.klausk.kiduyutv.lite.api

import com.kiduyuk.klausk.kiduyutv.lite.BuildConfig
import com.kiduyuk.klausk.kiduyutv.lite.model.Episode
import com.kiduyuk.klausk.kiduyutv.lite.model.MediaItem
import com.kiduyuk.klausk.kiduyutv.lite.model.Season
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Minimal synchronous TMDB client. Call every public method from Dispatchers.IO.
 */
object TmdbApi {

    private const val BASE_URL = "https://api.themoviedb.org/3"

    private fun requireCredential(): String = BuildConfig.TMDB_API_KEY
        .takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("TMDB_API_KEY is not configured")

    private fun get(path: String): JSONObject {
        val credential = requireCredential()
        val usesBearerAuthentication = credential.startsWith("eyJ")
        val requestPath = if (usesBearerAuthentication) {
            path
        } else {
            val separator = if ('?' in path) "&" else "?"
            "$path${separator}api_key=$credential"
        }
        val requestUrl = URL("$BASE_URL$requestPath")
        val connection = requestUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        if (usesBearerAuthentication) {
            connection.setRequestProperty("Authorization", "Bearer $credential")
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("TMDB request failed with HTTP $status")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun nullablePath(value: String): String? =
        value.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }

    private fun parseItem(json: JSONObject, forcedType: String? = null): MediaItem {
        val mediaType = forcedType ?: json.optString("media_type", "movie")
        val title = if (mediaType == "tv") {
            json.optString("name", json.optString("title", ""))
        } else {
            json.optString("title", json.optString("name", ""))
        }
        val releaseDate = if (mediaType == "tv") {
            json.optString("first_air_date", "")
        } else {
            json.optString("release_date", "")
        }

        return MediaItem(
            id = json.getInt("id"),
            title = title,
            overview = json.optString("overview", ""),
            posterPath = nullablePath(json.optString("poster_path", "")),
            backdropPath = nullablePath(json.optString("backdrop_path", "")),
            mediaType = mediaType,
            voteAverage = json.optDouble("vote_average", 0.0),
            releaseDate = releaseDate
        )
    }

    private fun parseList(json: JSONObject, forcedType: String? = null): List<MediaItem> {
        val results = json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = parseItem(results.getJSONObject(index), forcedType)
                if (item.title.isNotBlank() && item.mediaType in setOf("movie", "tv")) add(item)
            }
        }
    }

    fun trendingAll(): List<MediaItem> = parseList(get("/trending/all/day"))

    fun popularMovies(): List<MediaItem> = parseList(get("/movie/popular"), "movie")

    fun popularTv(): List<MediaItem> = parseList(get("/tv/popular"), "tv")

    fun search(query: String): List<MediaItem> {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return parseList(get("/search/multi?query=$encodedQuery"))
    }

    fun tvSeasons(showId: Int): List<Season> {
        val seasons = get("/tv/$showId").optJSONArray("seasons") ?: return emptyList()
        return buildList {
            for (index in 0 until seasons.length()) {
                val json = seasons.getJSONObject(index)
                val seasonNumber = json.optInt("season_number", 0)
                if (seasonNumber > 0) {
                    add(
                        Season(
                            seasonNumber = seasonNumber,
                            name = json.optString("name", "Season $seasonNumber"),
                            episodeCount = json.optInt("episode_count", 0)
                        )
                    )
                }
            }
        }
    }

    fun tvEpisodes(showId: Int, seasonNumber: Int): List<Episode> {
        val episodes = get("/tv/$showId/season/$seasonNumber")
            .optJSONArray("episodes") ?: return emptyList()
        return buildList {
            for (index in 0 until episodes.length()) {
                val json = episodes.getJSONObject(index)
                add(
                    Episode(
                        episodeNumber = json.optInt("episode_number", index + 1),
                        name = json.optString("name", "Episode ${index + 1}"),
                        overview = json.optString("overview", "")
                    )
                )
            }
        }
    }
}
