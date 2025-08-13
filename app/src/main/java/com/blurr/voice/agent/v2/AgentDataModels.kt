package com.blurr.voice.agent.v2
import java.util.UUID

interface ActionModel {
    fun getIndex(): Int? = null
    fun toMap(): Map<String, Any?> = emptyMap()
}

enum class ToolCallingMethod { FUNCTION_CALLING, JSON_MODE, RAW, AUTO, TOOLS }

data class MessageManagerState(
    val context: String? = null
)

data class XMLElementNode(
    val index: Int? = null,
    val tag: String? = null,
    val text: String? = null,
    val bounds: String? = null,
)

data class XMLHistoryElement(
    val tag: String? = null,
    val text: String? = null,
    val bounds: String? = null,
)

object HistoryTreeProcessor {
    fun convertDomElementToHistoryElement(el: XMLElementNode): XMLHistoryElement =
        XMLHistoryElement(tag = el.tag, text = el.text, bounds = el.bounds)
}

typealias SelectorMap = Map<Int, XMLElementNode>

data class AndroidStateHistory(
    val appActivity: String? = null,
    val screenshot: String? = null,
    val interactedElement: List<XMLHistoryElement?>? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "activity" to appActivity,
        "screenshot" to screenshot,
        "interacted_element" to interactedElement
    )
}

data class AgentSettings(
    val useVision: Boolean = false,
    val useVisionForPlanner: Boolean = false,
    val saveConversationPath: String? = null,
    val saveConversationPathEncoding: String? = "utf-8",
    val maxFailures: Int = 3,
    val retryDelay: Int = 10,
    val maxInputTokens: Int = 128_000,
    val validateOutput: Boolean = false,
    val messageContext: String? = null,
    val availableFilePaths: List<String>? = null,
    val overrideSystemMessage: String? = null,
    val extendSystemMessage: String? = null,
    val maxActionsPerStep: Int = 10,
    val toolCallingMethod: ToolCallingMethod? = ToolCallingMethod.AUTO,
    val pageExtractionLlm: String? = null,
    val plannerLlm: String? = null,
    val plannerInterval: Int = 1,
    val isPlannerReasoning: Boolean = false,
    val extendPlannerSystemMessage: String? = null,
)

data class AgentState(
    val agentId: String = UUID.randomUUID().toString(),
    val nSteps: Int = 1,
    val consecutiveFailures: Int = 0,
    val lastResult: List<ActionResult>? = null,
    val history: AgentHistoryList = AgentHistoryList(history = emptyList()),
    val lastPlan: String? = null,
    val lastModelOutput: AgentOutput? = null,
    val paused: Boolean = false,
    val stopped: Boolean = false,
    val messageManagerState: MessageManagerState = MessageManagerState(),
)

data class AgentStepInfo(
    val stepNumber: Int,
    val maxSteps: Int
) {
    fun isLastStep(): Boolean = stepNumber >= maxSteps - 1
}

data class ActionResult(
    val isDone: Boolean? = false,
    val success: Boolean? = null,
    val error: String? = null,
    val attachments: List<String>? = null,
    val longTermMemory: String? = null,
    val extractedContent: String? = null,
    val includeExtractedContentOnlyOnce: Boolean = false,
    val includeInMemory: Boolean = false, // kept for compatibility; not used
) {
    init {
        if (success == true && isDone != true) {
            throw IllegalArgumentException(
                "success=true can only be set when is_done=true. " +
                    "For regular actions that succeed, leave success as null. " +
                    "Use success=false only for actions that fail."
            )
        }
    }
}

data class StepMetadata(
    val stepStartTime: Double,
    val stepEndTime: Double,
    val inputTokens: Int,
    val stepNumber: Int
) {
    val durationSeconds: Double get() = stepEndTime - stepStartTime
}

data class AgentBrain(
    val thinking: String,
    val evaluationPreviousGoal: String,
    val memory: String,
    val nextGoal: String
)

data class AgentOutput(
    val thinking: String,
    val evaluationPreviousGoal: String,
    val memory: String,
    val nextGoal: String,
    val action: List<ActionModel>
) {
    val currentState: AgentBrain
        get() = AgentBrain(
            thinking = thinking,
            evaluationPreviousGoal = evaluationPreviousGoal,
            memory = memory,
            nextGoal = nextGoal
        )
}

