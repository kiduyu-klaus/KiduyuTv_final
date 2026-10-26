package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

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
    }
}
