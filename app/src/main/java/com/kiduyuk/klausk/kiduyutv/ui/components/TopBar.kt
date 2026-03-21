package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary

/**
 * Data class representing a navigation item in the top bar.
 * @param title The display name of the item.
 * @param icon The icon associated with the item.
 * @param route The navigation route associated with the item.
 */
data class NavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

/**
 * The top navigation bar component used across the main screens.
 * Displays the app logo, main navigation items (Home, Movies, TV Shows, My List),
 * and utility icons like Search and Settings.
 *
 * @param selectedRoute The currently active navigation route.
 * @param onNavItemClick Lambda to handle navigation when an item is clicked.
 * @param modifier Modifier for the layout.
 */
@Composable
fun TopBar(
    selectedRoute: String,
    onNavItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // List of main navigation destinations.
    val navItems = listOf(
        NavItem("Home", Icons.Default.Home, "home"),
        NavItem("Movies", Icons.Default.Movie, "movies"),
        NavItem("TV Shows", Icons.Default.Tv, "tv_shows"),
        NavItem("My List", Icons.AutoMirrored.Filled.List, "my_list")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.8f))
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Logo and Title Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = PrimaryRed,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "K",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
            Text(
                text = "KiduyuTV",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        // Navigation Items Section
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                NavBarItem(
                    item = item,
                    isSelected = selectedRoute == item.route,
                    onClick = { onNavItemClick(item.route) }
                )
            }
        }

        // Utility Icons Section: Search and Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Icon Button
            IconButton(
                onClick = { /* TODO: Implement search functionality */ }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            // Settings Icon Button
            IconButton(
                onClick = { /* TODO: Implement settings functionality */ }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Individual navigation item component within the TopBar.
 * Highlights the item if it is currently selected.
 *
 * @param item The navigation item data.
 * @param isSelected Whether this item is the currently active route.
 * @param onClick Lambda to handle click events.
 */
@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) PrimaryRed else Color.Transparent,
        modifier = Modifier.padding(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}
