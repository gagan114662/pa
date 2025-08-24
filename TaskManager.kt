package com.blurr.voice.v2.tasks

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 🧠 Real-Time Task Management System for Genius Panda
 * 
 * Provides human-like task execution with real-time updates, progress tracking,
 * and intelligent task prioritization. Works like a dedicated human worker.
 */
class TaskManager {
    
    // Task execution states
    enum class TaskStatus {
        QUEUED,      // Task received, waiting to start
        IN_PROGRESS, // Currently working on task
        BLOCKED,     // Waiting for dependencies/resources
        PAUSED,      // Temporarily stopped
        COMPLETED,   // Successfully finished
        FAILED,      // Failed with error
        CANCELLED    // Cancelled by user
    }
    
    enum class TaskPriority {
        URGENT,      // Complete within hours
        HIGH,        // Complete within 1-2 days  
        MEDIUM,      // Complete within 1 week
        LOW          // Complete when possible
    }
    
    data class Task(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val description: String,
        val priority: TaskPriority = TaskPriority.MEDIUM,
        val estimatedDuration: Long, // in minutes
        val dependencies: List<String> = emptyList(),
        val assignedBy: String = "User",
        val createdAt: Long = System.currentTimeMillis(),
        var status: TaskStatus = TaskStatus.QUEUED,
        var progress: Int = 0, // 0-100
        var currentStep: String = "",
        var completedSteps: MutableList<String> = mutableListOf(),
        var remainingSteps: MutableList<String> = mutableListOf(),
        var startedAt: Long? = null,
        var completedAt: Long? = null,
        var lastUpdate: Long = System.currentTimeMillis(),
        var result: String? = null,
        var error: String? = null,
        var blockedReason: String? = null
    )
    
