package com.blurr.voice.v2.workflow.models

import java.util.concurrent.ConcurrentHashMap

data class Workflow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val context: Map<String, Any> = emptyMap(),
    val checkpoints: List<WorkflowCheckpoint> = emptyList(),
    val errorHandlers: Map<String, ErrorHandler> = emptyMap()
)

data class WorkflowStep(
    val id: String,
    val name: String,
    val action: ActionType,
    val targetApp: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val dependencies: List<String> = emptyList(),
    val retryable: Boolean = true,
    val timeout: Long = 30000L,
    val requiresConfirmation: Boolean = false,
    val parallel: Boolean = false,
    val template: String? = null,
    val fallbackAction: ActionType? = null
)

enum class ActionType {
    LAUNCH_APP,
    SEARCH,
    FILTER,
    EXTRACT_DATA,
    SEND_MESSAGE,
    CREATE_EVENT,
    TAP,
    TYPE,
    SCROLL,
    WAIT,
    SWIPE,
    LONG_PRESS,
    BACK,
    HOME,
    RECENT_APPS,
    TAKE_SCREENSHOT,
    READ_SCREEN,
    SIMPLE_TASK,
    COMPLEX_SEQUENCE,
    CONDITIONAL,
    LOOP
}

data class WorkflowCheckpoint(
    val id: String,
    val afterStep: String,
    val state: Map<String, Any>
)

data class ErrorHandler(
    val stepId: String,
    val retryStrategy: RetryStrategy,
    val fallbackAction: ActionType? = null,
    val maxRetries: Int = 3
)

enum class RetryStrategy {
    NONE,
    IMMEDIATE,
    EXPONENTIAL_BACKOFF,
    LINEAR_BACKOFF,
    CUSTOM
}

class WorkflowExecution(
    val workflowId: String,
    val workflow: Workflow,
    val startTime: Long
) {
    private val completedStepsMap = ConcurrentHashMap<String, Boolean>()
    val outputs = ConcurrentHashMap<String, Any>()
    var currentStep: WorkflowStep? = null
    private var cancelled = false
    
    val completedSteps: List<String>
        get() = completedStepsMap.keys.toList()
    
    fun markStepCompleted(stepId: String) {
        completedStepsMap[stepId] = true
    }
    
    fun isStepCompleted(stepId: String): Boolean {
        return completedStepsMap[stepId] == true
    }
    
    fun addOutput(stepId: String, output: Any) {
        outputs[stepId] = output
    }
    
    fun cancel() {
        cancelled = true
    }
    
    fun isCancelled(): Boolean = cancelled
    
    fun getStatus(): WorkflowStatus {
        return WorkflowStatus(
            workflowId = workflowId,
            currentStep = currentStep?.name,
            completedSteps = completedSteps.size,
            totalSteps = workflow.steps.size,
            isRunning = !cancelled && currentStep != null,
            startTime = startTime,
            outputs = outputs.toMap()
        )
    }
    
    fun toState(): WorkflowState {
        return WorkflowState(
            workflowId = workflowId,
            completedSteps = completedSteps,
            outputs = outputs.toMap(),
            lastCompletedStep = completedSteps.lastOrNull(),
            context = workflow.context
        )
    }
    
    fun restoreFromState(state: WorkflowState) {
        state.completedSteps.forEach { stepId ->
            completedStepsMap[stepId] = true
        }
        outputs.putAll(state.outputs)
    }
}

data class WorkflowState(
    val workflowId: String,
    val completedSteps: List<String>,
    val outputs: Map<String, Any>,
    val lastCompletedStep: String?,
    val context: Map<String, Any>
)

data class WorkflowStatus(
    val workflowId: String,
    val currentStep: String?,
    val completedSteps: Int,
    val totalSteps: Int,
    val isRunning: Boolean,
    val startTime: Long,
    val outputs: Map<String, Any>
)

sealed class WorkflowResult {
    data class Success(
        val workflowId: String,
        val completedSteps: List<String>,
        val outputs: Map<String, Any>
    ) : WorkflowResult()
    
    data class Failure(
        val workflowId: String,
        val error: Exception,
        val completedSteps: List<String>,
        val canRetry: Boolean
    ) : WorkflowResult()
    
    data class Partial(
        val workflowId: String,
        val completedSteps: List<String>,
        val remainingSteps: List<String>,
        val outputs: Map<String, Any>
    ) : WorkflowResult()
}

class WorkflowStepException(
    val step: WorkflowStep,
    cause: Throwable
) : Exception("Failed to execute step: ${step.name}", cause)