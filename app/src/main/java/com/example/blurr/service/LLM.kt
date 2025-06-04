package com.example.blurr.service

import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class LLM(
    private val apiKey: String,
    private val model: String,
    private val apiUrl: String
) {
    private val client = OkHttpClient()

    fun encodeImageBase64(imagePath: String): String {
        val imageFile = File(imagePath)
        val imageBytes = imageFile.readBytes()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    fun buildRequestPayload(
        messages: List<Pair<String, List<Map<String, Any>>>>,
        maxTokens: Int = 2048,
        temperature: Double = 0.0
    ): JSONObject {
        val data = JSONObject()
        data.put("model", model)
        data.put("max_tokens", maxTokens)
        data.put("temperature", temperature)

        val messagesArray = JSONArray()

        for ((role, contentList) in messages) {
            if (model.contains("claude") && role == "system") {
                data.put("system", contentList[0]["text"])
                continue
            }

            val contentJson = JSONArray()
            for (item in contentList) {
                when (item["type"]) {
                    "text" -> {
                        contentJson.put(JSONObject().put("type", "text").put("text", item["text"]))
                    }
                    "image_url" -> {
                        val base64Data = (item["image_url"] as Map<*, *>)["url"]
                            .toString()
                            .replace("data:image/jpeg;base64,", "")
                        val imageJson = JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Data)
                            })
                        }
                        contentJson.put(imageJson)
                    }
                }
            }
            messagesArray.put(JSONObject().apply {
                put("role", role)
                put("content", contentJson)
            })
        }

        data.put("messages", messagesArray)
        return data
    }

    fun sendRequest(
        messages: List<Pair<String, List<Map<String, Any>>>>,
        onComplete: (String?) -> Unit
    ) {
        val payload = buildRequestPayload(messages)
        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .post(body)

        if (model.contains("claude")) {
            requestBuilder.addHeader("x-api-key", apiKey)
            requestBuilder.addHeader("anthropic-version", "2023-06-01")
            requestBuilder.addHeader("content-type", "application/json")
        } else {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            requestBuilder.addHeader("Content-Type", "application/json")
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Error: ${e.message}")
                onComplete(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onComplete(null)
                        return
                    }

                    val json = JSONObject(response.body!!.string())
                    val content = if (model.contains("claude")) {
                        json.getJSONArray("content").getJSONObject(0).getString("text")
                    } else {
                        json.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }
                    onComplete(content)
                }
            }
        })
    }
}
