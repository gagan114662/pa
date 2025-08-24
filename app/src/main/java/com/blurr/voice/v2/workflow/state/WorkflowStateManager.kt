package com.blurr.voice.v2.workflow.state

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import com.blurr.voice.v2.workflow.models.WorkflowState

class WorkflowStateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkflowStateManager"
        private const val STATE_DIR = "workflow_states"
        private const val CHECKPOINT_DIR = "workflow_checkpoints"
        private const val FAILED_DIR = "failed_workflows"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    private val stateDir: File by lazy {
        File(context.filesDir, STATE_DIR).apply { mkdirs() }
    }
    
    private val checkpointDir: File by lazy {
        File(context.filesDir, CHECKPOINT_DIR).apply { mkdirs() }
    }
    
    private val failedDir: File by lazy {
        File(context.filesDir, FAILED_DIR).apply { mkdirs() }
    }
    
    suspend fun saveWorkflowState(state: WorkflowState) = withContext(Dispatchers.IO) {
        try {
            val file = File(stateDir, "${state.workflowId}.json")
            val serialized = serializeState(state)
            file.writeText(serialized)
            Log.d(TAG, "Saved workflow state: ${state.workflowId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save workflow state", e)
        }
    }
    
    suspend fun loadWorkflowState(workflowId: String): WorkflowState? = withContext(Dispatchers.IO) {
        try {
            val file = File(stateDir, "$workflowId.json")
            if (file.exists()) {
                val serialized = file.readText()
                val state = deserializeState(serialized)
                Log.d(TAG, "Loaded workflow state: $workflowId")
                return@withContext state
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load workflow state", e)
        }
        return@withContext null
    }
    
    suspend fun saveCheckpoint(
        workflowId: String,
        checkpointId: String,
        state: WorkflowState
    ) = withContext(Dispatchers.IO) {
        try {
            val dir = File(checkpointDir, workflowId).apply { mkdirs() }
            val file = File(dir, "$checkpointId.json")
            val serialized = serializeState(state)
            file.writeText(serialized)
            
            // Keep only last 5 checkpoints
            cleanOldCheckpoints(workflowId)
            
            Log.d(TAG, "Saved checkpoint: $workflowId/$checkpointId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save checkpoint", e)
        }
    }
    
    suspend fun loadLastCheckpoint(workflowId: String): WorkflowState? = withContext(Dispatchers.IO) {
        try {
            val dir = File(checkpointDir, workflowId)
            if (dir.exists()) {
                val checkpoints = dir.listFiles()?.sortedByDescending { it.lastModified() }
                if (!checkpoints.isNullOrEmpty()) {
                    val serialized = checkpoints.first().readText()
                    return@withContext deserializeState(serialized)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load checkpoint", e)
        }
        return@withContext null
    }
    
    suspend fun saveFailedWorkflow(
        state: WorkflowState,
        error: Exception
    ) = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val file = File(failedDir, "${state.workflowId}_$timestamp.json")
            
            val failedState = FailedWorkflowState(
                state = state,
                error = error.message ?: "Unknown error",
                stackTrace = error.stackTraceToString(),
                timestamp = timestamp
            )
            
            file.writeText(json.encodeToString(failedState))
            Log.d(TAG, "Saved failed workflow: ${state.workflowId}")
            
            // Clean old failed workflows (keep last 10)
            cleanOldFailedWorkflows()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save failed workflow", e)
        }
    }
    
    suspend fun getFailedWorkflows(): List<FailedWorkflowState> = withContext(Dispatchers.IO) {
        try {
            val files = failedDir.listFiles()?.sortedByDescending { it.lastModified() }
            return@withContext files?.mapNotNull { file ->
                try {
                    json.decodeFromString<FailedWorkflowState>(file.readText())
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get failed workflows", e)
            return@withContext emptyList()
        }
    }
    
    suspend fun clearWorkflowState(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            // Clear main state
            File(stateDir, "$workflowId.json").delete()
            
            // Clear checkpoints
            File(checkpointDir, workflowId).deleteRecursively()
            
            Log.d(TAG, "Cleared workflow state: $workflowId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear workflow state", e)
        }
    }
    
    suspend fun getAllActiveWorkflows(): List<String> = withContext(Dispatchers.IO) {
        try {
            return@withContext stateDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active workflows", e)
            return@withContext emptyList()
        }
    }
    
    private fun serializeState(state: WorkflowState): String {
        // Convert to serializable format
        val serializableState = SerializableWorkflowState(
            workflowId = state.workflowId,
            completedSteps = state.completedSteps,
            outputs = state.outputs.mapValues { it.value.toString() },
            lastCompletedStep = state.lastCompletedStep,
            context = state.context.mapValues { it.value.toString() }
        )
        return json.encodeToString(serializableState)
    }
    
    private fun deserializeState(serialized: String): WorkflowState {
        val serializableState = json.decodeFromString<SerializableWorkflowState>(serialized)
        return WorkflowState(
            workflowId = serializableState.workflowId,
            completedSteps = serializableState.completedSteps,
            outputs = serializableState.outputs,
            lastCompletedStep = serializableState.lastCompletedStep,
            context = serializableState.context
        )
    }
    
    private fun cleanOldCheckpoints(workflowId: String) {
        try {
            val dir = File(checkpointDir, workflowId)
            val checkpoints = dir.listFiles()?.sortedByDescending { it.lastModified() }
            
            // Keep only last 5 checkpoints
            checkpoints?.drop(5)?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean old checkpoints", e)
        }
    }
    
    private fun cleanOldFailedWorkflows() {
        try {
            val files = failedDir.listFiles()?.sortedByDescending { it.lastModified() }
            
            // Keep only last 10 failed workflows
            files?.drop(10)?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean old failed workflows", e)
        }
    }
    
    @Serializable
    private data class SerializableWorkflowState(
        val workflowId: String,
        val completedSteps: List<String>,
        val outputs: Map<String, String>,
        val lastCompletedStep: String?,
        val context: Map<String, String>
    )
    
    @Serializable
    data class FailedWorkflowState(
        val state: WorkflowState,
        val error: String,
        val stackTrace: String,
        val timestamp: Long
    )
}