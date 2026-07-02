package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.model.Video
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val mobilePreferredVideoTypes = listOf(
    "Trailer",
    "Teaser",
    "Clip",
    "Behind the Scenes",
    "Bloopers",
    "Featurette"
)

class MobileVideosViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(MobileVideosUiState())
    val uiState: StateFlow<MobileVideosUiState> = _uiState.asStateFlow()

    fun loadVideos(mediaId: Int, isTv: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = if (isTv) {
                repository.getTvShowVideos(mediaId)
            } else {
                repository.getMovieVideos(mediaId)
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

data class MobileVideosUiState(
    val isLoading: Boolean = true,
    val videos: List<Video> = emptyList(),
    val error: String? = null
)

@Composable
fun MobileVideosScreen(
    mediaId: Int,
    isTv: Boolean,
    title: String,
    onBackClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    viewModel: MobileVideosViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(mediaId, isTv) {
        viewModel.loadVideos(mediaId, isTv)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        MobileMediaHeader(
            title = "$title Videos",
            onBackClick = onBackClick
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LottieLoadingView(size = 180.dp)
                }
            }

            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.error ?: "An error occurred",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            uiState.videos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No videos found",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                val videosByType = remember(uiState.videos) {
                    uiState.videos.groupBy { video -> video.type.ifBlank { "Other" } }
                }
                val availableTypes = remember(videosByType) {
                    val knownTypes = mobilePreferredVideoTypes.filter { type ->
                        videosByType.keys.any { it.equals(type, ignoreCase = true) }
                    }
                    val extraTypes = videosByType.keys
                        .filterNot { type ->
                            mobilePreferredVideoTypes.any { it.equals(type, ignoreCase = true) }
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
                    edgePadding = 12.dp
                ) {
                    availableTypes.forEach { type ->
                        Tab(
                            selected = type == selectedType,
                            onClick = { selectedType = type },
                            text = {
                                Text(
                                    text = type,
                                    color = if (type == selectedType) TextPrimary else TextSecondary,
                                    fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(selectedVideos) { video ->
                        MobileVideoItem(
                            video = video,
                            onClick = { onVideoClick(video) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileVideoItem(
    video: Video,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = "https://img.youtube.com/vi/${video.key}/hqdefault.jpg",
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
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(PrimaryRed.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.type,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun MobileMediaHeader(
    title: String,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
