package com.blurr.voice.v2.claude

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 🎵💻 Live Code Vibing System
 * 
 * Creates seamless, continuous collaboration between Genius Panda and Claude Code.
 * Like having Claude as your coding buddy who understands your flow and vibes with your style!
 */
class LiveCodeVibeSystem(
    private val context: Context,
    private val claudeCodeIntegration: ClaudeCodeIntegration
) {
    
    // Vibing states
    enum class VibeMode {
        CHILL,           // Minimal interruptions, subtle suggestions
        COLLABORATIVE,   // Active pair programming mode
        CREATIVE,        // High-energy creative problem solving
        FOCUSED,         // Deep work mode, emergency suggestions only
        LEARNING        // Teaching/learning mode with explanations
    }
    
    enum class CodeMood {
        FLOWING,         // Code is going well, in the zone
        STUCK,           // Hitting roadblocks, need help
        EXPLORING,       // Trying new approaches
        REFACTORING,     // Cleaning up existing code
        DEBUGGING       // Finding and fixing issues
    }
    
    data class CodingSession(
        val sessionId: String,
        val startTime: Long = System.currentTimeMillis(),
        var currentMood: CodeMood = CodeMood.FLOWING,
        var vibeMode: VibeMode = VibeMode.COLLABORATIVE,
        val codingPattern: CodingPattern = CodingPattern(),
        var isInTheZone: Boolean = false,
        val recentActivity: MutableList<CodingActivity> = mutableListOf(),
        var claudePersonality: ClaudePersonality = ClaudePersonality.BALANCED
    )
    
    data class CodingPattern(
        var avgTimeBetweenChanges: Long = 0,
        var avgLinesPerChange: Int = 0,
        var preferredSuggestionTiming: Long = 5000, // 5 seconds
        var interruptionTolerance: Float = 0.5f, // 0-1 scale
        var creativityPreference: Float = 0.7f,
        var explanationDepth: Float = 0.6f
    )
    
    data class CodingActivity(
        val timestamp: Long,
        val activityType: ActivityType,
        val filePath: String,
        val linesChanged: Int,
        val wasSuccessful: Boolean = true
    )
    
    enum class ActivityType {
        TYPING, DELETING, NAVIGATING, DEBUGGING, SEARCHING, BUILDING, TESTING
    }
    
    enum class ClaudePersonality {
        ENTHUSIASTIC,    // High energy, lots of encouragement
        BALANCED,        // Professional but friendly
        MINIMAL,         // Concise, only essential feedback
        MENTOR,          // Teaching-focused, detailed explanations
        CREATIVE        // Innovative, outside-the-box suggestions
    }
    
    data class VibeUpdate(
        val timestamp: Long = System.currentTimeMillis(),
        val mood: CodeMood,
        val suggestion: String?,
        val vibe: String, // Emotional/motivational message
        val action: VibeAction?
    )
    
    enum class VibeAction {
        SUGGEST_BREAK,
        OFFER_HELP,
        CELEBRATE_PROGRESS,
        ENCOURAGE_EXPERIMENTATION,
        RECOMMEND_REFACTOR
    }
    
    // State management
    private val _currentSession = MutableStateFlow<CodingSession?>(null)
    val currentSession: StateFlow<CodingSession?> = _currentSession
    
    private val _liveVibes = MutableStateFlow<VibeUpdate?>(null)
    val liveVibes: StateFlow<VibeUpdate?> = _liveVibes
    
    private val activeSessions = ConcurrentHashMap<String, CodingSession>()
    private val vibeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 🎯 Start vibing with your code - establishes the flow
     */
    fun startVibing(
        initialMood: CodeMood = CodeMood.FLOWING,
        vibeMode: VibeMode = VibeMode.COLLABORATIVE,
        personality: ClaudePersonality = ClaudePersonality.BALANCED
    ): String {
        val sessionId = "vibe_${System.currentTimeMillis()}"
        
        val session = CodingSession(
            sessionId = sessionId,
            currentMood = initialMood,
            vibeMode = vibeMode,
            claudePersonality = personality
        )
        
        activeSessions[sessionId] = session
        _currentSession.value = session
        
        // Start the vibing engine
        startVibeEngine(session)
        
        // Send initial vibe
        sendVibe(session, "🎵 Let's code together! I'm here to vibe with your flow and help when needed.")
        
        return sessionId
    }
    
    /**
     * 🎵 Update your coding mood - keeps Claude in sync with your state
     */
    fun updateMood(newMood: CodeMood, context: String = "") {
        val session = _currentSession.value ?: return
        
        val previousMood = session.currentMood
        session.currentMood = newMood
        
        // Adjust vibe based on mood change
        when (newMood) {
            CodeMood.STUCK -> {
                session.vibeMode = VibeMode.COLLABORATIVE
                sendVibe(session, "🤔 I sense you're hitting some roadblocks. Want me to take a look and brainstorm some solutions?", VibeAction.OFFER_HELP)
            }
            CodeMood.FLOWING -> {
                if (previousMood == CodeMood.STUCK) {
                    sendVibe(session, "🚀 Nice! Looks like you broke through that barrier. You're in the zone now!", VibeAction.CELEBRATE_PROGRESS)
                }
                session.isInTheZone = true
            }
            CodeMood.EXPLORING -> {
                session.vibeMode = VibeMode.CREATIVE
                sendVibe(session, "🔍 Love the exploration mode! I'll keep an eye out for creative opportunities and patterns.", VibeAction.ENCOURAGE_EXPERIMENTATION)
            }
            CodeMood.DEBUGGING -> {
                sendVibe(session, "🐛 Debug mode activated. I'll watch for potential issues and suggest fixes as you go.")
            }
            CodeMood.REFACTORING -> {
                sendVibe(session, "🧹 Clean-up time! I'll help spot opportunities to simplify and improve the code structure.", VibeAction.RECOMMEND_REFACTOR)
            }
        }
        
        // Record mood change activity
        session.recentActivity.add(CodingActivity(
            timestamp = System.currentTimeMillis(),
            activityType = ActivityType.NAVIGATING,
            filePath = "mood_change",
            linesChanged = 0
        ))
    }
    
    /**
     * 🎛️ Adjust Claude's personality to match your vibe
     */
    fun adjustClaudePersonality(personality: ClaudePersonality) {
        val session = _currentSession.value ?: return
        session.claudePersonality = personality
        
        val personalityMessage = when (personality) {
            ClaudePersonality.ENTHUSIASTIC -> "🎉 Awesome! I'm pumped to code with you! Let's build something amazing!"
            ClaudePersonality.BALANCED -> "👍 Perfect. I'm here to collaborate and help however you need."
            ClaudePersonality.MINIMAL -> "✓ Got it. I'll keep things concise and focus on essentials."
            ClaudePersonality.MENTOR -> "📚 Great! I'll provide detailed explanations and help you learn as we go."
            ClaudePersonality.CREATIVE -> "🌟 Love it! Let's think outside the box and explore innovative solutions."
        }
        
        sendVibe(session, personalityMessage)
    }
    
    /**
     * ⚡ Record coding activity to understand your flow
     */
    fun recordActivity(
        activityType: ActivityType,
        filePath: String,
        linesChanged: Int = 0,
        successful: Boolean = true
    ) {
        val session = _currentSession.value ?: return
        
        val activity = CodingActivity(
            timestamp = System.currentTimeMillis(),
            activityType = activityType,
            filePath = filePath,
            linesChanged = linesChanged,
            wasSuccessful = successful
        )
        
        session.recentActivity.add(activity)
        
        // Keep only recent activities (last 50)
        if (session.recentActivity.size > 50) {
            session.recentActivity.removeAt(0)
        }
        
        // Update coding patterns
        updateCodingPatterns(session)
        
        // Generate contextual vibes based on activity
        generateContextualVibe(session, activity)
    }
    
    /**
     * 🎯 Accept Claude's suggestion and keep the flow going
     */
    suspend fun acceptSuggestionAndContinue(suggestionId: String) {
        val applied = claudeCodeIntegration.acceptClaudeCodeSuggestion(suggestionId)
        
        if (applied) {
            val session = _currentSession.value ?: return
            sendVibe(session, "✅ Nice! Applied that suggestion. The flow continues!", VibeAction.CELEBRATE_PROGRESS)
            
            // Learn from acceptance to improve future suggestions
            adjustSuggestionTiming(session, accepted = true)
        }
    }
    
    /**
     * 🎵 Get live coding vibes and suggestions
     */
    suspend fun requestLiveVibe(currentCode: String, cursor: Pair<Int, Int>): VibeUpdate? {
        val session = _currentSession.value ?: return null
        
        // Generate contextual suggestion based on current state
        val suggestions = claudeCodeIntegration.requestLiveAssistance(
            code = currentCode,
            cursor = cursor,
            context = buildVibeContext(session)
        )
        
        if (suggestions.isNotEmpty() && shouldShowSuggestion(session)) {
            val suggestion = suggestions.first()
            val vibeMessage = createVibeMessage(session, suggestion.reasoning)
            
            return VibeUpdate(
                mood = session.currentMood,
                suggestion = suggestion.suggestion,
                vibe = vibeMessage,
                action = determineVibeAction(session, suggestion)
            )
        }
        
        return null
    }
    
    /**
     * 🏆 Celebrate coding achievements
     */
    fun celebrateAchievement(achievement: String, impact: String = "") {
        val session = _currentSession.value ?: return
        
        val celebrationMessage = when (session.claudePersonality) {
            ClaudePersonality.ENTHUSIASTIC -> "🎉🚀 AMAZING! $achievement! $impact You're crushing it!"
            ClaudePersonality.BALANCED -> "👏 Excellent work on $achievement. $impact"
            ClaudePersonality.MINIMAL -> "✅ $achievement"
            ClaudePersonality.MENTOR -> "🎓 Great job! $achievement. $impact This shows good software engineering practices."
            ClaudePersonality.CREATIVE -> "✨ Beautiful! $achievement with style! $impact"
        }
        
        sendVibe(session, celebrationMessage, VibeAction.CELEBRATE_PROGRESS)
    }
    
    /**
     * 🛑 Suggest taking a break when needed
     */
    private fun checkForBreakSuggestion(session: CodingSession) {
        val sessionDuration = System.currentTimeMillis() - session.startTime
        val recentErrors = session.recentActivity.count { 
            !it.wasSuccessful && (System.currentTimeMillis() - it.timestamp) < 300000 // 5 minutes
        }
        
        // Suggest break if coding for over 90 minutes or multiple recent errors
        if (sessionDuration > 5400000 || recentErrors >= 3) { // 90 minutes
            val breakMessage = when (session.claudePersonality) {
                ClaudePersonality.ENTHUSIASTIC -> "🌟 You've been coding hard! How about a quick break? Your brain (and the code) will thank you!"
                ClaudePersonality.BALANCED -> "Consider taking a short break. You've been focused for a while."
                ClaudePersonality.MINIMAL -> "Break recommended - 90+ min session"
                ClaudePersonality.MENTOR -> "🧠 Taking regular breaks improves code quality and prevents bugs. Time for a refresh?"
                ClaudePersonality.CREATIVE -> "🚶‍♂️ Time to step away and let the subconscious work its magic!"
            }
            
            sendVibe(session, breakMessage, VibeAction.SUGGEST_BREAK)
        }
    }
    
    // Private helper methods
    
    private fun startVibeEngine(session: CodingSession) {
        vibeScope.launch {
            while (session.sessionId in activeSessions) {
                // Check for break suggestions
                checkForBreakSuggestion(session)
                
                // Analyze coding patterns
                analyzeCodingFlow(session)
                
                // Generate proactive vibes if needed
                generateProactiveVibes(session)
                
                delay(30000) // Check every 30 seconds
            }
        }
    }
    
    private fun updateCodingPatterns(session: CodingSession) {
        val recentActivities = session.recentActivity.takeLast(10)
        
        if (recentActivities.size >= 2) {
            // Calculate average time between changes
            val timeDiffs = recentActivities.zipWithNext { a, b -> b.timestamp - a.timestamp }
            session.codingPattern.avgTimeBetweenChanges = timeDiffs.average().toLong()
            
            // Calculate average lines per change
            val linesChanged = recentActivities.map { it.linesChanged }.filter { it > 0 }
            if (linesChanged.isNotEmpty()) {
                session.codingPattern.avgLinesPerChange = linesChanged.average().toInt()
            }
            
            // Adjust suggestion timing based on coding speed
            session.codingPattern.preferredSuggestionTiming = when {
                session.codingPattern.avgTimeBetweenChanges < 3000 -> 8000 // Fast coder, wait longer
                session.codingPattern.avgTimeBetweenChanges > 15000 -> 3000 // Slow coder, help sooner
                else -> 5000 // Default timing
            }
        }
    }
    
    private fun generateContextualVibe(session: CodingSession, activity: CodingActivity) {
        // Generate vibes based on current activity and patterns
        when (activity.activityType) {
            ActivityType.BUILDING -> {
                if (activity.wasSuccessful) {
                    sendVibe(session, "🔨 Build successful! Code is solid.")
                } else {
                    sendVibe(session, "🔧 Build issues detected. I can help troubleshoot!", VibeAction.OFFER_HELP)
                }
            }
            ActivityType.TESTING -> {
                if (activity.wasSuccessful) {
                    sendVibe(session, "✅ Tests passing! Good coverage.", VibeAction.CELEBRATE_PROGRESS)
                } else {
                    session.currentMood = CodeMood.DEBUGGING
                    sendVibe(session, "🧪 Test failures - let's debug this together!", VibeAction.OFFER_HELP)
                }
            }
            ActivityType.TYPING -> {
                if (session.isInTheZone && session.vibeMode != VibeMode.FOCUSED) {
                    // Minimal interruption when in the zone
                    if (System.currentTimeMillis() - (session.recentActivity.lastOrNull()?.timestamp ?: 0) > 300000) { // 5 minutes
                        sendVibe(session, "🔥 You're in the zone! Keep the flow going.")
                    }
                }
            }
            else -> { /* Other activities don't need immediate vibe response */ }
        }
    }
    
    private fun buildVibeContext(session: CodingSession): String {
        return """
            Current mood: ${session.currentMood}
            Vibe mode: ${session.vibeMode}
            Personality: ${session.claudePersonality}
            In the zone: ${session.isInTheZone}
            Session duration: ${(System.currentTimeMillis() - session.startTime) / 60000} minutes
            Recent activities: ${session.recentActivity.takeLast(5).joinToString { it.activityType.name }}
        """.trimIndent()
    }
    
    private fun shouldShowSuggestion(session: CodingSession): Boolean {
        val lastSuggestionTime = session.recentActivity.lastOrNull()?.timestamp ?: 0
        val timeSinceLastSuggestion = System.currentTimeMillis() - lastSuggestionTime
        
        return when (session.vibeMode) {
            VibeMode.FOCUSED -> timeSinceLastSuggestion > 300000 // Only critical suggestions
            VibeMode.CHILL -> timeSinceLastSuggestion > session.codingPattern.preferredSuggestionTiming * 2
            VibeMode.COLLABORATIVE -> timeSinceLastSuggestion > session.codingPattern.preferredSuggestionTiming
            VibeMode.CREATIVE -> timeSinceLastSuggestion > session.codingPattern.preferredSuggestionTiming / 2
            VibeMode.LEARNING -> true // Always show suggestions in learning mode
        }
    }
    
    private fun createVibeMessage(session: CodingSession, reasoning: String): String {
        return when (session.claudePersonality) {
            ClaudePersonality.ENTHUSIASTIC -> "🌟 $reasoning Let's make this even better!"
            ClaudePersonality.BALANCED -> reasoning
            ClaudePersonality.MINIMAL -> reasoning.split(".").first() // Just first sentence
            ClaudePersonality.MENTOR -> "💡 Teaching moment: $reasoning"
            ClaudePersonality.CREATIVE -> "✨ Creative insight: $reasoning"
        }
    }
    
    private fun determineVibeAction(session: CodingSession, suggestion: ClaudeCodeIntegration.CodeSuggestion): VibeAction? {
        return when (suggestion.priority) {
            ClaudeCodeIntegration.SuggestionPriority.CRITICAL -> VibeAction.OFFER_HELP
            ClaudeCodeIntegration.SuggestionPriority.HIGH -> VibeAction.RECOMMEND_REFACTOR
            ClaudeCodeIntegration.SuggestionPriority.MEDIUM -> VibeAction.ENCOURAGE_EXPERIMENTATION
            ClaudeCodeIntegration.SuggestionPriority.LOW -> null
        }
    }
    
    private fun sendVibe(session: CodingSession, message: String, action: VibeAction? = null) {
        val vibe = VibeUpdate(
            mood = session.currentMood,
            suggestion = null,
            vibe = message,
            action = action
        )
        
        _liveVibes.value = vibe
    }
    
    private fun adjustSuggestionTiming(session: CodingSession, accepted: Boolean) {
        if (accepted) {
            // User likes suggestions, can show them a bit more frequently
            session.codingPattern.preferredSuggestionTiming = 
                (session.codingPattern.preferredSuggestionTiming * 0.9).toLong()
        } else {
            // User didn't accept, wait longer between suggestions
            session.codingPattern.preferredSuggestionTiming = 
                (session.codingPattern.preferredSuggestionTiming * 1.2).toLong()
        }
        
        // Keep within reasonable bounds
        session.codingPattern.preferredSuggestionTiming = 
            session.codingPattern.preferredSuggestionTiming.coerceIn(2000, 30000)
    }
    
    private fun analyzeCodingFlow(session: CodingSession) {
        val recentActivities = session.recentActivity.takeLast(10)
        
        // Detect if user is in the zone (consistent, frequent activity)
        val recentTimestamps = recentActivities.map { it.timestamp }
        if (recentTimestamps.size >= 5) {
            val intervals = recentTimestamps.zipWithNext { a, b -> b - a }
            val avgInterval = intervals.average()
            val consistency = intervals.map { kotlin.math.abs(it - avgInterval) }.average()
            
            session.isInTheZone = avgInterval < 10000 && consistency < 3000 // Consistent fast activity
        }
    }
    
    private fun generateProactiveVibes(session: CodingSession) {
        val recentSuccessRate = session.recentActivity.takeLast(5).count { it.wasSuccessful } / 5.0
        
        if (recentSuccessRate < 0.6 && session.currentMood != CodeMood.STUCK) {
            // Multiple recent failures, offer help
            sendVibe(session, "🤝 Noticed some recent challenges. Want to pair program through this section?", VibeAction.OFFER_HELP)
        } else if (recentSuccessRate == 1.0 && session.recentActivity.size >= 5) {
            // Everything going well, celebrate
            sendVibe(session, "🚀 You're on fire! Everything's working smoothly.", VibeAction.CELEBRATE_PROGRESS)
        }
    }
    
    /**
     * 🛑 End the vibing session
     */
    fun stopVibing(sessionId: String) {
        activeSessions.remove(sessionId)
        if (_currentSession.value?.sessionId == sessionId) {
            _currentSession.value = null
        }
    }
}

/**
 * 🎵 Usage Examples:
 * 
 * val vibeSystem = LiveCodeVibeSystem(context, claudeCodeIntegration)
 * 
 * // Start vibing with your preferred style
 * val sessionId = vibeSystem.startVibing(
 *     initialMood = CodeMood.EXPLORING,
 *     vibeMode = VibeMode.CREATIVE,
 *     personality = ClaudePersonality.ENTHUSIASTIC
 * )
 * 
 * // Update mood as you code
 * vibeSystem.updateMood(CodeMood.STUCK, "Having trouble with async logic")
 * 
 * // Record activities to help Claude understand your flow
 * vibeSystem.recordActivity(ActivityType.BUILDING, "MainActivity.kt", 0, false)
 * 
 * // Get live vibes while coding
 * val vibe = vibeSystem.requestLiveVibe(currentCode, cursorPosition)
 * 
 * // Celebrate achievements
 * vibeSystem.celebrateAchievement("Feature completed", "Users can now authenticate")
 * 
 * // Accept suggestions and keep flowing
 * vibeSystem.acceptSuggestionAndContinue(suggestion.id)
 */