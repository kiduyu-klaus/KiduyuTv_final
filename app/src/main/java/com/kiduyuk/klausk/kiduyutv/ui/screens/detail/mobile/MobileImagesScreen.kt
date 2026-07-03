package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.MovieImage
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.rememberPhoneInterstitialBackClick
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobileImagesViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(MobileImagesUiState())
    val uiState: StateFlow<MobileImagesUiState> = _uiState.asStateFlow()

    fun loadImages(mediaId: Int, isTv: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = if (isTv) {
                repository.getTvShowImages(mediaId)
            } else {
                repository.getMovieImages(mediaId)
            }

            result
                .onSuccess { images ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        images = images
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

data class MobileImagesUiState(
    val isLoading: Boolean = true,
    val images: List<MovieImage> = emptyList(),
    val error: String? = null
)

@Composable
fun MobileImagesScreen(
    mediaId: Int,
    title: String,
    isTv: Boolean,
    onBackClick: () -> Unit,
    onImageClick: (initialIndex: Int, imageUrls: List<String>) -> Unit,
    viewModel: MobileImagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val handleBackClick = rememberPhoneInterstitialBackClick(onBackClick)

    BackHandler(onBack = handleBackClick)

    LaunchedEffect(mediaId, isTv) {
        viewModel.loadImages(mediaId, isTv)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        MobileMediaHeader(
            title = "$title Images",
            onBackClick = handleBackClick
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

            uiState.images.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No images found",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                val imageUrls = uiState.images.map {
                    "${TmdbApiService.IMAGE_BASE_URL}original${it.filePath}"
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uiState.images) { index, image ->
                        MobileImageItem(
                            image = image,
                            onClick = { onImageClick(index, imageUrls) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileImageItem(
    image: MovieImage,
    onClick: () -> Unit
) {
    val isBackdrop = image.aspectRatio >= 1.4
    val itemAspectRatio = if (isBackdrop) 16f / 9f else 2f / 3f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        AsyncImage(
            model = "${TmdbApiService.IMAGE_BASE_URL}w500${image.filePath}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(itemAspectRatio)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}
