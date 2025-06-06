package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class ActionReflector : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are a helpful AI assistant for operating mobile phones.
            Your goal is to verify whether the last action produced the expected behavior 
            and to keep track of the overall progress.
        """.trimIndent()
        return listOf("system" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool): String {
        val sb = StringBuilder()

        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        sb.appendLine("### Progress Status ###")
        sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        sb.appendLine()

        sb.appendLine("---")
        sb.appendLine("These are screenshots before and after the last action.")
        sb.appendLine("Screen dimensions: ${infoPool.width} x ${infoPool.height}")
        sb.appendLine()

        sb.appendLine("### Screen Information Before the Action ###")
        infoPool.perceptionInfosPre.forEach {
            if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                sb.appendLine("${it.coordinates}; ${it.text}")
            }
        }
        sb.appendLine("Keyboard status before the action: ${if (infoPool.keyboardPre) "Active" else "Inactive"}\n")

        sb.appendLine("### Screen Information After the Action ###")
        infoPool.perceptionInfosPost.forEach {
            if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                sb.appendLine("${it.coordinates}; ${it.text}")
            }
        }
        sb.appendLine("Keyboard status after the action: ${if (infoPool.keyboardPost) "Active" else "Inactive"}\n")

        sb.appendLine("---")
        sb.appendLine("### Latest Action ###")
        sb.appendLine("Action: ${infoPool.lastAction}")
        sb.appendLine("Expectation: ${infoPool.lastSummary}")
        sb.appendLine()

        sb.appendLine("---")
        sb.appendLine("Carefully examine the action outcome.")
        sb.appendLine("For 'Swipe' actions, partial screen changes can still be valid.")
        sb.appendLine("Use this format:\n")
        sb.appendLine("### Outcome ###")
        sb.appendLine("A, B, or C\n")
        sb.appendLine("### Error Description ###")
        sb.appendLine("If failed, explain why. If successful, write 'None'.\n")
        sb.appendLine("### Progress Status ###")
        sb.appendLine("Updated progress if successful, otherwise copy old status.")

        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val outcome = extractSection(response, "### Outcome ###", "### Error Description ###")
        val errorDescription = extractSection(response, "### Error Description ###", "### Progress Status ###")
        val progressStatus = extractSection(response, "### Progress Status ###", null)
        return mapOf(
            "outcome" to outcome,
            "error_description" to errorDescription,
            "progress_status" to progressStatus
        )
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }
}
