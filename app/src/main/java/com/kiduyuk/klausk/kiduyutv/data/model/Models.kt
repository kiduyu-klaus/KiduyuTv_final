package com.kiduyuk.klausk.kiduyutv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a Movie.
 * @param id The unique identifier for the movie.
 * @param title The title of the movie.
 * @param overview A brief summary of the movie's plot.
 * @param posterPath The path to the movie's poster image.
 * @param backdropPath The path to the movie's backdrop image.
 * @param voteAverage The average vote score for the movie.
 * @param releaseDate The release date of the movie.
 * @param genreIds A list of genre IDs associated with the movie.
 * @param popularity The popularity score of the movie.
 */
data class Movie(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path", alternate = ["posterPath"]) val posterPath: String?,
    @SerializedName("backdrop_path", alternate = ["backdropPath"]) val backdropPath: String?,
    @SerializedName("vote_average", alternate = ["voteAverage"]) val voteAverage: Double,
    @SerializedName("release_date", alternate = ["releaseDate"]) val releaseDate: String?,
    @SerializedName("genre_ids", alternate = ["genreIds"]) val genreIds: List<Int>?,
    @SerializedName("popularity") val popularity: Double?
)

/**
 * Data class representing a TV Show.
 * @param id The unique identifier for the TV show.
 * @param name The name of the TV show.
 * @param overview A brief summary of the TV show's plot.
 * @param posterPath The path to the TV show's poster image.
 * @param backdropPath The path to the TV show's backdrop image.
 * @param voteAverage The average vote score for the TV show.
 * @param firstAirDate The first air date of the TV show.
 * @param genreIds A list of genre IDs associated with the TV show.
 * @param popularity The popularity score of the TV show.
 */
data class TvShow(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path", alternate = ["posterPath"]) val posterPath: String?,
    @SerializedName("backdrop_path", alternate = ["backdropPath"]) val backdropPath: String?,
    @SerializedName("vote_average", alternate = ["voteAverage"]) val voteAverage: Double,
    @SerializedName("first_air_date", alternate = ["firstAirDate"]) val firstAirDate: String?,
    @SerializedName("genre_ids", alternate = ["genreIds"]) val genreIds: List<Int>?,
    @SerializedName("popularity") val popularity: Double?
)

/**
 * Data class representing a response containing a list of Movies.
 * @param page The current page number of the results.
 * @param results The list of [Movie] objects.
 * @param totalPages The total number of pages available.
 * @param totalResults The total number of results available.
 */
data class MovieResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<Movie>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

/**
 * Data class representing a response containing a list of TV Shows.
 * @param page The current page number of the results.
 * @param results The list of [TvShow] objects.
 * @param totalPages The total number of pages available.
 * @param totalResults The total number of results available.
 */
data class TvShowResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<TvShow>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

/**
 * Data class representing a Genre.
 * @param id The unique identifier for the genre.
 * @param name The name of the genre.
 */
data class Genre(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

/**
 * Data class representing a response containing a list of Genres.
 * @param genres The list of [Genre] objects.
 */
data class GenreResponse(
    @SerializedName("genres") val genres: List<Genre>
)

/**
 * Data class representing a Network (e.g., Netflix, HBO).
 * @param id The unique identifier for the network.
 * @param name The name of the network.
 * @param logoPath The path to the network's logo image.
 */
data class Network(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("logo_path") val logoPath: String?
)

/**
 * Data class representing a Production Company.
 * @param id The unique identifier for the production company.
 * @param name The name of the production company.
 * @param logoPath The path to the company's logo image.
 * @param originCountry The country of origin for the production company.
 */
data class ProductionCompany(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("logo_path") val logoPath: String?,
    @SerializedName("origin_country") val originCountry: String?
)

/**
 * Data class representing a movie collection (e.g., "Harry Potter Collection").
 */
data class MovieCollection(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?
)

/**
 * Data class representing detailed information for a movie collection.
 */
data class CollectionDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("parts") val parts: List<Movie>
)

