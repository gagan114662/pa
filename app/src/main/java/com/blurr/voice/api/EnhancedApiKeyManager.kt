package com.blurr.voice.api

import android.util.Log
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import com.blurr.voice.BuildConfig

class EnhancedApiKeyManager {
    
    companion object {
        private const val TAG = "EnhancedApiKeyManager"
        private const val RATE_LIMIT_WINDOW = 60000L // 1 minute
        private const val MAX_REQUESTS_PER_KEY = 60 // per minute
        private const val COOLDOWN_PERIOD = 30000L // 30 seconds
        private const val HEALTH_CHECK_INTERVAL = 300000L // 5 minutes
    }
    
    private data class ApiKeyStatus(
        val key: String,
        val requestCount: AtomicInteger = AtomicInteger(0),
        val windowStart: AtomicLong = AtomicLong(System.currentTimeMillis()),
        val lastUsed: AtomicLong = AtomicLong(0),
        val consecutiveErrors: AtomicInteger = AtomicInteger(0),
        val isHealthy: Boolean = true,
        val cooldownUntil: AtomicLong = AtomicLong(0)
    )
    
    private val apiKeys: List<ApiKeyStatus> = if (BuildConfig.GEMINI_API_KEYS.isNotEmpty()) {
        BuildConfig.GEMINI_API_KEYS.split(",").map { ApiKeyStatus(it.trim()) }
    } else {
        emptyList()
    }
    
    private val keyPerformance = ConcurrentHashMap<String, KeyPerformanceMetrics>()
    private val currentIndex = AtomicInteger(0)
    
    init {
        if (apiKeys.isEmpty()) {
            Log.e(TAG, "No API keys configured!")
        } else {
            Log.d(TAG, "Initialized with ${apiKeys.size} API keys")
        }
    }
    
    suspend fun getNextAvailableKey(): String? {
        if (apiKeys.isEmpty()) {
            throw IllegalStateException("No API keys configured")
        }
        
        // Try to find a healthy, available key
        repeat(apiKeys.size * 2) { // Try twice through all keys
            val key = selectBestKey()
            if (key != null) {
                return key
            }
            delay(100) // Small delay before retry
        }
        
        // If no keys available, wait for cooldown and return least used
        Log.w(TAG, "All keys rate limited, waiting for cooldown")
        delay(1000)
        return getLeastUsedKey()
    }
    
    private fun selectBestKey(): String? {
        val now = System.currentTimeMillis()
        
        // First, try to find a key that's not rate limited and healthy
        val availableKeys = apiKeys.filter { status ->
            status.isHealthy &&
            status.cooldownUntil.get() < now &&
            !isRateLimited(status)
        }
        
        if (availableKeys.isEmpty()) {
            return null
        }
        
        // Select key with lowest usage
        val bestKey = availableKeys.minByOrNull { 
            status -> status.requestCount.get() 
        }
        
        if (bestKey != null) {
            updateKeyUsage(bestKey)
            return bestKey.key
        }
        
        return null
    }
    
    private fun isRateLimited(status: ApiKeyStatus): Boolean {
        val now = System.currentTimeMillis()
        val windowAge = now - status.windowStart.get()
        
        // Reset window if expired
        if (windowAge > RATE_LIMIT_WINDOW) {
            status.windowStart.set(now)
            status.requestCount.set(0)
            return false
        }
        
        return status.requestCount.get() >= MAX_REQUESTS_PER_KEY
    }
    
    private fun updateKeyUsage(status: ApiKeyStatus) {
        val now = System.currentTimeMillis()
        status.lastUsed.set(now)
        status.requestCount.incrementAndGet()
        
        // Update performance metrics
        val metrics = keyPerformance.getOrPut(status.key) { KeyPerformanceMetrics() }
        metrics.totalRequests.incrementAndGet()
        metrics.lastUsed = now
        
        Log.d(TAG, "Using API key ending in ...${status.key.takeLast(4)}, " +
              "requests in window: ${status.requestCount.get()}")
    }
    
