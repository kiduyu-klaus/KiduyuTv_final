package com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileBottomNavigation
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.IptvPlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv.LiveTvScreen

@Composable
fun MobileLiveTvScreen(
    navController: NavController,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = { MobileBottomNavigation(navController, currentRoute) }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            LiveTvScreen(
                onChannelPlay = { channel ->
                    val intent = IptvPlayerActivity.createIntent(
                        context,
                        channel.name,
                        channel.url,
                        channel.logo,
                        channel.tvgId,
                        channel.tvgName,
                        channel.group
                    )
                    context.startActivity(intent)
                },
                onNavigate = { route -> navController.navigate(route) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    }
}
