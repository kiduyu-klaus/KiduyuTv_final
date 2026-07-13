# Converting Zenith-TV into KiduyuTV Lite

## Purpose

This document describes how to turn the existing `Zenith-TV` project into a small, TV-first edition of KiduyuTV without copying the full application's 150-file architecture and dependency set.

The recommended first release is **KiduyuTV Lite for Android TV**. It should keep the fast browse-to-play path already present in Zenith:

1. Browse three TMDB rows.
2. Search movies and TV shows.
3. View movie or TV metadata.
4. Choose a season and episode.
5. Open a configured playback page in a TV-friendly WebView.

It should deliberately omit the full application's phone UI, ads, authentication, Firebase user sync, Trakt, Room database, Live TV, sports schedule, cast galleries, image galleries, trailers, and multiple specialized players. Those features are valuable in the full product, but including them would defeat the purpose of a Lite edition.

This guide is based on the code currently under `Zenith-TV/`. No Gradle task was run during the audit.

---

## 1. Current Zenith-TV implementation

### Project shape

Zenith is a single-module Android application using Kotlin, XML layouts, AppCompat, RecyclerView, view binding, and a WebView. There are only seven Kotlin source files.

| File | Current responsibility | Lite decision |
|---|---|---|
| `MainActivity.kt` | Immediately redirects to `HomeActivity` | Delete it and launch `HomeActivity` directly |
| `HomeActivity.kt` | Loads three TMDB rows, handles D-pad navigation, and shows the search overlay | Keep and harden |
| `DetailActivity.kt` | Displays metadata, loads TV seasons/episodes, and creates the playback URL | Keep and replace the hardcoded Zenith player URL |
| `PlayerActivity.kt` | Hosts the playback webpage and maps remote keys to JavaScript | Keep, but harden WebView security and input behavior |
| `api/TmdbApi.kt` | Makes synchronous TMDB calls with `HttpURLConnection` | Keep the lightweight client, move calls to coroutines, and remove the embedded key |
| `model/Models.kt` | Defines media, season, and episode models | Keep |
| `ui/MediaAdapter.kt` | Displays cards and downloads posters using raw threads | Keep the adapter, replace manual image loading with Coil |

### Current user flow

```text
MainActivity
    -> HomeActivity
       -> TMDB trending / popular movies / popular TV
       -> search overlay
       -> DetailActivity
          -> movie: Play
          -> TV: seasons -> episodes -> Play
          -> PlayerActivity
             -> https://zenith-movies.vercel.app/...
```

### Current strengths

- Small codebase and dependency footprint.
- TV-oriented landscape layouts.
- Clear three-row home experience.
- Manual D-pad behavior is easy to understand.
- Movie and TV episode paths are already separated.
- The WebView supports fullscreen custom video views.
- No ads, analytics, account requirement, or background services.

### Problems to correct before release

1. **The TMDB API key is committed in `TmdbApi.kt`.** Treat the current key as exposed, rotate it, and never place the replacement directly in Kotlin.
2. **`PlayerActivity.onReceivedSslError()` calls `handler.proceed()`.** This accepts invalid certificates and must be changed to `handler.cancel()`.
3. **The playback host is hardcoded** in both `DetailActivity` and the fallback in `PlayerActivity`.
4. **The package and product identity are still Zenith** (`com.texas.tv`, `ZenithTV`, `Zenith TV+`, and hardcoded `ZENITH TV+` layout text).
5. **Network and image calls use unmanaged raw threads.** They can outlive activities, silently swallow errors, and bind an old poster to a recycled card.
6. **Home has no loading, empty, offline, or error state.** A failed request currently only prints a stack trace.
7. **Player center-key handling always toggles video.** It can prevent focused webpage controls from receiving Enter.
8. **JavaScript cannot control video inside most cross-origin iframes.** Skip/play helpers should be treated as best-effort, not guaranteed playback control.
9. **`usesCleartextTraffic="true"` is unnecessary** for the current HTTPS endpoints.
10. **Release shrinking is not configured.** The Lite release should use R8 and resource shrinking.
11. **Several artifacts are unused:** `item_season.xml`, `season_chip_bg.xml`, `detail_scrim.xml`, the empty `styles.xml`, and the unused version-catalog declarations.
12. The README claims the project is open-source, but this folder has no standalone license file. Replace that statement and apply the intended KiduyuTV license before distribution.

