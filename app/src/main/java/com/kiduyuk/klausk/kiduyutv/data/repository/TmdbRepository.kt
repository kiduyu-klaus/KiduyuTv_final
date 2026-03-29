package com.kiduyuk.klausk.kiduyutv.data.repository

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.*
import com.kiduyuk.klausk.kiduyutv.data.model.CompaniesNetworksResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import android.content.Context
import com.google.gson.reflect.TypeToken
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

    /** Fetches recommended movies for a specific movie. */
    suspend fun getRecommendedMovies(movieId: Int): Result<List<Movie>> = runCatching {
        api.getRecommendedMovies(movieId).results
    }

    /** Fetches recommended TV shows for a specific TV show. */
    suspend fun getRecommendedTvShows(tvId: Int): Result<List<TvShow>> = runCatching {
        api.getRecommendedTvShows(tvId).results
    }

    /** Fetches detailed information for a movie collection. */
    suspend fun getCollectionDetails(collectionId: Int): Result<CollectionDetail> = runCatching {
        api.getCollectionDetails(collectionId)
    }

    /**
     * Saves a media item to the watch history using SharedPreferences.
     */
    fun saveToWatchHistory(context: Context, item: WatchHistoryItem) {
        val sharedPrefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val historyJson = sharedPrefs.getString("history", "[]")
        val type = object : TypeToken<MutableList<WatchHistoryItem>>() {}.type
        val history: MutableList<WatchHistoryItem> = gson.fromJson(historyJson, type)

        // Remove existing entry for the same media item
        history.removeAll { it.id == item.id && it.isTv == item.isTv }

        // Add new entry at the beginning
        history.add(0, item)

        // Limit history to 20 items
        val limitedHistory = history.take(20)

        sharedPrefs.edit().putString("history", gson.toJson(limitedHistory)).apply()
    }

    /**
     * Retrieves the watch history from SharedPreferences.
     */
    fun getWatchHistory(context: Context): List<WatchHistoryItem> {
        val sharedPrefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val historyJson = sharedPrefs.getString("history", "[]")
        val type = object : TypeToken<List<WatchHistoryItem>>() {}.type
        return gson.fromJson(historyJson, type)
    }

    /**
     * Checks if a media item is in the watch history.
     */
    fun getWatchHistoryItem(context: Context, id: Int, isTv: Boolean): WatchHistoryItem? {
        return getWatchHistory(context).find { it.id == id && it.isTv == isTv }
    }


    /**
     * Fetches movies from a GitHub JSON list.
     * @param urlString The GitHub raw URL.
     * @return Result containing list of Movie objects.
     */
    suspend fun getGitHubMovieList(urlString: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Failed to fetch GitHub list: HTTP $responseCode")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Movie>>() {}.type
            val movies: List<Movie> = gson.fromJson(response, type)
            movies
        }
    }

    /**
     * Fetches TV shows from a GitHub JSON list.
     * @param urlString The GitHub raw URL.
     * @return Result containing list of TvShow objects.
     */
    suspend fun getGitHubTvShowList(urlString: String): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Failed to fetch GitHub list: HTTP $responseCode")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<TvShow>>() {}.type
            val tvShows: List<TvShow> = gson.fromJson(response, type)
            tvShows
        }
    }

    /**
     * Fetches companies and networks from a GitHub JSON list.
     * @param urlString The GitHub raw URL for companies_networks.json.
     * @return Result containing CompaniesNetworksResponse with companies and networks.
     */
    suspend fun getGitHubCompaniesNetworks(urlString: String): Result<CompaniesNetworksResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Failed to fetch GitHub list: HTTP $responseCode")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            val gson = com.google.gson.Gson()
            val result: CompaniesNetworksResponse = gson.fromJson(response, CompaniesNetworksResponse::class.java)
            result
        }
    }


}
