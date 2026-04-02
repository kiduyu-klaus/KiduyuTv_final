# KiduyuTV

<div align="center">

![KiduyuTV Banner](https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/main/app/src/main/res/mipmap-xhdpi/ic_banner.png)

**A modern Android TV and Fire TV streaming application featuring a curated collection of movies and TV shows**
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Fire%20TV-FF6B35?style=flat-square)](https://developer.android.com/tv)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?style=flat-square)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-61DAFB?style=flat-square)](https://developer.android.com/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green?style=flat-square)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-red?style=flat-square)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![Build Status](https://img.shields.io/github/actions/workflow/status/kiduyu-klaus/KiduyuTv_final/kiduyu_final.yml?branch=main&style=flat-square&label=Build)](https://github.com/kiduyu-klaus/KiduyuTv_final/actions)
[![Latest Release](https://img.shields.io/github/v/release/kiduyu-klaus/KiduyuTv_final?include_prereleases&style=flat-square&label=Release)](https://github.com/kiduyu-klaus/KiduyuTv_final/releases/latest)
[![ExoPlayer](https://img.shields.io/badge/ExoPlayer-1.5.1-orange?style=flat-square)](https://developer.android.com/media/media3/exoplayer)
[![WebView](https://img.shields.io/badge/WebView-Chromium-4285F4?style=flat-square)](https://developer.android.com/reference/android/webkit/WebView)
[![TMDB](https://img.shields.io/badge/TMDB-API-01B4E4?style=flat-square&logo=themoviedatabase&logoColor=white)](https://www.themoviedatabase.org)


</div>

---

## Overview

KiduyuTV is a feature-rich streaming application designed specifically for the big screen experience. Built with Jetpack Compose and Material 3, it delivers a Netflix-like lean-back interface optimized for TV remote navigation with full D-Pad support. The app integrates with The Movie Database (TMDB) API to provide real-time movie and TV show data across multiple curated categories.


| Feature | Details |
|---|---|
| 🎬 **Content** | Movies, TV shows, by Production Company & TV Network |
| 🔍 **Discovery** | Search, genre browsing, cast & crew detail pages |
| ▶️ **Playback** | HLS · DASH · Progressive via ExoPlayer with multi-server failover |
| 📝 **Subtitles** | External subtitle fetch via Subdl, SRT · VTT · SSA/ASS · TTML |
| 🎮 **Navigation** | Full D-pad focus management, scale animations, back-stack handling |
| 💾 **History** | Watch progress saved and resumed per title |
| ⚙️ **Settings** | Playback buffer duration, preferences persistence |


## Key Features

### Immersive TV Experience

- **D-Pad Navigation**: Full TV remote and Fire TV controller support with intuitive focus states
- **Hero Section**: Dynamic backdrop carousel showcasing featured content with smooth transitions
- **Content Rows**: Horizontal scrolling lists powered by LazyRow for smooth performance
- **Detail Views**: Comprehensive movie and show information with ratings, overviews, and posters

### Curated Content Categories

The app includes multiple pre-configured content lists covering diverse genres:

| Category | Description |
|----------|-------------|
| Oscar Winners 2026 | Award-winning films and critically acclaimed titles |
| Hallmark Movies | Heartwarming family-friendly content |
| Jason Statham Collection | Action-packed blockbuster movies |
| Best Classics | Timeless cinema masterpieces |
| Best Sitcoms | Beloved comedy series |
| Spy Thrillers | CIA, Mossad, and espionage films |
| True Stories | Documentaries based on real events |
| Time Travel | Science fiction adventures through time |

### Modern Architecture

- **Clean Architecture**: Proper separation between UI, business logic, and data layers
- **MVVM Pattern**: Reactive ViewModels with state management using Kotlin Flow
- **Repository Pattern**: Centralized data management with offline caching support
- **Room Database**: Local persistence for favorites and watch history

## Tech Stack

| Category | Technology | Version |
|----------|------------|---------|
| Language | Kotlin | 1.9.24 |
| UI Framework | Jetpack Compose | BOM 2024.12.01 |
| Design System | Material 3 | Latest |
| Navigation | Navigation Compose | 2.8.5 |
| Networking | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| Image Loading | Coil + Glide | 2.7.0 / 4.16.0 |
| Animations | Lottie Compose | 6.6.2 |
| Database | Room | 2.6.1 |
| Async | Coroutines | 1.8.1 |
| DI | KSP | Latest |

## Project Structure

```
KiduyuTv/
├── app/
│   └── src/main/
│       ├── java/com/kiduyuk/klausk/kiduyutv/
│       │   ├── data/
│       │   │   ├── api/           # TMDB API service definitions
│       │   │   ├── model/         # Data models and DTOs
│       │   │   └── repository/    # Repository implementations
│       │   ├── ui/
│       │   │   ├── components/    # Reusable Compose components
│       │   │   ├── navigation/    # Navigation graph setup
│       │   │   ├── screens/       # Screen composables
│       │   │   │   ├── detail/    # Movie/Show detail screens
│       │   │   │   └── home/      # Home and browse screens
│       │   │   └── theme/         # App theming and colors
│       │   └── viewmodel/         # ViewModel classes
│       ├── res/                   # Android resources
│       └── assets/                # App assets and lists
├── lists/                         # Curated content JSON files
├── gradle/wrapper/                # Gradle wrapper
├── build.gradle                    # Root build configuration
└── settings.gradle               # Project settings
```

## Getting Started

### Prerequisites

Ensure you have the following installed on your development machine:

- **Android Studio** Hedgehog (2024.1.1) or later
- **Android SDK** 35 (compileSdk and targetSdk)
- **Java Development Kit** 17 or later
- **Gradle** 8.13 (automatically managed by wrapper)

### Installation Steps

#### 1. Clone the Repository

```bash
git clone https://github.com/kiduyu-klaus/KiduyuTv_final.git
cd KiduyuTv_final
```

#### 2. Configure Android SDK

Update the `local.properties` file with your Android SDK path:

```properties
sdk.dir=/path/to/your/Android/sdk
```

For macOS, this is typically:
```properties
sdk.dir=/Users/yourusername/Library/Android/sdk
```

#### 3. Setup Gradle Wrapper

The project includes a setup script to download the Gradle wrapper JAR:

```bash
chmod +x setup_gradle.sh
./setup_gradle.sh
```

Or manually download the wrapper JAR:

```bash
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  "https://github.com/gradle/gradle/raw/v8.13/gradle/wrapper/gradle-wrapper.jar"
```

#### 4. Open in Android Studio

1. Launch Android Studio
2. Select **Open an existing project**
3. Navigate to the `KiduyuTv_final` directory
4. Wait for Gradle sync to complete (check the status bar)

#### 5. Build and Run

**Build Debug APK:**
```bash
./gradlew assembleDebug
```

The debug APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

**Build Release APK:**
```bash
./gradlew assembleRelease
```

The release APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

### Running on Device or Emulator

1. Connect your Android TV, Fire TV, or Android device
2. Enable developer options and USB debugging
3. In Android Studio, select your target device from the device dropdown
4. Click **Run** (or press `Shift + F10`)

## Configuration

### TMDB API Setup

The app uses The Movie Database (TMDB) API for fetching movie and TV show data. The API token is configured in the data layer. For production use, replace the placeholder token in the API service configuration with your own TMDB API key.

### Content Lists

Curated content lists are stored as JSON files in the `/lists` directory. Each list contains TMDB movie IDs that are fetched at runtime. You can customize these lists by editing the JSON files:

```json
[
  {
    "id": 12345,
    "title": "Movie Title",
    "overview": "Movie description...",
    "posterPath": "/poster.jpg",
    "backdropPath": "/backdrop.jpg",
    "voteAverage": 8.5,
    "releaseDate": "2025-01-15",
    "genreIds": [28, 12, 878],
    "popularity": 150.234
  }
]
```

## Troubleshooting

### Gradle Sync Failed

- Verify Java 17+ is installed: `java -version`
- Ensure Android SDK is properly configured in `local.properties`
- Try invalidating caches: **File > Invalidate Caches > Invalidate and Restart**

### Build Errors

- Clean the project: `./gradlew clean`
- Rebuild: `./gradlew assembleDebug`
- Check the Gradle sync status in the Android Studio status bar

### API Errors

- Verify your internet connection
- Confirm the TMDB API token is valid
- Check Logcat for detailed error messages: `adb logcat | grep "KiduyuTV"`

### TV Remote Not Working

- Ensure your device is detected as a TV device
- Check that the Leanback library is properly included
- Verify D-Pad events are being captured in the app

## Screenshots

<div align="center">

| Home Screen | Detail View | Content Row |
|:-----------:|:-----------:|:-----------:|
| ![Home](docs/screenshots/home.png) | ![Detail](docs/screenshots/detail.png) | ![Browse](docs/screenshots/browse.png) |

</div>

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [The Movie Database (TMDB)](https://www.themoviedb.org/) for providing the API
- [Jetpack Compose Team](https://developer.android.com/compose) for the amazing UI toolkit
- All contributors and open-source library maintainers

---

<div align="center">

**Built with passion for the big screen experience**

*KiduyuTV - Your gateway to premium streaming*

</div>
