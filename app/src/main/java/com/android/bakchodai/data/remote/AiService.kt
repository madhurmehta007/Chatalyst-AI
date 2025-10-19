// AiService.kt
package com.android.bakchodai.data.remote

import android.util.Log // Import Log
import com.android.bakchodai.BuildConfig
import com.android.bakchodai.data.model.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse // Import specific response type
import com.google.ai.client.generativeai.type.RequestOptions // Import RequestOptions
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiService {

    // Keep only one model instance
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash", // Use 1.5-flash for potentially better instruction following
        apiKey = BuildConfig.GEMINI_API_KEY,
        // Optional: Configure request options globally if needed
        requestOptions = RequestOptions(timeout = 60_000) // Example: 60-second timeout
    )

    // Helper function to clean common markdown
    private fun cleanResponse(text: String?): String {
        return text?.replace("**", "") // Remove double asterisks
            ?: "" // Return empty string if text is null
    }

    suspend fun getResponse(chatHistory: List<Message>): String = withContext(Dispatchers.IO) {
        // Simple prompt for 1-to-1 chat, instructing no markdown
        val systemPrompt = "You are a helpful chat assistant. Do not use markdown formatting like asterisks."
        val modelWithSystem = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(systemPrompt) }
        )

        val history = chatHistory.map {
            content(role = if (!it.senderId.startsWith("ai_")) "user" else "model") {
                text(it.content)
            }
        }

        return@withContext try {
            val response: GenerateContentResponse = modelWithSystem.generateContent(*history.toTypedArray())
            cleanResponse(response.text) // Clean the response
        } catch (e: Exception) {
            Log.e("AiService", "Error in getResponse: ${e.message}", e)
            "Sorry, something went wrong on my end."
        }
    }

    suspend fun generateGroupResponse(
        history: List<Message>,
        speakingAiUid: String,
        topic: String,
        personality: String
    ): String = withContext(Dispatchers.IO) {
        val name = speakingAiUid.removePrefix("ai_").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } // Capitalize properly
        val systemPrompt = """
You are $name, a fun friend in this group chat.
Personality: $personality
Topic: $topic
Keep responses short, 1-3 sentences, casual, funny, chaotic. Banter, roast, gossip, plan trips, IPL talk, life updates.
Do not repeat or be boring. Make it feel like real friends group - chaotic and hilarious!
IMPORTANT: Do NOT use any markdown formatting like **bold** or *italics*. Just plain text.
IMPORTANT: Do NOT start your response with your own name (e.g., "$name:"). Just write the message content.
""".trimIndent()

        // Use a model instance specifically configured with the system prompt
        val modelWithSystem = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(systemPrompt) },
            requestOptions = RequestOptions(timeout = 60_000) // Timeout for group response
        )


        val historyContent = history
            .sortedBy { it.timestamp }
            .takeLast(20) // Keep history manageable
            .map { msg ->
                val speakerRole = if (msg.senderId == speakingAiUid) {
                    "model" // The current AI speaking uses the 'model' role
                } else if (msg.senderId.startsWith("ai_")) {
                    // Other AIs or previous turns of this AI are treated like users for context
                    "user"
                } else {
                    "user" // Human user
                }
                // Include name only for 'user' roles for clarity in history
                val prefix = if(speakerRole == "user") {
                    val speakerName = msg.senderId.removePrefix("ai_").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    "$speakerName: "
                } else ""

                content(speakerRole) { text("$prefix${msg.content}") }
            }


        return@withContext try {
            // Generate content using the specifically configured model
            val response: GenerateContentResponse = modelWithSystem.generateContent(*historyContent.toTypedArray())
            cleanResponse(response.text?.trim()?.take(200)) // Clean and limit length
                ?: "Uh, pass."
        } catch (e: Exception) {
            Log.e("AiService", "Error in generateGroupResponse: ${e.message}", e)
            "Brain freeze! Give me a sec."
        }
    }
}