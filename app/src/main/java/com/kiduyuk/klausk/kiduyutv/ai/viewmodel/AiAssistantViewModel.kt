package com.kiduyuk.klausk.kiduyutv.ai.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.ai.AiSearchService
import com.kiduyuk.klausk.kiduyutv.ai.GeminiService
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType
import com.kiduyuk.klausk.kiduyutv.ai.model.ChatMessage
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
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
    val isDialogVisible: Boolean = false,
    val toastMessage: String? = null
)

/**
 * ViewModel for the AI Assistant chat interface.
 * Manages conversation state, handles API calls, and processes actions.
 * Implements hybrid approach: AI understanding + real TMDB search.
 */
class AiAssistantViewModel(
    private val geminiService: GeminiService,
    private val aiSearchService: AiSearchService
) : ViewModel() {

    private val TAG = "AiAssistantViewModel"

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    init {
        // Add welcome message with updated instructions
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        content = "Hello! I'm your KiduyuTv assistant. I can help you find movies and TV shows you'll love. Just ask me something like 'Show me The Boys' or 'Find action movies' and I'll search our database for you!",
                        isUser = false
                    )
                )
            )
        }
    }

    /**
     * Sends a user message and gets an AI response.
     * Implements hybrid approach: AI understanding + real TMDB search.
     *
     * @param message The user's input message
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) {
            Log.d(TAG, "sendMessage: message is blank, ignoring")
            return
        }
        
        Log.d(TAG, "sendMessage: sending message: $message")

        viewModelScope.launch {
            // Add user message to the list
            val userMessage = ChatMessage(content = message, isUser = true)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + userMessage,
                    isLoading = true,
                    error = null,
                    toastMessage = null
                )
            }
            Log.d(TAG, "sendMessage: user message added, setting loading=true")

            // Add to conversation history
            conversationHistory.add(Pair("user", message))

            // Detect if this is a search request
            val searchIntent = aiSearchService.detectSearchIntent(message)
            
            if (searchIntent != null && searchIntent.query.length >= 2) {
                // This is a search request - use hybrid approach
                Log.d(TAG, "Detected search intent: ${searchIntent.query}, type: ${searchIntent.mediaType}")
                handleSearchRequest(searchIntent)
            } else {
                // General conversation - use AI only
                Log.d(TAG, "General conversation, using AI only")
                handleGeneralConversation(message)
            }
        }
    }

    /**
     * Handles search requests with real TMDB data.
     * This is the hybrid approach that combines AI understanding with actual API results.
     */
    private suspend fun handleSearchRequest(searchIntent: AiSearchService.SearchIntent) {
        try {
            Log.d(TAG, "Performing hybrid search for: ${searchIntent.query}")
            
            val searchData = when (searchIntent.mediaType) {
                AiSearchService.MediaType.TV_SHOW -> {
                    aiSearchService.searchTvShows(searchIntent.query)
                }
                AiSearchService.MediaType.MOVIE -> {
                    aiSearchService.searchMovies(searchIntent.query)
                }
                AiSearchService.MediaType.UNKNOWN -> {
                    aiSearchService.searchMulti(searchIntent.query)
                }
            }
            
            Log.d(TAG, "Search completed, found ${searchData.results.size} results")
            
            if (searchData.results.isNotEmpty()) {
                // Format a response with real search results
                val response = aiSearchService.formatSearchResponse(searchData, searchIntent.query)
                
                // Parse actions from the formatted response
                val actions = parseActionsFromResponse(response)
                
                val aiMessage = ChatMessage(
                    content = cleanResponseText(response),
                    isUser = false,
                    actions = actions
                )
                
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + aiMessage,
                        isLoading = false,
                        toastMessage = "Found ${searchData.results.size} results!"
                    )
                }
                
                conversationHistory.add(Pair("assistant", response))
            } else {
                // No results found - use AI to provide helpful response
                handleNoResultsResponse(searchIntent.query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}", e)
            handleNoResultsResponse(searchIntent.query)
        }
    }

    /**
     * Handles the case when no search results are found.
     * Uses AI to provide alternative suggestions.
     */
    private suspend fun handleNoResultsResponse(query: String) {
        val fallbackMessage = "I couldn't find exact matches for '$query' in our database. Let me try a different approach..."
        
        val result = geminiService.sendMessage(
            "User searched for '$query' but no exact matches were found. " +
            "Please suggest 3-5 popular movies or TV shows that might interest them instead. " +
            "Format your response to include action buttons if possible.",
            conversationHistory
        )
        
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
                    isLoading = false,
                    toastMessage = "No exact matches found, showing alternatives"
                )
            }
            
            conversationHistory.add(Pair("assistant", response))
        }.onFailure { exception ->
            Log.e(TAG, "Fallback AI response failed: ${exception.message}")
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatMessage(
                        content = "I couldn't find '$query'. Try searching with different keywords or ask about a specific movie/TV show by name.",
                        isUser = false
                    ),
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    /**
     * Handles general conversation using AI only.
     * Used for non-search related queries.
     */
    private suspend fun handleGeneralConversation(message: String) {
        val result = geminiService.sendMessage(message, conversationHistory)
        
        result.onSuccess { response ->
            Log.d(TAG, "sendMessage: success, response length: ${response.length}")
            val actions = parseActionsFromResponse(response)
            val aiMessage = ChatMessage(
                content = cleanResponseText(response),
                isUser = false,
                actions = actions
            )
            
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + aiMessage,
                    isLoading = false,
                    toastMessage = "Response received!"
                )
            }

            conversationHistory.add(Pair("assistant", response))
        }.onFailure { exception ->
            Log.e(TAG, "sendMessage: failure", exception)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = "Sorry, I couldn't process that. Please try again.",
                    toastMessage = "Error: ${exception.message}"
                )
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
 * Updated to include AiSearchService with TmdbRepository.
 */
class AiAssistantViewModelFactory(
    private val apiKey: String,
    private val tmdbRepository: TmdbRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiAssistantViewModel::class.java)) {
            return AiAssistantViewModel(
                GeminiService(apiKey),
                AiSearchService(tmdbRepository)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}