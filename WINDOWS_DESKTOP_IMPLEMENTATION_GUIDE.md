# KiduyuTV Windows Desktop Implementation Guide

## Goal

Extend KiduyuTV from two Android outputs to three supported applications:

| Target | Existing/New | Package |
|---|---|---|
| Android phone/tablet | Existing | `com.kiduyuk.klausk.kiduyutv.phone` APK |
| Android TV/Fire TV | Existing | `com.kiduyuk.klausk.kiduyutv.tv` APK |
| Windows 10/11 x64 | New | `KiduyuTV.exe` installer, optionally an MSI |

The Windows application should retain the KiduyuTV visual identity, call the same services, and display **every screen and user flow exposed by the Android TV version**. The TV flavor is the desktop product baseline; the Windows application must not be a reduced phone-style or five-screen companion.

The Windows build must **not** be added as a third Android product flavor. Android flavors only produce Android artifacts. Add a separate JVM/Compose Desktop module beside the existing `:app` module.

## Recommended architecture

Use **Compose Multiplatform Desktop** for the Windows interface and **mpv/libmpv** for video playback.

```text
KiduyuTv_final_room/
├── app/                         Existing Android application
│   ├── src/phone/               Phone-specific resources/code
│   ├── src/tv/                  TV-specific resources/code
│   └── src/main/                Shared Android code
├── shared/                      New pure Kotlin shared logic
│   └── src/commonMain/kotlin/
│       ├── catalog/
│       ├── model/
│       ├── providers/
│       ├── playback/
│       └── history/
├── desktopApp/                  New Windows application
│   ├── src/main/kotlin/
│   │   ├── Main.kt
│   │   ├── ui/
│   │   ├── player/
│   │   ├── settings/
│   │   └── history/
│   ├── src/main/resources/
│   │   └── kiduyutv.ico
│   └── resources/windows-x64/   Optional bundled mpv runtime
└── docs/
```

```text
                         +-----------------------+
Android phone APK ------>|                       |
Android TV APK --------->| shared Kotlin logic   |----> KiduyuTV Providers API
Windows EXE ------------>|                       |----> TMDB / Firebase services
                         +-----------------------+
                            |               |
                      Media3 player       mpv player
                       (Android)          (Windows)
```

Start with a small `desktopApp` module, then extract stable pure-Kotlin code into `shared`. Do not begin by moving the whole Android app into Kotlin Multiplatform; Android framework imports make that migration unnecessarily risky.

## TV-to-Windows screen parity requirement

The authoritative inventory is the TV `NavGraph.kt`, its TV screen packages, and the player activities launched from that graph. Whenever a TV destination is added later, the Windows destination inventory and parity test must be updated in the same change.

Windows should reproduce the TV screen's information, actions, loading/empty/error states, and navigation result. It does not need to reuse the Android composable source verbatim; each screen should use shared state/repositories with a Compose Desktop renderer.

### Primary TV destinations

| TV route/screen | Windows destination | Required behavior |
|---|---|---|
| `HomeScreen` / `home` | `Home` | Hero/backdrop, all TV home rails and curated list sections, continue watching, movie/TV selection, search and settings actions |
| `MoviesScreen` / `movies` | `Movies` | All movie categories/rails, pagination where present, focus restoration and movie details navigation |
| `TvShowsScreen` / `tv_shows` | `TvShows` | All TV categories/rails, pagination where present, focus restoration and show details navigation |
| `MyListScreen` / `my_list` | `MyList` | Saved movies/shows, companies, networks and cast entries, with the same empty and sync states |
| `LiveTvScreen` / `live_tv` | `LiveTv` | Channel tabs, groups, favorites, logos, EPG metadata, cached/scraped channel states and playback launch |
| `LiveTvScreen(initialTab = 1)` / `schedule` | `Schedule` | Schedule/EPG tab opened directly, event selection and channel playback |
| `SearchScreen` / `search` | `Search` | TV-style search field, keyboard focus, loading state, movie and TV results |
| `SettingsScreen` / `settings` | `Settings` | Account, Firebase sync, Trakt, My List, channels, playback, provider and app/update settings that have desktop equivalents |
| `TraktProfileScreen` / `trakt_profile` | `TraktProfile` | Profile, collection/watchlist/recommendations and movie/show navigation |

### Detail, discovery and gallery destinations

| TV route/screen | Windows destination | Required behavior |
|---|---|---|
| `MovieDetailScreen` / `movie/{movieId}` | `MovieDetail(movieId)` | Backdrop, metadata, cast, recommendations, images, trailers, list state and play action |
| `TvShowDetailScreen` / `tv/{tvId}` | `TvShowDetail(tvId)` | Backdrop, metadata, cast, recommendations, seasons, images, trailers, list state and play action |
| `SeasonEpisodesScreen` | `SeasonEpisodes(tvId, totalSeasons)` | Season selector, episode cards, episode metadata, watched progress and play action |
| `StreamLinksScreen` | `StreamLinks(playRequest)` | Provider/WebView selection only when direct streaming is disabled; direct mode bypasses it just as the TV app does |
| `MediaListScreen` | `MediaList(type, id, name)` | Company/network media list, pagination and movie/show navigation |
| `CastDetailScreen` | `CastDetail(castId)` | Cast profile, credits, gallery action and media navigation |
| `CastImagesScreen` | `CastImages(castId)` | Focusable image grid and image-slider navigation |
| `MovieImagesScreen` | `MovieImages(movieId)` | Movie backdrop/poster grid and image-slider navigation |
| TV images route using `MovieImagesScreen(isTvShow = true)` | `TvShowImages(tvId)` | TV backdrop/poster grid and image-slider navigation |
| `VideosScreen` | `Videos(mediaId, isTv)` | Trailer/video list and YouTube playback launch |
| `ImageSliderScreen` | `ImageSlider(images, initialIndex)` | Full-window image viewing, previous/next controls and correct back behavior |

