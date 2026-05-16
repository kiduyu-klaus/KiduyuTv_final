package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Floating Action Button for accessing the AI Assistant.
 * Positioned at the bottom-right of the screen.
 *
 * @param onClick Callback when the FAB is clicked
 * @param isVisible Whether the FAB should be visible
 */
@Composable
fun AiAssistantFab(
    onClick: () -> Unit,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            FloatingActionButton(
                onClick = onClick,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "AI Assistant",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}