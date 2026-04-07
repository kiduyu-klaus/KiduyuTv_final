package com.kiduyuk.klausk.kiduyutv.ui.components

import android.R.color.transparent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.DarkRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.R

@Composable
fun TopBar(
    selectedRoute: String,
    onNavItemClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navItems = listOf("Movies", "TV Shows", "My List")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 15.dp, vertical = 10.dp)
        ,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Logo + Nav items
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Logo — focused state gives dark red ring
            val logoInteraction = remember { MutableInteractionSource() }
            val logoFocused by logoInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        color = if (logoFocused) DarkRed.copy(alpha = 0.7f) else Color.Transparent
                    )
                    .noRippleClickable(interactionSource = logoInteraction) { onNavItemClick("home") },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher11),
                    contentDescription = "KiduyuTV Logo",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Nav items
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, title ->
                    val route = when (index) {
                        0 -> "movies"
                        1 -> "tv_shows"
                        2 -> "my_list"
                        else -> ""
                    }
                    val isSelected = selectedRoute == route
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isHighlighted = isSelected || isFocused

                    Text(
                        text = title,
                        color = if (isHighlighted) Color.White else TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .background(
                                color = if (isFocused) DarkRed else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                            .drawBehind {
                                if (isSelected) { // underline only for selected, not focused
                                    val strokeWidth = 2.dp.toPx()
                                    val y = size.height - strokeWidth / 2
                                    drawLine(
                                        color = DarkRed,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = strokeWidth
                                    )
                                }
                            }
                            .noRippleClickable(interactionSource = interactionSource) { onNavItemClick(route) }
                    )
                }
            }
        }

        // Right: Search + Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FocusableIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                onClick = onSearchClick
            )
            FocusableIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )
        }
    }
}

/** Icon button that tints dark red and shows text when focused. */
@Composable
private fun FocusableIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .height(48.dp)
            .wrapContentWidth()
            .background(
                color = if (isFocused) DarkRed else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = if (isFocused) 12.dp else 0.dp)
            .noRippleClickable(interactionSource = interactionSource) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        if (isFocused) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = contentDescription,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}

/**
 * Extension function to create a clickable modifier without ripple effect.
 */
@Composable
fun Modifier.noRippleClickable(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick
)

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun TopBarPreview() {
    KiduyuTvTheme {
        Surface(color = BackgroundDark) {
            TopBar(
                selectedRoute = "movies",
                onNavItemClick = {},
                onSearchClick = {},
                onSettingsClick = {}
            )
        }
    }
}
