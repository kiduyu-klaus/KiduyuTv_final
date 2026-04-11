package com.kiduyuk.klausk.kiduyutv.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Movies : Screen("movies")
    object TvShows : Screen("tv_shows")
    object MyList : Screen("my_list")
    object Search : Screen("search")
    object MovieDetail : Screen("movie/{movieId}") {
        fun createRoute(movieId: Int) = "movie/$movieId"
    }
    object TvShowDetail : Screen("tv/{tvId}") {
        fun createRoute(tvId: Int) = "tv/$tvId"
    }
    object SeasonDetail : Screen("tv/{tvId}/season/{seasonNumber}") {
        fun createRoute(tvId: Int, seasonNumber: Int) = "tv/$tvId/season/$seasonNumber"
    }
    object SeasonEpisodes : Screen("season_episodes/{tvId}/{totalSeasons}?tvShowName={tvShowName}") {
        fun createRoute(tvId: Int, tvShowName: String, totalSeasons: Int): String {
            val encodedName = android.net.Uri.encode(tvShowName)
            return "season_episodes/$tvId/$totalSeasons?tvShowName=$encodedName"
        }
    }
    object Settings : Screen("settings")
    object MobileCastDetail : Screen("mobile_cast_detail/{castId}?castName={castName}&character={character}&profilePath={profilePath}&knownForDepartment={knownForDepartment}") {
        fun createRoute(
            castId: Int,
            castName: String,
            character: String?,
            profilePath: String?,
            knownForDepartment: String?
        ): String {
            val encodedName = android.net.Uri.encode(castName)
            val encodedChar = android.net.Uri.encode(character ?: "")
            val encodedProfile = android.net.Uri.encode(profilePath ?: "")
            val encodedKnown = android.net.Uri.encode(knownForDepartment ?: "")
            return "mobile_cast_detail/$castId?castName=$encodedName&character=$encodedChar&profilePath=$encodedProfile&knownForDepartment=$encodedKnown"
        }
    }
    object CastDetail : Screen("cast_detail/{castId}/{castName}/{character}/{profilePath}/{knownForDepartment}")
    object StreamLinks : Screen("stream_links/{tmdbId}/{isTv}?season={season}&episode={episode}&title={title}&overview={overview}&posterPath={posterPath}&backdropPath={backdropPath}&voteAverage={voteAverage}&releaseDate={releaseDate}&timestamp={timestamp}") {
        fun createRoute(
            tmdbId: Int,
            isTv: Boolean,
            title: String,
            overview: String?,
            posterPath: String?,
            backdropPath: String?,
            voteAverage: Double?,
            releaseDate: String?,
            season: Int? = null,
            episode: Int? = null,
            timestamp: Long = 0L
        ): String {
            val encodedTitle = android.net.Uri.encode(title)
            val encodedOverview = android.net.Uri.encode(overview ?: "")
            val encodedPoster = android.net.Uri.encode(posterPath ?: "")
            val encodedBackdrop = android.net.Uri.encode(backdropPath ?: "")
            val encodedReleaseDate = android.net.Uri.encode(releaseDate ?: "")
            return "stream_links/$tmdbId/$isTv?season=${season ?: 0}&episode=${episode ?: 0}&title=$encodedTitle&overview=$encodedOverview&posterPath=$encodedPoster&backdropPath=$encodedBackdrop&voteAverage=$voteAverage&releaseDate=$encodedReleaseDate&timestamp=$timestamp"
        }


    }
}
