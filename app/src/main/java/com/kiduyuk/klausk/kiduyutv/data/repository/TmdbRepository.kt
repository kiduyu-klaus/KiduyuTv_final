package com.kiduyuk.klausk.kiduyutv.data.repository

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository class that handles data operations, specifically fetching data from the TMDB API.
 * It provides methods for movies, TV shows, genres, and specific content filtered by various criteria.
 */
class TmdbRepository {
    // Lazy initialization of the TMDB API service.
    private val api = ApiClient.tmdbApiService

    companion object {
        private const val TAG = "TmdbRepository"
    }

    /** Fetches trending TV shows for today. */
    suspend fun getTrendingTvToday(): Result<List<TvShow>> = runCatching {
        api.getTrendingTvToday().results
    }

    /** Fetches trending movies for today. */
    suspend fun getTrendingMoviesToday(): Result<List<Movie>> = runCatching {
        api.getTrendingMoviesToday().results
    }

    /** Fetches trending TV shows for the week. */
    suspend fun getTrendingTvThisWeek(): Result<List<TvShow>> = runCatching {
        api.getTrendingTvThisWeek().results
    }

    /** Fetches trending movies for the week. */
    suspend fun getTrendingMoviesThisWeek(): Result<List<Movie>> = runCatching {
        api.getTrendingMoviesThisWeek().results
    }

    /** Fetches movies currently playing in theaters. */
    suspend fun getNowPlayingMovies(): Result<List<Movie>> = runCatching {
        api.getNowPlayingMovies().results
    }

    /** Fetches top-rated movies. */
    suspend fun getTopRatedMovies(): Result<List<Movie>> = runCatching {
        api.getTopRatedMovies().results
    }

    /** Fetches top-rated TV shows. */
    suspend fun getTopRatedTvShows(): Result<List<TvShow>> = runCatching {
        api.getTopRatedTvShows().results
    }

    /** Fetches popular movies. */
    suspend fun getPopularMovies(): Result<List<Movie>> = runCatching {
        api.getPopularMovies().results
    }

    /** Fetches popular TV shows. */
    suspend fun getPopularTvShows(): Result<List<TvShow>> = runCatching {
        api.getPopularTvShows().results
    }

    /** Fetches detailed information for a specific movie. */
    suspend fun getMovieDetail(movieId: Int): Result<MovieDetail> = runCatching {
        api.getMovieDetail(movieId)
    }

    /** Fetches detailed information for a specific TV show. */
    suspend fun getTvShowDetail(tvId: Int): Result<TvShowDetail> = runCatching {
        api.getTvShowDetail(tvId)
    }

    /** Fetches detailed information for a specific season of a TV show. */
    suspend fun getSeasonDetail(tvId: Int, seasonNumber: Int): Result<SeasonDetail> = runCatching {
        api.getSeasonDetail(tvId, seasonNumber)
    }

    /** Fetches the list of available movie genres. */
    suspend fun getMovieGenres(): Result<List<Genre>> = runCatching {
        api.getMovieGenres().genres
    }

    /** Fetches the list of available TV show genres. */
    suspend fun getTvGenres(): Result<List<Genre>> = runCatching {
        api.getTvGenres().genres
    }

    /** Fetches movies filtered by a specific production company. */
    suspend fun getMoviesByCompany(companyId: Int, page: Int = 1): Result<MovieResponse> = runCatching {
        api.getMoviesByCompany(companyId, page = page)
    }

    /** Fetches TV shows filtered by a specific network. */
    suspend fun getTvShowsByNetwork(networkId: Int, page: Int = 1): Result<TvShowResponse> = runCatching {
        api.getTvShowsByNetwork(networkId, page = page)
    }

    /** Fetches details for a specific network, including its logo. */
    suspend fun getNetworkDetails(networkId: Int): Result<Network> = runCatching {
        api.getNetworkDetails(networkId)
    }

    /** Fetches details for a specific production company, including its logo. */
    suspend fun getCompanyDetails(companyId: Int): Result<ProductionCompany> = runCatching {
        api.getCompanyDetails(companyId)
    }

    /** Fetches videos (trailers, etc.) for a specific movie. */
    suspend fun getMovieVideos(movieId: Int): Result<List<Video>> = runCatching {
        api.getMovieVideos(movieId).results
    }

    /** Fetches videos (trailers, etc.) for a specific TV show. */
    suspend fun getTvShowVideos(tvId: Int): Result<List<Video>> = runCatching {
        api.getTvShowVideos(tvId).results
    }

    /** Searches for movies matching a query string. */
    suspend fun searchMovies(query: String): Result<List<Movie>> = runCatching {
        api.searchMovies(query).results
    }

    /** Searches for TV shows matching a query string. */
    suspend fun searchTvShows(query: String): Result<List<TvShow>> = runCatching {
        api.searchTvShows(query).results
    }

    /** Searches for both movies and TV shows matching a query string. */
    suspend fun searchMulti(query: String): Result<List<SearchResult>> = runCatching {
        val response = api.searchMulti(query)
        response.results.mapNotNull { it.toSearchResult() }
    }

    /**
     * Fetches Oscar movies from the CSV data source.
     * Filters by year_film starting from the specified year (default 2025).
     * Only returns movies that have a valid TMDB ID.
     * @param fromYear The starting year to filter movies (default 2025).
     * @return Result containing list of OscarMovie objects.
     */
    suspend fun getOscarMovies(fromYear: Int = 2025): Result<List<OscarMovie>> = withContext(Dispatchers.IO) {
        runCatching {
            val csvUrl = "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/the_oscar_tmdb.csv"
            val url = URL(csvUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "text/csv")

            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Failed to fetch CSV: HTTP $responseCode")
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val lines = mutableListOf<String>()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    lines.add(line!!)
                }
                reader.close()

                if (lines.isEmpty()) {
                    return@runCatching emptyList<OscarMovie>()
                }

                // Parse CSV (skip header)
                val movies = lines.drop(1).mapNotNull { csvLine ->
                    parseOscarMovieCsvLine(csvLine)
                }.filter { it.yearFilm >= fromYear && it.idTmdb != null && it.idTmdb > 0 }
                    .sortedByDescending { it.yearFilm }
                    .take(20) // Limit to top 20 movies

                movies
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Parses a single CSV line into an OscarMovie object.
     * Handles quoted fields and escaped commas.
     */
    private fun parseOscarMovieCsvLine(line: String): OscarMovie? {
        return try {
            val fields = parseCsvLine(line)

            if (fields.size < 19) return null

            OscarMovie(
                film = fields.getOrNull(0)?.trim() ?: "",
                yearFilm = fields.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                idTmdb = fields.getOrNull(7)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it.toIntOrNull()
                },
                originalTitle = fields.getOrNull(9)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it
                },
                overview = fields.getOrNull(10)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it
                },
                popularity = fields.getOrNull(11)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it.toDoubleOrNull()
                },
                posterPath = fields.getOrNull(12)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it
                },
                backdropPath = fields.getOrNull(6)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it
                },
                releaseDate = fields.getOrNull(13)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it
                },
                voteAverage = fields.getOrNull(15)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it.toDoubleOrNull()
                },
                oscarsWon = fields.getOrNull(4)?.trim()?.toIntOrNull() ?: 0,
                genres = fields.getOrNull(18)?.trim()?.let {
                    if (it == "NA" || it.isBlank()) null else it
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CSV line: ${e.message}")
            null
        }
    }

    /**
     * Parses a CSV line handling quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]

            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
