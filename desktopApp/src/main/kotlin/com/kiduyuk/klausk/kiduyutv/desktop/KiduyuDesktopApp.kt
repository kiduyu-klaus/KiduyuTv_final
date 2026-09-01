package com.kiduyuk.klausk.kiduyutv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.desktop.model.MediaItem
import com.kiduyuk.klausk.kiduyutv.desktop.model.MediaType
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopRoute
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.PrimaryDestination
import com.kiduyuk.klausk.kiduyutv.desktop.ui.*

@Composable
fun KiduyuDesktopApp(services: DesktopServices) {
    val route = services.navigator.backStack.last()
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    services.navigator.pop()
                } else false
            }
    ) {
        when (route) {
            is DesktopRoute.Shell -> PrimaryShell(services, route.destination)
            is DesktopRoute.MovieDetail -> MediaDetailScreen(services, route.movieId, MediaType.MOVIE)
            is DesktopRoute.TvShowDetail -> MediaDetailScreen(services, route.tvId, MediaType.SERIES)
            is DesktopRoute.SeasonEpisodes -> SeasonEpisodesScreen(services, route)
            is DesktopRoute.MediaList -> MediaListScreen(services, route)
            is DesktopRoute.CastDetail -> CastDetailScreen(services, route.castId)
            is DesktopRoute.CastImages -> CastImagesScreen(services, route.castId, route.castName)
            is DesktopRoute.MediaImages -> MediaImagesScreen(services, route)
            is DesktopRoute.Videos -> VideosScreen(services, route)
            is DesktopRoute.ImageSlider -> ImageSliderScreen(services, route)
            is DesktopRoute.TraktProfile -> TraktProfileScreen(services)
            is DesktopRoute.StreamLinks -> StreamLinksScreen(services, route.request)
            is DesktopRoute.Player -> DirectPlayerScreen(services, route.request)
            is DesktopRoute.LivePlayer -> LivePlayerScreen(services, route)
        }
    }
}

@Composable
private fun PrimaryShell(services: DesktopServices, destination: PrimaryDestination) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(132.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("K", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            PrimaryDestination.entries.forEach { item ->
                NavigationRailItem(
                    selected = destination == item,
                    onClick = { services.navigator.primary(item) },
                    icon = { Text(item.shortLabel) },
                    label = { Text(item.label) },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (destination) {
                PrimaryDestination.HOME -> HomeScreen(services)
                PrimaryDestination.MOVIES -> CatalogScreen(services, MediaType.MOVIE)
                PrimaryDestination.TV_SHOWS -> CatalogScreen(services, MediaType.SERIES)
                PrimaryDestination.MY_LIST -> MyListScreen(services)
                PrimaryDestination.LIVE_TV -> LiveTvScreen(services, schedule = false)
                PrimaryDestination.SCHEDULE -> LiveTvScreen(services, schedule = true)
                PrimaryDestination.SEARCH -> SearchScreen(services)
                PrimaryDestination.SETTINGS -> SettingsScreen(services)
            }
        }
    }
}

fun DesktopServices.openMedia(item: MediaItem) {
    if (item.resolvedType == MediaType.MOVIE) {
        navigator.push(DesktopRoute.MovieDetail(item.id))
    } else {
        navigator.push(DesktopRoute.TvShowDetail(item.id))
    }
}
