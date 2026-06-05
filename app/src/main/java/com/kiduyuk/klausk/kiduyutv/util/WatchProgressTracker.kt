package com.kiduyuk.klausk.kiduyutv.util

import android.util.Log

/**
 * Unified Watch Progress Tracker for all supported streaming platforms.
 *
 * Platforms and their message protocols:
 *
 * STRING message (event.data is a JSON string):
 *   - Videasy : id, type, progress%, timestamp(s), duration(s), season, episode
 *   - Vidking : id, type, progress%, timestamp(s), duration(s), season, episode
 *
 * MEDIA_DATA object message → saved to localStorage, polled every 15 s:
 *   - Vidrock  (https://vidrock.ru)       → localStorage key: vidRockProgress  (Array format)
 *   - Vidlink  (https://vidlink.pro)      → localStorage key: vidLinkProgress  (Object keyed by id)
 *   - Vidfast  (https://vidfast.*)        → localStorage key: vidFastProgress  (Object keyed by t<id>/m<id>)
 *   - Vidnest  (https://vidnest.fun)      → localStorage key: vidNestProgress  (Object keyed by id)
 *   - Vidup    (https://vidup.to)         → localStorage key: vidUpProgress    (Object keyed by t<id>/m<id>)
 *   - Peachify (https://peachify.top)     → localStorage key: peachifyProgress (Object keyed by id)
 *                                           also sends PLAYER_EVENT with currentTime/duration
 *
 * OBJECT message with mediaId field (no storage, direct event only):
 *   - Vidcore  (https://vidcore.net)      → mediaId, mediaType, season, episode
 */
object WatchProgressTracker {

    private const val TAG = "WatchProgressTracker"

    /**
     * Parsed progress from any platform, normalised to milliseconds.
     */
    data class ProgressData(
        val contentId: Int,
        val contentType: String,       // "movie" or "tv"
        val currentPosition: Long,     // ms
        val duration: Long,            // ms
        val season: Int? = null,
        val episode: Int? = null,
        val progressPercent: Double = 0.0
    )

    /**
     * Supported platforms: origin(s) and localStorage key where applicable.
     */
    enum class Platform(val origins: List<String>, val storageKey: String?) {
        VIDEASY(emptyList(), null),          // string postMessage only, no origin restriction
        VIDKING(emptyList(), null),          // string postMessage only, no origin restriction
        VIDROCK(listOf("https://vidrock.ru"), "vidRockProgress"),
        VIDLINK(listOf("https://vidlink.pro"), "vidLinkProgress"),
        VIDFAST(
            listOf(
                "https://vidfast.pro", "https://vidfast.in", "https://vidfast.io",
                "https://vidfast.me",  "https://vidfast.net", "https://vidfast.pm",
                "https://vidfast.xyz"
            ),
            "vidFastProgress"
        ),
        VIDNEST(listOf("https://vidnest.fun"), "vidNestProgress"),
        VIDUP(listOf("https://vidup.to"), "vidUpProgress"),
        VIDCORE(listOf("https://vidcore.net"), null),   // direct event only, no localStorage
        PEACHIFY(listOf("https://peachify.top"), "peachifyProgress")
    }

    // ── JavaScript generation ──────────────────────────────────────────────────