/**
 * Data class representing detailed information for a Movie.
 * @param id The unique identifier for the movie.
 * @param title The title of the movie.
 * @param overview A brief summary of the movie's plot.
 * @param posterPath The path to the movie's poster image.
 * @param backdropPath The path to the movie's backdrop image.
 * @param voteAverage The average vote score for the movie.
 * @param releaseDate The release date of the movie.
 * @param runtime The runtime of the movie in minutes.
 * @param genres A list of [Genre] objects associated with the movie.
 * @param productionCompanies A list of [ProductionCompany] objects involved in the movie's production.
 * @param belongsToCollection The collection this movie belongs to, if any.
 */
data class MovieDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("genres") val genres: List<Genre>?,
    @SerializedName("production_companies") val productionCompanies: List<ProductionCompany>?,
    @SerializedName("belongs_to_collection") val belongsToCollection: MovieCollection?
)

/**
 * Data class representing detailed information for a TV Show.
 * @param id The unique identifier for the TV show.
 * @param name The name of the TV show.
 * @param overview A brief summary of the TV show's plot.
 * @param posterPath The path to the TV show's poster image.
 * @param backdropPath The path to the TV show's backdrop image.
 * @param voteAverage The average vote score for the TV show.
 * @param firstAirDate The first air date of the TV show.
 * @param numberOfSeasons The total number of seasons for the TV show.
 * @param numberOfEpisodes The total number of episodes for the TV show.
 * @param genres A list of [Genre] objects associated with the TV show.
 * @param networks A list of [Network] objects that broadcast the TV show.
 * @param seasons A list of [Season] objects for the TV show.
 */
data class TvShowDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int?,
    @SerializedName("genres") val genres: List<Genre>?,
    @SerializedName("networks") val networks: List<Network>?,
    @SerializedName("seasons") val seasons: List<Season>?,
    @SerializedName("status") val status: String?
)

/**
 * Data class representing a Season of a TV Show.
 * @param id The unique identifier for the season.
 * @param name The name of the season.
 * @param seasonNumber The season number.
 * @param posterPath The path to the season's poster image.
 * @param episodeCount The number of episodes in the season.
 */
data class Season(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("episode_count") val episodeCount: Int?
)

/**
 * Data class representing a response containing a list of TV Show Seasons.
 * @param id The ID of the TV show.
 * @param name The name of the TV show.
 * @param seasons The list of [Season] objects.
 */
data class TvShowSeasonResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("seasons") val seasons: List<Season>
)

/**
 * Data class representing an Episode of a TV Show.
 * @param id The unique identifier for the episode.
 * @param name The name of the episode.
 * @param overview A brief summary of the episode's plot.
 * @param stillPath The path to the episode's still image.
 * @param episodeNumber The episode number within its season.
 * @param seasonNumber The season number the episode belongs to.
 * @param voteAverage The average vote score for the episode.
 */
data class Episode(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String?,
    @SerializedName("still_path") val stillPath: String?,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("runtime") val runtime: Int?
)

/**
 * Data class representing detailed information for a Season, including its episodes.
 * @param id The unique identifier for the season.
 * @param name The name of the season.
 * @param seasonNumber The season number.
 * @param episodes The list of [Episode] objects in the season.
 */
data class SeasonDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episodes") val episodes: List<Episode>
)

/**
 * Data class representing an item in the watch history.
 */
