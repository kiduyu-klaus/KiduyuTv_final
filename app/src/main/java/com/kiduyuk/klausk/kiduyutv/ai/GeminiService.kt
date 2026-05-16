package com.kiduyuk.klausk.kiduyutv.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GeminiService"

/**
 * Service class for interacting with the Gemini AI API.
 * Handles conversation management and response parsing.
 *
 * @param apiKey The Gemini API key for authentication
 */
class GeminiService(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
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
            // Validate API key before making any API calls
            if (apiKey.isBlank()) {
                Log.e(TAG, "API Key is missing or blank. Please configure your Gemini API key in local.properties or as a CI/CD secret.")
                return@withContext Result.failure(
                    IllegalStateException(
                        "Gemini API key is not configured. Please add GEMINI_API_KEY to your local.properties file " +
                        "or configure it in your CI/CD environment."
                    )
                )
            }
            
            Log.d(TAG, "sendMessage called with: $userMessage")
            Log.d(TAG, "API Key length: ${apiKey.length}")
            
            val prompt = buildPrompt(userMessage, conversationHistory)
            Log.d(TAG, "Built prompt, length: ${prompt.length}")
            
            val response = generativeModel.generateContent(prompt)
            Log.d(TAG, "API Response received: ${response.text?.take(100)}...")
            
            val text = response.text ?: "I couldn't generate a response."
            Log.d(TAG, "Returning text: ${text.take(100)}...")
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "API Error: ${e.message}", e)
            Log.e(TAG, "Error class: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Builds a contextual prompt for the AI assistant.
     * Updated to reflect the hybrid search approach with real TMDB integration.
     */
    private fun buildPrompt(
        message: String,
        history: List<Pair<String, String>>
    ): String {
        val contextPrompt = """
            You are a friendly movie and TV show recommendation assistant for KiduyuTv app.
            Your role is to help users discover content they might enjoy.

            IMPORTANT - Hybrid Search System:
            - The app has real-time search capabilities via TMDB API
            - When users ask about specific movies or TV shows, the system automatically searches TMDB
            - You will receive search results with accurate IDs and information
            - DO NOT provide fake or placeholder IDs - let the search system handle content discovery
            - Your role is to: understand intent, provide context, make recommendations, and be conversational

            Guidelines:
            - Be friendly, helpful, and conversational
            - When providing recommendations, suggest different types of content
            - Use action buttons when mentioning specific content, but focus on recommendations rather than exact matches
            - If no search results are found, provide helpful alternative suggestions
            - You can use the format [ACTION:type|label|text|data] for action buttons
            - Types: MOVIE, TV_SHOW, CAST, GENRE, SEARCH
            - Action buttons should use real IDs from search results when available

            Example responses:
            "Great choice! 'Stranger Things' is a thrilling sci-fi series. Here's what's available in our database..."
            "I love that genre! Here are some popular action movies you might enjoy..."
            "Hmm, let me suggest some alternatives based on what you're looking for..."

            Current user message: $message

            ${if (history.isNotEmpty()) "Previous conversation context:\n${history.takeLast(4).joinToString("\n") { "${it.first}: ${it.second.take(100)}" }}" else ""}
        """.trimIndent()

        return contextPrompt
    }
}
