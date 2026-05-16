package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.viewmodel.AiAssistantViewModel
import com.kiduyuk.klausk.kiduyutv.ai.viewmodel.AiAssistantViewModelFactory
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository

/**
 * Wrapper composable that adds AI Assistant functionality to any screen.
 * Includes the FAB and handles the chat dialog.
 * Now implements hybrid search approach with real TMDB integration.
 *
 * @param apiKey The Gemini API key
 * @param onActionClick Callback for handling action commands from the chat
 * @param content The main screen content
 */
@Composable
fun AiAssistantScreenWrapper(
    apiKey: String,
    onActionClick: (ActionCommand) -> Unit,
    content: @Composable () -> Unit
) {
    // Create TmdbRepository instance for search functionality
    val context = LocalContext.current
    val tmdbRepository = remember { TmdbRepository() }
    
    val viewModel: AiAssistantViewModel = viewModel(
        factory = AiAssistantViewModelFactory(apiKey, tmdbRepository)
    )
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        content()

        // Floating Action Button - positioned above the bottom navigation bar
        AiAssistantFab(
            onClick = { viewModel.showDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 72.dp)
                .systemBarsPadding()
        )

        // Chat Dialog
        if (uiState.isDialogVisible) {
            AiChatDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.hideDialog() },
                onActionClick = { action ->
                    viewModel.hideDialog()
                    onActionClick(action)
                }
            )
        }
    }
}