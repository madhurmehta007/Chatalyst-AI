// AiService.kt
package com.android.bakchodai.data.remote

import com.android.bakchodai.BuildConfig
import com.android.bakchodai.data.model.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiService {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun getResponse(chatHistory: List<Message>): String {
        val history = chatHistory.map {
            content(role = if (!it.senderId.startsWith("ai_")) "user" else "model") {
                text(it.content)
            }
        }

        return try {
            val response = generativeModel.generateContent(*history.toTypedArray())
            response.text ?: "Sorry, I'm not sure how to respond to that."
        } catch (e: Exception) {
            // Handle API errors (e.g., rate limiting, network issues)
            "Sorry, something went wrong on my end."
        }
    }

    suspend fun generateGroupResponse(
        history: List<Message>,
        speakingAiUid: String,
        topic: String,
        personality: String
    ): String = withContext(Dispatchers.IO) {
        val name = speakingAiUid.removePrefix("ai_").capitalize()
        val systemPrompt = """
You are $name, a fun friend in this group chat.
Personality: $personality
Topic: $topic
Keep responses short, 1-3 sentences, casual, funny, chaotic. Banter, roast, gossip, plan trips, IPL talk, life updates.
Do not repeat or be boring. Make it feel like real friends group - chaotic and hilarious!
"""

        val historyText = history
            .sortedBy { it.timestamp }
            .takeLast(20)
            .joinToString("\n") { msg ->
                val speaker = if (msg.senderId.startsWith("ai_")) {
                    msg.senderId.removePrefix("ai_").capitalize()
                } else {
                    "Human"
                }
                "$speaker: ${msg.content}"
            }

        val fullPrompt = """
$systemPrompt

Recent conversation:
$historyText

$name:
"""

        try {
            val response = generativeModel.generateContent(fullPrompt)
            response.text?.trim()?.take(200) ?: "Uh, pass."
        } catch (e: Exception) {
            "Brain freeze! Give me a sec."
        }
    }
}