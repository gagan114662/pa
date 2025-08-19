package com.blurr.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.services.EnhancedWakeWordService
import com.blurr.voice.utilities.PermissionManager
import com.blurr.voice.utilities.UserIdManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.WakeWordManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var handler: Handler
    private lateinit var managePermissionsButton: TextView
    private lateinit var tvPermissionStatus: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var wakeWordButton: TextView
    private lateinit var userId: String
    private lateinit var permissionManager: PermissionManager
    private lateinit var wakeWordManager: WakeWordManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                // The manager will handle the service start after permission is granted.
                wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handleIntent(intent)

        val profileManager = UserProfileManager(this)
        if (!profileManager.isProfileComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        managePermissionsButton = findViewById(R.id.btn_manage_permissions) // ADDED

        val userIdManager = UserIdManager(applicationContext)
        userId = userIdManager.getOrCreateUserId()

        permissionManager = PermissionManager(this)
        permissionManager.initializePermissionLauncher()
        permissionManager.requestAllPermissions()
        // Initialize UI components
        managePermissionsButton = findViewById(R.id.btn_manage_permissions)

        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        settingsButton = findViewById(R.id.settingsButton)
        wakeWordButton = findViewById(R.id.wakeWordButton)

        // Initialize managers
        wakeWordManager = WakeWordManager(this, requestPermissionLauncher)
        handler = Handler(Looper.getMainLooper())


        // Setup UI and listeners
        setupClickListeners()
        setupSettingsButton()
        setupGradientText()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.blurr.voice.WAKE_UP_PANDA") {
            Log.d("MainActivity", "Wake up Panda shortcut activated!")
            if (!ConversationalAgentService.isRunning) {
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Panda is waking up...", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "ConversationalAgentService is already running.")
                Toast.makeText(this, "Panda is already awake!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.memoriesButton).setOnClickListener {
            startActivity(Intent(this, MemoriesActivity::class.java))
        }
        wakeWordButton.setOnClickListener {
            wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
            // Give the service a moment to update its state before refreshing the UI
            handler.postDelayed({ updateUI() }, 500)
        }

        managePermissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        findViewById<TextView>(R.id.github_link_textview).setOnClickListener {
            val url = "https://github.com/Ayush0Chaudhary/blurr"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupGradientText() {
        val karanTextView = findViewById<TextView>(R.id.karan_textview_gradient)
        karanTextView.measure(0, 0)
        val textShader: Shader = LinearGradient(
            0f, 0f, karanTextView.measuredWidth.toFloat(), 0f,
            intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt()),
            null, Shader.TileMode.CLAMP
        )
        karanTextView.paint.shader = textShader
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        val allPermissionsGranted = permissionManager.areAllPermissionsGranted()
        if (allPermissionsGranted) {
            tvPermissionStatus.text = "All required permissions are granted."
            tvPermissionStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            tvPermissionStatus.text = "Some permissions are missing. Tap below to manage."
            tvPermissionStatus.setTextColor(Color.parseColor("#F44336")) // Red
        }
        wakeWordManager.updateButtonState(wakeWordButton)
    }

}