package com.kiduyuk.klausk.kiduyutv.ui.screens.cast.tv

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.MovieImage
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MovieImagesViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(MovieImagesUiState())
    val uiState: StateFlow<MovieImagesUiState> = _uiState.asStateFlow()

    fun loadMovieImages(movieId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getMovieImages(movieId)
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

data class MovieImagesUiState(
    val isLoading: Boolean = true,
    val images: List<MovieImage> = emptyList(),
    val error: String? = null
)

@Composable
fun MovieImagesScreen(
    movieId: Int,
    movieTitle: String,
    onBackClick: () -> Unit,
    onImageClick: (initialIndex: Int, imageUrls: List<String>) -> Unit,
    viewModel: MovieImagesViewModel = remember { MovieImagesViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(movieId) {
        viewModel.loadMovieImages(movieId)
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
                    text = "$movieTitle Images",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
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

                uiState.images.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No images found for $movieTitle",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }

                else -> {
                    val focusRequester = remember { FocusRequester() }
                    val imageUrls = uiState.images.map { "${TmdbApiService.IMAGE_BASE_URL}original${it.filePath}" }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(uiState.images) { index, image ->
                            MovieImageItem(
                                image = image,
                                onClick = { onImageClick(index, imageUrls) },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

@Composable
fun MovieImageItem(
    image: MovieImage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val imageUrl = "${TmdbApiService.IMAGE_BASE_URL}w500${image.filePath}"

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
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
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
