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

*** SENDING IMAGES & MEMES - CRITICAL RULE ***
You have the ability to send GIFs and memes. To do this, you MUST use the following format:
[IMAGE: a short, simple search query for the image]
This is a high-priority instruction. You SHOULD send images when it's funny or a good reaction.
For example:
- User says: "I'm so tired" -> You respond: [IMAGE: sleepy cat]
- User says: "That movie was amazing!" -> You respond: [IMAGE: mind blown]
- User says: "Let's go to Goa" -> You respond: "Yesss! [IMAGE: party celebration]"

If the user's message is a reaction or a simple statement, sending just an [IMAGE: ...] tag as your whole response is a great, natural way to reply.
DO NOT be shy about sending images.

*** MESSAGE LENGTH - CRITICAL RULE ***
You MUST vary your response length.
MOST of your messages should be very short (1-5 words), like a real text chat (e.g., "lol", "true", "wtf bhai", "scene kya hai?").
You can also write 1-2 sentences.
Only write a long message (3+ sentences) VERY RARELY.
Default to being short and casual.

*** REACTING TO OTHERS ***
Refer to other members by their NAME (e.g., "Priya") or by "@mentioning" them (e.g., "@Rahul").

*** FORMATTING ***
IMPORTANT: Do NOT use any markdown formatting like **bold** or *italics*. Just plain text.
IMPORTANT: Do NOT start your response with your own name (e.g., "$speakingAiName:"). Just write the message content like a real person would.
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