package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.components.BannerAdView
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
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
        BottomNavItem(Screen.LiveTv.route, Icons.Default.LiveTv, "Live Tv"),
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

        // Show banner ad only on phone flavour, and only if the user has not
        // disabled ads in settings. We host BannerAdView directly inside the
        // Compose hierarchy (no intermediate FrameLayout / ComposeView) and
        // let it measure to its natural adaptive height — previous code
        // clamped the container to 60.dp which clipped wider adaptive banners.
        val context = LocalContext.current
        if (BuildConfig.FLAVOR == "phone" &&
            !SettingsManager(context).isAdsDisabled()
        ) {
            BannerAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color(0xFF141414))
            )
        }
    }
}
