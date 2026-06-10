package com.kiduyuk.klausk.kiduyutv.data.model.trakt

import com.google.gson.annotations.SerializedName

/**
 * Trakt user profile
 */
data class TraktUser(
    @SerializedName("username") val username: String,
    @SerializedName("ids") val ids: TraktIds,
    @SerializedName("name") val name: String?,
    @SerializedName("vip") val vip: Boolean,
    @SerializedName("joined_at") val joinedAt: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("about") val about: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("age") val age: Int?,
    @SerializedName("avatar_url") val avatarUrl: String?
)

/**
 * Trakt ID mappings (TMDB, IMDB, TVDB, etc.)
 */
data class TraktIds(
    @SerializedName("trakt") val trakt: Int? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("imdb") val imdb: String?,
    @SerializedName("tmdb") val tmdb: Int?,
    @SerializedName("tvdb") val tvdb: Int?
)

/**
 * Watched movie with play count and last watched
 */
data class TraktWatchedMovie(
    @SerializedName("movie") val movie: TraktMovie,
    @SerializedName("watched") val watched: TraktWatchedCount,
    @SerializedName("last_watched_at") val lastWatchedAt: String?
)

/**
 * Watched count statistics
 */
data class TraktWatchedCount(
    @SerializedName("plays") val plays: Int,
    @SerializedName("collected") val collected: Int
)

/**
 * Trakt movie metadata
 */
data class TraktMovie(
    @SerializedName("title") val title: String,
    @SerializedName("year") val year: Int,
    @SerializedName("ids") val ids: TraktIds,
    @SerializedName("tagline") val tagline: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("released") val released: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("votes") val votes: Int?,
    @SerializedName("comment_count") val commentCount: Int?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("languages") val languages: List<String>?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("certification") val certification: String?
)

/**
 * Watched show (includes seasons with episodes)
 */
data class TraktWatchedShow(
    @SerializedName("show") val show: TraktShow,
    @SerializedName("watched") val watched: TraktWatchedCount,
    @SerializedName("last_watched_at") val lastWatchedAt: String?,
    @SerializedName("seasons") val seasons: List<TraktWatchedSeason>?
)

/**
 * Trakt show metadata
 */
data class TraktShow(
    @SerializedName("title") val title: String,
    @SerializedName("year") val year: Int,
    @SerializedName("ids") val ids: TraktIds,
    @SerializedName("overview") val overview: String?,
    @SerializedName("first_aired") val firstAired: String?,
    @SerializedName("airs") val airs: TraktAirs?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("votes") val votes: Int?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("status") val status: String?,
    @SerializedName("air_time") val airTime: String?,
    @SerializedName("air_day") val airDay: String?
)

data class TraktAirs(
    @SerializedName("day") val day: String?,
    @SerializedName("time") val time: String?,
    @SerializedName("timezone") val timezone: String?
)

/**
 * Season with watched episodes
 */
data class TraktWatchedSeason(
    @SerializedName("number") val number: Int,
    @SerializedName("episodes") val episodes: List<TraktWatchedEpisode>?
)

/**
 * Individual episode watch data
 */
data class TraktWatchedEpisode(
    @SerializedName("number") val number: Int,
    @SerializedName("plays") val plays: Int,
    @SerializedName("last_watched_at") val lastWatchedAt: String?
)

/**
 * Watchlist item
 */
data class TraktWatchlistItem(
    @SerializedName("listed_at") val listedAt: String,
    @SerializedName("type") val type: String,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("show") val show: TraktShow?
)

/**
 * Scrobble request for playback sync
 */
data class TraktScrobbleRequest(
    @SerializedName("movie") val movie: TraktScrobbleMovie?,
    @SerializedName("episode") val episode: TraktScrobbleEpisode?,
    @SerializedName("progress") val progress: Double,
    @SerializedName("app_version") val appVersion: String = "1.0",
    @SerializedName("date_watched") val dateWatched: String? = null
)

