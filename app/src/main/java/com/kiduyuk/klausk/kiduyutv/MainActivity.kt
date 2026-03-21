package com.kiduyuk.klausk.kiduyutv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kiduyuk.klausk.kiduyutv.ui.navigation.NavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiduyuTvTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Define bottom navigation items
                val bottomNavItems = listOf(
                    BottomNavItem(
                        route = Screen.Home.route,
                        icon = Icons.Default.Home,
                        label = "Home"
                    ),
                    BottomNavItem(
                        route = Screen.Movies.route,
                        icon = Icons.Default.Movie,
                        label = "Movies"
                    ),
                    BottomNavItem(
                        route = Screen.TvShows.route,
                        icon = Icons.Default.Tv,
                        label = "TV Shows"
                    ),
                    BottomNavItem(
                        route = Screen.MyList.route,
                        icon = Icons.Default.List,
                        label = "My List"
                    )
                )

                // Determine if bottom nav should be shown (only on main screens)
                val showBottomNav = currentRoute in bottomNavItems.map { it.route }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    NavGraph(navController = navController)

                    // Bottom Navigation Bar
                    if (showBottomNav) {
                        NavigationBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            containerColor = BackgroundDark,
                            contentColor = TextPrimary
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route

                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            // Pop up to the start destination of the graph to
                                            // avoid building up a large stack of destinations
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            // Avoid multiple copies of the same destination when
                                            // reselecting the same item
                                            launchSingleTop = true
                                            // Restore state when reselecting a previously selected item
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryRed,
                                        selectedTextColor = PrimaryRed,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = BackgroundDark
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)