---

## 2. Recommended Lite feature boundary

### Include in version 1

- Android TV launcher and banner.
- KiduyuTV Lite branding.
- Trending, popular movie, and popular TV rows.
- Multi-search.
- Movie and TV details.
- TV season and episode selection.
- One configurable playback gateway.
- D-pad navigation and fullscreen playback.
- Clear loading, empty, and retry states.
- Optional Lite-specific update notification.

### Do not port from the full app in version 1

- Phone flavor and mobile navigation.
- Compose navigation and the full screen/component hierarchy.
- Ads and consent SDKs.
- Firebase Authentication, Firestore, user sync, and FCM.
- Trakt authorization and scrobbling.
- Room, My List, and watch history.
- Live TV, IPTV, schedules, and channel scraping.
- YouTube player, cast pages, image galleries, and image slider.
- Multiple streaming-provider selection screens.
- DNS-over-HTTPS and provider availability probing.

### Optional version 1.1 features

- A small SharedPreferences-based My List.
- Resume position stored locally for the configured playback gateway.
- Firebase Realtime Database for a Lite-only playback URL and update notice.
- A simple settings screen containing app version, privacy link, and cache clearing.

Do not add an optional feature until the base browse, search, detail, and playback flow works reliably on a physical TV device.

---

## 3. Product identity and package migration

Use a distinct application ID so the Lite and full TV applications can coexist:

```text
Full TV app:  com.kiduyuk.klausk.kiduyutv.tv
Lite TV app:  com.kiduyuk.klausk.kiduyutv.lite.tv
Lite namespace: com.kiduyuk.klausk.kiduyutv.lite
```

In Android Studio, use **Refactor > Rename Package** rather than only changing the folder name. Rename `com.texas.tv` to `com.kiduyuk.klausk.kiduyutv.lite`, including the `api`, `model`, and `ui` packages. View-binding and `R` imports will then be generated under the new namespace.

Update `settings.gradle.kts`:

```kotlin
rootProject.name = "KiduyuTVLite"
include(":app")
```

Update the start of `app/build.gradle.kts`:

```kotlin
android {
    namespace = "com.kiduyuk.klausk.kiduyutv.lite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kiduyuk.klausk.kiduyutv.lite.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-lite"
    }
}
```

The current Zenith root build uses Android Gradle Plugin 8.2.1 with `compileSdk = 34`. If the Lite app is moved to SDK 35 as shown above, upgrade the root Android Gradle Plugin to a version that supports SDK 35. Otherwise, keep SDK 34 temporarily and upgrade the toolchain as a separate controlled change.

Do not copy the signing block from the full app. Signing passwords and keys must not be committed in a Gradle file. Use a local ignored `keystore.properties` file or CI secrets.

Change `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">KiduyuTV Lite</string>
    <string name="brand_name">KIDUYU</string>
    <string name="brand_suffix">TV LITE</string>
    <string name="search_hint">Search movies &amp; shows…</string>
    <string name="trending_today">Trending Today</string>
    <string name="popular_movies">Popular Movies</string>
    <string name="popular_tv">Popular TV Shows</string>
    <string name="play_now">Play Now</string>
    <string name="episodes">Episodes</string>
    <string name="back">← Back</string>
</resources>
```

Replace every hardcoded user-facing string in the layouts with `@string/...`. In particular, update the two `ZENITH` / `TV+` `TextView` elements in `activity_home.xml`:

```xml
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/brand_name"
    android:textColor="@color/kiduyu_red"
    android:textSize="26sp"
    android:textStyle="bold" />

<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/brand_suffix"
    android:textColor="@color/text_primary"
    android:textSize="20sp"
    android:textStyle="bold" />
```

Create a shared palette instead of repeating blue hex values throughout layouts and drawables:

