package com.kiduyuk.klausk.kiduyutv.ai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.ai.GeminiService
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType
import com.kiduyuk.klausk.kiduyutv.ai.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for the AI Assistant feature.
 */
data class AiAssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDialogVisible: Boolean = false
)

/**
 * ViewModel for the AI Assistant chat interface.
 * Manages conversation state, handles API calls, and processes actions.
 */
class AiAssistantViewModel(
    private val geminiService: GeminiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    init {
        // Add welcome message
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        content = "Hello! I'm your KiduyuTv assistant. I can help you find movies and TV shows you'll love. What are you in the mood for?",
                        isUser = false
                    )
                )
            )
        }
    }

    /**
     * Sends a user message and gets an AI response.
     *
     * @param message The user's input message
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            // Add user message to the list
            val userMessage = ChatMessage(content = message, isUser = true)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + userMessage,
                    isLoading = true,
                    error = null
                )
            }

            // Add to conversation history
            conversationHistory.add(Pair("user", message))

            // Get AI response
            val result = geminiService.sendMessage(message, conversationHistory)

            result.onSuccess { response ->
                val actions = parseActionsFromResponse(response)
                val aiMessage = ChatMessage(
                    content = cleanResponseText(response),
                    isUser = false,
                    actions = actions
                )
                
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + aiMessage,
                        isLoading = false
                    )
                }

                conversationHistory.add(Pair("assistant", response))
            }.onFailure { exception ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Sorry, I couldn't process that. Please try again."
                    )
                }
            }
        }
    }

    /**
     * Clears the conversation history.
     */
    fun clearConversation() {
        conversationHistory.clear()
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        content = "Conversation cleared! How can I help you find your next favorite show?",
                        isUser = false
                    )
                )
            )
        }
    }

    /**
     * Shows or hides the chat dialog.
     */
    fun toggleDialog() {
        _uiState.update { state ->
            state.copy(isDialogVisible = !state.isDialogVisible)
        }
    }

    fun showDialog() {
        _uiState.update { state ->
            state.copy(isDialogVisible = true)
        }
    }

    fun hideDialog() {
        _uiState.update { state ->
            state.copy(isDialogVisible = false)
        }
    }

    /**
     * Parses action commands from the AI response.
     * The AI is expected to include [ACTION:...] markers in responses.
     */
    private fun parseActionsFromResponse(response: String): List<ActionCommand> {
        val actions = mutableListOf<ActionCommand>()
        val actionPattern = "\\[ACTION:([^|]+)\\|([^|]+)\\|([^|]+)\\|([^\\]]+)]".toRegex()
        
        actionPattern.findAll(response).forEach { match ->
            val type = when (match.groupValues[1].uppercase()) {
                "MOVIE" -> ActionType.NAVIGATE_TO_MOVIE
                "TV_SHOW" -> ActionType.NAVIGATE_TO_TV_SHOW
                "CAST" -> ActionType.NAVIGATE_TO_CAST
                "GENRE" -> ActionType.NAVIGATE_TO_GENRE
                "SEARCH" -> ActionType.SEARCH_MOVIES
                else -> null
            }
            
            if (type != null) {
                val dataMap = parseActionData(match.groupValues[4])
                actions.add(
                    ActionCommand(
                        label = match.groupValues[2],
                        type = type,
                        data = dataMap
                    )
                )
            }
        }
        
        return actions
    }

    /**
     * Parses the data portion of an action command.
     */
    private fun parseActionData(dataString: String): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        dataString.split(";").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                data[key] = when {
                    value.toIntOrNull() != null -> value.toInt()
                    value.toLongOrNull() != null -> value.toLong()
                    value.toBooleanStrictOrNull() != null -> value.toBoolean()
                    else -> value
                }
            }
        }
        return data
    }

    /**
     * Removes action markers from the response text for display.
     */
    private fun cleanResponseText(response: String): String {
        val actionPattern = "\\[ACTION:[^\\]]+]".toRegex()
        return response.replace(actionPattern, "").trim()
    }
}

/**
 * Factory for creating AiAssistantViewModel with dependencies.
 */
class AiAssistantViewModelFactory(
    private val apiKey: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiAssistantViewModel::class.java)) {
            return AiAssistantViewModel(GeminiService(apiKey)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}