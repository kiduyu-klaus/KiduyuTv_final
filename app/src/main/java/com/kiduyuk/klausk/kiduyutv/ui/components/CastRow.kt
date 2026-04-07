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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

/**
 * A composable to display a horizontal row of cast members with circular profile images.
 * Each cast member shows their name and character/role name.
 *
 * @param title The title of the cast row (e.g., "Cast", "Top Billed Cast")
 * @param cast The list of cast members to display.
 * @param modifier The modifier to be applied to the cast row.
 * @param onCastClick Lambda to be invoked when a cast member is clicked.
 */
@Composable
fun CastRow(
    title: String,
    cast: List<CastMember>,
    modifier: Modifier = Modifier,
    onCastClick: (CastMember) -> Unit
) {
    // State to keep track of the currently selected item's index.
    var selectedIndex by remember { mutableIntStateOf(-1) }
    // State for the LazyRow to control scrolling.
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Display the title of the cast row.
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal scrollable list of cast members.
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(cast) { index, castMember ->
                CastCard(
                    castMember = castMember,
                    isSelected = index == selectedIndex,
                    onFocus = { selectedIndex = index },
                    onClick = {
                        selectedIndex = index
                        onCastClick(castMember)
                    }
                )
            }
        }
    }
}

/**
 * Composable function to display a single cast member card.
 * Shows a circular profile image, the cast member's name, and their character name.
 *
 * @param castMember The cast member data to display.
 * @param isSelected A boolean indicating if the card is currently selected.
 * @param onFocus Lambda to be invoked when the card gains focus.
 * @param onClick Lambda to be invoked when the card is clicked.
 */
@Composable
private fun CastCard(
    castMember: CastMember,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    // Create a MutableInteractionSource to observe focus state.
    val interactionSource = remember { MutableInteractionSource() }
    // Collect the focus state as a State.
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Create a FocusRequester for focus management.
    val focusRequester = remember { FocusRequester() }

    // Effect to trigger onFocus callback when the item gains focus.
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }

    // Build the full profile image URL if profilePath is available
    val profileUrl = castMember.profilePath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}w185$path"
    }

    // Column to hold the profile image and text below it
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(90.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
    ) {
        // Profile image container with border when focused
        Box(
            modifier = Modifier
                .size(70.dp)
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 2.dp,
                            color = FocusBorder,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            if (profileUrl != null) {
                AsyncImage(
                    model = profileUrl,
                    contentDescription = "${castMember.name} profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                // Placeholder when no profile image is available
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF444444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = castMember.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 24.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Cast member name
        Text(
            text = castMember.name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Character/role name
        if (!castMember.character.isNullOrBlank()) {
            Text(
                text = castMember.character,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