    fun reportKeyError(key: String, error: Exception) {
        val status = apiKeys.find { it.key == key } ?: return
        val errors = status.consecutiveErrors.incrementAndGet()
        
        Log.w(TAG, "API key error reported, consecutive errors: $errors", error)
        
        // Update performance metrics
        val metrics = keyPerformance.getOrPut(key) { KeyPerformanceMetrics() }
        metrics.totalErrors.incrementAndGet()
        
        // Mark unhealthy after 3 consecutive errors
        if (errors >= 3) {
            markKeyUnhealthy(status)
        }
        
        // Check for rate limit error
        if (error.message?.contains("429") == true || 
            error.message?.contains("quota", ignoreCase = true) == true) {
            applyRateLimitCooldown(status)
        }
    }
    
    fun reportKeySuccess(key: String, responseTime: Long? = null) {
        val status = apiKeys.find { it.key == key } ?: return
        status.consecutiveErrors.set(0)
        
        // Update performance metrics
        val metrics = keyPerformance.getOrPut(key) { KeyPerformanceMetrics() }
        metrics.totalSuccesses.incrementAndGet()
        responseTime?.let { metrics.addResponseTime(it) }
        
        Log.d(TAG, "API key success, response time: ${responseTime}ms")
    }
    
    private fun markKeyUnhealthy(status: ApiKeyStatus) {
        Log.w(TAG, "Marking API key as unhealthy: ...${status.key.takeLast(4)}")
        status.consecutiveErrors.set(0)
        status.cooldownUntil.set(System.currentTimeMillis() + COOLDOWN_PERIOD)
        
        // Schedule health check
        scheduleHealthCheck(status)
    }
    
    private fun applyRateLimitCooldown(status: ApiKeyStatus) {
        val cooldownTime = System.currentTimeMillis() + COOLDOWN_PERIOD
        status.cooldownUntil.set(cooldownTime)
        Log.w(TAG, "Applied rate limit cooldown until ${cooldownTime}")
    }
    
    private fun scheduleHealthCheck(status: ApiKeyStatus) {
        // In production, this would schedule a background task
        // For now, we'll reset after cooldown
        status.cooldownUntil.set(System.currentTimeMillis() + HEALTH_CHECK_INTERVAL)
    }
    
    private fun getLeastUsedKey(): String {
        val leastUsed = apiKeys.minByOrNull { it.requestCount.get() }
        return leastUsed?.key ?: apiKeys.first().key
    }
    
    fun getKeyCount(): Int = apiKeys.size
    
    fun getHealthyKeyCount(): Int = apiKeys.count { 
        it.isHealthy && it.cooldownUntil.get() < System.currentTimeMillis() 
    }
    
    fun getKeyStatistics(): Map<String, Any> {
        return mapOf(
            "total_keys" to apiKeys.size,
            "healthy_keys" to getHealthyKeyCount(),
            "performance_metrics" to keyPerformance.map { (key, metrics) ->
                "...${key.takeLast(4)}" to mapOf(
                    "total_requests" to metrics.totalRequests.get(),
                    "success_rate" to metrics.getSuccessRate(),
                    "avg_response_time" to metrics.getAverageResponseTime()
                )
            }
        )
    }
    
    private class KeyPerformanceMetrics {
        val totalRequests = AtomicInteger(0)
        val totalSuccesses = AtomicInteger(0)
        val totalErrors = AtomicInteger(0)
        private val responseTimes = mutableListOf<Long>()
        var lastUsed: Long = 0
        
        @Synchronized
        fun addResponseTime(time: Long) {
            responseTimes.add(time)
            // Keep only last 100 response times
            if (responseTimes.size > 100) {
                responseTimes.removeAt(0)
            }
        }
        
        fun getSuccessRate(): Double {
            val total = totalRequests.get()
            return if (total > 0) {
                totalSuccesses.get().toDouble() / total
            } else {
                0.0
            }
        }
        
        @Synchronized
        fun getAverageResponseTime(): Double {
            return if (responseTimes.isNotEmpty()) {
                responseTimes.average()
            } else {
                0.0
            }
        }
    }
    
    // Extension function for multiple API keys in config
    fun addApiKeys(newKeys: List<String>) {
        // This would need to modify the apiKeys list
        // In practice, keys should be loaded from secure storage
        Log.d(TAG, "Request to add ${newKeys.size} new API keys")
    }
}