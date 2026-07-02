package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.model.Video
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val preferredVideoTypes = listOf(
    "Trailer",
    "Teaser",
    "Clip",
    "Behind the Scenes",
    "Bloopers",
    "Featurette"
)

class VideosViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(VideosUiState())
    val uiState: StateFlow<VideosUiState> = _uiState.asStateFlow()

    fun loadVideos(tmdbId: Int, isTv: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = if (isTv) {
                repository.getTvShowVideos(tmdbId)
            } else {
                repository.getMovieVideos(tmdbId)
            }

            result
                .onSuccess { videos ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = videos.filter {
                            it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank()
                        }
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message
                    )
                }
        }
    }
}

data class VideosUiState(
    val isLoading: Boolean = true,
    val videos: List<Video> = emptyList(),
    val error: String? = null
)

@Composable
fun VideosScreen(
    mediaId: Int,
    isTv: Boolean,
    title: String,
    onBackClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    viewModel: VideosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(mediaId, isTv) {
        viewModel.loadVideos(mediaId, isTv)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$title Videos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LottieLoadingView(size = 300.dp)
                    }
                }

                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.error ?: "An error occurred",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }

                uiState.videos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No videos found for $title",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }

                else -> {
                    val videosByType = remember(uiState.videos) {
                        uiState.videos.groupBy { video -> video.type.ifBlank { "Other" } }
                    }
                    val availableTypes = remember(videosByType) {
                        val knownTypes = preferredVideoTypes.filter { type ->
                            videosByType.keys.any { it.equals(type, ignoreCase = true) }
                        }
                        val extraTypes = videosByType.keys
                            .filterNot { type ->
                                preferredVideoTypes.any { it.equals(type, ignoreCase = true) }
                            }
                            .sorted()
                        knownTypes + extraTypes
                    }
                    var selectedType by remember(availableTypes) {
                        mutableStateOf(availableTypes.firstOrNull().orEmpty())
                    }
                    val selectedIndex = availableTypes.indexOf(selectedType).coerceAtLeast(0)
                    val selectedVideos = videosByType.entries
                        .firstOrNull { it.key.equals(selectedType, ignoreCase = true) }
                        ?.value
                        .orEmpty()

                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = BackgroundDark,
                        contentColor = TextPrimary,
                        edgePadding = 16.dp
                    ) {
                        availableTypes.forEach { type ->
                            Tab(
                                selected = type == selectedType,
                                onClick = { selectedType = type },
                                text = {
                                    Text(
                                        text = type,
                                        color = if (type == selectedType) TextPrimary else TextSecondary,
                                        fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    val focusRequester = remember(selectedType) { FocusRequester() }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(selectedVideos) { index, video ->
                            VideoItem(
                                video = video,
                                onClick = { onVideoClick(video) },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }

                    LaunchedEffect(selectedType, selectedVideos) {
                        if (selectedVideos.isNotEmpty()) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoItem(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val thumbnailUrl = "https://img.youtube.com/vi/${video.key}/hqdefault.jpg"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) {
                    Modifier.border(3.dp, FocusBorder, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.name,
            color = TextPrimary,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = video.type,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}