### Player and intermediate flows

| TV flow | Windows equivalent |
|---|---|
| `DirectStreamActivity` | Native Windows direct-stream player using mpv/libmpv, including streams, tracks, subtitles, history, retry and episode controls |
| `IptvPlayerActivity` | Windows live-TV player with EPG, tracks, retry/fallback and channel metadata |
| `SchedulePlayerActivity` | Windows schedule server/sniffer flow where required by the selected Live TV source |
| `PlayerActivity` WebView playback | Desktop WebView/JCEF flow only when direct streaming is disabled and the source requires it |
| `YouTubePlayerActivity` | Embedded browser/player or launch the canonical YouTube URL in the user's browser |
| `CloudflareBypassActivity` | A narrowly scoped cookie-acquisition browser flow, only for providers that require clearance |
| Exit, no-stream, retry, stream, track, subtitle and update dialogs | Material Desktop dialogs with keyboard and remote focus parity |

Splash/startup, update checking, authentication state restoration, error recovery and deep-link routing are also product flows and must be implemented even though they are not ordinary `NavGraph` destinations.

### Parity rules

- Use TV screen names and navigation behavior as the source of truth, not `MobileNavGraph`.
- Preserve every TV content section and its ordering unless a documented desktop UX decision changes it.
- Preserve back-stack behavior: one Back/Escape action must pop one destination, never require a duplicate click.
- Preserve focus after returning from details or playback to a content rail/channel item.
- Every actionable card, tab, setting and dialog button must support mouse, keyboard, and D-pad input.
- Replace Android-only implementation details, not features. For example, replace an Android `Intent` with a desktop route/player command while keeping the same user action.
- If a feature has no viable desktop SDK, show a clear unsupported state during development; do not silently omit the entire screen.
- Android banner-ad SDK code is not portable. Keep the TV layout usable without it, then add a desktop-supported ad implementation only if one is selected.

## What can and cannot be reused

### Reuse or extract

- Provider response models and JSON parsing.
- `/api/providers` and `/api/streams/...` request construction.
- Movie/series metadata models.
- Stream quality ranking and the 1080p automatic ceiling.
- Provider, stream, audio, subtitle, and episode selection rules.
- Watch-history merge rules: select the newest matching local or remote entry.
- Repository interfaces and view-model state.
- Material color, typography, spacing, and component concepts.

### Replace on Windows

| Android implementation | Windows replacement |
|---|---|
| `Activity`, `Intent`, `Context` | Compose Desktop `Window` and navigation state |
| Media3/ExoPlayer | mpv process for the first release; embedded libmpv later |
| Android Room | SQLite JDBC or SQLDelight |
| `SharedPreferences` | `java.util.prefs.Preferences` or a local JSON/SQLite settings store |
| Android Firebase SDK | An authenticated backend/REST client or a shared sync service |
| Android WebView sniffer | Do not ship initially; later use JCEF only if truly required |
| Android ad SDKs | A desktop-supported ad product or no ads on Windows |
| Android resource XML | Compose Desktop components and JVM resources |
| Glide/Android Coil | Coil 3 Multiplatform or a desktop-compatible image loader |

## Phase 1: create the Windows module

### 1. Add the module to `settings.gradle`

Keep the existing Android module and add the desktop module:

```groovy
rootProject.name = "KiduyuTv"
include ':app'
include ':desktopApp'

// Add this in phase 2, after the desktop MVP works.
// include ':shared'
```

### 2. Add desktop plugins at the root

The repository currently uses Kotlin `2.1.10` and Java 17. Select a Compose Multiplatform plugin version that is compatible with that Kotlin version; verify it using the official compatibility table before pinning it.

```groovy
plugins {
    id 'com.android.application' version '8.13.2' apply false
    id 'org.jetbrains.kotlin.android' version '2.1.10' apply false
    id 'org.jetbrains.kotlin.plugin.compose' version '2.1.10' apply false

    // New desktop plugins
    id 'org.jetbrains.kotlin.jvm' version '2.1.10' apply false
    id 'org.jetbrains.compose' version '<compatible-compose-version>' apply false
}
```

Do not guess the Compose version. Upgrade Kotlin and Compose together in a separate commit if the currently pinned Kotlin version is not supported.

### 3. Create `desktopApp/build.gradle`

The repository uses Groovy Gradle files, so keep the desktop module consistent:

