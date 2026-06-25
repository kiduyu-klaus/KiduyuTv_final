package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv // Added this import
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import android.widget.FrameLayout
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

data class BottomNavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun MobileBottomNavigation(
    navController: NavController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem(Screen.Home.route, Icons.Default.Home, "Home"),
        BottomNavItem(Screen.Movies.route, Icons.Default.Movie, "Movies"),
        BottomNavItem(Screen.LiveTv.route, Icons.Default.LiveTv, "Live Tv"), // Updated icon here
        BottomNavItem(Screen.TvShows.route, Icons.Default.Tv, "TV Shows"),
        BottomNavItem(Screen.MyList.route, Icons.Default.PlaylistPlay, "My List")
    )

    Column(modifier = modifier) {
        NavigationBar(
            containerColor = BackgroundDark,
            contentColor = TextPrimary
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    icon = {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selected) PrimaryRed else TextSecondary
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = if (selected) PrimaryRed else TextSecondary
                        )
                    },
                    selected = selected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryRed,
                        selectedTextColor = PrimaryRed,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = SurfaceDark
                    )
                )
            }
        }

        // Show banner ad only on phone flavour via AdFallbackDispatcher
        if (BuildConfig.FLAVOR == "phone") {
            val context = LocalContext.current
            val activity = context as? Activity ?: return
            val bannerContainer = remember { mutableStateOf<FrameLayout?>(null) }
            DisposableEffect(Unit) {
                onDispose {
                    bannerContainer.value?.let { container ->
                        for (index in 0 until container.childCount) {
                            (container.getChildAt(index) as? com.google.android.gms.ads.AdView)?.destroy()
                        }
                        container.removeAllViews()
                    }
                    bannerContainer.value = null
                }
            }
            if (!SettingsManager(context).isAdsDisabled()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(0xFF141414)),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            bannerContainer.value = this
                            AdFallbackDispatcher.loadBanner(
                                activity,
                                this,
                                AdFallbackDispatcher.BannerNetwork.ADMOB
                            )
                        }
                    }
                )
            }
        }
    }
}

