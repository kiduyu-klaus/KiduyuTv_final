package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Vidsrc playback tokens are bound to the IP that requests generate.php.
 * Mint them on the playback device immediately before loading the HLS URL.
 */
internal object VidsrcPlaybackToken {
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun needsRefresh(stream: StreamItem): Boolean {
        if (!stream.provider.equals("vidsrc", ignoreCase = true)) return false
        val url = stream.url.toHttpUrlOrNull() ?: return false
        return url.encodedPath.contains(".m3u8", ignoreCase = true) ||
            url.encodedPath.endsWith("/index.m3u8", ignoreCase = true)
    }

    fun replaceToken(url: String, token: String): String =
        requireNotNull(url.toHttpUrlOrNull()).newBuilder()
            .setQueryParameter("token", token)
            .build()
            .toString()

    /** Blocking network operation: call only on Dispatchers.IO. Never cache across plays. */
    fun refresh(stream: StreamItem, headers: Map<String, String>): StreamItem {
        val mediaUrl = requireNotNull(stream.url.toHttpUrlOrNull())
        val endpoint = mediaUrl.newBuilder()
            .encodedPath("/generate.php")
            .query(null)
            .fragment(null)
            .build()

        val referer = headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: "${mediaUrl.scheme}://${mediaUrl.host}/"
        val origin = headers.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: "${mediaUrl.scheme}://${mediaUrl.host}"
        val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: BROWSER_USER_AGENT

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .header("Origin", origin)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .build()

        val token = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Vidsrc token request failed: HTTP ${response.code}")
            }
            val text = response.body?.string()?.trim().orEmpty()
            extractToken(text)
                ?: throw IOException("Vidsrc token response did not contain a JWT")
        }

        return stream.copy(url = replaceToken(stream.url, token))
    }

    private fun extractToken(text: String): String? {
        val parsed = runCatching { JSONTokener(text).nextValue() }.getOrNull()
        val candidate = when (parsed) {
            is JSONObject -> listOf("token", "data", "string", "result")
                .asSequence()
                .mapNotNull { parsed.opt(it) as? String }
                .firstOrNull { it.isNotBlank() }
            is String -> parsed
            else -> text
        }?.trim()
        return candidate?.takeIf { JWT_PATTERN.matches(it) }
    }

    private val JWT_PATTERN = Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
}
