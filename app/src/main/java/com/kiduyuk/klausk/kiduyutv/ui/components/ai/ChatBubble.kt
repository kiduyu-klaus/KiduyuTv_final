package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ChatMessage

/**
 * A chat bubble that displays a message and its associated actions.
 *
 * @param message The chat message to display
 * @param onActionClick Callback when an action button is clicked
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    onActionClick: (ActionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Display action buttons if present
                if (message.actions.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.actions.forEach { action ->
                            ActionButton(
                                action = action,
                                onClick = { onActionClick(action) }
                            )
                        }
                    }
                }
            }
        }
    }
}