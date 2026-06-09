# Gemini AI Assistant Implementation Guide

## Overview

This document outlines the implementation plan for adding a Gemini-powered AI Assistant to the KiduyuTv mobile application. The assistant will provide an interactive chat experience that helps users discover movies and TV shows, with the ability to navigate directly to relevant screens through clickable actions.

## Architecture Summary

The AI Assistant will be implemented using a modular architecture that integrates seamlessly with the existing Jetpack Compose navigation system. The key components include:

- **Floating Action Button**: A persistent UI element that launches the chat interface
- **Chat Dialog**: A bottom sheet or modal dialog containing the conversation interface
- **AI Service Layer**: Communication with the Gemini API for natural language processing
- **ViewModel**: Manages conversation state and handles business logic
- **Navigation Integration**: Seamless deep-linking to app screens from chat responses

## Prerequisites

### 1. Google AI SDK Dependency

Add the following to your `app/build.gradle.kts` (or `app/build.gradle`):

```kotlin
dependencies {
    // Gemini AI SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.2.0")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
}
```

### 2. API Key Configuration

Store your Gemini API key securely. Add it to your `local.properties` file:

```properties
GEMINI_API_KEY=your_api_key_here
```

Access it in your build configuration:

```kotlin
// build.gradle.kts
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
```

## Project Structure

The following new files and directories will be created:

```
app/src/main/java/com/kiduyuk/klausk/kiduyutv/
├── ai/
│   ├── GeminiService.kt           # API communication layer
│   ├── model/
│   │   ├── ChatMessage.kt        # Message data class
│   │   └── ActionCommand.kt      # Parsed action command
│   └── viewmodel/
│       └── AiAssistantViewModel.kt
├── ui/
│   └── components/
│       └── ai/
│           ├── AiAssistantFab.kt         # Floating action button
│           ├── AiChatDialog.kt           # Chat dialog UI
│           ├── ChatBubble.kt             # Message bubble component
│           └── ActionButton.kt          # Clickable action button
```

## Implementation Steps

### Step 1: Create Chat Message Models

Create the data models to represent conversation messages and action commands:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ai/model/ChatMessage.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ai.model

/**
 * Represents a single message in the chat conversation.
 *
 * @param id Unique identifier for the message
 * @param content The text content of the message
 * @param isUser Whether the message is from the user (true) or AI (false)
 * @param timestamp When the message was sent
 * @param actions Optional list of actions the user can take based on this message
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actions: List<ActionCommand> = emptyList()
)

/**
 * Represents an action that can be triggered from a chat message.
 *
 * @param label Display text for the action button
 * @param type The type of action (navigate, search, details, etc.)
 * @param data Required data to execute the action (e.g., movie ID, search query)
 */
data class ActionCommand(
    val label: String,
    val type: ActionType,
    val data: Map<String, Any>
)

/**
 * Enum defining the types of actions available in the chat.
 */
enum class ActionType {
    NAVIGATE_TO_MOVIE,
    NAVIGATE_TO_TV_SHOW,
    SEARCH_MOVIES,
    SEARCH_TV_SHOWS,
    NAVIGATE_TO_CAST,
    NAVIGATE_TO_GENRE,
    OPEN_SETTINGS,
    SHOW_RECOMMENDATIONS
}
```

### Step 2: Create Gemini Service

Create the service class that handles communication with the Gemini API:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ai/GeminiService.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.GenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service class for interacting with the Gemini AI API.
 * Handles conversation management and response parsing.
 *
 * @param apiKey The Gemini API key for authentication
 */
class GeminiService(private val apiKey: String) {

    private val generationConfig = GenerationConfig.builder()
        .temperature(0.7f)
        .topK(40)
        .topP(0.95)
        .maxOutputTokens(1024)
        .build()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey,
        generationConfig = generationConfig
    )

    /**
     * Sends a message to the AI and returns the response.
     *
     * @param userMessage The user's input message
     * @param conversationHistory List of previous messages for context
     * @return The AI's response text
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPrompt(userMessage, conversationHistory)
            val response = generativeModel.generateContent(prompt)
            Result.success(response.text ?: "I couldn't generate a response.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Builds a contextual prompt for the AI assistant.
     * Includes app-specific instructions and conversation context.
     */
    private fun buildPrompt(
        message: String,
        history: List<Pair<String, String>>
    ): String {
        val contextPrompt = """
            You are a friendly movie and TV show recommendation assistant for KiduyuTv app.
            Your role is to help users discover content they might enjoy.
            
            Guidelines:
            - Provide helpful, concise responses about movies and TV shows
            - When recommending content, format your response to include action buttons
            - Use the format [ACTION:type|label|text|data] to indicate clickable actions
            - Types: MOVIE, TV_SHOW, CAST, GENRE, SEARCH
            - Always be friendly and conversational
            - If you mention a specific movie or TV show, include an action button to view details
            
            Example response:
            "Based on your interest in sci-fi, I think you'd love 'Inception'! It's a mind-bending thriller directed by Christopher Nolan. [ACTION:MOVIE|Watch Now|View Details|id=12345]"
            
            Current message: $message
        """.trimIndent()

        return contextPrompt
    }
}
```

