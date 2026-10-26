# Adding and using libmpv in KiduyuTV

## Scope

KiduyuTV supports two selectable direct-stream backends. `exoplayer` continues to use the existing `PlayerEngine`, a wrapper around AndroidX Media3 `ExoPlayer` at:

`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/player/directstream/playback/PlayerEngine.kt`

The Playback settings now persist a player choice using `SettingsManager`:

- `exoplayer` — the existing Media3 implementation.
- `libmpv` — routes new direct-stream launches to `LibMpvDirectStreamActivity` and the JNI-backed `LibMpvSession`; an explicit launch of the phone-specific Media3 activity also forwards to this backend while preserving the original stream extras.

`LibMpvDirectStreamActivity` reuses the existing `activity_direct_stream.xml` controls and overlays, while libmpv owns the video surface and playback. The JNI source is in `app/src/main/cpp`; Gradle's CMake integration builds the `kiduyu_mpv` bridge for each ABI split and links it to the checked-in `libmpv.so`. HTTP headers, token refresh for Mega/Vidsrc, saved Cloudflare cookies, provider subtitles, SubDL/OpenSubtitles downloads, seeking, pause, mute, aspect selection, native track selection, local/Firebase resume history, and Android TV media play/pause keys are wired. ExoPlayer remains the default and is not silently substituted if libmpv initialization fails; the libmpv activity reports the error.

The bridge and activity have **not** been built or exercised on a device (Gradle was intentionally not run). In particular, confirm native compilation, surface rendering, device decoder behavior, HTTP header acceptance, subtitle loading, and playback across the ABI/device matrix before shipping. The checked-in FFmpeg libraries do not export `av_jni_set_java_vm`/`av_jni_set_android_app_ctx`; Android hardware-decoder interoperability therefore remains unverified, and `hwdec=auto-safe` may fall back to software decoding.

## Audited checked-in native payload (2026-10-26)

This inventory was produced from `app/src/main/jniLibs` using `readelf -h`, `readelf -d`, `nm -D`, and file-size checks. Sizes below are raw `.so` sizes (`MiB = 1,048,576` bytes), not final APK download sizes.

| Android ABI | ELF class / machine | `.so` count | Raw total |
|---|---|---:|---:|
| `arm64-v8a` | ELF64 / AArch64 | 9 | 111.94 MiB |
| `armeabi-v7a` | ELF32 / ARM EABI5 | 9 | 94.59 MiB |
| `x86` | ELF32 / Intel 80386 | 9 | 95.92 MiB |
| `x86_64` | ELF64 / x86-64 | 9 | 120.98 MiB |

Each ABI folder has the same nine libraries. Every ELF machine type matches its folder, and each library's SONAME matches its filename.

| Library | `arm64-v8a` | `armeabi-v7a` | `x86` | `x86_64` |
|---|---:|---:|---:|---:|
| `libavcodec.so` | 53.25 MiB | 44.82 MiB | 45.01 MiB | 60.91 MiB |
| `libavdevice.so` | 0.08 MiB | 0.07 MiB | 0.07 MiB | 0.08 MiB |
| `libavfilter.so` | 17.27 MiB | 14.36 MiB | 14.83 MiB | 18.60 MiB |
| `libavformat.so` | 14.80 MiB | 13.21 MiB | 12.90 MiB | 14.55 MiB |
| `libavutil.so` | 2.72 MiB | 2.54 MiB | 2.52 MiB | 2.77 MiB |
| `libmpv.so` | 9.77 MiB | 8.96 MiB | 9.55 MiB | 9.78 MiB |
| `libswresample.so` | 0.32 MiB | 0.28 MiB | 0.26 MiB | 0.41 MiB |
| `libswscale.so` | 4.70 MiB | 3.13 MiB | 3.48 MiB | 5.22 MiB |
| `libc++_shared.so` | 9.05 MiB | 7.22 MiB | 7.30 MiB | 8.67 MiB |
| **Total per ABI** | **111.94 MiB** | **94.59 MiB** | **95.92 MiB** | **120.98 MiB** |

