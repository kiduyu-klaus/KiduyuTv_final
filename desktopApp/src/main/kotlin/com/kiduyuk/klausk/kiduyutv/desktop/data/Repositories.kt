package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.kiduyuk.klausk.kiduyutv.desktop.model.*

interface CatalogRepository {
    suspend fun home(): List<Pair<String, List<MediaItem>>>
    suspend fun search(query: String): List<MediaItem>
    suspend fun details(id: Int, type: MediaType): MediaDetails
    suspend fun season(tvId: Int, seasonNumber: Int): SeasonDetails
    suspend fun person(id: Int): PersonDetails
    suspend fun images(id: Int, type: MediaType): Images
    suspend fun videos(id: Int, type: MediaType): Videos
    suspend fun discover(type: MediaType, filterName: String, filterId: Int): List<MediaItem>
}

interface StreamRepository {
    suspend fun enabledProviders(): List<String>
    suspend fun streams(
        request: PlayRequest,
        provider: String? = null,
        onProgress: suspend (current: Int, total: Int, provider: String) -> Unit = { _, _, _ -> }
    ): List<StreamItem>
}

interface WatchHistoryRepository {
    suspend fun find(request: PlayRequest): WatchProgress?
    suspend fun save(progress: WatchProgress)
    suspend fun getAll(): List<WatchProgress>
    suspend fun delete(request: PlayRequest)
    suspend fun clear()
}

interface FavoritesRepository {
    suspend fun getFavorites(): List<MediaItem>
    suspend fun toggleFavorite(item: MediaItem): Boolean
    suspend fun isFavorite(id: Int, type: MediaType): Boolean
}

class DesktopCatalogRepository(
    private val tmdb: TmdbClient,
    private val library: LocalLibrary
) : CatalogRepository {
    override suspend fun home(): List<Pair<String, List<MediaItem>>> {
        val history = library.history()
        return tmdb.homeSections(history)
    }

    override suspend fun search(query: String): List<MediaItem> =
        tmdb.search(query)

    override suspend fun details(id: Int, type: MediaType): MediaDetails =
        tmdb.details(id, type)

    override suspend fun season(tvId: Int, seasonNumber: Int): SeasonDetails =
        tmdb.season(tvId, seasonNumber)

    override suspend fun person(id: Int): PersonDetails =
        tmdb.person(id)

    override suspend fun images(id: Int, type: MediaType): Images =
        tmdb.images(id, type)

    override suspend fun videos(id: Int, type: MediaType): Videos =
        tmdb.videos(id, type)

    override suspend fun discover(
        type: MediaType,
        filterName: String,
        filterId: Int
    ): List<MediaItem> = tmdb.discover(type, filterName, filterId)
}

class DesktopStreamRepository(
    private val providers: ProvidersClient
) : StreamRepository {
    override suspend fun enabledProviders(): List<String> =
        providers.enabledProviders()

    override suspend fun streams(
        request: PlayRequest,
        provider: String?,
        onProgress: suspend (current: Int, total: Int, provider: String) -> Unit
    ): List<StreamItem> {
        return if (provider != null) {
            // Single provider
            providers.streams(request, provider)
        } else {
            // Multi-provider with progress
            val enabledProviders = enabledProviders()
            val allStreams = mutableListOf<StreamItem>()

            enabledProviders.forEachIndexed { index, providerName ->
                onProgress(index + 1, enabledProviders.size, providerName)
                try {
                    val streams = providers.streams(request, providerName)
                    allStreams.addAll(streams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            allStreams
        }
    }
}

class DesktopWatchHistoryRepository(
    private val database: DatabaseWatchHistoryStore
) : WatchHistoryRepository {
    override suspend fun find(request: PlayRequest): WatchProgress? =
        database.find(request)

    override suspend fun save(progress: WatchProgress) =
        database.save(progress)

    override suspend fun getAll(): List<WatchProgress> =
        database.getAll()

    override suspend fun delete(request: PlayRequest) =
        database.delete(request)

    override suspend fun clear() =
        database.clear()
}

class DesktopFavoritesRepository(
    private val library: LocalLibrary
) : FavoritesRepository {
    override suspend fun getFavorites(): List<MediaItem> =
        library.favorites()

    override suspend fun toggleFavorite(item: MediaItem): Boolean =
        library.toggleFavorite(item)

    override suspend fun isFavorite(id: Int, type: MediaType): Boolean =
        library.isFavorite(id, type)
}