```xml
<!-- app/src/main/res/values/colors.xml -->
<resources>
    <color name="kiduyu_red">#E50914</color>
    <color name="kiduyu_red_focused">#FF3340</color>
    <color name="background_dark">#141414</color>
    <color name="surface_dark">#1F1F1F</color>
    <color name="card_dark">#2A2A2A</color>
    <color name="text_primary">#FFFFFF</color>
    <color name="text_secondary">#B3B3B3</color>
    <color name="focus_border">#E50914</color>
</resources>
```

At minimum, replace Zenith's `#070D18`, `#1A6EFF`, and `#4A9EFF` values in:

- `activity_home.xml`
- `activity_detail.xml`
- `card_focus_ring.xml`
- `btn_play_bg.xml`
- `back_btn_bg.xml`
- `episode_bg.xml`
- `ep_play_btn.xml`
- `nav_divider.xml`
- `search_bg.xml`
- `season_nav_btn.xml`
- `ic_launcher_background.xml`
- `ic_launcher_foreground.xml`
- `PlayerActivity.errorHtml()` and the injected focus CSS

For example:

```xml
<!-- card_focus_ring.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@android:color/transparent" />
    <stroke android:width="3dp" android:color="@color/focus_border" />
    <corners android:radius="8dp" />
</shape>
```

Copy or redesign KiduyuTV Lite launcher and banner assets. Android TV requires a readable 320x180 banner; a square launcher icon is not a good substitute for `android:banner`.

Rename the theme used by the manifest:

```xml
<!-- app/src/main/res/values/themes.xml -->
<resources>
    <style name="Theme.KiduyuTvLite" parent="Theme.AppCompat.NoActionBar">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowBackground">@color/background_dark</item>
        <item name="android:fontFamily">sans</item>
    </style>
</resources>
```

---

## 4. Simplify and secure the manifest

`MainActivity` only redirects and adds no value. Make `HomeActivity` the launcher and delete `MainActivity.kt` after confirming there are no references to it.

Recommended manifest core:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />

    <application
        android:allowBackup="false"
        android:banner="@drawable/kiduyutv_lite_banner"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.KiduyuTvLite"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".HomeActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".DetailActivity"
            android:exported="false"
            android:screenOrientation="landscape" />

        <activity
            android:name=".PlayerActivity"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|uiMode"
            android:exported="false"
            android:launchMode="singleTop"
            android:screenOrientation="landscape" />
    </application>
</manifest>
```

If phone installation should remain possible, keep Leanback `required="false"`; however, that creates an obligation to build touch-friendly and portrait-aware UI. For a genuinely small first Lite release, TV-only is the cleaner boundary.

---

## 5. Clean up the dependency set

Zenith declares Leanback but does not use Leanback classes. It also declares AndroidX WebKit but only uses platform `android.webkit` APIs. Replace implicit/transitive dependencies with a small explicit set:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil:2.7.0")
}
```

Keep view binding and enable custom BuildConfig values:

