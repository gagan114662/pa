package com.blurr.app.api

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private var sttDetector: WakeWordDetector? = null
    private var isListening = false
    private var useSTTFallback = false
    private val keyManager = PicovoiceKeyManager(context)
    private var coroutineScope: CoroutineScope? = null

    companion object {
        private const val TAG = "PorcupineWakeWordDetector"
    }

    fun start() {
        if (isListening) {
            Log.d(TAG, "Already started.")
            return
        }

        // Create a new coroutine scope for this start operation
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Start the key fetching process asynchronously
        coroutineScope?.launch {
            try {
                val accessKey = keyManager.getAccessKey()
                if (accessKey != null) {
                    Log.d(TAG, "Successfully obtained Picovoice access key")
                    startPorcupineWithKey(accessKey)
                } else {
                    Log.e(TAG, "Failed to obtain Picovoice access key. Falling back to STT-based detection.")
                    startSTTFallback()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting access key: ${e.message}")
                startSTTFallback()
            }
        }
    }

    private suspend fun startPorcupineWithKey(accessKey: String) = withContext(Dispatchers.Main) {
        try {
            // Create the wake word callback
            val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
                Log.d(TAG, "Wake word detected! Keyword index: $keywordIndex")
                onWakeWordDetected()
                // PorcupineManager should automatically continue listening after detection
            }

            // Create error callback for debugging
            val errorCallback = PorcupineManagerErrorCallback { error ->
                Log.e(TAG, "Porcupine error: ${error.message}")
                // If there's an error, fall back to STT
                if (isListening && !useSTTFallback) {
                    Log.d(TAG, "Falling back to STT due to Porcupine error")
                    startSTTFallback()
                }
            }

            // Build and start PorcupineManager
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(arrayOf("Panda_en_android_v3_0_0.ppn"))
                .setSensitivity(0.5f) // Set sensitivity to 0.5 for better detection
                .setErrorCallback(errorCallback)
                .build(context, wakeWordCallback)

            porcupineManager?.start()
            isListening = true
            useSTTFallback = false
            Log.d(TAG, "Porcupine wake word detection started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}")
            // Fallback to STT-based detection if Porcupine fails
            Log.d(TAG, "Falling back to STT-based wake word detection")
            startSTTFallback()
        }
    }

    fun stop() {
        if (!isListening) {
            Log.d(TAG, "Already stopped.")
            return
        }

        try {
            if (useSTTFallback) {
                sttDetector?.stop()
                sttDetector = null
                Log.d(TAG, "STT fallback wake word detection stopped.")
            } else {
                porcupineManager?.stop()
                porcupineManager?.delete()
                porcupineManager = null
                Log.d(TAG, "Porcupine wake word detection stopped.")
            }
            isListening = false
            useSTTFallback = false
            
            // Cancel the coroutine scope
            coroutineScope?.cancel()
            coroutineScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection: ${e.message}")
        }
    }

    private fun startSTTFallback() {
        try {
            sttDetector = WakeWordDetector(context, onWakeWordDetected)
            sttDetector?.start()
            isListening = true
            useSTTFallback = true
            Log.d(TAG, "STT fallback wake word detection started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting STT fallback: ${e.message}")
        }
    }
} 