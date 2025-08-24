package com.blurr.voice.v2.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import com.blurr.voice.data.Memory
import com.blurr.voice.data.MemoryDao
import com.blurr.voice.data.AppDatabase
import java.util.UUID

class AdvancedMemorySystem(
    private val context: Context,
    private val memoryDao: MemoryDao
) {
    
    companion object {
        private const val TAG = "AdvancedMemorySystem"
        private const val MAX_SHORT_TERM_SIZE = 100
        private const val MAX_WORKING_MEMORY_SIZE = 10
        private const val CONSOLIDATION_INTERVAL = 3600000L // 1 hour
        private const val RELEVANCE_DECAY_RATE = 0.95
    }
    
    // Different memory stores
    private val workingMemory = WorkingMemoryStore()
    private val shortTermMemory = ShortTermMemoryStore()
    private val episodicMemory = EpisodicMemoryStore()
    private val semanticMemory = SemanticMemoryStore()
    private val proceduralMemory = ProceduralMemoryStore()
    
    // Context tracking
    private val currentContext = ConcurrentHashMap<String, Any>()
    private val memoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        startMemoryConsolidation()
        loadPersistentMemories()
    }
    
    data class MemoryItem(
        val id: String = UUID.randomUUID().toString(),
        val content: String,
        val type: MemoryType,
        val context: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis(),
        val relevance: Double = 1.0,
        val accessCount: Int = 0,
        val associations: List<String> = emptyList(),
        val confidence: Double = 1.0,
        val source: String? = null,
        val embedding: FloatArray? = null
    )
    
    enum class MemoryType {
        WORKING,      // Current task context
        SHORT_TERM,   // Recent interactions
        EPISODIC,     // Specific events/experiences
        SEMANTIC,     // Facts and knowledge
        PROCEDURAL    // How to do things
    }
    
    suspend fun remember(
        content: String,
        type: MemoryType = MemoryType.SHORT_TERM,
        context: Map<String, Any> = emptyMap()
    ): MemoryItem {
        val memory = MemoryItem(
            content = content,
            type = type,
            context = context + currentContext,
            source = context["source"] as? String
        )
        
        when (type) {
            MemoryType.WORKING -> workingMemory.store(memory)
            MemoryType.SHORT_TERM -> shortTermMemory.store(memory)
            MemoryType.EPISODIC -> episodicMemory.store(memory)
            MemoryType.SEMANTIC -> semanticMemory.store(memory)
            MemoryType.PROCEDURAL -> proceduralMemory.store(memory)
        }
        
        // Create associations
        createAssociations(memory)
        
        // Persist important memories
        if (shouldPersist(memory)) {
            persistMemory(memory)
        }
        
        Log.d(TAG, "Stored memory: ${memory.content.take(50)}... (${memory.type})")
        return memory
    }
    
    suspend fun recall(
        query: String,
        types: List<MemoryType> = MemoryType.values().toList(),
        limit: Int = 10
    ): List<MemoryItem> {
        val results = mutableListOf<MemoryItem>()
        
        // Search across specified memory types
        types.forEach { type ->
            val memories = when (type) {
                MemoryType.WORKING -> workingMemory.search(query, limit)
                MemoryType.SHORT_TERM -> shortTermMemory.search(query, limit)
                MemoryType.EPISODIC -> episodicMemory.search(query, limit)
                MemoryType.SEMANTIC -> semanticMemory.search(query, limit)
                MemoryType.PROCEDURAL -> proceduralMemory.search(query, limit)
            }
            results.addAll(memories)
        }
        
        // Rank by relevance and recency
        return results
            .distinctBy { it.id }
            .sortedByDescending { calculateRelevance(it, query) }
            .take(limit)
            .onEach { updateAccessCount(it) }
    }
    
    suspend fun getWorkflowMemory(workflowId: String): List<MemoryItem> {
        return recall(
            query = workflowId,
            types = listOf(MemoryType.WORKING, MemoryType.EPISODIC),
            limit = 20
        )
    }
    
    suspend fun learnFromExperience(
        task: String,
        steps: List<String>,
        outcome: String,
        success: Boolean
    ) {
        // Store as procedural memory
        val procedure = buildString {
            appendLine("Task: $task")
            appendLine("Steps:")
            steps.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
            appendLine("Outcome: $outcome")
            appendLine("Success: $success")
        }
        
        remember(
            content = procedure,
            type = MemoryType.PROCEDURAL,
            context = mapOf(
                "task" to task,
                "success" to success,
                "step_count" to steps.size
            )
        )
        
        // Store as episodic memory for specific instance
        remember(
            content = "Completed $task with outcome: $outcome",
            type = MemoryType.EPISODIC,
            context = mapOf(
                "task" to task,
                "timestamp" to System.currentTimeMillis(),
                "success" to success
            )
        )
        
        // Update semantic knowledge
        if (success) {
            updateSemanticKnowledge(task, steps)
        }
    }
    
    suspend fun updateContext(key: String, value: Any) {
        currentContext[key] = value
        
        // Store in working memory
        remember(
            content = "Context update: $key = $value",
            type = MemoryType.WORKING,
            context = mapOf("context_key" to key)
        )
    }
    
    suspend fun consolidateMemories() {
        Log.d(TAG, "Starting memory consolidation")
        
        // Move important short-term memories to long-term
        val importantMemories = shortTermMemory.getImportantMemories()
        importantMemories.forEach { memory ->
            when {
                memory.accessCount > 3 -> {
                    // Frequently accessed -> semantic
                    semanticMemory.store(memory.copy(type = MemoryType.SEMANTIC))
                }
                memory.context["emotional_weight"] as? Double ?: 0.0 > 0.7 -> {
                    // Emotionally significant -> episodic
                    episodicMemory.store(memory.copy(type = MemoryType.EPISODIC))
                }
            }
        }
        
        // Clear old working memory
        workingMemory.clearOld()
        
        // Decay relevance scores
        applyRelevanceDecay()
        
        Log.d(TAG, "Memory consolidation complete")
    }
    
    private fun calculateRelevance(memory: MemoryItem, query: String): Double {
        var relevance = memory.relevance
        
        // Recency factor
        val age = System.currentTimeMillis() - memory.timestamp
        val recencyFactor = Math.exp(-age / 86400000.0) // Decay over days
        relevance *= recencyFactor
        
        // Access frequency factor
        val frequencyFactor = 1.0 + (memory.accessCount * 0.1)
        relevance *= frequencyFactor
        
        // Content similarity (simple keyword matching)
        val queryWords = query.lowercase().split(" ")
        val memoryWords = memory.content.lowercase().split(" ")
        val overlap = queryWords.intersect(memoryWords.toSet()).size
        val similarityFactor = overlap.toDouble() / queryWords.size
        relevance *= (1.0 + similarityFactor)
        
        // Context match
        if (memory.context.any { it.value.toString().contains(query, ignoreCase = true) }) {
            relevance *= 1.5
        }
        
        return relevance
    }
    
    private suspend fun createAssociations(memory: MemoryItem) {
        // Find related memories
        val related = recall(memory.content, limit = 5)
        val associations = related.map { it.id }
        
        // Update memory with associations
        val updatedMemory = memory.copy(associations = associations)
        when (memory.type) {
            MemoryType.WORKING -> workingMemory.update(updatedMemory)
            MemoryType.SHORT_TERM -> shortTermMemory.update(updatedMemory)
            MemoryType.EPISODIC -> episodicMemory.update(updatedMemory)
            MemoryType.SEMANTIC -> semanticMemory.update(updatedMemory)
            MemoryType.PROCEDURAL -> proceduralMemory.update(updatedMemory)
        }
    }
    
    private fun shouldPersist(memory: MemoryItem): Boolean {
        return memory.type in listOf(
            MemoryType.SEMANTIC,
            MemoryType.PROCEDURAL,
            MemoryType.EPISODIC
        ) || memory.relevance > 0.8
    }
    
    private suspend fun persistMemory(memory: MemoryItem) {
        withContext(Dispatchers.IO) {
            try {
                val dbMemory = Memory(
                    content = memory.content,
                    type = memory.type.name,
                    timestamp = memory.timestamp,
                    context = memory.context.toString(),
                    embedding = memory.embedding
                )
                memoryDao.insert(dbMemory)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist memory", e)
            }
        }
    }
    
    private fun updateAccessCount(memory: MemoryItem) {
        val updated = memory.copy(accessCount = memory.accessCount + 1)
        when (memory.type) {
            MemoryType.WORKING -> workingMemory.update(updated)
            MemoryType.SHORT_TERM -> shortTermMemory.update(updated)
            MemoryType.EPISODIC -> episodicMemory.update(updated)
            MemoryType.SEMANTIC -> semanticMemory.update(updated)
            MemoryType.PROCEDURAL -> proceduralMemory.update(updated)
        }
    }
    
    private suspend fun updateSemanticKnowledge(task: String, steps: List<String>) {
        val knowledge = "Learned procedure for $task: ${steps.size} steps"
        remember(
            content = knowledge,
            type = MemoryType.SEMANTIC,
            context = mapOf(
                "task" to task,
                "procedure_learned" to true
            )
        )
    }
    
    private fun applyRelevanceDecay() {
        listOf(workingMemory, shortTermMemory, episodicMemory, semanticMemory, proceduralMemory)
            .forEach { store ->
                store.applyDecay(RELEVANCE_DECAY_RATE)
            }
    }
    
    private fun startMemoryConsolidation() {
        memoryScope.launch {
            while (isActive) {
                delay(CONSOLIDATION_INTERVAL)
                consolidateMemories()
            }
        }
    }
    
    private fun loadPersistentMemories() {
        memoryScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val memories = memoryDao.getAllMemories()
                    memories.forEach { dbMemory ->
                        val memoryItem = MemoryItem(
                            content = dbMemory.content,
                            type = MemoryType.valueOf(dbMemory.type),
                            timestamp = dbMemory.timestamp,
                            embedding = dbMemory.embedding
                        )
                        
                        when (memoryItem.type) {
                            MemoryType.SEMANTIC -> semanticMemory.store(memoryItem)
                            MemoryType.PROCEDURAL -> proceduralMemory.store(memoryItem)
                            MemoryType.EPISODIC -> episodicMemory.store(memoryItem)
                            else -> {}
                        }
                    }
                    Log.d(TAG, "Loaded ${memories.size} persistent memories")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load persistent memories", e)
                }
            }
        }
    }
    
    // Memory store implementations
    abstract class MemoryStore {
        protected val memories = ConcurrentHashMap<String, MemoryItem>()
        
        open fun store(memory: MemoryItem) {
            memories[memory.id] = memory
        }
        
        open fun update(memory: MemoryItem) {
            memories[memory.id] = memory
        }
        
        open fun search(query: String, limit: Int): List<MemoryItem> {
            return memories.values
                .filter { it.content.contains(query, ignoreCase = true) }
                .sortedByDescending { it.relevance }
                .take(limit)
        }
        
        open fun applyDecay(rate: Double) {
            memories.values.forEach { memory ->
                val decayed = memory.copy(relevance = memory.relevance * rate)
                memories[memory.id] = decayed
            }
        }
    }
    
    class WorkingMemoryStore : MemoryStore() {
        override fun store(memory: MemoryItem) {
            // Keep only recent items
            if (memories.size >= MAX_WORKING_MEMORY_SIZE) {
                val oldest = memories.values.minByOrNull { it.timestamp }
                oldest?.let { memories.remove(it.id) }
            }
            super.store(memory)
        }
        
        fun clearOld() {
            val cutoff = System.currentTimeMillis() - 3600000 // 1 hour
            memories.values
                .filter { it.timestamp < cutoff }
                .forEach { memories.remove(it.id) }
        }
    }
    
    class ShortTermMemoryStore : MemoryStore() {
        override fun store(memory: MemoryItem) {
            if (memories.size >= MAX_SHORT_TERM_SIZE) {
                val leastRelevant = memories.values.minByOrNull { it.relevance }
                leastRelevant?.let { memories.remove(it.id) }
            }
            super.store(memory)
        }
        
        fun getImportantMemories(): List<MemoryItem> {
            return memories.values
                .filter { it.relevance > 0.7 || it.accessCount > 2 }
                .toList()
        }
    }
    
    class EpisodicMemoryStore : MemoryStore()
    class SemanticMemoryStore : MemoryStore()
    class ProceduralMemoryStore : MemoryStore()
}