### Step 3: Create AI Assistant ViewModel

Create the ViewModel that manages conversation state and UI logic:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ai/viewmodel/AiAssistantViewModel.kt`**

```kotlin
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
```

### Step 4: Create UI Components

#### 4.1 Floating Action Button

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/components/ai/AiAssistantFab.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Floating Action Button for accessing the AI Assistant.
 * Positioned at the bottom-right of the screen.
 *
 * @param onClick Callback when the FAB is clicked
 * @param isVisible Whether the FAB should be visible
 */
@Composable
fun AiAssistantFab(
    onClick: () -> Unit,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            FloatingActionButton(
                onClick = onClick,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "AI Assistant",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
```

#### 4.2 Chat Bubble Component

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/components/ai/ChatBubble.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ChatMessage

/**
 * A chat bubble that displays a message and its associated actions.
 *
 * @param message The chat message to display
 * @param onActionClick Callback when an action button is clicked
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    onActionClick: (ActionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Display action buttons if present
                if (message.actions.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.actions.forEach { action ->
                            ActionButton(
                                action = action,
                                onClick = { onActionClick(action) }
                            )
                        }
                    }
                }
            }
        }
    }
}
```

#### 4.3 Action Button Component

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/components/ai/ActionButton.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType

/**
 * A clickable button that represents an action from the AI response.
 *
 * @param action The action command to display
 * @param onClick Callback when the button is clicked
 */
