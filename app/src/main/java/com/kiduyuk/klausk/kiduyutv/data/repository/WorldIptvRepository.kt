package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import com.kiduyuk.klausk.kiduyutv.data.model.CountryPlaylist
import com.kiduyuk.klausk.kiduyutv.data.model.CountryPlaylistCategory
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Indexes the public world_ip_tv country playlists and fetches only the M3U
 * chosen by the viewer. GitHub's directory API lets us discover regional
 * variants (for example `us-ca.m3u`) without bundling a stale country list.
 */
class WorldIptvRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchCountryCategories(
        context: Context,
        forceRefresh: Boolean = false
    ): Result<List<CountryPlaylistCategory>> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheFile = File(context.cacheDir, COUNTRY_INDEX_CACHE_FILE)
            val rawIndex = if (!forceRefresh && cacheFile.isFresh()) {
                cacheFile.readText()
            } else {
                val request = Request.Builder()
                    .url(COUNTRY_DIRECTORY_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "KiduyuTv")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) {
                        "Could not load country playlists (HTTP ${response.code})"
                    }
                    response.body?.string().orEmpty().also { body ->
                        check(body.isNotBlank()) { "Country playlist index was empty" }
                        cacheFile.writeText(body)
                    }
                }
            }
            parseCountryCategories(rawIndex)
        }
    }

    suspend fun fetchPlaylist(playlist: CountryPlaylist): Result<IptvPlaylist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(playlist.url)
                    .header("User-Agent", "KiduyuTv")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) {
                        "Could not load ${playlist.displayName} (HTTP ${response.code})"
                    }
                    val body = response.body?.string().orEmpty()
                    check(body.startsWith("#EXTM3U")) { "Selected country file was not an M3U playlist" }
                    IptvRepository.getInstance().parseM3uPlaylist(body)
                }
            }
        }

    private fun parseCountryCategories(rawIndex: String): List<CountryPlaylistCategory> {
        val files = JSONArray(rawIndex)
        val discovered = buildList {
            for (index in 0 until files.length()) {
                val entry = files.optJSONObject(index) ?: continue
                if (!entry.optString("type").equals("file", ignoreCase = true)) continue
                val name = entry.optString("name").lowercase(Locale.ROOT)
                val match = COUNTRY_PLAYLIST_FILE.matchEntire(name) ?: continue
                val countryCode = match.groupValues[1]
                val regionCode = match.groupValues[2].ifBlank { null }
                val url = entry.optString("download_url")
                    .ifBlank { "$COUNTRY_RAW_BASE$name" }
                add(
                    CountryPlaylist(
                        countryCode = countryCode,
                        regionCode = regionCode,
                        displayName = playlistDisplayName(countryCode, regionCode),
                        url = url
                    )
                )
            }
        }

        return discovered
            .groupBy { it.countryCode }
            .map { (countryCode, playlists) ->
                CountryPlaylistCategory(
                    countryCode = countryCode,
                    displayName = countryDisplayName(countryCode),
                    flagUrl = "$FLAG_RAW_BASE$countryCode.svg",
                    playlists = playlists.sortedWith(
                        compareBy<CountryPlaylist> { it.regionCode != null }
                            .thenBy { it.displayName }
                    )
                )
            }
            .sortedBy { it.displayName }
    }

    private fun countryDisplayName(code: String): String {
        val name = Locale("", code.uppercase(Locale.ROOT)).displayCountry
        return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            ?: code.uppercase(Locale.ROOT)
    }

    private fun playlistDisplayName(countryCode: String, regionCode: String?): String {
        val country = countryDisplayName(countryCode)
        if (regionCode == null) return "All $country"
        val region = US_STATE_NAMES[regionCode] ?: regionCode.uppercase(Locale.ROOT)
        return "$country — $region"
    }

    private fun File.isFresh(): Boolean = exists() &&
        System.currentTimeMillis() - lastModified() < COUNTRY_INDEX_CACHE_AGE_MS

    private companion object {
        private const val COUNTRY_DIRECTORY_API =
            "https://api.github.com/repos/Kkbrothers8795/world_ip_tv/contents/country"
        private const val COUNTRY_RAW_BASE =
            "https://raw.githubusercontent.com/Kkbrothers8795/world_ip_tv/master/country/"
        private const val FLAG_RAW_BASE =
            "https://raw.githubusercontent.com/hampusborgos/country-flags/main/svg/"
        private const val COUNTRY_INDEX_CACHE_FILE = "world_iptv_country_index.json"
        private const val COUNTRY_INDEX_CACHE_AGE_MS = 12 * 60 * 60 * 1000L
        private val COUNTRY_PLAYLIST_FILE = Regex("^([a-z]{2})(?:-([a-z0-9]+))?\\.m3u$")
        private val US_STATE_NAMES = mapOf(
            "ak" to "Alaska", "al" to "Alabama", "ar" to "Arkansas",
            "az" to "Arizona", "ca" to "California", "co" to "Colorado",
            "ct" to "Connecticut", "dc" to "District of Columbia", "de" to "Delaware",
            "fl" to "Florida", "ga" to "Georgia", "hi" to "Hawaii",
            "ia" to "Iowa", "id" to "Idaho", "il" to "Illinois", "in" to "Indiana",
            "ks" to "Kansas", "ky" to "Kentucky", "la" to "Louisiana",
            "ma" to "Massachusetts", "md" to "Maryland", "me" to "Maine",
            "mi" to "Michigan", "mn" to "Minnesota", "mo" to "Missouri",
            "ms" to "Mississippi", "mt" to "Montana", "nc" to "North Carolina",
            "nd" to "North Dakota", "ne" to "Nebraska", "nh" to "New Hampshire",
            "nj" to "New Jersey", "nm" to "New Mexico", "nv" to "Nevada",
            "ny" to "New York", "oh" to "Ohio", "ok" to "Oklahoma", "or" to "Oregon",
            "pa" to "Pennsylvania", "ri" to "Rhode Island", "sc" to "South Carolina",
            "sd" to "South Dakota", "tn" to "Tennessee", "tx" to "Texas",
            "ut" to "Utah", "va" to "Virginia", "vt" to "Vermont", "wa" to "Washington",
            "wi" to "Wisconsin", "wv" to "West Virginia", "wy" to "Wyoming"
        )
    }
}
