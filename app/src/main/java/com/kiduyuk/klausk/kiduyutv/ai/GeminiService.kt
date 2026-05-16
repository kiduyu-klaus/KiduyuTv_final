package com.kiduyuk.klausk.kiduyutv.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service class for interacting with the Gemini AI API.
 * Handles conversation management and response parsing.
 *
 * @param apiKey The Gemini API key for authentication
 */
class GeminiService(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = apiKey
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