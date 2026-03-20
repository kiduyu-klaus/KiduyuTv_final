package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

@Composable
fun <T> ContentRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    onItemClick: (T) -> Unit,
    content: @Composable (T, Boolean, () -> Unit) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                val isSelected = items.indexOf(item) == selectedIndex
                Box(
                    modifier = Modifier
                        .focusable()
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = FocusBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    content(item, isSelected) {
                        selectedIndex = items.indexOf(item)
                        onItemClick(item)
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkRow(
    title: String,
    items: List<com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem>,
    modifier: Modifier = Modifier,
    onItemClick: (com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                val isSelected = items.indexOf(item) == selectedIndex
                NetworkCard(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        selectedIndex = items.indexOf(item)
                        onItemClick(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun NetworkCard(
    item: com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp)
            .focusable()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = FocusBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                        .background(
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(8.dp)
                        )
                }
            )
            .background(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
    }
}
