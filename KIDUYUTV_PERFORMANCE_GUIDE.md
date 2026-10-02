# KiduyuTV performance and speed improvement guide

Reviewed: 2026-09-30  
Scope: the Android application under `app/`. This is an implementation guide,
not a claim that every recommendation is already a measured regression. Profile
the target user journey before and after each change.

## Executive summary

KiduyuTV already has useful foundations: Room-backed media caches, Coil memory
and disk caches, Compose lazy containers, release shrinking, a shared Media3
player wrapper, and provider-request concurrency limits. The largest likely
gains are from reducing work performed at cold start, preventing database work
on the main thread, consolidating network clients/caches, using stable Compose
keys, and limiting stream-provider fan-out to the media category.

Recommended implementation order:

1. Fix the direct-stream provider selection path so it uses
   `enabledProviderNamesForMedia(type, tmdbId)` rather than every enabled
   provider.
2. Remove `allowMainThreadQueries()`, make every DAO call suspend/Flow based,
   and add Room query indexes verified by query plans.
3. Defer non-essential `Application.onCreate()` work until after the first
   frame or until the related feature is opened.
4. Reuse one application-scoped OkHttp client/cache and use a bounded,
   cancellation-aware retry policy.
5. Add stable keys/content types to frequently updated Compose lists and avoid
   expensive transforms in composable bodies.
6. Measure cold start, scrolling jank, stream-search time, memory, and
   buffering before setting targets.

## Current code observations

| Area | Evidence in the project | Cost/risk | Improvement |
| --- | --- | --- | --- |
| Startup | `KiduyuTvApp.onCreate()` initializes Room, My List, Firebase Realtime persistence, Firebase config sync, auth, Trakt, provider sync, connectivity monitoring, app-open ads and log capture. | Adds disk I/O, binder work and network listeners before first content. | Keep only prerequisites synchronous; defer feature work. |
| Database | `AppDatabase.buildDatabase()` calls `allowMainThreadQueries()`. | A cache/history query can block rendering or D-pad input. | Remove it and expose suspend/Flow DAO APIs. |
| Networking | `ApiClient` builds a cached client for Coil but a second lazy Retrofit client without that cache; stream checks and repositories also own clients/caches. | Extra sockets, cache fragmentation, duplicate DNS work and inconsistent timeouts. | Inject named, shared clients by traffic type. |
| Streams | `StreamResolver.load()` limits concurrent provider requests to 7, but currently calls `ProvidersApi.enabledProviderNames()` rather than the category-aware provider selection API. | A movie/TV request may launch every enabled provider, including irrelevant providers, increasing latency, radio use and backend load. | Select one enabled category before creating coroutines. |
| Compose lists | Several `items(...)` and `itemsIndexed(...)` calls use no key, including reusable content/cast/crew rows and mobile home rows. | Identity changes can cause needless recomposition, image reloads, and focus loss. | Supply stable keys and content types. |
| Images | Coil is configured app-wide, but Compose and legacy View code use both Coil and Glide. Coil logging is enabled unconditionally. | Two independent caches/decoders increase memory and disk use; debug logging has runtime cost. | Pick Coil for Compose and isolate Glide to unavoidable legacy paths; enable verbose logging only in debug. |
| Code ownership | The largest Kotlin files include `SettingsScreen.kt` (~4,523 lines), `DirectStreamActivity.kt` (~3,401), and `LiveTvScreen.kt` (~2,054). | Large activity/composable files make lifecycle and recomposition regressions harder to identify and test. | Split by state, renderer, and side-effect owner before tuning locally. |

## 1. Make direct-stream resolution selective and cancellation-friendly

### Current opportunity

`StreamResolver` already caps requests at seven simultaneous providers and
retries individual provider fetches. However, its current code uses all enabled
providers:

```kotlin
val enabledProviderNames = ProvidersApi.enabledProviderNames()
```

`ProvidersApi.enabledProviderNamesForMedia(type, tmdbId)` already understands
movie, TV, and animated-media routing. Calling it avoids requests to providers
that cannot serve the selected media. This is both the biggest direct
performance improvement and a correctness improvement.

```kotlin
// StreamResolver.kt
val compatibleProviders = ProvidersApi.enabledProviderNamesForMedia(
    type = type,
    tmdbId = tmdbId
)

val selectedProvider = provider.key.takeIf { requested ->
    compatibleProviders.any { it.equals(requested, ignoreCase = true) }
}
val providerNames = selectedProvider?.let { requested ->
    listOf(compatibleProviders.first { it.equals(requested, ignoreCase = true) })
} ?: compatibleProviders
```

Keep the existing semaphore, but make a user leaving the screen cancel the
parent coroutine. Do not place the work in a detached scope.

