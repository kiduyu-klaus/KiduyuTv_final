# KiduyuTV

<div align="center">

![KiduyuTV Banner](https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/main/app/src/main/res/mipmap-xhdpi/ic_banner.png)

**KiduyuTV is a dual-form-factor Android streaming app for Android TV, Fire TV, and mobile devices. The codebase uses one Android app module with `phone` and `tv` product flavors, separate Compose navigation graphs, shared data/repository layers, local Room caching, Firebase sync, Trakt integration, IPTV playback, schedule playback, and a multi-network ads stack.**

[![Android Release CI](https://github.com/kiduyu-klaus/KiduyuTv_final/actions/workflows/kiduyu_final.yml/badge.svg)](https://github.com/kiduyu-klaus/KiduyuTv_final/actions/workflows/kiduyu_final.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Fire%20TV%20%7C%20Mobile-FF6B35?style=for-the-badge)](https://developer.android.com/tv)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?style=for-the-badge)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-61DAFB?style=for-the-badge)](https://developer.android.com/compose)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-red?style=for-the-badge)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![Build Status](https://img.shields.io/github/actions/workflow/status/kiduyu-klaus/KiduyuTv_final/kiduyu_final.yml?branch=main&style=for-the-badge&label=Build)](https://github.com/kiduyu-klaus/KiduyuTv_final/actions)
[![Media3 ExoPlayer](https://img.shields.io/badge/Media3%20ExoPlayer-1.5.1-orange?style=for-the-badge)](https://developer.android.com/media/media3/exoplayer)
[![TMDB API](https://img.shields.io/badge/TMDB%20API-01B4E4?style=for-the-badge&logo=themoviedatabase&logoColor=white)](https://www.themoviedb.org)

</div>

## Contents
- [Main Features](#main-features)
- [Implementation Overview](#implementation-overview)
- [Screens And Navigation](#screens-and-navigation)
- [Playback](#playback)
- [Live TV And Schedule](#live-tv-and-schedule)
- [Data, Sync, And Storage](#data-sync-and-storage)
- [Ads And Consent](#ads-and-consent)
- [Network And Protection](#network-and-protection)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Build Variants](#build-variants)
- [Configuration](#configuration)
- [Development Notes](#development-notes)
- [License](#license)

## Main Features

- Android TV, Fire TV, phone, and tablet support from one codebase.
- Dedicated TV UI with D-pad focus handling and dedicated mobile UI with touch-first screens.
- Home, Movies, TV Shows, My List, Live TV, Search, Settings, and Trakt profile areas.
- TMDB-powered movie and TV metadata, details, cast, crew, genres, companies, networks, recommendations, videos, seasons, and episodes.
- Curated GitHub-hosted content rows for themed movie and TV collections.
- My List with local Room persistence and Firebase sync.
- Watch history and continue-watching support with playback position tracking.
- Trakt OAuth login, profile data, watch history, collection, watchlist, recommendations, and scrobbling helpers.
- Live TV playlist browsing from M3U, EPG loading from XMLTV, category browsing, search, and favorite channels.
- Schedule tab with expandable events, available channel selection, and schedule-player launch.
- WebView streaming provider system with Firebase-configurable provider templates and local fallback providers.
- Dedicated players for WebView streams, IPTV streams, scheduled channels, and YouTube videos.
- WebView ad blocking, popup suppression, EasyList/EasyPrivacy assets, and schedule-player autoplay injection.
- Multi-network ads with UMP consent and priority: `StartApp -> AdMob -> Wortise -> Unity`.
- App-open ads through Wortise where available.
- Network reachability checks, network state dialogs, VPN/proxy/DNS diagnostics, and ad-blocking DNS detection.
- Firebase Auth, Analytics, Realtime Database, Firestore, and Cloud Messaging dependencies.
- In-app update helpers for checking, downloading, and installing APK updates.
- Notification channel support with deep links into movie and TV detail screens.

## Implementation Overview

The app starts in `SplashActivity`, resolves consent, initializes Firebase sync, initializes ad SDKs, checks updates/notifications, and then launches `MainActivity`. `MainActivity` chooses the TV or mobile navigation graph based on `UiModeManager`.

Core app initialization happens in `KiduyuTvApp`:

- Initializes Room through `DatabaseManager`.
- Initializes `MyListManager`.
- Creates notification channels.
- Cleans expired Room cache.
- Initializes Firebase Analytics and Realtime Database persistence.
- Restores Firebase auth state through `AuthManager`.
- Initializes `FirebaseManager` using either authenticated UID or a generated device ID.
- Restores Trakt auth through `TraktAuthManager`.
- Starts Firebase-backed stream-provider configuration sync.
- Starts continuous network monitoring through `NetworkConnectivityChecker`.

The main architecture is MVVM plus repositories:

- Compose screens render state and dispatch UI events.
- ViewModels expose `StateFlow` UI state.
- Repositories handle TMDB, Trakt, IPTV, schedule, and local data operations.
- Room stores saved media, watch history, cached movie/TV rows, cached detail records, and genres.
- SharedPreferences stores lightweight settings, device ID, IPTV favorites, and provider preferences.

## Screens And Navigation

TV navigation is implemented in `ui/navigation/NavGraph.kt`.

Mobile navigation is implemented in `ui/navigation/MobileNavGraph.kt`.

Shared routes are declared in `ui/navigation/Screen.kt`.

### TV Screens

- `HomeScreen`: hero content, continue watching, trending rows, curated rows, companies/networks, and top navigation.
- `MoviesScreen`: movie browsing.
- `TvShowsScreen`: TV browsing.
- `MyListScreen`: saved movies, TV shows, companies, networks, and cast shortcuts.
- `LiveTvScreen`: Live TV, Schedule, and My Channels tabs.
- `SearchScreen`: movie and TV search.
- `MovieDetailScreen`: metadata, cast, crew, recommendations, company navigation, and play entry.
- `TvShowDetailScreen`: show metadata, seasons, episodes, networks, cast, and play entry.
- `SeasonEpisodesScreen`: episode browsing and playback entry.
- `StreamLinksScreen`: provider selection before WebView playback.
- `MediaListScreen`: movies by company or TV shows by network.
- `CastDetailScreen`, `CastImagesScreen`, `ImageSliderScreen`: cast filmography and profile-image browsing.
- `SettingsScreen`: providers, cache, sync, updates, Live TV data, ads setting, and Trakt entry.
- `TraktProfileScreen`: Trakt profile, watch history, collection, and watchlist views.

### Mobile Screens

- `MobileHomeScreen`, `MobileMoviesScreen`, `MobileTvShowsScreen`, `MobileMyListScreen`.
- `MobileLiveTvScreen`.
- `MobileSearchScreen`.
- `MobileMovieDetailScreen`, `MobileTvShowDetailScreen`, `MobileSeasonEpisodesScreen`, `MobileStreamLinksScreen`.
- `MobileGenresScreen`, `MobileGenreContentScreen`, `SeeAllScreen`.
- `MobileMediaListScreen`.
- `MobileCastDetailScreen`.
- `MobileSettingsScreen`, `MobileTraktProfileScreen`.

## Playback

### WebView Playback

`PlayerActivity` handles movie and TV stream playback using a WebView. It:

- Receives TMDB ID, media type, season/episode, metadata, and optional stream URL/iframe HTML.
- Resolves provider HTML through `StreamProviderManager`.
- Uses `AdBlockerWebViewClient`.
- Blocks popups in `WebChromeClient.onCreateWindow`.
- Tracks watch progress every 15 seconds.
- Stores playback position in local watch history.
- Uses a TV/Fire TV cursor overlay for D-pad navigation when appropriate.
- Uses mobile/tablet behavior without the cursor.

### Stream Providers

`StreamProviderManager` contains local fallback provider templates and can load provider configuration from Firebase Realtime Database path:

`app_config/stream_providers_Configuration`

Providers define movie and TV URL templates, iframe attributes, provider parameters, and phone-only flags. The manager can generate iframe HTML or direct provider URLs.

### IPTV Playback

`IptvPlayerActivity` plays selected Live TV channels with channel metadata such as name, stream URL, logo, TVG ID/name, and group.

### Schedule Playback

`SchedulePlayerActivity` plays scheduled channels. It:

- Receives channel ID, channel name, event title, optional direct iframe URLs, and selected player index.
- Fetches `ChannelWatchPage` data through `ScheduleRepository`.
- Shows a top server/source selector.
- Uses `AdBlockerWebViewClient` as its base WebView client.
- Adds schedule-specific autoplay/unmute HTML injection for nested frames.
- Suppresses overlays and ad iframes that appear after page load.
- Falls back to the next available player/server on main-frame errors.
- Provides TV cursor support and a top bar with back/source controls.

### YouTube Playback

`YouTubePlayerActivity` is available for YouTube video playback through the Android YouTube Player dependency.

## Live TV And Schedule

`LiveTvScreen` contains three tabs:

- `Live TV`: category list, channel grid, search, and playback.
- `Schedule`: event schedule grouped by day/category; expanding an event exposes available channel chips that open `SchedulePlayerActivity`.
- `My Channels`: favorite IPTV channels.

`LiveTvViewModel` handles:

- Loading and caching the M3U playlist.
- Loading EPG data.
- Category selection.
- Channel search.
- Favorite channels in local SharedPreferences.
- Two-way favorite channel sync with Firebase.

`IptvRepository` handles:

- Remote M3U playlist URL:
  `https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u`
- Remote XMLTV EPG URL:
  `https://raw.githubusercontent.com/JulioCesarXY/EPG-LG-Channels/refs/heads/main/lg_epg_us.xml`
- Six-hour playlist/EPG cache.
- Streaming M3U parsing.
- Channel-to-EPG matching by `tvg-id`, `tvg-name`, or channel name.

`ScheduleRepository` handles:

- Schedule fetching and cache.
- Upcoming-event helpers.
- Category/event filtering.
- Channel watch-page fetching.
- Iframe HTML generation for scheduled streams.

`ChannelScraper` supports scraping `dlhd.pk` 24/7 channel pages, extracting watch-page URLs and stream iframe/server URLs.

## Data, Sync, And Storage

### TMDB

`TmdbRepository` handles:

- Trending, popular, top-rated, and now-playing movies/TV.
- Movie, TV, season, and episode details.
- Genres.
- Search and multi-search.
- Recommendations.
- Companies and networks.
- Collections.
- Cast, crew, person details, person credits, and person images.
- GitHub-hosted curated movie/TV lists.
- Local watch history and continue-watching flows.
- Playback position updates.
- Expired cache cleanup.

### Room

`AppDatabase` version `2` contains:

- `SavedMediaEntity`
- `WatchHistoryEntity`
- `CachedMovieEntity`
- `CachedTvShowEntity`
- `CachedMovieDetailEntity`
- `CachedTvShowDetailEntity`
- `GenreEntity`

DAOs are grouped under `data/local/dao`.

### My List

`MyListManager` and `SavedMediaDao` provide local saved-media persistence. Detail screens use `DetailViewModel.toggleMyList(...)` to add/remove movies and TV shows.

### Firebase

Firebase is used for:

- Analytics.
- Auth and persisted sign-in state.
- Realtime Database sync.
- Firestore dependency support.
- Cloud Messaging dependency support.

`FirebaseSyncManager` syncs:

- My List.
- Companies.
- Networks.
- Casts.
- Watch history.
- Favorite Live TV channels.
- Default stream provider.

It exposes sync state, progress, and messages through `StateFlow`.

### Trakt

`TraktAuthManager` supports:

- OAuth URL generation.
- Code exchange.
- Token persistence.
- Token refresh.
- Valid-token retrieval.
- User settings/profile fetch.
- Sign out.

`TraktRepository` supports:

- User settings.
- Watch history.
- Collection.
- Watchlist.
- Recommendations.
- Movie and episode scrobbling.
- Watchlist add/remove helpers.

`TraktSyncManager` contains a full-sync workflow for watch history, collection, and watchlist.

## Ads And Consent

Ads are initialized only after UMP consent resolves in `SplashActivity`.

Initialization order:

1. StartApp
2. AdMob
3. Wortise
4. Unity

The core fallback dispatcher is `AdFallbackDispatcher`.

Core ad priority:

`StartApp -> AdMob -> Wortise -> Unity`

Supported formats:

- Banners
- Interstitials
- Rewarded ads
- StartApp splash helper
- Wortise app-open ads

Important classes:

- `StartAppAdManager`
- `AdManager` for AdMob
- `WortiseAdManager`
- `UnityAdManager`
- `AdFallbackDispatcher`
- `TvInterstitialManager`
- `AppOpenAdObserver`
- `ConsentManager`

Generic TV/mobile banner surfaces now prefer StartApp. Explicitly named banner composables such as `WortiseBannerAdView` and `UnityBannerAdView` remain network-specific.

Users can disable ads through settings, and ad managers check that preference before loading or showing ads.

## Network And Protection

### Connectivity Monitoring

`NetworkConnectivityChecker` continuously monitors connectivity using:

- `ConnectivityManager.NetworkCallback`
- Periodic reachability checks
- Active test hosts
- Network diagnostics

Diagnostics include:

- Network type
- DNS servers
- VPN state
- Proxy state
- Metered state

The app detects ad-blocking DNS, VPN, and proxy conditions and can show `NetworkStateDialog` from the root UI. The explicit DNS allowlist currently permits:

- `192.168.100.1`
- `8.8.8.8`

### WebView Ad Blocking

`AdBlockerWebViewClient` blocks common ad domains and injects CSS/DOM cleanup after page load.

Additional supporting pieces:

- `AdvancedAdBlocker`
- `FilterListUpdater`
- `DomainTrie`
- bundled `easylist.txt`, `easyprivacy.txt`, and `custom_filters.txt`

`PlayerActivity` and `SchedulePlayerActivity` both use `AdBlockerWebViewClient`.

## Tech Stack

| Area | Implementation |
| --- | --- |
| Language | Kotlin |
| Android Gradle Plugin | 8.13.2 |
| Kotlin Gradle plugin | 2.1.10 |
| Java target | 17 |
| Compile SDK | 35 |
| Min SDK | 24 |
| Target SDK | 35 |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Async | Kotlin Coroutines, StateFlow |
| Local DB | Room 2.6.1 |
| Networking | Retrofit 2.11.0, OkHttp 4.12.0, Volley |
| DNS | OkHttp DNS-over-HTTPS dependency |
| Image loading | Coil Compose, Glide |
| Media | Media3 ExoPlayer 1.5.1 |
| Web playback | Android WebView, AndroidX WebKit |
| HTML parsing | Jsoup 1.18.1 |
| Firebase | Analytics, Auth, Realtime Database, Firestore, Messaging |
| Ads | StartApp, AdMob, Wortise, Unity, UMP |
| Animations | Lottie Compose |
| YouTube | Android YouTube Player |

## Project Structure

```text
KiduyuTv_final_room/
|-- app/
|   |-- build.gradle
|   |-- google-services.json
|   |-- proguard-rules.pro
|   `-- src/main/
|       |-- AndroidManifest.xml
|       |-- assets/
|       |   |-- custom_filters.txt
|       |   |-- easylist.txt
|       |   `-- easyprivacy.txt
|       |-- java/com/kiduyuk/klausk/kiduyutv/
|       |   |-- activity/
|       |   |   |-- mainactivity/
|       |   |   `-- splashactivity/
|       |   |-- application/
|       |   |-- data/
|       |   |   |-- api/
|       |   |   |-- local/
|       |   |   |-- model/
|       |   |   |-- remote/
|       |   |   |-- repository/
|       |   |   `-- sync/
|       |   |-- network/
|       |   |-- ui/
|       |   |   |-- components/
|       |   |   |-- navigation/
|       |   |   |-- player/
|       |   |   |-- screens/
|       |   |   `-- theme/
|       |   |-- util/
|       |   `-- viewmodel/
|       `-- res/
|-- lists/
|-- build.gradle
|-- settings.gradle
|-- gradle/
|-- VERSION
`-- README.md
```

## Build Variants

The app uses one flavor dimension: `formfactor`.

### Phone flavor

- Application ID suffix: `.phone`
- Version name suffix: `-phone`
- Touch-first mobile navigation graph.
- Mobile bottom navigation and mobile detail/search/settings screens.

### TV flavor

- Application ID suffix: `.tv`
- Version name suffix: `-tv`
- TV navigation graph.
- D-pad-first focus handling.
- TV banner overlay support.
- Leanback/touchscreen-optional manifest support.

Common default config:

- `applicationId`: `com.kiduyuk.klausk.kiduyutv`
- `versionCode`: `4`
- `versionName`: `1.1.71`
- `minSdk`: `24`
- `targetSdk`: `35`
- `compileSdk`: `35`
- `multiDexEnabled`: `true`

## Configuration

### Firebase

The app expects `app/google-services.json`.

Firebase-related initialization happens in:

- `KiduyuTvApp`
- `AuthManager`
- `FirebaseManager`
- `FirebaseSyncManager`
- `StreamProviderManager`

### TMDB

TMDB API access is implemented under:

- `data/api/ApiClient.kt`
- `data/api/TmdbApiService.kt`
- `data/repository/TmdbRepository.kt`

### Trakt

Trakt auth and API access are implemented under:

- `util/TraktAuthManager.kt`
- `data/remote/TraktApiClient.kt`
- `data/remote/TraktApiService.kt`
- `data/repository/TraktRepository.kt`
- `data/sync/TraktSyncManager.kt`

### Ads

Ad IDs and manifest placeholders are configured in `app/build.gradle` and `AndroidManifest.xml`.

Current ad managers:

- StartApp: `StartAppAdManager`
- AdMob: `AdManager`
- Wortise: `WortiseAdManager`
- Unity: `UnityAdManager`

### Curated Lists

Curated lists live in `/lists` and are fetched from GitHub raw URLs by `HomeViewModel`.

Current list files include:

- `oscar_winners_2026.json`
- `hallmark_movies.json`
- `true_story_movies.json`
- `best_sitcoms.json`
- `best_classics.json`
- `cia_mossad_spies.json`
- `jason_statham_movies.json`
- `time_travel_movies.json`
- `christian_movies.json`
- `movies_from_the_bible.json`
- `christian_tv_shows.json`
- `doctor_who_specials.json`
- `companies_networks.json`

## Development Notes

Common Gradle tasks:

```bash
./gradlew assemblePhoneDebug
./gradlew assembleTvDebug
./gradlew assemblePhoneRelease
./gradlew assembleTvRelease
```

Debug APK outputs:

```text
app/build/outputs/apk/phone/debug/
app/build/outputs/apk/tv/debug/
```

Release APK outputs:

```text
app/build/outputs/apk/phone/release/
app/build/outputs/apk/tv/release/
```

Notes:

- Release signing is configured in Gradle; use project-specific signing material for production.
- R8 minification and resource shrinking are enabled for release.
- The app allows cleartext traffic and uses `network_security_config.xml`.
- The manifest declares storage/media permissions, notification permission, ad ID permission, install-packages permission, TV leanback support, and touchscreen as optional.
- Gradle wrapper files are included.

## License

This project is proprietary and all rights are reserved. No part of this codebase, documentation, or associated assets may be used, copied, modified, merged, published, distributed, sublicensed, leased, sold, or otherwise exploited without prior written permission from the copyright holder.

See [LICENSE](LICENSE) for details.
