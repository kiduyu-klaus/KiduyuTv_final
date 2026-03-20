package com.kiduyuk.klausk.kiduyutv.data.api

import com.kiduyuk.klausk.kiduyutv.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    
    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        const val POSTER_SIZE = "w500"
        const val BACKDROP_SIZE = "w1280"
        const val LOGO_SIZE = "w200"
    }

    @GET("trending/tv/day")
    suspend fun getTrendingTvToday(
        @Query("page") page: Int = 1
    ): TvShowResponse

    @GET("trending/movie/day")
    suspend fun getTrendingMoviesToday(
        @Query("page") page: Int = 1
    ): MovieResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTvThisWeek(
        @Query("page") page: Int = 1
    ): TvShowResponse

    @GET("trending/movie/week")
    suspend fun getTrendingMoviesThisWeek(
        @Query("page") page: Int = 1
    ): MovieResponse

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = 1
    ): MovieResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1
    ): MovieResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedTvShows(
        @Query("page") page: Int = 1
    ): TvShowResponse

    @GET("discover/movie")
    suspend fun getMoviesByGenre(
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): MovieResponse

    @GET("discover/tv")
    suspend fun getTvShowsByNetwork(
        @Query("with_networks") networkId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TvShowResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1
    ): MovieResponse

    @GET("tv/popular")
    suspend fun getPopularTvShows(
        @Query("page") page: Int = 1
    ): TvShowResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Int
    ): MovieDetail

    @GET("tv/{tv_id}")
    suspend fun getTvShowDetail(
        @Path("tv_id") tvId: Int
    ): TvShowDetail

    @GET("tv/{tv_id}/seasons")
    suspend fun getTvShowSeasons(
        @Path("tv_id") tvId: Int
    ): TvShowSeasonResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetail(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): SeasonDetail

    @GET("genre/movie/list")
    suspend fun getMovieGenres(): GenreResponse

    @GET("genre/tv/list")
    suspend fun getTvGenres(): GenreResponse
}
