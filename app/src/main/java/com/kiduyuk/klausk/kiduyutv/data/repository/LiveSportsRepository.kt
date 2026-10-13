package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsEvent
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsSource
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the same public JSON feeds used by the supplied PPV and TIMST
 * reference scripts. No HTML/WebView scraping is needed for either source.
 */
object LiveSportsRepository {
    private const val PPV_API_URL = "https://api.ppv.st/api/streams"
    private const val PPV_FOOTBALL_CATEGORY_ID = "34"
    private const val TIMST_API_URL = "https://timst.top/api/live-upcoming"
    private const val MIN_VALID_TIMESTAMP_SECONDS = 1_000_000_000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(source: LiveSportsSource): Result<List<LiveSportsEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                LiveSportsSource.PPV -> parsePpv(fetchJson(PPV_API_URL))
                LiveSportsSource.TIMST -> parseTimst(fetchJson(TIMST_API_URL))
            }
        }
    }

    private fun fetchJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Sports feed returned HTTP ${response.code}")
            return JSONObject(body)
        }
    }

    private fun parsePpv(payload: JSONObject): List<LiveSportsEvent> {
        if (!payload.optBoolean("success", true)) {
            throw IOException("PPV feed reported failure")
        }
        val now = payload.optLong("timestamp").takeIf { it >= MIN_VALID_TIMESTAMP_SECONDS }
            ?: System.currentTimeMillis() / 1_000L
        val category = payload.optJSONArray("streams")
            ?.asSequence()
            ?.mapNotNull { it as? JSONObject }
            ?.firstOrNull { it.optString("id") == PPV_FOOTBALL_CATEGORY_ID }
            ?: throw IOException("PPV Football category was not returned")
        val categoryName = category.optString("category", "Football")
        val events = buildList {
            category.optJSONArray("streams")?.asSequence()?.mapNotNull { it as? JSONObject }?.forEachIndexed { index, stream ->
                val base = ppvEvent(
                    stream = stream,
                    id = "ppv-$index-${stream.optString("uri_name")}",
                    name = stream.optString("name"),
                    embed = stream.optString("iframe"),
                    category = categoryName,
                    league = stream.optString("tag"),
                    now = now
                )
                if (base != null) add(base)

                stream.optJSONArray("substreams")?.asSequence()
                    ?.mapNotNull { it as? JSONObject }
                    ?.forEachIndexed { subIndex, substream ->
                        ppvEvent(
                            stream = stream,
                            id = "ppv-$index-sub-$subIndex-${substream.optString("uri_name")}",
                            name = substream.optString("name", stream.optString("name")),
                            embed = substream.optString("iframe"),
                            category = categoryName,
                            league = stream.optString("tag"),
                            now = now
                        )?.let(::add)
                    }
            }
        }
        return sortEvents(events)
    }

    private fun ppvEvent(
        stream: JSONObject,
        id: String,
        name: String,
        embed: String,
        category: String,
        league: String,
        now: Long
    ): LiveSportsEvent? {
        val starts = stream.optLong("starts_at")
        val ends = stream.optLong("ends_at")
        val alwaysLive = stream.optInt("always_live") != 0
        val isLive = alwaysLive || (starts >= MIN_VALID_TIMESTAMP_SECONDS && starts <= now && now < ends)
        val isUpcoming = starts >= MIN_VALID_TIMESTAMP_SECONDS && starts > now
        if (!isLive && !isUpcoming) return null
        return LiveSportsEvent(
            id = id,
            source = LiveSportsSource.PPV,
            name = name.ifBlank { "Live event" },
            category = category,
            league = league,
            posterUrl = stream.optString("poster"),
            isLive = isLive,
            startsAtMillis = starts.takeIf { it >= MIN_VALID_TIMESTAMP_SECONDS }?.times(1_000L),
            viewerCount = stream.optInt("viewers"),
            streamUrls = emptyList(),
            embedUrl = embed
        )
    }

    private fun parseTimst(payload: JSONObject): List<LiveSportsEvent> {
        if (payload.optBoolean("success", true).not()) {
            throw IOException("TIMST feed reported failure")
        }
        val genres = payload.optJSONArray("genres")
        val topGenres = mutableMapOf<Int, String>()
        val subGenres = mutableMapOf<Int, String>()
        genres?.asSequence()?.mapNotNull { it as? JSONObject }?.forEach { genre ->
            topGenres[genre.optInt("id")] = genre.optString("name")
            genre.optJSONArray("sub_categories")?.asSequence()
                ?.mapNotNull { it as? JSONObject }
                ?.forEach { sub -> subGenres[sub.optInt("id")] = sub.optString("name") }
        }
        val now = payload.optLong("timestamp").takeIf { it > 0L } ?: System.currentTimeMillis() / 1_000L
        val events = payload.optJSONArray("events")?.asSequence()
            ?.mapNotNull { it as? JSONObject }
            ?.mapIndexedNotNull { index, event ->
                val startsAtMillis = parseTimstTime(event.optString("time"))
                val isLive = startsAtMillis?.div(1_000L)?.let { it <= now } ?: false
                val urls = event.optJSONArray("streams")
                    ?.asSequence()
                    ?.mapNotNull { it as? JSONObject }
                    ?.map { it.optString("url") }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
                LiveSportsEvent(
                    id = "timst-$index-${event.optString("url", event.optString("name"))}",
                    source = LiveSportsSource.TIMST,
                    name = event.optString("name").ifBlank { "Live event" },
                    category = topGenres[event.optInt("genre")].orEmpty(),
                    league = subGenres[event.optInt("sub_genre")].orEmpty(),
                    posterUrl = event.optString("logo"),
                    isLive = isLive,
                    startsAtMillis = startsAtMillis,
                    viewerCount = event.optInt("viewers"),
                    streamUrls = urls,
                    embedUrl = urls.firstOrNull().orEmpty()
                )
            }
            .orEmpty()
        return sortEvents(events)
    }

    private fun parseTimstTime(value: String): Long? {
        if (value.isBlank()) return null
        val normalized = value.trim().replace(' ', 'T')
        val timezone = TimeZone.getTimeZone("America/New_York")
        listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm").forEach { pattern ->
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = timezone
            }
            runCatching { parser.parse(normalized)?.time }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun sortEvents(events: List<LiveSportsEvent>): List<LiveSportsEvent> =
        events.sortedWith(
            compareByDescending<LiveSportsEvent> { it.isLive }
                .thenByDescending { it.viewerCount }
                .thenBy { it.startsAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.name }
        )

    private fun JSONArray.asSequence(): Sequence<Any?> = sequence {
        for (index in 0 until length()) yield(opt(index))
    }
}