All four ABI sets together account for about 423.43 MiB of raw shared-library files; each ABI-split APK packages only the matching set.

`libmpv.so` exports the libmpv C API (`mpv_create`, `mpv_initialize`, `mpv_command`, property/event functions, render-context functions, and `mpv_terminate_destroy`) for all four ABIs. It does **not** export `JNI_OnLoad` or `Java_*` JNI entry points. KiduyuTV now provides its own `kiduyu_mpv` JNI bridge built from C++ source; it does not depend on upstream `libplayer.so` or `libmediakitandroidhelper.so`.

### Native dependency audit

The `DT_NEEDED` graph is consistent across the four ABIs:

| Library | Other bundled `.so` dependencies | Android platform dependencies |
|---|---|---|
| `libmpv.so` | `libavcodec.so`, `libavdevice.so`, `libavfilter.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`, `libc++_shared.so` | `libandroid.so`, `libEGL.so`, `libOpenSLES.so`, `libc.so`, `libdl.so`, `libm.so`, `libz.so` |
| `libavcodec.so` | `libswresample.so`, `libavutil.so` | `libandroid.so`, `libmediandk.so`, `libz.so`, `libdl.so`, `libm.so`, `libc.so` |
| `libavdevice.so` | `libavformat.so`, `libavutil.so` | `libc.so` |
| `libavfilter.so` | `libswscale.so`, `libavformat.so`, `libavcodec.so`, `libswresample.so`, `libavutil.so` | `libm.so`, `libc.so` |
| `libavformat.so` | `libavcodec.so`, `libavutil.so` | `libm.so`, `libz.so`, `libc.so` |
| `libavutil.so` | — | `libm.so`, `libandroid.so`, `libdl.so`, `libc.so` |
| `libswresample.so` | `libavutil.so` | `libm.so`, `libc.so` |
| `libswscale.so` | `libavutil.so` | `libm.so`, `libc.so` |

All FFmpeg-to-FFmpeg dependency edges resolve within each ABI folder; **do not remove any of the seven FFmpeg `.so` files** from the current build. The previously supplied runtime was ELF32 ARM only and lacked the floating-point `std::__ndk1` `to_chars`/`from_chars` symbols required by libmpv. It was not used. The replacement runtimes are from the official Android NDK r30 archive and are ABI-matched. An exact mangled-symbol comparison found no missing C++ imports from any of the four `libmpv.so` files. This is a static compatibility check, not proof of runtime behavior on devices.

The runtimes were extracted from Google's [Android NDK r30 Linux archive](https://dl.google.com/android/repository/android-ndk-r30-linux.zip), from `toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/`. The corresponding ABI directories are `aarch64-linux-android`, `arm-linux-androideabi`, `i686-linux-android`, and `x86_64-linux-android`. SHA-256 checksums:

- `arm64-v8a`: `7466ed097631a564ed6bff0b47768caa76bf03270d2047b5a65c03f497b824df`
- `armeabi-v7a`: `2ee592c9ba3372da027384deb1070def5ae76f3ea2246bd447360faa73ac22f4`
- `x86`: `865b82bcda0b5b829614dd7a4c08aba121e53259c807898b6d0b375414187075`
- `x86_64`: `7918ebd0b8074c312d0c610205d49760b98c008026e6b817cd92103a3e12241b`

The `.comment` sections of the checked-in `libmpv.so` files contain Android Clang 21.0.0 and 22.0.1 build notes. The repository does not include a native source revision/configuration manifest, so those notes alone are not sufficient to reproduce the binaries. Preserve the source commit, NDK version, FFmpeg configuration, and licenses when rebuilding or replacing them. Google's NDK documentation identifies libc++ as the supported C++ standard library and states that `libc++_shared.so` must be included when using the shared runtime.

