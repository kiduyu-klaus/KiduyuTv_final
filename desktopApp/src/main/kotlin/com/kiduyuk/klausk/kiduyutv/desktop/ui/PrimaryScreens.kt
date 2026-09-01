package com.kiduyuk.klausk.kiduyutv.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.kiduyuk.klausk.kiduyutv.desktop.data.*
import com.kiduyuk.klausk.kiduyutv.desktop.model.*
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopRoute
import com.kiduyuk.klausk.kiduyutv.desktop.openMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(services: DesktopServices) {
    var sections by remember { mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload, services.settings.tmdbBearerToken) {
        loading = true
        error = null
        runCatching { services.tmdb.homeSections(services.library.history()) }
            .onSuccess { sections = it }
            .onFailure { error = it.message }
        loading = false
    }
    if (loading) return LoadingView("Loading the TV home screen…")
    error?.let { return ErrorView(it) { reload++ } }
    val hero = sections.firstOrNull()?.second?.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(item = hero, onOpen = { hero?.let(services::openMedia) })
        Spacer(Modifier.height(20.dp))
        sections.forEach { (name, items) ->
            MediaRail(name, items, services::openMedia)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Hero(item: MediaItem?, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(390.dp)) {
        RemoteImage(
            tmdbImage(item?.backdropPath, "original"),
            item?.displayTitle,
            Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF2080808), Color(0x33080808), Color.Transparent))
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart).widthIn(max = 620.dp).padding(42.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(item?.displayTitle ?: "KiduyuTV", fontSize = 38.sp, fontWeight = FontWeight.Black)
            if (!item?.overview.isNullOrBlank()) {
                Text(item?.overview.orEmpty(), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            TvActionButton("View details", onOpen, enabled = item != null)
        }
    }
}

@Composable
fun CatalogScreen(services: DesktopServices, type: MediaType) {
    var sections by remember(type) { mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()) }
    var loading by remember(type) { mutableStateOf(true) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var reload by remember(type) { mutableIntStateOf(0) }
    LaunchedEffect(type, reload, services.settings.tmdbBearerToken) {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val definitions = if (type == MediaType.MOVIE) listOf(
                    "Popular Movies" to "movie/popular",
                    "Now Playing" to "movie/now_playing",
                    "Movies Trending This Week" to "trending/movie/week",
                    "Top Rated Movies" to "movie/top_rated"
                ) else listOf(
                    "Popular TV Shows" to "tv/popular",
                    "TV Shows Trending Today" to "trending/tv/day",
                    "TV Shows Trending This Week" to "trending/tv/week",
                    "Top Rated TV Shows" to "tv/top_rated"
                )
                definitions.map { (name, path) -> async { name to services.tmdb.page(path, type) } }.awaitAll()
            }
        }.onSuccess { sections = it }.onFailure { error = it.message }
        loading = false
    }
    if (loading) return LoadingView()
    error?.let { return ErrorView(it) { reload++ } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(if (type == MediaType.MOVIE) "Movies" else "TV Shows")
        Spacer(Modifier.height(20.dp))
        sections.forEach { (title, items) ->
            MediaRail(title, items, services::openMedia)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun MyListScreen(services: DesktopServices) {
    var revision by remember { mutableIntStateOf(0) }
    val favorites = remember(revision) { services.library.favorites() }
    val history = remember(revision) { services.library.history() }
    val continueWatching = history.map {
        MediaItem(
            id = it.tmdbId,
            title = it.title.takeIf { _ -> it.mediaType == MediaType.MOVIE },
            name = it.title.takeIf { _ -> it.mediaType == MediaType.SERIES },
            posterPath = it.posterPath,
            backdropPath = it.backdropPath,
            mediaType = if (it.mediaType == MediaType.MOVIE) "movie" else "tv"
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("My List", actions = {
            TvActionButton("Refresh", { revision++ })
            TvActionButton("Trakt Profile", { services.navigator.push(DesktopRoute.TraktProfile) })
        })
        Spacer(Modifier.height(20.dp))
        if (favorites.isEmpty() && continueWatching.isEmpty()) {
            ErrorView("Your list is empty. Add titles from a movie or TV details screen.")
        } else {
            MediaRail("Saved Movies and TV Shows", favorites, services::openMedia)
            Spacer(Modifier.height(30.dp))
            MediaRail("Continue Watching", continueWatching, services::openMedia)
        }
    }
}

@Composable
fun SearchScreen(services: DesktopServices) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350L)
        loading = true
        runCatching { services.tmdb.search(query.trim()) }
            .onSuccess { results = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            label = { Text("Search movies and TV shows") },
            singleLine = true
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(24.dp)) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(results.size, key = { results[it].id }) { index ->
                val item = results[index]
                MediaCard(item, { services.openMedia(item) })
            }
        }
    }
}

