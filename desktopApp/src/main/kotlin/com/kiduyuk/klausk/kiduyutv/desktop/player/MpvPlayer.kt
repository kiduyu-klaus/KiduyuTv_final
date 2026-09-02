package com.kiduyuk.klausk.kiduyutv.desktop.player

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopSettings
import com.kiduyuk.klausk.kiduyutv.desktop.model.StreamItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import kotlin.io.path.isRegularFile

data class MpvState(
    val running: Boolean = false,
    val playing: Boolean = false,
    val videoOutputReady: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val title: String = "",
    val videoFill: Boolean = false,
    val error: String? = null
)

class MpvPlayer(
    private val settings: DesktopSettings,
    private val scope: CoroutineScope
) : AutoCloseable {
    private val gson = Gson()
    private val mutableState = MutableStateFlow(MpvState())
    val state: StateFlow<MpvState> = mutableState.asStateFlow()
    private val commands = LinkedBlockingQueue<String>()
    private var process: Process? = null
    private var ipcJob: Job? = null
    private var processJob: Job? = null
    private var processOutputJob: Job? = null
    private var intentionallyStopped = false

    private data class VideoOutputProfile(
        val id: String,
        val videoOutput: String,
        val gpuContext: String? = null
    )

    // Direct3D 9 is intentionally first: it is available in the bundled mpv build and does
    // not require the OpenGL 2.1/3.x context that fails on older Windows display drivers.
    private val videoOutputProfiles = listOf(
        VideoOutputProfile(id = "direct3d", videoOutput = "direct3d"),
        VideoOutputProfile(id = "d3d11", videoOutput = "gpu", gpuContext = "d3d11"),
        VideoOutputProfile(id = "winvk", videoOutput = "gpu", gpuContext = "winvk"),
        VideoOutputProfile(id = "win", videoOutput = "gpu", gpuContext = "win")
    )

    // Access violation / other native-crash-style exit codes we treat as
    // "this gpu-context doesn't work here" rather than a stream problem.
    private val crashExitCodes = setOf(-1073741819, -1073740791) // 0xC0000005, 0xC0000409

    private var currentStream: StreamItem? = null
    private var currentStartPositionMs: Long = 0L
    private var currentWindowId: Long? = null
    private var videoOutputAttemptIndex: Int = 0
    private var playStartedAtMs: Long = 0L
    private var videoFill = false

    fun play(stream: StreamItem, startPositionMs: Long = 0L, windowId: Long? = null) {
        currentStream = stream
        currentStartPositionMs = startPositionMs
        currentWindowId = windowId
        videoOutputAttemptIndex = 0
        startProcess(stream, startPositionMs, windowId)
    }

    private fun startProcess(stream: StreamItem, startPositionMs: Long, windowId: Long?) {
        stop()
        intentionallyStopped = false
        val outputProfile = videoOutputProfiles[videoOutputAttemptIndex]
        mutableState.value = MpvState(
            running = true,
            title = stream.displayName,
            videoFill = videoFill
        )
        val pipe = "\\\\.\\pipe\\kiduyutv-${System.nanoTime()}"
        val args = mutableListOf(
            resolveExecutable().toString(),
            "--input-ipc-server=$pipe",
            // Avoid incompatible user-level mpv settings. The first profile uses the bundled
            // Direct3D renderer and therefore does not depend on an OpenGL context.
            "--no-config",
            "--force-window=yes",
            "--vo=${outputProfile.videoOutput}",
            "--keep-open=no",
            "--no-osc",
            "--no-osd-bar",
            "--keepaspect=yes",
            "--panscan=${if (videoFill) 1.0 else 0.0}",
            // Prevent video frames or letterbox areas from carrying alpha into
            // the embedded Windows child surface.
            "--background=color",
            "--background-color=#FF000000",
            // Avoid hardware-decoder/DXVA native crashes with provider HLS streams on Windows.
            "--hwdec=no",
            "--cache=yes",
            "--cache-secs=30",
            "--demuxer-max-bytes=256MiB",
            "--demuxer-readahead-secs=30",
            "--title=${stream.displayName}",
            "--alang=eng,en",
            "--slang=${settings.preferredSubtitleLanguage},eng,en",
            "--sub-auto=all"
        )
        outputProfile.gpuContext?.let { args += "--gpu-context=$it" }
        if (startPositionMs > 0L) args += "--start=${startPositionMs / 1000.0}"
        windowId?.let {
            args += "--wid=$it"
            args += "--no-border"
        }

        val userAgent = stream.headers.valueIgnoreCase("User-Agent")
            ?.takeIf(::safeHeader)
        val referrer = stream.headers.valueIgnoreCase("Referer")
            ?: stream.headers.valueIgnoreCase("Referrer")
        val origin = stream.headers.valueIgnoreCase("Origin")
            ?.takeIf(::safeHeader)
        val customHeaders = stream.headers.entries
            .filterNot {
                it.key.equals("User-Agent", true) ||
                    it.key.equals("Referer", true) ||
                    it.key.equals("Referrer", true) ||
                    it.key.equals("Origin", true)
            }
            .filter { safeHeader(it.key) && safeHeader(it.value) }
            .map { "${it.key}: ${it.value}" }
            .toMutableList()
        userAgent?.let { args += "--user-agent=$it" }
        referrer?.takeIf(::safeHeader)?.let { args += "--referrer=$it" }
        origin?.let { customHeaders += "Origin: $it" }
        if (customHeaders.isNotEmpty()) {
            args += "--http-header-fields=${customHeaders.joinToString(",")}"
        }
        args += stream.url

        try {
            val startedProcess = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            process = startedProcess
            playStartedAtMs = 0L
            processOutputJob = scope.launch(Dispatchers.IO) {
                var videoOutputFallbackScheduled = false
                startedProcess.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.info(
                                "mpv output: {}",
                                line.take(2_000)
                            )
                        }
                        if (!videoOutputFallbackScheduled && line.contains("Video: no video", ignoreCase = true)) {
                            videoOutputFallbackScheduled = true
                            if (videoOutputAttemptIndex < videoOutputProfiles.lastIndex) {
                                val failedProfile = outputProfile.id
                                scope.launch(Dispatchers.IO) {
                                    if (process === startedProcess) {
                                        videoOutputAttemptIndex += 1
                                        val nextProfile = videoOutputProfiles[videoOutputAttemptIndex]
                                        com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.warn(
                                            "No video output with profile={}; retrying with profile={}",
                                            failedProfile,
                                            nextProfile.id
                                        )
                                        currentStream?.let {
                                            startProcess(it, currentStartPositionMs, currentWindowId)
                                        }
                                    }
                                }
                            } else {
                                mutableState.value = mutableState.value.copy(
                                    playing = false,
                                    videoOutputReady = false,
                                    error = "Windows could not initialize a compatible video renderer."
                                )
                            }
                        }
                    }
                }
            }
            connectIpc(pipe, startedProcess)
            processJob = scope.launch(Dispatchers.IO) {
                val exitCode = startedProcess.waitFor()
                com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.info(
                    "mpv process exited code={} intentionallyStopped={} videoOutputProfile={}",
                    exitCode,
                    intentionallyStopped,
                    outputProfile.id
                )
                if (process !== startedProcess) return@launch

                val elapsedSincePlaying = playStartedAtMs.takeIf { it > 0L }
                    ?.let { System.currentTimeMillis() - it }
                val looksLikeGpuContextCrash = !intentionallyStopped &&
                    exitCode in crashExitCodes &&
                    (elapsedSincePlaying == null || elapsedSincePlaying < 5_000L) &&
                    videoOutputAttemptIndex < videoOutputProfiles.lastIndex

                if (looksLikeGpuContextCrash) {
                    com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.warn(
                        "video-output profile={} crashed early (code={}), retrying with next candidate",
                        outputProfile.id,
                        exitCode
                    )
                    videoOutputAttemptIndex += 1
                    val stream = currentStream
                    if (stream != null) {
                        startProcess(stream, currentStartPositionMs, currentWindowId)
                    }
                } else {
                    if (!intentionallyStopped && mutableState.value.videoOutputReady) {
                        // Persist a renderer only after mpv has exposed real video output.
                        settings.mpvGpuContext = outputProfile.id
                    }
                    val previous = mutableState.value
                    mutableState.value = previous.copy(
                        running = false,
                        playing = false,
                        error = if (!intentionallyStopped && exitCode != 0) {
                            "mpv exited with code $exitCode"
                        } else previous.error
                    )
                }
            }
        } catch (error: Exception) {
            mutableState.value = MpvState(error = "Could not start mpv: ${error.message}")
        }
    }

    fun togglePause() = enqueue("cycle", "pause")
    fun seekBy(seconds: Int) = enqueue("seek", seconds, "relative")
    fun seekTo(positionMs: Long) = enqueue("set_property", "time-pos", positionMs / 1000.0)
    fun cycleAudio() = enqueue("cycle", "aid")
    fun cycleSubtitle() = enqueue("cycle", "sid")
    fun cycleVideo() = enqueue("cycle", "vid")
    fun toggleFullscreen() = enqueue("cycle", "fullscreen")
    fun toggleVideoFit() {
        videoFill = !videoFill
        mutableState.value = mutableState.value.copy(videoFill = videoFill)
        enqueue("set_property", "keepaspect", true)
        enqueue("set_property", "panscan", if (videoFill) 1.0 else 0.0)
    }
    fun addSubtitle(pathOrUrl: String) = enqueue("sub-add", pathOrUrl, "select")

    fun stop() {
        intentionallyStopped = true
        enqueue("quit")
        ipcJob?.cancel()
        processJob?.cancel()
        processOutputJob?.cancel()
        process?.destroy()
        process = null
        ipcJob = null
        processJob = null
        processOutputJob = null
        commands.clear()
        mutableState.value = mutableState.value.copy(running = false, playing = false)
    }

    override fun close() = stop()

    private fun connectIpc(pipe: String, startedProcess: Process) {
        ipcJob = scope.launch(Dispatchers.IO) {
            val connection = withTimeoutOrNull(8_000L) {
                while (isActive) {
                    val opened = runCatching { RandomAccessFile(pipe, "rw") }.getOrNull()
                    if (opened != null) return@withTimeoutOrNull opened
                    delay(100L)
                }
                null
            }
            if (connection == null) {
                mutableState.value = mutableState.value.copy(error = "Could not connect to mpv control pipe")
                com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.error(
                    "Timed out connecting to mpv IPC pipe; terminating process"
                )
                if (startedProcess.isAlive) startedProcess.destroy()
                return@launch
            }
            connection.use { ipc ->
                listOf(
                    1 to "time-pos",
                    2 to "duration",
                    3 to "pause",
                    4 to "media-title",
                    5 to "demuxer-cache-time",
                    6 to "paused-for-cache",
                    7 to "video-out-params"
                ).forEach { (id, property) ->
                    ipc.writeLine(gson.toJson(mapOf("command" to listOf("observe_property", id, property))))
                }

                val writer = launch(Dispatchers.IO) {
                    while (isActive && startedProcess.isAlive) {
                        val command = try {
                            commands.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } catch (e: InterruptedException) {
                            null
                        }
                        if (command != null) {
                            val sent = runCatching { ipc.writeLine(command) }
                            if (sent.isFailure) return@launch
                        }
                    }
                }
                try {
                    while (isActive && startedProcess.isAlive) {
                        val line = runCatching { ipc.readLine() }.getOrNull() ?: break
                        handleEvent(line)
                    }
                } finally {
                    writer.cancel()
                }
            }
        }
    }

    private fun handleEvent(line: String) {
        val json = runCatching { gson.fromJson(line, JsonObject::class.java) }.getOrNull() ?: return
        val event = json.get("event")?.asString ?: return
        val current = mutableState.value
        when (event) {
            "start-file" -> {
                mutableState.value = current.copy(
                    playing = false,
                    videoOutputReady = false,
                    error = null
                )
                return
            }
            "file-loaded" -> {
                mutableState.value = current.copy(error = null)
                return
            }
            "playback-restart" -> {
                mutableState.value = current.copy(playing = current.running, error = null)
                if (current.running) playStartedAtMs = System.currentTimeMillis()
                return
            }
            "end-file" -> {
                mutableState.value = current.copy(playing = false, videoOutputReady = false)
                return
            }
            "property-change" -> Unit
            else -> return
        }
        val name = json.get("name")?.asString ?: return
        val data = json.get("data")
        mutableState.value = when (name) {
            "time-pos" -> current.copy(
                positionMs = data?.takeUnless { it.isJsonNull }?.asDouble?.times(1000)?.toLong() ?: 0L,
                playing = current.running
            )
            "duration" -> current.copy(
                durationMs = data?.takeUnless { it.isJsonNull }?.asDouble?.times(1000)?.toLong() ?: 0L
            )
            "pause" -> current.copy(
                playing = if (data?.takeUnless { it.isJsonNull }?.asBoolean == true) {
                    false
                } else {
                    current.playing
                }
            )
            "media-title" -> current.copy(title = data?.takeUnless { it.isJsonNull }?.asString ?: current.title)
            "demuxer-cache-time" -> current.copy(
                bufferedMs = data?.takeUnless { it.isJsonNull }?.asDouble?.times(1000)?.toLong() ?: 0L
            )
            "paused-for-cache" -> current.copy(
                playing = if (data?.takeUnless { it.isJsonNull }?.asBoolean == true) {
                    false
                } else {
                    current.playing
                }
            )
            "video-out-params" -> current.copy(
                videoOutputReady = data != null && !data.isJsonNull
            )
            else -> current
        }
    }

    private fun enqueue(vararg values: Any) {
        commands.offer(gson.toJson(mapOf("command" to values.toList())))
    }

    private fun resolveExecutable(): Path {
        val configured = settings.mpvPath.trim()
        if (configured.isNotBlank()) {
            val path = Paths.get(configured)
            if (path.isRegularFile()) return path
        }
        val resources = System.getProperty("compose.application.resources.dir")
        if (!resources.isNullOrBlank()) {
            val bundled = Paths.get(resources).resolve("mpv").resolve("mpv.exe")
            if (Files.isRegularFile(bundled)) return bundled
        }
        return configured.takeIf { it.isNotBlank() }?.let(Paths::get) ?: Paths.get("mpv.exe")
    }

    private fun safeHeader(value: String): Boolean =
        value.isNotBlank() && '\r' !in value && '\n' !in value

    private fun RandomAccessFile.writeLine(text: String) {
        write((text + "\n").toByteArray(Charsets.UTF_8))
    }
}

private fun Map<String, String>.valueIgnoreCase(name: String): String? =
    entries.firstOrNull { it.key.equals(name, true) }?.value

object StreamRanker {
    fun automatic(streams: List<StreamItem>): StreamItem? = streams
        .filter { resolution(it.quality) in 0..1080 }
        .sortedWith(
            compareByDescending<StreamItem> { resolution(it.quality) }
                .thenByDescending { it.type.equals("hls", true) }
        )
        .firstOrNull() ?: streams.firstOrNull()

    fun sorted(streams: List<StreamItem>): List<StreamItem> =
        streams.sortedByDescending { resolution(it.quality) }

    private fun resolution(quality: String): Int =
        Regex("(\\d{3,4})").find(quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
