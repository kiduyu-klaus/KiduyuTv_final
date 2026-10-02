# Stream language probing with FFmpeg and manifests

## Goal

Populate `StreamItem.language` from the media source itself, rather than relying only on a provider label. The result should identify the available audio languages for HLS and DASH streams before playback whenever possible, and should allow the stream picker to show an honest summary such as `English · Japanese`.

This guide is designed for the Direct Stream flow:

```text
ProvidersApi -> StreamItem(url, headers, type, mimeType)
             -> Stream language probe
             -> StreamItem.language / audioTracks
             -> StreamSelectionDialog
             -> PlayerEngine / Media3 track selector
```

## Important: the current FFmpeg AAR is not FFprobe

The app currently includes `app/libs/lib-decoder-ffmpeg-release.aar` and builds `PlayerEngine` with Media3's extension renderer mode set to `PREFER`. That provides software decoding support for codecs such as DTS. It **does not** expose an `ffprobe` binary, a Kotlin `FFprobeKit`, or an API for inspecting arbitrary URLs.

Do not attempt to execute `ffprobe` from that decoder AAR. It will not exist on Android and a process-based implementation would fail.

For HLS and DASH, a small header-aware manifest parser is faster, safer, and more useful than FFprobe. Use an actual FFprobe integration only for progressive/container URLs (`.mp4`, `.mkv`, signed downloads) where the playlist does not expose track metadata.

## What the app already has

| Existing component | Reuse for the probe |
| --- | --- |
| `StreamItem.headers` | Attach the same `Referer`, `Origin`, `User-Agent`, and `Cookie` headers used by playback. |
| `PlayerEngine` | Reference behavior for `DefaultHttpDataSource.Factory`; do not create a second header policy. |
| `StreamValidator` | Reuse its short timeout, redirects, and malformed-page safeguards. Keep language probing separate from validity probing. |
| `TrackFormatter` | Convert ISO language codes to display labels in the track dialog. |
| `ProvidersApi` | Start background probes only after it has normalized a returned stream URL and headers. |

## Data model

Keep the provider's original `language` as a fallback, but preserve structured audio details instead of collapsing everything into a string.

```kotlin
data class AudioLanguageTrack(
    val languageCode: String?,     // BCP-47/ISO value, e.g. "en", "ja", "hin"
    val label: String?,            // Playlist NAME, e.g. "English 5.1"
    val codec: String? = null,
    val channels: String? = null,
    val isDefault: Boolean = false
)

data class StreamItem(
    // Existing fields …
    val language: String = "",
    val audioTracks: List<AudioLanguageTrack> = emptyList()
)
```

`language` can then be a user-facing summary derived from the structured values:

```kotlin
fun List<AudioLanguageTrack>.displaySummary(): String =
    mapNotNull { it.languageCode ?: it.label }
        .map(TrackFormatter::languageDisplay)
        .distinct()
        .joinToString(" · ")
```

Do not overwrite a non-empty provider label with `"Unknown"`. Prefer this order:

1. Manifest/FFprobe language tags.
2. Manifest audio rendition name when no language tag exists.
3. The provider's existing `StreamItem.language` value.
4. Empty string, displayed as `Unknown` only in UI.

## 1. Header-aware manifest fetcher

The probe must make the same authenticated request as playback. Many provider URLs return 403/empty data without `Referer`, `Origin`, or cookies. Never log raw signed URLs, cookies, or authorization headers.

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object StreamManifestFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun getText(stream: StreamItem, maxBytes: Long = 512 * 1024): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(stream.url)
                .header("Range", "bytes=0-${maxBytes - 1}")
                .apply {
                    stream.headers.forEach { (name, value) ->
                        if (name.isNotBlank() && value.isNotBlank()) header(name, value)
                    }
                }
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                        ?.takeIf { body -> !body.trimStart().startsWith("<html", true) }
                }
            }.getOrNull()
        }
}
```

In production, factor the `OkHttpClient` out of `StreamValidator` rather than creating another client. The code above is deliberately self-contained to show the required request behavior.

## 2. HLS: parse `#EXT-X-MEDIA` audio renditions

An HLS **master playlist** reports alternative audio tracks with lines like:

```text
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="en",NAME="English",DEFAULT=YES,URI="audio/en.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="ja",NAME="Japanese",DEFAULT=NO,URI="audio/ja.m3u8"
```

The media playlist itself usually cannot report all languages. Probe the master playlist URL returned by the provider, not individual segment URLs.

Use a quoted-attribute parser; splitting on commas alone breaks names such as `NAME="English, Stereo"`.