@Composable
fun LiveTvScreen(services: DesktopServices, schedule: Boolean) {
    var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload, services.settings.playlistUrl) {
        if (services.settings.playlistUrl.isBlank()) return@LaunchedEffect
        loading = true
        runCatching { services.iptv.load(services.settings.playlistUrl) }
            .onSuccess { channels = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (schedule) "Live TV Schedule" else "Live TV", actions = {
            TvActionButton("Refresh", { reload++ })
        })
        if (services.settings.playlistUrl.isBlank()) {
            ErrorView("Add an M3U playlist URL in Settings to display Live TV channels.")
            return@Column
        }
        if (loading) {
            LoadingView("Loading channels…")
            return@Column
        }
        error?.let {
            ErrorView(it) { reload++ }
            return@Column
        }
        val groups = remember(channels) { channels.map { it.group }.distinct().sorted() }
        LazyRow(
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterChip(selectedGroup == null, { selectedGroup = null }, { Text("All") }) }
            items(groups) { group ->
                FilterChip(selectedGroup == group, { selectedGroup = group }, { Text(group) })
            }
        }
        if (schedule) {
            Text(
                if (services.settings.epgUrl.isBlank()) "EPG URL is not configured; showing channel schedule entries."
                else "EPG source: ${services.settings.epgUrl}",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val filtered = channels.filter { selectedGroup == null || it.group == selectedGroup }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { "${it.group}-${it.name}-${it.url}" }) { channel ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface).clickable {
                            services.navigator.push(DesktopRoute.LivePlayer(channel.name, channel.url))
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RemoteImage(channel.logo, channel.name, Modifier.size(72.dp), ContentScale.Fit)
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(channel.group, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (schedule) Text("Select to view and play this channel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("PLAY", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(services: DesktopServices) {
    var tmdbToken by remember { mutableStateOf(services.settings.tmdbBearerToken) }
    var streamToken by remember { mutableStateOf(services.settings.streamApiToken) }
    var providersUrl by remember { mutableStateOf(services.settings.providersBaseUrl) }
    var playlistUrl by remember { mutableStateOf(services.settings.playlistUrl) }
    var epgUrl by remember { mutableStateOf(services.settings.epgUrl) }
    var mpvPath by remember { mutableStateOf(services.settings.mpvPath) }
    var subtitleLanguage by remember { mutableStateOf(services.settings.preferredSubtitleLanguage) }
    var directStream by remember { mutableStateOf(services.settings.directStreamEnabled) }
    var darkTheme by remember { mutableStateOf(services.settings.darkTheme) }
    var providers by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<DesktopUpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checkingForUpdate by remember { mutableStateOf(false) }
    var installingUpdate by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    val updater = remember { DesktopUpdater() }
    LaunchedEffect(Unit) {
        runCatching { services.providers.enabledProviders() }.onSuccess { providers = it }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings", actions = {
            TvActionButton("Trakt Profile", { services.navigator.push(DesktopRoute.TraktProfile) })
        })
        Column(
            Modifier.widthIn(max = 900.dp).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Theme")
                    Text(
                        if (darkTheme) "Dark theme (white text)" else "Light theme",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = {
                        darkTheme = it
                        services.settings.darkTheme = it
                        services.darkTheme.value = it
                    }
                )
            }
            Text("Playback", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use direct streams", Modifier.weight(1f))
                Switch(directStream, { directStream = it })
            }
            SettingsField("mpv executable path", mpvPath, { mpvPath = it })
            SettingsField("Preferred subtitle language", subtitleLanguage, { subtitleLanguage = it })
            Text("Providers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SettingsField("Providers base URL", providersUrl, { providersUrl = it })
            SettingsField("Stream API token hash", streamToken, { streamToken = it }, secret = true)
            Text(
                if (providers.isEmpty()) "Enabled providers could not be loaded yet."
                else "Enabled: ${providers.joinToString()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Catalog", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SettingsField("TMDB API read-access bearer token", tmdbToken, { tmdbToken = it }, secret = true)
            Text("Live TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SettingsField("M3U playlist URL", playlistUrl, { playlistUrl = it })
            SettingsField("XMLTV EPG URL", epgUrl, { epgUrl = it })
            Text("Updates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvActionButton(
                    "Check for updates",
                    onClick = {
                        updateScope.launch {
                            checkingForUpdate = true
                            updateStatus = "Checking for updates…"
                            updateInfo = null
                            runCatching { updater.checkForUpdate() }
                                .onSuccess { result ->
                                    updateInfo = result
                                    updateStatus = if (result == null) {
                                        "You are using the latest desktop version."
                                    } else {
                                        "Version ${result.version} is available."
                                    }
                                }
                                .onFailure { error ->
                                    updateStatus = "Update check failed: ${error.message ?: "unknown error"}"
                                }
                            checkingForUpdate = false
                        }
                    },
                    enabled = !checkingForUpdate && !installingUpdate
                )
                if (checkingForUpdate) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            updateInfo?.let { available ->
                Text(
                    "Windows update ${available.version} is ready to download.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (available.exeUrl != null) {
                        TvActionButton(
                            "Download and install EXE",
                            onClick = {
                                updateScope.launch {
                                    installingUpdate = true
                                    updateStatus = "Downloading Windows EXE update…"
                                    runCatching {
                                        updater.downloadAndInstall(available, preferMsi = false) { progress ->
                                            withContext(Dispatchers.Main.immediate) {
                                                updateStatus = "Downloading update… $progress%"
                                            }
                                        }
                                    }.onSuccess {
                                        updateStatus = "Installer started. Follow the Windows installer prompts."
                                    }.onFailure { error ->
                                        updateStatus = "Update installation failed: ${error.message ?: "unknown error"}"
                                    }
                                    installingUpdate = false
                                }
                            },
                            enabled = !installingUpdate
                        )
                    }
                    if (available.msiUrl != null) {
                        TvActionButton(
                            "Download and install MSI",
                            onClick = {
                                updateScope.launch {
                                    installingUpdate = true
                                    updateStatus = "Downloading Windows MSI update…"
                                    runCatching {
                                        updater.downloadAndInstall(available, preferMsi = true) { progress ->
                                            withContext(Dispatchers.Main.immediate) {
                                                updateStatus = "Downloading update… $progress%"
                                            }
                                        }
                                    }.onSuccess {
                                        updateStatus = "Installer started. Follow the Windows installer prompts."
                                    }.onFailure { error ->
                                        updateStatus = "Update installation failed: ${error.message ?: "unknown error"}"
                                    }
                                    installingUpdate = false
                                }
                            },
                            enabled = !installingUpdate
                        )
                    }
                }
            }
            updateStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TvActionButton("Save settings", {
                services.settings.tmdbBearerToken = tmdbToken
                services.settings.streamApiToken = streamToken
                services.settings.providersBaseUrl = providersUrl
                services.settings.playlistUrl = playlistUrl
                services.settings.epgUrl = epgUrl
                services.settings.mpvPath = mpvPath
                services.settings.preferredSubtitleLanguage = subtitleLanguage
                services.settings.directStreamEnabled = directStream
                services.settings.darkTheme = darkTheme
                services.darkTheme.value = darkTheme
                message = "Settings saved"
            })
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurface) }
            HorizontalDivider()
            Text("KiduyuTV Windows ${System.getProperty("kiduyutv.version", "1.1.71")}")
            Text("The Windows application follows the Android TV screen graph.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (secret) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else androidx.compose.ui.text.input.VisualTransformation.None
    )
}