```groovy
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id 'org.jetbrains.kotlin.jvm'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'org.jetbrains.compose'
}

group = 'com.kiduyuk.klausk.kiduyutv'
version = providers.gradleProperty('APP_VERSION').getOrElse('1.0.0')

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation compose.desktop.currentOs
    implementation compose.material3
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-swing:<version>'
    implementation 'com.squareup.okhttp3:okhttp:<version>'
    implementation 'com.squareup.okhttp3:okhttp-urlconnection:<version>'
    implementation 'com.google.code.gson:gson:<version>'

    // Phase 2:
    // implementation project(':shared')
}

compose.desktop {
    application {
        mainClass = 'com.kiduyuk.klausk.kiduyutv.desktop.MainKt'
        jvmArgs += ['-Xmx1G', '-Dfile.encoding=UTF-8']

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = 'KiduyuTV'
            packageVersion = project.version.toString()
            description = 'KiduyuTV for Windows'
            vendor = 'KiduyuTV'

            // Needed if SQLite JDBC or other java.sql code is used.
            modules('java.sql', 'java.net.http', 'java.naming')

            // Makes resources/windows-x64 available in the installed app.
            appResourcesRootDir.set(project.layout.projectDirectory.dir('resources'))

            windows {
                iconFile.set(project.file('src/main/resources/kiduyutv.ico'))
                menuGroup = 'KiduyuTV'
                dirChooser = true
                perUserInstall = true

                // Generate once, commit it, and NEVER change it between releases.
                upgradeUuid = 'REPLACE-WITH-A-STABLE-UUID'
            }
        }

        buildTypes.release.proguard {
            // Start without obfuscation. Enable only after testing the packaged app.
            obfuscate.set(false)
        }
    }
}
```

Compose Desktop uses `jpackage` and `jlink` to create a self-contained installer, so users do not need to install Java. Windows `.exe` and `.msi` packages must be built on Windows; cross-compilation is not supported.

### 4. Create the application entry point

`desktopApp/src/main/kotlin/com/kiduyuk/klausk/kiduyutv/desktop/Main.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    val appState = remember { DesktopAppState() }

    Window(
        title = "KiduyuTV",
        state = WindowState(width = 1280.dp, height = 760.dp),
        onCloseRequest = ::exitApplication
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFE50914),
                background = Color(0xFF080808),
                surface = Color(0xFF151515)
            )
        ) {
            KiduyuDesktopApp(appState)
        }
    }
}
```

Use one adaptive desktop shell based on the TV navigation. A permanent navigation rail works well above 1100 px; collapse it to icons or a drawer for smaller windows. Detail/gallery destinations remain on the back stack and do not need permanent rail buttons.

```kotlin
enum class DesktopDestination(val label: String) {
    HOME("Home"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    MY_LIST("My List"),
    LIVE_TV("Live TV"),
    SCHEDULE("Schedule"),
    SEARCH("Search"),
    SETTINGS("Settings")
}
```

```kotlin
@Composable
fun KiduyuDesktopApp(state: DesktopAppState) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationRail {
            DesktopDestination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = state.destination == destination,
                    onClick = { state.destination = destination },
                    icon = { DesktopDestinationIcon(destination) },
                    label = { Text(destination.label) }
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (state.destination) {
                DesktopDestination.HOME -> DesktopHomeScreen(state)
                DesktopDestination.MOVIES -> DesktopMoviesScreen(state)
                DesktopDestination.TV_SHOWS -> DesktopTvShowsScreen(state)
                DesktopDestination.MY_LIST -> DesktopMyListScreen(state)
                DesktopDestination.LIVE_TV -> DesktopLiveTvScreen(state, initialTab = 0)
                DesktopDestination.SCHEDULE -> DesktopLiveTvScreen(state, initialTab = 1)
                DesktopDestination.SEARCH -> DesktopSearchScreen(state)
                DesktopDestination.SETTINGS -> DesktopSettingsScreen(state)
            }
        }
    }
}
```

The enum above covers primary rail destinations only. Use a typed route stack for TV detail screens and players rather than encoding arguments into unvalidated strings:

```kotlin
sealed interface DesktopRoute {
    data object Shell : DesktopRoute
    data class MovieDetail(val movieId: Int) : DesktopRoute
    data class TvShowDetail(val tvId: Int) : DesktopRoute
    data class SeasonEpisodes(
        val tvId: Int,
        val tvShowName: String,
        val totalSeasons: Int
    ) : DesktopRoute
    data class MediaList(val type: String, val id: Int, val name: String) : DesktopRoute
    data class CastDetail(val castId: Int) : DesktopRoute
    data class CastImages(val castId: Int, val castName: String) : DesktopRoute
    data class MovieImages(val movieId: Int, val title: String) : DesktopRoute
    data class TvShowImages(val tvId: Int, val title: String) : DesktopRoute
    data class Videos(val mediaId: Int, val isTv: Boolean, val title: String) : DesktopRoute
    data class ImageSlider(val urls: List<String>, val initialIndex: Int) : DesktopRoute
    data class TraktProfile(val initialTab: String? = null) : DesktopRoute
    data class StreamLinks(val request: PlayRequest) : DesktopRoute
    data class Player(val request: PlayRequest) : DesktopRoute
}

class DesktopNavigator {
    private val backStack = mutableStateListOf<DesktopRoute>(DesktopRoute.Shell)
    val current: DesktopRoute get() = backStack.last()

    fun push(route: DesktopRoute) {
        backStack += route
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
```

## Phase 2: share non-Android code

Create `:shared` only after the Windows shell starts successfully. Keep it free of all `android.*` imports.

Suggested contracts:

```kotlin
interface CatalogRepository {
    suspend fun home(): HomeCatalog
    suspend fun search(query: String): List<MediaSummary>
    suspend fun details(tmdbId: Int, mediaType: MediaType): MediaDetails
}

interface StreamRepository {
    suspend fun enabledProviders(): List<String>

    suspend fun streams(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int? = null,
        episode: Int? = null,
        provider: String? = null
    ): List<StreamItem>
}

interface WatchHistoryStore {
    suspend fun find(key: MediaProgressKey): WatchProgress?
    suspend fun save(progress: WatchProgress)
}

enum class MediaType(val apiValue: String) {
    MOVIE("movie"),
    SERIES("series")
}
```

The first extraction candidates from the Android app are the concepts represented by:

- `directstream/model/StreamItem.kt`
- `directstream/playback/StreamCatalog.kt`
- the response parsing portion of `directstream/api/ProvidersApi.kt`
- stream ranking currently used by `DirectStreamActivity`
- local/Firebase resume-selection rules in `checkAndAddToWatchHistory()`

Do not move `DirectStreamActivity`, `PlayerEngine`, `HttpCookieStore`, or `SettingsManager` directly. Extract interfaces and pure functions, then leave Android implementations in `:app`.

## Provider API implementation

The existing Android contract is:

```text
GET https://sflatransport.com/kiduyuTv_providers/api/providers
GET https://sflatransport.com/kiduyuTv_providers/api/streams/movie/{tmdbId}?token={hash}
GET https://sflatransport.com/kiduyuTv_providers/api/streams/series/{tmdbId}?season={s}&episode={e}&token={hash}
GET https://sflatransport.com/kiduyuTv_providers/api/streams/{provider}/{type}/{tmdbId}?...&token={hash}
```

Use the response `name` as the primary stream-picker label, with `title` only as a fallback. Preserve `type`, MIME type, headers, and cookies.

```kotlin
data class StreamItem(
    val name: String,
    val title: String,
    val url: String,
    val quality: String = "Auto",
    val provider: String = "",
    val type: String = "",
    val mimeType: String = "",
    val headers: Map<String, String> = emptyMap()
) {
    val displayName: String
        get() = name.ifBlank { title.ifBlank { provider.ifBlank { "Stream" } } }
}

data class StreamResponse(
    val success: Boolean = false,
    val tmdbId: String? = null,
    val count: Int = 0,
    val streams: List<StreamItem> = emptyList()
)
```

An OkHttp desktop client can mirror `ProvidersApi` without Android's `Uri` or `HttpURLConnection`:

```kotlin
class DesktopProvidersApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager()))
        .build()
) {
    private val gson = Gson()

    suspend fun streams(
        type: MediaType,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        provider: String? = null
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        require(tmdbId > 0)
        if (type == MediaType.SERIES) {
            requireNotNull(season)
            requireNotNull(episode)
        }

        val path = buildString {
            append("api/streams/")
            if (!provider.isNullOrBlank()) append(provider.lowercase()).append('/')
            append(type.apiValue).append('/').append(tmdbId)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .addQueryParameter("token", tokenProvider())
            .apply {
                if (type == MediaType.SERIES) {
                    addQueryParameter("season", season.toString())
                    addQueryParameter("episode", episode.toString())
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KiduyuTV/1.0 (Windows)")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                // Never include the token or full signed stream URLs in logs.
                throw IOException("Providers API HTTP ${response.code}")
            }
            val parsed = gson.fromJson(body, StreamResponse::class.java)
            if (!parsed.success) throw IOException("Providers API returned success=false")
            parsed.streams.filter { it.url.isHttpUrl() }
        }
    }
}

private fun String.isHttpUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
}.getOrDefault(false)
```

### Token security

The current Android app injects `STREAM_API_TOKEN` through `BuildConfig`. The hash is still recoverable from an APK, and the same would be true if it were compiled into an EXE. A client-side token is an access key, not a securely held server secret.

Recommended production design:

1. User signs in or registers the Windows installation.
2. KiduyuTV exchanges that identity for a short-lived provider API token.
3. Store the refresh credential in Windows Credential Manager.
4. Refresh the provider token when it expires.
5. Rate-limit and revoke tokens on the backend.

For an internal MVP, read the existing hash from an environment variable or an ignored local settings file:

```kotlin
object DesktopSecrets {
    val streamApiToken: String by lazy {
        System.getenv("KIDUYUTV_STREAM_API_TOKEN")
            ?.takeIf(String::isNotBlank)
            ?: error("KIDUYUTV_STREAM_API_TOKEN is not configured")
    }
}
```

Never commit the token, print it, put it in an exception, or include the complete authenticated API URL in logs.

## Stream ranking

Keep Android behavior: automatic selection should choose the best stream up to 1080p. The user can manually select 1440p or 2160p from the stream dialog.

```kotlin
object StreamRanker {
    private fun resolution(quality: String): Int =
        Regex("(\\d{3,4})").find(quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun automatic(streams: List<StreamItem>): StreamItem? = streams
        .filter { it.url.isHttpUrl() }
        .filter { resolution(it.quality) in 1..1080 || resolution(it.quality) == 0 }
        .sortedWith(
            compareByDescending<StreamItem> { resolution(it.quality) }
                .thenByDescending { it.type.equals("hls", true) }
        )
        .firstOrNull()
        ?: streams.firstOrNull { it.url.isHttpUrl() }

    fun forPicker(streams: List<StreamItem>): List<StreamItem> =
        streams.sortedByDescending { resolution(it.quality) }
}
```

