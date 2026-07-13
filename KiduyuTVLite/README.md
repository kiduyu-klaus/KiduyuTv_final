# KiduyuTV Lite

KiduyuTV Lite is a small, TV-first Android application derived from the Zenith-TV layout architecture and rebuilt under the KiduyuTV identity. It intentionally focuses on the shortest useful flow: browse, search, inspect a title, select an episode, and open an authorized playback page.

The audited migration plan is preserved in [`docs/IMPLEMENTATION_GUIDE.md`](docs/IMPLEMENTATION_GUIDE.md).

## Included

- Android TV and Leanback launcher entry.
- KiduyuTV Lite package, branding, theme, launcher icon, and TV banner.
- TMDB trending, popular movie, and popular TV rows.
- Movie/TV multi-search.
- Movie details and TV season/episode selection.
- Configurable HTTPS playback URL.
- D-pad navigation, fullscreen WebView playback, and best-effort skip controls.
- Loading, empty, failure, and retry states.
- Lifecycle-aware coroutines and RecyclerView-safe Coil image loading.
- R8/resource shrinking for release builds.

## Intentionally excluded

The Lite edition does not include phone UI, ads, consent SDKs, authentication, Firebase user sync, Trakt, Room, Live TV, schedules, image/cast galleries, trailers, or the full application's multi-provider selection interface.

## Application identity

```text
Namespace:      com.kiduyuk.klausk.kiduyutv.lite
Application ID: com.kiduyuk.klausk.kiduyutv.lite.tv
App name:       KiduyuTV Lite
```

The application ID is different from the full TV package, so both editions can be installed on one device.

## Local setup

1. Copy `local.properties.example` to `local.properties`.
2. Keep the `sdk.dir` path created by Android Studio for your machine.
3. Add a rotated and restricted TMDB v3 API key or Read Access Token.
4. Add an HTTPS playback page that you operate or are authorized to use.

```properties
TMDB_API_KEY=your_rotated_key_or_read_access_token
LITE_PLAYER_BASE_URL=https://your-authorized-player.example/
```

The player URL builder appends these query parameters:

```text
Movie:   ?id={tmdbId}
Episode: ?id={tmdbId}&s={season}&e={episode}
```

The client detects TMDB Read Access Tokens and sends them in the `Authorization` header; shorter v3 keys use the `api_key` query parameter. `local.properties` is ignored by Git. A credential compiled into an APK can still be extracted, so use provider-side restrictions or a backend proxy when stronger protection is needed.
For CI, generate the ignored `local.properties` from repository secrets before the build, as described in the migration guide.

## Source layout

```text
app/src/main/java/com/kiduyuk/klausk/kiduyutv/lite/
├── HomeActivity.kt
├── DetailActivity.kt
├── PlayerActivity.kt
├── api/TmdbApi.kt
├── model/Models.kt
├── playback/LitePlaybackUrlBuilder.kt
└── ui/MediaAdapter.kt
```

## Security behavior

- Only HTTPS playback configuration is accepted.
- Invalid TLS certificates are rejected.
- Cleartext and mixed content are disabled.
- File/content access and geolocation are disabled in WebView.
- Unexpected top-level WebView hosts are blocked.
- The TMDB key and playback URL are not committed in Kotlin source.

## Optional Firebase work

Firebase is deliberately not included in the base Lite build. Before adding remote updates or configuration, register `com.kiduyuk.klausk.kiduyutv.lite.tv` as a separate Android app in Firebase and use Lite-specific database paths and APK links. Do not reuse the full app update APK.

## Verification commands

Run these only after `local.properties` has been configured:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

No Gradle task was run while this project was generated.

The source-only verification covered XML parsing, duplicate layout IDs, Kotlin/XML resource references, legacy Zenith identifiers, hardcoded credential patterns, the 320x180 TV banner, and ignored local signing/configuration files. Installation, device focus behavior, WebView compatibility with the authorized player, shrinking, lint, and APK signing still require the Gradle/device verification stage above.

## Release signing

Release signing is intentionally not hardcoded. `app/build.gradle.kts` already reads the optional values described by `keystore.properties.example`; provide them locally or through CI without committing the populated file or keystore.

## License

See [LICENSE](LICENSE). The KiduyuTV code and documentation in this folder follow the owner's stated proprietary distribution terms.
