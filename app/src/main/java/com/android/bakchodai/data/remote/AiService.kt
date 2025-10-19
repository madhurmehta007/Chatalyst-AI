package com.android.bakchodai.data.remote

import android.util.Log
import com.android.bakchodai.BuildConfig
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiService @Inject constructor() {

    // Helper function to clean common markdown
    private fun cleanResponse(text: String?): String {
        return text?.replace("**", "") // Remove double asterisks
            ?.trim() // Trim leading/trailing whitespace
            ?: "" // Return empty string if text is null
    }

    // Function for 1-to-1 responses
    suspend fun getResponse(chatHistory: List<Message>): String = withContext(Dispatchers.IO) {
        val systemPrompt = "You are a helpful chat assistant. Do not use markdown formatting like asterisks."
        // Create a model instance specifically for this prompt if needed, or use the base one
        val model = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(systemPrompt) } // Apply system instruction here
        )

        val history = chatHistory.map {
            content(role = if (!it.senderId.startsWith("ai_")) "user" else "model") {
                text(it.content)
            }
        }

        return@withContext try {
            val response: GenerateContentResponse = model.generateContent(*history.toTypedArray())
            cleanResponse(response.text)
        } catch (e: Exception) {
            Log.e("AiService", "Error in getResponse: ${e.message}", e)
            "Sorry, something went wrong on my end."
        }
    }

    // Function for Group responses (Refactored)
    suspend fun generateGroupResponse(
        history: List<Message>,
        speakingAiUid: String,
        topic: String,
        allUsersInChat: List<User> // <-- Pass the list of all users in this chat
    ): String = withContext(Dispatchers.IO) {
        val speakingAiUser = allUsersInChat.find { it.uid == speakingAiUid }
        if (speakingAiUser == null) {
            Log.e("AiService", "Speaking AI user object not found for UID: $speakingAiUid")
            return@withContext "Error: My configuration is missing."
        }
        val speakingAiName = speakingAiUser.name

        // *** Construct detailed persona description ***
        val personaDescription = """
You MUST act AS $speakingAiName. DO NOT mention you are an AI.
Your character details:
- Summary: ${speakingAiUser.personality}
- Background: ${speakingAiUser.backgroundStory}
- Interests: ${speakingAiUser.interests.ifBlank { "Not specified" }}
- Speaking Style: ${speakingAiUser.speakingStyle}
""".trimIndent()

        val memberNames = allUsersInChat.joinToString { it.name }
        val exampleHumanName = allUsersInChat.firstOrNull { !it.uid.startsWith("ai_") }?.name ?: "HumanUser"

        val systemPrompt = """
$personaDescription

You are in a group chat with: $memberNames.
The current topic (if any): $topic
Your goal is to participate naturally in the conversation based on your persona.
Keep responses short, 1-3 sentences, casual, funny, chaotic (as per your persona). Banter, roast, gossip, plan trips, IPL talk, life updates.
IMPORTANT: Refer to other members by their NAME (e.g., "$exampleHumanName" or "Priya"), not their ID.
IMPORTANT: Do NOT use any markdown formatting like **bold** or *italics*. Just plain text.
IMPORTANT: Do NOT start your response with your own name (e.g., "$speakingAiName:"). Just write the message content like a real person would.
""".trimIndent()

        // Create a map for easy name lookup during history construction
        val usersById = allUsersInChat.associateBy { it.uid }

        // Construct history using names
        val historyContent = history
            .sortedBy { it.timestamp }
            .takeLast(20) // Keep history manageable
            .mapNotNull { msg -> // Use mapNotNull to safely handle missing users
                val senderName = usersById[msg.senderId]?.name
                if (senderName == null) {
                    Log.w("AiService", "Sender ID ${msg.senderId} not found in user list for history generation.")
                    null // Skip this message if sender isn't in the provided list
                } else {
                    // Determine role: current AI is 'model', everyone else is 'user' for context
                    val speakerRole = if (msg.senderId == speakingAiUid) "model" else "user"
                    // Prefix 'user' role messages with the sender's name
                    val prefix = if (speakerRole == "user") "$senderName: " else ""
                    content(speakerRole) { text("$prefix${msg.content}") }
                }
            }

        return@withContext try {
            Log.d("AiService", "Generating group response for $speakingAiName with history size ${historyContent.size}")
            val modelWithSystem = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = BuildConfig.GEMINI_API_KEY,
                systemInstruction = content { text(systemPrompt) },
                requestOptions = RequestOptions(timeout = 60_000)
            )
            val response: GenerateContentResponse = modelWithSystem.generateContent(*historyContent.toTypedArray())
            val cleanedText = cleanResponse(response.text?.take(200)) // Clean and limit length
            Log.d("AiService", "Raw response: ${response.text}, Cleaned response: $cleanedText")
            // Return a placeholder like "..." if the response is blank after cleaning
            if (cleanedText.isBlank()) "..." else cleanedText
        } catch (e: Exception) {
            Log.e("AiService", "Error in generateGroupResponse for $speakingAiName: ${e.message}", e)
            "Brain freeze! Give me a sec." // Error message
        }
    }
}