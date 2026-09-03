package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.SubtitleItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.HttpCookieStore
import com.kiduyuk.klausk.kiduyutv.ui.player.cloudflareBypass.CloudflareBypassActivity

/**
 * Thin wrapper around [ExoPlayer] that:
 *  - Picks HLS or progressive media source based on the stream URL.
 *  - Applies per-stream HTTP request headers via [DefaultHttpDataSource.Factory]
 *    so playback works whether the server is in `enableProxy=true` (no
 *    headers) or `enableProxy=false` (Referer/Origin required) mode.
 *  - Forwards playback state changes and errors to the host activity via
 *    callbacks.
 *
 * The activity owns the [androidx.media3.ui.PlayerView] and just sets its
 * [Player] to [player].
 */
@OptIn(UnstableApi::class)
class PlayerEngine(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * `DefaultHttpDataSource.Factory` applies `Referer`/`Origin` and any
     * other headers required by the upstream CDN. It is rebuilt per stream
     * because each stream may carry different headers.
     */

    private fun buildDataSourceFactory(stream: StreamItem): DefaultDataSource.Factory {
        // Detect and replace obviously fake User-Agent strings before the
        // factory is built. Anti-bot CDNs (Cloudflare, Akamai, DataDome)
        // 403 any UA that claims a browser version newer than the latest
        // stable release; vixsrc.to in particular ships "Chrome/150"
        // which doesn't exist and triggers 403 on the manifest request.
        val safeHeaders = playbackHeaders(stream)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(safeHeaders)
        return DefaultDataSource.Factory(appContext, httpFactory)
    }

    /**
     * DahmerMovies' Cloudflare worker expects a browser-shaped request.
     * Do not copy its server-supplied `Range: bytes=0-` value: Media3
     * generates a precise Range header for every progressive-media read,
     * seek and reconnect.
     */
    private fun dahmerMoviesHeaders(): Map<String, String> = linkedMapOf(
        "User-Agent" to REAL_BROWSER_USER_AGENT,
        "Accept" to "*/*",
        "Referer" to DAHMER_MOVIES_REFERER
    )

    private fun playbackHeaders(stream: StreamItem): Map<String, String> {
        val streamCookie = stream.headers.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
        val headers = if (stream.provider.equals("DahmerMovies", ignoreCase = true)) {
            dahmerMoviesHeaders().toMutableMap().apply {
                streamCookie?.let { put("Cookie", it) }
            }
        } else {
            sanitizeUserAgent(stream.headers)
        }
        // Reuse a Cloudflare `cf_clearance` cookie captured by
        // [CloudflareBypassActivity] for the same host so the next playback
        // attempt can pass through the Cloudflare gate without re-solving
        // the challenge. Only applied when the stream did not already ship
        // its own `Cookie` header and no in-memory cookie is available.
        if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
            val cfCookie = loadSavedCloudflareCookie(stream.url)
            if (!cfCookie.isNullOrBlank()) {
                val merged = LinkedHashMap(headers)
                merged["Cookie"] = cfCookie
                Log.i(
                    TAG,
                    "Attaching saved Cloudflare cookie (${cfCookie.length} chars) " +
                        "to playback for ${stream.url}"
                )
                // Skip the in-memory HttpCookieStore path: cf_clearance takes
                // priority and we don't want the platform cookie jar to
                // shadow it.
                return merged
            }
        }
        val storedCookie = HttpCookieStore.cookieHeader(stream.url)
        if (
            storedCookie.isNullOrBlank() ||
            headers.keys.any { it.equals("Cookie", ignoreCase = true) }
        ) {
            return headers
        }
        return LinkedHashMap(headers).apply { put("Cookie", storedCookie) }
    }

    /**
     * Reads the Cloudflare cookie blob previously stored by
     * [CloudflareBypassActivity] for [url]'s host. Returns `null` when no
     * cookie is saved or the URL is unparseable.
     */
    private fun loadSavedCloudflareCookie(url: String): String? {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return CloudflareBypassActivity.loadCookies(appContext, host)
    }

    /**
     * The DahmerMovies worker expects the nested origin's scheme and host
     * to be percent-encoded. Keep the already encoded media path unchanged.
     */
    private fun normalizePlaybackUrl(stream: StreamItem): String =
        if (
            stream.provider.equals("DahmerMovies", ignoreCase = true) &&
            stream.url.startsWith(DAHMER_MOVIES_RAW_PREFIX, ignoreCase = true)
        ) {
            DAHMER_MOVIES_ENCODED_PREFIX +
                stream.url.substring(DAHMER_MOVIES_RAW_PREFIX.length)
        } else {
            stream.url
        }

    /**
     * Returns a copy of [headers] with the `User-Agent` entry replaced
     * when the JSON supplies an obviously-fake version. Header lookup is
     * case-insensitive. The replacement is logged so the operator can
     * identify which providers are shipping bad UAs and fix them
     * server-side. Headers without a `User-Agent` are returned unchanged
     * (the factory's [USER_AGENT] default applies).
     */
    private fun sanitizeUserAgent(headers: Map<String, String>): Map<String, String> {
        val entry = headers.entries.firstOrNull { (k, _) ->
            k.equals("User-Agent", ignoreCase = true)
        } ?: return headers

        if (!isObviouslyFakeUserAgent(entry.value)) return headers

        Log.w(TAG, "Replacing suspicious User-Agent: ${entry.value}")
        val mutated = LinkedHashMap(headers)
        mutated[entry.key] = REAL_BROWSER_USER_AGENT
        return mutated
    }

    /**
     * Returns true when [userAgent] claims a browser major version newer
     * than the latest known stable release. Real browsers in mid-2026 are
     * Chrome 130-140 / Firefox 130-140; anything claiming 141+ is almost
     * certainly copy-pasted from a future release schedule by an upstream
     * scraper. We also flag any UA whose Chrome major is *dramatically*
     * higher than reality (Chrome/999, Chrome/150, etc.) regardless of
     * cutoff, because the same CDNs that 403 Chrome/150 also 403 Chrome/999.
     */
    private fun isObviouslyFakeUserAgent(userAgent: String): Boolean {
        val chromeMajor = Regex("""Chrome/(\d+)""")
            .find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
        if (chromeMajor != null && chromeMajor > MAX_CHROME_MAJOR) return true

        val firefoxMajor = Regex("""Firefox/(\d+)""")
            .find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
        if (firefoxMajor != null && firefoxMajor > MAX_FIREFOX_MAJOR) return true

        val safariMajor = Regex("""Version/(\d+)\.\d+""")
            .find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
        if (safariMajor != null && safariMajor > MAX_SAFARI_MAJOR) return true

        return false
    }

    private fun buildMediaSource(
        stream: StreamItem,
        subtitles: List<SubtitleItem>
    ): MediaSource {
        val uri = Uri.parse(stream.url)
        val dataSourceFactory = buildDataSourceFactory(stream)
        val validSubtitles = subtitles.filter { subtitle ->
            subtitle.url.isNotBlank() && subtitle.mimeType.isNotBlank() &&
                (subtitle.url.startsWith("http://", ignoreCase = true) ||
                    subtitle.url.startsWith("https://", ignoreCase = true) ||
                    subtitle.url.startsWith("file://", ignoreCase = true))
        }

        // Media3 1.4+ expects sideloaded SRT/VTT tracks to be supplied through
        // MediaItem.SubtitleConfiguration and DefaultMediaSourceFactory. The
        // old SingleSampleMediaSource + MergingMediaSource path can produce an
        // invalid merged timeline and turn a valid video into a fatal playback
        // error when a local SubDL subtitle is added.
        //
        // Header-free subtitles cover SubDL's downloaded cache files and can
        // use the supported factory path. Keep the manual merge below for
        // sniffed remote subtitle tracks that require per-track headers.
        if (validSubtitles.isNotEmpty() && validSubtitles.all { it.headers.isEmpty() }) {
            val subtitleConfigurations = validSubtitles.mapIndexed { index, subtitle ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setMimeType(subtitle.mimeType)
                    .apply {
                        subtitle.language?.let { setLanguage(it) }
                        subtitle.label?.let { setLabel(it) }
                        if (index == 0) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    }
                    .build()
            }
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .apply { setDetectedMimeType(stream) }
                .setSubtitleConfigurations(subtitleConfigurations)
                .build()
            return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply { setDetectedMimeType(stream) }
            .build()
        val videoSource = when {
            isHls(stream) -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            isDash(stream) -> DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
        if (validSubtitles.isEmpty()) return videoSource

        val subtitleSources = validSubtitles.mapIndexed { index, subtitle ->
            val subtitleUri = Uri.parse(subtitle.url)
            val subtitleDataSource = if (
                subtitleUri.scheme.equals("http", ignoreCase = true) ||
                subtitleUri.scheme.equals("https", ignoreCase = true)
            ) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(REAL_BROWSER_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(subtitle.headers)
                DefaultDataSource.Factory(appContext, httpFactory)
            } else {
                DefaultDataSource.Factory(appContext)
            }
            val configuration = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(subtitle.mimeType)
                .apply {
                    subtitle.language?.let { setLanguage(it) }
                    subtitle.label?.let { setLabel(it) }
                    if (index == 0) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                }
                .build()
            SingleSampleMediaSource.Factory(subtitleDataSource)
                .createMediaSource(configuration, C.TIME_UNSET)
        }
        return MergingMediaSource(videoSource, *subtitleSources.toTypedArray())
    }

    private fun MediaItem.Builder.setDetectedMimeType(stream: StreamItem) {
        when {
            isHls(stream) -> setMimeType(MimeTypes.APPLICATION_M3U8)
            isDash(stream) -> setMimeType(MimeTypes.APPLICATION_MPD)
            isMatroska(stream) -> setMimeType(MimeTypes.VIDEO_MATROSKA)
        }
    }

    private fun isMatroska(stream: StreamItem): Boolean {
        if (stream.provider.equals("DahmerMovies", ignoreCase = true)) return true
        if (stream.type.equals("mkv", ignoreCase = true) ||
            stream.type.equals("matroska", ignoreCase = true)
        ) return true
        if (stream.mimeType.equals(MimeTypes.VIDEO_MATROSKA, ignoreCase = true) ||
            stream.mimeType.equals("application/x-matroska", ignoreCase = true)
        ) return true
        val path = stream.url.substringBefore('?').lowercase()
        if (path.endsWith(".mkv") || path.endsWith(".mka")) return true

        // Download providers such as HDHub4u can return an extensionless
        // Googleusercontent URL while the title still carries the original
        // `.mkv` filename. Use that metadata to select Media3's Matroska
        // extractor explicitly instead of relying only on URL sniffing.
        val metadata = "${stream.title} ${stream.name}".lowercase()
        return metadata.contains(".mkv") || metadata.contains("matroska")
    }

    private fun isDash(stream: StreamItem): Boolean =
        stream.type.equals("dash", ignoreCase = true) ||
            stream.mimeType.equals("application/dash+xml", ignoreCase = true) ||
            stream.url.substringBefore('?').lowercase().endsWith(".mpd")

    /**
     * HLS detection is more permissive than the obvious "URL ends in
     * .m3u8" rule because the server is also returning proxy URLs of the
     * form `…/m3u8-proxy?url=<encoded real m3u8>&headers=<json>`. The
     * `.m3u8` reference is buried in the query string in that case, so
     * `endsWith(".m3u8")` would miss it and the engine would feed an HLS
     * playlist to [ProgressiveMediaSource], which then throws
     * [androidx.media3.exoplayer.source.UnrecognizedInputFormatException].
     *
     * Signals treated as HLS:
     *   - the path or query string contains `.m3u8` (covers `…/master.m3u8`,
     *     `…/playlist.m3u8?token=…`, and `…/m3u8-proxy?url=…master.m3u8&…`)
     *   - the path ends with `/m3u8-proxy` (an explicit HLS proxy endpoint)
     */
    private fun isHls(stream: StreamItem): Boolean {
        if (stream.type.equals("hls", ignoreCase = true)) return true
        if (
            stream.mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
            stream.mimeType.equals("application/x-mpegURL", ignoreCase = true)
        ) return true

        val lower = stream.url.lowercase()
        if (lower.contains(".m3u8")) return true
        if (lower.contains("/m3u8-proxy") || lower.contains("/m3u8_proxy")) return true
        // Hexa's CDN serves HLS playlists through extensionless signed proxy
        // URLs. Deployed backends predating the explicit type field still
        // need to play correctly.
        if (
            stream.provider.equals("hexa", ignoreCase = true) &&
            lower.contains("oogachakacdn.store/proxy")
        ) return true
        if (
            stream.provider.equals("vixsrc", ignoreCase = true) &&
            lower.contains("vixsrc.to/playlist/")
        ) return true
        return false
    }

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setLoadControl(buildLoadControl())
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()

    init {
        // Establish a sensible default audio track selection: prefer
        // universally-supported codecs (AAC) over surround codecs the
        // device's AudioTrack may not be able to initialise (E-AC3 5.1).
        // Without this, the default TrackSelectionParameters lets the
        // player pick the *first* audio rendition it finds in the
        // manifest, which for HLS playlists that list the E-AC3 5.1
        // rendition first would force-feed E-AC3 to devices whose
        // AudioTrack cannot initialize 5.1 surround and raise
        // ERROR_CODE_AUDIO_TRACK_INIT_FAILED (5001).
        val defaults = player.trackSelectionParameters.buildUpon()
            .setSelectAudioByDefault(true)
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_AAC,
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_MPEG,
                MimeTypes.AUDIO_MPEG_L2,
                MimeTypes.AUDIO_RAW
            )
            .build()
        player.trackSelectionParameters = defaults
        Log.i(
            TAG,
            "Default TrackSelectionParameters initialised with preferred audio " +
                "MIME types: AAC > AC3 > E-AC3 > MPEG > MPEG-L2 > RAW"
        )
    }

    /**
     * Builds a [DefaultLoadControl] that keeps 3 minutes of media
     * buffered ahead of the playhead. The 3-minute window means
     * network hiccups have to last longer than 3 minutes before the
     * player has to rebuffer, which is a reasonable trade-off on a TV
     * with stable Wi-Fi.
     *
     * Settings:
     *  - `minBufferMs` = 10s — maintain at least 10s of buffered media.
     *  - `maxBufferMs` = 180s — the buffer fills up to 3 minutes and
     *    stops there. This is the "3 min buffer" the operator asked
     *    for.
     *  - `bufferForPlaybackMs` = 2.5s — after a rebuffer event, the
     *    player resumes once 2.5s is back in the buffer. Standard.
     *  - `bufferForPlaybackAfterRebufferMs` = 5s — same as above for
     *    subsequent rebuffers. Standard.
     */
    private fun buildLoadControl(): DefaultLoadControl {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,                    // minBufferMs
                MAX_BUFFER_MS,                    // maxBufferMs (3 minutes)
                BUFFER_FOR_PLAYBACK_MS,           // bufferForPlaybackMs
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS // bufferForPlaybackAfterRebufferMs
            )
            // High-bitrate 1440p/2160p streams can hit Media3's default byte
            // target well before the requested time buffer. Keep loading
            // until the time thresholds above are satisfied.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        Log.i(
            TAG,
            "LoadControl: min=${MIN_BUFFER_MS}ms max=${MAX_BUFFER_MS}ms " +
                "playback=${BUFFER_FOR_PLAYBACK_MS}ms " +
                "afterRebuffer=${BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS}ms"
        )
        return loadControl
    }

    /** Most recent URL we asked the player to load. Used to enrich error logs. */
    @Volatile
    private var currentUrl: String = ""
    private var currentSourceIsPlaylist = false
    private var highestVideoTrackApplied = false
    private var autoAudioTrackApplied = false

    /** Invoked on fatal playback errors. The argument is a stable error code name. */
    var onError: ((String) -> Unit)? = null

    /** Invoked with the new [Player.STATE_*] constant. */
    var onPlaybackStateChanged: ((Int) -> Unit)? = null

    /** Invoked when playback actually starts or stops rendering media. */
    var onIsPlayingChanged: ((Boolean) -> Unit)? = null

    /** Invoked whenever the available track list changes (manifest parsed, etc.). */
    var onTracksChanged: ((Tracks) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.i(TAG, "Playback state changed: $state")
                onPlaybackStateChanged?.invoke(state)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onIsPlayingChanged?.invoke(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                val completeErrorMessage = buildString {
                    append("Error code: ${error.errorCode}\n")
                    append("Error name: ${error.errorCodeName}\n")
                    append("URL: ${sanitize(currentUrl)}")
                    error.message?.takeIf { it.isNotBlank() }?.let {
                        append("\nMessage: $it")
                    }
                    error.cause?.let { cause ->
                        append("\nCause: ${cause.javaClass.simpleName}")
                        cause.message?.takeIf { it.isNotBlank() }?.let {
                            append("\nCause message: $it")
                        }
                    }
                }
                Log.w(TAG, completeErrorMessage, error)
                onError?.invoke(completeErrorMessage)
            }

            override fun onTracksChanged(tracks: Tracks) {
                Log.i(
                    TAG,
                    "Tracks changed: ${tracks.groups.size} groups " +
                        "(video=${countByType(tracks, C.TRACK_TYPE_VIDEO)}, " +
                        "audio=${countByType(tracks, C.TRACK_TYPE_AUDIO)}, " +
                        "text=${countByType(tracks, C.TRACK_TYPE_TEXT)})"
                )
                selectHighestResolutionVideoTrack(tracks)
                applyAutoAudioTrack(tracks)
                onTracksChanged?.invoke(tracks)
            }
        })
    }

    private fun countByType(tracks: Tracks, type: Int): Int =
        tracks.groups.count { it.type == type }

    /**
     * Sets the audio track to "auto" for a newly loaded HLS/DASH playlist.
     *
     * Behavior:
     *  - Runs once per media source. A subsequent user override from the
     *    Tracks dialog ("Audio > Track N") is preserved for the rest of
     *    the source's lifetime because the one-shot guard re-arms only in
     *    [play] when a brand new source is loaded.
     *  - Clears any audio track override that may have been carried over
     *    from a previous playback so Media3 is free to pick a compatible
     *    audio rendition from the current manifest.
     *  - Re-asserts the preferred audio MIME type order (AAC > AC3 >
     *    E-AC3) so when the manifest lists the E-AC3 5.1 rendition first
     *    the player falls back to the AAC stereo rendition rather than
     *    failing with ERROR_CODE_AUDIO_TRACK_INIT_FAILED (5001).
     *  - Re-enables audio in case a previous Track Selection dialog left
     *    it disabled.
     *
     * Mirrors [selectHighestResolutionVideoTrack] in scope and lifetime.
     */
    private fun applyAutoAudioTrack(tracks: Tracks) {
        if (!currentSourceIsPlaylist || autoAudioTrackApplied) return
        // No-op when the manifest exposes zero audio tracks; some streams
        // (e.g. video-only promo clips) legitimately have no audio group.
        val hasAudio = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        if (!hasAudio) {
            Log.i(TAG, "applyAutoAudioTrack: no audio groups in manifest, skipping")
            return
        }
        autoAudioTrackApplied = true
        val current = player.trackSelectionParameters
        val builder = current.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setSelectAudioByDefault(true)
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_AAC,
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_MPEG,
                MimeTypes.AUDIO_MPEG_L2,
                MimeTypes.AUDIO_RAW
            )
        player.trackSelectionParameters = builder.build()
        val chosen = describeCurrentAudioTrack(tracks)
        Log.i(
            TAG,
            "Applied Playlist audio track=auto (preferred AAC > AC3 > E-AC3). " +
                "Active audio track after selection: ${chosen ?: "<none>"}"
        )
    }

    /**
     * Returns a short description of the currently selected audio track
     * for logging, or null if no track is selected.
     */
    private fun describeCurrentAudioTrack(tracks: Tracks): String? {
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO } ?: return null
        val selectedIndex = (0 until group.length).firstOrNull { group.isTrackSelected(it) }
            ?: return null
        val format = group.getTrackFormat(selectedIndex)
        val language = format.language?.takeIf { it.isNotBlank() } ?: "und"
        val channels = format.channelCount.takeIf { it > 0 }?.let { "${it}ch" } ?: "?ch"
        val mime = format.sampleMimeType ?: format.codecs ?: "?"
        return "$language $channels $mime"
    }

    /**
     * Pins a newly loaded HLS/DASH playlist to its highest-resolution
     * supported video rendition. This runs once per media source, leaving
     * subsequent user choices from the Tracks dialog untouched.
     */
    private fun selectHighestResolutionVideoTrack(tracks: Tracks) {
        if (!currentSourceIsPlaylist || highestVideoTrackApplied) return

        val candidates = buildList {
            tracks.groups.forEach { group ->
                if (group.type != C.TRACK_TYPE_VIDEO) return@forEach
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        VideoCandidate(
                            trackGroup = group.mediaTrackGroup,
                            trackIndex = trackIndex,
                            width = format.width.coerceAtLeast(0),
                            height = format.height.coerceAtLeast(0),
                            bitrate = format.bitrate.coerceAtLeast(0)
                        )
                    )
                }
            }
        }
        val highest = candidates.maxWithOrNull(
            compareBy<VideoCandidate> { it.pixelCount }
                .thenBy { it.height }
                .thenBy { it.width }
                .thenBy { it.bitrate }
        ) ?: return

        highestVideoTrackApplied = true
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setOverrideForType(
                TrackSelectionOverride(highest.trackGroup, highest.trackIndex)
            )
            .build()
        Log.i(
            TAG,
            "Selected highest playlist video track: " +
                "${highest.width}x${highest.height} bitrate=${highest.bitrate}"
        )
    }

    /**
     * Begin playback of [stream]. Replaces any current item. Per-stream
     * headers are baked into the DataSource for this playback only.
     */
    fun play(
        stream: StreamItem,
        startPositionMs: Long = 0L,
        subtitles: List<SubtitleItem> = emptyList()
    ) {
        val normalizedUrl = normalizePlaybackUrl(stream)
        val playbackStream = if (normalizedUrl == stream.url) stream else stream.copy(url = normalizedUrl)
        currentUrl = normalizedUrl
        val scheme = normalizedUrl.substringBefore(':').uppercase()
        val isHlsStream = isHls(playbackStream)
        currentSourceIsPlaylist = isHlsStream || isDash(playbackStream)
        highestVideoTrackApplied = false
        autoAudioTrackApplied = false
        val sourceType = when {
            isHlsStream -> "HlsMediaSource"
            isDash(playbackStream) -> "DashMediaSource"
            else -> "ProgressiveMediaSource"
        }
        val host = runCatching { Uri.parse(normalizedUrl).host }.getOrNull() ?: "?"
        val requestHeaders = playbackHeaders(playbackStream)
        Log.i(
            TAG,
            "Engine.play provider=${playbackStream.provider.ifBlank { "?" }} " +
                "quality=${playbackStream.quality} scheme=$scheme isHls=$isHlsStream " +
                "source=$sourceType host=$host url=$normalizedUrl headerCount=${requestHeaders.size}"
        )
        // Log the actual header values (truncated) so a 403 from the CDN
        // is diagnosable from logcat without needing to attach a debugger.
        requestHeaders.forEach { (name, value) ->
            Log.i(
                PROVIDER_TAG,
                "  header $name=${
                    when {
                        name.equals("Cookie", ignoreCase = true) -> "<redacted>"
                        value.length > 120 -> value.substring(0, 117) + "..."
                        else -> value
                    }
                }"
            )
        }
        val source = buildMediaSource(playbackStream, subtitles)
        player.setMediaSource(source)
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Seek relative to the current position. ExoPlayer's [Player] interface
     * does not expose a `seekBy` method, so we compute the new absolute
     * position ourselves and clamp it to a non-negative value.
     */
    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(target)
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    /**
     * Apply a new [TrackSelectionParameters] to the underlying player. Used
     * by the track selection dialog to switch video / audio / subtitle
     * tracks at runtime. Pass a value returned by [buildSelectionForOverride]
     * (or any other builder pipeline rooted at the current parameters).
     */
    fun applyTrackSelectionParameters(parameters: TrackSelectionParameters) {
        Log.i(TAG, "Applying new track selection parameters: $parameters")
        player.trackSelectionParameters = parameters
    }

    /** Returns the player's current track selection parameters. */
    fun currentTrackSelectionParameters(): TrackSelectionParameters =
        player.trackSelectionParameters

    /** Returns the player's current track list. */
    fun currentTracks(): Tracks = player.currentTracks

    fun release() {
        runCatching { player.release() }
    }

    /** Redacts the query string of a URL for logging. */
    private fun sanitize(url: String): String =
        url.substringBefore('?').let { base ->
            if (url.length > base.length) "$base?<redacted>" else url
        }

    companion object {
        private const val TAG = "KiduyuLitePlayer"
        private const val PROVIDER_TAG = "KiduyuLiteProvider"

        /**
         * A real, current Chrome-on-Windows UA. Used as the engine's
         * baseline UA and as the replacement for obviously-fake UAs
         * coming from providers (`Chrome/150`, `Chrome/999`, etc.).
         * Update this when Chrome ships a new major release and CDNs
         * start rejecting the previous one.
         */
        private const val REAL_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        /**
         * Default User-Agent sent on every HTTP request. Most CDNs
         * reject the platform-default `Dalvik/...` UA, and
         * `KiduyuTVLite/...` doesn't look like a browser, so we send a
         * plausible Chrome-on-Windows string as the baseline. The
         * stream's `headers` map (set via
         * [DefaultHttpDataSource.Factory.setDefaultRequestProperties])
         * overrides this when present.
         */
        private const val USER_AGENT = REAL_BROWSER_USER_AGENT
        private const val DAHMER_MOVIES_REFERER = "https://a.111477.xyz/"
        private const val DAHMER_MOVIES_RAW_PREFIX =
            "https://p.111477.xyz/bulk?u=https://a.111477.xyz"
        private const val DAHMER_MOVIES_ENCODED_PREFIX =
            "https://p.111477.xyz/bulk?u=https%3A%2F%2Fa.111477.xyz"

        /**
         * Cap above which a `Chrome/MAJOR` claim is considered fake.
         * Real stable Chrome in mid-2026 is ~130-140. Bump this when
         * Chrome's stable channel crosses 145.
         */
        private const val MAX_CHROME_MAJOR = 140

        /**
         * Cap above which a `Firefox/MAJOR` claim is considered fake.
         * Mirrors [MAX_CHROME_MAJOR].
         */
        private const val MAX_FIREFOX_MAJOR = 140

        /**
         * Cap above which a `Version/MAJOR.x` (Safari) claim is
         * considered fake. Safari 18 was current in 2025.
         */
        private const val MAX_SAFARI_MAJOR = 25

        private const val HTTP_CONNECT_TIMEOUT_MS = 15_000
        private const val HTTP_READ_TIMEOUT_MS = 30_000
        private const val SEEK_STEP_MS = 10_000L

        /**
         * 3 minutes of media buffered ahead of the playhead. TV-class
         * devices have stable Wi-Fi and benefit from a generous buffer;
         * networks that drop out for <3 min are absorbed without
         * rebuffering.
         */
        private const val MAX_BUFFER_MS = 3 * 60 * 1000  // 180_000

        /**
         * Maintain at least 10 seconds of buffered media. Initial playback
         * is still controlled separately by BUFFER_FOR_PLAYBACK_MS.
         */
        private const val MIN_BUFFER_MS = 10 * 1000       //  10_000

        /**
         * After a rebuffer event, the player resumes once 2.5s is
         * back in the buffer. Standard ExoPlayer default.
         */
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500

        /**
         * Same as [BUFFER_FOR_PLAYBACK_MS] but for subsequent
         * rebuffers. Standard ExoPlayer default.
         */
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
    }

    private data class VideoCandidate(
        val trackGroup: androidx.media3.common.TrackGroup,
        val trackIndex: Int,
        val width: Int,
        val height: Int,
        val bitrate: Int
    ) {
        val pixelCount: Long = width.toLong() * height.toLong()
    }
}
