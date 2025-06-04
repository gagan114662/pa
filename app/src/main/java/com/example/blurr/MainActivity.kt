package com.example.blurr

import android.graphics.BitmapFactory
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.blurr.service.Finger
import com.google.ai.client.generativeai.GenerativeModel
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

        showLogsButton.setOnClickListener {
            logsText.text = logs.joinToString("\n")
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

            // Start periodic task
            handler.post(screenshotAndTapTask)

            // Show previous screenshot if exists
            if (screenshotFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
                screenshotView.setImageBitmap(bitmap)
            }
        }
    }

    private val screenshotAndTapTask = object : Runnable {

        override fun run() {
//            takeScreenshot()
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
//            Shell.cmd("input tap $randomX $randomY").exec()
            val finger = Finger()
//            finger.tap(randomX, randomY)
//            finger.swipe(100, 200, 100, 100, 1)
//            finger.switchApp()

//            val client = GenerativeModel("gemini-2.0-flash", "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
//            val prompt = "Explain quantum computing"
//            val responses = client.generateContentStream(prompt)
//            val fullText = buildString {
//                responses.collect { part ->
//                    append(part.text)
//                }
//            }
//            println(fullText)



            withContext(Dispatchers.Main) {
                val log = "👆 Tap at ($randomX, $randomY) at $timestamp"
                statusText.text = log
                logs.add(log)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(screenshotAndTapTask)
    }
}
