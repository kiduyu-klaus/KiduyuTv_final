package com.kiduyuk.klausk.kiduyutv.desktop.model

import com.google.gson.annotations.SerializedName

enum class MediaType(val apiValue: String) {
    MOVIE("movie"),
    SERIES("series")
}

data class MediaItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerializedName(value = "poster_path", alternate = ["posterPath"]) val posterPath: String? = null,
    @SerializedName(value = "backdrop_path", alternate = ["backdropPath"]) val backdropPath: String? = null,
    @SerializedName(value = "vote_average", alternate = ["voteAverage"]) val voteAverage: Double = 0.0,
    @SerializedName(value = "release_date", alternate = ["releaseDate"]) val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList()
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
    val displayDate: String get() = releaseDate ?: firstAirDate ?: ""
    val resolvedType: MediaType
        get() = if (mediaType.equals("tv", true) || title == null && name != null) {
            MediaType.SERIES
        } else {
            MediaType.MOVIE
        }
}

data class TmdbPage<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class Genre(val id: Int = 0, val name: String = "")
data class GenreResponse(val genres: List<Genre> = emptyList())

data class SeasonItem(
    val id: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("poster_path") val posterPath: String? = null,
    val overview: String = ""
)

data class EpisodeItem(
    val id: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0
)

data class SeasonDetails(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val episodes: List<EpisodeItem> = emptyList()
)

data class CastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
    @SerializedName("known_for_department") val knownForDepartment: String = "",
    @SerializedName("profile_path") val profilePath: String? = null
)

data class Credits(val cast: List<CastMember> = emptyList())

data class ImageItem(
    @SerializedName("file_path") val filePath: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerializedName("vote_average") val voteAverage: Double = 0.0
)

data class Images(
    val backdrops: List<ImageItem> = emptyList(),
    val posters: List<ImageItem> = emptyList(),
    val profiles: List<ImageItem> = emptyList()
)

data class VideoItem(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = ""
)

data class Videos(val results: List<VideoItem> = emptyList())

data class MediaDetails(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    val runtime: Int? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val seasons: List<SeasonItem> = emptyList(),
    val credits: Credits = Credits(),
    val images: Images = Images(),
    val videos: Videos = Videos(),
    val recommendations: TmdbPage<MediaItem> = TmdbPage()
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
    val displayDate: String get() = releaseDate ?: firstAirDate ?: ""
}

data class PersonDetails(
    val id: Int = 0,
    val name: String = "",
    val biography: String = "",
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("known_for_department") val knownForDepartment: String = "",
    @SerializedName("movie_credits") val movieCredits: PersonCredits = PersonCredits(),
    @SerializedName("tv_credits") val tvCredits: PersonCredits = PersonCredits(),
    val images: Images = Images()
)

data class PersonCredits(val cast: List<MediaItem> = emptyList())

data class ProviderStatus(
    val name: String = "",
    val enabled: Boolean = false
)

data class ProvidersResponse(
    val success: Boolean = false,
    val providers: List<ProviderStatus> = emptyList()
)

data class StreamItem(
    val name: String = "",
    val title: String = "",
    val url: String = "",
    val quality: String = "Auto",
    val provider: String = "",
    val type: String = "",
    val mimeType: String = "",
    val headers: Map<String, String> = emptyMap()
) {
    val displayName: String
        get() = name.ifBlank { title.ifBlank { provider.ifBlank { "Stream" } } }
}

data class StreamResponse(
    val success: Boolean = false,
    val count: Int = 0,
    val streams: List<StreamItem> = emptyList()
)

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "Other",
    val tvgId: String? = null,
    val tvgName: String? = null
)

data class WatchProgress(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class PlayRequest(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val provider: String? = null
)

data class TraktProfile(
    val username: String = "",
    val name: String? = null,
    val about: String? = null,
    val location: String? = null,
    val avatarUrl: String? = null
)

data class TraktDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresAtMs: Long
)

enum class TraktShelfType {
    COLLECTION,
    WATCHLIST,
    RECOMMENDATIONS
}
