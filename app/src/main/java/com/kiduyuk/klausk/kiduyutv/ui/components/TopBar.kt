package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.NotificationHelper

// ---------------- TOP BAR ----------------

@Composable
fun TopBar(
    selectedRoute: String,
    onNavItemClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationClick: (id: Int, type: String) -> Unit = { _, _ -> },
) {
    val navItems = listOf("Movies", "TV Shows", "My List")
    var showDialog by remember { mutableStateOf(false) }
    val notifications by NotificationHelper.notifications.collectAsState()

    if (showDialog) {
        NotificationDialog(
            notifications = notifications,
            onDismiss = { showDialog = false },
            onNotificationClick = {
                showDialog = false
                onNotificationClick(it.first, it.second)
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            navItems.forEach { title ->
                Text(
                    text = title,
                    modifier = Modifier
                        .focusable()
                        .clickable { onNavItemClick(title) }
                        .padding(8.dp),
                    color = Color.White
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
            }

            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }

            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}

// ---------------- FIXED DIALOG ----------------

@Composable
private fun NotificationDialog(
    notifications: List<com.kiduyuk.klausk.kiduyutv.util.AppNotification>,
    onDismiss: () -> Unit,
    onNotificationClick: (Pair<Int, String>) -> Unit
) {
    var selectedId by remember { mutableStateOf(notifications.firstOrNull()?.id) }

    val firstItemFocusRequester = remember { FocusRequester() }

    // 🔑 Move focus when dialog opens
    LaunchedEffect(Unit) {
        if (notifications.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier
                    .width(400.dp)
                    .heightIn(max = 500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {

                Text(
                    "Notifications",
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
                        Text("No notifications", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .focusable(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(notifications) { index, item ->

                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()

                            val isSelected = selectedId == item.id
                            val highlight = focused || isSelected

                            val modifier = if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else Modifier

                            Column(
                                modifier = modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (highlight) DarkRed else CardDark)
                                    .border(
                                        1.dp,
                                        if (highlight) Color.White else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .onFocusChanged {
                                        if (it.isFocused) selectedId = item.id
                                    }
                                    .focusable(interactionSource = interaction)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null
                                    ) {
                                        selectedId = item.id
                                        onNotificationClick(item.id to item.type)
                                    }
                                    .padding(12.dp)
                            ) {

                                Text(
                                    item.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    item.overview,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}