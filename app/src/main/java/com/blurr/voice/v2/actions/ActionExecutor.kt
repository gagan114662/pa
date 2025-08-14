package com.blurr.voice.v2.actions

import android.content.Context
import com.blurr.voice.agent.v1.InfoPool
import com.blurr.voice.api.Finger
import com.blurr.voice.utilities.SpeechCoordinator
import com.blurr.voice.utilities.UserInputManager
import com.blurr.voice.v2.fs.FileSystem
import kotlinx.coroutines.runBlocking
import kotlin.collections.get

/**
 * Executes a pre-validated, type-safe Action command.
 * The 'when' block is exhaustive, ensuring every action is handled.
 */
class ActionExecutor(private val finger: Finger) {

    suspend fun execute(action: Action, infoPool: InfoPool, context: Context) {
        // This 'when' is 100% type-safe and compile-time checked!
        when (action) {
            is Action.TapElement -> {
                val elementId = action.elementId
                val element = infoPool.currentElementsWithIds.find { it.id == elementId }
                if (element != null) {
                    finger.tap(element.centerX, element.centerY)
                    println("Tapped element $elementId at coordinates (${element.centerX}, ${element.centerY})")
                } else {
                    println("Element with ID $elementId not found")
                }
            }
            is Action.Speak -> { /* logic to speak action.message */ }
            is Action.Ask -> {

                val question = action.question
                runBlocking {
                    val speechCoordinator = SpeechCoordinator.getInstance(context)
                    speechCoordinator.speakToUser(question)
                }

                // Get user response using UserInputManager (which now uses SpeechCoordinator internally)
                val userInputManager = UserInputManager(context)
                val userResponse = userInputManager.askQuestion(question)

                // Update the instruction with the user's response
                val updatedInstruction = "${infoPool.instruction}\n\n[Agent asked: $question]\n[User responded: $userResponse]"
                infoPool.instruction = updatedInstruction

                println("Agent asked: $question")
                println("User responded: $userResponse")
            }
            is Action.OpenApp -> finger.openApp(action.appName)
            Action.Back -> finger.back()
            Action.Home -> finger.home()
            Action.SwitchApp -> finger.switchApp()
            Action.Wait -> {
                Thread.sleep(5_000)
            }
            is Action.ScrollDown -> finger.scrollDown(action.amount)
            is Action.ScrollUp -> finger.scrollUp(action.amount)
            is Action.SearchGoogle -> {
                finger.openApp("Chrome")
            }
            is Action.Done -> TODO()
            is Action.ExtractStructuredData -> TODO()
            is Action.InputText -> TODO()
            is Action.ScrollToText -> TODO()
            is Action.AppendFile -> {
                val fs = FileSystem(context)
                fs.appendFile(action.fileName,action.content)
            }
            is Action.ReadFile -> {
                val fs = FileSystem(context)
                //TODO
            }
            is Action.WriteFile -> {
                val fs = FileSystem(context)
                fs.writeFile(action.fileName,action.content)
            }
        }
    }
}