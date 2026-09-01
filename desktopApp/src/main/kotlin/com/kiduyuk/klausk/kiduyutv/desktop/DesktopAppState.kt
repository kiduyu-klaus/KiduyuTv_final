package com.kiduyuk.klausk.kiduyutv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.kiduyuk.klausk.kiduyutv.desktop.data.*
import com.kiduyuk.klausk.kiduyutv.desktop.model.*
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopNavigator
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.PrimaryDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class DesktopAppState(
    val navigator: DesktopNavigator,
    val settings: DesktopSettings,
    val tmdb: TmdbClient,
    val providers: ProvidersClient,
    val iptv: IptvClient,
    val library: LocalLibrary,
    val imageCache: ImageCache,
    private val scope: CoroutineScope
) {
    // Current primary destination
    var currentDestination = mutableStateOf(PrimaryDestination.HOME)

    // Player state
    val playerState = mutableStateOf<PlayerState>(PlayerState.Idle)
    val playerStream = mutableStateOf<StreamItem?>(null)
    val playerTracks = mutableStateOf<List<MediaTrack>>(emptyList())
    val playerProgress = mutableStateOf(0L)
    val playerDuration = mutableStateOf(0L)

    // Loading and error states
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    fun navigateTo(destination: PrimaryDestination) {
        currentDestination.value = destination
        navigator.primary(destination)
    }

    fun goBack(): Boolean {
        return navigator.pop()
    }

    fun dispose() {
        scope.cancel()
    }
}

sealed class PlayerState {
    data object Idle : PlayerState()
    data object FetchingStreams : PlayerState()
    data class FetchingStreamsProgress(val current: Int, val total: Int, val provider: String) : PlayerState()
    data class Preparing(val stream: StreamItem) : PlayerState()
    data class Playing(val stream: StreamItem) : PlayerState()
    data class Paused(val stream: StreamItem) : PlayerState()
    data class Failed(val message: String, val stream: StreamItem?) : PlayerState()
    data object NoStreams : PlayerState()
}

data class MediaTrack(
    val id: String,
    val name: String,
    val language: String? = null,
    val type: String // "audio", "subtitle", "video"
)

@Composable
fun rememberDesktopAppState(
    services: DesktopServices
): DesktopAppState {
    val scope = rememberCoroutineScope()
    val imageCache = remember { ImageCache(scope) }
    
    return remember {
        DesktopAppState(
            navigator = services.navigator,
            settings = services.settings,
            tmdb = services.tmdb,
            providers = services.providers,
            iptv = services.iptv,
            library = services.library,
            imageCache = imageCache,
            scope = scope
        )
    }
}