data class TraktScrobbleMovie(
    @SerializedName("ids") val ids: TraktIds
)

data class TraktScrobbleEpisode(
    @SerializedName("ids") val ids: TraktIds,
    @SerializedName("season") val season: Int,
    @SerializedName("episode") val episode: Int
)

/**
 * Scrobble response
 */
data class TraktScrobbleResponse(
    @SerializedName("action") val action: String,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("episode") val episode: TraktEpisode?,
    @SerializedName("progress") val progress: Double
)

data class TraktEpisode(
    @SerializedName("season") val season: Int,
    @SerializedName("episode") val number: Int,
    @SerializedName("title") val title: String,
    @SerializedName("ids") val ids: TraktIds
)

/**
 * Sync items for batch operations
 */
data class TraktSyncItems(
    @SerializedName("movies") val movies: List<TraktSyncMovie>?,
    @SerializedName("shows") val shows: List<TraktSyncShow>?,
    @SerializedName("episodes") val episodes: List<TraktSyncEpisode>?
)

data class TraktSyncMovie(
    @SerializedName("ids") val ids: TraktIds
)

data class TraktSyncShow(
    @SerializedName("ids") val ids: TraktIds
)

data class TraktSyncEpisode(
    @SerializedName("ids") val ids: TraktIds,
    @SerializedName("season") val season: Int,
    @SerializedName("episode") val episode: Int
)

/**
 * Sync response
 */
data class TraktSyncResponse(
    @SerializedName("added") val added: TraktSyncAdded,
    @SerializedName("updated") val updated: TraktSyncUpdated,
    @SerializedName("deleted") val deleted: TraktSyncDeleted,
    @SerializedName("not_found") val notFound: TraktSyncNotFound
)

data class TraktSyncAdded(
    @SerializedName("movies") val movies: Int,
    @SerializedName("shows") val shows: Int,
    @SerializedName("seasons") val seasons: Int,
    @SerializedName("episodes") val episodes: Int
)

data class TraktSyncUpdated(
    @SerializedName("movies") val movies: Int,
    @SerializedName("shows") val shows: Int
)

data class TraktSyncDeleted(
    @SerializedName("movies") val movies: Int,
    @SerializedName("shows") val shows: Int,
    @SerializedName("episodes") val episodes: Int
)

data class TraktSyncNotFound(
    @SerializedName("movies") val movies: List<TraktSyncMovie>?,
    @SerializedName("shows") val shows: List<TraktSyncShow>?,
    @SerializedName("episodes") val episodes: List<TraktSyncEpisode>?
)

/**
 * Playback progress item
 */
data class TraktPlaybackProgress(
    @SerializedName("id") val id: Int,
    @SerializedName("progress") val progress: Double,
    @SerializedName("paused_at") val pausedAt: String,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("episode") val episode: TraktEpisode?
)

/**
 * History item
 */
data class TraktHistoryItem(
    @SerializedName("id") val id: Long,
    @SerializedName("watched_at") val watchedAt: String,
    @SerializedName("action") val action: String,
    @SerializedName("type") val type: String,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("episode") val episode: TraktEpisode?,
    @SerializedName("show") val show: TraktShow?
)

/**
 * Trakt user settings/profile
 */
data class TraktSettings(
    @SerializedName("user") val user: TraktUser
)

/**
 * Trakt watch history response (list of history items)
 */
typealias TraktWatchHistoryResponse = List<TraktHistoryItem>

/**
 * Trakt collection item (movie or show in user's collection)
 */
data class TraktCollectionItem(
    @SerializedName("added_at") val addedAt: String,
    @SerializedName("type") val type: String,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("show") val show: TraktShow?
)

/**
 * Trakt recommendation item
 */
data class TraktRecommendation(
    @SerializedName("rank") val rank: Int,
    @SerializedName("listed_at") val listedAt: String,
    @SerializedName("type") val type: String,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("show") val show: TraktShow?
)


