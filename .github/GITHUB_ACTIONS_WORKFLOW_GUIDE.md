# GitHub Actions CI/CD Workflow - Windows Desktop Build

This document describes the updated GitHub Actions workflow that now includes Windows Desktop application building.

## Workflow Overview

The `kiduyu_final.yml` workflow automates building and releasing KiduyuTV for three platforms:

1. **Android Phone/Tablet** - phone-release APK
2. **Android TV/Fire TV** - tv-release APK  
3. **Windows 10/11 x64** - EXE and MSI installers

## Workflow Jobs

### 1. prepare_release
**Purpose**: Calculate release version and gather commit info once

**Outputs**:
- `tag` - Generated version tag (v1.2.3)
- `short_sha` - Abbreviated commit hash
- `title` - First line of commit message
- `full_message` - Complete commit message
- `build_count` - GitHub run number

**Strategy**: Runs on Ubuntu (minimal resources needed)

---

### 2. build_apk (Matrix Strategy)
**Purpose**: Build Android APKs for phone and TV flavors in parallel

**Matrix**: 
- phone flavor
- tv flavor

**Key Steps**:
1. Checkout code with shallow clone
2. Setup Java 17
3. Setup Gradle with caching
4. Optimize Gradle for 16GB runner
5. Bump version in app/build.gradle
6. Restore keystore from secrets
7. Build release APK
8. Rename APK with version and build number
9. Upload artifact

**Secrets Used**:
- `KEYSTORE_BASE64` - Encoded signing keystore
- `SUBDL_API_KEY` - Subtitle download API key
- `STREAM_API_TOKEN` - Provider stream API token

**Runs on**: ubuntu-latest (16GB RAM)

---

### 3. build_desktop (NEW - Windows)
**Purpose**: Build Windows EXE and MSI installers with SQLite and logging

**Key Steps**:

#### 1. Environment Setup
- Checkout code
- Setup Java 17 (Windows)
- Setup Gradle with caching
- Optimize Gradle for 16GB Windows runner

#### 2. Logging Configuration
Creates `logback.xml` in `desktopApp/src/main/resources/`:
- Console appender for development
- File appender with rolling policy (10MB per file, 30 day retention)
- Separate loggers for different packages
- Log file location: `%LOCALAPPDATA%\KiduyuTV\logs\`

#### 3. Database Configuration
Creates SQLite initialization script with:
- watch_progress table schema
- Columns: tmdb_id, media_type, title, poster_path, backdrop_path, season, episode, position_ms, duration_ms, updated_at
- Primary key: (tmdb_id, media_type, season, episode)
- Indexes on: updated_at, media_type, season+episode

#### 4. Gradle Optimization for Windows
- JVM memory: 10GB (tuned for 16GB runner)
- Parallel workers: 4
- Configuration cache enabled
- Kotlin incremental compilation enabled

#### 5. Build Steps
- Build Release EXE: `./gradlew :desktopApp:packageReleaseExe`
- Build Release MSI: `./gradlew :desktopApp:packageReleaseMsi`
- Environment variables passed:
  - `KIDUYUTV_STREAM_API_TOKEN` - From secrets
  - `KIDUYUTV_TMDB_TOKEN` - From secrets (optional, can use settings)

#### 6. Artifact Packaging
- Locates EXE and MSI in `desktopApp/build/compose/binaries/main-release/`
- Renames with pattern: `KiduyuTV-setup-{version}-build{build_number}.{exe|msi}`
- Uploads to artifact storage

**Secrets Used**:
- `STREAM_API_TOKEN` - Provider stream API token
- `TMDB_BEARER_TOKEN` - TMDB API bearer token (optional)

**Runs on**: windows-latest (16GB RAM, Windows native)

---

### 4. create_release
**Purpose**: Consolidate all artifacts and create GitHub Release

**Dependencies**: Waits for all builds (build_apk, build_desktop)

**Steps**:
1. Download phone-release APK artifacts
2. Download tv-release APK artifacts
3. Download windows-release artifacts (EXE + MSI)
4. Create GitHub Release with:
   - Release notes with download instructions
   - All artifacts attached
   - Three-platform download table
   - ADB/installer instructions per platform

**Release Body Includes**:
- System requirements for each platform
- Download links for all formats
- Installation instructions
- Sideload commands for Android
- Database and storage paths for Windows

**Runs on**: ubuntu-latest

---

### 5. update_changelog
**Purpose**: Update index.html with latest release info and deploy to GitHub Pages

**Key Functions**:
- Fetches releases from GitHub API
- Generates HTML from markdown release notes
- Updates #changelog section in index.html
- Extracts latest APK and Windows download URLs
- Patches index.html anchor tags with new URLs
- Deploys updated site to GitHub Pages

**Download Link Updates**:
- `#download-tv` - TV APK
- `#download-mobile` - Phone APK
- `#download-windows-exe` - Windows EXE
- `#download-windows-msi` - Windows MSI

