# KiduyuTV Application Review

**Repository:** [kiduyu-klaus/KiduyuTv_final](https://github.com/kiduyu-klaus/KiduyuTv_final)  
**Reviewed branch:** `main`  
**Reviewed commit:** `f991b47a` — “Add scrolling and max height to mobile settings provider dialogs”  
**Review scope:** Android phone/TV application, Windows desktop module, build configuration, persistence, integrations, playback, and operational risks.

## Executive summary

KiduyuTV is a large Kotlin application with two related products in one repository. The primary product is an Android application targeting Android 7.0/API 24 and newer, with separate `phone` and `tv` product flavors. The application uses Jetpack Compose for most navigation and screens, XML/ViewBinding for playback-heavy activities, Room for local persistence, Firebase for identity and synchronization, TMDB for catalog metadata, Trakt for connected viewing, IPTV/XMLTV for live television, and Media3/ExoPlayer for native playback. A second `desktopApp` module provides a Windows Compose Desktop client with its own service, model, navigation, and player layers.

The central design choice is a dual playback system. When Direct Stream is enabled, movie and episode actions launch `DirectStreamActivity`, which calls a provider backend, ranks returned streams, and plays them using Media3. When Direct Stream is unavailable or disabled, the application can fall back to provider WebViews, ad filtering, WebView media sniffing, and provider-page playback. This is a capable but operationally complex architecture because it depends on multiple remote services, provider behavior, cookies/headers, Firebase configuration, and platform-specific playback code.

The repository is feature-rich and actively maintained, but it also contains several issues that should be addressed before treating it as production-hardened. The most important are a hardcoded release keystore password in `app/build.gradle`, an invalid checked-in `local.properties` SDK path in this sandbox, cleartext traffic being enabled, broad runtime log capture, `allowMainThreadQueries()` and destructive Room migration defaults, and a very broad dependency/integration surface.

## Repository shape

The repository contains approximately 193 Kotlin source files and 98 XML files across the Android module, plus a separate Kotlin/JVM desktop module. The Android source is organized under `app/src/main/java/com/kiduyuk/klausk/kiduyutv/` and is divided into activities, application initialization, API/data/repository layers, Compose UI, playback, networking, utilities, and ViewModels. The root also contains curated JSON media lists, screenshots, implementation guides, a landing page, and update/versioning files.

| Area | Location | Responsibility |
| --- | --- | --- |
| Android app | `app/` | Phone and Android TV/Fire TV application |
| Windows desktop | `desktopApp/` | Compose Desktop client and Windows installers |
| Curated lists | `lists/` | JSON-backed themed collections and catalog inputs |
| Documentation | `docs/` | Feature guides, optimization notes, and implementation plans |
| Screenshots | `app_screenshots/` | Product/UI reference images |
| Update/version metadata | `VERSION`, `WHATSNEW_1.2.1.txt`, workflow files | Release and update support |
| Landing page | `index.html`, `privacy.html` | Project/download and privacy web content |

The public repository page reports the `main` branch, 2 branches, 679 tags, and 1,205 commits at the time of review.[1]

## Android startup and process initialization

The process starts in `KiduyuTvApp`, declared through the Android manifest as the custom `Application` class. Its `onCreate` initializes the Room database manager, the My List manager, notification channels, cache cleanup, Firebase Analytics, Firebase Realtime Database persistence, remote ad-unit synchronization, authentication restoration, Firebase user routing, Trakt authentication state, stream-provider synchronization, activity tracking, network monitoring, and log capture.[2]

The image pipeline is centralized through a shared Coil `ImageLoader`. The loader uses memory and disk caches and creates its OkHttp client through the project API client. This is a sensible consolidation because Compose screens and legacy playback/UI components can share consistent caching behavior.[2]

`SplashActivity` then performs the user-facing startup gate. It establishes the Compose splash UI before initializing SDKs, starts Firebase synchronization, waits for ad consent before initializing the advertising SDKs, checks notification permission on supported mobile versions, validates the device type against Firebase-configured phone/TV package information, and checks Firebase-configured app updates. The splash can download and install an APK update using a `FileProvider`, while `UpdateReceiver` handles package replacement events.[3]

`MainActivity` owns the root Compose content. It checks storage/media permissions, performs an initial connectivity check, installs a back-press callback, handles notification deep links, observes navigation state, and selects either the TV or mobile graph based on `UiModeManager.currentModeType`. TV devices use `NavGraph`; non-TV devices use `MobileNavGraph`.[4]

## Navigation and primary user flows

The TV graph starts at `Home`. It exposes dedicated destinations for Home, Movies, TV Shows, My List, Live TV, Schedule, Search, movie details, TV-show details, stream links, seasons/episodes, media lists by company/network, settings, Trakt profile, cast details, cast images, media images, videos, and image sliders.[5]

The navigation model passes compact identifiers and metadata through typed route arguments. Movie and TV detail screens receive TMDB IDs, while playback routes receive the content type, title, artwork, overview, rating, release date, and optional season/episode numbers. Companies, networks, cast members, image URLs, and titles are URL-encoded into routes when needed. This keeps the graph self-contained but also makes route construction relatively fragile: long titles, comma-separated image lists, and incomplete metadata require careful encoding and decoding.

| User action | Main implementation path |
| --- | --- |
| Browse home/catalog | Compose home, movie, TV, and list screens backed by ViewModels and repositories |
| Open details | `MovieDetailScreen` or `TvShowDetailScreen` with TMDB ID route arguments |
| Play movie/episode | Stream-links route; Direct Stream may launch immediately depending on settings |
| Browse providers | `StreamLinksScreen`, with manual provider/source selection when native direct playback is not used |
| Watch live TV | `LiveTvScreen` loads channels and starts `IptvPlayerActivity` |
| Browse schedule | Same Live TV screen with the schedule tab selected |
| Search | `SearchScreen` and `SearchViewModel` query TMDB-backed search functionality |
| Save content | Room/My List manager, with Firebase synchronization when available |
| Connected viewing | Trakt profile, history, collection, watchlist, recommendations, and scrobbling |
| Configure behavior | Settings screen controls playback, provider, sync, Trakt, ads, and IPTV-related options |

The mobile graph mirrors the product concept but uses phone-oriented screens and bottom navigation. The TV graph additionally overlays a TV banner ad view on every destination when the build flavor is `tv`.[5]

## Data and persistence architecture

The local data layer is Room-based. The database registers entities for saved media, watch history, cached movies, cached TV shows, cached movie details, cached TV details, and genres. `DatabaseManager` acts as a façade over Room and supports My List operations, watch-history CRUD, playback-position updates, continue-watching queries, catalog/detail/genre caching, legacy SharedPreferences migration, cache cleanup, statistics, and reset operations.[6]

The application uses SharedPreferences for lightweight state such as playback and provider settings, device identity, toggles, and some authentication/configuration values. This produces a hybrid persistence model: large structured data resides in Room, while operational preferences remain in key/value storage.

Firebase synchronization is orchestrated by `FirebaseSyncManager`. On startup it performs a seven-stage process covering My List, companies, networks, casts, watch history, favorite channels, and the default provider. It chooses a Firebase Auth UID when the user is authenticated and otherwise falls back to a generated device ID. When authentication changes, it reinitializes the Firebase manager and restarts synchronization under the new identity.[7]

The sync strategy is merge-oriented rather than a strict transactional replication protocol. Cloud data is imported into local storage, local items are uploaded when needed, and the implementation contains special handling for favorite channels and watch-history merging. This is appropriate for a consumer media app, but conflict semantics should be documented and tested carefully, particularly when the same user opens the phone and TV apps concurrently.

## Catalog, integrations, and remote configuration

TMDB is the primary metadata source for movies and television. The repository layer supports discovery lists, search, details, seasons/episodes, cast and crew, collections, production companies, networks, images, recommendations, trailers/videos, episode runtime, and skip-segment-related lookups. Trakt adds OAuth sign-in, profile data, history, collection, watchlist, recommendations, token refresh, synchronization, and scrobbling.

Firebase is used for more than user data. It also carries remote configuration for stream providers, app package/version information, ad-unit configuration, notifications, update metadata, and other application settings. The stream-provider manager has a substantial local fallback catalog and can refresh provider configuration from a Firebase path described in the README as `app_config/stream_providers_Configuration`.[1]

Live TV is a separate subsystem. It loads M3U playlists, parses IPTV channels, consumes XMLTV guide data, supports channel categories and search, stores favorite channels, and presents Live TV, Schedule, and My Channels views. Channel playback uses dedicated activities rather than the movie/episode direct-stream player.

## Playback architecture

The preferred movie/episode path is native playback. The stream-links route checks `SettingsManager.isDirectStreamEnabled()`. If enabled, it launches `DirectStreamActivity` through an activity-result launcher. If the native activity returns a special result indicating that stream links should be shown, the route renders the manual stream-selection screen instead.[5]

`DirectStreamActivity` coordinates `StreamResolver`, `PlayerEngine`, `StreamCatalog`, stream and track dialogs, subtitle clients, Cloudflare bypass, TMDB runtime data, watch history, and episode navigation. The documented flow is:

> Fetch all available streams, select the highest-ranked usable stream, and hand it to the native Media3 player.[8]

`StreamResolver` queries the provider backend, discovers enabled providers when no single provider is selected, limits concurrent provider requests, retries some empty/HTTP 500 responses, reports progress, flattens and de-duplicates streams, and returns the combined catalog.[8]

The player supports HLS, DASH, and progressive media, provider headers/cookies, stream switching, video/audio/subtitle track selection, subtitle search/download through SubDL, WebVTT/SRT handoff, buffering/progress UI, resize modes, TV D-pad seeking, previous/next episode navigation, and recovery flows for playback failures. The activity periodically persists watch progress; its code defines a recurring progress tick and uses local/Firebase history to restore positions.

When direct playback is disabled or unsuitable, `PlayerActivity` remains the provider WebView player. `WebViewStreamSniffer` can inspect WebView requests for HLS, DASH, direct media, and subtitle URLs, carrying request headers and cookies into the native player. `AdBlockerWebViewClient` and the advanced ad-blocking utilities are used to reduce provider-page popups and advertising. This fallback preserves compatibility with providers that cannot be represented as clean direct media URLs, but it increases exposure to provider HTML/JavaScript changes and WebView security concerns.

## Build and platform variants

The Android module uses the `formfactor` flavor dimension. The `phone` flavor produces `com.kiduyuk.klausk.kiduyutv.phone`; the `tv` flavor produces `com.kiduyuk.klausk.kiduyutv.tv`. The shared default application ID is `com.kiduyuk.klausk.kiduyutv`. The module targets compile/target SDK 35, supports minimum SDK 24, uses Java/Kotlin 17, and enables Compose, ViewBinding, BuildConfig fields, R8 shrinking, and resource shrinking for release builds.[9]

| Build target | Expected command | Role |
| --- | --- | --- |
| Phone debug | `./gradlew assemblePhoneDebug` | Touch-first Android build |
| TV debug | `./gradlew assembleTvDebug` | D-pad/lean-back Android TV build |
| Phone release | `./gradlew assemblePhoneRelease` | Signed phone release |
| TV release | `./gradlew assembleTvRelease` | Signed TV release |
| Windows EXE | `bash ./gradlew :desktopApp:packageReleaseExe` | Windows installer |
| Windows MSI | `bash ./gradlew :desktopApp:packageReleaseMsi` | Managed/enterprise-style installer |

The repository also contains a Windows Compose Desktop application. `DesktopServices` provides TMDB, provider, IPTV, local settings, Trakt-related, and update services. `DesktopRoute` models navigation. Primary screens cover home, catalogs, My List, search, Live TV, schedule, and settings; detail screens cover media, seasons, cast, galleries, videos, and Trakt; player screens cover provider selection, direct playback, live playback, and WebView playback.[10]

## Dependency profile

The Android dependency set is broad. It includes Compose/Material 3, Navigation Compose, Lifecycle/ViewModel, Retrofit, OkHttp, Volley, Gson, Coil, Glide, Room/KSP, coroutines, Firebase Analytics/Database/Firestore/Auth/Messaging, Google Sign-In, Lottie, Media3 ExoPlayer with HLS/DASH/UI/data-source modules, YouTube playback, UMP consent, AdMob, StartApp, Unity Ads, Wortise, and mediation adapters.[9]

This stack gives the app significant capability, but it also raises maintenance and supply-chain complexity. Several libraries overlap in responsibility, notably Coil and Glide, Retrofit/OkHttp and Volley, and multiple advertising SDKs. A future cleanup could reduce binary size and the number of initialization paths by standardizing image loading, HTTP, and advertising abstractions.

## Findings and risks

| Severity | Finding | Evidence and impact |
| --- | --- | --- |
| High | Release signing credentials are hardcoded | `app/build.gradle` contains a release keystore path and literal `storePassword`/`keyPassword` values.[9] These should be rotated and moved to protected CI secrets immediately. |
| High | A production Firebase configuration file is committed | `app/google-services.json` is present in the repository. Firebase client identifiers are not equivalent to server secrets, but exposure should be intentional and Firebase security rules must be reviewed. |
| High | Cleartext traffic is enabled | The manifest sets `android:usesCleartextTraffic="true"`. This broadens the network attack surface and should be constrained to explicitly required domains or removed. |
| Medium | Runtime logcat capture is enabled process-wide | `KiduyuTvApp` starts `LogcatManager`; this can retain sensitive URLs, provider headers, cookies, user identifiers, or playback metadata if logging is verbose.[2] Release builds should disable or strictly redact it. |
| Medium | Room is configured with risky defaults | `AppDatabase` uses `allowMainThreadQueries()` and `fallbackToDestructiveMigration()`. The first can block UI threads; the second can erase local data after schema changes. |
| Medium | Build reproducibility is environment-dependent | The sandbox build failed because checked-in `local.properties` points to an unavailable SDK directory. The wrapper itself also lacks executable permission in the checkout, requiring `bash ./gradlew`. CI should provide `ANDROID_HOME`/SDK setup and executable-bit validation. |
| Medium | Provider playback is externally fragile | Native playback and WebView fallback depend on remote provider APIs, provider templates, cookies, Cloudflare behavior, and Firebase configuration. A provider outage can affect a core product path. |
| Medium | Authentication and identity are dual-mode | The app combines Firebase Auth UIDs, phone authorization, and generated device IDs. A mismatch or stale auth transition could write data to the wrong Firebase namespace if not covered by integration tests. |
| Low | Dependency overlap increases maintenance cost | Coil and Glide, multiple HTTP clients, multiple ad networks, and multiple Firebase modules increase APK size, initialization complexity, and upgrade risk. |
| Low | Route arguments carry rich strings | Titles, names, and comma-separated image URLs are encoded into routes. A typed state-holder or saved-state model would be less error-prone for large payloads. |

The build verification attempted `:app:assemblePhoneDebug`. Gradle 8.13 resolved successfully, but compilation could not begin because the repository’s `local.properties` referenced a nonexistent Android SDK directory in the sandbox. Therefore, this review confirms source/configuration behavior but does not claim that the current checkout produces a successful APK in the review environment.

## Recommended next steps

First, rotate the exposed release keystore credentials and remove literal signing secrets from the Gradle file. Configure signing through environment variables or a private `keystore.properties` file supplied only in CI. At the same time, audit Firebase Realtime Database and Firestore rules, because the application stores user lists, watch history, favorite channels, settings, and provider configuration in cloud services.

Second, harden networking. Remove global cleartext traffic if possible, constrain any required HTTP domains with a network security configuration, redact request headers and cookies from logs, and make provider and subtitle endpoints explicit allowlisted configuration. The WebView should use a narrowly scoped JavaScript/cookie policy, and captured headers/cookies should not be persisted beyond the minimum required playback session.

Third, improve data safety. Remove `allowMainThreadQueries()`, ensure all Room access remains on IO dispatchers, replace destructive migration fallback with tested migrations, and add conflict-resolution tests for simultaneous phone/TV updates. A repository-level test suite should cover auth transitions from device ID to Firebase UID and back.

Fourth, reduce release complexity. Consolidate image and HTTP clients where practical, isolate advertising SDK initialization behind a single interface, and use feature-level remote configuration rather than allowing a single Firebase failure to affect startup. Add automated smoke tests for splash gating, notification deep links, direct-stream failure, WebView fallback, IPTV playback, and update installation.

## References

[1]: https://github.com/kiduyu-klaus/KiduyuTv_final "KiduyuTv_final repository and README"
[2]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/application/KiduyuTvApp.kt "KiduyuTvApp.kt"
[3]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/activity/splashactivity/SplashActivity.kt "SplashActivity.kt"
[4]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/activity/mainactivity/MainActivity.kt "MainActivity.kt"
[5]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/navigation/NavGraph.kt "TV navigation graph"
[6]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/local/database/DatabaseManager.kt "DatabaseManager.kt"
[7]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/util/FirebaseSyncManager.kt "FirebaseSyncManager.kt"
[8]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/player/directstream/playback/StreamResolver.kt "StreamResolver.kt"
[9]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/build.gradle "Android module build configuration"
[10]: https://github.com/kiduyu-klaus/KiduyuTv_final/tree/main/desktopApp "Windows desktop module"
