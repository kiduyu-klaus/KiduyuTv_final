package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.viewmodel.AiAssistantViewModel
import com.kiduyuk.klausk.kiduyutv.ai.viewmodel.AiAssistantViewModelFactory

/**
 * Wrapper composable that adds AI Assistant functionality to any screen.
 * Includes the FAB and handles the chat dialog.
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
    val viewModel: AiAssistantViewModel = viewModel(
        factory = AiAssistantViewModelFactory(apiKey)
    )
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        content()

        // Floating Action Button
        AiAssistantFab(
            onClick = { viewModel.showDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding()
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