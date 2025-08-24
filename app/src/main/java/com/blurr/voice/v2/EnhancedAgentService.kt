package com.blurr.voice.v2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import com.blurr.voice.v2.workflow.WorkflowEngine
import com.blurr.voice.v2.workflow.state.WorkflowStateManager
import com.blurr.voice.v2.workflow.recovery.ErrorRecoveryStrategy
import com.blurr.voice.v2.workflow.templates.WorkflowTemplates
import com.blurr.voice.v2.memory.AdvancedMemorySystem
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.api.EnhancedApiKeyManager
import com.blurr.voice.api.Finger
import com.blurr.voice.data.AppDatabase
import com.blurr.voice.v2.llm.GeminiAPI

class EnhancedAgentService(
    private val context: Context,
    private val screenAnalysis: ScreenAnalysis,
    private val finger: Finger
) {
    
    companion object {
        private const val TAG = "EnhancedAgentService"
    }
    
    private val apiKeyManager = EnhancedApiKeyManager()
    private val stateManager = WorkflowStateManager(context)
    private val errorRecovery = ErrorRecoveryStrategy(screenAnalysis, finger)
    private val workflowEngine = WorkflowEngine(screenAnalysis, stateManager, errorRecovery)
    
    private val database = AppDatabase.getDatabase(context)
    private val memorySystem = AdvancedMemorySystem(context, database.memoryDao())
    
    private val agentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val geminiApi = GeminiAPI(apiKeyManager)
    
    suspend fun handleComplexTask(userRequest: String): String {
        Log.d(TAG, "Processing complex task: $userRequest")
        
        try {
            // Store request in memory
            memorySystem.remember(
                content = "User request: $userRequest",
                type = AdvancedMemorySystem.MemoryType.WORKING,
                context = mapOf("source" to "user_input")
            )
            
            // Analyze request and determine if template exists
            val template = analyzeForTemplate(userRequest)
            
            val result = if (template != null) {
                Log.d(TAG, "Using template: ${template.name}")
                executeTemplateWorkflow(template.id, userRequest)
            } else {
                Log.d(TAG, "Creating custom workflow")
                executeCustomWorkflow(userRequest)
            }
            
            // Learn from the experience
            memorySystem.learnFromExperience(
                task = userRequest,
                steps = result.completedSteps,
                outcome = result.toString(),
                success = result.success
            )
            
            return generateResponse(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed", e)
            handleTaskFailure(userRequest, e)
            return "I encountered an issue: ${e.message}. I'll remember this and try a different approach next time."
        }
    }
    
    private suspend fun analyzeForTemplate(request: String): WorkflowTemplate? {
        val keywords = mapOf(
            "hire" to "hire_freelancer",
            "freelancer" to "hire_freelancer",
            "upwork" to "hire_freelancer",
            "schedule" to "schedule_meeting",
            "meeting" to "schedule_meeting",
            "calendar" to "schedule_meeting",
            "research" to "research_and_report",
            "social media" to "social_media_post",
            "shopping" to "online_shopping",
            "travel" to "travel_booking",
            "job" to "job_application",
            "food" to "food_delivery"
        )
        
        val templateName = keywords.entries
            .find { (keyword, _) -> request.contains(keyword, ignoreCase = true) }
            ?.value
        
        return templateName?.let { name ->
            WorkflowTemplates.getTemplate(name)?.let {
                WorkflowTemplate(name, it)
            }
        }
    }
    
    private suspend fun executeTemplateWorkflow(
        templateId: String,
        userRequest: String
    ): TaskResult {
        // Extract parameters from user request
        val parameters = extractParameters(userRequest)
        
        // Get relevant memories
        val memories = memorySystem.recall(userRequest, limit = 5)
        val context = buildContext(parameters, memories)
        
        // Execute workflow
        val result = workflowEngine.executeComplexTask(userRequest, context)
        
        return TaskResult(
            success = result is com.blurr.voice.v2.workflow.models.WorkflowResult.Success,
            completedSteps = when (result) {
                is com.blurr.voice.v2.workflow.models.WorkflowResult.Success -> result.completedSteps
                is com.blurr.voice.v2.workflow.models.WorkflowResult.Failure -> result.completedSteps
                is com.blurr.voice.v2.workflow.models.WorkflowResult.Partial -> result.completedSteps
            },
            message = generateResultMessage(result)
        )
    }
    
    private suspend fun executeCustomWorkflow(userRequest: String): TaskResult {
        // Use AI to understand and decompose the task
        val taskAnalysis = analyzeTaskWithAI(userRequest)
        
        // Get relevant memories for context
        val memories = memorySystem.recall(userRequest, limit = 10)
        
        // Build context from memories and current state
        val context = buildContext(taskAnalysis, memories)
        
        // Execute the workflow
        val result = workflowEngine.executeComplexTask(userRequest, context)
        
        return TaskResult(
            success = result is com.blurr.voice.v2.workflow.models.WorkflowResult.Success,
            completedSteps = when (result) {
                is com.blurr.voice.v2.workflow.models.WorkflowResult.Success -> result.completedSteps
                is com.blurr.voice.v2.workflow.models.WorkflowResult.Failure -> result.completedSteps
                is com.blurr.voice.v2.workflow.models.WorkflowResult.Partial -> result.completedSteps
            },
            message = generateResultMessage(result)
        )
    }
    
    private suspend fun analyzeTaskWithAI(request: String): Map<String, Any> {
        val apiKey = apiKeyManager.getNextAvailableKey() ?: throw Exception("No API keys available")
        
        val prompt = """
        Analyze this user request and break it down into actionable components:
        Request: "$request"
        
        Provide:
        1. Main objective
        2. Required apps/services
        3. Step-by-step actions needed
        4. Success criteria
        5. Potential challenges
        
        Format as structured data.
        """.trimIndent()
        
        val response = geminiApi.generateContent(prompt, apiKey)
        return parseAIResponse(response)
    }
    
    private fun extractParameters(request: String): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>()
        
        // Extract budget
        val budgetPattern = "\\$?([0-9]+)\\s*(USD|dollars?)?".toRegex(RegexOption.IGNORE_CASE)
        budgetPattern.find(request)?.let {
            parameters["budget"] = it.groupValues[1].toIntOrNull() ?: 0
        }
        
        // Extract job title/role
        val rolePattern = "(AI|fashion|designer|developer|writer|artist)".toRegex(RegexOption.IGNORE_CASE)
        rolePattern.findAll(request).map { it.value }.toList().let {
            if (it.isNotEmpty()) parameters["job_title"] = it.joinToString(" ")
        }
        
        // Extract time/date
        val timePattern = "(today|tomorrow|next week|[0-9]{1,2}:[0-9]{2})".toRegex(RegexOption.IGNORE_CASE)
        timePattern.find(request)?.let {
            parameters["time"] = it.value
        }
        
        return parameters
    }
    
    private fun buildContext(
        parameters: Map<String, Any>,
        memories: List<AdvancedMemorySystem.MemoryItem>
    ): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            putAll(parameters)
            put("memories", memories.map { it.content })
            put("timestamp", System.currentTimeMillis())
            put("api_keys_available", apiKeyManager.getHealthyKeyCount())
        }
    }
    
    private fun generateResultMessage(
        result: com.blurr.voice.v2.workflow.models.WorkflowResult
    ): String {
        return when (result) {
            is com.blurr.voice.v2.workflow.models.WorkflowResult.Success -> {
                "Successfully completed all ${result.completedSteps.size} steps"
            }
            is com.blurr.voice.v2.workflow.models.WorkflowResult.Failure -> {
                "Completed ${result.completedSteps.size} steps before encountering: ${result.error.message}"
            }
            is com.blurr.voice.v2.workflow.models.WorkflowResult.Partial -> {
                "Partially completed: ${result.completedSteps.size} of ${result.remainingSteps.size + result.completedSteps.size} steps"
            }
        }
    }
    
    private fun generateResponse(result: TaskResult): String {
        return if (result.success) {
            "✓ Task completed successfully! ${result.message}"
        } else {
            "Task partially completed. ${result.message}"
        }
    }
    
    private suspend fun handleTaskFailure(request: String, error: Exception) {
        // Store failure in memory for learning
        memorySystem.remember(
            content = "Task failed: $request. Error: ${error.message}",
            type = AdvancedMemorySystem.MemoryType.EPISODIC,
            context = mapOf(
                "error" to error.message.toString(),
                "task" to request,
                "success" to false
            )
        )
        
        // Report API key error if applicable
        if (error.message?.contains("API") == true) {
            // Would need to track which key was used
        }
    }
    
    private fun parseAIResponse(response: String): Map<String, Any> {
        // Parse structured response from AI
        // This is simplified - in production would use proper parsing
        return mapOf(
            "analysis" to response,
            "parsed" to true
        )
    }
    
    suspend fun getActiveWorkflows(): List<String> {
        return workflowEngine.getActiveWorkflows()
    }
    
    suspend fun cancelWorkflow(workflowId: String) {
        workflowEngine.cancelWorkflow(workflowId)
    }
    
    suspend fun getMemoryStats(): Map<String, Any> {
        val recentMemories = memorySystem.recall("", limit = 100)
        return mapOf(
            "total_memories" to recentMemories.size,
            "memory_types" to recentMemories.groupBy { it.type }.mapValues { it.value.size },
            "api_key_stats" to apiKeyManager.getKeyStatistics()
        )
    }
    
    fun shutdown() {
        agentScope.cancel()
    }
    
    data class TaskResult(
        val success: Boolean,
        val completedSteps: List<String>,
        val message: String
    )
    
    data class WorkflowTemplate(
        val name: String,
        val workflow: com.blurr.voice.v2.workflow.models.Workflow
    ) {
        val id: String get() = workflow.id
    }
}