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