Do not HEAD-probe every stream before showing the player. Start the best candidate quickly, validate alternatives in parallel, and retry another candidate only after a real playback failure.

## Windows playback

Media3 is Android-only. mpv is a good fit for KiduyuTV because it supports progressive files, HLS, DASH, byte-range requests, hardware decoding, custom HTTP headers, cookies, external subtitles, and track selection.

### Recommended delivery stages

1. **MVP:** start a bundled `mpv.exe` using `ProcessBuilder`. This gives reliable playback quickly, but mpv owns the player window.
2. **Parity release:** integrate `mpv-1.dll` through libmpv/JNA and render into the KiduyuTV window. Keep Compose controls, stream dialogs, episode navigation, and history in the app.

The official libmpv documentation recommends the render API for embedding; native `wid` window embedding is simpler but has platform/toolkit edge cases.

### Bundle mpv resources

```text
desktopApp/resources/windows-x64/mpv/
├── mpv.exe
├── mpv-1.dll                 If required by the selected distribution
├── required runtime DLLs
└── licenses/
    ├── mpv-LICENSE.txt
    └── third-party-notices.txt
```

Use a reproducible, trusted Windows build and record its version/checksum. mpv is GPLv2-or-later by default and can be built LGPLv2.1-or-later with `-Dgpl=false`; the actual binary and its dependencies determine your distribution obligations. Review the chosen build's licenses before bundling it.

### Resolve the packaged executable

```kotlin
object MpvBinary {
    fun resolve(): Path {
        val packagedResources = System.getProperty("compose.application.resources.dir")
        val packaged = packagedResources
            ?.let(Paths::get)
            ?.resolve("mpv")
            ?.resolve("mpv.exe")

        if (packaged != null && Files.isRegularFile(packaged)) return packaged

        // Developer fallback; allow an explicit local mpv installation.
        val configured = System.getenv("KIDUYUTV_MPV_PATH")
            ?.takeIf(String::isNotBlank)
            ?.let(Paths::get)
        if (configured != null && Files.isRegularFile(configured)) return configured

        error("mpv.exe is missing")
    }
}
```

### Build mpv arguments safely

Pass an argument list directly to `ProcessBuilder`; never construct a shell command. Reject CR/LF in header values to prevent header injection.

```kotlin
class MpvProcessPlayer(private val executable: Path = MpvBinary.resolve()) {
    private var process: Process? = null

    fun play(stream: StreamItem, startMs: Long = 0L, subtitles: List<Path> = emptyList()) {
        require(stream.url.isHttpUrl())

        val headers = stream.headers
            .filterKeys { !it.equals("User-Agent", true) && !it.equals("Referer", true) }
            .filter { (name, value) -> safeHeader(name) && safeHeader(value) }
            .map { (name, value) -> "$name: $value" }

        val args = mutableListOf(
            executable.toString(),
            "--force-window=yes",
            "--keep-open=no",
            "--hwdec=auto-safe",
            "--cache=yes",
            "--cache-secs=30",
            "--demuxer-max-bytes=256MiB",
            "--save-position-on-quit=no",
            "--title=${stream.displayName}"
        )

        stream.headers.valueIgnoreCase("User-Agent")?.takeIf(::safeHeader)?.let {
            args += "--user-agent=$it"
        }
        stream.headers.valueIgnoreCase("Referer")?.takeIf(::safeHeader)?.let {
            args += "--referrer=$it"
        }
        if (headers.isNotEmpty()) {
            args += "--http-header-fields=${headers.joinToString(",")}"
        }
        if (startMs > 0L) args += "--start=${startMs / 1000.0}"
        subtitles.forEach { args += "--sub-file=${it.toAbsolutePath()}" }
        args += stream.url

        process?.destroy()
        process = ProcessBuilder(args)
            .redirectErrorStream(true)
            // Prevent an unread child-process output pipe from filling and
            // eventually stalling playback. Use sanitized IPC for real logs.
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    private fun safeHeader(value: String): Boolean =
        value.isNotBlank() && '\r' !in value && '\n' !in value
}

private fun Map<String, String>.valueIgnoreCase(name: String): String? =
    entries.firstOrNull { it.key.equals(name, true) }?.value
```

`Cookie` is intentionally left in `--http-header-fields`, so provider cookies are sent with manifest and segment requests. `User-Agent` and `Referer` use mpv's dedicated options. Do not add a provider's API Origin/Referer globally when the backend did not return those headers; some CDNs reject incorrect playback headers.

### Integrated libmpv player

For a polished release, create a platform player abstraction:

```kotlin
interface PlatformPlayer : AutoCloseable {
    val state: StateFlow<PlaybackState>
    val tracks: StateFlow<List<MediaTrack>>

    fun play(stream: StreamItem, startPositionMs: Long = 0L)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun selectVideoTrack(id: String?)
    fun selectAudioTrack(id: String?)
    fun selectSubtitleTrack(id: String?)
    fun addSubtitle(file: Path, label: String)
    override fun close()
}
```

Implement it with libmpv events/properties:

