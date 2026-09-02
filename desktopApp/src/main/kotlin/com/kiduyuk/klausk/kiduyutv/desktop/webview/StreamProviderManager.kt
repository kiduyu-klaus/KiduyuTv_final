package com.kiduyuk.klausk.kiduyutv.desktop.webview

import com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog
import com.kiduyuk.klausk.kiduyutv.desktop.data.logSafe

import java.net.URI

data class StreamProvider(
    val name: String,
    val baseUrl: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String,
    val enabled: Boolean,
    val iframeAttributes: Map<String, String> = emptyMap(),
    val movieParameters: Map<String, String> = emptyMap(),
    val tvParameters: Map<String, String> = emptyMap()
)

/**
 * Desktop WebView provider catalog copied from the authoritative stream_providers.json.
 * The provider URL is kept separately from the media templates so getBaseUrl() always
 * returns the configured provider base URL rather than a local fallback.
 */
object StreamProviderManager {
    val providers: List<StreamProvider> = listOf(
        StreamProvider(
            name = "111Movies",
            baseUrl = "https://111movies.com",
            movieUrlTemplate = "https://111movies.com/movie/%d",
            tvUrlTemplate = "https://111movies.com/tv/%d/%d/%d",
            enabled = true,
            iframeAttributes = mapOf("frameborder" to "0"),
        ),
        StreamProvider(
            name = "Amri",
            baseUrl = "https://amri.gg",
            movieUrlTemplate = "https://amri.gg/movie/%d",
            tvUrlTemplate = "https://amri.gg/tv/%d/%d/%d",
            enabled = false,
            movieParameters = mapOf("autoplay" to "true"),
            tvParameters = mapOf("autoplay" to "true"),
        ),
        StreamProvider(
            name = "Anyembed",
            baseUrl = "https://anyembed.xyz",
            movieUrlTemplate = "https://anyembed.xyz/embed/tmdb-movie-%d",
            tvUrlTemplate = "https://anyembed.xyz/embed/tmdb-tv-%d-%d-%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Autoembed",
            baseUrl = "https://autoembed.co",
            movieUrlTemplate = "https://autoembed.co/movie/tmdb/%d",
            tvUrlTemplate = "https://autoembed.co/tv/tmdb/%d-%d-%d",
            enabled = true,
        ),
        StreamProvider(
            name = "CineSrc",
            baseUrl = "https://cinesrc.st",
            movieUrlTemplate = "https://cinesrc.st/embed/movie/%d",
            tvUrlTemplate = "https://cinesrc.st/embed/tv/%d?s=%d&e=%d",
            enabled = true,
            movieParameters = mapOf("autoplay" to "true", "autoskip" to "true", "color" to "#e50914", "quality" to "1080"),
            tvParameters = mapOf("autonext" to "true", "autoplay" to "true", "autoskip" to "true", "color" to "FF1493", "quality" to "1080"),
        ),
        StreamProvider(
            name = "Cinemaos",
            baseUrl = "https://cinemaos.tech",
            movieUrlTemplate = "https://cinemaos.tech/player/%d",
            tvUrlTemplate = "https://cinemaos.tech/player/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoplay" to "true"),
            tvParameters = mapOf("autoplay" to "true"),
        ),
        StreamProvider(
            name = "Cinezo",
            baseUrl = "https://www.cinezo.net/",
            movieUrlTemplate = "https://player.cinezo.live/embed/movie/%d",
            tvUrlTemplate = "https://player.cinezo.live/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Delta",
            baseUrl = "https://player.videasy.net",
            movieUrlTemplate = "https://player.videasy.net/movie/%d",
            tvUrlTemplate = "https://player.videasy.net/tv/%d/%d/%d",
            enabled = false,
            movieParameters = mapOf("color" to "e50914", "overlay" to "true"),
            tvParameters = mapOf("color" to "e50914", "overlay" to "true"),
        ),
        StreamProvider(
            name = "EmbedMaster",
            baseUrl = "https://embedmaster.link",
            movieUrlTemplate = "https://embedmaster.link/movie/%d",
            tvUrlTemplate = "https://embedmaster.link/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoPlay" to "true"),
            tvParameters = mapOf("autoNext" to "true", "autoPlay" to "true", "nextButton" to "true"),
        ),
        StreamProvider(
            name = "Filesun",
            baseUrl = "https://filesun.sbs",
            movieUrlTemplate = "https://filesun.sbs/embed/movie/%d",
            tvUrlTemplate = "https://filesun.sbs/embed/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Filmora",
            baseUrl = "https://filmora.qzz.io",
            movieUrlTemplate = "https://filmora.qzz.io/watch/movie/%d",
            tvUrlTemplate = "https://filmora.qzz.io/watch/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Flixer",
            baseUrl = "https://flixer.su",
            movieUrlTemplate = "https://flixer.su/watch/movie/%d",
            tvUrlTemplate = "https://flixer.su/watch/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Gaiaflix",
            baseUrl = "https://gaiaflix.live",
            movieUrlTemplate = "https://gaiaflix.live/watch/%d?type=movie",
            tvUrlTemplate = "https://gaiaflix.live/watch/%d?type=tv&s=%d&e=%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Goated",
            baseUrl = "https://goated.cx",
            movieUrlTemplate = "https://goated.cx/movie/%d/play",
            tvUrlTemplate = "https://goated.cx/tv/%d/play?season=%d&episode=%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Haze",
            baseUrl = "https://vsembed.ru",
            movieUrlTemplate = "https://vsembed.ru/embed/movie/%d",
            tvUrlTemplate = "https://vsembed.ru/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Kappa",
            baseUrl = "https://airflix1.com",
            movieUrlTemplate = "https://airflix1.com/embed/movie/%d",
            tvUrlTemplate = "https://airflix1.com/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Kite",
            baseUrl = "https://www.zxcstream.xyz",
            movieUrlTemplate = "https://www.zxcstream.xyz/player/movie/%d",
            tvUrlTemplate = "https://www.zxcstream.xyz/player/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Lordflix",
            baseUrl = "https://lordflix.org",
            movieUrlTemplate = "https://lordflix.org/watch/movie/%d",
            tvUrlTemplate = "https://lordflix.org/watch/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Mapple",
            baseUrl = "https://mapple.uk",
            movieUrlTemplate = "https://mapple.uk/watch/movie/%d",
            tvUrlTemplate = "https://mapple.uk/watch/tv/%d-%d-%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Moviebite",
            baseUrl = "https://moviebite.cc",
            movieUrlTemplate = "https://moviebite.cc/watch/movie/%d",
            tvUrlTemplate = "https://moviebite.cc/watch/tv/%d/season/%d/episode/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Neon",
            baseUrl = "https://moviesapi.to",
            movieUrlTemplate = "https://moviesapi.to/movie/%d",
            tvUrlTemplate = "https://moviesapi.to/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Nxsha",
            baseUrl = "https://web.nxsha.app",
            movieUrlTemplate = "https://web.nxsha.app/watch/movie/%d",
            tvUrlTemplate = "https://web.nxsha.app/watch/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Peachify",
            baseUrl = "https://peachify.top",
            movieUrlTemplate = "https://peachify.top/embed/movie/%d",
            tvUrlTemplate = "https://peachify.top/embed/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("accent" to "e50914", "autoPlay" to "true", "cast" to "hide", "pip" to "hide", "sub" to "English"),
            tvParameters = mapOf("accent" to "e50914", "autoNext" to "30", "autoPlay" to "true", "cast" to "hide", "pip" to "hide", "sub" to "English"),
        ),
        StreamProvider(
            name = "PrimeSrc",
            baseUrl = "https://primesrc.me",
            movieUrlTemplate = "https://primesrc.me/embed/movie?tmdb=%d",
            tvUrlTemplate = "https://primesrc.me/embed/tv?tmdb=%d&season=%d&episode=%d",
            enabled = false,
            movieParameters = mapOf("fallback" to "true", "serverOrder" to "PrimeVid"),
            tvParameters = mapOf("fallback" to "true", "serverOrder" to "PrimeVid"),
        ),
        StreamProvider(
            name = "Pulse",
            baseUrl = "https://play.xpass.top",
            movieUrlTemplate = "https://play.xpass.top/e/movie/%d",
            tvUrlTemplate = "https://play.xpass.top/e/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Rivestream",
            baseUrl = "https://rivestream.ru",
            movieUrlTemplate = "https://rivestream.ru/embed?type=movie&id=%d",
            tvUrlTemplate = "https://rivestream.ru/embed?type=tv&id=%d&season=%d&episode=%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Smashystream",
            baseUrl = "https://embed.smashystream.com",
            movieUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d",
            tvUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d&season=%d&episode=%d",
            enabled = true,
            iframeAttributes = mapOf("frameborder" to "0"),
        ),
        StreamProvider(
            name = "Stigstream",
            baseUrl = "https://stigstream.ru",
            movieUrlTemplate = "https://stigstream.ru/movie/watch/%d",
            tvUrlTemplate = "https://stigstream.ru/tv/watch/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Streamo",
            baseUrl = "https://streamo.pro",
            movieUrlTemplate = "https://streamo.pro/movies/%d",
            tvUrlTemplate = "https://streamo.pro/series/%d?play=1&s=%d&e=%d",
            enabled = false,
            movieParameters = mapOf("play" to "1"),
        ),
        StreamProvider(
            name = "Streamvaults",
            baseUrl = "https://streamvaultsrc.click",
            movieUrlTemplate = "https://streamvaultsrc.click/embed/movie/%d",
            tvUrlTemplate = "https://streamvaultsrc.click/embed/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Toustream",
            baseUrl = "https://toustream.xyz",
            movieUrlTemplate = "https://toustream.xyz/tou/movies/%d",
            tvUrlTemplate = "https://toustream.xyz/watch/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "VidAPI",
            baseUrl = "https://vaplayer.ru",
            movieUrlTemplate = "https://vaplayer.ru/embed/movie/%d",
            tvUrlTemplate = "https://vaplayer.ru/embed/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoplay" to "1", "color" to "e50914", "overlay" to "true", "sub_lang" to "en"),
            tvParameters = mapOf("autoplay" to "1", "color" to "e50914", "overlay" to "true", "sub_lang" to "en"),
        ),
        StreamProvider(
            name = "VidCore",
            baseUrl = "https://vidcore.net",
            movieUrlTemplate = "https://vidcore.net/movie/%d",
            tvUrlTemplate = "https://vidcore.net/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoPlay" to "true", "chromecast" to "false", "sub" to "en", "theme" to "e50914"),
            tvParameters = mapOf("autoNext" to "true", "autoPlay" to "true", "chromecast" to "false", "nextButton" to "true", "sub" to "en", "theme" to "e50914"),
        ),
        StreamProvider(
            name = "VidFast",
            baseUrl = "https://vidfast.pro",
            movieUrlTemplate = "https://vidfast.vc/movie/%d",
            tvUrlTemplate = "https://vidfast.vc/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoPlay" to "true", "theme" to "9B59B6"),
            tvParameters = mapOf("autoNext" to "true", "autoPlay" to "true", "nextButton" to "true", "theme" to "9B59B6"),
        ),
        StreamProvider(
            name = "VidKing",
            baseUrl = "https://www.vidking.net",
            movieUrlTemplate = "https://www.vidking.net/embed/movie/%d",
            tvUrlTemplate = "https://www.vidking.net/embed/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoPlay" to "true", "color" to "e50914"),
            tvParameters = mapOf("autoPlay" to "true", "color" to "e50914", "episodeSelector" to "true", "nextEpisode" to "true"),
        ),
        StreamProvider(
            name = "VidLink",
            baseUrl = "https://vidlink.pro",
            movieUrlTemplate = "https://vidlink.pro/movie/%d",
            tvUrlTemplate = "https://vidlink.pro/tv/%d/%d/%d",
            enabled = true,
            iframeAttributes = mapOf("frameborder" to "0"),
            movieParameters = mapOf("autoPlay" to "true"),
            tvParameters = mapOf("autoPlay" to "true"),
        ),
        StreamProvider(
            name = "VidNest",
            baseUrl = "https://vidnest.fun",
            movieUrlTemplate = "https://vidnest.fun/movie/%d",
            tvUrlTemplate = "https://vidnest.fun/tv/%d/%d/%d",
            enabled = true,
            iframeAttributes = mapOf("frameBorder" to "0", "scrolling" to "no"),
            movieParameters = mapOf("bottomcaption" to "true", "servericon" to "show", "timeslider" to "1"),
        ),
        StreamProvider(
            name = "VidPlus",
            baseUrl = "https://player.vidplus.to",
            movieUrlTemplate = "https://player.vidplus.to/embed/movie/%d",
            tvUrlTemplate = "https://player.vidplus.to/embed/tv/%d/%d/%d",
            enabled = false,
            movieParameters = mapOf("autoNext" to "true", "autoplay" to "true", "episodelist" to "true", "nextButton" to "true", "poster" to "true", "servericon" to "true", "title" to "true"),
            tvParameters = mapOf("autoNext" to "true", "autoplay" to "true", "poster" to "true", "servericon" to "true", "title" to "true"),
        ),
        StreamProvider(
            name = "VidSrc (WTF) v1",
            baseUrl = "https://vidsrc.wtf",
            movieUrlTemplate = "https://vidsrc.wtf/api/1/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/1/tv/?id=%d&s=%d&e=%d",
            enabled = true,
        ),
        StreamProvider(
            name = "VidSrc (WTF) v3",
            baseUrl = "https://vidsrc.wtf",
            movieUrlTemplate = "https://vidsrc.wtf/api/3/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/3/tv/?id=%d&s=%d&e=%d",
            enabled = true,
        ),
        StreamProvider(
            name = "VidSrc (WTF) v4",
            baseUrl = "https://vidsrc.wtf",
            movieUrlTemplate = "https://vidsrc.wtf/api/4/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/4/tv/?id=%d&s=%d&e=%d",
            enabled = true,
        ),
        StreamProvider(
            name = "VidUp",
            baseUrl = "https://vidup.to",
            movieUrlTemplate = "https://vidup.to/movie/%d",
            tvUrlTemplate = "https://vidup.to/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoPlay" to "true", "chromecast" to "false", "sub" to "en", "theme" to "e50914"),
            tvParameters = mapOf("autoPlay" to "true", "chromecast" to "false", "sub" to "en", "theme" to "e50914"),
        ),
        StreamProvider(
            name = "VidZee",
            baseUrl = "https://player.vidzee.wtf",
            movieUrlTemplate = "https://player.vidzee.wtf/v2/embed/movie/%d",
            tvUrlTemplate = "https://player.vidzee.wtf/v2/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Vidapi2",
            baseUrl = "https://vidapi.xyz",
            movieUrlTemplate = "https://vidapi.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidapi.xyz/embed/tv/%d/%d/%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Videasy",
            baseUrl = "https://player.videasy.net",
            movieUrlTemplate = "https://player.videasy.to/movie/%d",
            tvUrlTemplate = "https://player.videasy.to/tv/%d/%d/%d",
            enabled = true,
            iframeAttributes = mapOf("allow" to "encrypted-media", "frameborder" to "0"),
            movieParameters = mapOf("color" to "8B5CF6", "overlay" to "true"),
            tvParameters = mapOf("autoplayNextEpisode" to "true", "color" to "8B5CF6", "episodeSelector" to "true", "nextEpisode" to "true", "overlay" to "true"),
        ),
        StreamProvider(
            name = "Vidlove",
            baseUrl = "https://vidlove.cc",
            movieUrlTemplate = "https://player.vidlove.cc/embed/movie/%d",
            tvUrlTemplate = "https://player.vidlove.cc/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Vidrock",
            baseUrl = "https://vidrock.net",
            movieUrlTemplate = "https://vidrock.ru/movie/%d",
            tvUrlTemplate = "https://vidrock.ru/tv/%d/%d/%d",
            enabled = false,
            movieParameters = mapOf("autoplay" to "true"),
            tvParameters = mapOf("autonext" to "true", "autoplay" to "true"),
        ),
        StreamProvider(
            name = "Vidsrc",
            baseUrl = "https://vidsrc.mov",
            movieUrlTemplate = "https://vidsrc.mov/embed/movie/%d",
            tvUrlTemplate = "https://vidsrc.mov/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Vidsrcsrb",
            baseUrl = "https://vidsrc.sbs",
            movieUrlTemplate = "https://vidsrc.sbs/embed/movie/%d",
            tvUrlTemplate = "https://vidsrc.sbs/embed/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Vidstorm",
            baseUrl = "https://vidstorm.ru",
            movieUrlTemplate = "https://vidstorm.ru/movie/%d",
            tvUrlTemplate = "https://vidstorm.ru/tv/%d/%d/%d",
            enabled = false,
            movieParameters = mapOf("autoplay" to "true", "download" to "false", "theme" to "ff6b6b"),
            tvParameters = mapOf("autonext" to "true", "autoplay" to "true", "download" to "false", "theme" to "ff6b6b"),
        ),
        StreamProvider(
            name = "Vidsync",
            baseUrl = "https://vidsync.xyz",
            movieUrlTemplate = "https://vidsync.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidsync.xyz/embed/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoPlay" to "true", "theme" to "e50914"),
            tvParameters = mapOf("autoNext" to "true", "autoPlay" to "true", "theme" to "e50914"),
        ),
        StreamProvider(
            name = "Vidzen",
            baseUrl = "https://vidzen.fun",
            movieUrlTemplate = "https://vidzen.fun/movie/%d",
            tvUrlTemplate = "https://vidzen.fun/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoplay" to "true"),
            tvParameters = mapOf("autoplay" to "true"),
        ),
        StreamProvider(
            name = "Vlux",
            baseUrl = "https://vidlux.xyz",
            movieUrlTemplate = "https://vidlux.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidlux.xyz/embed/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoplay" to "true"),
            tvParameters = mapOf("autoplay" to "true"),
        ),
        StreamProvider(
            name = "Warp",
            baseUrl = "https://vidfast.pro",
            movieUrlTemplate = "https://vidfast.pro/movie/%d",
            tvUrlTemplate = "https://vidfast.pro/tv/%d/%d/%d",
            enabled = true,
        ),
        StreamProvider(
            name = "Watchott",
            baseUrl = "https://watchott.ru",
            movieUrlTemplate = "https://watchott.ru/play?id=%d&type=movie",
            tvUrlTemplate = "https://watchott.ru/play?id=%d&type=tv&season=%d&episode=%d",
            enabled = false,
        ),
        StreamProvider(
            name = "Zxc",
            baseUrl = "https://zxcstream.xyz",
            movieUrlTemplate = "https://zxcstream.xyz/embed/movie/%d",
            tvUrlTemplate = "https://zxcstream.xyz/embed/tv/%d/%d/%d",
            enabled = true,
            movieParameters = mapOf("autoplay" to "true"),
            tvParameters = mapOf("autoplay" to "true"),
        )
    )

    fun getProvider(providerName: String): StreamProvider? =
        providers.firstOrNull { it.name.equals(providerName, ignoreCase = true) }

    fun generateUrl(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L
    ): String {
        val provider = getProvider(providerName) ?: providers.first().also {
            DesktopLog.logger.warn("Unknown WebView provider={} fallback={}", providerName, it.name)
        }
        val template = if (isTv) provider.tvUrlTemplate else provider.movieUrlTemplate
        val formattedUrl = runCatching {
            if (isTv) template.format(tmdbId, season ?: 1, episode ?: 1)
            else template.format(tmdbId)
        }.getOrElse {
            DesktopLog.logger.error("Failed to format WebView URL provider={} template={}", provider.name, template, it)
            return provider.baseUrl
        }
        val configuredParameters = if (isTv) provider.tvParameters else provider.movieParameters
        val params = if (timestamp > 0L) {
            configuredParameters + ("startAt" to timestamp.toString())
        } else {
            configuredParameters
        }
        if (params.isEmpty()) {
            DesktopLog.logger.info(
                "Generated WebView URL provider={} type={} tmdbId={} url={}",
                provider.name,
                if (isTv) "series" else "movie",
                tmdbId,
                formattedUrl.logSafe()
            )
            return formattedUrl
        }
        val query = params.entries.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
        return (if ('?' in formattedUrl) "$formattedUrl&$query" else "$formattedUrl?$query").also {
            DesktopLog.logger.info(
                "Generated WebView URL provider={} type={} tmdbId={} url={}",
                provider.name,
                if (isTv) "series" else "movie",
                tmdbId,
                it.logSafe()
            )
        }
    }

    fun generateIframeHtml(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L
    ): String {
        val provider = getProvider(providerName) ?: providers.first().also {
            DesktopLog.logger.warn("Unknown WebView provider={} fallback={}", providerName, it.name)
        }
        val finalUrl = generateUrl(provider.name, tmdbId, isTv, season, episode, timestamp)
        val attributes = linkedMapOf<String, String>().apply { putAll(provider.iframeAttributes) }
        if (attributes.keys.none { it.equals("frameborder", ignoreCase = true) }) attributes["frameborder"] = "0"
        if (attributes.keys.none { it.equals("allowfullscreen", ignoreCase = true) }) attributes["allowfullscreen"] = ""

        val allowFeatures = attributes.entries
            .firstOrNull { it.key.equals("allow", ignoreCase = true) }
            ?.value
            ?.split(';', ' ', ',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toMutableList()
            ?: mutableListOf()
        listOf("autoplay", "encrypted-media", "fullscreen", "picture-in-picture").forEach { feature ->
            if (allowFeatures.none { it.equals(feature, ignoreCase = true) }) allowFeatures += feature
        }
        attributes.entries.removeAll { it.key.equals("allow", ignoreCase = true) }
        attributes["allow"] = allowFeatures.joinToString("; ")

        val attributeHtml = attributes.entries.joinToString(" ") { (key, value) ->
            if (key.equals("allowfullscreen", ignoreCase = true)) "allowfullscreen"
            else "${escapeHtml(key)}=\"${escapeHtml(value)}\""
        }
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                <style>
                    html,body { margin:0; width:100%; height:100%; overflow:hidden; background:#000; }
                    iframe { position:absolute; inset:0; width:100%; height:100%; border:0; background:#000; }
                </style>
            </head>
            <body>
                <iframe id="player-frame" src="${escapeHtml(finalUrl)}" $attributeHtml></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    fun getBaseUrl(providerName: String): String {
        val provider = getProvider(providerName) ?: providers.first().also {
            DesktopLog.logger.warn("Unknown WebView provider={} base URL fallback={}", providerName, it.name)
        }
        val baseUrl = provider.baseUrl.trim()
        return runCatching {
            val uri = URI(baseUrl)
            require(!uri.scheme.isNullOrBlank() && !uri.authority.isNullOrBlank())
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
            baseUrl
        }.onFailure {
            DesktopLog.logger.error("Invalid provider base URL provider={} url={}", provider.name, baseUrl.logSafe(), it)
        }.getOrElse { provider.baseUrl }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character.toString()
                }
            )
        }
    }
}
