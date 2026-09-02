package com.kiduyuk.klausk.kiduyutv.desktop.webview

import dev.datlag.kcef.KCEF
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed interface WebViewRuntimeState {
    data object NotStarted : WebViewRuntimeState
    data class Preparing(val message: String, val downloadPercent: Float? = null) : WebViewRuntimeState
    data object Ready : WebViewRuntimeState
    data class RestartRequired(val message: String) : WebViewRuntimeState
    data class Failed(val message: String) : WebViewRuntimeState
}

/** Owns the single Chromium/JCEF runtime used by all desktop WebView player screens. */
object DesktopWebViewRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<WebViewRuntimeState>(WebViewRuntimeState.NotStarted)
    val state: StateFlow<WebViewRuntimeState> = mutableState.asStateFlow()

    private var initializationJob: Job? = null

    @Synchronized
    fun ensureInitialized(forceRetry: Boolean = false) {
        DesktopLog.logger.info(
            "WebView runtime initialization requested forceRetry={} currentState={}",
            forceRetry,
            mutableState.value
        )
        if (!forceRetry && mutableState.value == WebViewRuntimeState.Ready) return
        if (initializationJob?.isActive == true) return

        mutableState.value = WebViewRuntimeState.Preparing("Preparing browser engine…")
        initializationJob = runtimeScope.launch {
            val initializationSettled = AtomicBoolean(false)
            runCatching {
                val initializationCompleted = CompletableDeferred<Unit>()
                val bundledRoot = System.getProperty("compose.application.resources.dir")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::File)
                val appData = System.getenv("LOCALAPPDATA")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::File)
                    ?: File(System.getProperty("user.home"), "AppData/Local")
                val persistentRoot = File(appData, "KiduyuTV/WebView")
                val bundledInstallDir = bundledRoot?.let { File(it, "kcef-bundle") }
                val bundledEngineAvailable = bundledInstallDir
                    ?.takeIf { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }
                val installDir = bundledEngineAvailable ?: File(persistentRoot, "kcef-bundle")
                val cacheDir = File(persistentRoot, "cache")
                DesktopLog.logger.info(
                    "Initializing KCEF installDir={} cacheDir={} bundledEngineAvailable={}",
                    installDir,
                    cacheDir,
                    bundledEngineAvailable != null
                )

                KCEF.init(
                    builder = {
                        installDir(installDir)
                        progress {
                            onLocating {
                                mutableState.value = WebViewRuntimeState.Preparing("Locating browser engine…")
                            }
                            onDownloading { percent ->
                                DesktopLog.logger.info("KCEF downloading percent={}", percent)
                                mutableState.value = WebViewRuntimeState.Preparing(
                                    message = "Downloading browser engine…",
                                    downloadPercent = percent.coerceIn(0f, 100f)
                                )
                            }
                            onExtracting {
                                mutableState.value = WebViewRuntimeState.Preparing("Extracting browser engine…")
                            }
                            onInstall {
                                mutableState.value = WebViewRuntimeState.Preparing("Installing browser engine…")
                            }
                            onInitializing {
                                mutableState.value = WebViewRuntimeState.Preparing("Starting browser engine…")
                            }
                            onInitialized {
                                if (initializationSettled.compareAndSet(false, true)) {
                                    DesktopLog.logger.info("KCEF initialized successfully")
                                    mutableState.value = WebViewRuntimeState.Ready
                                    initializationCompleted.complete(Unit)
                                } else {
                                    DesktopLog.logger.warn("Ignoring late KCEF initialized callback")
                                }
                            }
                        }
                        settings {
                            cachePath = cacheDir.absolutePath
                            // Match compose-webview-multiplatform DesktopWebSettings defaults.
                            // The WebView composable controls off-screen rendering per browser.
                            windowlessRenderingEnabled = false
                        }
                        addArgs(
                            "--autoplay-policy=no-user-gesture-required",
                            "--disable-features=TranslateUI"
                        )
                    },
                    onError = { error ->
                        if (initializationSettled.compareAndSet(false, true)) {
                            DesktopLog.logger.error("KCEF initialization error", error)
                            mutableState.value = WebViewRuntimeState.Failed(
                                error?.message ?: "The browser engine could not be initialized."
                            )
                            initializationCompleted.complete(Unit)
                        }
                    },
                    onRestartRequired = {
                        if (initializationSettled.compareAndSet(false, true)) {
                            DesktopLog.logger.warn("KCEF requested an application restart")
                            mutableState.value = WebViewRuntimeState.RestartRequired(
                                "The browser engine was updated. Restart KiduyuTV to use WebView playback."
                            )
                            initializationCompleted.complete(Unit)
                        }
                    }
                )
                // KCEF.init may spend several minutes preparing Chromium on first run.
                // Do not impose a short timeout: cancelling this coroutine while KCEF is still
                // downloading or extracting leaves the runtime in an indeterminate state and
                // causes the UI to report a false initialization failure.
                initializationCompleted.await()
            }.onFailure { error ->
                if (initializationSettled.compareAndSet(false, true)) {
                    DesktopLog.logger.error("WebView runtime initialization failed", error)
                    mutableState.value = WebViewRuntimeState.Failed(
                        error.message ?: "The browser engine could not be initialized."
                    )
                }
            }
        }
    }

    fun dispose() {
        DesktopLog.logger.info("Disposing WebView runtime state={}", mutableState.value)
        initializationJob?.cancel()
        if (mutableState.value == WebViewRuntimeState.Ready) {
            runCatching { KCEF.disposeBlocking() }
        }
        runtimeScope.cancel()
    }
}
