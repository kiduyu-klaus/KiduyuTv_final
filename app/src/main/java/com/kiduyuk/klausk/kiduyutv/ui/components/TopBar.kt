package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.DarkRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.util.NotificationHelper
import com.kiduyuk.klausk.kiduyutv.R

@Composable
fun TopBar(
    selectedRoute: String,
    onNavItemClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationClick: (id: Int, type: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val navItems = listOf("Movies", "TV Shows", "My List")
    var showNotificationDialog by remember { mutableStateOf(false) }
    val notifications by NotificationHelper.notifications.collectAsState()

    if (showNotificationDialog) {
        NotificationDialog(
            notifications = notifications,
            onDismiss = { showNotificationDialog = false },
            onNotificationClick = { id, type ->
                showNotificationDialog = false
                onNotificationClick(id, type)
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 15.dp, vertical = 10.dp),
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

        // Right: Notifications + Search + Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FocusableIconButton(
                    icon = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    onClick = { showNotificationDialog = true }
                )
                if (notifications.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(PrimaryRed)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                    )
                }
            }
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

@Composable
private fun NotificationDialog(
    notifications: List<com.kiduyuk.klausk.kiduyutv.util.AppNotification>,
    onDismiss: () -> Unit,
    onNotificationClick: (Int, String) -> Unit
) {
    var selectedNotificationId by remember(notifications) {
        mutableStateOf(notifications.firstOrNull()?.id)
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    // Used to route D-pad DOWN out of the LazyColumn into the Close button
    val closeFocusRequester = remember { FocusRequester() }
    val closeInteractionSource = remember { MutableInteractionSource() }
    val isCloseFocused by closeInteractionSource.collectIsFocusedAsState()

    LaunchedEffect(Unit) {
        if (notifications.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        } else {
            // Nothing in the list — put focus straight on Close
            closeFocusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .heightIn(max = 500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .clickable(enabled = false) {} // Prevent backdrop clicks from closing dialog
                    .padding(20.dp)
            ) {
                Text(
                    text = "Notifications",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No new notifications",
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            // Allow D-pad DOWN to escape the list and land on the Close button
                            .focusProperties { down = closeFocusRequester },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(notifications) { index, notification ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            val isSelected = selectedNotificationId == notification.id
                            val isHighlighted = isFocused || isSelected

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isHighlighted) DarkRed else CardDark)
                                    .border(
                                        width = 1.dp,
                                        color = if (isHighlighted) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            selectedNotificationId = notification.id
                                        }
                                    }
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstItemFocusRequester)
                                        else Modifier
                                    )
                                    // clickable already makes the item focusable on TV (D-pad center
                                    // fires onClick). A separate .focusable() with the same
                                    // interactionSource would duplicate focus handling and break clicks.
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = {
                                            selectedNotificationId = notification.id
                                            onNotificationClick(notification.id, notification.type)
                                        }
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = notification.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notification.overview,
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Close button — tracks focus so we can show a distinct focused style
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            // Brighter/lighter red when focused so the TV user can see selection
                            color = if (isCloseFocused) PrimaryRed else DarkRed
                        )
                        .border(
                            width = if (isCloseFocused) 2.dp else 0.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(closeFocusRequester)
                        .clickable(
                            interactionSource = closeInteractionSource,
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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

