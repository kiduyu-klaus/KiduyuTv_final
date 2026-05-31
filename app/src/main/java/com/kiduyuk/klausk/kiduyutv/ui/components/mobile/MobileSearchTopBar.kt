
package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

@Composable
fun MobileSearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onSearch: () -> Unit,
    onSettingsClick: () -> Unit,
    title: String, // New parameter for the title
    onBackClick: (() -> Unit)? = null, // Optional back button
    extraAction: @Composable (() -> Unit)? = null // Optional extra action (e.g., Clear All)
) {
    Surface(
        color = CardDark,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExpanded) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(PrimaryRed),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search movies, TV shows...", color = TextSecondary, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryRed)
                }
                IconButton(onClick = onExpandToggle) {
                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = TextSecondary)
                }
            } else {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = if (onBackClick == null) 4.dp else 0.dp)
                )
                if (extraAction != null) {
                    extraAction()
                }
                IconButton(onClick = onExpandToggle) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                }
            }
        }
    }
}