| KiduyuTV feature | libmpv command/property |
|---|---|
| Open stream | `loadfile` |
| Play/pause | `pause` property |
| Seek | `time-pos` property or `seek` command |
| Progress | observe `time-pos` and `duration` |
| Buffered amount | `demuxer-cache-time` / cache properties |
| Video/audio/subtitle tracks | `track-list`, `vid`, `aid`, `sid` |
| Add downloaded subtitle | `sub-add` |
| Fit/fill/zoom | `video-unscaled`, `video-zoom`, `panscan`, aspect options |
| Playback error/end | `MPV_EVENT_END_FILE` and its reason/error |

All libmpv calls and event polling should run off the Compose UI thread. Convert player callbacks into `StateFlow` and render state in Compose.

## Subtitles

Keep the existing SubDL flow, but download selected subtitles through the desktop HTTP client before loading them. This supports ZIP extraction and provider-specific subtitle headers without relying on the video player's global headers.

```kotlin
suspend fun downloadSubtitle(
    url: String,
    headers: Map<String, String>,
    client: OkHttpClient,
    destination: Path
): Path = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) ->
            if ('\r' !in name && '\n' !in name && '\r' !in value && '\n' !in value) {
                header(name, value)
            }
        }
    }.build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Subtitle HTTP ${response.code}")
        Files.createDirectories(destination.parent)
        response.body.byteStream().use { input ->
            Files.newOutputStream(destination).use { output -> input.copyTo(output) }
        }
    }
    destination
}
```

Store temporary subtitles below `%LOCALAPPDATA%\KiduyuTV\cache\subtitles` and delete stale files at startup. Never extract ZIP entries without verifying that their normalized output paths remain inside the cache directory.

## Watch history and resume

The Android native player currently:

- reads local and Firebase history before fetching/playing a stream;
- uses the newest history entry that matches the current movie or episode;
- seeks only after playback is ready;
- saves progress every 15 seconds;
- updates the active season/episode when episode navigation changes;
- saves locally and syncs remotely.

Keep the same rules on Windows.

```kotlin
data class WatchProgress(
    val tmdbId: Int,
    val mediaType: MediaType,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

fun selectResume(
    local: WatchProgress?,
    remote: WatchProgress?,
    key: MediaProgressKey
): WatchProgress? = listOfNotNull(local, remote)
    .filter { candidate ->
        candidate.tmdbId == key.tmdbId &&
            candidate.mediaType == key.mediaType &&
            (key.mediaType == MediaType.MOVIE ||
                (candidate.season == key.season && candidate.episode == key.episode))
    }
    .maxWithOrNull(compareBy<WatchProgress> { it.updatedAt }.thenBy { it.positionMs })
```

Run the progress writer as a lifecycle-aware coroutine:

```kotlin
class ProgressCoordinator(
    private val scope: CoroutineScope,
    private val local: WatchHistoryStore,
    private val remote: WatchHistoryStore,
    private val snapshot: () -> WatchProgress?
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(15.seconds)
                snapshot()?.let { progress ->
                    local.save(progress)
                    runCatching { remote.save(progress) }
                }
            }
        }
    }

    suspend fun flush() {
        snapshot()?.let { progress ->
            local.save(progress)
            runCatching { remote.save(progress) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
```

Call `flush()` when the user changes stream, changes episode, closes the player, or exits the app. Ignore resume positions below a small threshold and treat almost-complete content according to the same completion rule used by Android.

For local storage, SQLDelight is the cleanest long-term cross-platform choice. SQLite JDBC is adequate for a Windows-first MVP. Do not copy Android Room entities into the desktop module because Room annotations and drivers are Android-specific in the current project.

## Settings

Use Windows-specific settings while retaining familiar names:

```kotlin
class DesktopSettings {
    private val prefs = Preferences.userRoot().node("com/kiduyutv/desktop")

    var directStreamEnabled: Boolean
        get() = prefs.getBoolean("direct_stream_enabled", true)
        set(value) = prefs.putBoolean("direct_stream_enabled", value)

    var defaultProvider: String
        get() = prefs.get("default_provider", "")
        set(value) = prefs.put("default_provider", value)

    var mpvPath: String
        get() = prefs.get("mpv_path", "")
        set(value) = prefs.put("mpv_path", value)
}
```

Recommended Windows playback settings:

- Hardware decoding: automatic/on/off.
- Maximum automatic quality: 720p, 1080p, 1440p, or 2160p.
- Default provider or all providers.
- Subtitle language and auto-load preference.
- Audio language preference.
- mpv runtime path with a “Test player” action.
- Cache duration and maximum memory.

The Windows app should default to native direct streaming. Do not expose Android-only WebView sniffer settings until an actual Windows sniffer exists.

## Player user interface

Match `DirectStreamActivity` behavior rather than copying its Android XML:

- Backdrop/loading overlay during stream discovery and initial buffering.
- Center play/pause button and `-30` / `+30` actions.
- Current time, seek bar, buffered position, and total time.
- Fit, fill, zoom, fixed-width, and fixed-height resize modes.
- Volume, streams, tracks, subtitles, previous episode, and next episode.
- Material stream-selection and track-selection dialogs.
- Retry/exit dialog when no streams are returned.
- On playback failure, keep the player open and ask the user to try another stream.
- Automatically load the next episode when a TV episode completes.

