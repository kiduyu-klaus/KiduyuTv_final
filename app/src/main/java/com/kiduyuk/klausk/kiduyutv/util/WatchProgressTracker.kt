package com.kiduyuk.klausk.kiduyutv.util

import android.util.Log

/**
 * Unified Watch Progress Tracker that handles all streaming platforms.
 *
 * Platforms supported:
 * - Videasy
 * - Vidrock
 * - Vidlink
 * - Vidfast
 * - Vidking
 * - Vidnest
 * - Vidup
 * - Vidcore
 * - Peachify
 *
 * This class provides a unified interface for extracting watch progress from various
 * streaming providers using their postMessage API.
 */
object WatchProgressTracker {

    private const val TAG = "WatchProgressTracker"

    /**
     * Data class representing extracted watch progress from any platform.
     */
    data class ProgressData(
        val contentId: Int,
        val contentType: String, // "movie" or "tv"
        val currentPosition: Long, // in milliseconds
        val duration: Long, // in milliseconds
        val season: Int? = null,
        val episode: Int? = null,
        val progressPercent: Double = 0.0
    )

    /**
     * Supported streaming platforms with their origins and storage keys.
     */
    enum class Platform(val origins: List<String>, val storageKey: String) {
        VIDROCK(
            listOf("https://vidrock.ru"),
            "vidRockProgress"
        ),
        VIDLINK(
            listOf("https://vidlink.pro"),
            "vidLinkProgress"
        ),
        VIDFAST(
            listOf(
                "https://vidfast.pro",
                "https://vidfast.in",
                "https://vidfast.io",
                "https://vidfast.me",
                "https://vidfast.net",
                "https://vidfast.pm",
                "https://vidfast.xyz"
            ),
            "vidFastProgress"
        ),
        VIDNEST(
            listOf("https://vidnest.fun"),
            "vidNestProgress"
        ),
        VIDUP(
            listOf("https://vidup.to"),
            "vidUpProgress"
        ),
        PEACHIFY(
            listOf("https://peachify.top"),
            "peachifyProgress"
        )
    }

