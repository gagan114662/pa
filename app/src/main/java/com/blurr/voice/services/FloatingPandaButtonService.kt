package com.blurr.voice.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.content.ContextCompat
import com.blurr.voice.ConversationalAgentService

class FloatingPandaButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "FloatingPandaButton"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "Floating Panda Button Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Floating Panda Button Service starting...")
        
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating button: 'Draw over other apps' permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            showFloatingButton()
            if (floatingButton == null) {
                Log.w(TAG, "Failed to show floating button, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
            stopSelf()
            return START_NOT_STICKY
        }
        
        return START_STICKY
    }

    private fun showFloatingButton() {
        if (floatingButton != null) {
            Log.d(TAG, "Floating button already showing")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            // Create the button programmatically
            floatingButton = createButtonProgrammatically()
            val button = floatingButton as Button

            // Set up the button click listener
            button.setOnClickListener {
                Log.d(TAG, "Floating Panda button clicked!")
                triggerPandaActivation()
            }

            // Calculate position near the battery icon (top-right area)
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Position the button in the bottom-right area
            val buttonWidth = (70 * displayMetrics.density).toInt() // 100dp for smaller size
            val buttonHeight = (33 * displayMetrics.density).toInt() // 40dp for smaller height
            val marginFromBottom = (0 * displayMetrics.density).toInt() // 100dp from bottom
            val marginFromRight = (16 * displayMetrics.density).toInt() // 16dp from right edge

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                buttonWidth,
                buttonHeight,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = marginFromRight
                y = marginFromBottom
            }

            windowManager?.addView(floatingButton, params)
            Log.d(TAG, "Floating Panda button added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button", e)
            floatingButton = null
        }
    }

    private fun createButtonProgrammatically(): Button {
        return Button(this).apply {
            text = "Hey Panda!"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#BE63F3"))
            // setPadding(, 1, 2, 1)
            isClickable = true
            isFocusable = true
            
            // Add some styling to make it look better
            elevation = 8f
            alpha = 0.9f
        }
    }

    private fun triggerPandaActivation() {
        // This is the same action that happens when wake word is detected
        try {
            if (!ConversationalAgentService.isRunning) {
                Log.d(TAG, "Starting ConversationalAgentService from floating button")
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                Log.d(TAG, "ConversationalAgentService is already running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ConversationalAgentService", e)
        }
    }

    private fun hideFloatingButton() {
        floatingButton?.let { button ->
            try {
                if (button.isAttachedToWindow) {
                    windowManager?.removeView(button)
                } else {
                    // Button is not attached, just continue
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button", e)
            }
        }
        floatingButton = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Floating Panda Button Service destroying...")
        hideFloatingButton()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 