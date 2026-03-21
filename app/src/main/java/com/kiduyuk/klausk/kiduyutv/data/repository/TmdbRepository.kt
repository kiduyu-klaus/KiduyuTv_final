package com.kiduyuk.klausk.kiduyutv.data.repository

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.*

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
    suspend fun getMoviesByCompany(companyId: Int): Result<List<Movie>> = runCatching {
        api.getMoviesByCompany(companyId).results
    }

    /** Fetches TV shows filtered by a specific network. */
    suspend fun getTvShowsByNetwork(networkId: Int): Result<List<TvShow>> = runCatching {
        api.getTvShowsByNetwork(networkId).results
    }

    /** Fetches videos (trailers, etc.) for a specific movie. */
    suspend fun getMovieVideos(movieId: Int): Result<List<Video>> = runCatching {
        api.getMovieVideos(movieId).results
    }

    /** Fetches videos (trailers, etc.) for a specific TV show. */
    suspend fun getTvShowVideos(tvId: Int): Result<List<Video>> = runCatching {
        api.getTvShowVideos(tvId).results
    }
}
