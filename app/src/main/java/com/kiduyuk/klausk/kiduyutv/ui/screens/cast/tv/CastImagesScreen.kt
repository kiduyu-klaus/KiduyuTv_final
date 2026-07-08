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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.ProfileImage
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ViewModel for CastImagesScreen
class CastImagesViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(CastImagesUiState())
    val uiState: StateFlow<CastImagesUiState> = _uiState.asStateFlow()

    fun loadCastImages(personId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getPersonImages(personId)
            result.onSuccess { images ->
                _uiState.value = _uiState.value.copy(isLoading = false, images = images)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = throwable.message)
            }
        }
    }
}

data class CastImagesUiState(
    val isLoading: Boolean = true,
    val images: List<ProfileImage> = emptyList(),
    val error: String? = null
)

@Composable
fun CastImagesScreen(
    castId: Int,
    castName: String,
    onBackClick: () -> Unit,
    onImageClick: (initialIndex: Int, imageUrls: List<String>) -> Unit,
    viewModel: CastImagesViewModel = remember { CastImagesViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val horizontalPadding = 25.dp
    val spacing = 10.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 100.dp
    val actualColumns = maxOf(4, minOf(8, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))

    LaunchedEffect(castId) {
        viewModel.loadCastImages(castId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar with back button and title
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
                    text = "${castName}'s Images",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LottieLoadingView(size = 300.dp)
                }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.error ?: "An error occurred",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            } else if (uiState.images.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No images found for ${castName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            } else {
                val focusRequester = remember { FocusRequester() }
                val imageUrls = uiState.images.map { "${TmdbApiService.IMAGE_BASE_URL}original${it.filePath}" }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(actualColumns),
                    contentPadding = PaddingValues(horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uiState.images) { index, image ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CastImageItem(
                                image = image,
                                onClick = { onImageClick(index, imageUrls) },
                                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
                            )
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
fun CastImageItem(
    image: ProfileImage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val imageUrl = "${TmdbApiService.IMAGE_BASE_URL}w500${image.filePath}"

    Box(
        modifier = modifier
            .width(100.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { }
            .then(
                if (isFocused) {
                    Modifier.border(3.dp, FocusBorder, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