Release builds use R8, but R8 does not shrink or fix native libraries. `app/proguard-rules.pro` preserves `LibMpvSession` and its native declarations so the JNI symbol names survive shrinking.

## Important upstream constraint

The official [`mpv-android`](https://github.com/mpv-android/mpv-android) repository says that it is **not a library/module or AAR that can be imported into another app**. It provides the Kotlin wrapper, Android view, JNI code, and native build scripts as reference code. The relevant upstream files are:

- [`MPVLib.kt`](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVLib.kt) — JNI declarations, commands, properties, observers, and event constants.
- [`BaseMPVView.kt`](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt) — `SurfaceView` lifecycle and attaching/detaching the libmpv surface.
- [`app/src/main/jni`](https://github.com/mpv-android/mpv-android/tree/master/app/src/main/jni) — native bridge code.
- [`buildscripts`](https://github.com/mpv-android/mpv-android/tree/master/buildscripts) — native FFmpeg/mpv/Android build pipeline.

The upstream build documentation also states that native compilation is separate from Gradle, is supported on Linux and macOS, and that Windows/WSL is not supported by those scripts.

## Architecture

The established ExoPlayer activity remains intact. `DirectStreamActivity.createIntent()` reads the persisted setting and selects the player for subsequent direct-stream launches. The libmpv path is split into these pieces:

- `LibMpvDirectStreamActivity` — reuses `activity_direct_stream.xml`, stream resolution, player controls, and provider subtitle UI.
- `LibMpvSession` — serializes JNI work and exposes playback snapshots, track selection, seeking, audio, aspect, and subtitle commands.
- `app/src/main/cpp/mpv_jni.cpp` — implements the small JNI bridge against libmpv's C API.
- `app/src/main/cpp/CMakeLists.txt` — builds `libkiduyu_mpv.so` and links the ABI-matched imported `libmpv.so`.

Native load/init failure is shown in the libmpv screen; it does not fall back to ExoPlayer without the user's choice. This avoids changing player behavior silently and makes ABI/device compatibility errors visible.

## Native build and packaging

The libmpv/FFmpeg/libc++ `.so` payloads under `jniLibs` are prebuilt artifacts; Gradle packages these files and compiles only KiduyuTV's smaller JNI bridge. Rebuilding the prebuilt native payload is a separate toolchain process. The commands below follow the official `mpv-android/buildscripts` layout and are for a controlled rebuild, not proof of the current binary provenance. Run those scripts on Linux or macOS; upstream says Windows/WSL are unsupported.

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

### 4. JNI/player bridge in KiduyuTV

KiduyuTV's bridge is implemented in `app/src/main/cpp/mpv_jni.cpp`, declared by `LibMpvSession.kt`, and built as `libkiduyu_mpv.so` by `app/src/main/cpp/CMakeLists.txt`. It wraps libmpv creation/initialization, Android Surface attachment, commands, observed playback properties, events, tracks, HTTP headers, subtitles, and release. It is project-owned JNI code; do not copy the upstream application's Android/player bridge or merge its app module.

The bridge is compiled against the pinned libmpv client header in `app/src/main/cpp/include/mpv/client.h` and links the ABI-matched `libmpv.so` imported from `app/src/main/jniLibs/${ANDROID_ABI}`. Transitive FFmpeg and C++ runtime libraries are packaged from the same ABI directory. See `app/build.gradle` for the active CMake path/version, NDK version, shared C++ runtime flag, and ABI filters.

### 5. Inspect and copy the native artifacts

Do not assume artifact names or locations across upstream revisions. Locate the output libraries first:

```bash
find /path/to/mpv-android -type f \\
  \\( -name 'libmpv.so' -o -name 'libplayer.so' -o -name 'lib*.so' \\) \\
  -path '*/arm64-v8a/*' -print
```

The current KiduyuTV layout is nine shared libraries in each ABI directory:

```text
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libmpv.so
│   ├── libavcodec.so
│   ├── libavdevice.so
│   ├── libavfilter.so
│   ├── libavformat.so
│   ├── libavutil.so
│   ├── libswresample.so
│   ├── libswscale.so
│   └── libc++_shared.so
├── armeabi-v7a/
│   └── [same nine names]
├── x86/
│   └── [same nine names]
└── x86_64/
    └── [same nine names]
```

The ABI-matched `libc++_shared.so` is present in every folder. The CMake build produces the JNI bridge per selected ABI; Gradle packages that output alongside these prebuilt files. Check the complete dependency graph before packaging:

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

`app/build.gradle` currently packages `src/main/jniLibs` for both the `phone` and `tv` flavors and splits release APKs across the four checked-in ABIs. The active configuration is:

```groovy
splits {
    abi {
        enable true
        reset()
        include 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'
        universalApk false
    }
}
```

The GitHub Actions workflow installs the pinned NDK and CMake, assembles `PhoneRelease` and `TvRelease`, then publishes four ABI-specific APKs per flavor; there is no universal APK. Keep `splits.abi`, `ndk.abiFilters`, and the directories under `app/src/main/jniLibs` aligned. The build verifies that every supported ABI contains libmpv, all seven FFmpeg libraries, and `libc++_shared.so`; this file-presence check does not replace native compilation or device testing.

Keep the ABI directories under `src/main/jniLibs` when all flavors share them. Use `src/tv/jniLibs` or `src/phone/jniLibs` only when the native payload genuinely differs. Never advertise an ABI for which `libmpv.so`, its compatible C++ runtime, the JNI bridge, or a transitive FFmpeg library is missing.

### 7. Compile the JNI bridge with CMake

The existing KiduyuTV bridge uses CMake to import the checked-in libmpv binaries. Its current structure is:

```text
app/src/main/cpp/
├── CMakeLists.txt
├── mpv_jni.cpp
└── include/mpv/client.h
app/src/main/jniLibs/<abi>/
├── libmpv.so
├── libavcodec.so
├── libavdevice.so
├── libavfilter.so
├── libavformat.so
├── libavutil.so
├── libswresample.so
├── libswscale.so
└── libc++_shared.so        # NDK r30 runtime, ABI-specific
```

Example `CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(kiduyutv_mpv LANGUAGES CXX)

add_library(kiduyu_mpv SHARED mpv_jni.cpp)

add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES
    IMPORTED_LOCATION
        "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libmpv.so"
)

target_include_directories(kiduyu_mpv PRIVATE "${CMAKE_CURRENT_LIST_DIR}/include")

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(kiduyu_mpv
    mpv
    ${android-lib}
    ${log-lib}
)
```

The `jniLibs` directory and CMake target are already wired in `app/build.gradle`; do not add a second native-library packaging directory. The active Gradle wiring includes:

```groovy
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags '-std=c++17'
                arguments '-DANDROID_STL=c++_shared'
            }
        }
        ndk {
            // Keep this aligned with the ABI splits above.
            abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'
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

The app pins NDK `27.2.12479018`; GitHub Actions installs that NDK and CMake `3.22.1`. The shared `libc++_shared.so` files are from official NDK r30 and were statically checked for the bundled libmpv imports. Recheck these assumptions when changing native versions. The bridge and packaging have not yet been confirmed by a Gradle build or device test.

### 8. Load libraries in the correct order

`LibMpvSession` attempts to load the ABI-matched C++ runtime and libmpv before the app's generated JNI bridge; it captures load failures so the Activity can show an error instead of crashing:

```kotlin
private val nativeLoadFailure: Throwable? = runCatching {
    System.loadLibrary("c++_shared")
    System.loadLibrary("mpv")
    System.loadLibrary("kiduyu_mpv")
}.exceptionOrNull()

// Native initialization reports nativeLoadFailure in the player UI.
}
```

Test loading on a real arm64 device and an emulator separately. A `java.lang.UnsatisfiedLinkError` usually means a missing transitive `.so`, the wrong ABI directory, a bridge/library name mismatch, or an NDK/API-level incompatibility. The current bridge has not yet received Android build/device validation.

### 9. Preserve native debug symbols and licenses

Keep unstripped symbols outside the APK for crash analysis and configure release symbol handling. Preserve notices for mpv, FFmpeg, libass, and every bundled dependency:

```bash
find /path/to/mpv-android -iname '*license*' -o -iname '*copying*' -o -iname '*notice*'
```

Copy the applicable files into KiduyuTV’s third-party notices screen or distribution bundle. Confirm whether the exact FFmpeg configuration triggers LGPL/GPL obligations before shipping.

The upstream scripts are useful as a reference, but they should not be copied blindly into the KiduyuTV Gradle build. They are a full application build pipeline rather than a small Maven dependency.

## Kotlin/JNI bridge responsibilities

The implemented `LibMpvSession` deliberately exposes a smaller app-specific interface than upstream's general-purpose `MPVLib`. Its native entry points are `nativeCreate`, `nativeAttach`, `nativeDetach`, `nativeLoad`, `nativeCommand`, `nativeSnapshot`, and `nativeRelease`. Native calls are serialized on a single coroutine dispatcher. A polling coroutine drains mpv's event queue, samples observed properties and tracks, and emits a JSON snapshot; the Activity collects that `StateFlow` on the main thread. File-loaded events are used to attach provider subtitles only after a stream becomes active.

Keep the JNI class/method names in sync with `mpv_jni.cpp` and preserved by R8. Do not add callbacks from native code into UI objects; keep the bridge free of Activity/view references except for the global `Surface` reference, which must be detached and released when the surface is destroyed.

## Rendering on Android

KiduyuTV uses a dedicated `SurfaceView` inserted into the shared DirectStream layout. Its lifecycle follows the upstream `BaseMPVView` pattern:

1. Creates and initializes the libmpv handle when the first valid surface is created.
2. Attaches the Java Surface as mpv's `wid` and enables `force-window`.
3. Updates `android-surface-size` from `surfaceChanged`.
4. Sets `vo=null`, disables the force window, then clears `wid` and releases the JNI global Surface reference on `surfaceDestroyed`.
5. Reattaches a recreated surface to the existing libmpv handle instead of initializing a second context.
6. Terminates the mpv handle and releases the surface reference when the Activity is destroyed.

A `SurfaceView` is the safer first implementation for Android TV and Fire TV because it avoids copying video frames through the CPU. The libmpv render API can provide more flexible Compose overlays, but it requires an OpenGL/EGL rendering integration and careful synchronization with the render callback.

## Creating and configuring the mpv context

`mpv_jni.cpp` sets options before `mpv_initialize()`: `vo=gpu`, `gpu-context=android`, `opengl-es=yes`, `gpu-api=opengl`, `ao=audiotrack`, input bindings off, `terminal=no`, `force-window=no`, `idle=once`, `hwdec=auto-safe`, cache enabled, and `keep-open=yes`. `nativeAttach()` then enables the window for the supplied surface. The exact hardware-decoding behavior still needs phone, Android TV, and Fire TV testing; the checked-in FFmpeg libraries do not export Android's `av_jni_set_*` helpers, so hardware interoperability has not been confirmed.

## Loading a direct stream

The libmpv command API accepts separate arguments. KiduyuTV passes the URL and flags as distinct command arguments and includes the required insertion index `-1` before the per-file options (the fourth `loadfile` argument in the checked mpv API):

```kotlin
MPVLib.command(arrayOf("loadfile", stream.url, "replace", "-1", "start=120.0"))
```

The app supplies the actual resume position as seconds in the `start=<seconds>` file option, so seeking does not race asynchronous file loading. Subsequent user seeks use relative or absolute mpv seek commands:

```kotlin
MPVLib.command(arrayOf("seek", "30", "relative+exact"))
```

For playback controls:

```kotlin
MPVLib.command(arrayOf("set", "pause", "yes"))
MPVLib.command(arrayOf("set", "pause", "no"))
MPVLib.command(arrayOf("seek", "-30", "relative+exact"))
```

Prefer typed property and command APIs in the JNI layer. Do not construct shell commands, and do not pass provider URLs through a shell.

## Headers, cookies, and subtitles

`LibMpvDirectStreamActivity` normalizes provider headers and saved Cloudflare cookies into a structured `Map<String, String>`. The JNI bridge validates header names/values, escapes commas and backslashes for mpv's list option syntax, sets `http-header-fields`, and then issues `loadfile`. It does not log header values. Video loading starts immediately; optional provider subtitle tracks are fetched asynchronously to an app-private cache with each subtitle's own headers (independent of the media headers), then attached with `sub-add` after libmpv reports `MPV_EVENT_FILE_LOADED`. Content/file URIs are copied into the same cache so libmpv can open them directly. Cached provider subtitle files are limited to 20 MiB and failed/empty downloads are skipped without stopping video playback. Downloaded SubDL/OpenSubtitles files use the `sub-add` command at runtime. A playback-generation guard prevents late token-refresh or subtitle work from a previous stream choice from replacing the current selection.

## Observing progress and errors

`LibMpvSession` samples these properties and drains native events every 250 ms:

- `time-pos` as a double for watch-history position.
- `duration` as a double for total duration.
- `pause` as a boolean.
- `eof-reached` as a boolean.
- `paused-for-cache` for buffering state.
- `track-list` for native audio/video/subtitle choices.
- `MPV_EVENT_START_FILE`, `MPV_EVENT_FILE_LOADED`, and `MPV_EVENT_END_FILE` for load/terminal errors.

Native time conversion rejects non-finite, negative, and overflowing values before converting to milliseconds. The Activity throttles local/Firebase watch-history writes to 30-second checkpoints and saves on pause; Firebase sync remains signed-in-only.

The bridge reports mpv command and end-file errors in the player status overlay. It does not currently expose a sanitized failed URL or HTTP response status, and its release log output has not been device-validated; do not add raw provider URLs, cookies, or tokens to logs.

## Activity and lifecycle checklist

- `LibMpvDirectStreamActivity` owns one `LibMpvSession` per screen and reattaches the same handle if Android recreates its `SurfaceView`.
- Playback pauses on Activity pause and resumes only if it was playing before the pause.
- The Activity saves progress locally and (when signed in) to Firebase, releases subtitle/resolver work, and terminates the native handle on destruction.
- Media play/pause key events are supported; test D-pad focus, seek, track selection, back navigation, phone rotation, and Fire TV surface recreation.
- ExoPlayer remains the default. A libmpv load/init error is shown to the user; it does not silently switch backends.

## Suggested rollout for KiduyuTV

Implementation is present, but it is **not release-validated**: Gradle was not run and no Android phone/TV/Fire TV device test was performed. Before enabling libmpv as a production default, build all flavor/ABI variants, verify native loading and surface rendering, test authenticated HLS/DASH and resume/history, confirm provider subtitle behavior, exercise decoder fallback on target devices, and review licenses/notices. Keep ExoPlayer as the default until these checks pass.

## References

- [mpv-android README](https://github.com/mpv-android/mpv-android)
- [mpv-android MPVLib.kt](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVLib.kt)
- [mpv-android BaseMPVView.kt](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt)
- [mpv-android native build instructions](https://github.com/mpv-android/mpv-android/blob/master/buildscripts/README.md)
- [Official libmpv examples](https://github.com/mpv-player/mpv-examples/blob/master/libmpv/README.md)
- [mpv manual: using mpv from other programs](https://mpv.io/manual/master/#using-mpv-from-other-programs-or-scripts)
