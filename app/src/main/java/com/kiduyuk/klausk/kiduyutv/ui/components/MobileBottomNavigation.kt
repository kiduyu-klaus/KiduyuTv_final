package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen

data class BottomNavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun MobileBottomNavigation(navController: NavController, currentRoute: String?) {
    val items = listOf(
        BottomNavItem(Screen.Home.route, Icons.Default.Home, "Home"),
        BottomNavItem(Screen.Movies.route, Icons.Default.Movie, "Movies"),
        BottomNavItem(Screen.TvShows.route, Icons.Default.Tv, "TV Shows"),
        BottomNavItem(Screen.Search.route, Icons.Default.Search, "Search"),
        BottomNavItem(Screen.Settings.route, Icons.Default.Settings, "Settings")
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