@Composable
fun ActionButton(
    action: ActionCommand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (action.type) {
        ActionType.NAVIGATE_TO_MOVIE -> Icons.Filled.Movie
        ActionType.NAVIGATE_TO_TV_SHOW -> Icons.Filled.Tv
        ActionType.NAVIGATE_TO_CAST -> Icons.Filled.Person
        ActionType.SEARCH_MOVIES, ActionType.SEARCH_TV_SHOWS -> Icons.Filled.Search
        ActionType.NAVIGATE_TO_GENRE -> Icons.Filled.Search
        else -> Icons.Filled.ArrowForward
    }

    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

#### 4.4 Chat Dialog Component

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/components/ai/AiChatDialog.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.viewmodel.AiAssistantUiState
import com.kiduyuk.klausk.kiduyutv.ai.viewmodel.AiAssistantViewModel

/**
 * Modal bottom sheet containing the AI chat interface.
 *
 * @param viewModel The ViewModel managing the chat state
 * @param onDismiss Callback to dismiss the dialog
 * @param onActionClick Callback when an action button is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatDialog(
    viewModel: AiAssistantViewModel,
    onDismiss: () -> Unit,
    onActionClick: (ActionCommand) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kiduyu Assistant",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Powered by Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = { viewModel.clearConversation() }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Clear conversation"
                    )
                }
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatBubble(
                        message = message,
                        onActionClick = onActionClick
                    )
                }

                // Loading indicator
                if (uiState.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // Input field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask me about movies or TV shows...") },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    enabled = !uiState.isLoading
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (inputText.isNotBlank() && !uiState.isLoading) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    onClick = {
                        if (inputText.isNotBlank() && !uiState.isLoading) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !uiState.isLoading) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
```

### Step 5: Create the Main Screen with FAB

Create a wrapper composable that adds the FAB to any screen and handles navigation:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/components/ai/AiAssistantScreenWrapper.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType
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
```

### Step 6: Action Handler Implementation

Create a utility to handle navigation actions from the AI:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ai/NavigationActionHandler.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ai

import androidx.navigation.NavHostController
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType

/**
 * Handles navigation actions triggered from the AI chat.
 */
class NavigationActionHandler(
    private val navController: NavHostController
) {

    /**
     * Processes an action command and navigates accordingly.
     *
     * @param action The action command to process
     * @return true if navigation was successful, false otherwise
     */
    fun handleAction(action: ActionCommand): Boolean {
        return when (action.type) {
            ActionType.NAVIGATE_TO_MOVIE -> {
                val movieId = action.data["id"] as? Int ?: return false
                navController.navigate("movie_detail/$movieId")
                true
            }
            ActionType.NAVIGATE_TO_TV_SHOW -> {
                val tvId = action.data["id"] as? Int ?: return false
                navController.navigate("tv_show_detail/$tvId")
                true
            }
            ActionType.NAVIGATE_TO_CAST -> {
                val castId = action.data["id"] as? Int ?: return false
                val name = action.data["name"] as? String ?: ""
                navController.navigate("cast_detail/$castId/$name//")
                true
            }
            ActionType.NAVIGATE_TO_GENRE -> {
                val genreId = action.data["id"] as? Int ?: return false
                val mediaType = action.data["type"] as? String ?: "movie"
                navController.navigate("genre_content/$mediaType/$genreId/Recommended")
                true
            }
            ActionType.SEARCH_MOVIES -> {
                val query = action.data["query"] as? String ?: return false
                navController.navigate(Screen.Search.route)
                // Could also implement a search state update mechanism
                true
            }
            ActionType.SEARCH_TV_SHOWS -> {
                val query = action.data["query"] as? String ?: return false
                navController.navigate(Screen.Search.route)
                true
            }
            else -> false
        }
    }
}
```

### Step 7: Integration with MobileHomeScreen

Update the main screen to include the AI Assistant:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/screens/home/mobile/MobileHomeScreen.kt`**

Add the wrapper to the main composable. First, read the current file to understand its structure, then add the import and wrap the content:

```kotlin
// Add import
import com.kiduyuk.klausk.kiduyutv.ui.components.ai.AiAssistantScreenWrapper

// Then wrap the root content in the main composable function:
// Inside the main composable function, wrap the Scaffold content:
@Composable
fun MobileHomeScreen(
    navController: NavHostController,
    // ... existing parameters
) {
    // Add the action handler
    val actionHandler = remember { NavigationActionHandler(navController) }
    
    // Get API key from BuildConfig or local properties
    val apiKey = BuildConfig.GEMINI_API_KEY

    AiAssistantScreenWrapper(
        apiKey = apiKey,
        onActionClick = { action ->
            actionHandler.handleAction(action)
        }
    ) {
        // Existing scaffold content here
        Scaffold(
            // ... existing content
        ) { paddingValues ->
            // ... existing content
        }
    }
}
```

### Step 8: Update Screen Routes

Update the `Screen.kt` navigation file to include any new routes if needed:

**`app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/navigation/Screen.kt`**

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.navigation

sealed class Screen(val route: String) {
    // ... existing screens

    companion object {
        // Add factory method for creating routes with parameters
        fun MovieDetail.createRoute(movieId: Int) = "movie_detail/$movieId"
        fun TvShowDetail.createRoute(tvId: Int) = "tv_show_detail/$tvId"
    }
}
```

## Configuration and Security

### API Key Management

For production, never hardcode API keys. Use one of these approaches:

**Option 1: local.properties (Development)**
```properties
# local.properties
GEMINI_API_KEY=your_api_key_here
```

Access in build.gradle:
```kotlin
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
```

**Option 2: BuildConfig Fields (Production)**
In `app/build.gradle.kts`:
```kotlin
buildTypes {
    release {
        buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY")}\"")
    }
}
```

**Option 3: Gradle Properties with Encryption (Recommended for Production)**
Use a secrets management solution or environment variables in your CI/CD pipeline.

## Testing Recommendations

1. **Unit Tests**: Test the `GeminiService` response parsing and the `ActionCommand` parsing logic
2. **UI Tests**: Test the chat interface interactions using Espresso or Compose UI tests
3. **Integration Tests**: Verify that navigation actions correctly open screens
4. **Error Handling**: Test scenarios where the API fails or returns unexpected responses

## Summary

This implementation plan provides a complete framework for adding a Gemini-powered AI Assistant to the KiduyuTv mobile application. The key components include:

- **Data Layer**: `ChatMessage` and `ActionCommand` models for conversation management
- **Service Layer**: `GeminiService` for API communication
- **ViewModel**: `AiAssistantViewModel` for state management and business logic
- **UI Components**: `AiAssistantFab`, `AiChatDialog`, `ChatBubble`, and `ActionButton`
- **Integration**: Wrapper composable and navigation action handler

The implementation follows the existing app architecture and integrates seamlessly with the Jetpack Compose navigation system, providing users with an intuitive chat interface for discovering content.