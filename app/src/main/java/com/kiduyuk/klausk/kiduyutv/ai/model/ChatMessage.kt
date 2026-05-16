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