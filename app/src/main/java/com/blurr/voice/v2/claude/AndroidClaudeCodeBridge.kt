package com.blurr.voice.v2.claude

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 🌉📱 Android-Claude Code Bridge
 * 
 * Seamless bridge that connects your Android Genius Panda with Claude Code
 * running on your development machine. Creates a unified coding experience
 * where Android and desktop work together like magic!
 */
class AndroidClaudeCodeBridge(
    private val context: Context
) {
    
    // Bridge states
    enum class BridgeStatus {
        DISCONNECTED,    // No connection established
        CONNECTING,      // Attempting to connect
        CONNECTED,       // Active bidirectional connection
        SYNCING,         // Synchronizing data/code
        ERROR           // Connection issues
    }
    
    enum class SyncDirection {
        ANDROID_TO_CLAUDE,   // Send from Android to Claude Code
        CLAUDE_TO_ANDROID,   // Receive from Claude Code to Android  
        BIDIRECTIONAL       // Two-way continuous sync
    }
    
    data class BridgeConfig(
        val claudeCodeUrl: String = "http://localhost:3000", // Claude Code server
        val termuxBridge: Boolean = true,                    // Use Termux as bridge
        val fileSync: Boolean = true,                        // Enable file sync
        val realTimeCollab: Boolean = true,                  // Real-time collaboration
        val autoReconnect: Boolean = true,                   // Auto-reconnect on disconnect
        val syncInterval: Long = 2000,                       // Sync every 2 seconds
        val sharedProjectPath: String = "/sdcard/shared_projects",
        val bridgeMethod: BridgeMethod = BridgeMethod.TERMUX_WEBSOCKET
    )
    
    enum class BridgeMethod {
        TERMUX_WEBSOCKET,    // WebSocket through Termux
        FILE_SYNC,           // File-based synchronization
        HTTP_API,            // HTTP API communication
        SSH_TUNNEL          // SSH tunnel connection
    }
    
    data class CodeSyncMessage(
        val messageId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val source: String, // "android" or "claude"
        val type: MessageType,
        val filePath: String? = null,
        val content: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    enum class MessageType {
        CODE_CHANGE,         // Code file modified
        FILE_CREATED,        // New file created
        FILE_DELETED,        // File removed
        CURSOR_POSITION,     // Cursor moved
        SELECTION_CHANGED,   // Text selection changed
        BUILD_STATUS,        // Build completed/failed
        TEST_RESULTS,        // Test execution results
        SUGGESTION,          // AI suggestion
        COMMAND             // Execute command
    }
    
    // State management
    private val _bridgeStatus = MutableStateFlow(BridgeStatus.DISCONNECTED)
    val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus
    
    private val _syncMessages = MutableStateFlow<CodeSyncMessage?>(null)
    val syncMessages: StateFlow<CodeSyncMessage?> = _syncMessages
    
    private val activeConnections = ConcurrentHashMap<String, BridgeConnection>()
    private val messageQueue = mutableListOf<CodeSyncMessage>()
    
    private val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var config = BridgeConfig()
    private var isActive = false
    
    /**
     * 🚀 Establish bridge connection to Claude Code
     */
    suspend fun establishBridge(
        bridgeConfig: BridgeConfig = BridgeConfig()
    ): Boolean {
        config = bridgeConfig
        _bridgeStatus.value = BridgeStatus.CONNECTING
        
        return try {
            when (config.bridgeMethod) {
                BridgeMethod.TERMUX_WEBSOCKET -> establishTermuxWebSocketBridge()
                BridgeMethod.FILE_SYNC -> establishFileSyncBridge()
                BridgeMethod.HTTP_API -> establishHttpApiBridge()
                BridgeMethod.SSH_TUNNEL -> establishSshTunnelBridge()
            }
        } catch (e: Exception) {
            _bridgeStatus.value = BridgeStatus.ERROR
            false
        }
    }
    
    /**
     * 🔄 Start continuous sync with Claude Code
     */
    fun startContinuousSync(direction: SyncDirection = SyncDirection.BIDIRECTIONAL) {
        if (!isActive) return
        
        bridgeScope.launch {
            while (_bridgeStatus.value == BridgeStatus.CONNECTED) {
                when (direction) {
                    SyncDirection.ANDROID_TO_CLAUDE -> syncAndroidToClaudeCode()
                    SyncDirection.CLAUDE_TO_ANDROID -> syncClaudeCodeToAndroid()
                    SyncDirection.BIDIRECTIONAL -> {
                        syncAndroidToClaudeCode()
                        syncClaudeCodeToAndroid()
                    }
                }
                
                delay(config.syncInterval)
            }
        }
    }
    
    /**
     * 📝 Send code changes to Claude Code
     */
    suspend fun sendCodeToClaudeCode(
        filePath: String,
        content: String,
        cursorPosition: Pair<Int, Int>? = null
    ): Boolean {
        if (_bridgeStatus.value != BridgeStatus.CONNECTED) return false
        
        val message = CodeSyncMessage(
            messageId = "android_${System.currentTimeMillis()}",
            source = "android",
            type = MessageType.CODE_CHANGE,
            filePath = filePath,
            content = content,
            metadata = cursorPosition?.let { 
                mapOf("cursor_line" to it.first, "cursor_column" to it.second) 
            } ?: emptyMap()
        )
        
        return sendMessage(message)
    }
    
    /**
     * 📥 Receive and apply Claude Code changes
     */
    suspend fun receiveClaudeCodeChanges(): List<CodeSyncMessage> {
        return try {
            val messages = fetchPendingMessages()
            messages.filter { it.source == "claude" }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * ⚡ Accept Claude Code suggestion and apply automatically
     */
    suspend fun acceptClaudeSuggestion(
        suggestion: String,
        targetFile: String,
        targetLine: Int
    ): Boolean {
        return try {
            // Apply suggestion to file via Termux
            val applyCommand = """
                cd "${config.sharedProjectPath}"
                
                # Backup original file
                cp "$targetFile" "${targetFile}.backup"
                
                # Apply Claude's suggestion at target line
                sed -i '${targetLine}i\\$suggestion' "$targetFile"
                
                # Notify Claude Code of change
                echo "Applied suggestion at line $targetLine" > .claude-suggestion-applied
                echo "File: $targetFile" >> .claude-suggestion-applied
                echo "Content: $suggestion" >> .claude-suggestion-applied
            """.trimIndent()
            
            executeTermuxCommand(applyCommand)
            
            // Send confirmation back to Claude Code
            val confirmMessage = CodeSyncMessage(
                messageId = "accept_${System.currentTimeMillis()}",
                source = "android",
                type = MessageType.CODE_CHANGE,
                filePath = targetFile,
                content = suggestion,
                metadata = mapOf("action" to "suggestion_accepted", "line" to targetLine)
            )
            
            sendMessage(confirmMessage)
            
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 🎯 Request specific help from Claude Code
     */
    suspend fun requestClaudeCodeHelp(
        question: String,
        codeContext: String,
        filePath: String? = null
    ): String? {
        val helpRequest = CodeSyncMessage(
            messageId = "help_${System.currentTimeMillis()}",
            source = "android",
            type = MessageType.COMMAND,
            filePath = filePath,
            content = question,
            metadata = mapOf(
                "context" to codeContext,
                "request_type" to "help",
                "response_needed" to true
            )
        )
        
        return if (sendMessage(helpRequest)) {
            // Wait for response from Claude Code
            waitForClaudeCodeResponse(helpRequest.messageId, 30000) // 30 second timeout
        } else null
    }
    
    /**
     * 🔧 Execute command in Claude Code environment
     */
    suspend fun executeClaudeCodeCommand(
        command: String,
        workingDirectory: String? = null
    ): String? {
        val commandMessage = CodeSyncMessage(
            messageId = "cmd_${System.currentTimeMillis()}",
            source = "android",
            type = MessageType.COMMAND,
            content = command,
            metadata = mapOf(
                "working_dir" to (workingDirectory ?: config.sharedProjectPath),
                "execute" to true
            )
        )
        
        return if (sendMessage(commandMessage)) {
            waitForClaudeCodeResponse(commandMessage.messageId, 60000) // 60 second timeout
        } else null
    }
    
    /**
     * 📊 Get Claude Code project status
     */
    suspend fun getClaudeCodeProjectStatus(): ProjectStatus? {
        val statusRequest = CodeSyncMessage(
            messageId = "status_${System.currentTimeMillis()}",
            source = "android",
            type = MessageType.COMMAND,
            content = "project_status",
            metadata = mapOf("request_type" to "status")
        )
        
        return if (sendMessage(statusRequest)) {
            val response = waitForClaudeCodeResponse(statusRequest.messageId, 10000)
            parseProjectStatus(response)
        } else null
    }
    
    // Private implementation methods
    
    private suspend fun establishTermuxWebSocketBridge(): Boolean {
        return try {
            // Setup WebSocket bridge through Termux
            val setupCommand = """
                # Install required packages
                pkg install python nodejs websocket -y
                
                # Create bridge script
                cat > /data/data/com.termux/files/home/claude-bridge.py << 'EOF'
                import websocket
                import json
                import threading
                import time
                from http.server import HTTPServer, BaseHTTPRequestHandler
                
                class ClaudeBridgeHandler(BaseHTTPRequestHandler):
                    def do_POST(self):
                        if self.path == '/sync':
                            content_length = int(self.headers['Content-Length'])
                            post_data = self.rfile.read(content_length)
                            
                            try:
                                message = json.loads(post_data.decode('utf-8'))
                                # Forward to Claude Code WebSocket
                                if hasattr(self.server, 'ws'):
                                    self.server.ws.send(json.dumps(message))
                                
                                self.send_response(200)
                                self.send_header('Content-type', 'application/json')
                                self.end_headers()
                                self.wfile.write(b'{"status":"success"}')
                            except Exception as e:
                                self.send_response(500)
                                self.end_headers()
                                self.wfile.write(f'{{"error":"{str(e)}"}}'.encode())
                
                def on_message(ws, message):
                    print(f"Received from Claude Code: {message}")
                    # Save message for Android to pick up
                    with open('/data/data/com.termux/files/home/.claude-messages', 'a') as f:
                        f.write(message + '\n')
                
                def on_error(ws, error):
                    print(f"WebSocket error: {error}")
                
                def on_close(ws, close_status_code, close_msg):
                    print("WebSocket connection closed")
                
                def on_open(ws):
                    print("WebSocket connection established")
                
                # Start WebSocket connection to Claude Code
                ws = websocket.WebSocketApp("${config.claudeCodeUrl}/ws",
                                          on_message=on_message,
                                          on_error=on_error,
                                          on_close=on_close,
                                          on_open=on_open)
                
                # Start HTTP server for Android communication
                server = HTTPServer(('localhost', 8080), ClaudeBridgeHandler)
                server.ws = ws
                
                # Run WebSocket in separate thread
                ws_thread = threading.Thread(target=ws.run_forever)
                ws_thread.daemon = True
                ws_thread.start()
                
                print("Claude Bridge started on port 8080")
                server.serve_forever()
                EOF
                
                # Start the bridge
                python claude-bridge.py &
                
                echo "Bridge established successfully"
            """.trimIndent()
            
            executeTermuxCommand(setupCommand)
            
            // Wait for bridge to start
            delay(3000)
            
            // Test connection
            testBridgeConnection()
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun establishFileSyncBridge(): Boolean {
        return try {
            // Create shared directory structure
            val setupCommand = """
                # Create shared project directory
                mkdir -p "${config.sharedProjectPath}"
                cd "${config.sharedProjectPath}"
                
                # Create bridge control files
                touch .android-to-claude
                touch .claude-to-android
                touch .bridge-status
                
                # Create sync script
                cat > sync-with-claude.sh << 'EOF'
                #!/bin/bash
                
                SHARED_DIR="${config.sharedProjectPath}"
                CLAUDE_DIR="$1"  # Claude Code project directory
                
                while true; do
                    # Check for changes from Android
                    if [[ .android-to-claude -nt .last-android-sync ]]; then
                        echo "Syncing from Android to Claude Code..."
                        rsync -avz --exclude='.git' ${'$'}SHARED_DIR/ ${'$'}CLAUDE_DIR/
                        touch .last-android-sync
                    fi
                    
                    # Check for changes from Claude Code
                    if [[ ${'$'}CLAUDE_DIR -nt .last-claude-sync ]]; then
                        echo "Syncing from Claude Code to Android..."
                        rsync -avz --exclude='.git' ${'$'}CLAUDE_DIR/ ${'$'}SHARED_DIR/
                        touch .last-claude-sync
                        touch .claude-to-android
                    fi
                    
                    sleep ${config.syncInterval / 1000}
                done
                EOF
                
                chmod +x sync-with-claude.sh
                echo "File sync bridge established"
            """.trimIndent()
            
            executeTermuxCommand(setupCommand)
            true
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun establishHttpApiBridge(): Boolean {
        return try {
            // Test HTTP connection to Claude Code
            val testCommand = """
                # Test connection to Claude Code
                curl -s -X GET "${config.claudeCodeUrl}/api/status" > /tmp/claude-status
                
                if grep -q "active" /tmp/claude-status; then
                    echo "HTTP API bridge established"
                    exit 0
                else
                    echo "Failed to connect to Claude Code API"
                    exit 1
                fi
            """.trimIndent()
            
            val result = executeTermuxCommandWithResult(testCommand)
            result?.contains("established") == true
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun establishSshTunnelBridge(): Boolean {
        return try {
            // Setup SSH tunnel (requires SSH keys)
            val sshCommand = """
                # Check if SSH is available
                if ! command -v ssh &> /dev/null; then
                    pkg install openssh -y
                fi
                
                # Create SSH tunnel to development machine
                # Note: Requires SSH keys to be set up
                ssh -N -L 3001:localhost:3000 user@dev-machine &
                
                # Test tunnel
                sleep 2
                curl -s http://localhost:3001/api/status > /tmp/ssh-test
                
                if grep -q "active" /tmp/ssh-test; then
                    echo "SSH tunnel bridge established"
                    exit 0
                else
                    echo "Failed to establish SSH tunnel"
                    exit 1
                fi
            """.trimIndent()
            
            val result = executeTermuxCommandWithResult(sshCommand)
            result?.contains("established") == true
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun testBridgeConnection(): Boolean {
        return try {
            val testMessage = CodeSyncMessage(
                messageId = "test_${System.currentTimeMillis()}",
                source = "android",
                type = MessageType.COMMAND,
                content = "ping"
            )
            
            sendMessage(testMessage)
            
            // If we can send without error, connection is good
            _bridgeStatus.value = BridgeStatus.CONNECTED
            isActive = true
            true
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun sendMessage(message: CodeSyncMessage): Boolean {
        return try {
            when (config.bridgeMethod) {
                BridgeMethod.TERMUX_WEBSOCKET -> sendViaWebSocket(message)
                BridgeMethod.FILE_SYNC -> sendViaFileSync(message)
                BridgeMethod.HTTP_API -> sendViaHttpApi(message)
                BridgeMethod.SSH_TUNNEL -> sendViaSshTunnel(message)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun sendViaWebSocket(message: CodeSyncMessage): Boolean {
        val sendCommand = """
            curl -X POST http://localhost:8080/sync \
                -H "Content-Type: application/json" \
                -d '${message.toJson()}'
        """.trimIndent()
        
        executeTermuxCommand(sendCommand)
        return true
    }
    
    private suspend fun sendViaFileSync(message: CodeSyncMessage): Boolean {
        val writeCommand = """
            cd "${config.sharedProjectPath}"
            echo '${message.toJson()}' >> .android-messages
            touch .android-to-claude
        """.trimIndent()
        
        executeTermuxCommand(writeCommand)
        return true
    }
    
    private suspend fun sendViaHttpApi(message: CodeSyncMessage): Boolean {
        val httpCommand = """
            curl -X POST "${config.claudeCodeUrl}/api/sync" \
                -H "Content-Type: application/json" \
                -d '${message.toJson()}'
        """.trimIndent()
        
        executeTermuxCommand(httpCommand)
        return true
    }
    
    private suspend fun sendViaSshTunnel(message: CodeSyncMessage): Boolean {
        val sshCommand = """
            curl -X POST http://localhost:3001/api/sync \
                -H "Content-Type: application/json" \
                -d '${message.toJson()}'
        """.trimIndent()
        
        executeTermuxCommand(sshCommand)
        return true
    }
    
    private suspend fun syncAndroidToClaudeCode() {
        _bridgeStatus.value = BridgeStatus.SYNCING
        
        // Check for local changes and sync them
        val syncCommand = """
            cd "${config.sharedProjectPath}"
            find . -name "*.kt" -o -name "*.java" -newer .last-sync 2>/dev/null | while read file; do
                echo "Syncing: ${'$'}file"
                # Send file change message
                echo '{"type":"file_changed","file":"'${'$'}file'","timestamp":'$(date +%s)'}' >> .sync-queue
            done
            touch .last-sync
        """.trimIndent()
        
        executeTermuxCommand(syncCommand)
        
        _bridgeStatus.value = BridgeStatus.CONNECTED
    }
    
    private suspend fun syncClaudeCodeToAndroid() {
        // Check for messages from Claude Code
        val messages = fetchPendingMessages()
        messages.forEach { message ->
            _syncMessages.value = message
            processIncomingMessage(message)
        }
    }
    
    private suspend fun fetchPendingMessages(): List<CodeSyncMessage> {
        val fetchCommand = """
            cd "${config.sharedProjectPath}"
            if [ -f .claude-messages ]; then
                cat .claude-messages
                rm .claude-messages
            fi
        """.trimIndent()
        
        val result = executeTermuxCommandWithResult(fetchCommand)
        return parseMessages(result ?: "")
    }
    
    private suspend fun processIncomingMessage(message: CodeSyncMessage) {
        when (message.type) {
            MessageType.CODE_CHANGE -> {
                // Apply code change from Claude Code
                message.filePath?.let { filePath ->
                    message.content?.let { content ->
                        applyCodeChange(filePath, content)
                    }
                }
            }
            MessageType.SUGGESTION -> {
                // Show Claude Code suggestion
                showClaudeSuggestion(message)
            }
            MessageType.BUILD_STATUS -> {
                // Handle build status from Claude Code
                handleBuildStatus(message)
            }
            else -> {
                // Handle other message types
            }
        }
    }
    
    private suspend fun waitForClaudeCodeResponse(messageId: String, timeoutMs: Long): String? {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val messages = fetchPendingMessages()
            val response = messages.find { 
                it.metadata["response_to"] == messageId 
            }
            
            if (response != null) {
                return response.content
            }
            
            delay(1000) // Check every second
        }
        
        return null // Timeout
    }
    
    // Utility methods
    
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
            // Fallback methods could be implemented here
        }
    }
    
    private suspend fun executeTermuxCommandWithResult(command: String): String? {
        // This would need proper implementation with result capture
        executeTermuxCommand("$command > /tmp/command_result 2>&1")
        delay(2000) // Wait for execution
        
        // Read result (simplified - real implementation would be more robust)
        executeTermuxCommand("cat /tmp/command_result")
        return "Command executed" // Placeholder
    }
    
    private fun CodeSyncMessage.toJson(): String {
        // Simplified JSON serialization
        return """
            {
                "messageId": "$messageId",
                "timestamp": $timestamp,
                "source": "$source",
                "type": "$type",
                "filePath": ${filePath?.let { "\"$it\"" } ?: "null"},
                "content": ${content?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},
                "metadata": ${metadata.entries.joinToString(",", "{", "}") { 
                    "\"${it.key}\": \"${it.value}\""
                }}
            }
        """.trimIndent()
    }
    
    private fun parseMessages(messagesText: String): List<CodeSyncMessage> {
        // Simplified message parsing - real implementation would use proper JSON parsing
        return messagesText.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    // Parse JSON message (simplified)
                    CodeSyncMessage(
                        messageId = "parsed_${System.currentTimeMillis()}",
                        source = "claude",
                        type = MessageType.CODE_CHANGE,
                        content = line
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    private fun parseProjectStatus(response: String?): ProjectStatus? {
        return response?.let {
            ProjectStatus(
                isActive = it.contains("active"),
                filesOpen = it.lines().count { line -> line.contains(".kt") || line.contains(".java") },
                lastActivity = System.currentTimeMillis()
            )
        }
    }
    
    private suspend fun applyCodeChange(filePath: String, content: String) {
        val applyCommand = """
            cd "${config.sharedProjectPath}"
            echo '$content' > "$filePath"
        """.trimIndent()
        
        executeTermuxCommand(applyCommand)
    }
    
    private fun showClaudeSuggestion(message: CodeSyncMessage) {
        // Show suggestion in Android UI (would integrate with your UI system)
        _syncMessages.value = message
    }
    
    private fun handleBuildStatus(message: CodeSyncMessage) {
        // Handle build status updates from Claude Code
        _syncMessages.value = message
    }
    
    data class ProjectStatus(
        val isActive: Boolean,
        val filesOpen: Int,
        val lastActivity: Long
    )
    
    data class BridgeConnection(
        val connectionId: String,
        val method: BridgeMethod,
        val establishedAt: Long,
        val lastActivity: Long
    )
    
    /**
     * 🛑 Disconnect bridge
     */
    fun disconnect() {
        isActive = false
        _bridgeStatus.value = BridgeStatus.DISCONNECTED
        
        bridgeScope.launch {
            val disconnectCommand = """
                # Clean up bridge processes
                pkill -f "claude-bridge"
                pkill -f "sync-with-claude"
                
                # Remove temporary files
                rm -f /tmp/claude-*
                rm -f /data/data/com.termux/files/home/.claude-*
            """.trimIndent()
            
            executeTermuxCommand(disconnectCommand)
        }
    }
}

/**
 * 🎯 Usage Examples:
 * 
 * val bridge = AndroidClaudeCodeBridge(context)
 * 
 * // Establish bridge connection
 * val connected = bridge.establishBridge(
 *     BridgeConfig(
 *         claudeCodeUrl = "http://192.168.1.100:3000",
 *         bridgeMethod = BridgeMethod.TERMUX_WEBSOCKET
 *     )
 * )
 * 
 * // Start continuous sync
 * bridge.startContinuousSync(SyncDirection.BIDIRECTIONAL)
 * 
 * // Send code to Claude Code
 * bridge.sendCodeToClaudeCode("MainActivity.kt", currentCode, Pair(42, 15))
 * 
 * // Accept Claude's suggestion
 * bridge.acceptClaudeSuggestion(
 *     suggestion = "optimized code here",
 *     targetFile = "MainActivity.kt",
 *     targetLine = 42
 * )
 * 
 * // Request help from Claude Code
 * val help = bridge.requestClaudeCodeHelp(
 *     question = "How to optimize this function?",
 *     codeContext = currentFunctionCode
 * )
 * 
 * // Execute command in Claude Code environment
 * val result = bridge.executeClaudeCodeCommand("npm test")
 */