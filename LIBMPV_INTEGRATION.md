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

The native build is separate from the Android/Gradle build. The commands below follow the layout of the official `mpv-android/buildscripts` repository. Run them on Linux or macOS; the upstream documentation says that its scripts do not support Windows or WSL.

### 1. Pin the source and toolchain

Use a pinned checkout for reproducible release builds. Do not build production APKs from an unpinned `master` checkout.

```bash
git clone https://github.com/mpv-android/mpv-android.git
cd mpv-android

# Pin this to the revision approved for KiduyuTV.
git checkout <mpv-android-commit-or-tag>

cd buildscripts
./download.sh
```

`download.sh` installs or downloads the SDK/NDK and native source dependencies expected by the upstream scripts. If the build machine already has an Android SDK, the upstream instructions allow `android-sdk-linux` to be symlinked to that SDK root.

Record the versions used by the build:

```bash
git rev-parse HEAD > kiduyutv-mpv-android-revision.txt

# Use the SDK/NDK selected by the buildscripts checkout.
printf 'ANDROID_HOME=%s\\n' "$ANDROID_HOME"
"${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/<pinned-ndk>/ndk-build" --version || true
```

Replace `<pinned-ndk>` with the actual NDK directory selected by the checked-out build scripts. Do not invent a different NDK version in Gradle after the native libraries have been built.

### 2. Map Android ABIs to the upstream build architectures

| Android ABI | Upstream `buildall.sh --arch` value | Typical device use |
| --- | --- | --- |
| `arm64-v8a` | `arm64` | Current phones, Android TV, Fire TV; recommended minimum |
| `armeabi-v7a` | `armv7l` | Older 32-bit ARM phones and TVs |
| `x86` | `x86` | Older Intel emulators and a small number of devices |
| `x86_64` | `x86_64` | Modern Intel/Google emulator images |

The APK should contain exactly the ABIs that the app declares in Gradle. Building `arm64-v8a` does not make the library available to an `armeabi-v7a` device.

### 3. Build FFmpeg and mpv for every ABI

The upstream build scripts build the native dependency chain, including FFmpeg, before producing the mpv libraries. Run one build per supported architecture:

```bash
cd /path/to/mpv-android/buildscripts

# Recommended phone/TV release set.
./buildall.sh --arch arm64 mpv

# Add these only when the APK supports the corresponding ABIs.
./buildall.sh --arch armv7l mpv
./buildall.sh --arch x86 mpv
./buildall.sh --arch x86_64 mpv
```

For a clean rebuild:

```bash
./buildall.sh --clean --arch arm64 mpv
```

The first build can take a long time because FFmpeg and mpv are compiled from source. Keep the generated prefix/output directories per architecture until all artifacts have been copied and checked. If a native dependency changes, rebuild the affected architecture rather than mixing libraries produced by different source or NDK revisions.

### 4. Build the Android JNI/player bridge

The Kotlin wrapper calls two native libraries in the upstream project (`mpv` and the Android/player bridge). The JNI bridge must be built for the same ABI as libmpv and FFmpeg. After the architecture-specific FFmpeg/mpv build completes, build the Android part using the upstream native build:

```bash
cd /path/to/mpv-android/buildscripts

# Run once for each architecture selected above, or use the upstream
# buildscript's documented multi-architecture workflow.
./buildall.sh --arch arm64 -n
./buildall.sh --arch armv7l -n
./buildall.sh --arch x86 -n
./buildall.sh --arch x86_64 -n
```

Here `-n` is the upstream native/application bridge rebuild option. Always confirm the exact option supported by the pinned checkout with:

```bash
./buildall.sh --help
```

The upstream scripts can also build the complete mpv-android application. For KiduyuTV, copy only the native artifacts and headers required by the bridge; do not merge the entire mpv-android application module into the KiduyuTV app.

### 5. Inspect and copy the native artifacts

Do not assume artifact names or locations across upstream revisions. Locate the output libraries first:

```bash
find /path/to/mpv-android -type f \\
  \\( -name 'libmpv.so' -o -name 'libplayer.so' -o -name 'lib*.so' \\) \\
  -path '*/arm64-v8a/*' -print
```

