package com.kiduyuk.klausk.kiduyutv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.desktop.DesktopServices
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopRoute
import com.kiduyuk.klausk.kiduyutv.desktop.webview.DesktopWebViewRuntime
import com.kiduyuk.klausk.kiduyutv.desktop.webview.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.desktop.webview.WebViewRuntimeState
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData

private const val DESKTOP_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

@Composable
fun WebPlayerScreen(services: DesktopServices, route: DesktopRoute.WebPlayer) {
    val runtimeState by DesktopWebViewRuntime.state.collectAsState()
    LaunchedEffect(Unit) { DesktopWebViewRuntime.ensureInitialized() }

    when (val state = runtimeState) {
        WebViewRuntimeState.Ready -> EmbeddedProviderPlayer(services, route)
        is WebViewRuntimeState.Preparing -> BrowserRuntimeStatus(
            services = services,
            route = route,
            message = state.message,
            detail = state.downloadPercent?.let { "${it.toInt()}%" }
        )
        is WebViewRuntimeState.Failed -> BrowserRuntimeStatus(
            services = services,
            route = route,
            message = "WebView playback is unavailable",
            detail = state.message,
            canRetry = true
        )
        is WebViewRuntimeState.RestartRequired -> BrowserRuntimeStatus(
            services = services,
            route = route,
            message = "Restart required",
            detail = state.message,
            showProgress = false
        )
        WebViewRuntimeState.NotStarted -> BrowserRuntimeStatus(
            services = services,
            route = route,
            message = "Preparing browser engine…"
        )
    }
}

@Composable
private fun EmbeddedProviderPlayer(services: DesktopServices, route: DesktopRoute.WebPlayer) {
    val request = route.request
    val resumeSeconds = remember(request) {
        (services.library.progress(request)?.positionMs ?: 0L) / 1_000L
    }
    val iframeHtml = remember(route, resumeSeconds) {
        StreamProviderManager.generateIframeHtml(
            providerName = route.providerName,
            tmdbId = request.tmdbId,
            isTv = request.mediaType == com.kiduyuk.klausk.kiduyutv.desktop.model.MediaType.SERIES,
            season = request.season,
            episode = request.episode,
            timestamp = resumeSeconds
        )
    }
    val baseUrl = remember(route.providerName) { StreamProviderManager.getBaseUrl(route.providerName) }

    key(route.providerName, request.tmdbId, request.season, request.episode) {
        val webViewState = rememberWebViewStateWithHTMLData(
            data = iframeHtml,
            baseUrl = baseUrl,
            encoding = "utf-8",
            mimeType = "text/html"
        )
        val webViewNavigator = rememberWebViewNavigator()
        DisposableEffect(webViewState) {
            webViewState.webSettings.apply {
                isJavaScriptEnabled = true
                customUserAgentString = DESKTOP_BROWSER_USER_AGENT
                supportZoom = false
                desktopWebSettings.apply {
                    offScreenRendering = true
                    transparent = false
                    disablePopupWindows = true
                }
            }
            onDispose { }
        }

        Column(Modifier.fillMaxSize().background(Color.Black)) {
            ScreenHeader(
                title = request.title,
                onBack = { services.navigator.pop() },
                actions = {
                    Text(route.providerName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TvActionButton("Reload", webViewNavigator::reload)
                    TvActionButton("Providers", { services.navigator.pop() })
                }
            )
            if (webViewState.loadingState !is LoadingState.Finished) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            WebView(
                state = webViewState,
                navigator = webViewNavigator,
                captureBackPresses = false,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BrowserRuntimeStatus(
    services: DesktopServices,
    route: DesktopRoute.WebPlayer,
    message: String,
    detail: String? = null,
    canRetry: Boolean = false,
    showProgress: Boolean = true
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RemoteImage(
            tmdbImage(route.request.backdropPath, "original"),
            route.request.title,
            Modifier.fillMaxSize(),
            ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)))
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(route.request.title, { services.navigator.pop() })
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (showProgress) CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color.White, style = MaterialTheme.typography.titleLarge)
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(detail, color = Color.LightGray)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canRetry) {
                        TvActionButton("Retry", { DesktopWebViewRuntime.ensureInitialized(forceRetry = true) })
                    }
                    TvActionButton("Exit", { services.navigator.pop() })
                }
            }
        }
    }
}