```kotlin
android {
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

The existing `gradle/libs.versions.toml` is not used by the build scripts and lists Compose/TV Compose libraries that Zenith does not use. Either migrate the whole build consistently to the catalog or remove the unused catalog file. Do not maintain two conflicting version sources.

---

## 6. Remove committed API and playback configuration

### Local development values

Add these ignored values to `local.properties`:

```properties
TMDB_API_KEY=replace_with_rotated_key
LITE_PLAYER_BASE_URL=https://your-authorized-player.example/
```

Do not commit `local.properties`. Do not reuse the API key currently embedded in Zenith; rotate it first.

At the top of `app/build.gradle.kts`, load the properties:

```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun quotedProperty(name: String): String {
    val value = localProperties.getProperty(name, "")
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
```

Then add fields inside `defaultConfig`:

```kotlin
buildConfigField("String", "TMDB_API_KEY", quotedProperty("TMDB_API_KEY"))
buildConfigField("String", "PLAYER_BASE_URL", quotedProperty("LITE_PLAYER_BASE_URL"))
```

In CI, generate `local.properties` from repository secrets before building. This keeps secrets out of source control, although an API key included in an APK can still be extracted. Apply TMDB-side restrictions and use a backend proxy if stronger protection is required.

### Replace `TmdbApi.API_KEY`

```kotlin
import com.kiduyuk.klausk.kiduyutv.lite.BuildConfig

object TmdbApi {
    private const val BASE = "https://api.themoviedb.org/3"

    private fun requireApiKey(): String = BuildConfig.TMDB_API_KEY
        .takeIf { it.isNotBlank() }
        ?: error("TMDB_API_KEY is not configured")

    private fun get(path: String): JSONObject {
        val separator = if ('?' in path) "&" else "?"
        val url = URL("$BASE$path${separator}api_key=${requireApiKey()}")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("TMDB request failed with HTTP $status")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
```

Add `java.io.IOException` to the imports. Avoid logging response bodies because API error payloads can expose configuration details.

---

## 7. Build playback URLs in one place

Delete all references to `zenith-movies.vercel.app`. Create `playback/LitePlaybackUrlBuilder.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.lite.playback

import android.net.Uri
import com.kiduyuk.klausk.kiduyutv.lite.BuildConfig

object LitePlaybackUrlBuilder {

    fun movie(tmdbId: Int): String? =
        baseBuilder(tmdbId)?.build()?.toString()

    fun episode(tmdbId: Int, season: Int, episode: Int): String? =
        baseBuilder(tmdbId)
            ?.appendQueryParameter("s", season.toString())
            ?.appendQueryParameter("e", episode.toString())
            ?.build()
            ?.toString()

    private fun baseBuilder(tmdbId: Int): Uri.Builder? {
        val baseUrl = BuildConfig.PLAYER_BASE_URL.trim()
        if (!baseUrl.startsWith("https://")) return null

        return Uri.parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("id", tmdbId.toString())
    }
}
```

Use only a playback service that you operate or are authorized to distribute.

Replace `DetailActivity.launchPlayer()`:

```kotlin
private fun launchPlayer() {
    val url = if (mediaType == "movie") {
        LitePlaybackUrlBuilder.movie(mediaId)
    } else {
        val season = seasons.getOrNull(seasonIndex)?.seasonNumber ?: return
        LitePlaybackUrlBuilder.episode(mediaId, season, selectedEpisode)
    } ?: run {
        Toast.makeText(this, "Playback is not configured", Toast.LENGTH_LONG).show()
        return
    }

    startActivity(
        Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, url)
    )
}
```

Add `android.widget.Toast` to `DetailActivity` imports. Returning `null` from the builder prevents a missing or non-HTTPS configuration from crashing the detail screen.

In `PlayerActivity`, remove the test fallback URL. Missing input should close with an error instead of silently playing unrelated content:

```kotlin
companion object {
    const val EXTRA_URL = "playback_url"
}

val url = intent.getStringExtra(EXTRA_URL)
if (url.isNullOrBlank()) {
    Toast.makeText(this, "Playback link is unavailable", Toast.LENGTH_LONG).show()
    finish()
    return
}

binding.playerWebView.loadUrl(url)
```

---

## 8. Make networking lifecycle-aware

Raw `Thread {}` calls should be replaced with `lifecycleScope` and `Dispatchers.IO`. This does not require adopting the full app's repository/ViewModel architecture.

Example replacement for `HomeActivity.loadRow()`:

```kotlin
private fun loadRow(
    recyclerView: RecyclerView,
    fetch: () -> List<MediaItem>
) {
    lifecycleScope.launch {
        setRowLoading(recyclerView, true)

        val result = runCatching {
            withContext(Dispatchers.IO) { fetch() }
        }

        setRowLoading(recyclerView, false)
        result.onSuccess { items ->
            if (items.isEmpty()) {
                showRowMessage(recyclerView, "No titles available")
            } else {
                bindRow(recyclerView, items)
            }
        }.onFailure {
            showRowMessage(recyclerView, "Unable to load. Select to retry.")
        }
    }
}
```

`setRowLoading()` and `showRowMessage()` in this example are new helpers. Back them with per-row loading and message views added to `activity_home.xml`; the original Zenith layout does not contain those views.

Apply the same pattern to:

- Home rows.
- Search.
- TV seasons.
- Episodes.
- Any remaining manual image fetch.

Keep references to retry actions rather than only displaying an error. A TV user must be able to recover without restarting the app.

### Replace manual poster downloads

Using raw threads in `MediaAdapter` can display the wrong bitmap after RecyclerView recycles a holder. Coil handles cancellation and recycling:

```kotlin
import coil.load

override fun onBindViewHolder(holder: CardVH, position: Int) {
    val item = items[position]
    holder.title.text = item.title

    holder.poster.load(item.posterUrl.takeIf { it.isNotBlank() }) {
        placeholder(R.drawable.placeholder)
        error(R.drawable.placeholder)
        crossfade(true)
    }

    // Keep the existing badge, click, Enter, and focus animation logic.
}
```

After this change, remove `Bitmap`, `BitmapFactory`, `RoundedBitmapDrawableFactory`, `URL`, and the adapter's `Handler` from `MediaAdapter.kt`.

Use the same `ImageView.load()` approach in `DetailActivity.bindMetadata()` for poster and backdrop images.

---

## 9. Harden the WebView player

### Never bypass certificate failures

Replace the existing SSL callback:

```kotlin
override fun onReceivedSslError(
    view: WebView,
    handler: SslErrorHandler,
    error: SslError
) {
    handler.cancel()
    view.loadDataWithBaseURL(
        null,
        errorHtml("A secure connection could not be established."),
        "text/html",
        "UTF-8",
        null
    )
}
```

Do not add a debug or production branch that calls `proceed()`.

### Restrict top-level navigation

Only allow HTTPS and hosts that belong to the configured playback service:

```kotlin
private fun isAllowedPlaybackUri(uri: Uri): Boolean {
    val configured = Uri.parse(BuildConfig.PLAYER_BASE_URL)
    return uri.scheme == "https" && uri.host == configured.host
}

override fun shouldOverrideUrlLoading(
    view: WebView,
    request: WebResourceRequest
): Boolean {
    return if (isAllowedPlaybackUri(request.url)) {
        false
    } else {
        true // Block unexpected top-level navigation and external ad pages.
    }
}
```

If the authorized player legitimately redirects across multiple owned domains, use a small explicit host allowlist. Never accept every host merely to make a broken provider appear to work.

### Tighten settings

```kotlin
wv.settings.apply {
    javaScriptEnabled = true              // Required by the playback page.
    domStorageEnabled = true
    mediaPlaybackRequiresUserGesture = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    allowFileAccess = false
    allowContentAccess = false
    setGeolocationEnabled(false)
    builtInZoomControls = false
    displayZoomControls = false
    userAgentString = WebSettings.getDefaultUserAgent(this@PlayerActivity) +
        " KiduyuTVLite/${BuildConfig.VERSION_NAME}"
}

CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
```

Only enable third-party cookies if the authorized playback service proves they are required and that requirement is documented in the privacy policy.

### Do not steal Enter from webpage controls

The current `dispatchKeyEvent()` toggles play/pause for every center-key press. First ask JavaScript whether an interactive control is focused:

```kotlin
private fun handleCenterKey() {
    val js = """
        (function() {
          const el = document.activeElement;
          if (el && el !== document.body && typeof el.click === 'function') {
            el.click();
            return 'clicked';
          }
          const video = document.querySelector('video');
          if (!video) return 'no-video';
          video.paused ? video.play() : video.pause();
          return 'toggled';
        })();
    """.trimIndent()
    binding.playerWebView.evaluateJavascript(js, null)
}
```

Call `handleCenterKey()` only on `ACTION_UP` to avoid duplicate actions from key repeat.

The existing skip code can remain as a best-effort enhancement, but cross-origin iframe security means it will not work for every player. The playback page should expose an explicit JavaScript API if reliable skip controls are required.

### Clean up safely

```kotlin
override fun onDestroy() {
    stopSkipRamp()
    uiHandler.removeCallbacksAndMessages(null)

    binding.playerWebView.apply {
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        (parent as? ViewGroup)?.removeView(this)
        removeAllViews()
        destroy()
    }
    super.onDestroy()
}
```

---

## 10. Improve TV focus and state handling

Zenith manually tracks `currentRow` and each row position. This is acceptable for three fixed rows, but account for slow, failed, or empty rows.

Required changes:

- Do not focus row 0 until it has at least one item.
- When moving vertically, find the next non-empty row.
- Preserve the nearest valid column instead of always forcing column 0.
- Disable search result focus until results are attached.
- Put focus on the close button when a search has no results.
- Restore the previous card and scroll position after returning from details.
- Save `currentRow`, each row position, search query, and overlay state in `onSaveInstanceState()`.
- Remove delayed focus callbacks in `onDestroy()` with `mainHandler.removeCallbacksAndMessages(null)`.

Example safe focus guard:

```kotlin
private fun focusCard(recyclerView: RecyclerView, requestedPosition: Int) {
    val count = recyclerView.adapter?.itemCount ?: 0
    if (count == 0) return

    val position = requestedPosition.coerceIn(0, count - 1)
    recyclerView.scrollToPosition(position)
    recyclerView.post {
        recyclerView.findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.requestFocus()
    }
}
```

Prefer RecyclerView's `post {}` after layout over fixed 60/80/200 ms delays whenever possible.

---

## 11. Optional Firebase configuration without importing the full app

Firebase is not required for the first Lite build. A BuildConfig playback URL produces the smallest and most predictable application.

If remote control is required, register the **Lite application ID** in the existing Firebase project and download a new `google-services.json`. Do not blindly copy the full app's file because Firebase Android apps are registered by package name.

Add only the required plugins/dependencies:

```kotlin
plugins {
    id("com.google.gms.google-services")
}

dependencies {
    implementation("com.google.firebase:firebase-database:22.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
```

Use a Lite-specific node so the full and Lite release channels cannot install each other's APKs:

```json
{
  "app_config": {
    "lite": {
      "player_base_url": "https://your-authorized-player.example/",
      "app_update": {
        "enabled": false,
        "version": "1.0.1-lite",
        "update_title": "KiduyuTV Lite update",
        "message": "A Lite update is available.",
        "download_link_tv": "https://example.com/kiduyutv-lite-tv.apk"
      }
    }
  }
}
```

Recommended database paths:

```text
app_config/lite/player_base_url
app_config/lite/app_update
```

Do not reuse `app_config/app_update/download_link_tv` from the full app. That link is intended for the full TV package and could replace or conflict with the wrong edition.

For a minimal updater, open the Lite URL in the browser rather than adding APK download, FileProvider, storage, and package-install permissions. Add an in-app installer only if browser installation is not acceptable.

---

## 12. Optional local My List

If users need one retained feature from the full app, add My List without Room or Firebase. Store only TMDB ID and media type in SharedPreferences, then refresh metadata from TMDB.

```kotlin
class LiteMyListStore(context: Context) {
    private val preferences = context.getSharedPreferences("lite_my_list", Context.MODE_PRIVATE)

    fun keys(): Set<String> =
        preferences.getStringSet("items", emptySet()).orEmpty()

    fun contains(id: Int, mediaType: String): Boolean =
        "$mediaType:$id" in keys()

    fun toggle(id: Int, mediaType: String): Boolean {
        val key = "$mediaType:$id"
        val updated = keys().toMutableSet()
        val added = if (key in updated) {
            updated.remove(key)
            false
        } else {
            updated.add(key)
            true
        }
        preferences.edit().putStringSet("items", updated).apply()
        return added
    }
}
```

This is suitable for a small local list. If metadata, ordering, progress, multiple profiles, or sync are required, stop and use a real database rather than expanding the string-set format.

---

## 13. Remove dead code and resources

After the functional migration, remove only resources confirmed to have no references:

- `MainActivity.kt` after `HomeActivity` becomes the launcher.
- `item_season.xml` if the existing arrow-based season selector remains.
- `season_chip_bg.xml`, which is only associated with the unused season item.
- `detail_scrim.xml` if `detail_full_scrim.xml` remains the only detail overlay.
- Empty `styles.xml`.
- Unused `gradle/libs.versions.toml`, unless the project is migrated to the catalog.
- `home.png` and `search.png` from release packaging; keep them only as README documentation assets.
- Leanback and AndroidX WebKit dependencies if no code uses them.

Before deleting any resource, search both Kotlin and XML:

```powershell
rg -n "item_season|season_chip_bg|detail_scrim" app/src/main
```

---

## 14. Recommended implementation order

### Phase 1: Identity and coexistence

1. Rename package/namespace.
2. Set the Lite application ID.
3. Rename the project and app.
4. Replace launcher and TV banner assets.
5. Apply KiduyuTV colors and extract strings.
6. Make `HomeActivity` the launcher.

**Exit condition:** Lite and full TV apps can be installed side by side and are visibly distinct in the Android TV launcher.

### Phase 2: Configuration and networking

1. Rotate the exposed TMDB key.
2. Add BuildConfig-backed local/CI values.
3. Centralize playback URL construction.
4. Replace raw activity threads with lifecycle coroutines.
5. Replace manual image loading with Coil.
6. Add loading, retry, empty, and offline states.

**Exit condition:** No API key or player host is hardcoded in Kotlin, and every network failure has a visible recovery path.

### Phase 3: Player safety and TV behavior

1. Cancel SSL errors.
2. Disable mixed content, file access, content access, and geolocation.
3. Allowlist top-level playback navigation.
4. Correct Enter handling.
5. Verify fullscreen entry/exit and WebView cleanup.
6. Test D-pad focus across empty and partially loaded rows.

**Exit condition:** Playback works with the authorized host without bypassing TLS or opening arbitrary top-level pages.

### Phase 4: Size and release preparation

1. Remove dead files and unused dependencies.
2. Enable R8 and resource shrinking.
3. Add Lite-specific signing through local/CI secrets.
4. Replace the README and apply the intended license.
5. Add privacy/support links.
6. Optionally add the Lite Firebase update channel.

**Exit condition:** Release output is independently signed, shrink-enabled, documented, and cannot install the full edition's update APK.

---

## 15. Verification checklist

### Product and installation

- [ ] Package is `com.kiduyuk.klausk.kiduyutv.lite.tv`.
- [ ] Full and Lite editions install simultaneously.
- [ ] Launcher name is `KiduyuTV Lite`.
- [ ] TV banner is legible at launcher distance.
- [ ] No Zenith text, package, URL, icon, or color remains.

### Home and search

- [ ] Trending, movies, and TV rows show loading states.
- [ ] One failed row does not block the others.
- [ ] Empty rows are skipped by vertical D-pad navigation.
- [ ] Search handles success, no results, timeout, and offline states.
- [ ] Back closes search before exiting the home screen.
- [ ] Returning from details restores the previous card.

### Detail and playback

- [ ] Movie URLs contain the expected TMDB ID.
- [ ] TV URLs contain the expected TMDB ID, season, and episode.
- [ ] Missing playback configuration shows an error and does not load a test title.
- [ ] Invalid TLS certificates are rejected.
- [ ] HTTP and mixed-content playback are rejected.
- [ ] Unexpected top-level hosts are blocked.
- [ ] Enter activates focused controls or toggles the page's direct video.
- [ ] Left/right key-up stops skip repetition.
- [ ] Back exits fullscreen first, WebView history second, and the activity last.

### Lifecycle and resource use

- [ ] Rotating/recreating an activity does not update a destroyed view.
- [ ] RecyclerView cards do not briefly show another title's poster.
- [ ] WebView and Handler callbacks are cleaned up.
- [ ] Repeated browse/detail/player cycles do not steadily increase memory.
- [ ] Release shrinking does not remove view-binding or launcher resources.

### Configuration and release

- [ ] The old committed TMDB key has been rotated.
- [ ] No secret, signing password, or private key is committed.
- [ ] CI supplies required BuildConfig values.
- [ ] Lite Firebase registration uses the Lite package ID.
- [ ] Lite updates use a Lite-only database path and APK.
- [ ] README and license match KiduyuTV's actual distribution policy.

Suggested commands for the implementer after changes are complete:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

These commands are listed for the future implementation/verification stage; they were not run during this audit.

---

## 16. Definition of done

KiduyuTV Lite is complete when it is a separately installable, KiduyuTV-branded Android TV application that performs the browse/search/detail/play flow with the same content identifiers as the full app, uses only an authorized configurable playback host, exposes no committed credentials, rejects insecure WebView connections, handles network failures visibly, and remains materially smaller and simpler than the full application.

The best Lite app is not the full app with screens hidden. It is this small Zenith architecture with KiduyuTV identity, shared content conventions, secure configuration, and a carefully limited feature contract.
