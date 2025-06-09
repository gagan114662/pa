package com.example.blurr

import android.content.Context
import android.graphics.BitmapFactory
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.blurr.agent.ActionReflector
import com.example.blurr.agent.ClickableInfo
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.Manager
import com.example.blurr.agent.Operator
import com.example.blurr.agent.addResponse
import com.example.blurr.agent.addResponsePrePost
import com.example.blurr.agent.getReasoningModelApiResponse
import com.example.blurr.service.Retina
import com.example.blurr.service.Eyes
import com.example.blurr.service.Finger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import org.json.JSONObject
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
            handleUserInput(this,userInput)
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
//            performRandomTap()
//            println("App is running")
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

    private fun handleUserInput(context: Context, inputText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val finger = Finger()
            finger.home()
            Thread.sleep((500))
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val logDir = File(context.filesDir, "logs/mobile_agent_E/test/$timestamp").apply { mkdirs() }
            val screenshotsDir = File(logDir, "screenshots").apply { mkdirs() }

            val eyes = Eyes(context)
            val retina = Retina(context, eyes, "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg") // Replace with actual key or inject
            val infoPool = InfoPool(inputText) // Assuming constructor exists

            var iteration = 0
            var lastScreenshotFile: File? = null
            val maxItr = 10
            val maxConsecutiveFailures = 3
            val maxRepetitiveActions = 3
            val steps = mutableListOf<JSONObject>()

            while (iteration < maxItr) {
                iteration++
//                Thread.sleep(10000)
                // Step 1: Take Perception
                val screenshotFile = eyes.getScreenshotFile()
                val screenshotPath = File(screenshotsDir, "screenshot.jpg")
                screenshotFile.copyTo(screenshotPath, overwrite = true)
                val (perceptionInfos, width, height) = retina.getPerceptionInfos(context)
                infoPool.width = width
                infoPool.height = height
                infoPool.perceptionInfosPre = perceptionInfos as MutableList<ClickableInfo>



                // Step 2: Manager Planning
                val manager = Manager()
                val promptPlan = manager.getPrompt(infoPool)
                val chatPlan = manager.initChat()
                val combinedChatPlan  = addResponse("user",promptPlan, chatPlan, screenshotFile )
                val outputPlan = getReasoningModelApiResponse(combinedChatPlan, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")

                val parsedManagerPlan = manager.parseResponse(outputPlan)
                infoPool.plan = parsedManagerPlan["plan"].toString()
                infoPool.currentSubgoal =  parsedManagerPlan["current_subgoal"].toString()
                infoPool.lastActionThought = parsedManagerPlan["thought"].toString()
                infoPool.lastSummary = infoPool.lastActionThought
                println(parsedManagerPlan)

                infoPool.perceptionInfosPre.forEach { element ->
                    println("THE ELEMENT NAME : ${element.text}, COORDINATES : ${element.coordinates}")
                }

                // Step 3 Operator's turn, he will execute on the plan of manager
                val operator = Operator(finger)
                val actionPrompt = operator.getPrompt(infoPool)
                println("ACTION PROMPT :::: $actionPrompt")

                val actionChat = operator.initChat()
//                println("SYSTEM PROMPT :::: "+actionChat[0].second[0].text)
                val actionCombinedChat  = addResponse("user",actionPrompt, actionChat, screenshotPath )
                var actionOutput = getReasoningModelApiResponse(actionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
                println("ACTION OUTPUT :::: $actionOutput")
                val screenshotLogger: (String) -> Unit = { filename ->
                    println("Saving screenshot: $filename")
                }
                if (actionOutput.startsWith("```json")){
                    actionOutput = actionOutput
                        .replace(Regex("^```(?:json)?\\s*"), "") // remove opening code block
                        .replace(Regex("\\s*```\\s*$"), "")     // remove closing code block
                        .trim()
                }

                // THE EXECUTION HAPPEN HERE
                if(actionOutput != "") {
                    val result = operator.execute(
                        actionOutput.toString(), infoPool, screenshotLogger, context
                    )
                }


            //          NOTE:  ONLY FOR REMEMBER TO ADD THEM BY REFLECTOR
            //            if (infoPool.errorFlagPlan) {
//                sb.appendLine("### Potentially Stuck! ###")
//                val k = infoPool.errToManagerThresh
//                val lastActions = infoPool.actionHistory.takeLast(k)
//                val lastSummaries = infoPool.summaryHistory.takeLast(k)
//                val lastErrors = infoPool.errorDescriptions.takeLast(k)
//                for (i in lastActions.indices) {
//                    sb.appendLine("- Attempt: Action: ${lastActions[i]} | Description: ${lastSummaries[i]} | Outcome: Failed | Feedback: ${lastErrors[i]}")
//                }
//            }
                //          NOTE:  ONLY FOR REMEMBER TO ADD THEM BY REFLECTOR

                // Step 4 : Take the Perception after the action by operator has been performed
//                infoPool.lastAction = actionOutput
//                val postScreenshotFile = eyes.getScreenshotFile()
//                postScreenshotFile.copyTo(screenshotPath, overwrite = true)
//                val (postPerceptionInfos, _, _) = retina.getPerceptionInfos(context)
//                infoPool.perceptionInfosPost = postPerceptionInfos as MutableList<ClickableInfo>
//
//
//                val actionReflector = ActionReflector()
//                val reflectionPrompt = actionReflector.getPrompt(infoPool)
////                println("reflection prompt : $reflectionPrompt")
//                val reflectionChat = actionReflector.initChat()
//
//                val reflectionCombinedChat  = addResponsePrePost("user",reflectionPrompt, reflectionChat, screenshotFile, postScreenshotFile )
////                println("combined: $reflectionCombinedChat")
//                val reflectionLLMOutput = getReasoningModelApiResponse(reflectionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
//                val parsedReflection = actionReflector.parseResponse(reflectionLLMOutput)
//                println("Parsed LLM Output $parsedReflection")
////                break
//
////
//                infoPool.progressStatusHistory.add(parsedReflection["progress_status"].toString())
//                infoPool.actionHistory.add(actionOutput)
//                infoPool.summaryHistory.add(actionOutput)
//                infoPool.actionOutcomes.add(parsedReflection["outcome"].toString())
//                infoPool.errorDescriptions.add(parsedReflection["error_description"].toString())
//                infoPool.progressStatus = parsedReflection["progress_status"].toString()
//                // Step 7: Delay before next step
                Thread.sleep(1500)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(screenshotAndTapTask)
    }
}