data class WatchHistoryItem(
    val id: Int,
    val title: String,
    val overview: String? = null,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double = 0.0,
    val releaseDate: String? = null,
    val isTv: Boolean,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lastWatched: Long = System.currentTimeMillis(),
    val playbackPosition: Long = 0L
) {
    /**
     * Checks if this watch history item needs enrichment from TMDB.
     * An item needs enrichment when any of the critical fields are missing or empty.
     *
     * Fields checked:
     * - title: Must not be blank
     * - posterPath: Must not be null or blank (needed for card display)
     * - backdropPath: Must not be null or blank (needed for hero section)
     * - voteAverage: Must not be 0.0 (indicates missing data)
     * - releaseDate: Must not be null or blank
     *
     * @return true if the item needs TMDB detail enrichment, false otherwise
     */
    fun needsEnrichment(): Boolean {
        return title.isBlank() ||
                posterPath.isNullOrBlank() ||
                backdropPath.isNullOrBlank() ||
                voteAverage == 0.0 ||
                releaseDate.isNullOrBlank()
    }

    /**
     * Checks if the overview is missing.
     * Useful for deciding whether to show "No overview available" placeholder.
     *
     * @return true if overview is null or blank, false otherwise
     */
    fun hasOverview(): Boolean = !overview.isNullOrBlank()

    /**
     * Checks if the item has a valid poster image path.
     *
     * @return true if posterPath is not null or blank, false otherwise
     */
    fun hasPoster(): Boolean = !posterPath.isNullOrBlank()

    /**
     * Checks if the item has a valid backdrop image path.
     *
     * @return true if backdropPath is not null or blank, false otherwise
     */
    fun hasBackdrop(): Boolean = !backdropPath.isNullOrBlank()

    /**
     * Checks if the item has a valid vote average.
     *
     * @return true if voteAverage is greater than 0, false otherwise
     */
    fun hasVoteAverage(): Boolean = voteAverage > 0.0

    /**
     * Checks if the item has a valid release date.
     *
     * @return true if releaseDate is not null or blank, false otherwise
     */
    fun hasReleaseDate(): Boolean = !releaseDate.isNullOrBlank()

    /**
     * Gets the media type string for API calls.
     *
     * @return "movie" if this is a movie, "tv" if this is a TV show
     */
    fun getMediaType(): String = if (isTv) "tv" else "movie"

    /**
     * Creates a copy of this item with enriched data from TMDB.
     * Only updates fields that are missing in this item.
     *
     * @param enrichedTitle The title from TMDB (used if current title is blank)
     * @param enrichedOverview The overview from TMDB (used if current overview is blank)
     * @param enrichedPosterPath The poster path from TMDB (used if current is blank)
     * @param enrichedBackdropPath The backdrop path from TMDB (used if current is blank)
     * @param enrichedVoteAverage The vote average from TMDB (used if current is 0)
     * @param enrichedReleaseDate The release date from TMDB (used if current is blank)
     * @return A new WatchHistoryItem with enriched data
     */
    fun withEnrichedData(
        enrichedTitle: String?,
        enrichedOverview: String?,
        enrichedPosterPath: String?,
        enrichedBackdropPath: String?,
        enrichedVoteAverage: Double,
        enrichedReleaseDate: String?
    ): WatchHistoryItem {
        return copy(
            title = if (title.isBlank() && !enrichedTitle.isNullOrBlank()) enrichedTitle else title,
            overview = if (overview.isNullOrBlank() && !enrichedOverview.isNullOrBlank()) enrichedOverview else overview,
            posterPath = if (posterPath.isNullOrBlank() && !enrichedPosterPath.isNullOrBlank()) enrichedPosterPath else posterPath,
            backdropPath = if (backdropPath.isNullOrBlank() && !enrichedBackdropPath.isNullOrBlank()) enrichedBackdropPath else backdropPath,
            voteAverage = if (voteAverage == 0.0 && enrichedVoteAverage > 0) enrichedVoteAverage else voteAverage,
            releaseDate = if (releaseDate.isNullOrBlank() && !enrichedReleaseDate.isNullOrBlank()) enrichedReleaseDate else releaseDate
        )
    }
}

/**
 * Data class representing a video associated with a movie or TV show (e.g., a trailer).
 */
data class Video(
    @SerializedName("id") val id: String,
    @SerializedName("key") val key: String, // YouTube video key
    @SerializedName("name") val name: String,
    @SerializedName("site") val site: String, // e.g., "YouTube"
    @SerializedName("type") val type: String // e.g., "Trailer"
)

/**
 * Response class for the videos endpoint.
 */
data class VideoResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("results") val results: List<Video>
)

/**
 * Sealed class representing search results that can be either a Movie or a TV Show.
 * This allows for unified handling of multi-search results from TMDB API.
 */