```kotlin
private var streamLoadJob: Job? = null

fun loadStreams(...) {
    streamLoadJob?.cancel()
    streamLoadJob = lifecycleScope.launch {
        val streams = resolver.load(...)
        if (isActive) renderStreams(streams)
    }
}

override fun onStop() {
    streamLoadJob?.cancel()
    super.onStop()
}
```

### Avoid duplicate validation traffic

Only validate a source when the user selects it or when it is within the first
few visible candidates. Validating every returned stream makes source lookup
appear slow even when providers returned quickly. Cache a short-lived result by
normalized URL plus the headers that affect availability.

```kotlin
data class ValidationKey(
    val url: String,
    val referer: String?,
    val userAgent: String?
)

private val validationCache = mutableMapOf<ValidationKey, TimedResult>()

suspend fun validateOnce(stream: StreamItem): ValidationResult {
    val key = ValidationKey(
        url = stream.url,
        referer = stream.headers["Referer"],
        userAgent = stream.headers["User-Agent"]
    )
    validationCache[key]?.takeIf { it.isFresh(10.minutes) }?.let { return it.value }
    return validate(stream).also { validationCache[key] = TimedResult.now(it) }
}
```

## 2. Reduce cold-start work

### Keep `Application.onCreate()` small

`KiduyuTvApp.onCreate()` currently launches many cross-cutting services. Keep
the database reference, crash/analytics prerequisites, and essential auth state
there. Delay cache cleanup, remote config, provider sync, Trakt initialization,
log capture and ad loading until after the first frame or the feature is first
needed.

```kotlin
class KiduyuTvApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        DatabaseManager.init(this)
        AuthManager.init(this, webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // Yield so the first activity can draw before non-critical startup work.
            yield()
            launch(Dispatchers.IO) { DatabaseManager.cleanExpiredCache() }
            launch { AdUnitIds.startFirebaseSync() }
            launch { StreamProviderManager.startFirebaseSync() }
        }
    }
}
```

Use AndroidX App Startup only for true, dependency-ordered startup components.
It makes initialization visible and testable, but should not be used to make
every optional service eager.

### Log capture

`LogcatManager.clearAllLogs(this)` and `LogcatManager.start(this)` run at
every launch. Consider enabling continuous capture only from a user-facing
diagnostics setting, or use a fixed-size circular file. Avoid clearing a large
directory on the UI-critical process startup path.

## 3. Make Room strictly off-main-thread

`AppDatabase` currently has:

```kotlin
.allowMainThreadQueries()
.fallbackToDestructiveMigration()
```

The first can directly cause dropped frames. Remove it after migrating callers
to `suspend` and `Flow`. The second may trade correctness for a quick
startup; replace it with versioned migrations before schema changes ship.

```kotlin
@Dao
interface CachedMovieDao {
    @Query("SELECT * FROM cached_movies WHERE cacheType = :type AND expirationTimestamp > :now")
    fun observeValidByType(type: String, now: Long = System.currentTimeMillis()): Flow<List<CachedMovieEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedMovieEntity>)
}

class MovieRepository(private val dao: CachedMovieDao) {
    fun movies(type: String): Flow<List<Movie>> =
        dao.observeValidByType(type).map { entities -> entities.map(::toMovie) }
}
```

Add indexes for every frequently filtered or ordered column, then verify using
SQLite's query plan:

```kotlin
@Entity(
    tableName = "cached_movies",
    indices = [
        Index(value = ["cacheType", "expirationTimestamp"]),
        Index(value = ["fetchedTimestamp"])
    ]
)
data class CachedMovieEntity(...)
```

Run expensive legacy-preference migration once and record completion even when
the legacy blob is empty, so it is not repeatedly parsed.

## 4. Consolidate HTTP clients, cache policy, and retries

### One named client per traffic class

At present `ApiClient`, `StreamLinksViewModel`, repositories, Coil and
Trakt can create separate clients and caches. Use application-scoped clients
with explicit roles instead:

```kotlin
class HttpClients(context: Context) {
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 6
    }

    val metadata: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "metadata_http"), 25L * 1024 * 1024))
        .dispatcher(sharedDispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Providers have volatile URLs and unusual headers: do not pollute the
    // metadata cache with them.
    val providers: OkHttpClient = metadata.newBuilder()
        .cache(null)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
```

Do not add TMDB's Authorization header or JSON Content-Type header to arbitrary
image/CDN requests. Scope those headers to the TMDB Retrofit client.

### Retry without blocking an OkHttp thread

`ApiClient.retryInterceptor` uses `Thread.sleep(3_000)`. Even though it is
currently disabled for the cached client, it remains enabled on the lazy
Retrofit client. Retrying in an interceptor blocks an OkHttp dispatcher thread,
and retries for non-idempotent requests can be unsafe.

