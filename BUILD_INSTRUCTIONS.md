# Build Instructions

## Prerequisites

1. Android Studio Hedgehog (2024.1.1) or later
2. Android SDK 36
3. Java 17 or later
4. Gradle 8.13 (will be downloaded via wrapper)

## Setup Steps

### 1. Download Gradle Wrapper JAR

Run the setup script or manually download:

```bash
chmod +x setup_gradle.sh
./setup_gradle.sh
```

Or manually:
```bash
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar "https://github.com/gradle/gradle/raw/v8.13/gradle/wrapper/gradle-wrapper.jar"
```

### 2. Configure Android SDK

Update `local.properties` with your Android SDK path:
```
sdk.dir=/Users/yourusername/Library/Android/sdk
```

### 3. Open in Android Studio

1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the `KiduyuTv` folder
4. Wait for Gradle sync to complete

### 4. Build the Project

**Using Android Studio:**
- Go to Build > Make Project (Ctrl+F9)
- Or go to Build > Generate Signed Bundle / APK

**Using Command Line:**
```bash
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure Overview

```
KiduyuTv/
├── app/src/main/java/com/kiduyuk/klausk/kiduyutv/
│   ├── data/                    # Data layer
│   │   ├── api/                 # TMDB API service
│   │   ├── model/               # Data models
│   │   └── repository/          # Repository pattern
│   ├── ui/                      # Presentation layer
│   │   ├── components/          # Reusable Compose components
│   │   ├── navigation/          # Navigation setup
│   │   ├── screens/             # Screen composables
│   │   │   ├── detail/          # Detail screens
│   │   │   └── home/            # Home screens
│   │   └── theme/               # App theming
│   └── viewmodel/               # ViewModels
└── app/src/main/res/             # Android resources
```

## Key Features

- **D-Pad Navigation**: Full TV remote support with focus states
- **Hero Section**: Dynamic backdrop with featured content
- **Content Rows**: Horizontal scrolling with LazyRow
- **TMDB Integration**: Real movie/TV show data
- **Dark Theme**: Netflix-style dark mode

## Troubleshooting

### Gradle Sync Failed
- Ensure you have Java 17+ installed
- Verify Android SDK is installed and configured
- Try Invalidating Caches: File > Invalidate Caches > Invalidate and Restart

### API Errors
- Check your internet connection
- Verify TMDB API token is valid
- Check Logcat for detailed error messages

### Build Errors
- Clean project: Build > Clean Project
- Rebuild: Build > Rebuild Project
- Check Gradle sync status in the status bar