sealed class SearchResult {
    abstract val id: Int
    abstract val title: String
    abstract val overview: String
    abstract val posterPath: String?
    abstract val backdropPath: String?
    abstract val voteAverage: Double
    abstract val mediaType: String

    data class MovieResult(
        override val id: Int,
        override val title: String,
        override val overview: String,
        override val posterPath: String?,
        override val backdropPath: String?,
        override val voteAverage: Double,
        override val mediaType: String = "movie"
    ) : SearchResult()

    data class TvResult(
        override val id: Int,
        override val title: String,
        override val overview: String,
        override val posterPath: String?,
        override val backdropPath: String?,
        override val voteAverage: Double,
        override val mediaType: String = "tv"
    ) : SearchResult()
}

/**
 * Data class representing a response from multi-search endpoint.
 * @param page The current page number of the results.
 * @param results The list of search results (can be movies or TV shows).
 * @param totalPages The total number of pages available.
 * @param totalResults The total number of results available.
 */
data class MultiSearchResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<MultiSearchItem>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

/**
 * Data class representing a single item from multi-search results.
 * @param id The unique identifier for the item.
 * @param title The title of the movie or name of the TV show.
 * @param name Alternative name field (used for TV shows).
 * @param overview A brief summary of the item's plot.
 * @param posterPath The path to the item's poster image.
 * @param backdropPath The path to the item's backdrop image.
 * @param voteAverage The average vote score for the item.
 * @param mediaType The type of media ("movie", "tv", or "person").
 */
data class MultiSearchItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("media_type") val mediaType: String?
) {
    fun toSearchResult(): SearchResult? {
        return when (mediaType) {
            "movie" -> SearchResult.MovieResult(
                id = id,
                title = title ?: "",
                overview = overview ?: "",
                posterPath = posterPath,
                backdropPath = backdropPath,
                voteAverage = voteAverage ?: 0.0
            )
            "tv" -> SearchResult.TvResult(
                id = id,
                title = name ?: "",
                overview = overview ?: "",
                posterPath = posterPath,
                backdropPath = backdropPath,
                voteAverage = voteAverage ?: 0.0
            )
            else -> null
        }
    }
}

/**
 * Data class representing an Oscar movie from CSV data.
 * @param film The title of the movie.
 * @param yearFilm The release year of the film.
 * @param idTmdb The TMDB ID of the movie.
 * @param originalTitle The original title of the movie.
 * @param overview The overview/synopsis of the movie.
 * @param popularity The popularity score.
 * @param posterPath The path to the poster image.
 * @param backdropPath The path to the backdrop image.
 * @param releaseDate The release date.
 * @param voteAverage The average vote score.
 * @param oscarsWon The number of Oscars won.
 * @param genres The genre names.
 */

/**
 * Data class representing a cast member.
 * @param id The unique identifier for the person.
 * @param name The name of the cast member.
 * @param character The character name they played.
 * @param profilePath The path to the profile image.
 * @param knownForDepartment The department they're known for (acting, directing, etc.).
 * @param popularity The popularity score of the cast member.
 * @param order The order of the cast member in the credits list.
 * @param overview The biography/overview of the cast member (from person details endpoint).
 */
data class CastMember(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("character") val character: String?,
    @SerializedName("profile_path") val profilePath: String?,
    @SerializedName("known_for_department") val knownForDepartment: String?,
    @SerializedName("popularity") val popularity: Double?,
    @SerializedName("order") val order: Int?,
    @SerializedName("overview") val overview: String? = null
)

/**
 * Data class representing a crew member.
 * @param id The unique identifier for the person.
 * @param name The name of the crew member.
 * @param job The job they performed.
 * @param department The department they belong to.
 * @param profilePath The path to the profile image.
 */
data class CrewMember(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("job") val job: String?,
    @SerializedName("department") val department: String?,
    @SerializedName("profile_path") val profilePath: String?
)

/**
 * Response class for movie credits endpoint.
 * @param id The TMDB ID of the movie.
 * @param cast The list of cast members.
 * @param crew The list of crew members.
 */