    data class TaskUpdate(
        val taskId: String,
        val status: TaskStatus,
        val progress: Int,
        val currentStep: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Task storage and state management
    private val tasks = ConcurrentHashMap<String, Task>()
    private val taskQueue = mutableListOf<Task>()
    private var currentTask: Task? = null
    
    // Real-time update streams
    private val _taskUpdates = MutableStateFlow<TaskUpdate?>(null)
    val taskUpdates: StateFlow<TaskUpdate?> = _taskUpdates
    
    private val _allTasks = MutableStateFlow<List<Task>>(emptyList())
    val allTasks: StateFlow<List<Task>> = _allTasks
    
    // Task execution scope
    private val taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 📋 Assign a new task like you would to a human worker
     */
    fun assignTask(
        title: String,
        description: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        estimatedDuration: Long = 60 // minutes
    ): String {
        val task = Task(
            title = title,
            description = description,
            priority = priority,
            estimatedDuration = estimatedDuration
        )
        
        // Break down complex tasks into steps
        task.remainingSteps.addAll(analyzeTaskSteps(description))
        
        tasks[task.id] = task
        addToQueue(task)
        
        sendUpdate(task, "📝 Task assigned: $title")
        
        // Auto-start if no current task
        if (currentTask == null) {
            startNextTask()
        }
        
        return task.id
    }
    
    /**
     * 🎯 Get real-time status updates like asking a human worker "How's it going?"
     */
    fun getTaskStatus(taskId: String): Task? {
        return tasks[taskId]?.also { task ->
            sendUpdate(task, getHumanLikeStatusMessage(task))
        }
    }
    
    /**
     * 📊 Get comprehensive task report like a human worker's daily summary
     */
    fun getDailyReport(): TaskReport {
        val now = System.currentTimeMillis()
        val todayStart = now - (24 * 60 * 60 * 1000) // 24 hours ago
        
        val todaysTasks = tasks.values.filter { it.createdAt > todayStart }
        
        return TaskReport(
            totalTasks = todaysTasks.size,
            completed = todaysTasks.count { it.status == TaskStatus.COMPLETED },
            inProgress = todaysTasks.count { it.status == TaskStatus.IN_PROGRESS },
            queued = todaysTasks.count { it.status == TaskStatus.QUEUED },
            blocked = todaysTasks.count { it.status == TaskStatus.BLOCKED },
            failed = todaysTasks.count { it.status == TaskStatus.FAILED },
            totalTimeSpent = calculateTotalTimeSpent(todaysTasks),
            productivity = calculateProductivityScore(todaysTasks),
            currentlyWorkingOn = currentTask?.title ?: "Available for new tasks"
        )
    }
    
    /**
     * ⏸️ Pause current task (like asking worker to take a break)
     */
    fun pauseCurrentTask(reason: String = "Paused by user") {
        currentTask?.let { task ->
            task.status = TaskStatus.PAUSED
            task.blockedReason = reason
            sendUpdate(task, "⏸️ Taking a break: $reason")
        }
    }
    
    /**
     * ▶️ Resume paused task
     */
    fun resumeTask(taskId: String) {
        tasks[taskId]?.let { task ->
            if (task.status == TaskStatus.PAUSED) {
                task.status = TaskStatus.IN_PROGRESS
                task.blockedReason = null
                currentTask = task
                continueTaskExecution(task)
                sendUpdate(task, "▶️ Back to work on: ${task.title}")
            }
        }
    }
    
    /**
     * ❌ Cancel task
     */
    fun cancelTask(taskId: String, reason: String = "Cancelled by user") {
        tasks[taskId]?.let { task ->
            task.status = TaskStatus.CANCELLED
            task.error = reason
            task.completedAt = System.currentTimeMillis()
            
            if (currentTask?.id == taskId) {
                currentTask = null
                startNextTask()
            }
            
            sendUpdate(task, "❌ Task cancelled: $reason")
        }
    }
    
    /**
     * 🔄 Request progress update (like checking in with worker)
     */
    fun requestProgressUpdate(taskId: String): TaskUpdate? {
        return tasks[taskId]?.let { task ->
            val progressMsg = when (task.status) {
                TaskStatus.IN_PROGRESS -> "🔄 Currently working on: ${task.currentStep} (${task.progress}% complete)"
                TaskStatus.QUEUED -> "📋 Waiting to start (Position ${getQueuePosition(task)} in queue)"
                TaskStatus.BLOCKED -> "⏸️ Temporarily blocked: ${task.blockedReason}"
                TaskStatus.COMPLETED -> "✅ Completed successfully: ${task.result}"
                TaskStatus.FAILED -> "❌ Failed: ${task.error}"
                TaskStatus.CANCELLED -> "❌ Cancelled"
                TaskStatus.PAUSED -> "⏸️ Paused: ${task.blockedReason}"
            }
            
            TaskUpdate(task.id, task.status, task.progress, task.currentStep, progressMsg)
        }
    }
    
    // Private helper methods
    
    private fun analyzeTaskSteps(description: String): List<String> {
        // AI-powered task breakdown (simplified)
        return when {
            description.contains("email", ignoreCase = true) -> listOf(
                "Analyze email requirements",
                "Draft email content", 
                "Review and optimize",
                "Send email",
                "Confirm delivery"
            )
            description.contains("research", ignoreCase = true) -> listOf(
                "Define research scope",
                "Gather initial sources",
                "Analyze information",
                "Synthesize findings",
                "Prepare summary"
            )
            description.contains("build", ignoreCase = true) -> listOf(
                "Analyze requirements",
                "Plan architecture",
                "Implement solution",
                "Test functionality",
                "Deploy and verify"
            )
            else -> listOf(
                "Analyze task requirements",
                "Plan execution strategy", 
                "Execute main task",
                "Verify completion",
                "Provide summary"
            )
        }
    }
    
    private fun addToQueue(task: Task) {
        // Priority-based insertion
        val insertIndex = taskQueue.indexOfFirst { it.priority.ordinal > task.priority.ordinal }
        if (insertIndex == -1) {
            taskQueue.add(task)
        } else {
            taskQueue.add(insertIndex, task)
        }
        updateAllTasksState()
    }
    
    private fun startNextTask() {
        if (taskQueue.isNotEmpty()) {
            val nextTask = taskQueue.removeFirst()
            currentTask = nextTask
            nextTask.status = TaskStatus.IN_PROGRESS
            nextTask.startedAt = System.currentTimeMillis()
            
            sendUpdate(nextTask, "🚀 Starting work on: ${nextTask.title}")
            executeTask(nextTask)
        }
    }
    
    private fun executeTask(task: Task) {
        taskScope.launch {
            try {
                for ((index, step) in task.remainingSteps.withIndex()) {
                    if (task.status != TaskStatus.IN_PROGRESS) break
                    
                    task.currentStep = step
                    task.progress = ((index + 1) * 100) / task.remainingSteps.size
                    
                    sendUpdate(task, "🔄 $step (${task.progress}% complete)")
                    
                    // Simulate step execution time
                    delay((task.estimatedDuration * 60 * 1000) / task.remainingSteps.size)
                    
                    task.completedSteps.add(step)
                }
                
                if (task.status == TaskStatus.IN_PROGRESS) {
                    task.status = TaskStatus.COMPLETED
                    task.progress = 100
                    task.completedAt = System.currentTimeMillis()
                    task.result = "Task completed successfully"
                    
                    sendUpdate(task, "✅ Task completed: ${task.title}")
                    
                    currentTask = null
                    startNextTask()
                }
                
            } catch (e: Exception) {
                task.status = TaskStatus.FAILED
                task.error = e.message ?: "Unknown error"
                sendUpdate(task, "❌ Task failed: ${e.message}")
                
                currentTask = null
                startNextTask()
            }
            
            updateAllTasksState()
        }
    }
    
    private fun continueTaskExecution(task: Task) {
        taskScope.launch {
            executeTask(task)
        }
    }
    
    private fun sendUpdate(task: Task, message: String) {
        task.lastUpdate = System.currentTimeMillis()
        val update = TaskUpdate(task.id, task.status, task.progress, task.currentStep, message)
        _taskUpdates.value = update
        updateAllTasksState()
    }
    
    private fun updateAllTasksState() {
        _allTasks.value = tasks.values.toList().sortedByDescending { it.lastUpdate }
    }
    
    private fun getHumanLikeStatusMessage(task: Task): String {
        return when (task.status) {
            TaskStatus.IN_PROGRESS -> {
                val timeSpent = (System.currentTimeMillis() - (task.startedAt ?: 0)) / 60000
                "Currently working on '${task.currentStep}'. Been at it for ${timeSpent} minutes, ${task.progress}% done!"
            }
            TaskStatus.QUEUED -> "Got it queued up, will start as soon as I finish my current task"
            TaskStatus.BLOCKED -> "Hit a roadblock: ${task.blockedReason}. Need your help to proceed"
            TaskStatus.COMPLETED -> "All done! ✅ Completed in ${calculateTaskDuration(task)} minutes"
            TaskStatus.FAILED -> "Sorry, ran into issues: ${task.error}"
            TaskStatus.CANCELLED -> "Understood, cancelled as requested"
            TaskStatus.PAUSED -> "Taking a break from this one: ${task.blockedReason}"
        }
    }
    
    private fun calculateTaskDuration(task: Task): Long {
        val start = task.startedAt ?: return 0
        val end = task.completedAt ?: System.currentTimeMillis()
        return (end - start) / 60000 // minutes
    }
    
    private fun getQueuePosition(task: Task): Int {
        return taskQueue.indexOf(task) + 1
    }
    
    private fun calculateTotalTimeSpent(tasks: List<Task>): Long {
        return tasks.sumOf { calculateTaskDuration(it) }
    }
    
    private fun calculateProductivityScore(tasks: List<Task>): Double {
        if (tasks.isEmpty()) return 0.0
        val completed = tasks.count { it.status == TaskStatus.COMPLETED }
        return (completed.toDouble() / tasks.size) * 100
    }
    
    data class TaskReport(
        val totalTasks: Int,
        val completed: Int,
        val inProgress: Int,
        val queued: Int,
        val blocked: Int,
        val failed: Int,
        val totalTimeSpent: Long, // minutes
        val productivity: Double, // percentage
        val currentlyWorkingOn: String
    )
}

/**
 * 🎯 Example Usage - Like Managing a Human Worker:
 * 
 * val taskManager = TaskManager()
 * 
 * // Assign tasks like to a human assistant
 * val emailTask = taskManager.assignTask(
 *     "Send follow-up emails to 50 prospects", 
 *     "Research each prospect, personalize message, send professional emails",
 *     TaskPriority.HIGH,
 *     120 // 2 hours estimated
 * )
 * 
 * // Check progress anytime
 * val status = taskManager.getTaskStatus(emailTask)
 * 
 * // Get daily productivity report  
 * val report = taskManager.getDailyReport()
 * 
 * // Pause if needed
 * taskManager.pauseCurrentTask("Need to prioritize urgent call")
 * 
 * // Resume later
 * taskManager.resumeTask(emailTask)
 */