package com.blurr.voice.v2

import android.content.Context
import com.blurr.voice.v2.memory.UniversalMemorySystem
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.api.Finger
import com.blurr.voice.api.Eyes
import com.blurr.voice.data.AppDatabase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SuperPandaAgentTest {

    private lateinit var context: Context
    private lateinit var screenAnalysis: ScreenAnalysis
    private lateinit var finger: Finger
    private lateinit var eyes: Eyes
    private lateinit var database: AppDatabase
    private lateinit var superPanda: SuperPandaWithMemory

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        screenAnalysis = mockk(relaxed = true)
        finger = mockk(relaxed = true)
        eyes = mockk(relaxed = true)
        database = mockk(relaxed = true)
        
        // Mock database singleton
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns database
        
        superPanda = SuperPandaWithMemory(context, screenAnalysis, finger, eyes)
    }

    @Test
    fun `test basic task handling with memory`() = runTest {
        // Given
        val userRequest = "Check my emails and summarize important ones"
        
        // When
        val result = superPanda.handleAnyTaskWithMemory(userRequest)
        
        // Then
        assertNotNull(result)
        assertTrue("Response should indicate completion", result.contains("✅") || result.contains("completed"))
    }

    @Test
    fun `test business task with memory enhancement`() = runTest {
        // Given
        val businessRequest = "Should I hire this freelancer for $50/hour?"
        
        // When
        val result = superPanda.handleAnyTaskWithMemory(businessRequest)
        
        // Then
        assertNotNull(result)
        assertTrue("Should indicate business decision analysis", result.contains("Business") || result.contains("decision"))
    }

    @Test
    fun `test creative task with pattern learning`() = runTest {
        // Given
        val creativeRequest = "Create viral Instagram content for my fashion brand"
        
        // When
        val result = superPanda.handleAnyTaskWithMemory(creativeRequest)
        
        // Then
        assertNotNull(result)
        assertTrue("Should indicate creative work completion", result.contains("🎨") || result.contains("Creative"))
    }

    @Test
    fun `test relationship memory integration`() = runTest {
        // Given
        val relationshipRequest = "Message John about the project status"
        
        // When
        val result = superPanda.handleAnyTaskWithMemory(relationshipRequest)
        
        // Then
        assertNotNull(result)
        assertTrue("Should indicate relationship context", result.contains("👥") || result.contains("context"))
    }

    @Test
    fun `test app interaction with memory`() = runTest {
        // Given
        val appName = "Instagram"
        val action = "Post new content"
        
        // When
        val result = superPanda.interactWithApp(appName, action, "Content posted successfully")
        
        // Then
        assertNotNull(result)
        assertTrue("Should confirm app interaction", result.contains("completed") || result.contains("interaction"))
    }

    @Test
    fun `test memory stats retrieval`() {
        // When
        val stats = superPanda.getMemoryStats()
        
        // Then
        assertNotNull(stats)
        assertTrue("Should have status", stats.containsKey("status"))
        assertTrue("Should have memory layers", stats.containsKey("memory_layers"))
        assertEquals("Should have 7 memory layers", 7, stats["memory_layers"])
    }

    @Test
    fun `test task classification accuracy`() = runTest {
        // Test business task classification
        val businessResult = superPanda.handleAnyTaskWithMemory("Hire a developer for $40/hour")
        assertTrue("Business task should be classified correctly", businessResult.contains("Business") || businessResult.contains("experiences"))
        
        // Test creative task classification  
        val creativeResult = superPanda.handleAnyTaskWithMemory("Create a marketing campaign")
        assertTrue("Creative task should be classified correctly", creativeResult.contains("🎨") || creativeResult.contains("Creative"))
        
        // Test routine task classification
        val routineResult = superPanda.handleAnyTaskWithMemory("Check daily emails")
        assertTrue("Routine task should be handled", routineResult.isNotEmpty())
    }
}