Move retry policy to the repository layer, use exponential backoff with jitter,
and preserve coroutine cancellation:

```kotlin
suspend fun <T> retryTransient(
    attempts: Int = 3,
    block: suspend () -> T
): T {
    var last: Throwable? = null
    repeat(attempts) { index ->
        try {
            return block()
        } catch (error: IOException) {
            last = error
            if (index == attempts - 1) throw error
            val base = 300L shl index
            delay(base + Random.nextLong(0, 150))
        }
    }
    throw checkNotNull(last)
}
```

Apply this only to safe GET/HEAD requests and retryable status codes. Respect
`Retry-After` on HTTP 429.

### Cache stale metadata intentionally

Avoid forcing a five-minute cache age on every request. Use a network-aware
interceptor: network available means `maxAge`; offline means
`onlyIfCached + maxStale`. Return a typed stale-data state to UI rather than
silently turning a cache miss into an opaque error.

```kotlin
val cacheControl = if (connectivity.isOnline) {
    CacheControl.Builder().maxAge(5, TimeUnit.MINUTES).build()
} else {
    CacheControl.Builder()
        .onlyIfCached()
        .maxStale(7, TimeUnit.DAYS)
        .build()
}
chain.proceed(chain.request().newBuilder().cacheControl(cacheControl).build())
```

## 5. Improve Compose rendering and focus stability

### Stable keys and content types

Rows such as `ContentRow`, `CastRow`, `CrewRow`, home feeds, search and
stream lists often use `itemsIndexed(items)` without keys. Index identity is
not stable when a list is filtered, prepended, refreshed, or sorted.

```kotlin
LazyRow(state = rowState) {
    items(
        items = movies,
        key = { movie -> movie.id },
        contentType = { "movie" }
    ) { movie ->
        MediaCard(movie = movie)
    }
}
```

For channels that can share IDs across sources, use a stable compound key:

```kotlin
key = { channel -> "${channel.sourceId}:${channel.id}" }
```

### Keep transformations out of composition

Do not call expensive `filter`, `map`, `distinctBy`, grouping, JSON
parsing, or image URL building each time a parent recomposes. Use
`remember(input)` for small, UI-only derivations; place data transformations in
the ViewModel for shared or large results.

```kotlin
val visibleChannels by remember(channels, selectedCategory, query) {
    derivedStateOf {
        channels.asSequence()
            .filter { it.category == selectedCategory }
            .filter { it.name.contains(query, ignoreCase = true) }
            .toList()
    }
}
```

For text input, debounce search work in the ViewModel:

```kotlin
val results: StateFlow<SearchUiState> = query
    .debounce(300)
    .distinctUntilChanged()
    .flatMapLatest(repository::search)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)
```

### Limit animation and allocation pressure

Skeleton screens with many simultaneous shimmer rows can be expensive on low-end
TV hardware. Render only near-viewport placeholders, reuse a single animation
specification, and stop infinite animation when the app is backgrounded.

Keep `MutableInteractionSource`, `FocusRequester` and callbacks remembered
per keyed item, not recreated through a parent list refresh.

## 6. Image memory, decoding and scrolling

KiduyuTV uses Coil for Compose and Glide for legacy View-based episode UI. Each
library owns its own memory and disk caches.

1. Prefer Coil for new Compose surfaces.
2. Retain Glide only where XML/RecyclerView integration cannot yet be migrated.
3. Set image request size to the displayed card size; decoding a full backdrop
   for a 160dp poster is wasted CPU and memory.
4. Use low-resolution poster/backdrop TMDB paths in rows, and only request
   original/full-size images in dedicated detail or image viewer screens.
