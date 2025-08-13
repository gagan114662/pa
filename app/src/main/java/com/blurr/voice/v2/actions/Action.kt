package com.blurr.voice.v2.actions

import com.blurr.voice.agent.v1.AgentConfig
import com.blurr.voice.agent.v1.InfoPool
import org.json.JSONObject
import kotlin.reflect.KClass

// Data class to hold parameter metadata without reflection
data class ParamSpec(val name: String, val type: KClass<*>, val description: String)

/**
 * A sealed class representing all possible type-safe commands the agent can execute.
 * The companion object acts as a registry for metadata and construction.
 */
sealed class Action {
    // Each action is a data class (if it has args) or an object (if it doesn't)
    data class TapElement(val elementId: Int) : Action()
//    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : Action()
//    data class Type(val text: String) : Action()
    data object SwitchApp : Action()
    data object Back : Action()
    data object Home : Action()
    data object Wait : Action()
    data class Speak(val message: String) : Action()
    data class Ask(val question: String) : Action()
    data class OpenApp(val appName: String) : Action()
    data class ScrollDown(val amount: Int) : Action()
    data class ScrollUp(val amount: Int) : Action()
    data class SearchGoogle(val query: String) : Action()
    data class ScrollToText(val text: String) : Action()
    data class ExtractStructuredData(val query: String) : Action()
    data class InputText(val index: Int, val text: String) : Action()
    data class Done(val success: Boolean, val text: String, val filesToDisplay: List<String>? = null) : Action()

    // --- Companion Object: The Registry and Metadata Provider ---
    companion object {
        // Defines the metadata for an action and how to build it from raw arguments
        data class Spec(
            val name: String,
            val description: String,
            val params: List<ParamSpec>,
            val build: (args: Map<String, Any?>) -> Action
        )

        // The single source of truth for all action specifications
        private val allSpecs: Map<String, Spec> = mapOf(
            "TapElement" to Spec(
                name = "TapElement",
                description = "Tap the element with the specified numeric ID.",
                params = listOf(ParamSpec("element_id", Int::class, "The numeric ID of the element.")),
                build = { args -> TapElement(args["element_id"] as Int) }
            ),
            "Switch_App" to Spec("Switch_App", "Show the App switcher.", emptyList()) { SwitchApp },
            "Back" to Spec("Back", "Go back to the previous screen.", emptyList()) { Back },
            "Home" to Spec("Home", "Go to the device's home screen.", emptyList()) { Home },
            "Wait" to Spec("Wait", "Wait for 10 seconds for loading.", emptyList()) { Wait },
            "Speak" to Spec(
                name = "Speak",
                description = "Speak the 'message' to the user.",
                params = listOf(ParamSpec("message", String::class, "The message to speak.")),
                build = { args -> Speak(args["message"] as String) }
            ),
            "Ask" to Spec(
                name = "Ask",
                description = "Ask the 'question' to the user and await a response.",
                params = listOf(ParamSpec("question", String::class, "The question to ask.")),
                build = { args -> Ask(args["question"] as String) }
            ),
            "Open_App" to Spec(
                name = "Open_App",
                description = "Open the app named 'app_name'.",
                params = listOf(ParamSpec("app_name", String::class, "The name of the app.")),
                build = { args -> OpenApp(args["app_name"] as String) }
            ),
            "Scroll_Down" to Spec(
                name = "Scroll_Down",
                description = "Scroll down by the specified amount of pixels (negative value).",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to scroll down (use negative value)")),
                build = { args -> ScrollDown(args["amount"] as Int) }
            ),
            "Scroll_Up" to Spec(
                name = "Scroll_Up",
                description = "Scroll up by the specified amount of pixels (negative value).",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to scroll up (use negative value)")),
                build = { args -> ScrollUp(args["amount"] as Int) }
            ),
            "Search_Google" to Spec(
                name = "Search_Google",
                description = "Search Google with the specified query.",
                params = listOf(ParamSpec("query", String::class, "The search query to perform on Google")),
                build = { args -> SearchGoogle(args["query"] as String) }
            ),
            "Scroll_To_Text" to Spec(
                name = "Scroll_To_Text",
                description = "Scrolls the page until the specified text is visible in the viewport. This is useful when an element is not currently visible.",
                params = listOf(ParamSpec("text", String::class, "The text content to find and scroll to")),
                build = { args -> ScrollToText(args["text"] as String) }
            ),
            "Extract_Structured_Data" to Spec(
                name = "Extract_Structured_Data",
                description = "Extracts specific, structured information from the current webpage based on a query. It's best used on detail pages (like a single product or article) rather than lists. The agent uses another LLM to parse the page content and answer the query.",
                params = listOf(ParamSpec("query", String::class, "A question or description of the data to extract (e.g., 'what is the price and rating of this product?')")),
                build = { args -> ExtractStructuredData(args["query"] as String) }
            ),
            "Input_Text" to Spec(
                name = "Input_Text",
                description = "Enters text into an input field (like a search bar or a form field).",
                params = listOf(
                    ParamSpec("index", Int::class, "The numerical index of the input element"),
                    ParamSpec("text", String::class, "The text to be typed into the element")
                ),
                build = { args -> InputText(args["index"] as Int, args["text"] as String) }
            ),
            "Done" to Spec(
                name = "Done",
                description = "Completes the current task. It is used to signal that the objective has been met or that the agent cannot proceed further.",
                params = listOf(
                    ParamSpec("success", Boolean::class, "True if the task was completed successfully, False otherwise"),
                    ParamSpec("text", String::class, "A summary of the results or a final message for the user"),
                    ParamSpec("files_to_display", List::class, "A list of filenames (e.g., ['report.pdf']) that should be presented to the user as attachments")
                ),
                build = { args -> 
                    val filesToDisplay = args["files_to_display"] as? List<String>
                    Done(args["success"] as Boolean, args["text"] as String, filesToDisplay)
                }
            )
//            "Swipe" to Spec(
//                name = "Swipe",
//                description = "Swipe from (x1, y1) to (x2, y2).",
//                params = listOf(
//                    ParamSpec("x1", Int::class, "Start X"), ParamSpec("y1", Int::class, "Start Y"),
//                    ParamSpec("x2", Int::class, "End X"), ParamSpec("y2", Int::class, "End Y")
//                ),
//                build = { args -> Swipe(args["x1"] as Int, args["y1"] as Int, args["x2"] as Int, args["y2"] as Int) }
//            ),
            // "Type" to Spec(
            //     name = "Type",
            //     description = "Type the 'text' into a focused input box.",
            //     params = listOf(ParamSpec("text", String::class, "The text to type.")),
            //     build = { args -> Type(args["text"] as String) }
            // ),
        )

        /**
         * Returns the specifications for all actions, respecting the agent's configuration.
         */
        fun getAvailableSpecs(config: AgentConfig, infoPool: InfoPool): List<Spec> {
            return allSpecs.values.filter { spec ->
                when (spec.name) {
                    "Open_App" -> config.enableDirectAppOpening
                    "Type" -> infoPool.keyboardPre
                    else -> true
                }
            }
        }

//        /**
//         * Safely constructs an Action object from a name and a map of arguments.
//         */
//        fun fromJson(name: String, argsJson: JSONObject?): Action {
//            val spec = allSpecs[name] ?: throw IllegalArgumentException("Unknown action name: $name")
//            val argsMap = argsJson?.toMap() ?: emptyMap<String, Any?>()
//            return spec.build(argsMap)
//        }
    }
}