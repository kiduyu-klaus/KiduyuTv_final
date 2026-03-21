package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

/**
 * A generic composable to display a horizontal row of content items.
 * It handles focus changes and click events for each item, and can optionally trigger a callback
 * when an item gains focus, typically to update a hero section.
 *
 * @param T The type of items in the row.
 * @param title The title of the content row.
 * @param items The list of items to display in the row.
 * @param modifier The modifier to be applied to the content row.
 * @param onItemFocus Optional lambda to be invoked when an item in the row gains focus.
 * @param onItemClick Lambda to be invoked when an item in the row is clicked.
 * @param content A composable lambda that defines how each item in the row is rendered.
 *                It receives the item, a boolean indicating if it's focused, and an onClick lambda.
 */
@Composable
fun <T> ContentRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    onItemFocus: ((T) -> Unit)? = null,
    onItemClick: (T) -> Unit,
    content: @Composable (T, Boolean, () -> Unit) -> Unit
) {
    // State to keep track of the currently selected item's index. Initialized to -1 (no selection).
    var selectedIndex by remember { mutableIntStateOf(-1) }
    // State for the LazyRow to control scrolling.
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // Display the title of the content row.
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

        // Horizontal scrollable list of items.
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items) { index, item ->
                // Create a MutableInteractionSource to observe focus state.
                val interactionSource = remember { MutableInteractionSource() }
                // Collect the focus state as a State.
                val isFocused by interactionSource.collectIsFocusedAsState()

                // Effect to trigger onItemFocus when the item gains focus.
                LaunchedEffect(isFocused) {
                    if (isFocused) {
                        selectedIndex = index
                        onItemFocus?.invoke(item)
                    }
                }

                // Box to wrap each content item, handling focus and clicks.
                Box(
                    modifier = Modifier
                        // Detect focus changes and update selectedIndex and call onItemFocus.
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                selectedIndex = index
                                onItemFocus?.invoke(item)
                            }
                        }
                        // Make the item focusable.
                        .focusable(interactionSource = interactionSource)
                        // Handle click events.
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            selectedIndex = index
                            onItemClick(item)
                        },
                    propagateMinConstraints = true
                ) {
                    // Render the actual content of the item using the provided lambda.
                    content(item, isFocused) {
                        selectedIndex = index
                        onItemClick(item)
                    }
                }
            }
        }
    }
}

/**
 * Composable function to display a horizontal row of network items.
 * It handles focus changes and click events for each network item.
 *
 * @param title The title of the network row.
 * @param items The list of [NetworkItem] data to display.
 * @param modifier The modifier to be applied to the network row.
 * @param onItemClick Lambda to be invoked when a network item is clicked.
 */
@Composable
fun NetworkRow(
    title: String,
    items: List<com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem>,
    modifier: Modifier = Modifier,
    onItemClick: (com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem) -> Unit
) {
    // State to keep track of the currently selected item's index. Initialized to -1 (no selection).
    var selectedIndex by remember { mutableIntStateOf(-1) }
    // State for the LazyRow to control scrolling.
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // Display the title of the network row.
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

        // Horizontal scrollable list of network items.
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items) { index, item ->
                // Render each network item using NetworkCard.
                NetworkCard(
                    item = item,
                    isSelected = index == selectedIndex,
                    onFocus = { selectedIndex = index }, // Update selectedIndex when item gains focus.
                    onClick = {
                        selectedIndex = index
                        onItemClick(item)
                    }
                )
            }
        }
    }
}

/**
 * Composable function to display a single network card.
 * It handles its own focus and click events, and provides visual feedback for focus.
 *
 * @param item The [NetworkItem] data to display.
 * @param isSelected A boolean indicating if the card is currently selected (focused).
 * @param onFocus Lambda to be invoked when the card gains focus.
 * @param onClick Lambda to be invoked when the card is clicked.
 */
@Composable
private fun NetworkCard(
    item: com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    // Create a MutableInteractionSource to observe focus state.
    val interactionSource = remember { MutableInteractionSource() }
    // Collect the focus state as a State.
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Effect to trigger onFocus callback when the item gains focus.
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }

    // Box to hold the network card content, applying size, background, and conditional border for focus indication.
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp)
            .then(
                // Apply a border if the card is focused.
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = FocusBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(8.dp)) // Clip content to rounded corners.
            .background(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(8.dp)
            )
            // Detect focus changes and call onFocus.
            .onFocusChanged { if (it.isFocused) onFocus() }
            // Make the item focusable.
            .focusable(interactionSource = interactionSource)
            // Handle click events.
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Display the network item's name.
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
    }
}
