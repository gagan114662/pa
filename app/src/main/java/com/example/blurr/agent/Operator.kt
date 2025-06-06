package com.example.blurr.agent

import com.example.blurr.service.Finger
import com.google.ai.client.generativeai.type.TextPart

class Operator(private val finger: Finger) : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are a helpful AI assistant for operating mobile phones. 
            Your goal is to choose the correct actions to complete the user's instruction. 
            Think as if you are a human user operating the phone.
        """.trimIndent()
        return listOf("system" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        sb.appendLine("### Overall Plan ###")
        sb.appendLine(infoPool.plan)
        sb.appendLine()

        sb.appendLine("### Progress Status ###")
        sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        sb.appendLine()

        sb.appendLine("### Screen Information ###")
        sb.appendLine("Width: ${infoPool.width}, Height: ${infoPool.height}")
        sb.appendLine("Clickable elements:")
        infoPool.perceptionInfosPre.forEach {
            if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                sb.appendLine("${it.coordinates}; ${it.text}")
            }
        }
        sb.appendLine()
        sb.appendLine("### Keyboard status ###")
        sb.appendLine(if (infoPool.keyboardPre) "Keyboard is active." else "Keyboard is not active.")

        if (infoPool.tips.isNotBlank()) {
            sb.appendLine("\n### Tips ###")
            sb.appendLine(infoPool.tips)
        }

        if (infoPool.importantNotes.isNotBlank()) {
            sb.appendLine("\n### Important Notes ###")
            sb.appendLine(infoPool.importantNotes)
        }

        sb.appendLine("\n---")
        sb.appendLine("Carefully decide the next action. Choose one from atomic actions or available shortcuts.")
        sb.appendLine("Format: valid JSON {\"name\": \"ActionName\", \"arguments\": {key: value}}")

        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val thought = extractSection(response, "### Thought ###", "### Action ###")
        val action = extractSection(response, "### Action ###", "### Description ###")
        val description = extractSection(response, "### Description ###", null)
        return mapOf(
            "thought" to thought,
            "action" to action,
            "description" to description
        )
    }

    fun executeAtomicAction(name: String, args: Map<String, Any?>) {
        when (name.lowercase()) {
            "tap" -> finger.tap((args["x"] as Number).toInt(), (args["y"] as Number).toInt())
            "swipe" -> finger.swipe(
                (args["x1"] as Number).toInt(),
                (args["y1"] as Number).toInt(),
                (args["x2"] as Number).toInt(),
                (args["y2"] as Number).toInt()
            )
            "type" -> finger.type(args["text"].toString())
            "enter" -> finger.enter()
            "back" -> finger.back()
            "home" -> finger.home()
            "switch_app" -> finger.switchApp()
            "wait" -> Thread.sleep(10_000)
            else -> println("Unsupported action: $name")
        }
        if (name.lowercase() != "wait") Thread.sleep(3000)
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }
}
