package com.example.blurr.agent

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.blurr.service.Eyes
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class Retina(
    private val context: Context,
    private val eyes: Eyes,
    private val apiKey: String
) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getPerceptionInfos(): Triple<List<ClickableInfo>, Int, Int> {
        // Step 1: Capture screenshot
        eyes.openEyes()
        val screenshotFile: File = eyes.getScreenshotFile()

        // Step 2: Read image and encode as base64
        val imageBytes = screenshotFile.readBytes()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val width = bitmap.width
        val height = bitmap.height

        // Step 3: Prepare JSON request body
        val payload = JSONObject()
        payload.put("model", "gemini-2.0-flash")

        val contents = JSONArray().apply {
            put(JSONObject().put("parts", JSONArray().put(JSONObject()
                .put("inline_data", JSONObject()
                    .put("mime_type", "image/png")
                    .put("data", base64Image)
                ))))
            put(JSONObject().put("parts", JSONArray().put(JSONObject()
                .put("text", "Detect all icons and UI elements. Return a JSON array where each item has `label` and `box_2d` fields in format [ymin, xmin, ymax, xmax] scaled 0–1000."))))
        }
        payload.put("contents", contents)

        // Step 4: Send request to Gemini
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .post(RequestBody.create("application/json".toMediaType(), payload.toString()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Gemini API failed: ${response.code} ${response.message}")
        }

        val resultJson = JSONObject(response.body?.string() ?: "")
        val responseText = resultJson
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        // Step 5: Parse response (assumes Gemini returned JSON in text)
        val boxes = extractJsonArray(responseText)
        val clickableInfos = boxes.mapNotNull { obj ->
            try {
                val box = obj.getJSONArray("box_2d")
                val label = obj.getString("label")
                val ymin = box.getDouble(0) / 1000 * height
                val xmin = box.getDouble(1) / 1000 * width
                val ymax = box.getDouble(2) / 1000 * height
                val xmax = box.getDouble(3) / 1000 * width
                val centerX = ((xmin + xmax) / 2).toInt()
                val centerY = ((ymin + ymax) / 2).toInt()
                ClickableInfo("icon: $label", centerX to centerY)
            } catch (e: Exception) {
                null
            }
        }

        return Triple(clickableInfos, width, height)
    }

    private fun extractJsonArray(text: String): List<JSONObject> {
        val regex = Regex("""\[\s*\{.*?\}\s*]""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(text) ?: return emptyList()
        val array = JSONArray(match.value)
        return List(array.length()) { array.getJSONObject(it) }
    }
}