    /**
     * Returns an IIFE that installs a unified message listener in the WebView page.
     *
     * Responsibilities:
     *  1. Intercept postMessage events from all platforms and forward them to Android
     *     via window.AndroidProgressCallback.onProgressUpdate(json).
     *  2. On MEDIA_DATA events: store the payload to localStorage under the correct key,
     *     then immediately extract progress for the current content and forward it.
     *  3. Expose window.startProgressPolling(contentId, isTv, interval) so the Android
     *     side can ask the page to poll localStorage periodically.
     *
     * localStorage polling note:
     *   startProgressPolling is meant to be called from *within* the iframe page context
     *   (i.e. injected via evaluateJavascript when the iframe's own page is loaded), NOT
     *   from the outer wrapper page, because localStorage is per-origin.
     */
    fun generateUnifiedListenerScript(): String {
        return """
            (function() {
                'use strict';

                // ── Origin lists ──────────────────────────────────────────────
                // Exact-match origins for MEDIA_DATA / PLAYER_EVENT platforms.
                const mediaDataOrigins = [
                    'https://vidrock.ru',
                    'https://vidlink.pro',
                    'https://vidfast.pro', 'https://vidfast.in', 'https://vidfast.io',
                    'https://vidfast.me',  'https://vidfast.net','https://vidfast.pm',
                    'https://vidfast.xyz',
                    'https://vidnest.fun',
                    'https://vidup.to',
                    'https://peachify.top'
                ];

                // Storage key per hostname (only MEDIA_DATA platforms).
                const storageKeyMap = {
                    'vidrock.ru':    'vidRockProgress',
                    'vidlink.pro':   'vidLinkProgress',
                    'vidfast.pro':   'vidFastProgress',
                    'vidfast.in':    'vidFastProgress',
                    'vidfast.io':    'vidFastProgress',
                    'vidfast.me':    'vidFastProgress',
                    'vidfast.net':   'vidFastProgress',
                    'vidfast.pm':    'vidFastProgress',
                    'vidfast.xyz':   'vidFastProgress',
                    'vidnest.fun':   'vidNestProgress',
                    'vidup.to':      'vidUpProgress',
                    'peachify.top':  'peachifyProgress'
                };

                // ── Helpers ───────────────────────────────────────────────────

                function getStorageKey(origin) {
                    try { return storageKeyMap[new URL(origin).hostname] || null; }
                    catch (e) { return null; }
                }

                /**
                 * Send normalised progress to Android.
                 * AndroidProgressCallback is a @JavascriptInterface Java object — its
                 * methods must be called explicitly, not as a plain function invocation.
                 */
                function sendToAndroid(data) {
                    try {
                        if (window.AndroidProgressCallback &&
                            window.AndroidProgressCallback.onProgressUpdate) {
                            window.AndroidProgressCallback.onProgressUpdate(JSON.stringify(data));
                        } else {
                            window.parent.postMessage({ type: 'KIDUYU_PROGRESS', data: data }, '*');
                        }
                    } catch (e) {
                        console.log('[KiduyuTV] sendToAndroid error:', e);
                    }
                }

                /**
                 * Look up the current content in localStorage for a given platform key.
                 *
                 * Storage formats differ per platform:
                 *  - Vidrock  : Array  → find by item.id
                 *  - Vidlink / Vidnest / Peachify : Object keyed by numeric id string
                 *  - Vidfast / Vidup   : Object keyed by "t<id>" (tv) or "m<id>" (movie)
                 *
                 * show_progress is a map of "s<S>e<E>" keys, each containing
                 * { season, episode, progress: { watched, duration } }.
                 * last_season_watched / last_episode_watched track the latest episode.
                 */
                function extractFromStorage(storageKey, contentId, isTv) {
                    try {
                        var raw = localStorage.getItem(storageKey);
                        if (!raw) return null;

                        var stored = JSON.parse(raw);
                        var item = null;

                        if (Array.isArray(stored)) {
                            // Vidrock: array of items
                            for (var i = 0; i < stored.length; i++) {
                                if (stored[i].id == contentId) { item = stored[i]; break; }
                            }
                        } else {
                            // Object: try prefixed key first (Vidfast/Vidup), then plain id
                            var prefixKey = (isTv ? 't' : 'm') + contentId;
                            item = stored[prefixKey] || stored[contentId] || stored[String(contentId)] || null;

                            if (!item) {
                                // Fallback: scan all values
                                var keys = Object.keys(stored);
                                for (var k = 0; k < keys.length; k++) {
                                    if (stored[keys[k]].id == contentId) { item = stored[keys[k]]; break; }
                                }
                            }
                        }

                        if (!item || !item.progress) return null;

                        var watched  = item.progress.watched  || 0;
                        var duration = item.progress.duration || 0;
                        var season   = null;
                        var episode  = null;

                        if (isTv) {
                            // Prefer the most-recently-updated show_progress entry
                            if (item.show_progress) {
                                var epKeys = Object.keys(item.show_progress);
                                if (epKeys.length > 0) {
                                    // Find the entry with the highest last_updated (or use last key)
                                    var latestEntry = item.show_progress[epKeys[0]];
                                    for (var e = 1; e < epKeys.length; e++) {
                                        var entry = item.show_progress[epKeys[e]];
                                        if ((entry.last_updated || 0) > (latestEntry.last_updated || 0)) {
                                            latestEntry = entry;
                                        }
                                    }
                                    // Use this episode's own progress if available
                                    if (latestEntry.progress) {
                                        watched  = latestEntry.progress.watched  || watched;
                                        duration = latestEntry.progress.duration || duration;
                                    }
                                    season  = latestEntry.season;
                                    episode = latestEntry.episode;
                                }
                            }
                            // Fall back to top-level last_season/episode_watched fields
                            if (!season)  season  = item.last_season_watched;
                            if (!episode) episode = item.last_episode_watched;

                            season  = season  ? parseInt(season,  10) : null;
                            episode = episode ? parseInt(episode, 10) : null;
                        }

                        return {
                            id:       contentId,
                            type:     isTv ? 'tv' : 'movie',
                            position: Math.round(watched  * 1000),
                            duration: Math.round(duration * 1000),
                            season:   season,
                            episode:  episode,
                            progress: duration > 0 ? (watched / duration) * 100 : 0
                        };
                    } catch (e) {
                        console.log('[KiduyuTV] extractFromStorage error:', e);
                        return null;
                    }
                }

                // ── localStorage polling ──────────────────────────────────────
                // Must be called from *within* the iframe page (same origin as the player)
                // so that localStorage refers to the player's own storage.

                function startProgressPolling(contentId, isTv, pollInterval) {
                    pollInterval = pollInterval || 15000;
                    var storageKey = getStorageKey(window.location.origin);
                    if (!storageKey) {
                        console.log('[KiduyuTV] Polling not available for origin:', window.location.origin);
                        return;
                    }

                    var lastPosition = -1;
                    console.log('[KiduyuTV] Polling started for', contentId, 'key:', storageKey);

                    setInterval(function() {
                        var progress = extractFromStorage(storageKey, contentId, isTv);
                        if (progress && progress.position !== lastPosition) {
                            lastPosition = progress.position;
                            sendToAndroid(progress);
                        }
                    }, pollInterval);
                }

                window.startProgressPolling = startProgressPolling;

                // ── Message event listener ────────────────────────────────────

                window.addEventListener('message', function(event) {
                    var origin = event.origin;
                    var data   = event.data;

                    // ── 1. Videasy / Vidking: string JSON message ─────────────
                    // No origin restriction — these players send a plain JSON string.
                    if (typeof data === 'string') {
                        try {
                            var p = JSON.parse(data);
                            // Must have id and either progress or timestamp to be valid
                            if (p && p.id && (p.progress !== undefined || p.timestamp !== undefined)) {
                                sendToAndroid({
                                    id:       p.id,
                                    type:     p.type     || 'movie',
                                    position: (p.timestamp || 0) * 1000,
                                    duration: (p.duration  || 0) * 1000,
                                    season:   p.season   || null,
                                    episode:  p.episode  || null,
                                    progress: p.progress || 0
                                });
                            }
                        } catch (e) { /* not JSON — ignore */ }
                        return;
                    }

                    if (!data || typeof data !== 'object') return;

                    // ── 2. Vidcore: object with mediaId (exact origin) ────────
                    if (origin === 'https://vidcore.net') {
                        if (data.mediaId) {
                            sendToAndroid({
                                id:      data.mediaId,
                                type:    data.mediaType || 'movie',
                                position: 0,
                                duration: 0,
                                season:  data.season  || null,
                                episode: data.episode || null,
                                progress: 0
                            });
                        }
                        return;
                    }

                    // ── 3. MEDIA_DATA platforms (exact origin check) ──────────
                    if (mediaDataOrigins.indexOf(origin) === -1) return;

                    if (data.type === 'MEDIA_DATA' && data.data) {
                        var storageKey = getStorageKey(origin);
                        if (storageKey) {
                            try {
                                localStorage.setItem(storageKey, JSON.stringify(data.data));
                            } catch (e) {
                                console.log('[KiduyuTV] localStorage write failed:', e);
                            }
                        }
                        // Immediately extract and forward progress for the tracked content
                        if (window.currentContentId && storageKey) {
                            var isTv = (window.currentContentType === 'tv');
                            var progress = extractFromStorage(storageKey, window.currentContentId, isTv);
                            if (progress) sendToAndroid(progress);
                        }
                    }

                    // ── 4. Peachify PLAYER_EVENT ──────────────────────────────
                    if (origin === 'https://peachify.top' &&
                        data.type === 'PLAYER_EVENT' && data.data) {
                        var pd = data.data;
                        if (pd.currentTime !== undefined && pd.duration !== undefined) {
                            sendToAndroid({
                                id:       window.currentContentId || 0,
                                type:     window.currentContentType || 'movie',
                                position: Math.round(pd.currentTime * 1000),
                                duration: Math.round(pd.duration    * 1000),
                                season:   window.currentSeason  || null,
                                episode:  window.currentEpisode || null,
                                progress: pd.duration > 0 ? (pd.currentTime / pd.duration) * 100 : 0
                            });
                        }
                    }
                });

                // Notify Android that the listener is installed
                console.log('[KiduyuTV] Progress Listener initialized');
                if (window.AndroidProgressCallback &&
                    window.AndroidProgressCallback.onProgressUpdate) {
                    window.AndroidProgressCallback.onProgressUpdate(
                        JSON.stringify({ type: 'LISTENER_READY' })
                    );
                }
            })();
        """.trimIndent()
    }

