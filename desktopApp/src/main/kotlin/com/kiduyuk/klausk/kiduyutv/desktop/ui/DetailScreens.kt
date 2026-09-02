package com.kiduyuk.klausk.kiduyutv.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.desktop.DesktopServices
import com.kiduyuk.klausk.kiduyutv.desktop.model.*
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopRoute
import com.kiduyuk.klausk.kiduyutv.desktop.openMedia
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

@Composable
fun MediaDetailScreen(services: DesktopServices, id: Int, type: MediaType) {
    var details by remember(id, type) { mutableStateOf<MediaDetails?>(null) }
    var loading by remember(id, type) { mutableStateOf(true) }
    var error by remember(id, type) { mutableStateOf<String?>(null) }
    var collection by remember(id, type) { mutableStateOf<CollectionDetails?>(null) }
    var favoriteRevision by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, type, reload) {
        loading = true
        collection = null
        try {
            val loaded = services.tmdb.details(id, type)
            details = loaded
            error = null
            if (type == MediaType.MOVIE) {
                loaded.belongsToCollection?.id?.let { collectionId ->
                    collection = runCatching { services.tmdb.collection(collectionId) }
                        .onFailure { error ->
                            com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.warn(
                                "Unable to load movie collection id={}",
                                collectionId,
                                error
                            )
                        }
                        .getOrNull()
                }
            }
        } catch (loadError: Exception) {
            error = loadError.message
        } finally {
            loading = false
        }
    }
    if (loading) return LoadingView("Loading details…")
    error?.let { return ErrorView(it) { reload++ } }
    val media = details ?: return ErrorView("No details were returned")
    val item = MediaItem(
        id = media.id,
        title = media.title,
        name = media.name,
        overview = media.overview,
        posterPath = media.posterPath,
        backdropPath = media.backdropPath,
        voteAverage = media.voteAverage,
        releaseDate = media.releaseDate,
        firstAirDate = media.firstAirDate,
        mediaType = if (type == MediaType.MOVIE) "movie" else "tv"
    )
    val favorite = remember(favoriteRevision) { services.library.isFavorite(id, type) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(520.dp)) {
            RemoteImage(tmdbImage(media.backdropPath, "original"), media.displayTitle, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            Color.Transparent
                        )
                    )
                )
            )
            ScreenHeader(media.displayTitle, { services.navigator.pop() })
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(38.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                RemoteImage(tmdbImage(media.posterPath), media.displayTitle, Modifier.width(190.dp).height(285.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(media.displayTitle, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${media.displayDate.take(4)}  •  ★ ${"%.1f".format(media.voteAverage)}  •  ${media.genres.joinToString { it.name }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(media.overview, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (type == MediaType.MOVIE) {
                            TvActionButton("▶ Play", { openPlay(services, item, null, null) })
                        } else {
                            TvActionButton("Episodes", {
                                services.navigator.push(
                                    DesktopRoute.SeasonEpisodes(id, media.displayTitle, media.numberOfSeasons.coerceAtLeast(1))
                                )
                            })
                            TvActionButton("▶ S1 E1", { openPlay(services, item, 1, 1) })
                        }
                        TvActionButton(if (favorite) "✓ My List" else "+ My List", {
                            services.library.toggleFavorite(item)
                            favoriteRevision++
                        })
                        TvActionButton("Images", {
                            services.navigator.push(DesktopRoute.MediaImages(id, type, media.displayTitle))
                        })
                        TvActionButton("Videos", {
                            services.navigator.push(DesktopRoute.Videos(id, type, media.displayTitle))
                        })
                    }
                }
            }
        }
        if (media.credits.cast.isNotEmpty()) {
            Text("Cast", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp))
            TvHorizontalLazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(media.credits.cast.take(20), key = { it.id }) { cast ->
                    Column(
                        Modifier.width(130.dp).clickable {
                            services.navigator.push(DesktopRoute.CastDetail(cast.id))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RemoteImage(
                            tmdbImage(cast.profilePath, "w185"), cast.name,
                            Modifier.size(112.dp).clip(CircleShape)
                        )
                        Text(cast.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(cast.character, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        collection?.let { collectionDetails ->
            val collectionParts = collectionDetails.parts.map { it.copy(mediaType = "movie") }
            if (collectionParts.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                MediaRail(collectionDetails.name.uppercase(), collectionParts, services::openMedia)
            }
        }
        Spacer(Modifier.height(28.dp))
        MediaRail("Recommendations", media.recommendations.results.map {
            it.copy(mediaType = if (type == MediaType.MOVIE) "movie" else "tv")
        }, services::openMedia)
        Spacer(Modifier.height(50.dp))
    }
}

private fun openPlay(
    services: DesktopServices,
    item: MediaItem,
    season: Int?,
    episode: Int?
) {
    val request = PlayRequest(
        tmdbId = item.id,
        mediaType = item.resolvedType,
        title = item.displayTitle,
        overview = item.overview,
        posterPath = item.posterPath,
        backdropPath = item.backdropPath,
        season = season,
        episode = episode
    )
    if (services.settings.directStreamEnabled) {
        services.navigator.push(DesktopRoute.Player(request))
    } else {
        services.navigator.push(DesktopRoute.StreamLinks(request))
    }
}

@Composable
fun SeasonEpisodesScreen(services: DesktopServices, route: DesktopRoute.SeasonEpisodes) {
    var selectedSeason by remember(route.tvId) { mutableIntStateOf(route.initialSeason) }
    var season by remember(route.tvId, selectedSeason) { mutableStateOf<SeasonDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route.tvId, selectedSeason) {
        loading = true
        runCatching { services.tmdb.season(route.tvId, selectedSeason) }
            .onSuccess { season = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("${route.title} — Episodes", { services.navigator.pop() })
        TvHorizontalLazyRow(
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items((1..route.totalSeasons).toList()) { number ->
                FilterChip(selectedSeason == number, { selectedSeason = number }, { Text("Season $number") })
            }
        }
        if (loading) {
            LoadingView("Loading season $selectedSeason…")
            return@Column
        }
        error?.let {
            ErrorView(it)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(330.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(season?.episodes.orEmpty(), key = { it.id }) { episode ->
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            openPlay(
                                services,
                                MediaItem(
                                    id = route.tvId,
                                    name = route.title,
                                    mediaType = "tv",
                                    backdropPath = episode.stillPath
                                ),
                                selectedSeason,
                                episode.episodeNumber
                            )
                        }.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RemoteImage(tmdbImage(episode.stillPath, "w300"), episode.name, Modifier.width(150.dp).height(90.dp))
                    Column(Modifier.weight(1f)) {
                        Text("E${episode.episodeNumber} • ${episode.name}", fontWeight = FontWeight.Bold)
                        Text(episode.overview, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        episode.runtime?.let { Text("$it min", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaListScreen(services: DesktopServices, route: DesktopRoute.MediaList) {
    val mediaType = if (route.type == "network") MediaType.SERIES else MediaType.MOVIE
    var items by remember(route) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
        runCatching { services.tmdb.discover(mediaType, route.type, route.id) }
            .onSuccess { items = it }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(route.name, { services.navigator.pop() })
        error?.let {
            ErrorView(it)
            return@Column
        }
        if (items.isEmpty()) {
            LoadingView()
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id }) { item -> MediaCard(item, { services.openMedia(item) }) }
        }
    }
}

@Composable
fun CastDetailScreen(services: DesktopServices, castId: Int) {
    var person by remember(castId) { mutableStateOf<PersonDetails?>(null) }
    var error by remember(castId) { mutableStateOf<String?>(null) }
    LaunchedEffect(castId) {
        runCatching { services.tmdb.person(castId) }.onSuccess { person = it }.onFailure { error = it.message }
    }
    val data = person
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(data?.name ?: "Cast", { services.navigator.pop() }, actions = {
            if (data != null) TvActionButton("Images", {
                services.navigator.push(DesktopRoute.CastImages(castId, data.name))
            })
        })
        error?.let {
            ErrorView(it)
            return@Column
        }
        if (data == null) {
            LoadingView()
            return@Column
        }
        Row(Modifier.padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RemoteImage(tmdbImage(data.profilePath, "h632"), data.name, Modifier.width(220.dp).height(330.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(data.name, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(data.knownForDepartment, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.biography.ifBlank { "No biography available." })
            }
        }
        MediaRail("Movies", data.movieCredits.cast.distinctBy { it.id }.map { it.copy(mediaType = "movie") }, services::openMedia)
        Spacer(Modifier.height(28.dp))
        MediaRail("TV Shows", data.tvCredits.cast.distinctBy { it.id }.map { it.copy(mediaType = "tv") }, services::openMedia)
        Spacer(Modifier.height(50.dp))
    }
}

@Composable
fun CastImagesScreen(services: DesktopServices, castId: Int, castName: String) {
    var images by remember(castId) { mutableStateOf<List<ImageItem>>(emptyList()) }
    var error by remember(castId) { mutableStateOf<String?>(null) }
    LaunchedEffect(castId) {
        runCatching { services.tmdb.person(castId).images.profiles }
            .onSuccess { images = it }.onFailure { error = it.message }
    }
    GalleryScreen(
        title = "$castName — Images",
        images = images,
        error = error,
        onBack = { services.navigator.pop() },
        onImage = { index ->
            services.navigator.push(
                DesktopRoute.ImageSlider(images.map { tmdbImage(it.filePath, "original").orEmpty() }, index)
            )
        }
    )
}

@Composable
fun MediaImagesScreen(services: DesktopServices, route: DesktopRoute.MediaImages) {
    var images by remember(route) { mutableStateOf<List<ImageItem>>(emptyList()) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
        runCatching { services.tmdb.images(route.mediaId, route.type) }
            .onSuccess { images = it.backdrops + it.posters }.onFailure { error = it.message }
    }
    GalleryScreen(
        title = "${route.title} — Images", images = images, error = error,
        onBack = { services.navigator.pop() },
        onImage = { index ->
            services.navigator.push(
                DesktopRoute.ImageSlider(images.map { tmdbImage(it.filePath, "original").orEmpty() }, index)
            )
        }
    )
}

@Composable
private fun GalleryScreen(
    title: String,
    images: List<ImageItem>,
    error: String?,
    onBack: () -> Unit,
    onImage: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        error?.let {
            ErrorView(it)
            return@Column
        }
        if (images.isEmpty()) {
            LoadingView("Loading images…")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(250.dp), contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(images.size) { index ->
                RemoteImage(
                    tmdbImage(images[index].filePath, "w500"), title,
                    Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp)).clickable { onImage(index) }
                )
            }
        }
    }
}

@Composable
fun VideosScreen(services: DesktopServices, route: DesktopRoute.Videos) {
    var videos by remember(route) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
        runCatching { services.tmdb.videos(route.mediaId, route.type).results }
            .onSuccess { videos = it }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("${route.title} — Videos", { services.navigator.pop() })
        error?.let {
            ErrorView(it)
            return@Column
        }
        if (videos.isEmpty()) {
            LoadingView("Loading videos…")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(320.dp), contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        if (video.site.equals("YouTube", true)) {
                            runCatching { Desktop.getDesktop().browse(URI("https://www.youtube.com/watch?v=${video.key}")) }
                        }
                    }
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("▶ ${video.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${video.type} • ${video.site}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageSliderScreen(services: DesktopServices, route: DesktopRoute.ImageSlider) {
    var index by remember(route) { mutableIntStateOf(route.initialIndex.coerceIn(0, (route.urls.size - 1).coerceAtLeast(0))) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RemoteImage(route.urls.getOrNull(index), "Image ${index + 1}", Modifier.fillMaxSize(), ContentScale.Fit)
        ScreenHeader("${index + 1} / ${route.urls.size}", { services.navigator.pop() }, actions = {
            TvActionButton("Previous", { if (route.urls.isNotEmpty()) index = (index - 1 + route.urls.size) % route.urls.size })
            TvActionButton("Next", { if (route.urls.isNotEmpty()) index = (index + 1) % route.urls.size })
        })
    }
}

@Composable
fun TraktProfileScreen(services: DesktopServices) {
    var selectedShelf by remember { mutableStateOf(TraktShelfType.COLLECTION) }
    var profile by remember { mutableStateOf<TraktProfile?>(null) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(services.trakt.isAuthenticated) }
    var error by remember { mutableStateOf<String?>(null) }
    var deviceCode by remember { mutableStateOf<TraktDeviceCode?>(null) }
    var authAttempt by remember { mutableIntStateOf(0) }
    var authRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedShelf, authRevision) {
        if (!services.trakt.isAuthenticated) {
            profile = null
            media = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            profile = services.trakt.profile()
            services.trakt.shelf(selectedShelf)
        }.onSuccess { media = it }
            .onFailure { error = it.message ?: "Unable to load Trakt data" }
        loading = false
    }

    LaunchedEffect(authAttempt) {
        if (authAttempt == 0) return@LaunchedEffect
        loading = true
        error = null
        deviceCode = null
        runCatching {
            val code = services.trakt.requestDeviceCode()
            deviceCode = code
            runCatching { Desktop.getDesktop().browse(URI(code.verificationUrl)) }
            services.trakt.awaitDeviceAuthorization(code)
        }.onSuccess {
            profile = it
            deviceCode = null
            authRevision++
        }.onFailure {
            error = it.message ?: "Trakt authentication failed"
            deviceCode = null
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Trakt Profile", { services.navigator.pop() }, actions = {
            if (services.trakt.isAuthenticated) {
                TvActionButton("Refresh", { authRevision++ })
                TvActionButton("Disconnect", {
                    services.trakt.signOut()
                    profile = null
                    media = emptyList()
                    error = null
                    authRevision++
                })
            }
        })

        if (!services.trakt.isAuthenticated && deviceCode == null && !loading) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Connect Trakt.tv", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Sync your collection, watchlist, recommendations, and playback activity.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
                TvActionButton("Connect with Trakt.tv", { authAttempt++ })
            }
            return@Column
        }

        deviceCode?.let { code ->
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Authorize KiduyuTV", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Open ${code.verificationUrl} and enter:")
                Text(code.userCode.uppercase(), fontSize = 44.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvActionButton("Open Trakt", { runCatching { Desktop.getDesktop().browse(URI(code.verificationUrl)) } })
                    TvActionButton("Copy code", {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code.userCode), null)
                    })
                }
                CircularProgressIndicator(Modifier.size(32.dp))
                Text("Waiting for authorization…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (services.trakt.isAuthenticated) {
            profile?.let { user ->
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    RemoteImage(user.avatarUrl, user.username, Modifier.size(76.dp).clip(CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(user.username, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        user.name?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        user.about?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TraktShelfType.entries.forEach { shelf ->
                    FilterChip(
                        selected = selectedShelf == shelf,
                        onClick = { selectedShelf = shelf },
                        label = { Text(shelf.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            error?.let { Text(it, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error) }
            if (loading && media.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (media.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No items found in ${selectedShelf.name.lowercase()}.")
                }
            } else {
                Spacer(Modifier.height(18.dp))
                MediaRail(selectedShelf.name.lowercase().replaceFirstChar(Char::uppercase), media, services::openMedia)
            }
        }
    }
}
