package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Mega tokens must be issued to the device that downloads the media. */
internal object MegaPlaybackToken {
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun needsRefresh(stream: StreamItem): Boolean {
        val url = stream.url.toHttpUrlOrNull() ?: return false
        val isCineSrcMega = stream.provider.equals("cinesrc", ignoreCase = true) &&
            stream.name.contains("Mega", ignoreCase = true)
        return isCineSrcMega &&
            url.encodedPath.startsWith("/pl/") &&
            (url.encodedPath.endsWith(".m3u8") || url.encodedPath.endsWith(".mpd"))
    }

    fun replaceToken(url: String, token: String): String =
        requireNotNull(url.toHttpUrlOrNull()).newBuilder()
            .setQueryParameter("token", token).build().toString()

    /** Blocking network operation: call only on Dispatchers.IO. Never cache across plays. */
    fun refresh(stream: StreamItem, headers: Map<String, String>): StreamItem {
        val mediaUrl = requireNotNull(stream.url.toHttpUrlOrNull())
        val endpoint = mediaUrl.newBuilder().encodedPath("/generate.php").query(null).fragment(null).build()
        val request = Request.Builder().url(endpoint).apply {
            headers.forEach { (name, value) ->
                if (name.equals("User-Agent", true) || name.equals("Origin", true) || name.equals("Referer", true)) {
                    header(name, value)
                }
            }
            header("Cache-Control", "no-cache")
        }.build()
        val token = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Mega token request failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty Mega token response")
            val source = body.source()
            source.request(16385)
            if (source.buffer.size > 16384) throw IOException("Invalid Mega token response")
            val text = source.readUtf8().trim()
            val parsed = runCatching { JSONTokener(text).nextValue() }.getOrNull()
            when (parsed) {
                is JSONObject -> listOf("token", "data", "string", "result")
                    .mapNotNull { parsed.opt(it) as? String }.firstOrNull { it.isNotBlank() }.orEmpty()
                is String -> parsed
                else -> text
            }
        }
        if (!Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matches(token)) {
            throw IOException("Invalid Mega token response")
        }
        return stream.copy(url = replaceToken(stream.url, token))
    }
}