```kotlin
private val hlsAttribute = Regex("""([A-Z0-9-]+)=(?:\"([^\"]*)\"|([^,]*))(?:,|$)""")

fun parseHlsAudioTracks(manifest: String): List<AudioLanguageTrack> =
    manifest.lineSequence()
        .filter { it.startsWith("#EXT-X-MEDIA:") }
        .mapNotNull { line ->
            val attributes = hlsAttribute.findAll(line.substringAfter(':'))
                .associate { match ->
                    match.groupValues[1] to match.groupValues[2].ifBlank { match.groupValues[3] }
                }
            if (!attributes["TYPE"].equals("AUDIO", ignoreCase = true)) return@mapNotNull null

            AudioLanguageTrack(
                languageCode = attributes["LANGUAGE"]?.normalizeLanguageCode(),
                label = attributes["NAME"]?.takeIf(String::isNotBlank),
                channels = attributes["CHANNELS"]?.takeIf(String::isNotBlank),
                isDefault = attributes["DEFAULT"].equals("YES", ignoreCase = true)
            )
        }
        .distinctBy { listOf(it.languageCode, it.label, it.channels) }
        .toList()

private fun String.normalizeLanguageCode(): String =
    trim().lowercase().replace('_', '-')
```

Useful HLS fallbacks:

- `#EXT-X-STREAM-INF` may contain only an embedded/default audio codec, not language metadata. Do not infer English from it.
- `#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS` is a text/caption track, not an audio language.
- An `AUDIO` rendition with no `LANGUAGE` should retain `NAME` as its label, then be rendered as `Unknown audio` if both are absent.

## 3. DASH: parse audio `AdaptationSet` language tags

DASH manifests generally store language at `AdaptationSet`, sometimes inherited from `Representation`:

```xml
<AdaptationSet contentType="audio" lang="en" mimeType="audio/mp4">
  <Representation codecs="mp4a.40.2" audioSamplingRate="48000" />
</AdaptationSet>
```

Use Android's pull parser; do not use regular expressions for XML.

```kotlin
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

fun parseDashAudioTracks(mpd: String): List<AudioLanguageTrack> {
    val parser = Xml.newPullParser().apply { setInput(StringReader(mpd)) }
    val tracks = mutableListOf<AudioLanguageTrack>()

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "AdaptationSet") {
            val contentType = parser.getAttributeValue(null, "contentType")
            val mimeType = parser.getAttributeValue(null, "mimeType").orEmpty()
            if (contentType.equals("audio", true) || mimeType.startsWith("audio/")) {
                tracks += AudioLanguageTrack(
                    languageCode = parser.getAttributeValue(null, "lang")
                        ?.normalizeLanguageCode(),
                    label = parser.getAttributeValue(null, "label"),
                    codec = parser.getAttributeValue(null, "codecs")
                )
            }
        }
        parser.next()
    }
    return tracks.distinctBy { listOf(it.languageCode, it.label, it.codec) }
}
```

Some MPDs are dynamic and use `BaseURL`/segment templates. The first 256–512 KiB is enough for adaptation metadata, so do not download segments just to discover language.

## 4. One probe entry point

Choose the parser from the already normalized `type`, MIME hint, and URL. The existing `ProvidersApi` already forces known disguised playlists to `type = "hls"`; preserve that logic.

```kotlin
object StreamLanguageProbe {
    suspend fun probe(stream: StreamItem): List<AudioLanguageTrack> {
        val kind = when {
            stream.type.equals("hls", true) ||
                stream.mimeType.contains("mpegurl", true) ||
                stream.url.contains(".m3u8", true) -> "hls"
            stream.type.equals("dash", true) ||
                stream.mimeType.equals("application/dash+xml", true) ||
                stream.url.substringBefore('?').endsWith(".mpd", true) -> "dash"
            else -> "container"
        }

        return when (kind) {
            "hls" -> StreamManifestFetcher.getText(stream)
                ?.let(::parseHlsAudioTracks)
                .orEmpty()
            "dash" -> StreamManifestFetcher.getText(stream)
                ?.let(::parseDashAudioTracks)
                .orEmpty()
            else -> emptyList() // Optional FFprobe path below.
        }
    }
}
```

Run it with a bounded parallelism of two or three streams. Do not block the initial stream list or automatic playback while all probes run.

```kotlin
private val languageProbeDispatcher = Dispatchers.IO.limitedParallelism(3)

suspend fun enrichLanguages(streams: List<StreamItem>): List<StreamItem> =
    coroutineScope {
        streams.map { stream ->
            async(languageProbeDispatcher) {
                val tracks = runCatching { StreamLanguageProbe.probe(stream) }
                    .getOrDefault(emptyList())
                val summary = tracks.displaySummary()
                stream.copy(
                    language = summary.ifBlank { stream.language },
                    audioTracks = tracks
                )
            }
        }.awaitAll()
    }
```

Store enrichment separately from source fetch state or update an observable stream list. `StreamItem` is currently immutable for language fields, so creating copies prevents UI races.

## 5. Actual FFprobe: optional progressive-file fallback

For an MP4/MKV/TS URL, language tags are in container stream metadata and are not available from an HLS/DASH parser. This is where FFprobe is useful.

An FFprobe JSON request would be:

```text
ffprobe -v error \
  -show_entries stream=index,codec_type,codec_name,channels:stream_tags=language,title \
  -of json \
  -headers "Referer: https://example.invalid\r\nUser-Agent: ...\r\nCookie: ...\r\n" \
  "https://signed.example.invalid/movie.mp4"
```

Expected JSON shape:

