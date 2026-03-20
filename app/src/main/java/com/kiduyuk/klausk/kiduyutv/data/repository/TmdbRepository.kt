package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmdbRepository {
    
    private val apiService = ApiClient.tmdbApiService

    suspend fun getTrendingTvToday(): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrendingTvToday()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrendingMoviesToday(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrendingMoviesToday()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrendingTvThisWeek(): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrendingTvThisWeek()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrendingMoviesThisWeek(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrendingMoviesThisWeek()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNowPlayingMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNowPlayingMovies()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopRatedMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTopRatedMovies()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopRatedTvShows(): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTopRatedTvShows()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPopularMovies()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularTvShows(): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPopularTvShows()
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovieDetail(movieId: Int): Result<MovieDetail> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMovieDetail(movieId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTvShowDetail(tvId: Int): Result<TvShowDetail> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTvShowDetail(tvId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTvShowSeasons(tvId: Int): Result<TvShowSeasonResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTvShowSeasons(tvId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeasonDetail(tvId: Int, seasonNumber: Int): Result<SeasonDetail> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSeasonDetail(tvId, seasonNumber)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovieGenres(): Result<List<Genre>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMovieGenres()
            Result.success(response.genres)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTvGenres(): Result<List<Genre>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTvGenres()
            Result.success(response.genres)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
