package com.kiduyuk.klausk.kiduyutv.desktop.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kiduyuk.klausk.kiduyutv.desktop.model.MediaType
import com.kiduyuk.klausk.kiduyutv.desktop.model.PlayRequest

enum class PrimaryDestination(val label: String, val shortLabel: String) {
    HOME("Home", "H"),
    MOVIES("Movies", "M"),
    TV_SHOWS("TV Shows", "TV"),
    MY_LIST("My List", "+"),
    LIVE_TV("Live TV", "LIVE"),
    SCHEDULE("Schedule", "EPG"),
    SEARCH("Search", "?"),
    SETTINGS("Settings", "⚙")
}

sealed interface DesktopRoute {
    data class Shell(val destination: PrimaryDestination = PrimaryDestination.HOME) : DesktopRoute
    data class MovieDetail(val movieId: Int) : DesktopRoute
    data class TvShowDetail(val tvId: Int) : DesktopRoute
    data class SeasonEpisodes(
        val tvId: Int,
        val title: String,
        val totalSeasons: Int,
        val initialSeason: Int = 1
    ) : DesktopRoute
    data class MediaList(val type: String, val id: Int, val name: String) : DesktopRoute
    data class CastDetail(val castId: Int) : DesktopRoute
    data class CastImages(val castId: Int, val castName: String) : DesktopRoute
    data class MediaImages(val mediaId: Int, val type: MediaType, val title: String) : DesktopRoute
    data class Videos(val mediaId: Int, val type: MediaType, val title: String) : DesktopRoute
    data class ImageSlider(val urls: List<String>, val initialIndex: Int) : DesktopRoute
    data object TraktProfile : DesktopRoute
    data class StreamLinks(val request: PlayRequest) : DesktopRoute
    data class Player(val request: PlayRequest) : DesktopRoute
    data class LivePlayer(val name: String, val url: String, val headers: Map<String, String> = emptyMap()) : DesktopRoute
}

class DesktopNavigator {
    val backStack: SnapshotStateList<DesktopRoute> = mutableStateListOf(DesktopRoute.Shell())
    val current: DesktopRoute get() = backStack.last()

    fun primary(destination: PrimaryDestination) {
        backStack.clear()
        backStack += DesktopRoute.Shell(destination)
    }

    fun push(route: DesktopRoute) {
        if (backStack.lastOrNull() != route) backStack += route
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
