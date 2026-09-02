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
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val title: String = "",
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

    // These contexts are supported by the bundled Windows mpv runtime.
    // ANGLE is first because it can fall back to the software WARP renderer
    // on machines without a usable hardware GPU. Native D3D11 and WinVk are
    // retained as fallbacks for systems where ANGLE is unavailable or crashes.
    private val gpuContextCandidates = listOf("angle", "d3d11", "winvk")

    // Access violation / other native-crash-style exit codes we treat as
    // "this gpu-context doesn't work here" rather than a stream problem.
    private val crashExitCodes = setOf(-1073741819, -1073740791) // 0xC0000005, 0xC0000409

    private var currentStream: StreamItem? = null
    private var currentStartPositionMs: Long = 0L
    private var currentWindowId: Long? = null
    private var gpuContextAttemptIndex: Int = 0
    private var playStartedAtMs: Long = 0L

    fun play(stream: StreamItem, startPositionMs: Long = 0L, windowId: Long? = null) {
        currentStream = stream
        currentStartPositionMs = startPositionMs
        currentWindowId = windowId
        gpuContextAttemptIndex = candidateStartIndex()
        startProcess(stream, startPositionMs, windowId)
    }

    /**
     * Start from the cached working gpu-context for this machine if we have
     * one, otherwise start probing from the top of the candidate list.
     */
    private fun candidateStartIndex(): Int {
        val cached = settings.mpvGpuContext.trim()
        if (cached.isNotBlank()) {
            val idx = gpuContextCandidates.indexOf(cached)
            if (idx >= 0) return idx
        }
        return 0
    }

    private fun startProcess(stream: StreamItem, startPositionMs: Long, windowId: Long?) {
        stop()
        intentionallyStopped = false
        val gpuContext = gpuContextCandidates[gpuContextAttemptIndex]
        mutableState.value = MpvState(running = true, title = stream.displayName)
        val pipe = "\\\\.\\pipe\\kiduyutv-${System.nanoTime()}"
        val args = mutableListOf(
            resolveExecutable().toString(),
            "--input-ipc-server=$pipe",
            // Avoid incompatible user-level mpv settings. gpu-next supports
            // the bundled ANGLE, D3D11, and WinVk contexts; the selected
            // context may change between attempts on incompatible machines.
            "--no-config",
            "--force-window=yes",
            "--vo=gpu-next",
            "--gpu-context=$gpuContext",
            "--keep-open=no",
            "--no-osc",
            "--no-osd-bar",
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
                startedProcess.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.info(
                                "mpv output: {}",
                                line.take(2_000)
                            )
                        }
                    }
                }
            }
            connectIpc(pipe, startedProcess)
            processJob = scope.launch(Dispatchers.IO) {
                val exitCode = startedProcess.waitFor()
                com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.info(
                    "mpv process exited code={} intentionallyStopped={} gpuContext={}",
                    exitCode,
                    intentionallyStopped,
                    gpuContext
                )
                if (process !== startedProcess) return@launch

                val elapsedSincePlaying = playStartedAtMs.takeIf { it > 0L }
                    ?.let { System.currentTimeMillis() - it }
                val looksLikeGpuContextCrash = !intentionallyStopped &&
                    exitCode in crashExitCodes &&
                    (elapsedSincePlaying == null || elapsedSincePlaying < 5_000L) &&
                    gpuContextAttemptIndex < gpuContextCandidates.lastIndex

                if (looksLikeGpuContextCrash) {
                    com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog.logger.warn(
                        "gpu-context={} crashed early (code={}), retrying with next candidate",
                        gpuContext,
                        exitCode
                    )
                    gpuContextAttemptIndex += 1
                    val stream = currentStream
                    if (stream != null) {
                        startProcess(stream, currentStartPositionMs, currentWindowId)
                    }
                } else {
                    if (!intentionallyStopped && exitCode == 0) {
                        // Survived to a clean/normal exit on this gpu-context:
                        // remember it so future launches skip straight to it.
                        settings.mpvGpuContext = gpuContext
                    } else if (!intentionallyStopped && exitCode !in crashExitCodes) {
                        // Non-crash nonzero exit (e.g. stream error) on a
                        // gpu-context we've already seen play frames under
                        // is still evidence it's usable; remember it too.
                        if (elapsedSincePlaying != null) {
                            settings.mpvGpuContext = gpuContext
                        }
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
                    6 to "paused-for-cache"
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
                mutableState.value = current.copy(playing = false, error = null)
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
                mutableState.value = current.copy(playing = false)
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
                playing = current.running,
                error = null
            )
            "duration" -> current.copy(
                durationMs = data?.takeUnless { it.isJsonNull }?.asDouble?.times(1000)?.toLong() ?: 0L
            )
            "pause" -> current.copy(
                playing = if (data?.takeUnless { it.isJsonNull }?.asBoolean == true) {
                    false
                } else {
                    current.playing
                },
                error = null
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
