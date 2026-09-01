package com.kiduyuk.klausk.kiduyutv.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.kiduyuk.klausk.kiduyutv.desktop.data.*
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopNavigator

fun main() = application {
    val scope = rememberCoroutineScope()
    val services = remember {
        val settings = DesktopSettings()
        DesktopServices(
            settings = settings,
            tmdb = TmdbClient(settings),
            providers = ProvidersClient(settings),
            iptv = IptvClient(),
            library = LocalLibrary(),
            navigator = DesktopNavigator(),
            darkTheme = mutableStateOf(settings.darkTheme)
        )
    }
    Window(
        title = "KiduyuTV",
        state = WindowState(width = 1366.dp, height = 768.dp),
        onCloseRequest = ::exitApplication
    ) {
        val darkTheme by services.darkTheme
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme(
                primary = Color(0xFFE50914),
                onPrimary = Color.White,
                secondary = Color(0xFFFFB4AB),
                background = Color(0xFF080808),
                surface = Color(0xFF151515),
                surfaceVariant = Color(0xFF242424),
                onBackground = Color(0xFFF5F5F5),
                onSurface = Color.White,
                onSurfaceVariant = Color.White
            ) else lightColorScheme(
                primary = Color(0xFFB00020),
                onPrimary = Color.White,
                secondary = Color(0xFF7A4A00),
                onSecondary = Color.White,
                background = Color(0xFFF8F8F8),
                surface = Color.White,
                surfaceVariant = Color(0xFFECECEC),
                onBackground = Color(0xFF171717),
                onSurface = Color(0xFF171717),
                onSurfaceVariant = Color(0xFF404040)
            )
        ) {
            val appState = rememberDesktopAppState(services)
            KiduyuDesktopApp(services)
            DisposableEffect(Unit) {
                onDispose { appState.dispose() }
            }
        }
    }
}

data class DesktopServices(
    val settings: DesktopSettings,
    val tmdb: TmdbClient,
    val providers: ProvidersClient,
    val iptv: IptvClient,
    val library: LocalLibrary,
    val navigator: DesktopNavigator,
    val darkTheme: MutableState<Boolean>
)
