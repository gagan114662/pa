package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class Manager : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are a helpful AI assistant for operating mobile phones. 
            Your goal is to track progress and devise high-level plans to achieve the user's requests. 
            Think as if you are a human user operating the phone.
        """.trimIndent()
        return listOf("system" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        if (infoPool.plan.isEmpty()) {
            sb.appendLine("---")
            sb.appendLine("Think step by step and make a high-level plan to achieve the user's instruction.")
            sb.appendLine("Break down complex tasks into subgoals. The screenshot displays the starting state of the phone.\n")

            if (infoPool.shortcuts.isNotEmpty()) {
                sb.appendLine("### Available Shortcuts from Past Experience ###")
                infoPool.shortcuts.forEach { (name, shortcut) ->
                    sb.appendLine("- $name: ${shortcut.description} | Precondition: ${shortcut.precondition}")
                }
                sb.appendLine()
            }

            sb.appendLine("---")
            sb.appendLine("Provide your output in the following format:\n")
            sb.appendLine("### Thought ###")
            sb.appendLine("Explain your rationale for the plan and subgoals.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("1. first subgoal")
            sb.appendLine("2. second subgoal")
            sb.appendLine("...\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("The first subgoal you should work on.")
        } else {
            sb.appendLine("### Current Plan ###")
            sb.appendLine(infoPool.plan)
            sb.appendLine()
            sb.appendLine("### Previous Subgoal ###")
            sb.appendLine(infoPool.currentSubgoal)
            sb.appendLine()
            sb.appendLine("### Progress Status ###")
            sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
            sb.appendLine()
            sb.appendLine("### Important Notes ###")
            sb.appendLine(infoPool.importantNotes.ifEmpty { "No important notes recorded." })
            sb.appendLine()

            if (infoPool.errorFlagPlan) {
                sb.appendLine("### Potentially Stuck! ###")
                val k = infoPool.errToManagerThresh
                val lastActions = infoPool.actionHistory.takeLast(k)
                val lastSummaries = infoPool.summaryHistory.takeLast(k)
                val lastErrors = infoPool.errorDescriptions.takeLast(k)
                for (i in lastActions.indices) {
                    sb.appendLine("- Attempt: Action: ${lastActions[i]} | Description: ${lastSummaries[i]} | Outcome: Failed | Feedback: ${lastErrors[i]}")
                }
            }

            if (infoPool.shortcuts.isNotEmpty()) {
                sb.appendLine("\n### Available Shortcuts from Past Experience ###")
                infoPool.shortcuts.forEach { (name, shortcut) ->
                    sb.appendLine("- $name: ${shortcut.description} | Precondition: ${shortcut.precondition}")
                }
            }

            sb.appendLine("\n---")
            sb.appendLine("### Thought ###")
            sb.appendLine("Explain whether the plan needs to be revised and why.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("Updated or continued plan.\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("Next subgoal to execute or write \"Finished\".")
        }

        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val thought = extractSection(response, "### Thought ###", "### Plan ###")
        val plan = extractSection(response, "### Plan ###", "### Current Subgoal ###")
        val currentSubgoal = extractSection(response, "### Current Subgoal ###", null)
        return mapOf(
            "thought" to thought,
            "plan" to plan,
            "current_subgoal" to currentSubgoal
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