Copy each required library into the matching Android ABI directory. A typical KiduyuTV layout is:

```text
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libmpv.so
│   └── libplayer.so
├── armeabi-v7a/
│   ├── libmpv.so
│   └── libplayer.so
├── x86/
│   ├── libmpv.so
│   └── libplayer.so
└── x86_64/
    ├── libmpv.so
    └── libplayer.so
```

The bridge may have a different library name in the pinned source. The name must match the `System.loadLibrary("...")` call and the copied filename (`lib<name>.so`). Check dependencies before packaging:

```bash
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  for so in app/src/main/jniLibs/$abi/*.so; do
    [ -f "$so" ] || continue
    echo "--- $so ---"
    readelf -d "$so" | grep NEEDED || true
    file "$so"
  done
done
```

Every `NEEDED` library must exist in the same ABI directory or be supplied by Android. Never copy an `arm64-v8a` dependency into another ABI directory under a renamed filename.

### 6. Package only supported ABIs in Gradle

If the app uses checked-in `jniLibs`, configure the ABI set explicitly in `app/build.gradle`:

```groovy
android {
    defaultConfig {
        ndk {
            // Start with the architectures actually tested by KiduyuTV.
            abiFilters 'arm64-v8a'
            // Add only if their native directories are present and tested:
            // abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'
        }
    }
}
```

Keep the ABI directories under `src/main/jniLibs` when all flavors share them. Use `src/tv/jniLibs` or `src/phone/jniLibs` only when the native payload genuinely differs. The APK should never advertise an ABI for which `libmpv.so`, the JNI bridge, or a transitive FFmpeg library is missing.

### 7. Optionally compile the JNI bridge with CMake

If the JNI bridge is maintained in KiduyuTV rather than copied from the upstream app, use CMake to compile the bridge and import the prebuilt libmpv/FFmpeg libraries. A simplified structure is:

```text
app/src/main/cpp/
├── CMakeLists.txt
└── mpv_bridge.cpp
app/src/main/prebuilt/
└── <abi>/
    ├── libmpv.so
    └── libavcodec.so       # plus every required FFmpeg dependency
```

Example `CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(kiduyu_mpv_bridge)

add_library(kiduyu_mpv_bridge SHARED mpv_bridge.cpp)

add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES
    IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../prebuilt/${ANDROID_ABI}/libmpv.so"
    INTERFACE_INCLUDE_DIRECTORIES
        "${CMAKE_SOURCE_DIR}/../prebuilt/include"
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(kiduyu_mpv_bridge
    mpv
    ${android-lib}
    ${log-lib}
)
```

Example Gradle wiring:

```groovy
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags '-std=c++17 -fexceptions -frtti'
            }
        }
        ndk {
            abiFilters 'arm64-v8a'
        }
    }
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }
}
```

This is only a bridge skeleton. FFmpeg/mpv may have additional transitive native dependencies, linker flags, include directories, and required license/configuration files. Use `readelf -d`, `nm -D`, and the upstream JNI build files to reproduce the complete link set.

### 8. Load libraries in the correct order

The Kotlin wrapper must load the exact libraries packaged for the current ABI before calling `create()` or `init()`:

```kotlin
object MPVLib {
    init {
        // Names omit the lib prefix and .so suffix.
        System.loadLibrary("mpv")
        System.loadLibrary("player") // or the bridge name from the pinned build
    }

    external fun create(context: Context)
    external fun init()
    external fun destroy()
}
```

Test loading on a real arm64 device and an emulator separately. A `java.lang.UnsatisfiedLinkError` usually means a missing transitive `.so`, the wrong ABI directory, a bridge/library name mismatch, or an NDK/API-level incompatibility.

### 9. Preserve native debug symbols and licenses

Keep unstripped symbols outside the APK for crash analysis and configure release symbol handling. Preserve notices for mpv, FFmpeg, libass, and every bundled dependency:

```bash
find /path/to/mpv-android -iname '*license*' -o -iname '*copying*' -o -iname '*notice*'
```

Copy the applicable files into KiduyuTV’s third-party notices screen or distribution bundle. Confirm whether the exact FFmpeg configuration triggers LGPL/GPL obligations before shipping.

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
