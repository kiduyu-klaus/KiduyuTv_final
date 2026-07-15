/*
 * Kiduyu player tracker.
 * Bridges postMessage events from third-party iframe players (Videasy, Vidking,
 * Vidrock, Vidfast, Vidnest) into the Android `MavisInterface` JavaScript
 * interface so the host activity can persist watch progress.
 *
 * Configuration is read from the global `window.__kiduyuPlayerConfig` set by the
 * tiny inline bootstrap that ships inside the generated iframe HTML. When the
 * config is missing the tracker stays inert and logs a warning to the console.
 */
(function() {
    'use strict';

    var config = (typeof window !== 'undefined' && window.__kiduyuPlayerConfig) || null;
    if (!config) {
        if (typeof console !== 'undefined' && console.warn) {
            console.warn('[kiduyu-tracker] Missing __kiduyuPlayerConfig; tracker disabled.');
        }
        return;
    }

    var currentContentId = config.contentId;
    var currentIsTv = !!config.isTv;
    var currentSeason = (typeof config.season === 'number') ? config.season : 1;
    var currentEpisode = (typeof config.episode === 'number') ? config.episode : 1;

    function sendToAndroid(currentTime, duration, seasonNum, episodeNum, provider) {
        if (typeof MavisInterface !== 'undefined' && MavisInterface.onPlayerEvent) {
            var payload = {
                currentTime: parseFloat(currentTime),
                duration:    parseFloat(duration),
                season: seasonNum ? parseInt(seasonNum, 10) : null,
                episode: episodeNum ? parseInt(episodeNum, 10) : null,
                provider: provider || ''
            };
            MavisInterface.onPlayerEvent(JSON.stringify(payload));
        }
    }

    // Provider origin -> canonical name. Only origins we control or trust should
    // be listed here; everything else is silently dropped.
    var providerMap = {
        'https://player.videasy.net': 'Videasy',
        'https://vidrock.ru':         'Vidrock',
        'https://vidfast.pro':        'Vidfast',
        'https://vidfast.in':         'Vidfast',
        'https://vidfast.io':         'Vidfast',
        'https://vidfast.me':         'Vidfast',
        'https://vidfast.net':        'Vidfast',
        'https://vidfast.pm':         'Vidfast',
        'https://vidfast.xyz':        'Vidfast',
        'https://www.vidking.net':    'Vidking',
        'https://vidnest.fun':        'Vidnest'
    };

    window.addEventListener('message', function(event) {
        var origin = event.origin || '';
        var provider = providerMap[origin];
        if (!provider) return;

        var data = event.data;
        if (!data) return;

        try {
            if (provider === 'Videasy' || provider === 'Vidking') {
                if (typeof data === 'string') {
                    var parsed = JSON.parse(data);
                    if (parsed.timestamp !== undefined) {
                        var seasonVal = parsed.season !== undefined ? parsed.season : (currentIsTv ? currentSeason : null);
                        var episodeVal = parsed.episode !== undefined ? parsed.episode : (currentIsTv ? currentEpisode : null);
                        sendToAndroid(parsed.timestamp, parsed.duration || 0, seasonVal, episodeVal, provider);
                    }
                }
                return;
            }

            if (data.type === 'MEDIA_DATA' && data.data) {
                var mediaData = data.data;
                var item = null;

                if (provider === 'Vidrock') {
                    if (Array.isArray(mediaData)) {
                        for (var i = 0; i < mediaData.length; i++) {
                            if (mediaData[i] && mediaData[i].id == currentContentId) {
                                item = mediaData[i];
                                break;
                            }
                        }
                    }
                } else if (provider === 'Vidfast') {
                    var key = (currentIsTv ? 't' : 'm') + currentContentId;
                    item = mediaData[key] || null;
                } else if (provider === 'Vidnest') {
                    item = mediaData[String(currentContentId)] || null;
                }

                if (!item) return;

                var watched = 0;
                var seasonNum = null;
                var episodeNum = null;

                if (currentIsTv) {
                    var rawSeason = item.last_season_watched !== undefined ? parseInt(item.last_season_watched, 10) : currentSeason;
                    var rawEpisode = item.last_episode_watched !== undefined ? parseInt(item.last_episode_watched, 10) : currentEpisode;
                    seasonNum = rawSeason;
                    episodeNum = rawEpisode;

                    var epKey = 's' + seasonNum + 'e' + episodeNum;
                    if (item.show_progress && item.show_progress[epKey]) {
                        var epProgress = item.show_progress[epKey];
                        if (epProgress.progress && epProgress.progress.watched !== undefined) {
                            watched = epProgress.progress.watched;
                        } else if (epProgress.watched !== undefined) {
                            watched = epProgress.watched;
                        }
                    } else if (item.progress && item.progress.watched !== undefined) {
                        watched = item.progress.watched;
                    }
                } else {
                    if (item.progress && item.progress.watched !== undefined) {
                        watched = item.progress.watched;
                    }
                }

                var duration = 0;
                if (item.progress && item.progress.duration !== undefined) {
                    duration = item.progress.duration;
                }

                sendToAndroid(watched, duration, seasonNum, episodeNum, provider);
            }
        } catch (e) {
            // Ignore parse errors so a single bad message does not break the listener.
        }
    });
})();