    /**
     * Returns a script that starts localStorage polling for a specific content.
     *
     * This must be evaluated in the same page context as the player iframe so that
     * localStorage refers to the player's origin. In direct-URL mode (no wrapper page),
     * evaluateJavascript injects directly into the player page — correct.
     * In iframe mode, polling is started via the iframe's onload (see generateIframeHtml).
     */
    fun generateStartPollingScript(tmdbId: Int, isTv: Boolean): String {
        return """
            (function() {
                function tryStartPolling() {
                    if (window.startProgressPolling) {
                        window.startProgressPolling($tmdbId, $isTv, 15000);
                    } else {
                        console.log('[KiduyuTV] startProgressPolling not available on this origin');
                    }
                }
                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    tryStartPolling();
                } else {
                    document.addEventListener('DOMContentLoaded', tryStartPolling);
                }
            })();
        """.trimIndent()
    }

    /**
     * Builds the full HTML wrapper page for iframe mode.
     *
     * The unified listener script is embedded once (single source of truth).
     * Polling is triggered by the iframe's onload event — no fixed delay needed.
     *
     * Note: the listener is installed on the *outer* page so it can receive
     * postMessage events bubbled up from the iframe. localStorage polling,
     * however, must run inside the iframe's own context; for platforms that
     * rely solely on localStorage (not postMessage), polling via evaluateJavascript
     * into the iframe page after load is more reliable.
     */
    fun generateIframeHtml(
        baseUrl: String,
        iframeUrl: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): String {
        val listenerScript = generateUnifiedListenerScript()

        return """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { background: #000; overflow: hidden; }
        iframe { width: 100vw; height: 100vh; border: none; }
    </style>
</head>
<body>
    <iframe id="playerFrame"
        src="$iframeUrl"
        allowfullscreen
        allow="autoplay; fullscreen"
        onload="onIframeLoaded()">
    </iframe>
    <script>
        window.currentContentId   = $tmdbId;
        window.currentContentType = '${if (isTv) "tv" else "movie"}';
        window.currentSeason      = ${season ?: 1};
        window.currentEpisode     = ${episode ?: 1};

        $listenerScript

        function onIframeLoaded() {
            if (window.startProgressPolling) {
                window.startProgressPolling($tmdbId, $isTv, 15000);
                console.log('[KiduyuTV] Polling started after iframe load, content: $tmdbId');
            }
        }
    </script>
</body>
</html>""".trimIndent()
    }

