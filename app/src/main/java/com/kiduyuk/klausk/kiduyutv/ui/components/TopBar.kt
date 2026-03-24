package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary

/**
 * Data class representing a navigation item in the top bar.
 * @param title The display name of the item.
 * @param icon The icon associated with the item (optional).
 * @param route The navigation route associated with the item.
 */
data class NavItem(
    val title: String,
    val icon: ImageVector? = null,
    val route: String
)

/**
 * The top navigation bar component used across the main screens.
 * Displays main navigation items (text-only for Home, Movies, TV Shows; icon+text for My List),
 * and utility icons like Search and Settings. App name and logo are hidden.
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
    // Home, Movies, TV Shows are text-only; My List keeps its icon.
    val navItems = listOf(
        NavItem("Home", null, "home"),
        NavItem("Movies", null, "movies"),
        NavItem("TV Shows", null, "tv_shows"),
        NavItem("My List", null, "my_list")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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
            IconButton(onClick = { /* TODO: Implement search functionality */ }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = { /* TODO: Implement settings functionality */ }) {
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
 * Individual navigation item. Shows icon + text if an icon is provided, otherwise text only.
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
            item.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = item.title,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}