data class MovieCreditsResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("cast") val cast: List<CastMember>,
    @SerializedName("crew") val crew: List<CrewMember>
)

/**
 * Response class for TV show credits endpoint.
 * @param id The TMDB ID of the TV show.
 * @param cast The list of cast members.
 * @param crew The list of crew members.
 */
data class TvShowCreditsResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("cast") val cast: List<CastMember>,
    @SerializedName("crew") val crew: List<CrewMember>
)

/**
 * Response class for person movie credits endpoint.
 * @param cast The list of movies the person acted in.
 */
data class PersonMovieCreditsResponse(
    @SerializedName("cast") val cast: List<Movie>
)

/**
 * Response class for person TV show credits endpoint.
 * @param cast The list of TV shows the person acted in.
 */
data class PersonTvCreditsResponse(
    @SerializedName("cast") val cast: List<TvShow>
)

/**
 * Data class representing detailed information for a person (actor/director/etc.).
 * @param id The unique identifier for the person.
 * @param name The name of the person.
 * @param biography The biography of the person.
 * @param birthday The birthday of the person.
 * @param deathday The death day of the person (if deceased).
 * @param profilePath The path to the profile image.
 * @param knownForDepartment The department they're known for.
 * @param popularity The popularity score of the person.
 */
data class PersonDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("biography") val biography: String?,
    @SerializedName("birthday") val birthday: String?,
    @SerializedName("deathday") val deathday: String?,
    @SerializedName("profile_path") val profilePath: String?,
    @SerializedName("known_for_department") val knownForDepartment: String?,
    @SerializedName("popularity") val popularity: Double?
)

/**
 * Data class representing a combined media item (Movie or TV Show) for cast detail screen.
 */
sealed class MediaItem {
    abstract val id: Int
    abstract val title: String
    abstract val posterPath: String?
    abstract val backdropPath: String?
    abstract val voteAverage: Double
    abstract val releaseDate: String?
    abstract val mediaType: String

    data class MovieItem(
        override val id: Int,
        override val title: String,
        override val posterPath: String?,
        override val backdropPath: String?,
        override val voteAverage: Double,
        override val releaseDate: String?,
        val overview: String?,
        val popularity: Double?
    ) : MediaItem() {
        override val mediaType: String = "movie"
    }

    data class TvShowItem(
        override val id: Int,
        override val title: String,
        override val posterPath: String?,
        override val backdropPath: String?,
        override val voteAverage: Double,
        override val releaseDate: String?,
        val overview: String?,
        val popularity: Double?
    ) : MediaItem() {
        override val mediaType: String = "tv"
    }
}

/**
 * Data class representing a company from GitHub JSON list.
 */
data class GitHubCompany(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("logo_path") val logoPath: String?,
    @SerializedName("origin_country") val originCountry: String?
)

/**
 * Data class representing a network from GitHub JSON list.
 */
data class GitHubNetwork(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("logo_path") val logoPath: String?
)

/**
 * Data class representing the combined companies and networks response from GitHub.
 */
data class CompaniesNetworksResponse(
    @SerializedName("companies") val companies: List<GitHubCompany>,
    @SerializedName("networks") val networks: List<GitHubNetwork>
)

data class OscarMovie(
    val film: String,
    val yearFilm: Int,
    val idTmdb: Int?,
    val originalTitle: String?,
    val overview: String?,
    val popularity: Double?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
    val oscarsWon: Int,
    val genres: String?
) {
    /**
     * Converts OscarMovie to a standard Movie object for display.
     * Only converts if TMDB ID is available.
     */
    fun toMovie(): Movie? {
        val tmdbId = idTmdb ?: return null
        return Movie(
            id = tmdbId,
            title = originalTitle ?: film,
            overview = overview ?: "",
            posterPath = posterPath,
            backdropPath = backdropPath,
            voteAverage = voteAverage ?: 0.0,
            releaseDate = releaseDate,
            genreIds = emptyList(),
            popularity = popularity
        )
    }
}