    // ── Kotlin-side parser ─────────────────────────────────────────────────────

    /**
     * Parses the JSON string delivered by WebAppInterface.onProgressUpdate().
     * Returns null for control messages (LISTENER_READY) or malformed data.
     */
    fun parseProgressData(jsonString: String): ProgressData? {
        return try {
            val json = com.google.gson.JsonParser.parseString(jsonString).asJsonObject

            // Control message — not progress data
            if (json.has("type") && json.get("type").asString == "LISTENER_READY") {
                Log.i(TAG, "[Parser] Progress listener ready")
                return null
            }

            val contentId = json.get("id")?.takeIf { !it.isJsonNull }?.asInt ?: return null
            val contentType = json.get("type")?.takeIf { !it.isJsonNull }?.asString ?: "movie"
            val isTv = contentType == "tv" || contentType == "anime"

            val position = json.get("position")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            val duration = json.get("duration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            val progress = json.get("progress")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0

            val season: Int? = if (isTv)
                json.get("season")?.takeIf { !it.isJsonNull }?.asInt
            else null

            val episode: Int? = if (isTv)
                json.get("episode")?.takeIf { !it.isJsonNull }?.asInt
            else null

            ProgressData(
                contentId      = contentId,
                contentType    = contentType,
                currentPosition = position,
                duration       = duration,
                season         = season,
                episode        = episode,
                progressPercent = progress
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Parser] Failed to parse: ${e.message} — input: $jsonString")
            null
        }
    }
}
