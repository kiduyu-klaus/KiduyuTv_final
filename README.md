# KiduyuTV

<div align="center">

![KiduyuTV Banner](https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/main/app/src/main/res/mipmap-xhdpi/ic_banner.png)

### **A Premium Dual-Platform Streaming Application**

<span>

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM_2024.12.01-FF6B6B?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/compose)
[![Platform](https://img.shields.io/badge/Platform-Android_TV_|_Fire_TV_|_Mobile-FF6B35?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/tv)
[![SDK](https://img.shields.io/badge/SDK-35-red?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/kiduyu-klaus/KiduyuTv_final/kiduyu_final.yml?branch=main&style=for-the-badge&label=Build)](https://github.com/kiduyu-klaus/KiduyuTv_final/actions)

</span>

<span>

[![Media3](https://img.shields.io/badge/Media3-ExoPlayer_1.5.1-orange?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/media/media3/exoplayer)
[![TMDB](https://img.shields.io/badge/TMDB-API-01B4E4?style=for-the-badge&logo=themoviedatabase&logoColor=white)](https://www.themoviedb.org)
[![Firebase](https://img.shields.io/badge/Firebase-Enabled-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![AI](https://img.shields.io/badge/AI-Gemini_2.5_Flash_Lite-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/)

</span>

</div>

---

## 🌟 Features

### 🤖 AI-Powered Assistant

<div align="center">

| Feature | Description |
|---------|-------------|
| **Gemini Integration** | Intelligent movie and TV show recommendations powered by Google's Gemini 2.5 Flash-Lite model |
| **Conversational Search** | Natural language queries to discover content |
| **Smart Actions** | Contextual action buttons for seamless navigation |

</div>

### 📺 Dual-Platform Experience

<div align="center">

| TV Mode | Mobile Mode |
|---------|-------------|
| D-pad navigation with visual focus indicators | Touch gestures and swipe navigation |
| Optimized layouts for large screens | Bottom navigation with quick access |
| Remote control support | Portrait and landscape support |

</div>

### 🎬 Content Discovery

<div align="center">

| Feature | Benefit |
|---------|---------|
| Dynamic Hero Section | Showcase featured content with smooth backdrop transitions |
| Curated Collections | 12 pre-configured categories for easy browsing |
| Production Networks | Browse by company and network logos |
| Intelligent Search | Find movies and TV shows instantly |

</div>

### 🎥 Streaming Capabilities

<div align="center">

| Player Type | Technology |
|-------------|------------|
| HLS Adaptive Streaming | Media3 ExoPlayer with intelligent quality selection |
| YouTube Playback | Native Android YouTube Player library |
| WebView Fallback | Embedded content from multiple providers |
| Multiple Sources | Stream links aggregated for maximum availability |

</div>

### 💾 Offline & Sync Features

<div align="center">

| Feature | Implementation |
|---------|----------------|
| Local Caching | Room database with configurable expiration |
| Watch History | Automatic position restoration |
| My List | Save favorites with Firebase sync |
| Cross-Device | Synchronized viewing across devices |

</div>

---

## 🛠 Tech Stack

<div align="center">

### Core Technologies

| Category | Technology | Version |
|----------|------------|---------|
| Language | Kotlin | 1.9.24 |
| UI Framework | Jetpack Compose | BOM 2024.12.01 |
| Design System | Material Design 3 | Latest |

### Networking & Data

| Category | Technology | Version |
|----------|------------|---------|
| Networking | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| JSON Parsing | Gson | 2.10.1 |
| Image Loading | Coil + Glide | 2.7.0 / 4.16.0 |
| Database | Room | 2.6.1 |

### Media & AI

| Category | Technology | Version |
|----------|------------|---------|
| Media Playback | Media3 ExoPlayer | 1.5.1 |
| YouTube | Android YouTube Player | 13.0.0 |
| AI Assistant | Gemini 2.5 Flash-Lite | 0.2.0 |
| Animations | Lottie Compose | 6.6.2 |

### Backend & Infrastructure

| Category | Technology | Version |
|----------|------------|---------|
| Firebase | Analytics, Auth, Firestore | Various |
| Navigation | Navigation Compose | 2.8.5 |
| Async | Kotlin Coroutines | 1.8.1 |
| Build | KSP | Latest |

</div>

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Screens   │  │Components   │  │   Navigation Graphs     │  │
│  │   (TV/Mobile)│  │  (Reusable) │  │   (Typed Routes)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Domain Layer                             │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                     ViewModels (MVVM)                       │ │
│  │  HomeViewModel │ SearchVM │ DetailVM │ StreamLinksVM        │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Kotlin Flow (State)                      │ │
│  │  StateFlow │ MutableStateFlow │ reactive updates            │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                           Data Layer                              │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────┐  │
│  │  TmdbRepository │  │ TraktRepository │  │ MyListManager      │  │
│  └───────────────┘  └───────────────┘  └─────────────────────┘  │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────┐  │
│  │  Room Database │  │  API Client   │  │  Firebase Manager   │  │
│  └───────────────┘  └───────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Responsibilities |
|-------|------------------|
| **Presentation** | Jetpack Compose UI, screen composables, reusable components, navigation |
| **Domain** | ViewModels, state management, business logic, Kotlin Flow reactive updates |
| **Data** | Repository pattern, API calls, Room caching, Firebase sync, GitHub content |

---

## 📁 Project Structure

```
KiduyuTv_final/
├── app/
│   └── src/main/
│       ├── java/com/kiduyuk/klausk/kiduyutv/
│       │   ├── activity/                    # Entry points
│       │   │   ├── mainactivity/            # Main Compose setup
│       │   │   └── splashactivity/         # Splash initialization
│       │   ├── ai/                         # AI Assistant module
│       │   │   ├── viewmodel/              # AiAssistantViewModel
│       │   │   ├── model/                  # ChatMessage models
│       │   │   ├── GeminiService.kt        # Gemini API integration
│       │   │   └── NavigationActionHandler.kt
│       │   ├── data/                      # Data layer
│       │   │   ├── api/                   # Retrofit API definitions
│       │   │   ├── local/                 # Room database, DAOs, entities
│       │   │   ├── model/                 # Data models and DTOs
│       │   │   └── repository/             # Repository implementations
│       │   ├── network/                    # Network utilities
│       │   │   ├── NetworkConnectivityChecker.kt
│       │   │   └── NetworkState.kt
│       │   ├── ui/                        # Presentation layer
│       │   │   ├── components/            # Reusable composables
│       │   │   │   ├── mobile/            # Phone-specific components
│       │   │   │   ├── ai/                # AI chat components
│       │   │   │   └── *.kt               # Shared components
│       │   │   ├── navigation/            # Navigation graphs
│       │   │   ├── player/                # Video players
│       │   │   │   ├── webview/           # WebView player
│       │   │   │   └── youtube/           # YouTube player
│       │   │   ├── screens/               # Screen composables
│       │   │   │   ├── cast/              # Cast detail screens
│       │   │   │   ├── company_network_list/
│       │   │   │   ├── detail/            # Movie/TV detail screens
│       │   │   │   ├── home/             # Home and browse screens
│       │   │   │   ├── search/           # Search screens
│       │   │   │   └── settings/         # Settings screens
│       │   │   └── theme/                 # Material 3 theming
│       │   ├── util/                      # Utilities and helpers
│       │   └── viewmodel/                 # ViewModel classes
│       ├── res/                           # Android resources
│       └── assets/                        # App assets
├── lists/                                 # Curated content JSON
├── build.gradle                           # Root build config
└── settings.gradle                        # Project settings
```

---

## 🚀 Getting Started

### Prerequisites

<div align="center">

| Requirement | Version |
|-------------|---------|
| Android Studio | Hedgehog (2024.1.1)+ |
| Android SDK | 35 |
| Java JDK | 17+ |
| Gradle | 8.13 (wrapper) |

</div>

### Installation Steps

#### 1️⃣ Clone the Repository

```bash
git clone https://github.com/kiduyu-klaus/KiduyuTv_final.git
cd KiduyuTv_final
```

#### 2️⃣ Configure Android SDK

Create or update `local.properties`:

```properties
sdk.dir=/path/to/your/Android/sdk
```

#### 3️⃣ Setup Gradle Wrapper

```bash
chmod +x setup_gradle.sh
./setup_gradle.sh
```

#### 4️⃣ Open in Android Studio

1. Launch Android Studio
2. Select **Open an existing project**
3. Navigate to `KiduyuTv_final`
4. Wait for Gradle sync to complete

#### 5️⃣ Build Commands

```bash
# Build both flavors
./gradlew assembleDebug

# Build phone flavor only
./gradlew assemblePhoneDebug

# Build TV flavor only
./gradlew assembleTvDebug

# Build release
./gradlew assembleRelease
```

---

## ⚙️ Configuration

### TMDB API Setup

Update `app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/api/ApiClient.kt`:

```kotlin
companion object {
    private const val TMDB_API_KEY = "your_api_key_here"
}
```

Get your API key from [TMDB API Settings](https://www.themoviedb.org/settings/api).

### Firebase Configuration

Place your `google-services.json` in the `app/` directory. Download from [Firebase Console](https://console.firebase.google.com/).

### Gemini AI Setup

Add your Gemini API key to `local.properties`:

```properties
GEMINI_API_KEY=your_gemini_api_key
```

Get your API key from [Google AI Studio](https://aistudio.google.com/).

---

## 📦 Build Variants

<div align="center">

| Flavor | Target | Ad Integration | Navigation |
|--------|--------|---------------|------------|
| **phone** | Smartphones & Tablets | AdMob | Touch-optimized |
| **tv** | Android TV & Fire TV | Ad Manager | D-pad navigation |

</div>

### Build Types

| Type | Features | Use Case |
|------|----------|----------|
| **Debug** | Fast builds, no minification | Development |
| **Release** | R8 minification, code shrinking | Production |

---

## 🎭 Content Categories

<div align="center">

| Category | Description | Type |
|----------|-------------|------|
| 🏆 Oscar Winners 2026 | Award-winning films | Movies |
| 🎄 Hallmark Movies | Family-friendly content | Movies |
| 💪 Jason Statham | Action blockbusters | Movies |
| 🎭 Best Classics | Timeless cinema | Movies |
| 😄 Best Sitcoms | Beloved comedies | TV Shows |
| 🕵️ Spy Thrillers | CIA & Mossad films | Movies |
| 📖 True Stories | Real event dramatizations | Movies |
| ⏰ Time Travel | Sci-fi adventures | Movies & TV |
| ✝️ Christian Movies | Faith-based entertainment | Movies |
| 📺 Christian TV Shows | Faith-based series | TV Shows |
| 📜 Bible Movies | Biblical adaptations | Movies |
| 🐲 Doctor Who Specials | Iconic British sci-fi | Movies |

</div>

---

## 🔧 Troubleshooting

### Gradle Sync Failed

```bash
# Verify Java version
java -version

# Check SDK configuration
cat local.properties

# Clear caches
./gradlew clean
```

### Build Errors

```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Check for missing files
ls app/google-services.json
```

### API Errors

```bash
# Check network connectivity
adb logcat | grep "KiduyuTV"
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** your changes: `git commit -m 'Add amazing feature'`
4. **Push** to the branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Code Standards

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and small
- Use Jetpack Compose best practices

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

<div align="center">

| Library/Service | Purpose |
|-----------------|---------|
| [The Movie Database](https://www.themoviedb.org/) | Comprehensive movie & TV API |
| [Jetpack Compose](https://developer.android.com/compose) | Modern declarative UI |
| [Media3 ExoPlayer](https://developer.android.com/media/media3) | Video playback |
| [Google Firebase](https://firebase.google.com/) | Backend services |
| [Lottie](https://airbnb.io/lottie/) | JSON animations |
| [Coil](https://coil-kt.github.io/coil/) | Image loading |
| [Google Gemini](https://ai.google.dev/) | AI assistance |

</div>

---

<div align="center">

### Built with ❤️ for the big screen experience

**KiduyuTV - Your gateway to premium streaming**

[![Stars](https://img.shields.io/github/stars/kiduyu-klaus/KiduyuTv_final?style=social)](https://github.com/kiduyu-klaus/KiduyuTv_final)
[![Forks](https://img.shields.io/github/forks/kiduyu-klaus/KiduyuTv_final?style=social)](https://github.com/kiduyu-klaus/KiduyuTv_final)
[![Follow](https://img.shields.io/github/followers/kiduyu-klaus?style=social)](https://github.com/kiduyu-klaus)

</div>