data class AgentHistory(
    val modelOutput: AgentOutput?,
    val result: List<ActionResult>,
    val state: AndroidStateHistory,
    val metadata: StepMetadata? = null
) {
    companion object {
        fun getInteractedElement(modelOutput: AgentOutput, selectorMap: SelectorMap): List<XMLHistoryElement?> {
            val out = mutableListOf<XMLHistoryElement?>()
            for (action in modelOutput.action) {
                val idx = action.getIndex()
                if (idx != null && selectorMap.containsKey(idx)) {
                    val el = selectorMap[idx]
                    out.add(el?.let { HistoryTreeProcessor.convertDomElementToHistoryElement(it) })
                } else {
                    out.add(null)
                }
            }
            return out
        }
    }

    fun toMap(): Map<String, Any?> {
        val modelOutputDump = modelOutput?.let { mo ->
            mapOf(
                "thinking" to mo.thinking,
                "evaluation_previous_goal" to mo.evaluationPreviousGoal,
                "memory" to mo.memory,
                "next_goal" to mo.nextGoal,
                "action" to mo.action.map { it.toMap() }
            )
        }
        return mapOf(
            "model_output" to modelOutputDump,
            "result" to result.map {
                mapOf(
                    "is_done" to it.isDone,
                    "success" to it.success,
                    "error" to it.error,
                    "attachments" to it.attachments,
                    "long_term_memory" to it.longTermMemory,
                    "extracted_content" to it.extractedContent,
                    "include_extracted_content_only_once" to it.includeExtractedContentOnlyOnce
                )
            },
            "state" to state.toMap(),
            "metadata" to (metadata?.let { m ->
                mapOf(
                    "step_start_time" to m.stepStartTime,
                    "step_end_time" to m.stepEndTime,
                    "input_tokens" to m.inputTokens,
                    "step_number" to m.stepNumber,
                    "duration_seconds" to m.durationSeconds
                )
            })
        )
    }
}

data class AgentHistoryList(
    val history: List<AgentHistory>
) {
    fun totalDurationSeconds(): Double = history.sumOf { it.metadata?.durationSeconds ?: 0.0 }
    fun totalInputTokens(): Int = history.sumOf { it.metadata?.inputTokens ?: 0 }
    fun inputTokenUsage(): List<Int> = history.mapNotNull { it.metadata?.inputTokens }
    override fun toString(): String =
        "AgentHistoryList(all_results=${actionResults()}, all_model_outputs=${modelActions()})"

    fun actionResults(): List<ActionResult> = history.flatMap { it.result }
    fun modelOutputs(): List<AgentOutput> = history.mapNotNull { it.modelOutput }
    fun modelThoughts(): List<AgentBrain> = modelOutputs().map { it.currentState }
    fun actionNames(): List<String> = modelActions().mapNotNull { it.keys.firstOrNull() }
    fun extractedContent(): List<String> = history.flatMap { h ->
        h.result.mapNotNull { it.extractedContent }
    }
    fun numberOfSteps(): Int = history.size
    fun activity(): List<String?> = history.map { it.state.appActivity }
    fun screenshots(): List<String?> = history.map { it.state.screenshot }

    fun modelActions(): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        history.forEach { h ->
            h.modelOutput?.action?.forEachIndexed { i, act ->
                val base = act.toMap().toMutableMap()
                val interacted = h.state.interactedElement?.getOrNull(i)
                base["interacted_element"] = interacted
                out.add(base)
            }
        }
        return out
    }
}

object AgentError {
    const val VALIDATION_ERROR = "Invalid model output format. Please follow the correct schema."
    const val RATE_LIMIT_ERROR = "Rate limit reached. Waiting before retry."
    const val NO_VALID_ACTION = "No valid action found"

    fun formatError(error: Exception, includeTrace: Boolean = false): String {
        val base = error.message ?: error.toString()
        return if (includeTrace) {
            base + "\n" + error.stackTraceToString()
        } else base
    }
}