**Runs on**: ubuntu-latest

---

### 6. cleanup_failed_runs
**Purpose**: Automatically delete failed workflow runs to keep repo clean

**Strategy**:
- Runs after release creation (regardless of success/failure)
- Fetches all failed runs
- Skips current run
- Deletes old failed runs

**Runs on**: ubuntu-latest

---

## Environment Variables & Secrets

### Required Secrets (GitHub Settings > Secrets)

```
KEYSTORE_BASE64         - Base64-encoded Android signing keystore (.keystore)
SUBDL_API_KEY           - Subtitle downloader API key
STREAM_API_TOKEN        - KiduyuTV provider stream API token
TMDB_BEARER_TOKEN       - TMDB API bearer token (for desktop)
GITHUB_TOKEN            - Auto-provided by GitHub Actions
```

### Environment Variables (Set in Workflow)

**Desktop Build**:
```
KIDUYUTV_STREAM_API_TOKEN  - Passed from secrets.STREAM_API_TOKEN
KIDUYUTV_TMDB_TOKEN        - Passed from secrets.TMDB_BEARER_TOKEN (optional)
```

### Runtime Configuration (Windows Desktop)

**User Settings** (Windows Registry: `HKCU\Software\JavaSoft\Prefs\com\kiduyutv\desktop`):
```
tmdb_bearer_token        - TMDB bearer token
stream_api_token         - Stream provider API token
providers_base_url       - Stream providers API base URL
direct_stream_enabled    - Enable/disable direct streaming
default_provider         - Default provider for streams
mpv_path                 - Path to mpv.exe executable
```

---

## Gradle Optimization Strategies

### Ubuntu Runner (16GB RAM)
```gradle
org.gradle.jvmargs=-Xmx10g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.workers.max=4
org.gradle.caching=true
org.gradle.configuration-cache=true
```

### Windows Runner (16GB RAM)
```gradle
# Same as Ubuntu, adapted for Windows paths and processes
```

**Rationale**:
- Ephemeral runners: daemon disabled (no warmup benefit)
- Large heap: 10GB JVM (2GB headroom for OS)
- Parallel: 4 workers (optimal for CI resources)
- Caching: enabled for reuse across builds
- Configuration cache: speeds up project loading

---

## Database Setup

The `DatabaseWatchHistoryStore` automatically initializes on first run:

### Schema (Auto-created)
```sql
CREATE TABLE watch_progress (
    tmdb_id INTEGER NOT NULL,
    media_type TEXT NOT NULL,
    title TEXT NOT NULL,
    poster_path TEXT,
    backdrop_path TEXT,
    season INTEGER DEFAULT -1,
    episode INTEGER DEFAULT -1,
    position_ms INTEGER NOT NULL DEFAULT 0,
    duration_ms INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (tmdb_id, media_type, season, episode)
);

CREATE INDEX idx_updated_at ON watch_progress(updated_at DESC);
CREATE INDEX idx_media_type ON watch_progress(media_type);
CREATE INDEX idx_season_episode ON watch_progress(season, episode);
```

### Storage Location (Windows)
```
%LOCALAPPDATA%\KiduyuTV\history.db
```

### Access
The `DatabaseWatchHistoryStore` class handles all database operations with proper error handling and fallbacks.

---

## Logging Configuration

### Logback Setup (Auto-created during build)

**Location**: `desktopApp/src/main/resources/logback.xml`

