package com.blurr.app.data

import android.util.Log
import com.blurr.app.api.GeminiApi
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Extracts and stores memories from conversations using LLM-based memory extraction
 */
object MemoryExtractor {
    
    private val memoryExtractionPrompt = """
        You are a memory extraction agent. Analyze the following conversation and extract key, lasting facts about the user which are supposed to be known by perfect friend to understand the user better.
        
        Focus on:
        - Personal details (family, relationships, preferences, life events)
        - Significant experiences or traumas or hobbies
        - Important dates, locations, or circumstances
        - Long-term preferences or goals or habits etc
        Ignore:
        - Fleeting emotions or temporary states
        - Generic statements or hypothetical scenarios
        - Technical details or app-specific information
        
        Format each memory as a clear, concise sentence that captures the essential fact.
        If no significant memories are found, return "NO_MEMORIES".
        
        Conversation:
        {conversation}
        
        Extracted Memories (one per line):
    """.trimIndent()
    
    /**
     * Extracts memories from a conversation and stores them asynchronously
     * This is a fire-and-forget operation that doesn't block the conversation flow
     */
    fun extractAndStoreMemories(
        conversationHistory: List<Pair<String, List<Any>>>,
        memoryManager: MemoryManager,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d("MemoryExtractor", "Starting memory extraction from conversation")
                
                // Convert conversation to text format for analysis
                val conversationText = formatConversationForExtraction(conversationHistory)
                
                // Create the extraction prompt
                val extractionPrompt = memoryExtractionPrompt.replace("{conversation}", conversationText)
                
                // Call LLM for memory extraction
                val extractionChat = listOf(
                    "user" to listOf(TextPart(extractionPrompt))
                )
                
                val extractionResponse = GeminiApi.generateContent(extractionChat)
                
                if (extractionResponse != null) {
                    Log.d("MemoryExtractor", "Memory extraction response: ${extractionResponse.take(200)}...")
                    
                    // Parse the extracted memories
                    val memories = parseExtractedMemories(extractionResponse)
                    
                    if (memories.isNotEmpty()) {
                        Log.d("MemoryExtractor", "Extracted ${memories.size} memories")
                        
                        // Store each memory asynchronously
                        memories.forEach { memory ->
                            try {
                                val success = memoryManager.addMemory(memory)
                                if (success) {
                                    Log.d("MemoryExtractor", "Successfully stored memory: $memory")
                                } else {
                                    Log.e("MemoryExtractor", "Failed to store memory: $memory")
                                }
                            } catch (e: Exception) {
                                Log.e("MemoryExtractor", "Error storing memory: $memory", e)
                            }
                        }
                    } else {
                        Log.d("MemoryExtractor", "No significant memories found in conversation")
                    }
                } else {
                    Log.e("MemoryExtractor", "Failed to get memory extraction response")
                }
                
            } catch (e: Exception) {
                Log.e("MemoryExtractor", "Error during memory extraction", e)
            }
        }
    }
    
    /**
     * Formats conversation history for memory extraction analysis
     */
    private fun formatConversationForExtraction(conversationHistory: List<Pair<String, List<Any>>>): String {
        return conversationHistory.joinToString("\n") { (role, parts) ->
            val textParts = parts.filterIsInstance<TextPart>()
            val text = textParts.joinToString(" ") { it.text }
            "$role: $text"
        }
    }
    
    /**
     * Parses the LLM response to extract individual memories
     */
    private fun parseExtractedMemories(response: String): List<String> {
        return try {
            response.lines()
                .filter { it.isNotBlank() }
                .filter { !it.equals("NO_MEMORIES", ignoreCase = true) }
                .filter { !it.startsWith("Extracted Memories") }
                .filter { !it.startsWith("Memories:") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("MemoryExtractor", "Error parsing extracted memories", e)
            emptyList()
        }
    }
} 