Keyboard mapping:

| Key | Action |
|---|---|
| Space / media play-pause | Play or pause |
| Left / Right | Seek -30 / +30 seconds |
| Up / Down | Volume |
| `F` | Cycle resize mode or fullscreen |
| `S` | Open streams |
| `T` | Open tracks |
| `C` | Open subtitles |
| Page Up / Page Down | Previous / next episode |
| Escape | Close controls, then show exit confirmation |

Use `FocusRequester`, explicit focus order, visible focused states, and keyboard handlers. This preserves TV-style navigation for users controlling a Windows media PC with a remote or keyboard.

## Error and loading behavior

Represent playback as explicit state, not independent booleans:

```kotlin
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class FetchingStreams(val attempt: Int) : PlaybackState
    data class Preparing(val stream: StreamItem) : PlaybackState
    data class Playing(val stream: StreamItem) : PlaybackState
    data class Paused(val stream: StreamItem) : PlaybackState
    data class Failed(val message: String, val stream: StreamItem?) : PlaybackState
    data object NoStreams : PlaybackState
}
```

Rules:

- Do not show status, Cloudflare, retry, or buffering dialogs while playback is actually progressing.
- Hide the backdrop/status overlay as soon as the player reports real playback.
- A stalled cache is not automatically an HTTP error; wait for the player error/end event.
- Retry stream discovery up to three bounded attempts with backoff.
- When discovery returns no streams, show **Retry** and **Exit**.
- When one stream fails, show a non-blocking error and keep the stream picker available.
- Cancel old discovery, subtitle, validation, and progress jobs when media changes.

## Networking and performance

- Reuse one `OkHttpClient`; do not build a client per request.
- Share its `CookieJar` between provider discovery and subtitle downloads.
- Keep aggregate stream timeout near the Android value of 180 seconds, but display progressive provider results if the backend later supports them.
- Cache posters/backdrops on disk and request image sizes close to their rendered size.
- Load home sections lazily instead of waiting for every section before rendering.
- Use immutable UI models and stable list keys.
- Avoid serial HEAD checks for all returned streams.
- Start automatic playback at no more than 1080p; let users select larger streams.
- Enable `--hwdec=auto-safe` by default and allow software decode fallback.
- Do not log tokens, cookies, signed URLs, subtitle download URLs, or complete request headers.

## GitHub Actions and releases

The existing workflow builds phone and TV APKs on Ubuntu and creates one GitHub Release. Add a separate Windows job on `windows-latest`; do not put Windows packaging into the Android flavor matrix.

```yaml
build_windows:
  name: Build Windows EXE
  runs-on: windows-latest
  needs: [prepare_release]

  steps:
    - name: Checkout code
      uses: actions/checkout@v6

    - name: Set up Java 17
      uses: actions/setup-java@v5
      with:
        distribution: temurin
        java-version: '17'

    - name: Set up Gradle
      uses: gradle/actions/setup-gradle@v4

    - name: Set package version
      shell: pwsh
      run: |
        $version = '${{ needs.prepare_release.outputs.tag }}'.TrimStart('v')
        "APP_VERSION=$version" | Out-File -FilePath gradle.properties -Append

    - name: Build Windows installer
      shell: pwsh
      env:
        STREAM_API_TOKEN: ${{ secrets.STREAM_API_TOKEN }}
      run: .\gradlew.bat :desktopApp:packageReleaseExe --stacktrace

    - name: Upload Windows artifact
      uses: actions/upload-artifact@v4
      with:
        name: windows-release
        path: desktopApp/build/compose/binaries/main-release/exe/*.exe
```

Then change the release job:

```yaml
create_release:
  needs: [prepare_release, build_apk, build_windows]

  steps:
    # Existing phone and TV downloads remain here.

    - name: Download Windows artifact
      uses: actions/download-artifact@v4
      with:
        name: windows-release
        path: ./windows-release

    - name: Create GitHub Release
      uses: softprops/action-gh-release@v2
      with:
        files: |
          ./apks/phone-release/*.apk
          ./apks/tv-release/*.apk
          ./windows-release/*.exe
```

Update the release table to contain all three outputs:

```markdown
| Download | Platform |
|---|---|
| `KiduyuTV-phone-release-*.apk` | Android phone/tablet |
| `KiduyuTV-tv-release-*.apk` | Android TV/Fire TV |
| `KiduyuTV-setup-*.exe` | Windows 10/11 x64 |
```

If mpv is bundled, ensure its files exist before packaging and verify their checksum in CI. Do not download an unpinned “latest” binary during every release.

## Windows code signing

Unsigned installers often trigger Microsoft Defender SmartScreen warnings. For public distribution:

1. Obtain an Authenticode code-signing certificate.
2. Store the certificate and password as protected GitHub secrets.
3. Restore the certificate only inside the Windows runner.
4. Sign the installed application binaries and final installer with `signtool`.
5. Timestamp the signature.
6. Delete the restored certificate in an `always()` cleanup step.

Do not put certificate material or passwords in Gradle files. Consider keeping the first internal builds unsigned until the desktop application is stable.

## Testing plan

### Unit tests

