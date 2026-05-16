package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType

/**
 * A clickable button that represents an action from the AI response.
 *
 * @param action The action command to display
 * @param onClick Callback when the button is clicked
 */
@Composable
fun ActionButton(
    action: ActionCommand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (action.type) {
        ActionType.NAVIGATE_TO_MOVIE -> Icons.Filled.Movie
        ActionType.NAVIGATE_TO_TV_SHOW -> Icons.Filled.Tv
        ActionType.NAVIGATE_TO_CAST -> Icons.Filled.Person
        ActionType.SEARCH_MOVIES, ActionType.SEARCH_TV_SHOWS -> Icons.Filled.Search
        ActionType.NAVIGATE_TO_GENRE -> Icons.Filled.Search
        else -> Icons.Filled.ArrowForward
    }

    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}