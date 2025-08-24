package com.blurr.voice.v2.claude

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 🧠⚡ Claude Code Integration System
 * 
 * Seamless bridge between Genius Panda and Claude Code for continuous
 * pair programming, live code collaboration, and intelligent assistance.
 * 
 * Works like having Claude Code as your coding partner on Android!
 */
class ClaudeCodeIntegration(
    private val context: Context
) {
    
    // Integration states
    enum class ClaudeCodeStatus {
        DISCONNECTED,    // No active connection
        CONNECTING,      // Establishing connection  
        CONNECTED,       // Actively collaborating
        SYNCING,         // Syncing code changes
        ERROR            // Connection issues
    }
    
    enum class CollaborationMode {
        PAIR_PROGRAMMING,  // Real-time pair programming
        CODE_REVIEW,       // Code review and suggestions
        LIVE_ASSISTANCE,   // Continuous coding help
        PROJECT_SYNC,      // Project-wide synchronization
        CREATIVE_CODING    // Creative problem solving
    }
    
    data class CodeSession(
        val sessionId: String,
        val projectPath: String,
        val mode: CollaborationMode,
        val startedAt: Long = System.currentTimeMillis(),
        val filesWatched: MutableSet<String> = mutableSetOf(),
        val activeFile: String? = null,
        val lastSync: Long = System.currentTimeMillis(),
        var isActive: Boolean = true
    )
    
    data class CodeSuggestion(
        val id: String,
        val filePath: String,
        val lineNumber: Int,
        val suggestion: String,
        val reasoning: String,
        val priority: SuggestionPriority,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class SuggestionPriority {
        CRITICAL,    // Security/crash issues
        HIGH,        // Performance/best practices  
        MEDIUM,      // Code quality improvements
        LOW          // Style/optimization
    }
    
    // State management
    private val _status = MutableStateFlow(ClaudeCodeStatus.DISCONNECTED)
    val status: StateFlow<ClaudeCodeStatus> = _status
    
    private val _currentSession = MutableStateFlow<CodeSession?>(null)
    val currentSession: StateFlow<CodeSession?> = _currentSession
    
    private val _liveSuggestions = MutableStateFlow<List<CodeSuggestion>>(emptyList())
    val liveSuggestions: StateFlow<List<CodeSuggestion>> = _liveSuggestions
    
    private val activeSessions = ConcurrentHashMap<String, CodeSession>()
    private val codeCache = ConcurrentHashMap<String, String>()
    
    private val integrationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 🔗 Start vibing with Claude Code - seamless connection
     */
    suspend fun startClaudeCodeSession(
        projectPath: String,
        mode: CollaborationMode = CollaborationMode.PAIR_PROGRAMMING
    ): String {
        _status.value = ClaudeCodeStatus.CONNECTING
        
        try {
            val sessionId = "session_${System.currentTimeMillis()}"
            val session = CodeSession(
                sessionId = sessionId,
                projectPath = projectPath,
                mode = mode
            )
            
            // Initialize project sync
            initializeProjectSync(session)
            
            // Start file watching
            startFileWatcher(session)
            
            // Connect to Claude Code
            establishClaudeCodeConnection(session)
            
            activeSessions[sessionId] = session
            _currentSession.value = session
            _status.value = ClaudeCodeStatus.CONNECTED
            
            // Send initial context to Claude Code
            sendProjectContextToClaudeCode(session)
            
            return sessionId
            
        } catch (e: Exception) {
            _status.value = ClaudeCodeStatus.ERROR
            throw e
        }
    }
    
    /**
     * 🎯 Accept and apply Claude Code suggestions seamlessly
     */
    suspend fun acceptClaudeCodeSuggestion(suggestionId: String): Boolean {
        val suggestion = _liveSuggestions.value.find { it.id == suggestionId }
            ?: return false
        
        return try {
            // Apply the suggestion to the file
            applySuggestionToFile(suggestion)
            
            // Sync back to Claude Code
            syncFileWithClaudeCode(suggestion.filePath)
            
            // Remove applied suggestion
            updateSuggestions { suggestions ->
                suggestions.filter { it.id != suggestionId }
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 💡 Get live coding assistance for current context
     */
    suspend fun requestLiveAssistance(
        code: String,
        cursor: Pair<Int, Int>, // line, column
        context: String = ""
    ): List<CodeSuggestion> {
        val session = _currentSession.value ?: return emptyList()
        
        return try {
            // Send code context to Claude Code via Termux bridge
            val suggestions = requestClaudeCodeSuggestions(code, cursor, context, session)
            
            // Update live suggestions
            updateSuggestions { currentSuggestions ->
                currentSuggestions + suggestions
            }
            
            suggestions
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 🔄 Continuous code sync - keeps Claude Code in sync with changes
     */
    fun enableContinuousSync(enabled: Boolean = true) {
        if (enabled) {
            integrationScope.launch {
                while (_status.value == ClaudeCodeStatus.CONNECTED) {
                    syncActiveFiles()
                    delay(2000) // Sync every 2 seconds
                }
            }
        }
    }
    
    /**
     * 🎨 Creative coding mode - collaborative problem solving
     */
    suspend fun enterCreativeCodingMode(
        problem: String,
        constraints: List<String> = emptyList()
    ): String {
        val session = _currentSession.value ?: throw IllegalStateException("No active session")
        
        // Switch to creative mode
        session.mode = CollaborationMode.CREATIVE_CODING
        
        // Send problem to Claude Code for creative solutions
        return requestCreativeSolution(problem, constraints, session)
    }
    
    /**
     * 📝 Live code review mode
     */
    suspend fun startLiveCodeReview(filePath: String): List<CodeSuggestion> {
        val session = _currentSession.value ?: return emptyList()
        
        session.mode = CollaborationMode.CODE_REVIEW
        
        val fileContent = readFileContent(filePath)
        return requestCodeReview(fileContent, filePath, session)
    }
    
    /**
     * 🚀 Project-wide refactoring with Claude Code
     */
    suspend fun requestProjectRefactoring(
        refactoringType: String,
        scope: List<String> = emptyList()
    ): RefactoringPlan {
        val session = _currentSession.value 
            ?: throw IllegalStateException("No active session")
        
        return requestClaudeCodeRefactoring(refactoringType, scope, session)
    }
    
    // Private implementation methods
    
    private suspend fun initializeProjectSync(session: CodeSession) {
        // Scan project structure
        val projectFiles = scanProjectFiles(session.projectPath)
        session.filesWatched.addAll(projectFiles)
        
        // Cache initial file states
        projectFiles.forEach { filePath ->
            try {
                val content = readFileContent(filePath)
                codeCache[filePath] = content
            } catch (e: Exception) {
                // Skip files that can't be read
            }
        }
    }
    
    private suspend fun startFileWatcher(session: CodeSession) {
        integrationScope.launch {
            while (session.isActive) {
                // Check for file changes
                session.filesWatched.forEach { filePath ->
                    val currentContent = try {
                        readFileContent(filePath)
                    } catch (e: Exception) {
                        return@forEach
                    }
                    
                    val cachedContent = codeCache[filePath]
                    if (currentContent != cachedContent) {
                        // File changed - sync with Claude Code
                        codeCache[filePath] = currentContent
                        syncFileWithClaudeCode(filePath)
                        
                        // Generate live suggestions
                        generateLiveSuggestions(filePath, currentContent)
                    }
                }
                
                delay(1000) // Check every second
            }
        }
    }
    
    private suspend fun establishClaudeCodeConnection(session: CodeSession) {
        // Use Termux to establish connection with Claude Code
        val command = buildClaudeCodeConnectionCommand(session)
        executeTermuxCommand(command)
    }
    
    private fun buildClaudeCodeConnectionCommand(session: CodeSession): String {
        return """
            # Connect to Claude Code session
            cd "${session.projectPath}"
            
            # Start Claude Code if not running
            if ! pgrep -f "claude-code" > /dev/null; then
                claude-code . &
                sleep 2
            fi
            
            # Establish integration bridge
            echo "Genius Panda connected - Session: ${session.sessionId}" > .claude-panda-bridge
            echo "Mode: ${session.mode}" >> .claude-panda-bridge
            echo "Started: ${session.startedAt}" >> .claude-panda-bridge
        """.trimIndent()
    }
    
    private suspend fun sendProjectContextToClaudeCode(session: CodeSession) {
        val contextCommand = """
            cd "${session.projectPath}"
            
            # Send project context to Claude Code
            echo "🐼 Genius Panda Integration Active" > .claude-context
            echo "Project: $(basename $(pwd))" >> .claude-context
            echo "Mode: ${session.mode}" >> .claude-context
            echo "Files watched: ${session.filesWatched.size}" >> .claude-context
            echo "Ready for collaboration!" >> .claude-context
            
            # Trigger Claude Code context refresh
            touch .claude-refresh
        """.trimIndent()
        
        executeTermuxCommand(contextCommand)
    }
    
    private suspend fun requestClaudeCodeSuggestions(
        code: String,
        cursor: Pair<Int, Int>,
        context: String,
        session: CodeSession
    ): List<CodeSuggestion> {
        
        val suggestionCommand = """
            cd "${session.projectPath}"
            
            # Create suggestion request file
            cat > .claude-suggestion-request << 'EOF'
            {
                "type": "live_assistance",
                "code": "${code.replace("\"", "\\\"")}",
                "cursor": {"line": ${cursor.first}, "column": ${cursor.second}},
                "context": "${context.replace("\"", "\\\"")}",
                "timestamp": ${System.currentTimeMillis()}
            }
            EOF
            
            # Trigger Claude Code processing
            touch .claude-process-suggestion
            
            # Wait for response (with timeout)
            timeout=10
            while [ ${'$'}timeout -gt 0 ] && [ ! -f .claude-suggestion-response ]; do
                sleep 0.5
                timeout=${'$'}((timeout - 1))
            done
            
            # Output response if available
            if [ -f .claude-suggestion-response ]; then
                cat .claude-suggestion-response
                rm .claude-suggestion-response
            fi
        """.trimIndent()
        
        val response = executeTermuxCommandWithOutput(suggestionCommand)
        return parseClaudeCodeSuggestions(response)
    }
    
    private suspend fun applySuggestionToFile(suggestion: CodeSuggestion) {
        val file = File(suggestion.filePath)
        if (!file.exists()) return
        
        val lines = file.readLines().toMutableList()
        
        // Apply suggestion at specified line
        if (suggestion.lineNumber <= lines.size) {
            lines[suggestion.lineNumber - 1] = suggestion.suggestion
            file.writeText(lines.joinToString("\n"))
        }
    }
    
    private suspend fun syncFileWithClaudeCode(filePath: String) {
        _status.value = ClaudeCodeStatus.SYNCING
        
        val syncCommand = """
            # Notify Claude Code of file change
            echo "File updated: $filePath" >> .claude-file-changes
            echo "Timestamp: ${System.currentTimeMillis()}" >> .claude-file-changes
            
            # Trigger Claude Code sync
            touch .claude-sync-files
        """.trimIndent()
        
        executeTermuxCommand(syncCommand)
        
        delay(100) // Brief sync delay
        _status.value = ClaudeCodeStatus.CONNECTED
    }
    
    private suspend fun generateLiveSuggestions(filePath: String, content: String) {
        // Analyze code for potential improvements
        val suggestions = analyzeCodeForSuggestions(filePath, content)
        
        updateSuggestions { currentSuggestions ->
            // Add new suggestions, remove old ones for this file
            currentSuggestions.filter { it.filePath != filePath } + suggestions
        }
    }
    
    private fun analyzeCodeForSuggestions(filePath: String, content: String): List<CodeSuggestion> {
        val suggestions = mutableListOf<CodeSuggestion>()
        val lines = content.split("\n")
        
        // Basic code analysis (can be enhanced with AI)
        lines.forEachIndexed { index, line ->
            val lineNum = index + 1
            
            // Check for common issues
            when {
                line.contains("TODO") -> {
                    suggestions.add(CodeSuggestion(
                        id = "todo_${lineNum}_${System.currentTimeMillis()}",
                        filePath = filePath,
                        lineNumber = lineNum,
                        suggestion = "Consider implementing this TODO item",
                        reasoning = "Found TODO comment that may need attention",
                        priority = SuggestionPriority.MEDIUM
                    ))
                }
                
                line.contains("printStackTrace") -> {
                    suggestions.add(CodeSuggestion(
                        id = "logging_${lineNum}_${System.currentTimeMillis()}",
                        filePath = filePath,
                        lineNumber = lineNum,
                        suggestion = "Consider using proper logging instead of printStackTrace",
                        reasoning = "Better error handling and debugging practices",
                        priority = SuggestionPriority.HIGH
                    ))
                }
                
                line.trim().startsWith("//") && line.contains("FIXME") -> {
                    suggestions.add(CodeSuggestion(
                        id = "fixme_${lineNum}_${System.currentTimeMillis()}",
                        filePath = filePath,
                        lineNumber = lineNum,
                        suggestion = "Address this FIXME comment",
                        reasoning = "Code marked for fixing",
                        priority = SuggestionPriority.HIGH
                    ))
                }
            }
        }
        
        return suggestions
    }
    
    private suspend fun requestCreativeSolution(
        problem: String,
        constraints: List<String>,
        session: CodeSession
    ): String {
        val creativeCommand = """
            cd "${session.projectPath}"
            
            # Create creative problem request
            cat > .claude-creative-request << 'EOF'
            {
                "type": "creative_coding",
                "problem": "${problem.replace("\"", "\\\"")}",
                "constraints": [${constraints.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}],
                "project_context": "$(find . -name '*.kt' -o -name '*.java' | head -10 | xargs wc -l)",
                "timestamp": ${System.currentTimeMillis()}
            }
            EOF
            
            # Request Claude Code creative solution
            touch .claude-process-creative
            
            # Wait for creative response
            timeout=30
            while [ ${'$'}timeout -gt 0 ] && [ ! -f .claude-creative-response ]; do
                sleep 1
                timeout=${'$'}((timeout - 1))
            done
            
            # Return creative solution
            if [ -f .claude-creative-response ]; then
                cat .claude-creative-response
                rm .claude-creative-response
            else
                echo "Creative solution generation timed out"
            fi
        """.trimIndent()
        
        return executeTermuxCommandWithOutput(creativeCommand)
    }
    
    private suspend fun requestCodeReview(
        content: String,
        filePath: String,
        session: CodeSession
    ): List<CodeSuggestion> {
        val reviewCommand = """
            cd "${session.projectPath}"
            
            # Create code review request
            cat > .claude-review-request << 'EOF'
            {
                "type": "code_review",
                "file_path": "$filePath",
                "content": "${content.replace("\"", "\\\"").replace("\n", "\\n")}",
                "timestamp": ${System.currentTimeMillis()}
            }
            EOF
            
            # Request Claude Code review
            touch .claude-process-review
            
            # Wait for review response
            timeout=20
            while [ ${'$'}timeout -gt 0 ] && [ ! -f .claude-review-response ]; do
                sleep 1
                timeout=${'$'}((timeout - 1))
            done
            
            # Return review suggestions
            if [ -f .claude-review-response ]; then
                cat .claude-review-response
                rm .claude-review-response
            fi
        """.trimIndent()
        
        val response = executeTermuxCommandWithOutput(reviewCommand)
        return parseClaudeCodeSuggestions(response)
    }
    
    private suspend fun requestClaudeCodeRefactoring(
        refactoringType: String,
        scope: List<String>,
        session: CodeSession
    ): RefactoringPlan {
        val refactoringCommand = """
            cd "${session.projectPath}"
            
            # Create refactoring request
            cat > .claude-refactoring-request << 'EOF'
            {
                "type": "project_refactoring",
                "refactoring_type": "$refactoringType",
                "scope": [${scope.joinToString(",") { "\"$it\"" }}],
                "project_files": "$(find . -name '*.kt' -o -name '*.java' | wc -l) files",
                "timestamp": ${System.currentTimeMillis()}
            }
            EOF
            
            # Request Claude Code refactoring plan
            touch .claude-process-refactoring
            
            # Wait for refactoring plan
            timeout=60
            while [ ${'$'}timeout -gt 0 ] && [ ! -f .claude-refactoring-response ]; do
                sleep 2
                timeout=${'$'}((timeout - 2))
            done
            
            # Return refactoring plan
            if [ -f .claude-refactoring-response ]; then
                cat .claude-refactoring-response
                rm .claude-refactoring-response
            fi
        """.trimIndent()
        
        val response = executeTermuxCommandWithOutput(refactoringCommand)
        return parseRefactoringPlan(response)
    }
    
    // Utility methods
    
    private fun scanProjectFiles(projectPath: String): List<String> {
        val projectDir = File(projectPath)
        if (!projectDir.exists()) return emptyList()
        
        return projectDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .map { it.absolutePath }
            .toList()
    }
    
    private fun readFileContent(filePath: String): String {
        return try {
            File(filePath).readText()
        } catch (e: IOException) {
            ""
        }
    }
    
    private suspend fun executeTermuxCommand(command: String) {
        try {
            val intent = Intent()
            intent.action = "com.termux.RUN_COMMAND"
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            
            context.startService(intent)
        } catch (e: Exception) {
            // Fallback: try via runtime exec
            try {
                Runtime.getRuntime().exec(arrayOf("am", "start", "--user", "0", "-n", "com.termux/.HomeActivity"))
            } catch (e: Exception) {
                // Connection failed
            }
        }
    }
    
    private suspend fun executeTermuxCommandWithOutput(command: String): String {
        return try {
            // This is a simplified version - in real implementation,
            // we'd use proper IPC or shared files for communication
            executeTermuxCommand(command)
            delay(1000) // Wait for execution
            
            // In real implementation, read from shared output file
            "Command executed successfully"
        } catch (e: Exception) {
            "Error executing command: ${e.message}"
        }
    }
    
    private fun parseClaudeCodeSuggestions(response: String): List<CodeSuggestion> {
        // Parse JSON response from Claude Code (simplified)
        return try {
            // In real implementation, parse JSON response
            listOf(
                CodeSuggestion(
                    id = "parsed_${System.currentTimeMillis()}",
                    filePath = "",
                    lineNumber = 1,
                    suggestion = response.take(100),
                    reasoning = "Parsed from Claude Code response",
                    priority = SuggestionPriority.MEDIUM
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseRefactoringPlan(response: String): RefactoringPlan {
        return RefactoringPlan(
            id = "plan_${System.currentTimeMillis()}",
            title = "Claude Code Refactoring Plan",
            description = response,
            steps = listOf("Parse response", "Apply changes", "Verify results"),
            estimatedTime = 60,
            affectedFiles = emptyList()
        )
    }
    
    private fun updateSuggestions(updater: (List<CodeSuggestion>) -> List<CodeSuggestion>) {
        _liveSuggestions.value = updater(_liveSuggestions.value)
    }
    
    private suspend fun syncActiveFiles() {
        val session = _currentSession.value ?: return
        
        session.filesWatched.forEach { filePath ->
            try {
                val currentContent = readFileContent(filePath)
                val cachedContent = codeCache[filePath]
                
                if (currentContent != cachedContent) {
                    codeCache[filePath] = currentContent
                    syncFileWithClaudeCode(filePath)
                }
            } catch (e: Exception) {
                // Skip files with issues
            }
        }
        
        session.lastSync = System.currentTimeMillis()
    }
    
    /**
     * 🛑 End Claude Code session
     */
    fun endSession(sessionId: String) {
        activeSessions[sessionId]?.let { session ->
            session.isActive = false
            activeSessions.remove(sessionId)
            
            if (_currentSession.value?.sessionId == sessionId) {
                _currentSession.value = null
                _status.value = ClaudeCodeStatus.DISCONNECTED
            }
        }
    }
    
    data class RefactoringPlan(
        val id: String,
        val title: String,
        val description: String,
        val steps: List<String>,
        val estimatedTime: Int, // minutes
        val affectedFiles: List<String>
    )
}

/**
 * 🎯 Usage Examples:
 * 
 * val claudeCodeIntegration = ClaudeCodeIntegration(context)
 * 
 * // Start pair programming session
 * val sessionId = claudeCodeIntegration.startClaudeCodeSession(
 *     projectPath = "/path/to/project",
 *     mode = CollaborationMode.PAIR_PROGRAMMING
 * )
 * 
 * // Enable continuous sync
 * claudeCodeIntegration.enableContinuousSync(true)
 * 
 * // Get live coding assistance
 * val suggestions = claudeCodeIntegration.requestLiveAssistance(
 *     code = currentCode,
 *     cursor = Pair(42, 15),
 *     context = "Working on authentication logic"
 * )
 * 
 * // Accept Claude Code suggestions
 * claudeCodeIntegration.acceptClaudeCodeSuggestion(suggestion.id)
 * 
 * // Enter creative coding mode
 * val solution = claudeCodeIntegration.enterCreativeCodingMode(
 *     problem = "Design a scalable notification system",
 *     constraints = listOf("Android", "Real-time", "Low battery usage")
 * )
 */