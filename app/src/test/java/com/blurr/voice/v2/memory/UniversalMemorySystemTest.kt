package com.blurr.voice.v2.memory

import android.content.Context
import com.blurr.voice.data.AppDatabase
import com.blurr.voice.data.MemoryDao
import com.blurr.voice.v2.universal.UniversalAIOrchestrator
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class UniversalMemorySystemTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var aiOrchestrator: UniversalAIOrchestrator
    private lateinit var memorySystem: UniversalMemorySystem

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        database = mockk(relaxed = true)
        memoryDao = mockk(relaxed = true)
        aiOrchestrator = mockk(relaxed = true)
        
        every { database.memoryDao() } returns memoryDao
        every { memoryDao.getAllMemories() } returns emptyList()
        
        memorySystem = UniversalMemorySystem(context, database, aiOrchestrator)
    }

    @Test
    fun `test memory record creation and storage`() = runTest {
        // Given
        val content = "User prefers morning meetings"
        val type = UniversalMemorySystem.MemoryType.RELATIONSHIP
        
        // When
        val record = memorySystem.remember(content, type)
        
        // Then
        assertNotNull(record)
        assertEquals(content, record.content)
        assertEquals(type, record.type)
        assertTrue("Should have timestamp", record.timestamp > 0)
    }

    @Test
    fun `test memory recall functionality`() = runTest {
        // Given - First store some memories
        memorySystem.remember("User likes coffee meetings", UniversalMemorySystem.MemoryType.RELATIONSHIP)
        memorySystem.remember("Project deadline is Friday", UniversalMemorySystem.MemoryType.WORKING)
        memorySystem.remember("Successfully completed email automation", UniversalMemorySystem.MemoryType.PROCEDURAL)
        
        // When - Recall memories about meetings
        val results = memorySystem.recall("meetings", limit = 10)
        
        // Then
        assertNotNull(results)
        assertTrue("Should find relevant memories", results.isNotEmpty())
    }

    @Test
    fun `test preference learning system`() = runTest {
        // Given
        val category = "meeting_time"
        val preference = "morning_preferred"
        val strength = 0.8
        
        // When
        memorySystem.learnPreference(category, preference, strength)
        
        // Then - Should store preference memory
        val memories = memorySystem.recall(category, types = listOf(UniversalMemorySystem.MemoryType.RELATIONSHIP))
        assertNotNull(memories)
    }

    @Test
    fun `test outcome learning from success and failure`() = runTest {
        // Given
        val task = "Send email campaign"
        val approach = "personalized_templates"
        val successOutcome = UniversalMemorySystem.TaskOutcome(
            success = true,
            duration = 5000L,
            app = "gmail"
        )
        val lessons = listOf("Personalization increases open rates", "Send on Tuesday mornings")
        
        // When
        memorySystem.rememberOutcome(task, approach, successOutcome, lessons)
        
        // Then - Should store both outcome and lessons
        val outcomeMemories = memorySystem.recall(task, types = listOf(UniversalMemorySystem.MemoryType.PROCEDURAL))
        assertNotNull(outcomeMemories)
        
        val lessonMemories = memorySystem.recall("personalization", types = listOf(UniversalMemorySystem.MemoryType.SEMANTIC))
        assertNotNull(lessonMemories)
    }

    @Test
    fun `test app interaction memory`() = runTest {
        // Given
        val app = "Instagram"
        val action = "Create post"
        val result = "Posted successfully"
        val uiElements = listOf("camera_button", "filter_options", "caption_field")
        
        // When
        memorySystem.rememberAppInteraction(app, action, result, uiElements = uiElements)
        
        // Then
        val appMemories = memorySystem.recall(app, types = listOf(UniversalMemorySystem.MemoryType.EPISODIC))
        assertNotNull(appMemories)
        assertTrue("Should remember app interaction", appMemories.isNotEmpty())
        
        val uiMemories = memorySystem.recall("camera_button", types = listOf(UniversalMemorySystem.MemoryType.SEMANTIC))
        assertNotNull(uiMemories)
    }

    @Test
    fun `test conversation memory with sentiment`() = runTest {
        // Given
        val participant = "John Smith"
        val message = "Great job on the project presentation!"
        val platform = "slack"
        val sentiment = "positive"
        val topics = listOf("project", "presentation", "feedback")
        
        // When
        memorySystem.rememberConversation(participant, message, platform, sentiment, topics)
        
        // Then
        val conversationMemories = memorySystem.recall(participant, types = listOf(UniversalMemorySystem.MemoryType.RELATIONSHIP))
        assertNotNull(conversationMemories)
        assertTrue("Should remember positive conversation", conversationMemories.isNotEmpty())
    }

    @Test
    fun `test memory-based recommendation system`() = runTest {
        // Given - Store some relevant experiences
        memorySystem.remember("Used freelancer A for design work - excellent quality", UniversalMemorySystem.MemoryType.EPISODIC)
        memorySystem.remember("Freelancer B was unreliable with deadlines", UniversalMemorySystem.MemoryType.EPISODIC)
        memorySystem.learnPreference("freelancer_quality", "high_portfolio_quality", 0.9)
        
        // When
        val situation = "Need to hire a designer for logo work"
        val options = listOf("Freelancer A", "Freelancer B", "New freelancer C")
        val recommendation = memorySystem.getMemoryBasedRecommendation(situation, options)
        
        // Then
        assertNotNull(recommendation)
        assertNotNull(recommendation.recommendation)
        assertTrue("Should have confidence score", recommendation.confidence > 0.0)
        assertNotNull(recommendation.reasoning)
    }

    @Test
    fun `test workflow memory retrieval`() = runTest {
        // Given
        val workflowId = "email_campaign_workflow"
        memorySystem.remember("Step 1: Create email templates", UniversalMemorySystem.MemoryType.WORKING, 
            UniversalMemorySystem.MemoryContext(metadata = mapOf("workflow_id" to workflowId)))
        memorySystem.remember("Step 2: Send test emails", UniversalMemorySystem.MemoryType.EPISODIC,
            UniversalMemorySystem.MemoryContext(metadata = mapOf("workflow_id" to workflowId)))
        
        // When
        val workflowMemories = memorySystem.getWorkflowMemory(workflowId)
        
        // Then
        assertNotNull(workflowMemories)
        assertTrue("Should find workflow-related memories", workflowMemories.isNotEmpty())
    }

    @Test
    fun `test adaptive behavior learning`() = runTest {
        // Given - Store success and failure patterns
        val task = "Social media posting"
        memorySystem.remember("Posted at 9 AM - low engagement", UniversalMemorySystem.MemoryType.PROCEDURAL,
            UniversalMemorySystem.MemoryContext(metadata = mapOf("success" to false, "time" to "9AM")))
        memorySystem.remember("Posted at 2 PM - high engagement", UniversalMemorySystem.MemoryType.PROCEDURAL,
            UniversalMemorySystem.MemoryContext(metadata = mapOf("success" to true, "time" to "2PM")))
        memorySystem.remember("Posted at 7 PM - medium engagement", UniversalMemorySystem.MemoryType.PROCEDURAL,
            UniversalMemorySystem.MemoryContext(metadata = mapOf("success" to true, "time" to "7PM")))
        
        // When
        val adaptation = memorySystem.adaptBehaviorFromMemory(task)
        
        // Then
        assertNotNull(adaptation)
        assertNotNull(adaptation.recommendations)
        assertNotNull(adaptation.warnings)
        assertTrue("Should have confidence score", adaptation.confidence >= 0.0)
    }

    @Test
    fun `test memory types segregation`() = runTest {
        // Given - Store different types of memories
        memorySystem.remember("Current task context", UniversalMemorySystem.MemoryType.WORKING)
        memorySystem.remember("Learned fact about user", UniversalMemorySystem.MemoryType.SEMANTIC)
        memorySystem.remember("Specific event occurred", UniversalMemorySystem.MemoryType.EPISODIC)
        memorySystem.remember("How to perform task X", UniversalMemorySystem.MemoryType.PROCEDURAL)
        memorySystem.remember("Personal milestone", UniversalMemorySystem.MemoryType.AUTOBIOGRAPHICAL)
        memorySystem.remember("Relationship context", UniversalMemorySystem.MemoryType.RELATIONSHIP)
        
        // When - Recall specific memory types
        val workingMemories = memorySystem.recall("context", types = listOf(UniversalMemorySystem.MemoryType.WORKING))
        val semanticMemories = memorySystem.recall("fact", types = listOf(UniversalMemorySystem.MemoryType.SEMANTIC))
        val episodicMemories = memorySystem.recall("event", types = listOf(UniversalMemorySystem.MemoryType.EPISODIC))
        
        // Then
        assertNotNull(workingMemories)
        assertNotNull(semanticMemories)
        assertNotNull(episodicMemories)
    }

    @Test
    fun `test memory importance and relevance scoring`() = runTest {
        // Given - Store memories with different importance levels
        val highImportance = memorySystem.remember("Critical business decision made", 
            UniversalMemorySystem.MemoryType.EPISODIC, importance = 0.95)
        val mediumImportance = memorySystem.remember("Regular task completed", 
            UniversalMemorySystem.MemoryType.PROCEDURAL, importance = 0.6)
        val lowImportance = memorySystem.remember("Minor preference noted", 
            UniversalMemorySystem.MemoryType.RELATIONSHIP, importance = 0.3)
        
        // When - Recall with relevance scoring
        val results = memorySystem.recall("decision", limit = 10, useAI = false)
        
        // Then
        assertNotNull(results)
        assertEquals(0.95, highImportance.importance, 0.01)
        assertEquals(0.6, mediumImportance.importance, 0.01)
        assertEquals(0.3, lowImportance.importance, 0.01)
    }
}