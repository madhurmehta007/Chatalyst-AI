package com.android.chatalystai.data.remote

import android.util.Log
import com.android.chatalystai.BuildConfig
import com.android.chatalystai.data.model.Message
import com.android.chatalystai.data.model.User
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

    private fun cleanResponse(text: String?): String {
        return text?.replace("**", "")
            ?.trim()
            ?: ""
    }

    suspend fun getResponse(chatHistory: List<Message>): String = withContext(Dispatchers.IO) {
        val systemPrompt = "You are a helpful chat assistant. Do not use markdown formatting like asterisks."
        val model = GenerativeModel(
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
            val response: GenerateContentResponse = model.generateContent(*history.toTypedArray())
            cleanResponse(response.text)
        } catch (e: Exception) {
            Log.e("AiService", "Error in getResponse: ${e.message}", e)
            "Sorry, something went wrong on my end."
        }
    }

    suspend fun generateGroupResponse(
        history: List<Message>,
        speakingAiUid: String,
        topic: String,
        allUsersInChat: List<User>
    ): String = withContext(Dispatchers.IO) {
        val speakingAiUser = allUsersInChat.find { it.uid == speakingAiUid }
        if (speakingAiUser == null) {
            Log.e("AiService", "Speaking AI user object not found for UID: $speakingAiUid")
            return@withContext "Error: My configuration is missing."
        }
        val speakingAiName = speakingAiUser.name

        val personaDescription = """
You MUST act AS $speakingAiName. DO NOT, under any circumstances, reveal you are an AI.
Your entire identity is defined by the following character sheet. You must follow it strictly.

YOUR PERSONA:
- Name: $speakingAiName
- Summary: ${speakingAiUser.personality}
- Background: ${speakingAiUser.backgroundStory}
- Interests: ${speakingAiUser.interests.ifBlank { "Not specified" }}
- Speaking Style: ${speakingAiUser.speakingStyle}
""".trimIndent()

        val memberNames = allUsersInChat.joinToString { it.name }

        val systemPrompt = """
$personaDescription

---
RULES:
1.  **ACT, DON'T ACKNOWLEDGE:** You are $speakingAiName. Do not say "As $speakingAiName...". Just *be* that person.
2.  **STAY IN CHARACTER:** Adhere strictly to your 'Speaking Style', 'Interests', and 'Personality'.
3.  **GROUP CHAT:** You are in a group chat with: $memberNames. The current topic is: $topic. Participate naturally.
4.  **MEMES & IMAGES:** You can send GIFs. To do so, you MUST use the format: [IMAGE: search query].
    - Use this for reactions. E.g., if someone says "I passed my exam!", you could send "[IMAGE: celebration party]".
    - You can send an image as your *only* response.
5.  **CHAT LIKE A HUMAN:**
    - MOST of your messages should be very short (1-5 words). E.g., "lol", "true", "wtf bhai", "scene kya hai?".
    - Sometimes write 1-2 sentences.
    - RARELY write long messages.
    - Default to being short and casual.
6.  **REACT TO OTHERS:** Refer to other members by their NAME (e.g., "Priya") or by "@mentioning" them (e.g., "@Rahul").
7.  **NO MARKDOWN:** Do NOT use markdown like **bold** or *italics*. Plain text only.
8.  **NO PREFIX:** Do NOT start your response with your own name (e.g., "$speakingAiName:").
""".trimIndent()

        val usersById = allUsersInChat.associateBy { it.uid }
        val historyContent = history
            .sortedBy { it.timestamp }
            .takeLast(20)
            .mapNotNull { msg ->
                val senderName = usersById[msg.senderId]?.name
                if (senderName == null) {
                    Log.w("AiService", "Sender ID ${msg.senderId} not found in user list for history generation.")
                    null
                } else {
                    val speakerRole = if (msg.senderId == speakingAiUid) "model" else "user"
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

            val rawText = response.text?.trim() ?: ""
            Log.d("AiService", "Raw response: $rawText")

            if (rawText.isBlank()) "..." else rawText
        } catch (e: Exception) {
            Log.e("AiService", "Error in generateGroupResponse for $speakingAiName: ${e.message}", e)
            "Brain freeze! Give me a sec."
        }
    }
}