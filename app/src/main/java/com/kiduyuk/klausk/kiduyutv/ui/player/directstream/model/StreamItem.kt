package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model

/**
 * One stream returned by the kiduyuTv_providers server.
 *
 * With `enableProxy=false` on the server, the [url] points directly at the
 * upstream CDN. The [headers] map (typically `Referer` + `User-Agent`) must
 * be attached when fetching the manifest and segments; [PlayerEngine] does
 * this at the DataSource level.
 */
data class StreamItem(
    /** Detailed backend label. Used only when [name] is missing. */
    val title: String,
    /** Preferred stream-picker label, e.g. `"Vidfast vRapid"`. */
    val name: String = "",
    /** Direct HTTPS URL to the upstream HLS playlist or progressive file. */
    val url: String,
    /** Quality hint, e.g. `"1080p"`, `"720p"`, `"Auto"`. */
    val quality: String,
    /** Optional audio/subtitle language or variant label supplied by a provider. */
    val language: String = "",
    /** Originating provider key, e.g. `"vidfast"`, `"vixsrc"`. */
    val provider: String,
    /** Optional backend media hint such as `"hls"` or `"progressive"`. */
    val type: String = "",
    /** Optional MIME type, for example `application/vnd.apple.mpegurl`. */
    val mimeType: String = "",
    /** HTTP headers to attach when fetching the manifest and segments. */
    val headers: Map<String, String> = emptyMap(),
    /**
     * Provider-supplied side-loaded subtitle tracks for this exact stream.
     * These are kept with the stream because a source switch can require a
     * different caption host, headers, or language list.
     */
    val subtitles: List<SubtitleItem> = emptyList(),
    /**
     * Mutable validation status. Set to `true` after a successful HEAD/Range
     * probe confirms the upstream is reachable and returns 2xx. The
     * `StreamSelectionDialog` reads this flag to render the "stream ok"
     * badge for working sources.
     */
    var isValid: Boolean = false,
    /** `true` while a background validity check is in progress for this stream. */
    var isChecking: Boolean = false,
    /**
     * Set to `true` only when a successful probe returns a clearly non-media
     * document body, such as an HTML or JSON error page. Generic or incomplete
     * CDN headers are not enough to mark a stream failed because valid media
     * endpoints can still play through Media3 without self-describing headers.
     * A stream can be both `isFailed` and `isValid` is `false` — they are not
     * mutually exclusive.
     */
    var isFailed: Boolean = false,
    /**
     * Last observed HTTP status code from a [com.kiduyuk.klausk.kiduyutv.ui.player
     * .directstream.playback.StreamValidator] probe. Used by the player to
     * detect Cloudflare-style 403 challenges before launching ExoPlayer.
     * `null` until the first probe completes.
     */
    var httpStatusCode: Int? = null
)

/**
 * Top-level response from `GET /api/streams/{type}/{tmdbId}`. Used for
 * diagnostics and resume in the future; today only the [streams] list is
 * consumed.
 */
data class StreamResponse(
    val tmdbId: Int,
    val imdbId: String?,
    val streams: List<StreamItem>
)
