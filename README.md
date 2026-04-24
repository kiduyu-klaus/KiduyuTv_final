# KiduyuTV

<div align="center">

![KiduyuTV Banner](https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/main/app/src/main/res/mipmap-xhdpi/ic_banner.png)

**A modern dual-platform streaming application for Android TV, Fire TV, and mobile devices featuring a curated collection of movies and TV shows**

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Fire%20TV%20%7C%20Mobile-FF6B35?style=for-the-badge)](https://developer.android.com/tv)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?style=for-the-badge)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-61DAFB?style=for-the-badge)](https://developer.android.com/compose)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-red?style=for-the-badge)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![Build Status](https://img.shields.io/github/actions/workflow/status/kiduyu-klaus/KiduyuTv_final/kiduyu_final.yml?branch=main&style=for-the-badge&label=Build)](https://github.com/kiduyu-klaus/KiduyuTv_final/actions)
[![Media3 ExoPlayer](https://img.shields.io/badge/Media3%20ExoPlayer-1.5.1-orange?style=for-the-badge)](https://developer.android.com/media/media3/exoplayer)
[![TMDB API](https://img.shields.io/badge/TMDB%20API-01B4E4?style=for-the-badge&logo=themoviedatabase&logoColor=white)](https://www.themoviedb.org)

</div>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Build Variants](#build-variants)
- [Content Categories](#content-categories)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

KiduyuTV is a production-ready streaming application that delivers a premium viewing experience across Android TV, Amazon Fire TV, and mobile devices. Built with modern Android development practices using Jetpack Compose and Material Design 3, the app provides a Netflix-style interface with smooth D-pad navigation for television remotes and intuitive touch interactions for mobile devices.

The application integrates with The Movie Database (TMDB) API to provide real-time access to extensive movie and television content, enhanced by curated collections hosted on GitHub. With intelligent caching powered by Room database, users enjoy seamless offline access to previously viewed content while maintaining fresh data synchronization.

KiduyuTV supports multiple streaming formats including HLS adaptive streaming through Media3 ExoPlayer, YouTube video playback, and WebView-based content. The app implements a dual-platform strategy through build flavors, generating optimized variants for both television and mobile experiences from a single codebase.

---

## Features

### Dual-Platform Experience

KiduyuTV provides optimized interfaces for both television and mobile platforms, automatically detecting the device type at runtime and applying the appropriate navigation patterns and UI layouts. The television interface features D-pad focus management with visual focus indicators, while the mobile interface leverages touch gestures and swipe navigation for fluid content browsing.

### Content Discovery

The home screen presents a dynamic hero section showcasing featured content with backdrop images and smooth transitions. Below the hero, content rows organized by category enable horizontal scrolling through curated collections. Users can browse by production company, television network, genre, or curated themes, with intelligent search functionality supporting both movies and TV shows.

### Curated Collections

KiduyuTV includes twelve pre-configured content categories spanning diverse genres and themes. Oscar Winners 2026 showcases recent award-winning films, while Hallmark Movies provides family-friendly holiday content. Action enthusiasts can explore the Jason Statham Collection, and fans of science fiction can dive into Time Travel movies and TV shows. Additional collections include Best Classics, Best Sitcoms, Spy Thrillers, True Stories, Christian Movies and TV Shows, Bible Movies, and Doctor Who Specials.

### Streaming Playback

The application supports multiple playback mechanisms for diverse content types. Media3 ExoPlayer handles HLS adaptive bitrate streaming with intelligent quality selection based on network conditions. YouTube videos play natively through the YouTube Android Player library, while WebView-based playback serves as a fallback for embedded content from various sources. The stream links screen aggregates available sources for content that may be distributed across multiple providers.

### Personalization

User personalization features include watch history tracking with automatic position restoration for seamless continuation of interrupted viewing sessions. The My List feature enables users to save favorites for quick access, with data persisted locally using Room database and synchronized to Firebase for signed-in users. Watch history enrichment automatically updates metadata from TMDB to ensure even historical items display current poster images and complete information.

### Modern Architecture

The application implements Clean Architecture principles with clear separation between UI, business logic, and data layers. The MVVM pattern with Kotlin Flow manages reactive state updates, while the Repository pattern provides centralized data access with intelligent caching strategies. Room database enables offline-first functionality with configurable cache expiration periods.

---

## Tech Stack

| Category | Technology | Version |
|----------|------------|---------|
| Language | Kotlin | 1.9.24 |
| UI Framework | Jetpack Compose | BOM 2024.12.01 |
| Design System | Material Design 3 | Latest |
| Navigation | Navigation Compose | 2.8.5 |
| Networking | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| JSON Parsing | Gson | 2.10.1 |
| Image Loading | Coil + Glide | 2.7.0 / 4.16.0 |
| Animations | Lottie Compose | 6.6.2 |
| Database | Room | 2.6.1 |
| Media Playback | Media3 ExoPlayer | 1.5.1 |
| Async Operations | Kotlin Coroutines | 1.8.1 |
| Code Generation | KSP | Latest |
| Firebase | Analytics, Auth, Firestore, Database | Various |
| YouTube Playback | Android YouTube Player | 13.0.0 |

---

## Architecture

KiduyuTV follows Clean Architecture principles with three distinct layers that ensure maintainability, testability, and scalability.

### Data Layer

The data layer handles all external and local data operations through the repository pattern. The `TmdbRepository` class coordinates API calls to The Movie Database, managing request execution, response caching, and error handling. Room database entities store cached content with expiration timestamps, enabling automatic cache invalidation and offline access. The GitHub-hosted content fetcher retrieves curated lists using a dedicated OkHttp client configured for larger payload handling with DNS-over-HTTPS support.

### Domain Layer

Business logic resides in ViewModels that manage UI state and coordinate between the data and presentation layers. The `HomeViewModel` orchestrates parallel loading of content from multiple sources using coroutines and async operations, while individual ViewModels handle specific features including search, detail views, and media playback. State management uses Kotlin Flow for reactive updates throughout the application.

### Presentation Layer

The UI layer implements screens and components using Jetpack Compose with Material Design 3 theming. Reusable composables handle common patterns including content rows with lazy loading, hero sections with dynamic backdrop transitions, and network logo displays. The navigation system uses typed routes with arguments for type-safe screen transitions, while platform-specific navigation graphs handle television and mobile interaction patterns independently.

---

## Project Structure

```
KiduyuTv_final/
├── app/
│   └── src/main/
│       ├── java/com/kiduyuk/klausk/kiduyutv/
│       │   ├── activity/
│       │   │   ├── mainactivity/      # Main entry point with Compose setup
│       │   │   └── splashactivity/    # Splash screen and initialization
│       │   ├── data/
│       │   │   ├── api/               # Retrofit API definitions
│       │   │   ├── local/             # Room database, DAOs, entities
│       │   │   ├── model/             # Data models and DTOs
│       │   │   └── repository/         # Repository implementations
│       │   ├── network/                # Network utilities and monitoring
│       │   ├── ui/
│       │   │   ├── components/        # Reusable Compose components
│       │   │   │   ├── mobile/        # Phone-specific components
│       │   │   ├── navigation/         # Navigation graphs and routes
│       │   │   ├── player/             # Video player implementations
│       │   │   │   ├── webview/        # WebView player activity
│       │   │   │   └── youtube/        # YouTube player activity
│       │   │   ├── screens/            # Screen composables
│       │   │   │   ├── cast/           # Cast detail screens
│       │   │   │   ├── company_network_list/
│       │   │   │   ├── detail/         # Movie/TV detail screens
│       │   │   │   ├── home/           # Home and browse screens
│       │   │   │   ├── search/         # Search screens
│       │   │   │   └── settings/       # Settings screens
│       │   │   └── theme/              # Material 3 theming
│       │   ├── util/                   # Utilities and helpers
│       │   └── viewmodel/              # ViewModel classes
│       ├── res/                        # Android resources
│       └── assets/                     # App assets and easylist
├── lists/                              # Curated content JSON files
├── gradle/wrapper/                     # Gradle wrapper files
├── build.gradle                         # Root build configuration
└── settings.gradle                      # Project settings
```

---

## Getting Started

### Prerequisites

Before building KiduyuTV, ensure your development environment meets the following requirements:

- **Android Studio** Hedgehog (2024.1.1) or later with Kotlin plugin
- **Android SDK** 35 with build tools installed
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

**macOS typical path:**
```properties
sdk.dir=/Users/yourusername/Library/Android/sdk
```

**Linux typical path:**
```properties
sdk.dir=/home/yourusername/Android/Sdk
```

#### 3. Setup Gradle Wrapper

The project includes a setup script to download the Gradle wrapper JAR:

```bash
chmod +x setup_gradle.sh
./setup_gradle.sh
```

Alternatively, manually download the wrapper JAR from the Gradle releases page and place it in `gradle/wrapper/`.

#### 4. Open in Android Studio

1. Launch Android Studio
2. Select **Open an existing project**
3. Navigate to the `KiduyuTv_final` directory
4. Wait for Gradle sync to complete
5. The project will configure itself with the correct SDK targets and dependencies

#### 5. Build the Project

**Build Debug APK (both flavors):**
```bash
./gradlew assembleDebug
```

**Build Phone Debug APK specifically:**
```bash
./gradlew assemblePhoneDebug
```

**Build TV Debug APK specifically:**
```bash
./gradlew assembleTvDebug
```

**Build Release APK (requires signing configuration):**
```bash
./gradlew assembleRelease
```

Debug APKs are generated at:
- Phone: `app/build/outputs/apk/phone/debug/`
- TV: `app/build/outputs/apk/tv/debug/`

#### 6. Running on Device or Emulator

1. Connect your Android TV, Fire TV, or Android device
2. Enable developer options and USB debugging on your device
3. Ensure your device is detected: `adb devices`
4. In Android Studio, select your target device
5. Click **Run** or press `Shift + F10`

---

## Configuration

### TMDB API Setup

KiduyuTV uses The Movie Database (TMDB) API for all movie and TV show data. The API configuration is managed in the `ApiClient.kt` file within the data layer. For production deployment, replace the placeholder token with your own TMDB API key obtained from [TMDB API Settings](https://www.themoviedb.org/settings/api).

```kotlin
// In ApiClient.kt
companion object {
    private const val TMDB_API_KEY = "your_api_key_here"
}
```

### Firebase Configuration

The app integrates with Firebase services for analytics, authentication, and data synchronization. Place your `google-services.json` file in the `app/` directory. This file is downloaded from the Firebase Console and contains your project-specific configuration including API keys and application IDs.

### AdMob / Ad Manager

Advertising is configured through the manifest metadata. For production, update the application ID in `AndroidManifest.xml` with your own AdMob application ID from the Google AdMob console.

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-xxxxxxxx~xxxxxxxx"/>
```

### Content Lists

Curated content lists are stored as JSON files in the `/lists` directory and hosted on GitHub for easy updates without app releases. Each list contains TMDB movie or TV show IDs with pre-fetched metadata for efficient batch loading.

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

To create custom content lists, add JSON files to the repository under `/lists` and update the URLs in `HomeViewModel.kt`.

---

## Build Variants

KiduyuTV uses product flavors to generate platform-optimized builds from a single codebase.

### Phone Flavor

The phone variant targets Android smartphones and tablets with touch-based interfaces. This variant includes Google AdMob integration for mobile advertising and uses the mobile navigation graph with touch-optimized layouts.

```gradle
phone {
    dimension "formfactor"
    applicationIdSuffix ".phone"
    versionNameSuffix "-phone"
    resValue "string", "app_name", "KiduyuTV"
}
```

### TV Flavor

The TV variant targets Android TV, Amazon Fire TV, and other television platforms. This variant includes Google Ad Manager integration for television-appropriate advertising and uses the TV navigation graph with D-pad focus management.

```gradle
tv {
    dimension "formfactor"
    applicationIdSuffix ".tv"
    versionNameSuffix "-tv"
    resValue "string", "app_name", "KiduyuTV"
}
```

### Build Types

**Debug:** Optimized for development with disabled code shrinking and resource shrinking for faster build times.

**Release:** Full optimization enabled including R8 minification, code shrinking, and resource shrinking. Requires valid signing configuration for APK generation.

---

## Content Categories

KiduyuTV provides the following curated content collections:

| Category | Description | Content Type |
|----------|-------------|--------------|
| Oscar Winners 2026 | Award-winning films from recent ceremonies | Movies |
| Hallmark Movies | Heartwarming family-friendly content | Movies |
| Jason Statham Collection | Action-packed blockbuster movies | Movies |
| Best Classics | Timeless cinema masterpieces | Movies |
| Best Sitcoms | Beloved comedy series | TV Shows |
| Spy Thrillers | CIA, Mossad, and espionage films | Movies |
| True Stories | Documentaries and dramatizations of real events | Movies |
| Time Travel | Science fiction adventures through time | Movies & TV Shows |
| Christian Movies | Faith-based entertainment | Movies |
| Christian TV Shows | Faith-based television content | TV Shows |
| Bible Movies | Cinematic adaptations of biblical stories | Movies |
| Doctor Who Specials | Iconic British sci-fi series content | Movies |

---

## Troubleshooting

### Gradle Sync Failed

- Verify Java 17+ is installed: `java -version`
- Ensure Android SDK is properly configured in `local.properties`
- Clear Gradle caches: `./gradlew clean`
- In Android Studio: **File > Invalidate Caches > Invalidate and Restart**

### Build Errors

- Clean the project: `./gradlew clean`
- Rebuild: `./gradlew assembleDebug`
- Check for dependency version conflicts in the Gradle sync output
- Verify all required files (google-services.json) are present

### API Errors

- Verify your internet connection
- Confirm the TMDB API token is valid and has not expired
- Check Logcat for detailed error messages: `adb logcat | grep "KiduyuTV"`
- Review network security configuration if running on restricted networks

### TV Remote Navigation Issues

- Ensure your device is detected as a TV device in system settings
- Verify the device is running Android TV or Fire OS with leanback support
- Check that focusable elements have proper focus state styling
- Enable developer options on the TV device for additional diagnostics

### Playback Issues

- For HLS streaming, verify network connectivity and bandwidth
- YouTube playback requires Google Play Services on the device
- WebView playback may require updated Chromium on older devices
- Check device storage if buffering issues occur frequently

---

## Contributing

Contributions are welcome and appreciated. Whether you find a bug, have a feature request, or want to improve the documentation, your input helps make KiduyuTV better for everyone.

### How to Contribute

1. **Fork the Repository** - Create your own copy of the project on GitHub
2. **Create a Feature Branch** - Work on your changes in a dedicated branch
   ```bash
   git checkout -b feature/improvement-name
   ```
3. **Make Your Changes** - Implement your feature, fix the bug, or update documentation
4. **Test Your Changes** - Build and run the app to verify your changes work correctly
5. **Commit Your Changes** - Write clear, descriptive commit messages
   ```bash
   git commit -m 'Add new content category for western movies'
   ```
6. **Push to Your Branch** - Upload your changes to your fork
   ```bash
   git push origin feature/improvement-name
   ```
7. **Open a Pull Request** - Submit your changes for review and inclusion

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and small
- Use Jetpack Compose best practices

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

The MIT License is a permissive license that allows you to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the software, provided you include the copyright notice and permission notice in all copies or substantial portions of the software.

---

## Acknowledgments

KiduyuTV stands on the shoulders of giants. We gratefully acknowledge the following projects and organizations:

- **[The Movie Database (TMDB)](https://www.themoviedb.org/)** - For providing the comprehensive movie and TV show database API that powers all content discovery in the application
- **[Jetpack Compose Team](https://developer.android.com/compose)** - For creating the modern declarative UI toolkit that makes dual-platform development elegant and efficient
- **[Android Media3 Team](https://developer.android.com/media/media3)** - For ExoPlayer and the media playback libraries that enable smooth streaming
- **[Google Firebase](https://firebase.google.com/)** - For the backend services enabling analytics, authentication, and data synchronization
- **[Airbnb](https://airbnb.io/lottie/)** - For Lottie animations that add polish and delight to the user experience
- **[Coil](https://coil-kt.github.io/coil/)** - For the Kotlin-first image loading library with excellent Compose support
- **[All Open Source Contributors](https://github.com/kiduyu-klaus/KiduyuTv_final/graphs/contributors)** - For their time and contributions to the project

---

<div align="center">

**Built with passion for the big screen experience**

*KiduyuTV - Your gateway to premium streaming*

[![GitHub Stars](https://img.shields.io/github/stars/kiduyu-klaus/KiduyuTv_final?style=social)](https://github.com/kiduyu-klaus/KiduyuTv_final)
[![GitHub Forks](https://img.shields.io/github/forks/kiduyu-klaus/KiduyuTv_final?style=social)](https://github.com/kiduyu-klaus/KiduyuTv_final)
[![Follow on GitHub](https://img.shields.io/github/followers/kiduyu-klaus?style=social)](https://github.com/kiduyu-klaus)

</div>