    /**
     * Generates a unified JavaScript event listener that handles all platforms.
     * This script should be injected into the WebView after page load.
     */
    fun generateUnifiedListenerScript(): String {
        return """
            (function() {
                // Allowed origins for all platforms
                const allowedOrigins = [
                    'https://vidrock.ru',
                    'https://vidlink.pro',
                    'https://vidfast.pro',
                    'https://vidfast.in',
                    'https://vidfast.io',
                    'https://vidfast.me',
                    'https://vidfast.net',
                    'https://vidfast.pm',
                    'https://vidfast.xyz',
                    'https://vidnest.fun',
                    'https://vidup.to',
                    'https://peachify.top'
                ];

                // Platform storage keys mapping
                const storageKeys = {
                    'vidrock.ru': 'vidRockProgress',
                    'vidlink.pro': 'vidLinkProgress',
                    'vidfast.pro': 'vidFastProgress',
                    'vidfast.in': 'vidFastProgress',
                    'vidfast.io': 'vidFastProgress',
                    'vidfast.me': 'vidFastProgress',
                    'vidfast.net': 'vidFastProgress',
                    'vidfast.pm': 'vidFastProgress',
                    'vidfast.xyz': 'vidFastProgress',
                    'vidnest.fun': 'vidNestProgress',
                    'vidup.to': 'vidUpProgress',
                    'peachify.top': 'peachifyProgress'
                };

                // Get the storage key for current origin
                function getStorageKey(origin) {
                    try {
                        const hostname = new URL(origin).hostname;
                        return storageKeys[hostname] || null;
                    } catch (e) {
                        return null;
                    }
                }

                // Send progress data to Android
                function sendProgressToAndroid(data) {
                    if (window.AndroidProgressCallback) {
                        window.AndroidProgressCallback(JSON.stringify(data));
                    } else {
                        // Fallback to postMessage for other listeners
                        try {
                            window.parent.postMessage({
                                type: 'KIDUYU_PROGRESS',
                                data: data
                            }, '*');
                        } catch (e) {
                            console.log('Could not send progress:', e);
                        }
                    }
                }

                // Extract and send progress from localStorage
                function extractProgressFromStorage(storageKey, contentId, isTv) {
                    try {
                        const storedData = localStorage.getItem(storageKey);
                        if (!storedData) return null;

                        const progressObj = JSON.parse(storedData);

                        // Handle different storage formats (by ID key or prefix)
                        let item = null;
                        const idKey = isTv ? 't' + contentId : 'm' + contentId;

                        if (progressObj[idKey]) {
                            item = progressObj[idKey];
                        } else if (progressObj[contentId]) {
                            item = progressObj[contentId];
                        } else {
                            // Try to find by scanning all keys
                            for (let key in progressObj) {
                                if (progressObj[key].id == contentId) {
                                    item = progressObj[key];
                                    break;
                                }
                            }
                        }

                        if (!item || !item.progress) return null;

                        const progressData = item.progress;
                        const duration = progressData.duration || 0;
                        const watched = progressData.watched || 0;

                        let season = null;
                        let episode = null;

                        if (isTv) {
                            // Try multiple formats for season/episode
                            season = item.last_season_watched || item.show_progress?.season;
                            episode = item.last_episode_watched || item.show_progress?.episode;

                            // Check show_progress for episode-specific data
                            if (item.show_progress) {
                                const progressKeys = Object.keys(item.show_progress);
                                if (progressKeys.length > 0) {
                                    const lastKey = progressKeys[progressKeys.length - 1];
                                    const lastProgress = item.show_progress[lastKey];
                                    season = lastProgress.season || season;
                                    episode = lastProgress.episode || episode;
                                }
                            }

                            // Convert to numbers
                            season = season ? parseInt(season, 10) : null;
                            episode = episode ? parseInt(episode, 10) : null;
                        }

                        return {
                            id: contentId,
                            type: isTv ? 'tv' : 'movie',
                            position: Math.round(watched * 1000), // Convert to ms
                            duration: Math.round(duration * 1000), // Convert to ms
                            season: season,
                            episode: episode,
                            progress: duration > 0 ? (watched / duration) * 100 : 0
                        };
                    } catch (e) {
                        console.log('Error extracting progress:', e);
                        return null;
                    }
                }

                // Poll localStorage for progress updates (every 15 seconds)
                function startProgressPolling(contentId, isTv, pollInterval = 15000) {
                    const storageKey = getStorageKey(window.location.origin);
                    if (!storageKey) return;

                    let lastPosition = 0;

                    setInterval(() => {
                        const progress = extractProgressFromStorage(storageKey, contentId, isTv);
                        if (progress && progress.position !== lastPosition) {
                            lastPosition = progress.position;
                            sendProgressToAndroid(progress);
                        }
                    }, pollInterval);
                }

                // Message event listener for direct player messages
                window.addEventListener('message', function(event) {
                    // Validate origin
                    const origin = event.origin;
                    if (!allowedOrigins.some(o => origin.includes(o))) {
                        return;
                    }

                    const data = event.data;

                    // Handle MEDIA_DATA type (Vidrock, Vidlink, Vidfast, Vidnest, Vidup, Peachify)
                    if (data && data.type === 'MEDIA_DATA' && data.data) {
                        try {
                            const mediaData = data.data;
                            // Store for later retrieval
                            const storageKey = getStorageKey(origin);
                            if (storageKey) {
                                localStorage.setItem(storageKey, JSON.stringify(mediaData));
                            }

                            // Extract progress for current content if possible
                            // The player will typically send current playback info
                            if (data.data.current) {
                                const current = data.data.current;
                                sendProgressToAndroid({
                                    id: current.id || 0,
                                    type: current.type || 'movie',
                                    position: (current.progress?.watched || 0) * 1000,
                                    duration: (current.progress?.duration || 0) * 1000,
                                    season: current.season,
                                    episode: current.episode,
                                    progress: current.progress?.watched || 0
                                });
                            }
                        } catch (e) {
                            console.log('Error handling MEDIA_DATA:', e);
                        }
                    }

                    // Handle direct string messages (Videasy, Vidking format)
                    if (typeof data === 'string') {
                        try {
                            const parsed = JSON.parse(data);

                            // Extract Videasy/Vidking format
                            if (parsed.id && parsed.progress !== undefined) {
                                sendProgressToAndroid({
                                    id: parsed.id,
                                    type: parsed.type || 'movie',
                                    position: (parsed.timestamp || 0) * 1000,
                                    duration: (parsed.duration || 0) * 1000,
                                    season: parsed.season,
                                    episode: parsed.episode,
                                    progress: parsed.progress
                                });
                            }
                        } catch (e) {
                            // Not JSON, ignore
                        }
                    }

                    // Handle Vidcore format with mediaId
                    if (data && data.mediaId) {
                        sendProgressToAndroid({
                            id: data.mediaId,
                            type: data.mediaType || 'movie',
                            season: data.season,
                            episode: data.episode
                        });
                    }

                    // Handle Peachify PLAYER_EVENT
                    if (data && data.type === 'PLAYER_EVENT' && data.data) {
                        const playerData = data.data;
                        if (playerData.currentTime !== undefined && playerData.duration !== undefined) {
                            sendProgressToAndroid({
                                id: window.currentContentId || 0,
                                type: window.currentContentType || 'movie',
                                position: playerData.currentTime * 1000,
                                duration: playerData.duration * 1000,
                                progress: playerData.duration > 0 ? (playerData.currentTime / playerData.duration) * 100 : 0
                            });
                        }
                    }
                });

                // Expose function to start polling for specific content
                window.startProgressPolling = startProgressPolling;

                // Signal that listener is ready
                console.log('KiduyuTV Progress Listener initialized');

                // Notify Android that listener is ready
                if (window.AndroidProgressCallback) {
                    window.AndroidProgressCallback(JSON.stringify({type: 'LISTENER_READY'}));
                }
            })();
        """.trimIndent()
    }