5. Disable Coil `DebugLogger()` in release builds.

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(posterUrl)
        .size(POSTER_WIDTH_PX, POSTER_HEIGHT_PX)
        .memoryCacheKey("poster:${movie.id}:w342")
        .diskCacheKey("poster:${movie.id}:w342")
        .crossfade(false) // rows scroll more smoothly without many crossfades
        .build(),
    contentDescription = movie.title
)
```

The current Coil disk cache is 30 MB. Measure cache hit rate and eviction
before changing it; on a TV with adequate storage, 75–150 MB commonly improves
home-screen revisit speed, while phones may need the current smaller limit.

## 7. Player and playback performance

`PlayerEngine` already centralizes Media3 configuration, which is the right
place for tuning. Prefer adaptive playback and let Media3 pick supported tracks
unless the user explicitly locks quality.

```kotlin
val trackSelector = DefaultTrackSelector(context).apply {
    setParameters(
        buildUponParameters()
            .setForceHighestSupportedBitrate(false)
            .setMaxVideoSizeSd() // only for a user-selected data-saver mode
    )
}
```

Recommended checks:

- Use the same `DefaultHttpDataSource.Factory` and bounded connection pool for
  manifests, segments, and external subtitle requests.
- Keep subtitle downloading on I/O and avoid parsing a large subtitle file on
  the main thread.
- Cache only VOD segments with an explicit user-controlled storage budget.
  Never blindly cache live streams.
- Track time-to-first-frame, rebuffer count, rebuffer duration, dropped frames,
  selected bitrate and manifest failures using `AnalyticsListener`.
- Release the player and remove listeners from the matching lifecycle callback;
  recreate only when the activity/player session is intentionally new.

```kotlin
player.addAnalyticsListener(object : AnalyticsListener {
    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        metrics.record("player_first_frame_ms", renderTimeMs)
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        metrics.record("player_dropped_frames", droppedFrames)
    }
})
```

## 8. Live TV and large-data parsing

IPTV playlists and EPG XML can be large enough to cause allocation spikes.
Avoid holding raw content, parsed objects, mapped UI objects and filtered lists
at the same time.

- Parse playlist/EPG data on `Dispatchers.Default` or a dedicated limited
  parser dispatcher.
- Stream XML where practical instead of building an entire DOM.
- Store a compact normalized representation in Room/file cache.
- Use `Sequence`/single-pass transforms when deriving categories.
- Keep only current category/visible schedule windows in UI state.
- Precompute normalized search fields once per channel rather than lowercasing
  every name on each keystroke.

```kotlin
private val parserDispatcher = Dispatchers.Default.limitedParallelism(2)

suspend fun parsePlaylist(raw: String): Playlist = withContext(parserDispatcher) {
    parser.parse(raw) // no UI-state mutation here
}
```

## 9. Build and APK/runtime footprint

Release shrinking and resource shrinking are already on. Next steps:

- Use the APK Analyzer to identify native libraries and duplicated resources.
- Review the need for both Coil and Glide, Volley and OkHttp/Retrofit, and
  dependencies only used by one flavor.
- Move TV-only and phone-only heavy dependencies into flavor-specific
  configurations where possible.
- Confirm that diagnostic-only dependencies, logging, and inspector resources
  do not ship in release.
- Maintain Baseline Profiles for main navigation, details, direct-stream launch,
  IPTV launch and Compose lazy-list scroll.

```kotlin
// benchmark/src/main/java/.../BaselineProfileGenerator.kt
@Test
fun generateProfile() = baselineProfileRule.collect("com.kiduyuk.klausk.kiduyutv.phone") {
    pressHome()
    startActivityAndWait()
    device.waitForIdle()
    // Navigate through the main user journeys here.
}
```

## 10. Measurement plan and acceptance targets

Do not merge performance work based on intuition alone. Capture a baseline on a
representative lower-end phone and TV device, then compare the same build type,
network and content.

| Journey | Instrumentation | Starting target |
| --- | --- | --- |
| Cold start to interactive home | Macrobenchmark startup timing, Perfetto | Improve p50/p95 after deferred initialization; no visual regression. |
| Home scroll / D-pad navigation | JankStats, Macrobenchmark frame timing | Keep slow frames below 5% during sustained scroll. |
| Search typing | Trace query-to-results time, recomposition counts | Cancel stale requests; results should reflect the final query only. |
| Details screen | Network trace + Compose layout inspector | Cached content first, then refresh without blocking navigation. |
| Direct stream load | Provider timing per compatible provider, time to first source | Query only category-compatible enabled providers. |
| Playback | Media3 AnalyticsListener | Reduce time-to-first-frame/rebuffer rate for the same test stream. |
| Memory | Android Studio Memory Profiler, LeakCanary in debug | No retained Activity/player/Ad objects after navigation. |

Add a lightweight structured metric for provider calls. Do not log signed URLs,
cookies, authorization headers, or subtitle tokens.

```kotlin
data class ProviderMetric(
    val provider: String,
    val mediaType: String,
    val durationMs: Long,
    val streamCount: Int,
    val result: String // success, empty, timeout, http_error
)
```

## Delivery checklist

- [ ] Capture baseline Macrobenchmark/Perfetto traces.
- [ ] Route direct-stream discovery through compatible provider preferences.
- [ ] Remove Room main-thread queries and add migrations/indexes.
- [ ] Defer optional app startup work.
- [ ] Consolidate HTTP clients and cache policy.
- [ ] Add stable keys/content types to the highest-traffic Compose lists.
- [ ] Restrict image decode sizes and release-only logging.
- [ ] Add Media3 startup/rebuffer metrics.
- [ ] Re-measure on phone and TV hardware before rollout.

