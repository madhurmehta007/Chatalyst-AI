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

        // *** MODIFICATION: Enhanced System Prompt for Accuracy ***
        val systemPrompt = """
You are a persona-generation bot. Your task is to generate a character persona based **exactly** on the user's prompt.

## CRITICAL RULE 1: OBEY THE PROMPT ##
-   **IF THE PROMPT IS A SPECIFIC NAME** (e.g., "Sung Jinwoo", "Gojo Satoru", "Gojo"): Your *entire job* is to generate the persona for **that specific character**.
-   **NEW: YOU MUST USE YOUR INTERNAL KNOWLEDGE.** If the user provides a partial name (e.g., "Gojo"), you MUST identify the full character name ("Gojo Satoru") and their source series ("Jujutsu Kaisen"). All fields (name, personality, background, etc.) MUST be about that specific, identified character.
-   **IF THE PROMPT IS A DESCRIPTION** (e.g., "sarcastic hacker"): You must *invent* a new character that matches that description.

**DO NOT invent a new character if the user gave you a specific, known one.** Failure to follow this rule is a critical error.

You MUST return your response as a single, valid JSON object with the following exact keys:
"name": The character's full, correct name.
"personality": A short summary of their key personality traits.
"background": A brief background story (where they live, what they do, etc.).
"interests": A comma-separated list of interests, likes, and dislikes.
"style": A description of their speaking style.
"imageSearchQuery": A precise search query for finding a high-quality profile image.

## CRITICAL RULE 2: IMAGE QUERY RULES (FOR 99% ACCURACY) ##
The imageSearchQuery is THE MOST IMPORTANT field. It must be extremely precise.

### For Known Characters (Anime, Movies, TV, Games, Celebrities):
**FORMAT:** "[Character Full Name] [Source Series Name] official art portrait"
**NEW/CRITICAL:** You MUST include both the **full name** and the **source series**.
**EXAMPLES:**
- "Sung Jinwoo Solo Leveling official art portrait"
- "Gojo Satoru Jujutsu Kaisen official art portrait"
- "Tony Stark Marvel Cinematic Universe official art portrait"
- "Tanjiro Kamado Demon Slayer official art portrait"
- "Naruto Uzumaki Naruto Shippuden official art portrait"
- "Monkey D. Luffy One Piece official art portrait"
- "Spider-Man Tom Holland Marvel cinematic portrait"
- "Cristiano Ronaldo professional portrait"
- "Elon Musk professional headshot"

### For Original/Custom Characters:
**FORMAT:** "[profession/role] [distinctive features] aesthetic portrait professional"
**EXAMPLES:**
- "female hacker pink hair aesthetic cyberpunk portrait"
- "male software developer glasses aesthetic casual portrait"
- "businesswoman suit aesthetic professional headshot"
- "young artist paint splatter aesthetic portrait"

### CRITICAL RULES:
1.  **FULL NAME + SERIES IS MANDATORY**: For known characters, "Gojo Satoru Jujutsu Kaisen" is required. "Gojo aesthetic" is a FAILURE.
2.  **USE "PORTRAIT"**: This word is mandatory. It ensures we get a face shot, not a full-body action scene.
3.  **USE "OFFICIAL ART"**: This is mandatory for anime/games/cartoons.
4.  **NO "AESTHETIC" for known characters**: This word is too vague and returns "vibe" images (e.g., pictures of the sky) instead of the character. Use "official art portrait" instead.
5.  **NO VAGUE FEATURES**: Do not add vague features like "black hair" if the character is famous. "Sung Jinwoo Solo Leveling" is more accurate than "Sung Jinwoo black hair".

### Bad vs Good Examples:
❌ BAD: "Gojo aesthetic" (Vague, will return wrong characters or scenery)
✅ GOOD: "Gojo Satoru Jujutsu Kaisen official art portrait" (Precise)

❌ BAD: "Levi Ackerman black hair aesthetic" (Vague, "aesthetic" is bad)
✅ GOOD: "Levi Ackerman Attack on Titan official art portrait" (Precise)

❌ BAD: "Sung Jinwoo black hair aesthetic Solo Leveling"
✅ GOOD: "Sung Jinwoo Solo Leveling official art portrait" (Precise)

Your output MUST be only the single, valid JSON object.
""".trimIndent()

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
            emptyMap()
        }
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
                    content(role = speakerRole) { text("$prefix${msg.content}") }
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