    /**
     * Generates JavaScript to start progress polling for a specific content.
     * Call this after the iframe has loaded.
     */
    fun generateStartPollingScript(tmdbId: Int, isTv: Boolean): String {
        return """
            if (window.startProgressPolling) {
                window.startProgressPolling($tmdbId, $isTv, 15000);
                console.log('Started progress polling for content: $tmdbId');
            } else {
                console.log('Progress polling not available');
            }
        """.trimIndent()
    }

    /**
     * Generates the complete iframe HTML with integrated progress tracking.
     * This should be used when creating iframe content for the WebView.
     */
    fun generateIframeHtml(
        baseUrl: String,
        iframeUrl: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): String {
        val progressListener = generateUnifiedListenerScript()
        val startPolling = generateStartPollingScript(tmdbId, isTv)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background: #000; overflow: hidden; }
                    iframe { width: 100vw; height: 100vh; border: none; }
                    #messageArea {
                        position: fixed;
                        bottom: 10px;
                        left: 10px;
                        background: rgba(0,0,0,0.7);
                        color: #fff;
                        padding: 10px;
                        font-size: 12px;
                        max-width: 300px;
                        z-index: 9999;
                        display: none;
                    }
                </style>
            </head>
            <body>
                <iframe id="playerFrame" src="${iframeUrl}" allowfullscreen></iframe>
                <div id="messageArea"></div>

                <script>
                    // Store current content info
                    window.currentContentId = $tmdbId;
                    window.currentContentType = '${if (isTv) "tv" else "movie"}';
                    window.currentSeason = ${season ?: 1};
                    window.currentEpisode = ${episode ?: 1};

