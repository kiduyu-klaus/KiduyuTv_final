
package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

@Composable
fun MobileSearchTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    title: String,
    onBackClick: (() -> Unit)? = null,
    extraAction: @Composable (() -> Unit)? = null
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
            
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
            }
            
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }
    }
}
