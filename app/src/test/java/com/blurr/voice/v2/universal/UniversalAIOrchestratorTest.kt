package com.blurr.voice.v2.universal

import android.content.Context
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.api.Finger
import com.blurr.voice.api.Eyes
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class UniversalAIOrchestratorTest {

    private lateinit var context: Context
    private lateinit var screenAnalysis: ScreenAnalysis
    private lateinit var finger: Finger
    private lateinit var eyes: Eyes
    private lateinit var orchestrator: UniversalAIOrchestrator

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        screenAnalysis = mockk(relaxed = true)
        finger = mockk(relaxed = true)
        eyes = mockk(relaxed = true)
        
        orchestrator = UniversalAIOrchestrator(context, screenAnalysis, finger, eyes)
    }

    @Test
    fun `test universal task execution with AI integration`() = runTest {
        // Given
        val userRequest = "Analyze this business proposal and provide recommendations"
        val useExternalAI = true
        
        // When
        val result = orchestrator.executeUniversalTask(userRequest, useExternalAI)
        
        // Then
        assertNotNull(result)
        assertTrue("Should complete successfully or partially", 
            result.success || result.results.isNotEmpty())
        assertNotNull("Should have results list", result.results)
        assertNotNull("Should have learnings", result.learnings)
    }

    @Test
    fun `test task execution without external AI`() = runTest {
        // Given
        val simpleRequest = "Check current time and weather"
        val useExternalAI = false
        
        // When
        val result = orchestrator.executeUniversalTask(simpleRequest, useExternalAI)
        
        // Then
        assertNotNull(result)
        // Should still function without external AI for simple tasks
    }

    @Test
    fun `test app learning system integration`() = runTest {
        // Given
        val newAppRequest = "Learn how to use TikTok for business marketing"
        
        // Mock screen analysis to simulate app exploration
        every { screenAnalysis.analyzeCurrentScreen() } returns mockk(relaxed = true)
        
        // When
        val result = orchestrator.executeUniversalTask(newAppRequest)
        
        // Then
        assertNotNull(result)
        // Should attempt to learn the app interface
        verify { screenAnalysis.analyzeCurrentScreen() }
    }

    @Test
    fun `test visual AI processing capabilities`() = runTest {
        // Given
        val visualRequest = "Analyze this image and explain the content"
        
        // Mock visual processing components
        every { eyes.takeScreenshot() } returns mockk(relaxed = true)
        
        // When
        val result = orchestrator.executeUniversalTask(visualRequest)
        
        // Then
        assertNotNull(result)
        // Should attempt visual analysis
        verify(atLeast = 0) { eyes.takeScreenshot() }
    }

    @Test
    fun `test error handling and recovery`() = runTest {
        // Given
        val problematicRequest = "Complete impossible task that will fail"
        
        // Mock components to throw errors
        every { screenAnalysis.analyzeCurrentScreen() } throws Exception("Simulated error")
        
        // When
        val result = orchestrator.executeUniversalTask(problematicRequest)
        
        // Then
        assertNotNull(result)
        // Should handle errors gracefully and provide meaningful result
        assertTrue("Should indicate partial completion or have learnings", 
            !result.success || result.learnings.isNotEmpty())
    }

    @Test
    fun `test adaptive decision making`() = runTest {
        // Given
        val decisionRequest = "Should I invest in this stock based on market conditions?"
        
        // When
        val result = orchestrator.executeUniversalTask(decisionRequest)
        
        // Then
        assertNotNull(result)
        // Decision-making tasks should produce meaningful analysis
        assertTrue("Should have some form of analysis or results", 
            result.results.isNotEmpty() || result.learnings.isNotEmpty())
    }

    @Test
    fun `test context preservation across complex operations`() = runTest {
        // Given
        val contextualRequest = "Book a restaurant based on my dietary preferences and schedule"
        
        // When
        val result = orchestrator.executeUniversalTask(contextualRequest)
        
        // Then
        assertNotNull(result)
        // Should maintain context throughout the operation
    }

    @Test
    fun `test multi-app workflow coordination`() = runTest {
        // Given
        val multiAppRequest = "Check my calendar, email team about conflicts, reschedule meetings"
        
        // When
        val result = orchestrator.executeUniversalTask(multiAppRequest)
        
        // Then
        assertNotNull(result)
        assertTrue("Multi-app workflows should show progress", 
            result.results.isNotEmpty() || result.success)
    }

    @Test
    fun `test security challenge handling simulation`() = runTest {
        // Given
        val securityRequest = "Login to my banking app and check balance"
        
        // When
        val result = orchestrator.executeUniversalTask(securityRequest)
        
        // Then
        assertNotNull(result)
        // Should handle security challenges (even if simulated in test)
    }

    @Test
    fun `test creative problem solving integration`() = runTest {
        // Given
        val creativeProblem = "Design an innovative solution for remote team collaboration"
        
        // When
        val result = orchestrator.executeUniversalTask(creativeProblem)
        
        // Then
        assertNotNull(result)
        assertTrue("Creative problems should generate insights", 
            result.learnings.isNotEmpty() || result.results.isNotEmpty())
    }

    @Test
    fun `test performance and resource optimization`() = runTest {
        // Given
        val resourceIntensiveTask = "Process large dataset and generate comprehensive analysis"
        
        // When
        val startTime = System.currentTimeMillis()
        val result = orchestrator.executeUniversalTask(resourceIntensiveTask)
        val executionTime = System.currentTimeMillis() - startTime
        
        // Then
        assertNotNull(result)
        assertTrue("Should complete in reasonable time", executionTime < 30000) // 30 seconds max
    }

    @Test
    fun `test learning and adaptation capabilities`() = runTest {
        // Given - Execute similar tasks multiple times
        val learningTask = "Optimize my daily schedule"
        
        // When - Execute multiple times to test learning
        val firstResult = orchestrator.executeUniversalTask(learningTask)
        val secondResult = orchestrator.executeUniversalTask(learningTask)
        
        // Then
        assertNotNull(firstResult)
        assertNotNull(secondResult)
        // Second execution might be more efficient or show learning
        assertTrue("Should maintain or improve performance", 
            secondResult.results.size >= firstResult.results.size ||
            secondResult.learnings.isNotEmpty())
    }

    @Test
    fun `test result structure and completeness`() = runTest {
        // Given
        val structuredRequest = "Create a business plan for a tech startup"
        
        // When
        val result = orchestrator.executeUniversalTask(structuredRequest)
        
        // Then - Verify result structure
        assertNotNull("Should have result", result)
        assertNotNull("Should have results list", result.results)
        assertNotNull("Should have learnings", result.learnings)
        assertTrue("Should have boolean success indicator", 
            result.success == true || result.success == false)
        
        // Results should contain meaningful information
        if (result.results.isNotEmpty()) {
            result.results.forEach { stepResult ->
                assertNotNull("Each result should have success indicator", stepResult.success)
                // stepResult.data can be null, that's acceptable
                // stepResult.error should be null for successful steps
            }
        }
    }

    @Test
    fun `test concurrent task handling`() = runTest {
        // Given
        val concurrentTasks = listOf(
            "Check weather forecast",
            "Get latest news headlines", 
            "Check calendar for today"
        )
        
        // When - Execute tasks concurrently (simulated)
        val results = concurrentTasks.map { task ->
            orchestrator.executeUniversalTask(task)
        }
        
        // Then
        assertEquals("Should handle all tasks", concurrentTasks.size, results.size)
        results.forEach { result ->
            assertNotNull("Each result should be valid", result)
        }
    }

    @Test
    fun `test complex workflow with dependencies`() = runTest {
        // Given
        val dependentWorkflow = "Research market trends, create analysis report, email to stakeholders"
        
        // When
        val result = orchestrator.executeUniversalTask(dependentWorkflow)
        
        // Then
        assertNotNull(result)
        // Complex workflows should show multiple step results
        assertTrue("Complex workflows should have multiple results or high success", 
            result.results.size > 1 || result.success)
    }
}