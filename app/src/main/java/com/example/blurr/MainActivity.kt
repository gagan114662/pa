package com.example.blurr

import android.graphics.BitmapFactory
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.blurr.service.Finger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var screenshotView: ImageView
    private lateinit var statusText: TextView
    private lateinit var logsText: TextView
    private lateinit var showLogsButton: Button
    private lateinit var inputField: EditText
    private lateinit var performTaskButton: TextView
    private lateinit var screenshotFile: File

    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 3000L // 3 seconds
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenshotView = findViewById(R.id.screenshotView)
        statusText = findViewById(R.id.statusText)
        logsText = findViewById(R.id.logsText)
        showLogsButton = findViewById(R.id.showLogsButton)
        inputField = findViewById(R.id.inputField)
        performTaskButton = findViewById(R.id.performTaskButton)

        showLogsButton.setOnClickListener {
            logsText.text = logs.joinToString("\n")
        }

        performTaskButton.setOnClickListener {
            val userInput = inputField.text.toString()
            handleUserInput(userInput)
        }

        Shell.getShell()
        val hasRoot = Shell.isAppGrantedRoot()

        statusText.text = if (hasRoot == true) {
            "✅ Root access granted!"
        } else {
            "❌ No root access!"
        }

        if (hasRoot == true) {
            screenshotFile = File(filesDir, "latest.png")

            handler.post(screenshotAndTapTask)

            if (screenshotFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
                screenshotView.setImageBitmap(bitmap)
            }
        }
    }

    private val screenshotAndTapTask = object : Runnable {
        override fun run() {
            performRandomTap()
            println("App is running")
            handler.postDelayed(this, interval)
        }
    }

    private fun takeScreenshot() {
        val output = screenshotFile.absolutePath
        val timestamp = dateFormat.format(Date())

        CoroutineScope(Dispatchers.IO).launch {
            Shell.cmd("screencap -p $output").exec()
            if (screenshotFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(output)
                withContext(Dispatchers.Main) {
                    screenshotView.setImageBitmap(bitmap)
                    val log = "🖼️ Screenshot at $timestamp"
                    statusText.text = log
                    logs.add(log)
                }
            }
        }
    }

    private fun performRandomTap() {
        val randomX = Random.nextInt(100, 900)
        val randomY = Random.nextInt(300, 1600)
        val timestamp = dateFormat.format(Date())

        CoroutineScope(Dispatchers.IO).launch {
            val finger = Finger()
            // finger.tap(randomX, randomY)

            withContext(Dispatchers.Main) {
                val log = "👆 Tap at ($randomX, $randomY) at $timestamp"
                statusText.text = log
                logs.add(log)
            }
        }
    }

    private fun handleUserInput(inputText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val timestamp = dateFormat.format(Date())
            val result = "🔧 Performing task with input: $inputText at $timestamp"

            withContext(Dispatchers.Main) {
                statusText.text = result
                logs.add(result)
            }

            // You can insert actual task logic here, e.g.:
            // if (inputText.contains("swipe")) finger.swipe(...)
            // or trigger AI, scripts, etc.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(screenshotAndTapTask)
    }
}
