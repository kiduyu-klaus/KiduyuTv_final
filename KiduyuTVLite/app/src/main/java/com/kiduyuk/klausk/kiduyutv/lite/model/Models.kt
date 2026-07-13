package com.kiduyuk.klausk.kiduyutv.lite.model

data class MediaItem(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String,
    val voteAverage: Double,
    val releaseDate: String
) {
    val posterUrl: String
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty()

    val backdropUrl: String
        get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty()

    val isMovie: Boolean get() = mediaType == "movie"
}

data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int
)

data class Episode(
    val episodeNumber: Int,
    val name: String,
    val overview: String
)
