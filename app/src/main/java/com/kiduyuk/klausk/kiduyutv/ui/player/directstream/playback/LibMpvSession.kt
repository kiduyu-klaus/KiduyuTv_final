package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

data class MpvPlaybackSnapshot(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val paused: Boolean = true,
    val buffering: Boolean = false,
    val eof: Boolean = false,
    val error: String? = null,
    val tracks: List<MpvTrack> = emptyList()
)

data class MpvTrack(
    val id: Int,
    val type: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
    val external: Boolean
)

/** A small libmpv session. Native calls are serialized on Dispatchers.IO. */
internal class LibMpvSession(context: Context) {
    @Suppress("unused")
    private val appContext = context.applicationContext
    private val nativeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + nativeDispatcher)
    private val handle = AtomicLong(0)
    private var poller: Job? = null
    private val mutableSnapshot = MutableStateFlow(MpvPlaybackSnapshot())
    val snapshot: StateFlow<MpvPlaybackSnapshot> = mutableSnapshot.asStateFlow()

    suspend fun initialize(surface: Surface) = withContext(nativeDispatcher) {
        nativeLoadFailure?.let { throw IllegalStateException("Could not load libmpv native libraries", it) }
        check(surface.isValid) { "Playback surface is not valid" }
        val existing = handle.get()
        if (existing != 0L) {
            check(nativeAttach(existing, surface)) { "libmpv could not reattach the Android video surface" }
            nativeCommand(existing, arrayOf("set", "vo", "gpu"))
            nativeCommand(existing, arrayOf("set", "force-window", "yes"))
            return@withContext
        }
        val created = nativeCreate()
        check(created != 0L) { "libmpv initialization failed" }
        check(nativeAttach(created, surface)) {
            nativeRelease(created)
            "libmpv could not attach the Android video surface"
        }
        handle.set(created)
        poller = scope.launch {
            while (isActive && handle.get() == created) {
                runCatching { mutableSnapshot.value = decodeSnapshot(nativeSnapshot(created)) }
                    .onFailure { mutableSnapshot.value = mutableSnapshot.value.copy(error = it.message) }
                delay(250)
            }
        }
    }

    fun detachSurface() {
        val current = handle.get()
        if (current != 0L) scope.launch {
            if (handle.get() == current) {
                nativeCommand(current, arrayOf("set", "vo", "null"))
                nativeCommand(current, arrayOf("set", "force-window", "no"))
                nativeDetach(current)
            }
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0) submit {
            nativeCommand(it, arrayOf("set", "android-surface-size", "${width}x$height"))
        }
    }

    fun load(url: String, headers: Map<String, String>, subtitles: List<Pair<String, String>> = emptyList(), startPositionMs: Long = 0) {
        require(url.isNotBlank()) { "URL must not be blank" }
        require(startPositionMs >= 0) { "startPositionMs must be non-negative" }
        mutableSnapshot.value = mutableSnapshot.value.copy(
            positionMs = startPositionMs,
            durationMs = 0L,
            loading = true,
            buffering = true,
            eof = false,
            error = null,
            tracks = emptyList()
        )
        submit { nativeLoad(it, url, headers, subtitles, startPositionMs) }
    }
    fun togglePause() = submit { nativeCommand(it, arrayOf("cycle", "pause")) }
    fun setPaused(paused: Boolean) = submit { nativeCommand(it, arrayOf("set", "pause", if (paused) "yes" else "no")) }
    fun seekTo(positionMs: Long) = seek(positionMs, "absolute+exact")
    fun seekBy(deltaMs: Long) = seek(deltaMs, "relative+exact")
    private fun seek(ms: Long, mode: String) = submit {
        nativeCommand(it, arrayOf("seek", (ms.toDouble() / 1000.0).toString(), mode))
    }
    fun selectTrack(id: Int, type: String) {
        val property = when (type.lowercase()) {
            "audio" -> "aid"
            "video" -> "vid"
            "sub", "subtitle", "text" -> "sid"
            else -> throw IllegalArgumentException("Unsupported track type: $type")
        }
        submit { nativeCommand(it, arrayOf("set", property, id.toString())) }
    }
    fun addSubtitle(url: String, label: String) = submit { nativeCommand(it, arrayOf("sub-add", url, "select", label)) }
    fun setMuted(muted: Boolean) = submit { nativeCommand(it, arrayOf("set", "mute", if (muted) "yes" else "no")) }
    fun setAspectMode(mode: String) {
        require(mode in setOf("auto", "16:9", "4:3", "21:9", "1:1")) { "Unsupported aspect mode: $mode" }
        val ratio = when (mode) {
            "auto" -> "-1"
            "16:9" -> "1.7777778"
            "4:3" -> "1.3333333"
            "21:9" -> "2.3333333"
            else -> "1.0"
        }
        submit { nativeCommand(it, arrayOf("set", "video-aspect-override", ratio)) }
    }
    fun release() {
        poller?.cancel()
        val old = handle.getAndSet(0)
        if (old != 0L) scope.launch {
            runCatching { nativeRelease(old) }
            mutableSnapshot.value = MpvPlaybackSnapshot()
            scope.cancel()
        }
        else {
            mutableSnapshot.value = MpvPlaybackSnapshot()
            scope.cancel()
        }
    }
    private fun submit(block: (Long) -> Unit) {
        val current = handle.get()
        if (current != 0L) scope.launch { if (handle.get() == current) block(current) }
    }
    private fun decodeSnapshot(raw: String): MpvPlaybackSnapshot {
        val root = JSONObject(raw)
        val tracksJson = root.optJSONArray("tracks")
        val tracks = buildList {
            if (tracksJson != null) for (i in 0 until tracksJson.length()) {
                val t = tracksJson.getJSONObject(i)
                add(MpvTrack(t.optInt("id"), t.optString("type"), t.optString("label"),
                    t.optString("language").takeUnless { it == "null" || it.isEmpty() },
                    t.optBoolean("selected"), t.optBoolean("external")))
            }
        }
        return MpvPlaybackSnapshot(
            positionMs = root.optLong("positionMs"),
            durationMs = root.optLong("durationMs"),
            loading = root.optBoolean("loading"),
            paused = root.optBoolean("paused", true),
            buffering = root.optBoolean("buffering"),
            eof = root.optBoolean("eof"),
            error = root.optString("error").takeUnless { it == "null" || it.isEmpty() },
            tracks = tracks
        )
    }

    private external fun nativeCreate(): Long
    private external fun nativeAttach(handle: Long, surface: Surface): Boolean
    private external fun nativeDetach(handle: Long)
    private external fun nativeLoad(handle: Long, url: String, headers: Map<String, String>, subtitles: List<Pair<String, String>>, startPositionMs: Long)
    private external fun nativeCommand(handle: Long, args: Array<String>)
    private external fun nativeSnapshot(handle: Long): String
    private external fun nativeRelease(handle: Long)

    companion object {
        private val nativeLoadFailure: Throwable? = runCatching {
            System.loadLibrary("c++_shared")
            System.loadLibrary("mpv")
            System.loadLibrary("kiduyu_mpv")
        }.exceptionOrNull()
    }
}