```json
{
  "streams": [
    {"index": 1, "codec_type": "audio", "codec_name": "aac", "channels": 2,
     "tags": {"language": "eng", "title": "English"}},
    {"index": 2, "codec_type": "audio", "codec_name": "ac3", "channels": 6,
     "tags": {"language": "jpn", "title": "Japanese 5.1"}}
  ]
}
```

### Integration choices

1. **Preferred for the current app: server-side FFprobe.** Send the URL and a short-lived, allowlisted header set to the providers backend; the backend runs FFprobe in a constrained process and returns only normalized audio metadata. This keeps FFmpeg native binaries out of the APK and centralizes updates.
2. **On-device JNI wrapper:** build an Android NDK library exposing a narrow `probe(url, headers): String` function around `libavformat`. Do not spawn a shell process. Treat all input as data, cap probe bytes/time, and keep only audio stream metadata.
3. **Do not use a retired/unmaintained Android FFmpeg wrapper just for this feature.** Verify maintenance, ABI coverage, binary size, codec licensing, and license obligations before adding one.

A safe app-facing abstraction keeps the implementation swappable:

```kotlin
interface ContainerMetadataProbe {
    suspend fun probeAudioTracks(
        url: String,
        headers: Map<String, String>
    ): List<AudioLanguageTrack>
}

class HybridStreamLanguageProbe(
    private val containerProbe: ContainerMetadataProbe?
) {
    suspend fun probe(stream: StreamItem): List<AudioLanguageTrack> {
        val manifestTracks = StreamLanguageProbe.probe(stream)
        if (manifestTracks.isNotEmpty()) return manifestTracks
        return containerProbe?.probeAudioTracks(stream.url, stream.headers).orEmpty()
    }
}
```

## 6. Media3 is the final authority during playback

Even a successful preflight probe can become stale: signed URLs expire, a CDN can serve a different manifest by region, and a master playlist can redirect. When playback begins, use the existing `onTracksChanged` callback as the authoritative source.

```kotlin
engine.player.addListener(object : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        val audio = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.length).map { index -> group.getTrackFormat(index) }
            }
            .map { format ->
                AudioLanguageTrack(
                    languageCode = format.language,
                    label = format.label,
                    codec = format.codecs,
                    channels = format.channelCount.takeIf { it > 0 }?.toString(),
                    isDefault = false
                )
            }
        // Update the stream-picker label only if this differs from the
        // preflight result. TrackSelectionDialog already formats these tracks.
    }
})
```

This also covers playlists whose audio selection is exposed only after Media3 prepares the source.

## Where to integrate

1. Add `AudioLanguageTrack` and `audioTracks` to `StreamItem`.
2. Add `StreamManifestFetcher`, HLS/DASH parsers, and `StreamLanguageProbe` beside `StreamValidator` in `ui/player/directstream/playback`.
3. After `ProvidersApi` produces normalized `StreamItem` values, start `enrichLanguages` in `DirectStreamActivity` or `StreamResolver` as a non-blocking child job.
4. Publish enriched stream copies to `StreamSelectionDialog.updateStreams(...)` rather than waiting before selecting the first playable stream.
5. Render `stream.language` in the stream picker and keep the complete list in the Tracks dialog after playback begins.

Do not add probing inside `StreamValidator.probe()`: validation should remain cheap and answer “can this URL be played?”, while metadata discovery is optional and may need a larger manifest range.

## Failure and privacy rules

- Never log full stream URLs, query signatures, cookies, or `Authorization` headers.
- Redact host/path details in diagnostics, as the player already does for sensitive URLs.
- Use 5–6 second connect/read timeouts and cancel probes with the activity/job lifecycle.
- Limit manifest reads to 256–512 KiB; never download media segments for language discovery.
- A failed language probe must leave the stream playable and preserve the provider label.
- Treat `und`, blank, and malformed language tags as unknown; do not guess language from a title or filename.
- Cache only normalized metadata keyed by a stable stream identity. Never persist expiring signed URLs or cookies.

## Test matrix

| Case | Expected result |
| --- | --- |
| HLS master with `LANGUAGE=en` and `ja` | Picker shows English and Japanese; Tracks dialog agrees after prepare. |
| HLS media playlist with no `EXT-X-MEDIA` | No override; retain provider language/fallback. |
| DASH MPD with audio `AdaptationSet lang` | One audio track per language/label. |
| Progressive MP4 with `eng`/`jpn` container tags | FFprobe fallback returns both tracks. |
| Signed URL requiring Referer + Cookie | Probe succeeds only because `StreamItem.headers` are forwarded. |
| 403/Cloudflare/HTML challenge page | No language result; stream remains available for existing bypass/validation flow. |
| Header says `image/jpeg`, body is `#EXTM3U` | Existing HLS normalization still forces HLS; parser reads the playlist body. |
| No language metadata | UI shows `Unknown`; no false English label. |

## Recommended rollout

Ship manifest parsing first behind debug logging that records only provider, media family, track count, and normalized language codes. Compare the probe result to Media3's `onTracksChanged` result for a release cycle. Add an FFprobe fallback only if progressive sources materially lack usable metadata after that measurement.