- Movie URL contains `token` but no `season` or `episode`.
- Series URL contains `token`, `season`, and `episode`.
- Provider-specific and aggregate paths are correct.
- Cookie objects/strings are normalized into a `Cookie` header.
- Invalid and non-HTTP(S) streams are skipped independently.
- Stream picker uses `name`, with `title` as fallback.
- Automatic ranking never chooses above 1080p.
- Manual picker includes 1440p and 2160p.
- Resume selects the newest matching local/remote movie or exact episode.
- Header values containing CR/LF are rejected.

### Desktop integration tests

- Package and run the app with `runDistributable`, not only `run`.
- Walk every TV `NavGraph` destination and confirm that it has a reachable Windows equivalent.
- Compare every Home, Movies, TV Shows and My List rail against the TV version for content and ordering.
- Verify Live TV and Schedule open the same two logical tabs as the TV version.
- Navigate movie details, TV details, seasons/episodes, media lists, cast, galleries, videos and image slider, then verify one Back/Escape returns to the correct focused item.
- Exercise Settings and Trakt profile sections, including loading, authenticated, signed-out, empty and error states.
- Browse home, details, search, and history using mouse and keyboard.
- Play direct MP4/MKV, HLS, and DASH streams.
- Confirm returned User-Agent, Referer, Origin, Cookie, and other headers reach playback requests exactly as provided.
- Switch stream while buffering and while playing.
- Select video, audio, and “Default” subtitle tracks.
- Download and load SubDL subtitles.
- Resume movie and exact TV episode progress.
- Save local and remote progress every 15 seconds.
- Move to next episode automatically and verify history keys change.
- Test no-stream, HTTP 403, expired signed URL, timeout, corrupt manifest, and unsupported-codec paths.
- Confirm no status overlay remains over active playback.

### Installer tests

- Clean Windows 10 x64 VM with no Java or mpv installed.
- Clean Windows 11 x64 VM.
- Install for a non-administrator user.
- Launch from Start Menu and desktop shortcut if enabled.
- Upgrade over an older version using the same `upgradeUuid`.
- Uninstall and confirm user history/settings are either preserved or removed according to the documented policy.
- Verify the EXE and all bundled native libraries with antivirus scanning.

## Suggested implementation sequence

1. Create `:desktopApp`, open a Material window, and package a hello-world EXE.
2. Implement the TV shell and all primary destinations: Home, Movies, TV Shows, My List, Live TV, Schedule, Search and Settings.
3. Implement the complete typed back stack and all TV detail/discovery destinations: movie, show, seasons/episodes, media lists, Trakt profile, cast, galleries, videos and image slider.
4. Match every TV home/catalog section, action, loading state, empty state, focus rule and back-navigation result.
5. Port provider DTO parsing, enabled-provider loading and aggregate/provider-specific stream fetching.
6. Add external mpv playback with exact headers/cookies, then implement direct-stream and IPTV player flows.
7. Add stream selection, tracks, subtitles, episode navigation, trailer playback and error/retry states.
8. Add local watch history, resume, My List/favorites persistence and 15-second progress persistence.
9. Extract tested pure Kotlin logic into `:shared` and use it from Android and desktop.
10. Add authenticated Firebase/Trakt remote sync and desktop equivalents for TV account flows.
11. Implement provider clearance or WebView/JCEF fallback flows needed for TV feature parity.
12. Replace external mpv with embedded libmpv if an in-window player is required.
13. Add Windows CI, installer assets, license notices, signing, release upload and a three-platform download page.

Keep each phase independently releasable. The existing phone and TV build must continue to pass after every shared-code extraction.

## Definition of done

The Windows work is complete when:

- `:app` still builds both `phoneRelease` and `tvRelease` APKs.
- `:desktopApp:packageReleaseExe` builds on a clean Windows runner.
- The installer works without a separately installed JDK.
- Every primary TV screen is present: Home, Movies, TV Shows, My List, Live TV, Schedule, Search and Settings.
- Every secondary TV screen is present: movie details, TV details, seasons/episodes, stream links, company/network lists, Trakt profile, cast details, cast/movie/TV galleries, videos and image slider.
- TV startup, update, authentication, retry, empty-state, dialog and player flows have Windows equivalents.
- No screen or content rail is borrowed only from the phone implementation when a TV implementation exists.
- The Windows app can browse, search, open all details, and play movies, episodes, trailers and live channels.
- HLS, DASH, MP4/MKV, headers, cookies, audio tracks, subtitle tracks, and external subtitles work.
- Automatic playback is capped at 1080p while higher qualities remain selectable.
- Resume is checked before playback and progress is saved every 15 seconds.
- TV episodes support previous, next, and automatic-next behavior.
- Secrets and signed URLs are absent from source and logs.
- The GitHub Release contains phone APK, TV APK, and Windows EXE.
- Bundled player licensing and Windows signing requirements are documented and satisfied.
- A maintained TV-to-Windows parity checklist passes before release, including mouse, keyboard and D-pad focus testing for every screen.

## Official references

- [Compose Multiplatform native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)
- [Compose Desktop APIs and keyboard events](https://kotlinlang.org/docs/multiplatform/compose-desktop-components.html)
- [mpv manual: network, headers, cookies, hardware decoding, and commands](https://mpv.io/manual/master/)
- [mpv source, supported Windows versions, and licensing](https://github.com/mpv-player/mpv)
- [Official libmpv embedding examples](https://github.com/mpv-player/mpv-examples/tree/master/libmpv)
