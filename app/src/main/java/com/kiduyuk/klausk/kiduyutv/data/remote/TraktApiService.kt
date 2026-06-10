package com.kiduyuk.klausk.kiduyutv.data.remote

import com.kiduyuk.klausk.kiduyutv.data.model.trakt.*
import retrofit2.Response
import retrofit2.http.*

/**
 * TraktApiService - Retrofit service interface for Trakt API
 */
interface TraktApiService {

    // ── User ────────────────────────────────────────────────────────────────
    
    /**
     * Get user profile
     */
    @GET("users/me")
    suspend fun getUserProfile(
        @Header("Authorization") token: String,
        @Query("extended") extended: String = "full" // Defaults to "full" to always pull the bio
    ): Response<TraktUser>
    
    /**
     * Get user settings
     */
    @GET("users/me/settings")
    suspend fun getSettings(
        @Header("Authorization") token: String
    ): Response<TraktSettings>
    
    /**
     * Get watched movies
     */
    @GET("users/me/watched/movies")
    suspend fun getWatchedMovies(
        @Header("Authorization") token: String
    ): Response<List<TraktWatchedMovie>>
    
    /**
     * Get watched shows (includes all seasons/episodes)
     */
    @GET("users/me/watched/shows")
    suspend fun getWatchedShows(
        @Header("Authorization") token: String
    ): Response<List<TraktWatchedShow>>
    
    // ── Sync ────────────────────────────────────────────────────────────────
    
    /**
     * Get playback progress for movies and shows
     */
    @GET("sync/playback")
    suspend fun getPlaybackProgress(
        @Header("Authorization") token: String,
        @Query("type") type: String
    ): Response<List<TraktPlaybackProgress>>
    
    /**
     * Get watchlist
     */
    @GET("users/me/watchlist/{type}")
    suspend fun getWatchlist(
        @Header("Authorization") token: String,
        @Path("type") type: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<TraktWatchlistItem>>
    
    /**
     * Add item to watchlist
     */
    @POST("sync/watchlist")
    suspend fun addToWatchlist(
        @Header("Authorization") token: String,
        @Body items: TraktSyncItems
    ): Response<TraktSyncResponse>
    
    /**
     * Remove item from watchlist
     */
    @POST("sync/watchlist/remove")
    suspend fun removeFromWatchlist(
        @Header("Authorization") token: String,
        @Body items: TraktSyncItems
    ): Response<TraktSyncResponse>
    
    // ── Scrobble ────────────────────────────────────────────────────────────
    
    /**
     * Start scrobbling (start watching) for movie
     */
    @POST("scrobble/start")
    suspend fun scrobbleMovie(
        @Header("Authorization") token: String,
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    /**
     * Start scrobbling (start watching) for episode
     */
    @POST("scrobble/start")
    suspend fun scrobbleEpisode(
        @Header("Authorization") token: String,
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    /**
     * Pause scrobbling (update progress)
     */
    @POST("scrobble/pause")
    suspend fun scrobblePause(
        @Header("Authorization") token: String,
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    /**
     * Stop scrobbling (mark as watched)
     */
    @POST("scrobble/stop")
    suspend fun scrobbleStop(
        @Header("Authorization") token: String,
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    // ── History ──────────────────────────────────────────────────────────────
    
    /**
     * Add to watch history
     */
    @POST("sync/history")
    suspend fun addToHistory(
        @Header("Authorization") token: String,
        @Body items: TraktSyncItems
    ): Response<TraktSyncResponse>
    
    /**
     * Get watch history
     */
    @GET("users/me/history")
    suspend fun getWatchedHistory(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<TraktHistoryItem>>
    
    // ── Collection ──────────────────────────────────────────────────────────
    
    /**
     * Get user's collection
     */
    @GET("users/me/collection/{type}")
    suspend fun getCollection(
        @Header("Authorization") token: String,
        @Path("type") type: String = "movies"
    ): Response<List<TraktCollectionItem>>
    
    // ── Recommendations ──────────────────────────────────────────────────────
    
    /**
     * Get user's personalized recommendations
     */
    @GET("recommendations/{type}")
    suspend fun getRecommendations(
        @Header("Authorization") token: String,
        @Path("type") type: String = "movies"
    ): Response<List<TraktRecommendation>>
}
