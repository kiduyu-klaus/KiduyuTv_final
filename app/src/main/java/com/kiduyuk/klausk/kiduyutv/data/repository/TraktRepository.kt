package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.trakt.*
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiService
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Trakt.tv API operations.
 * Handles data fetching and syncing with Trakt services.
 */
@Singleton
class TraktRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val traktAuthManager: TraktAuthManager
) {

    /**
     * Get the user's Trakt profile/settings.
     */
    fun getUserSettings(): Flow<Result<TraktSettings>> = flow {
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }

            val response = traktApiService.getSettings("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!))
            } else {
                emit(Result.failure(Exception("Failed to fetch settings: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get user's watch history (movies and shows).
     */
    fun getWatchHistory(page: Int = 1, limit: Int = 20): Flow<Result<TraktWatchHistoryResponse>> = flow {
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }

            val response = traktApiService.getWatchedHistory(
                token = "Bearer $token",
                page = page,
                limit = limit
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!))
            } else {
                emit(Result.failure(Exception("Failed to fetch watch history: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get user's collection (movies and shows added to library).
     */
    fun getCollection(type: String = "movies"): Flow<Result<List<TraktCollectionItem>>> = flow {
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }

            val response = traktApiService.getCollection("Bearer $token", type)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!))
            } else {
                emit(Result.failure(Exception("Failed to fetch collection: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get user's watchlist.
     */
    fun getWatchlist(type: String = "movies"): Flow<Result<List<TraktWatchlistItem>>> = flow {
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }

            val response = traktApiService.getWatchlist(
                token = "Bearer $token",
                type = type
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!))
            } else {
                emit(Result.failure(Exception("Failed to fetch watchlist: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get user's personalized recommendations.
     */
    fun getRecommendations(type: String = "movies"): Flow<Result<List<TraktRecommendation>>> = flow {
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }

            val response = traktApiService.getRecommendations("Bearer $token", type)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!))
            } else {
                emit(Result.failure(Exception("Failed to fetch recommendations: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Scrobble a movie (report watching progress).
     */
    suspend fun scrobbleMovie(
        traktId: Int? = null,
        tmdbId: Int? = null,
        progress: Float,
        status: String = "start" // start, pause, scrobble
    ): Result<TraktScrobbleResponse> {
        return try {
            val token = traktAuthManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated with Trakt.tv"))

            val ids = buildIds(traktId = traktId, tmdbId = tmdbId)

            val response = when (status.lowercase()) {
                "pause" -> traktApiService.scrobblePause(
                    token = "Bearer $token",
                    scrobble = TraktScrobbleRequest(
                        movie = TraktScrobbleMovie(ids = ids),
                        episode = null,
                        progress = progress.toDouble(),
                        appVersion = "1.0"
                    )
                )
                "scrobble", "stop" -> traktApiService.scrobbleStop(
                    token = "Bearer $token",
                    scrobble = TraktScrobbleRequest(
                        movie = TraktScrobbleMovie(ids = ids),
                        episode = null,
                        progress = progress.toDouble(),
                        appVersion = "1.0"
                    )
                )
                else -> traktApiService.scrobbleMovie(
                    token = "Bearer $token",
                    scrobble = TraktScrobbleRequest(
                        movie = TraktScrobbleMovie(ids = ids),
                        episode = null,
                        progress = progress.toDouble(),
                        appVersion = "1.0"
                    )
                )
            }

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Scrobble failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scrobble an episode (report watching progress).
     */
    suspend fun scrobbleEpisode(
        traktId: Int? = null,
        tmdbId: Int? = null,
        season: Int,
        episode: Int,
        progress: Float,
        status: String = "start"
    ): Result<TraktScrobbleResponse> {
        return try {
            val token = traktAuthManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated with Trakt.tv"))

            val ids = buildIds(traktId = traktId, tmdbId = tmdbId)

            val response = when (status.lowercase()) {
                "pause" -> traktApiService.scrobblePause(
                    token = "Bearer $token",
                    scrobble = TraktScrobbleRequest(
                        movie = null,
                        episode = TraktScrobbleEpisode(
                            ids = ids,
                            season = season,
                            episode = episode
                        ),
                        progress = progress.toDouble(),
                        appVersion = "1.0"
                    )
                )
                "scrobble", "stop" -> traktApiService.scrobbleStop(
                    token = "Bearer $token",
                    scrobble = TraktScrobbleRequest(
                        movie = null,
                        episode = TraktScrobbleEpisode(
                            ids = ids,
                            season = season,
                            episode = episode
                        ),
                        progress = progress.toDouble(),
                        appVersion = "1.0"
                    )
                )
                else -> traktApiService.scrobbleEpisode(
                    token = "Bearer $token",
                    scrobble = TraktScrobbleRequest(
                        movie = null,
                        episode = TraktScrobbleEpisode(
                            ids = ids,
                            season = season,
                            episode = episode
                        ),
                        progress = progress.toDouble(),
                        appVersion = "1.0"
                    )
                )
            }

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Scrobble failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a movie to the user's watchlist.
     */
    suspend fun addToWatchlist(traktId: Int): Result<Unit> {
        return try {
            val token = traktAuthManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated with Trakt.tv"))

            val response = traktApiService.addToWatchlist(
                token = "Bearer $token",
                items = TraktSyncItems(
                    movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = traktId, slug = "", imdb = null, tmdb = null, tvdb = null))),
                    shows = null,
                    episodes = null
                )
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add to watchlist: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a movie from the user's watchlist.
     */
    suspend fun removeFromWatchlist(traktId: Int): Result<Unit> {
        return try {
            val token = traktAuthManager.getValidAccessToken()
                ?: return Result.failure(Exception("Not authenticated with Trakt.tv"))

            val response = traktApiService.removeFromWatchlist(
                token = "Bearer $token",
                items = TraktSyncItems(
                    movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = traktId, slug = "", imdb = null, tmdb = null, tvdb = null))),
                    shows = null,
                    episodes = null
                )
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove from watchlist: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if the user is authenticated with Trakt.
     */
    suspend fun isAuthenticated(): Boolean {
        return traktAuthManager.getValidAccessToken() != null
    }

    /**
     * Get the current user's username.
     */
    fun getUsername(): String? {
        return traktAuthManager.getUsername()
    }

    /**
     * Sign out from Trakt (clear tokens).
     */
    suspend fun signOut() {
        traktAuthManager.clearTokens()
    }

    private fun buildIds(traktId: Int? = null, tmdbId: Int? = null): TraktIds {
        return TraktIds(
            trakt = traktId,
            slug = null,
            imdb = null,
            tmdb = tmdbId,
            tvdb = null
        )
    }
}