                    // Unified progress listener
                    (function() {
                        const allowedOrigins = [
                            'vidrock.ru', 'vidlink.pro', 'vidfast.pro', 'vidfast.in',
                            'vidfast.io', 'vidfast.me', 'vidfast.net', 'vidfast.pm',
                            'vidfast.xyz', 'vidnest.fun', 'vidup.to', 'peachify.top'
                        ];

                        const storageKeys = {
                            'vidrock.ru': 'vidRockProgress',
                            'vidlink.pro': 'vidLinkProgress',
                            'vidfast.pro': 'vidFastProgress',
                            'vidfast.in': 'vidFastProgress',
                            'vidfast.io': 'vidFastProgress',
                            'vidfast.me': 'vidFastProgress',
                            'vidfast.net': 'vidFastProgress',
                            'vidfast.pm': 'vidFastProgress',
                            'vidfast.xyz': 'vidFastProgress',
                            'vidnest.fun': 'vidNestProgress',
                            'vidup.to': 'vidUpProgress',
                            'peachify.top': 'peachifyProgress'
                        };

                        function getStorageKey(origin) {
                            try {
                                const hostname = new URL(origin).hostname;
                                return storageKeys[hostname] || null;
                            } catch (e) { return null; }
                        }

                        function sendProgress(data) {
                            try {
                                window.AndroidProgressCallback && window.AndroidProgressCallback(JSON.stringify(data));
                                window.parent.postMessage({type: 'KIDUYU_PROGRESS', data: data}, '*');
                            } catch (e) { console.log('Send error:', e); }
                        }

                        function extractProgress(storageKey, contentId, isTv) {
                            try {
                                const stored = localStorage.getItem(storageKey);
                                if (!stored) return null;

                                const data = JSON.parse(stored);
                                const key = isTv ? 't' + contentId : 'm' + contentId;
                                let item = data[key] || data[contentId];

                                if (!item) {
                                    for (let k in data) {
                                        if (data[k].id == contentId) { item = data[k]; break; }
                                    }
                                }

                                if (!item || !item.progress) return null;

                                const watched = item.progress.watched || 0;
                                const duration = item.progress.duration || 0;
                                let s = null, e = null;

                                if (isTv) {
                                    s = parseInt(item.last_season_watched || item.show_progress?.season);
                                    e = parseInt(item.last_episode_watched || item.show_progress?.episode);

                                    if (item.show_progress) {
                                        const keys = Object.keys(item.show_progress);
                                        if (keys.length) {
                                            const last = item.show_progress[keys[keys.length - 1]];
                                            s = last.season || s;
                                            e = last.episode || e;
                                        }
                                    }
                                }

                                return {
                                    id: contentId,
                                    type: isTv ? 'tv' : 'movie',
                                    position: Math.round(watched * 1000),
                                    duration: Math.round(duration * 1000),
                                    season: s,
                                    episode: e,
                                    progress: duration > 0 ? (watched / duration) * 100 : 0
                                };
                            } catch (e) { return null; }
                        }

                        let lastPosition = 0;

                        function pollProgress() {
                            const key = getStorageKey(window.location.origin);
                            if (!key) return;

                            const progress = extractProgress(key, window.currentContentId, window.currentContentType === 'tv');
                            if (progress && progress.position !== lastPosition) {
                                lastPosition = progress.position;
                                sendProgress(progress);
                            }
                        }

                        setInterval(pollProgress, 5000);

                        window.addEventListener('message', function(event) {
                            try {
                                const origin = new URL(event.origin).hostname;
                                if (!allowedOrigins.some(o => event.origin.includes(o))) return;

                                const data = event.data;

                                if (data?.type === 'MEDIA_DATA' && data.data) {
                                    const key = getStorageKey(event.origin);
                                    if (key) localStorage.setItem(key, JSON.stringify(data.data));

                                    if (data.data.current) {
                                        const c = data.data.current;
                                        sendProgress({
                                            id: c.id || window.currentContentId,
                                            type: c.type || window.currentContentType,
                                            position: (c.progress?.watched || 0) * 1000,
                                            duration: (c.progress?.duration || 0) * 1000,
                                            season: c.season || window.currentSeason,
                                            episode: c.episode || window.currentEpisode
                                        });
                                    }
                                }

                                if (typeof data === 'string') {
                                    try {
                                        const p = JSON.parse(data);
                                        if (p.id && p.progress !== undefined) {
                                            sendProgress({
                                                id: p.id,
                                                type: p.type || window.currentContentType,
                                                position: (p.timestamp || 0) * 1000,
                                                duration: (p.duration || 0) * 1000,
                                                season: p.season || window.currentSeason,
                                                episode: p.episode || window.currentEpisode,
                                                progress: p.progress
                                            });
                                        }
                                    } catch (e) {}
                                }

                                if (data?.mediaId) {
                                    sendProgress({
                                        id: data.mediaId,
                                        type: data.mediaType || window.currentContentType,
                                        season: data.season || window.currentSeason,
                                        episode: data.episode || window.currentEpisode
                                    });
                                }

                                if (data?.type === 'PLAYER_EVENT' && data.data) {
                                    if (data.data.currentTime !== undefined) {
                                        sendProgress({
                                            id: window.currentContentId,
                                            type: window.currentContentType,
                                            position: data.data.currentTime * 1000,
                                            duration: (data.data.duration || 0) * 1000,
                                            season: window.currentSeason,
                                            episode: window.currentEpisode
                                        });
                                    }
                                }
                            } catch (e) { console.log('Message error:', e); }
                        });

                        console.log('KiduyuTV Progress Listener ready');
                        if (window.AndroidProgressCallback) {
                            window.AndroidProgressCallback(JSON.stringify({type: 'LISTENER_READY'}));
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Parses the progress data from a JSON string received from JavaScript.
     */
    fun parseProgressData(jsonString: String): ProgressData? {
        return try {
            val json = com.google.gson.JsonParser.parseString(jsonString).asJsonObject

            // Handle LISTENER_READY
            if (json.has("type") && json.get("type").asString == "LISTENER_READY") {
                Log.i(TAG, "Progress listener is ready")
                return null
            }

            val contentId = json.get("id")?.asInt ?: return null
            val contentType = json.get("type")?.asString ?: "movie"
            val isTv = contentType == "tv"

            val position = json.get("position")?.asLong ?: 0L
            val duration = json.get("duration")?.asLong ?: 0L

            var season: Int? = null
            var episode: Int? = null

            if (isTv) {
                json.get("season")?.let {
                    season = if (it.isJsonNull) null else it.asInt
                }
                json.get("episode")?.let {
                    episode = if (it.isJsonNull) null else it.asInt
                }
            }

            val progress = json.get("progress")?.asDouble ?: 0.0

            ProgressData(
                contentId = contentId,
                contentType = contentType,
                currentPosition = position,
                duration = duration,
                season = season,
                episode = episode,
                progressPercent = progress
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing progress data: ${e.message}")
            null
        }
    }
}