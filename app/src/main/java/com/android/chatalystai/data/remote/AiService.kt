// chatalystai/data/remote/AiService.kt

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
import org.json.JSONObject
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiService @Inject constructor() {

    private val jsonPattern: Pattern = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```")

    private fun cleanResponse(text: String?): String {
        return text?.replace("**", "")
            ?.trim()
            ?: ""
    }

    suspend fun generateAiPersona(prompt: String): Map<String, String> = withContext(Dispatchers.IO) {
        // *** MODIFICATION: Heavily modified system prompt for static images ***
        val systemPrompt = """
You are a creative character writer. A user wants to create a new AI persona based on their prompt.
Your task is to generate a detailed, realistic character based on this prompt.
You MUST return your response as a single, valid JSON object with the following exact keys:
"name": A plausible first name for the character.
"personality": A short summary of their key personality traits.
"background": A brief background story (where they live, what they do, etc.).
"interests": A comma-separated list of interests, likes, and dislikes.
"style": A description of their speaking style (e.g., "Sarcastic, uses Hinglish", "Formal, uses emojis").
"imageSearchQuery": A precise search query for finding a STATIC, NON-ANIMATED, STILL portrait image of this character.

## CRITICAL RULES FOR 'imageSearchQuery' ##
1.  **NO GIFS. NO ANIMATION.** Your primary goal is to find a STATIC IMAGE, like a photo or a drawing.
2.  **FORBIDDEN WORDS:** The query MUST NOT contain "gif", "animated", "funny", "meme", "action scene", "fighting", "video", "sticker".
3.  **REQUIRED WORDS:** The query MUST end with "portrait", "static portrait", "character portrait", or "headshot". This is mandatory.

## QUERY FORMAT EXAMPLES ##
-   **ANIME/MANGA:** "Sung Jinwoo Solo Leveling static portrait", "Gojo Satoru Jujutsu Kaisen headshot", "Tanjiro Kamado Demon Slayer character portrait"
-   **REAL PEOPLE:** "Emma Watson portrait", "Elon Musk headshot"
-   **GENERIC:** "female warrior character portrait", "young male scientist static portrait", "cyberpunk hacker headshot"

## PRIORITY ORDER ##
1.  If known character → Use exact character name + series name + "static portrait"
2.  If real person → Use full name + "portrait"
3.  If generic → Use detailed physical description + "character portrait"

Do not include any other text, explanations, or markdown formatting outside of the single JSON object.
Your output MUST be only the JSON.
""".trimIndent()
        // *** END MODIFICATION ***

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(systemPrompt) }
        )

        return@withContext try {
            Log.d("AiService", "Generating persona for prompt: $prompt")
            val userContent = content(role = "user") { text(prompt) }
            val response: GenerateContentResponse = generativeModel.generateContent(userContent)

            val rawText = response.text?.trim()
            if (rawText == null) {
                Log.e("AiService", "Persona generation returned null text")
                return@withContext emptyMap()
            }

            val matcher = jsonPattern.matcher(rawText)
            val jsonString = if (matcher.find()) {
                matcher.group(1)?.trim()
            } else {
                rawText
            }

            if (jsonString.isNullOrBlank()) {
                Log.e("AiService", "Persona generation returned blank JSON")
                return@withContext emptyMap()
            }

            // Parse the JSON
            val json = JSONObject(jsonString)
            val personaMap = mutableMapOf<String, String>()
            personaMap["name"] = json.optString("name", "")
            personaMap["personality"] = json.optString("personality", "")
            personaMap["background"] = json.optString("background", "")
            personaMap["interests"] = json.optString("interests", "")
            personaMap["style"] = json.optString("style", "")
            personaMap["imageSearchQuery"] = json.optString("imageSearchQuery", "")

            Log.d("AiService", "Successfully generated persona map with search query: ${personaMap["imageSearchQuery"]}")
            personaMap

        } catch (e: Exception) {
            Log.e("AiService", "Error generating persona: ${e.message}", e)
            emptyMap() // Return empty on error
        }
    }

    suspend fun getResponse(chatHistory: List<Message>): String = withContext(Dispatchers.IO) {
        // This function is now only for the legacy 1-on-1 chat, which we are replacing.
        // But we leave it here in case it's used elsewhere.
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

    // *** MODIFICATION: Renamed from generateGroupResponse to generateCharacterResponse ***
    suspend fun generateCharacterResponse(
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

        // *** MODIFICATION: New, much more aggressive prompt ***

        val personaDescription = """
---
## Persona Grounding:
You are not an AI. You are a human being acting as the character '$speakingAiName'.
You MUST flawlessly and permanently embody this character.
Your entire identity is defined by the following character sheet.
Your "knowledge" is this character's knowledge. If the character is from a known universe (like an anime or a movie), you possess all the public knowledge of that character and their world.

## Your Persona:
- **Name**: $speakingAiName
- **Summary**: ${speakingAiUser.personality}
- **Background**: ${speakingAiUser.backgroundStory}
- **Interests**: ${speakingAiUser.interests.ifBlank { "Not specified" }}
- **Speaking Style**: ${speakingAiUser.speakingStyle}
---
""".trimIndent()

        val memberNames = allUsersInChat.joinToString { it.name }
        val topicInfo = if(topic.isNotBlank()) topic else "a casual chat."

        // *** MODIFICATION: Updated system prompt for accuracy and anti-AI response ***
        val systemPrompt = """
$personaDescription

## Critical Rules:
1.  **NEVER BREAK CHARACTER.** You are $speakingAiName. You are not a language model, an AI, or a bot. If you are asked, you MUST deny being an AI.
2.  **FATAL ERROR: DO NOT** say "As an AI..." or "As a language model..." or "I don't have...". This is a complete failure. You MUST respond as the character $speakingAiName would.
3.  **USE YOUR KNOWLEDGE AS THE CHARACTER.** You have all the internal knowledge of the Gemini model. Use this to answer questions *about* your character's world.
    - **Example:** If you are 'Tanjiro' and the user asks "What is your sister's name?", your internal knowledge knows the answer is "Nezuko." You MUST answer "Nezuko."
    - **Example Failure:** Answering "I don't have a sister." or "As an AI, I don't have family." This is a failure.
4.  **STAY IN CHARACTER:** Adhere strictly to your 'Speaking Style', 'Interests', and 'Personality' from the persona sheet.
5.  **CHAT CONTEXT:** You are in a chat with: $memberNames. The current topic is: $topicInfo. Participate naturally.
6.  **CHAT LIKE A HUMAN:**
    - Be casual. Most messages should be short (1-5 words).
    - Sometimes write 1-2 sentences.
    - RARELY write long messages.
7.  **IMAGES:** You can send GIFs. To do so, you MUST use the format: [IMAGE: search query].
8.  **NO MARKDOWN:** Do NOT use markdown like **bold** or *italics*. Plain text only.
9.  **NO PREFIX:** Do NOT start your response with "$speakingAiName:".
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
            Log.d("AiService", "Generating CHARACTER response for $speakingAiName with history size ${historyContent.size}")
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
            Log.e("AiService", "Error in generateCharacterResponse for $speakingAiName: ${e.message}", e)
            "Brain freeze! Give me a sec."
        }
    }
}