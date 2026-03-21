package com.kiduyuk.klausk.kiduyutv.data.api

import com.kiduyuk.klausk.kiduyutv.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface defining the TMDB API endpoints for fetching movie and TV show data.
 * It uses Retrofit to handle network requests.
 */
interface TmdbApiService {
    
    companion object {
        /** Base URL for the TMDB API. */
        const val BASE_URL = "https://api.themoviedb.org/3/"
        /** Base URL for TMDB images. */
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        /** Standard size for poster images. */
        const val POSTER_SIZE = "w500"
        /** Standard size for backdrop images. */
        const val BACKDROP_SIZE = "w1280"
        /** Standard size for logos. */
        const val LOGO_SIZE = "w200"
    }

    /** Fetches the list of TV shows trending today. */
    @GET("trending/tv/day")
    suspend fun getTrendingTvToday(
        @Query("page") page: Int = 1
    ): TvShowResponse

    /** Fetches the list of movies trending today. */
    @GET("trending/movie/day")
    suspend fun getTrendingMoviesToday(
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches the list of TV shows trending this week. */
    @GET("trending/tv/week")
    suspend fun getTrendingTvThisWeek(
        @Query("page") page: Int = 1
    ): TvShowResponse

    /** Fetches the list of movies trending this week. */
    @GET("trending/movie/week")
    suspend fun getTrendingMoviesThisWeek(
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches movies currently playing in theaters. */
    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches the top-rated movies. */
    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches the top-rated TV shows. */
    @GET("tv/top_rated")
    suspend fun getTopRatedTvShows(
        @Query("page") page: Int = 1
    ): TvShowResponse

    /** Fetches movies belonging to a specific genre. */
    @GET("discover/movie")
    suspend fun getMoviesByGenre(
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches movies belonging to a specific production company. */
    @GET("discover/movie")
    suspend fun getMoviesByCompany(
        @Query("with_companies") companyId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches TV shows belonging to a specific network. */
    @GET("discover/tv")
    suspend fun getTvShowsByNetwork(
        @Query("with_networks") networkId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TvShowResponse

    /** Fetches popular movies. */
    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1
    ): MovieResponse

    /** Fetches popular TV shows. */
    @GET("tv/popular")
    suspend fun getPopularTvShows(
        @Query("page") page: Int = 1
    ): TvShowResponse

    /** Fetches detailed information for a specific movie. */
    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Int
    ): MovieDetail

    /** Fetches detailed information for a specific TV show. */
    @GET("tv/{tv_id}")
    suspend fun getTvShowDetail(
        @Path("tv_id") tvId: Int
    ): TvShowDetail

    /** Fetches the list of seasons for a specific TV show. */
    @GET("tv/{tv_id}/seasons")
    suspend fun getTvShowSeasons(
        @Path("tv_id") tvId: Int
    ): TvShowSeasonResponse

    /** Fetches detailed information for a specific season of a TV show. */
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetail(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): SeasonDetail

    /** Fetches the list of available movie genres. */
    @GET("genre/movie/list")
    suspend fun getMovieGenres(): GenreResponse

    /** Fetches the list of available TV show genres. */
    @GET("genre/tv/list")
    suspend fun getTvGenres(): GenreResponse

    /** Fetches videos (trailers, teasers, etc.) for a specific movie. */
    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int
    ): VideoResponse

    /** Fetches videos (trailers, teasers, etc.) for a specific TV show. */
    @GET("tv/{tv_id}/videos")
    suspend fun getTvShowVideos(
        @Path("tv_id") tvId: Int
    ): VideoResponse
}
