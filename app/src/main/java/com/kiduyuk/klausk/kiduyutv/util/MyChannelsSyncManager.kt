package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsEvent
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsSource
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bidirectional synchronization for the three My Channels tabs.
 *
 * Firebase layout:
 * users/{uid}/myChannels/{iptv|scraped|liveSports}/{stableKey}
 *
 * The existing users/{uid}/savedChannels node remains untouched for backwards
 * compatibility. When myChannels/iptv is empty, its data is imported once as
 * the initial IPTV tab.
 */
object MyChannelsSyncManager {
    private const val TAG = "MyChannelsSync"
    private const val PREFS_NAME = "live_tv_prefs"
    private const val FAVORITES_KEY = "favorite_channels"
    private const val SCRAPED_KEY = "saved_scraped_channels"
    private const val SPORTS_KEY = "favorite_live_sports"
    private const val IPTV_NODE = "iptv"
    private const val SCRAPED_NODE = "scraped"
    private const val SPORTS_NODE = "liveSports"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Start a best-effort cloud reconciliation if an authenticated user exists. */
    fun syncNow(context: Context) {
        if (!AuthManager.isSignedIn.value) return
        scope.launch {
            runCatching { sync(context.applicationContext) }
                .onFailure { Log.e(TAG, "My Channels background sync failed", it) }
        }
    }

    /** Merge cloud and local data, then persist the merged result in both places. */
    suspend fun sync(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cloud = FirebaseManager.getMyChannelsOnce()
        val localIptv = readChannels(prefs.getString(FAVORITES_KEY, "[]"))
        val localScraped = readChannels(prefs.getString(SCRAPED_KEY, "[]"))
        val localSports = readSports(prefs.getString(SPORTS_KEY, "[]"))

        var cloudIptv = readChannelNode(cloud?.get(IPTV_NODE))
        val cloudScraped = readChannelNode(cloud?.get(SCRAPED_NODE))
        val cloudSports = readSportsNode(cloud?.get(SPORTS_NODE))

        // Migrate the legacy IPTV-only node into the new structure when needed.
        if (cloudIptv.isEmpty()) {
            cloudIptv = readLegacyChannels(FirebaseManager.getSavedChannelsOnce())
        }

        val mergedIptv = mergeChannels(cloudIptv, localIptv)
        val mergedScraped = mergeChannels(cloudScraped, localScraped)
        val mergedSports = mergeSports(cloudSports, localSports)

        writeChannels(prefs, FAVORITES_KEY, mergedIptv)
        writeChannels(prefs, SCRAPED_KEY, mergedScraped)
        writeSports(prefs, mergedSports)

        FirebaseManager.replaceMyChannels(
            mapOf(
                IPTV_NODE to mergedIptv.associateBy { channelKey(it.url) }.mapValues { (_, channel) -> channelMap(channel) },
                SCRAPED_NODE to mergedScraped.associateBy { channelKey(it.url) }.mapValues { (_, channel) -> channelMap(channel) },
                SPORTS_NODE to mergedSports.associateBy { sportKey(it) }.mapValues { (_, event) -> sportsMap(event) },
                "lastUpdated" to System.currentTimeMillis()
            )
        )

        Log.i(
            TAG,
            "Synced My Channels: iptv=${mergedIptv.size}, scraped=${mergedScraped.size}, liveSports=${mergedSports.size}"
        )
        return mergedIptv.size + mergedScraped.size + mergedSports.size
    }

