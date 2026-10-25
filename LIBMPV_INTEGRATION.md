# Adding and using libmpv in KiduyuTV

## Scope

KiduyuTV currently plays native direct streams through `PlayerEngine`, a wrapper around AndroidX Media3 `ExoPlayer` at:

`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/player/directstream/playback/PlayerEngine.kt`

The Playback settings now persist a player choice using `SettingsManager`:

- `exoplayer` — the existing Media3 implementation.
- `libmpv` — the selected backend identifier for the future native libmpv adapter.

Selecting `libmpv` currently stores the preference and exposes it to the settings state; it does not silently pretend that libmpv is already linked into the APK. The native library, JNI bridge, rendering view, and player adapter must be added before routing playback to it.

## Important upstream constraint

The official [`mpv-android`](https://github.com/mpv-android/mpv-android) repository says that it is **not a library/module or AAR that can be imported into another app**. It provides the Kotlin wrapper, Android view, JNI code, and native build scripts as reference code. The relevant upstream files are:

- [`MPVLib.kt`](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVLib.kt) — JNI declarations, commands, properties, observers, and event constants.
- [`BaseMPVView.kt`](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt) — `SurfaceView` lifecycle and attaching/detaching the libmpv surface.
- [`app/src/main/jni`](https://github.com/mpv-android/mpv-android/tree/master/app/src/main/jni) — native bridge code.
- [`buildscripts`](https://github.com/mpv-android/mpv-android/tree/master/buildscripts) — native FFmpeg/mpv/Android build pipeline.

The upstream build documentation also states that native compilation is separate from Gradle, is supported on Linux and macOS, and that Windows/WSL is not supported by those scripts.

## Recommended architecture

Do not place libmpv calls directly throughout `DirectStreamActivity`. Introduce an engine abstraction so stream resolution, watch-history synchronization, controls, and the UI do not depend on one playback implementation.

```kotlin
interface DirectStreamPlayer {
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun play(stream: StreamItem, startPositionMs: Long = 0L, subtitles: List<SubtitleItem> = emptyList())
    fun pause()
    fun resume()
    fun seekBy(deltaMs: Long)
    fun release()
}
```

Then move the existing `PlayerEngine` behavior behind `ExoPlayerDirectStreamPlayer` and create a separate `LibMpvDirectStreamPlayer`. A factory can select the implementation from `SettingsManager.getDirectStreamPlayer()`:

```kotlin
when (SettingsManager(context).getDirectStreamPlayer()) {
    SettingsManager.PLAYER_LIBMPV -> LibMpvDirectStreamPlayer(...)
    else -> ExoPlayerDirectStreamPlayer(...)
}
```

The factory should have a safe fallback to ExoPlayer if the libmpv native library is unavailable for the device ABI or if initialization fails.

## Native build and packaging

1. Pin an upstream mpv/libmpv revision and the Android NDK version. Do not use an unpinned floating native build for production.
2. Build FFmpeg, mpv, and the Android JNI bridge for every ABI that the app ships. At minimum, most current phones and TVs need `arm64-v8a`; add `armeabi-v7a`, `x86`, and `x86_64` only if the app supports those devices.
3. Package the resulting `.so` files under `app/src/main/jniLibs/<abi>/`, or build them from a dedicated native Gradle/CMake module.
4. Export the libmpv headers needed by the JNI bridge and keep the native C/C++ wrapper in the app source tree or a versioned internal module.
5. Load the native libraries once, before creating the mpv context. The upstream wrapper loads `mpv` and its Android/player bridge with `System.loadLibrary(...)`.
6. Verify licensing obligations for mpv, FFmpeg, libass, and every bundled native dependency before distributing the APK. Preserve license and notice files in the application’s third-party notices.

The upstream scripts are useful as a reference, but they should not be copied blindly into the KiduyuTV Gradle build. They are a full application build pipeline rather than a small Maven dependency.

## Kotlin/JNI bridge responsibilities

The bridge needs at least these operations, following the upstream `MPVLib` shape:

```kotlin
external fun create(context: Context)
external fun init()
external fun destroy()
external fun attachSurface(surface: Surface)
external fun detachSurface()
external fun command(command: Array<out String>)
external fun setPropertyString(name: String, value: String)
external fun setPropertyDouble(name: String, value: Double)
external fun setPropertyBoolean(name: String, value: Boolean)
external fun getPropertyDouble(name: String): Double?
external fun observeProperty(name: String, format: Int)
```

Native code must deliver mpv events back to Kotlin on a controlled thread. Important events include file loaded, playback restart, end file, shutdown, property change, and log messages. UI state updates must be marshalled onto the Android main thread.

## Rendering on Android

Use a dedicated `SurfaceView` or `TextureView` owned by the player activity. The upstream `BaseMPVView` uses a `SurfaceView` and:

1. Creates and initializes mpv before the view is shown.
2. Registers a `SurfaceHolder.Callback`.
3. Calls `attachSurface(holder.surface)` from `surfaceCreated`.
4. Updates `android-surface-size` from `surfaceChanged`.
5. Sets the video output to `null`, disables the force window, and calls `detachSurface()` from `surfaceDestroyed`.
6. Removes callbacks before destroying the mpv context.

A `SurfaceView` is the safer first implementation for Android TV and Fire TV because it avoids copying video frames through the CPU. The libmpv render API can provide more flexible Compose overlays, but it requires an OpenGL/EGL rendering integration and careful synchronization with the render callback.

## Creating and configuring the mpv context

Set options before calling `init()`. A practical first configuration is:

```kotlin
MPVLib.setOptionString("vo", "gpu")
MPVLib.setOptionString("hwdec", "auto-safe")
MPVLib.setOptionString("audio-client-name", "KiduyuTV")
MPVLib.setOptionString("osd-level", "0")
MPVLib.setOptionString("force-window", "no")
MPVLib.setOptionString("idle", "once")
MPVLib.init()
```

The exact hardware-decoding option should be tested per device family. Keep a software-decoding fallback for Fire TV and Android TV devices whose GPU driver reports support incorrectly.

Use an app-private config/cache directory rather than a shared filesystem path. Avoid loading arbitrary user configuration until the security and compatibility implications are understood.

## Loading a direct stream

The libmpv command API accepts separate arguments. The mpv documentation recommends avoiding string-concatenated `loadfile` commands because URL escaping can break. Use separate arguments instead:

```kotlin
MPVLib.command(arrayOf("loadfile", stream.url, "replace"))
```

For a resume position, set or include the start position after the file is loaded:

```kotlin
MPVLib.command(arrayOf("seek", (startPositionMs / 1000.0).toString(), "absolute"))
```

For playback controls:

```kotlin
MPVLib.command(arrayOf("set", "pause", "yes"))
MPVLib.command(arrayOf("set", "pause", "no"))
MPVLib.command(arrayOf("seek", "30", "relative"))
MPVLib.command(arrayOf("seek", "-10", "relative"))
```

Prefer typed property and command APIs in the JNI layer. Do not construct shell commands, and do not pass provider URLs through a shell.

## Headers, cookies, and subtitles

KiduyuTV’s `StreamItem` can contain provider-specific headers, cookies, referer/origin values, and subtitle metadata. These must be translated to mpv options before loading the file, for example:

```kotlin
stream.headers["User-Agent"]?.let {
    MPVLib.setOptionString("user-agent", it)
}
stream.headers["Referer"]?.let {
    MPVLib.setOptionString("http-referrer", it)
}
stream.headers["Cookie"]?.let {
    MPVLib.setOptionString("http-header-fields", "Cookie: $it")
}
```

Do not log cookie values or authorization headers. Header option names and behavior should be verified against the exact mpv version built for Android. If a provider requires multiple custom headers, pass them through the native bridge as a structured list rather than concatenating untrusted values without escaping.

Subtitle support should initially use mpv’s `sub-add` command after the stream is loaded:

```kotlin
MPVLib.command(arrayOf("sub-add", subtitle.url, "select"))
```

Validate subtitle URL schemes and retain the same allow-list used by the ExoPlayer path.

## Observing progress and errors

Observe at least:

- `time-pos` as a double for watch-history position.
- `duration` as a double for total duration.
- `pause` as a boolean.
- `eof-reached` as a boolean.
- `video-params` or a playback-restart event to know when rendering begins.
- `core-idle` and end-file events for terminal playback state.

Convert seconds to milliseconds only after checking for `null`, `NaN`, negative values, and `Double.POSITIVE_INFINITY`. Throttle watch-history writes; do not write to Firebase on every property callback. Preserve the existing signed-in guard before syncing history.

Forward native errors to the same activity callback shape currently used by `PlayerEngine`, including a sanitized URL and an HTTP status when one is available. Native logs should be disabled or redacted in release builds where they could expose provider URLs, cookies, or tokens.

## Activity and lifecycle checklist

- Create one engine per player screen, not one global engine per application.
- Attach the rendering surface in `onStart`/`surfaceCreated` and detach it before the surface is destroyed.
- Pause or stop playback according to the existing activity/PiP/background policy.
- Release observers, coroutines, and the mpv context in `onDestroy`.
- Ensure a second activity instance cannot destroy a context still used by another instance.
- Handle configuration changes or recreate the engine deterministically.
- Test D-pad play/pause, seek, track selection, back navigation, and Fire TV surface recreation.

## Suggested rollout for KiduyuTV

1. Keep ExoPlayer as the default and add telemetry-free local diagnostics for the selected engine.
2. Add the native bridge and a standalone `LibMpvPlayerView` screen that can play one known HTTPS HLS URL.
3. Add headers, cookies, subtitles, seek, progress, and error handling one feature at a time.
4. Add an adapter behind the direct-stream player interface.
5. Route the settings preference to the adapter only after the libmpv path passes phone, Android TV, and Fire TV smoke tests.
6. If libmpv initialization or ABI loading fails, show a short message and fall back to ExoPlayer instead of leaving a black surface.

## References

- [mpv-android README](https://github.com/mpv-android/mpv-android)
- [mpv-android MPVLib.kt](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVLib.kt)
- [mpv-android BaseMPVView.kt](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt)
- [mpv-android native build instructions](https://github.com/mpv-android/mpv-android/blob/master/buildscripts/README.md)
- [Official libmpv examples](https://github.com/mpv-player/mpv-examples/blob/master/libmpv/README.md)
- [mpv manual: using mpv from other programs](https://mpv.io/manual/master/#using-mpv-from-other-programs-or-scripts)
