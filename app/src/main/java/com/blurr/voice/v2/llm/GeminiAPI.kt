package com.blurr.voice.v2.llm

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import com.blurr.voice.api.ApiKeyManager // Assuming ApiKeyManager exists in this package

private const val TAG = "GeminiV2Api"

/**
 * A modern Gemini API client using the official Google AI SDK.
 * It handles the conversion of our internal message format to the SDK's format,
 * manages API key rotation via ApiKeyManager, and includes a retry mechanism.
 *
 * @param modelName The name of the Gemini model to use (e.g., "gemini-1.5-flash").
 * @param maxRetry The maximum number of times to retry a failed API call.
 */
class GeminiApi(
    private val modelName: String,
    private val maxRetry: Int = 3
) {

    /**
     * Generates content based on a list of messages.
     *
     * @param messages The list of [GeminiMessage] objects representing the conversation history.
     * @return The response text from the model as a String, or null if the call fails after all retries.
     */
    suspend fun generateContent(messages: List<GeminiMessage>): String? {
        var attempts = 0
        while (attempts < maxRetry) {
            try {
                // 1. Get a new API key for each attempt, as in the original design
                val apiKey = ApiKeyManager.getNextKey()
                Log.d(TAG, "Attempt ${attempts + 1}/$maxRetry: Calling model $modelName with key ending in ...${apiKey.takeLast(4)}")

                // 2. Initialize the GenerativeModel from the SDK
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey
                )

                // 3. Convert our internal message format to the SDK's `Content` format
                val history = convertToSdkHistory(messages)

                // 4. Make the API call
                val response = generativeModel.generateContent(*history.toTypedArray())

                // 5. Parse and return the response
                val responseText = response.text
                if (responseText != null) {
                    Log.d(TAG, "Successfully received response from model.")
                    return responseText
                } else {
                    val finishReason = response.promptFeedback?.blockReason?.name ?: "UNKNOWN"
                    // Handle cases where the response is empty or blocked
                    Log.w(TAG, "API call succeeded but returned no text. Finish Reason: $finishReason")
                    // You might want to retry for certain block reasons, but for now, we'll fail this attempt.
                    throw Exception("Blocked or empty response from API. Reason: $finishReason")
                }

            } catch (e: Exception) {
                Log.e(TAG, "API call failed on attempt ${attempts + 1}: ${e.message}", e)
                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts // Progressive backoff
                    Log.d(TAG, "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e(TAG, "Request failed after all $maxRetry retries.")
                    return null // Failed after all retries
                }
            }
        }
        return null
    }

    /**
     * Converts our internal `List<GeminiMessage>` to the `List<Content>` required by the Google AI SDK.
     */
    private fun convertToSdkHistory(messages: List<GeminiMessage>): List<Content> {
        return messages.map { message ->
            // Map our enum to the SDK's role strings
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.MODEL -> "model"
                MessageRole.TOOL -> "tool" // The SDK uses 'function' but 'tool' is the modern term
            }

            content(role) {
                message.parts.forEach { part ->
                    when (part) {
                        is TextPart -> text(part.text)
                    }
                }
            }
        }
    }
}