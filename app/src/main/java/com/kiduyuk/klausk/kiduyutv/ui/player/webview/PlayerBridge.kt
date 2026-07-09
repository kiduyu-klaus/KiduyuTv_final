package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JavascriptInterface bridge between WebView and Android for player events.
 * Receives watch progress data from the injected tracking script in the iframe HTML.
 */
data class PlayerBridgeEvent(
    val event: String,
    val provider: String,
    val positionSec: Double?,
    val durationSec: Double?,
    val season: Int?,
    val episode: Int?
)

class PlayerBridge(
    private val onEvent: (PlayerBridgeEvent) -> Unit
) {
    companion object {
        private const val TAG = "PlayerBridge"
    }

    @JavascriptInterface
    fun onPlayerEvent(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val event = obj.optString("event", "progress").ifBlank { "progress" }
            val pos = if (obj.isNull("currentTime")) null else obj.getDouble("currentTime")
            val dur = if (obj.isNull("duration")) null else obj.getDouble("duration")
            val season = if (obj.isNull("season")) null else obj.getInt("season")
            val episode = if (obj.isNull("episode")) null else obj.getInt("episode")
            val provider = obj.optString("provider", "")

            Log.d(
                TAG,
                "[PlayerBridge] Event: event=$event, provider=$provider, pos=${pos}s, dur=${dur}s, season=$season, episode=$episode"
            )
            onEvent(
                PlayerBridgeEvent(
                    event = event,
                    provider = provider,
                    positionSec = pos,
                    durationSec = dur,
                    season = season,
                    episode = episode
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "[PlayerBridge] Failed to parse event: ${e.message}")
        }
    }
}
