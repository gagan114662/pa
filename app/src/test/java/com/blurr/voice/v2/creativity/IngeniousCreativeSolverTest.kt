package com.blurr.voice.v2.creativity

import android.content.Context
import com.blurr.voice.v2.universal.UniversalAIOrchestrator
import com.blurr.voice.v2.memory.UniversalMemorySystem
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.api.Finger
import com.blurr.voice.api.Eyes
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class IngeniousCreativeSolverTest {

    private lateinit var context: Context
    private lateinit var aiOrchestrator: UniversalAIOrchestrator
    private lateinit var memorySystem: UniversalMemorySystem
    private lateinit var screenAnalysis: ScreenAnalysis
    private lateinit var finger: Finger
    private lateinit var eyes: Eyes
    private lateinit var creativeSolver: IngeniousCreativeSolver

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        aiOrchestrator = mockk(relaxed = true)
        memorySystem = mockk(relaxed = true)
        screenAnalysis = mockk(relaxed = true)
        finger = mockk(relaxed = true)
        eyes = mockk(relaxed = true)
        
        creativeSolver = IngeniousCreativeSolver(
            context, aiOrchestrator, memorySystem, screenAnalysis, finger, eyes
        )
    }

    @Test
    fun `test creative problem solving pipeline`() = runTest {
        // Given
        val problem = "Design an efficient customer onboarding system for mobile app"
        val constraints = listOf("budget_limited", "2_week_timeline")
        val resources = listOf("android_development", "ui_design_tools")
        
        // When
        val solution = creativeSolver.solveCreatively(problem, constraints, resources)
        
        // Then
        assertNotNull(solution)
        assertEquals("Problem should match input", problem, solution.problem)
        assertNotNull("Should have primary solution", solution.primarySolution)
        assertTrue("Should have alternative solutions", solution.alternativeSolutions.isNotEmpty())
        assertNotNull("Should have implementation plan", solution.implementationPlan)
        assertTrue("Should have confidence score", solution.confidence > 0.0 && solution.confidence <= 1.0)
        assertTrue("Should have novelty score", solution.novelty > 0.0 && solution.novelty <= 1.0)
        assertTrue("Should have creative insights", solution.creativeInsights.isNotEmpty())
    }

    @Test
    fun `test repository-to-production transformation`() = runTest {
        // Given
        val repoUrl = "https://github.com/example/ai-agents-research"
        val targetPlatform = "android"
        val outputFormat = "claude_code"
        
        // When
        val solution = creativeSolver.createProductionAgentFromRepo(repoUrl, targetPlatform, outputFormat)
        
        // Then
        assertNotNull(solution)
        assertTrue("Should contain repository analysis", solution.problem.contains(repoUrl))
        assertNotNull("Should have production-focused primary solution", solution.primarySolution)
        assertTrue("Should have high novelty for creative transformation", solution.novelty > 0.7)
        assertTrue("Should have reasonable confidence", solution.confidence > 0.5)
        
        // Verify implementation plan exists and is detailed
        assertNotNull("Should have implementation plan", solution.implementationPlan)
        assertTrue("Implementation plan should have phases", solution.implementationPlan.phases.isNotEmpty())
    }

    @Test
    fun `test solution creativity and feasibility balance`() = runTest {
        // Given
        val technicalProblem = "Optimize database performance for millions of concurrent users"
        val constraints = listOf("existing_infrastructure", "zero_downtime")
        
        // When
        val solution = creativeSolver.solveCreatively(technicalProblem, constraints)
        
        // Then
        assertNotNull(solution)
        
        // Primary solution should balance creativity with feasibility
        val primarySolution = solution.primarySolution
        assertTrue("Should have reasonable creativity", primarySolution.creativity > 0.4)
        assertTrue("Should have reasonable feasibility", primarySolution.feasibility > 0.4)
        assertTrue("Creativity + Feasibility should be high", 
            primarySolution.creativity + primarySolution.feasibility > 1.0)
        
        // Should have multiple approaches with different trade-offs
        assertTrue("Should have alternative solutions", solution.alternativeSolutions.size >= 2)
    }

    @Test
    fun `test creative insights generation`() = runTest {
        // Given
        val businessProblem = "Increase user engagement in social media app"
        val inspirationSources = listOf("gaming", "psychology", "behavioral_economics")
        
        // When
        val solution = creativeSolver.solveCreatively(
            problem = businessProblem,
            inspirationSources = inspirationSources
        )
        
        // Then
        assertNotNull(solution)
        assertTrue("Should generate creative insights", solution.creativeInsights.isNotEmpty())
        
        // Insights should be meaningful and specific
        val insights = solution.creativeInsights
        assertTrue("Insights should be detailed", insights.all { it.length > 20 })
        
        // Should reference inspiration sources in creative ways
        val insightText = insights.joinToString(" ").lowercase()
        assertTrue("Should incorporate inspiration themes", 
            inspirationSources.any { source -> 
                insightText.contains(source) || 
                insightText.contains("game") || 
                insightText.contains("behavior") 
            })
    }

    @Test
    fun `test implementation plan generation and structure`() = runTest {
        // Given
        val engineeringProblem = "Build real-time collaboration platform"
        val resources = listOf("cloud_infrastructure", "development_team", "3_month_timeline")
        
        // When
        val solution = creativeSolver.solveCreatively(engineeringProblem, resources = resources)
        
        // Then
        val implementationPlan = solution.implementationPlan
        assertNotNull("Should have implementation plan", implementationPlan)
        
        // Plan structure validation
        assertTrue("Should have multiple phases", implementationPlan.phases.size >= 3)
        assertTrue("Should have timeline", implementationPlan.timeline.isNotEmpty())
        assertTrue("Should identify resources needed", implementationPlan.resources.isNotEmpty())
        assertTrue("Should identify risks", implementationPlan.risks.isNotEmpty())
        assertTrue("Should have success metrics", implementationPlan.successMetrics.isNotEmpty())
        assertTrue("Should have rollback strategy", implementationPlan.rollbackStrategy.isNotEmpty())
        
        // Each phase should be well-defined
        implementationPlan.phases.forEach { phase ->
            assertTrue("Phase should have name", phase.name.isNotEmpty())
            assertTrue("Phase should have description", phase.description.isNotEmpty())
            assertTrue("Phase should have tasks", phase.tasks.isNotEmpty())
            assertTrue("Phase should have duration", phase.duration.isNotEmpty())
        }
    }

    @Test
    fun `test analogical reasoning capabilities`() = runTest {
        // Given
        val abstractProblem = "Design resilient distributed system architecture"
        val constraints = listOf("fault_tolerance", "scalability", "cost_efficiency")
        
        // When
        val solution = creativeSolver.solveCreatively(abstractProblem, constraints)
        
        // Then
        assertNotNull(solution)
        
        // Should use analogical thinking in solution description or insights
        val allText = (solution.primarySolution.description + " " + 
                      solution.creativeInsights.joinToString(" ")).lowercase()
        
        // Look for biological, natural, or engineering analogies
        val analogyKeywords = listOf(
            "immune", "ecosystem", "network", "organism", "colony", "swarm",
            "forest", "river", "brain", "cell", "evolution", "adaptation"
        )
        
        val hasAnalogy = analogyKeywords.any { keyword -> allText.contains(keyword) }
        assertTrue("Should incorporate analogical thinking", hasAnalogy || solution.novelty > 0.8)
    }

    @Test
    fun `test constraint handling and adaptation`() = runTest {
        // Given
        val constrainedProblem = "Develop AI solution for healthcare"
        val strictConstraints = listOf(
            "HIPAA_compliance", 
            "no_cloud_storage", 
            "99.9_uptime", 
            "budget_50k", 
            "30_day_delivery"
        )
        
        // When
        val solution = creativeSolver.solveCreatively(constrainedProblem, strictConstraints)
        
        // Then
        assertNotNull(solution)
        
        // Solution should acknowledge and work within constraints
        val solutionText = solution.primarySolution.description.lowercase()
        assertTrue("Should address constraints", 
            strictConstraints.any { constraint ->
                solutionText.contains(constraint.lowercase()) ||
                solutionText.contains("compliant") ||
                solutionText.contains("secure") ||
                solutionText.contains("local") ||
                solutionText.contains("budget")
            })
        
        // Should still maintain reasonable creativity despite constraints
        assertTrue("Should maintain creativity under constraints", solution.novelty > 0.3)
    }

    @Test
    fun `test resource optimization in solutions`() = runTest {
        // Given
        val resourceProblem = "Launch startup with minimal resources"
        val limitedResources = listOf("5k_budget", "solo_founder", "part_time_effort")
        
        // When
        val solution = creativeSolver.solveCreatively(
            problem = resourceProblem,
            resources = limitedResources
        )
        
        // Then
        assertNotNull(solution)
        
        // Solution should be resource-aware
        val implementationPlan = solution.implementationPlan
        assertTrue("Should consider resource limitations", 
            implementationPlan.resources.any { resource ->
                resource.contains("low-cost") || 
                resource.contains("free") || 
                resource.contains("minimal") ||
                resource.contains("efficient")
            } || solution.primarySolution.description.lowercase().contains("lean"))
    }

    @Test
    fun `test novelty scoring accuracy`() = runTest {
        // Test with conventional problem - should have lower novelty
        val conventionalProblem = "Create standard CRUD web application"
        val conventionalSolution = creativeSolver.solveCreatively(conventionalProblem)
        
        // Test with innovative problem - should have higher novelty
        val innovativeProblem = "Design quantum-inspired optimization algorithm for urban planning"
        val innovativeSolution = creativeSolver.solveCreatively(innovativeProblem)
        
        // Then
        assertTrue("Conventional problem should have moderate novelty", 
            conventionalSolution.novelty <= 0.8)
        assertTrue("Innovative problem should have high novelty", 
            innovativeSolution.novelty >= 0.6)
    }

    @Test
    fun `test creative solution components completeness`() = runTest {
        // Given
        val complexProblem = "Revolutionize online education experience"
        
        // When
        val solution = creativeSolver.solveCreatively(complexProblem)
        
        // Then - Verify all solution components are present and meaningful
        val primarySolution = solution.primarySolution
        
        assertNotNull("Should have solution name", primarySolution.name)
        assertTrue("Solution name should be descriptive", primarySolution.name.length > 5)
        
        assertNotNull("Should have solution description", primarySolution.description)
        assertTrue("Description should be detailed", primarySolution.description.length > 50)
        
        assertNotNull("Should have components list", primarySolution.components)
        assertTrue("Should have multiple components", primarySolution.components.size >= 3)
        
        assertNotNull("Should have implementation strategy", primarySolution.implementation)
        assertTrue("Should have implementation tools", primarySolution.implementation.tools.isNotEmpty())
        assertTrue("Should have implementation steps", primarySolution.implementation.steps.isNotEmpty())
        assertTrue("Should have timeline", primarySolution.implementation.timeline.isNotEmpty())
    }
}