    private fun readChannels(json: String?): List<IptvChannel> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                    add(
                        IptvChannel(
                            name = item.optString("name"),
                            logo = item.optString("logo").takeIf { it.isNotBlank() },
                            url = url,
                            group = item.optString("group").takeIf { it.isNotBlank() },
                            tvgId = item.optString("tvgId").takeIf { it.isNotBlank() },
                            tvgName = item.optString("tvgName").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readSports(json: String?): List<LiveSportsEvent> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val source = runCatching { LiveSportsSource.valueOf(item.optString("source")) }.getOrNull()
                        ?: continue
                    val streamsJson = item.optJSONArray("streams")
                    val streams = buildList {
                        for (streamIndex in 0 until (streamsJson?.length() ?: 0)) {
                            val stream = streamsJson?.optJSONObject(streamIndex) ?: continue
                            add(LiveSportsStream(stream.optString("name"), stream.optString("url")))
                        }
                    }
                    add(
                        LiveSportsEvent(
                            id = item.optString("id"),
                            source = source,
                            name = item.optString("name"),
                            category = item.optString("category"),
                            league = item.optString("league"),
                            posterUrl = item.optString("posterUrl"),
                            isLive = item.optBoolean("isLive"),
                            startsAtMillis = if (item.isNull("startsAtMillis")) null else item.optLong("startsAtMillis"),
                            viewerCount = item.optInt("viewerCount"),
                            streams = streams,
                            embedUrl = item.optString("embedUrl"),
                            fallbackStreamName = item.optString("fallbackStreamName")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readChannelNode(node: Any?): List<IptvChannel> =
        (node as? Map<*, *>)?.values?.mapNotNull { value -> channelFromMap(value as? Map<*, *>) }.orEmpty()

    private fun readLegacyChannels(node: Map<String, Any>?): List<IptvChannel> =
        node?.values?.mapNotNull { value -> channelFromMap(value as? Map<*, *>) }.orEmpty()

    private fun channelFromMap(value: Map<*, *>?): IptvChannel? {
        value ?: return null
        val url = value["url"] as? String ?: return null
        if (url.isBlank()) return null
        return IptvChannel(
            name = value["name"] as? String ?: "",
            logo = value["logo"] as? String,
            url = url,
            group = value["group"] as? String,
            tvgId = value["tvgId"] as? String,
            tvgName = value["tvgName"] as? String
        )
    }

    private fun readSportsNode(node: Any?): List<LiveSportsEvent> =
        (node as? Map<*, *>)?.values?.mapNotNull { value -> sportsFromMap(value as? Map<*, *>) }.orEmpty()

    private fun sportsFromMap(value: Map<*, *>?): LiveSportsEvent? {
        value ?: return null
        val source = runCatching { LiveSportsSource.valueOf(value["source"] as? String ?: "") }.getOrNull() ?: return null
        val streams = (value["streams"] as? Map<*, *>)?.values?.mapNotNull { streamValue ->
            val stream = streamValue as? Map<*, *> ?: return@mapNotNull null
            LiveSportsStream(stream["name"] as? String ?: "", stream["url"] as? String ?: "")
        }.orEmpty()
        return LiveSportsEvent(
            id = value["id"] as? String ?: return null,
            source = source,
            name = value["name"] as? String ?: "",
            category = value["category"] as? String ?: "",
            league = value["league"] as? String ?: "",
            posterUrl = value["posterUrl"] as? String ?: "",
            isLive = value["isLive"] as? Boolean ?: false,
            startsAtMillis = (value["startsAtMillis"] as? Number)?.toLong(),
            viewerCount = (value["viewerCount"] as? Number)?.toInt() ?: 0,
            streams = streams,
            embedUrl = value["embedUrl"] as? String ?: "",
            fallbackStreamName = value["fallbackStreamName"] as? String ?: ""
        )
    }

    private fun mergeChannels(cloud: List<IptvChannel>, local: List<IptvChannel>): List<IptvChannel> {
        val result = mutableListOf<IptvChannel>()
        val seen = mutableSetOf<String>()
        (cloud + local).forEach { channel -> if (channel.url.isNotBlank() && seen.add(channel.url)) result += channel }
        return result
    }

    private fun mergeSports(cloud: List<LiveSportsEvent>, local: List<LiveSportsEvent>): List<LiveSportsEvent> {
        val result = mutableListOf<LiveSportsEvent>()
        val seen = mutableSetOf<String>()
        (cloud + local).forEach { event -> if (seen.add(sportKey(event))) result += event }
        return result
    }

    private fun writeChannels(prefs: android.content.SharedPreferences, key: String, channels: List<IptvChannel>) {
        val array = JSONArray()
        channels.forEach { channel -> array.put(JSONObject(channelMap(channel))) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun writeSports(prefs: android.content.SharedPreferences, events: List<LiveSportsEvent>) {
        val array = JSONArray()
        events.forEach { event -> array.put(JSONObject(sportsMap(event))) }
        prefs.edit().putString(SPORTS_KEY, array.toString()).apply()
    }

    private fun channelMap(channel: IptvChannel): Map<String, Any?> = mapOf(
        "name" to channel.name,
        "logo" to channel.logo,
        "url" to channel.url,
        "group" to channel.group,
        "tvgId" to channel.tvgId,
        "tvgName" to channel.tvgName,
        "savedAt" to System.currentTimeMillis()
    )

    private fun sportsMap(event: LiveSportsEvent): Map<String, Any?> = mapOf(
        "id" to event.id,
        "source" to event.source.name,
        "name" to event.name,
        "category" to event.category,
        "league" to event.league,
        "posterUrl" to event.posterUrl,
        "isLive" to event.isLive,
        "startsAtMillis" to event.startsAtMillis,
        "viewerCount" to event.viewerCount,
        "streams" to event.streams.mapIndexed { index, stream ->
            index.toString() to mapOf("name" to stream.name, "url" to stream.url)
        }.toMap(),
        "embedUrl" to event.embedUrl,
        "fallbackStreamName" to event.fallbackStreamName,
        "savedAt" to System.currentTimeMillis()
    )

    private fun sportKey(event: LiveSportsEvent): String = "${event.source.name}_${event.id}"

    private fun channelKey(url: String): String =
        Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
}