**Appenders**:
1. **Console** - Development/debugging
   - Pattern: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`

2. **File** - Persistent logs
   - Location: `%LOCALAPPDATA%\KiduyuTV\logs\kiduyutv.log`
   - Rolling policy: Size (10MB) + Time (daily)
   - Retention: 30 days / 500MB total
   - Format: Same as console

**Logger Configuration**:
```xml
<logger name="com.kiduyuk.klausk.kiduyutv.desktop" level="DEBUG" />
<logger name="okhttp3" level="INFO" />
<logger name="com.google.gson" level="WARN" />
<root level="INFO" />
```

**Log Output**:
- Development: Console + File
- Production: File only
- File rotation prevents unbounded growth

---

## Build Artifacts

### Android Artifacts
- **Location**: `app/build/outputs/apk/{flavor}/release/`
- **Naming**: `KiduyuTV-{flavor}-release-{version}-build{number}.apk`
- **Flavors**: phone, tv

### Windows Artifacts
- **Location**: `desktopApp/build/compose/binaries/main-release/`
- **EXE**: `KiduyuTV-setup-{version}-build{number}.exe`
- **MSI**: `KiduyuTV-setup-{version}-build{number}.msi`

### Artifact Storage
GitHub Artifacts API (temporary, 90-day retention):
- phone-release
- tv-release
- windows-release

### GitHub Release Assets
Permanent storage on GitHub:
- All three formats available for download
- Direct browser download links
- ADB/installer command examples

---

## Workflow Triggers

**Current Trigger**: Push to `main` branch

```yaml
on:
  push:
    branches:
      - main
```

**When Workflow Runs**:
1. Any commit pushed to `main`
2. All 6 jobs execute in sequence/parallel
3. Release created on GitHub
4. Website updated and deployed

**To Skip Release**:
Add `[skip-release]` to commit message:
```bash
git commit -m "Fix typo [skip-release]"
```

(Requires additional setup not shown here)

---

## Permissions & Secrets Configuration

### GitHub Actions Permissions
```yaml
permissions:
  contents: write        # Create releases, push commits
  packages: write        # Publish packages
  pages: write          # Deploy to GitHub Pages
  id-token: write       # OIDC for deployments
  actions: write        # Manage workflow runs
```

### Repository Secrets (GitHub Settings)
Required before first run:

1. **KEYSTORE_BASE64**
   - Encode: `base64 -i keystore.jks > keystore.txt`
   - Paste into: Settings > Secrets > New Repository Secret
   
2. **SUBDL_API_KEY**
   - Get from subtitle API provider
   
3. **STREAM_API_TOKEN**
   - Get from KiduyuTV stream provider
   
4. **TMDB_BEARER_TOKEN**
   - Get from TMDB API settings

---

## Monitoring & Troubleshooting

### View Workflow Runs
1. Go to GitHub repo
2. Click "Actions" tab
3. View latest runs and logs

### Common Issues

**Build Fails**:
- Check secrets are configured correctly
- Verify Java 17 compatibility
- Review gradle.properties syntax

**Windows Build Fails**:
- Ensure mpv dependencies available
- Check STREAM_API_TOKEN is valid
- Verify disk space (300MB+)

**Release Not Created**:
- Check build_apk and build_desktop both succeeded
- Verify create_release has proper permissions
- Check GITHUB_TOKEN is valid

**Pages Deployment Fails**:
- Verify GitHub Pages enabled in repo settings
- Check index.html exists
- Verify publishing source is set to "GitHub Actions"

---

## Maintenance

### Dependencies to Update Regularly

In root `build.gradle`:
- `org.jetbrains.kotlin:kotlin-gradle-plugin` - Kotlin version
- `org.jetbrains.compose:compose-gradle-plugin` - Compose Desktop

In `desktopApp/build.gradle`:
- `com.squareup.okhttp3:okhttp` - HTTP client
- `com.google.code.gson:gson` - JSON library
- `org.xerial:sqlite-jdbc` - Database driver
- `ch.qos.logback:logback-classic` - Logging

### Updating Versions

1. Update version in `gradle.properties` or `app/build.gradle`
2. Push to main
3. Workflow auto-generates semantic version tag
4. Release created automatically

---

## Performance Metrics

### Build Times (Approximate)

| Step | Duration |
|------|----------|
| Checkout | 10s |
| Setup Java/Gradle | 15s |
| Android Phone APK | 3-5 min |
| Android TV APK | 3-5 min |
| Windows EXE | 5-8 min |
| Windows MSI | 2-3 min |
| Create Release | 30s |
| Deploy Pages | 30s |
| **Total** | **15-25 min** |

**Parallelization**: Phone + TV + Windows run in parallel, reducing total time

---

## Future Enhancements

Possible workflow additions:
1. Code signing for Windows EXE/MSI
2. Notarization for macOS
3. Automated testing before release
4. APK size reporting
5. Performance benchmarking
6. Changelog generation from commit history
7. Slack/Discord notifications on release

---

**Last Updated**: 2026-09-01
**Workflow File**: `.github/workflows/kiduyu_final.yml`
**Status**: Production Ready with Windows Desktop Support
