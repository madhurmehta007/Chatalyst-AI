package com.android.bakchodai.data.service

import android.util.Log
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

// No Hilt annotations here, created manually in Application
class GroupChatService(
    private val conversationRepository: ConversationRepository,
    private val aiService: AiService
) {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    // Use SupervisorJob so one failure doesn't cancel the whole scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serviceJob: Job? = null // To manage the main loop job

    init {
        // Collect users continuously in the service scope
        serviceScope.launch {
            Log.d("GroupChatService", "Starting user collection flow")
            // Make sure getUsersFlow() provides ALL users, including non-AI
            conversationRepository.getUsersFlow().collectLatest { users ->
                Log.d("GroupChatService", "Received ${users.size} users (incl. human)")
                _users.value = users
            }
        }
    }

    fun start() {
        // Ensure only one service loop runs
        if (serviceJob?.isActive == true) {
            Log.d("GroupChatService", "Service loop already running.")
            return
        }
        serviceJob = serviceScope.launch {
            Log.d("GroupChatService", "Service loop started")
            while (isActive) { // Use isActive to allow cancellation
                try {
                    // Fetch conversations using the suspend function (reads from Room)
                    val conversations = conversationRepository.getConversations().filter { it.group }
                    Log.d("GroupChatService", "Checking ${conversations.size} group conversations")

                    val currentUsers = _users.value // Get the latest user list
                    if (currentUsers.isEmpty()) {
                        Log.w("GroupChatService", "User list is empty, delaying check.")
                        delay(10000) // Wait 10 seconds if users aren't loaded
                        continue // Skip this iteration
                    }

                    conversations.forEach { conversation ->
                        // Check if the scope is still active before processing each conversation
                        if (!isActive) return@forEach

                        val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }
                        val lastTimestamp = lastMessage?.timestamp ?: 0L
                        val currentTime = System.currentTimeMillis()

                        // *** AI TIMEOUT LOGIC ***
                        val timeSinceLastMessage = currentTime - lastTimestamp
                        // Check if inactive for more than 200 seconds (and it's not a brand new chat)
                        val isChatInactive = timeSinceLastMessage > 150_000 && lastTimestamp != 0L

                        if (isChatInactive) {
                            Log.d("GroupChatService", "Conv ${conversation.id}: Skipping AI response, chat inactive for ${timeSinceLastMessage / 1000}s.")
                            return@forEach // Skip to the next conversation
                        }

                        // Decide if an AI should speak (only if chat is active)
                        val humanSpokeRecently = lastMessage != null && !lastMessage.senderId.startsWith("ai_")
                        // Lower random chance to reduce chattiness in quiet groups
                        val randomChance = Random.nextFloat() < 0.5f // 50% chance if no human spoke

                        val shouldAiSpeak = humanSpokeRecently || randomChance

                        Log.v("GroupChatService", "Conv ${conversation.id}: Should AI speak? $shouldAiSpeak (Human spoke recently: $humanSpokeRecently, Random chance: $randomChance, Inactive: $isChatInactive)")

                        if (shouldAiSpeak) {
                            val aiParticipants = conversation.participants.keys.filter { it.startsWith("ai_") }
                            if (aiParticipants.isNotEmpty()) {

                                // Get ALL users in this specific chat from the collected list
                                val participantIds = conversation.participants.keys
                                val usersInThisChat = currentUsers.filter { it.uid in participantIds }

                                if (usersInThisChat.isEmpty() || usersInThisChat.size < participantIds.size) {
                                    Log.w("GroupChatService", "User data might be incomplete for conv ${conversation.id}. Found ${usersInThisChat.size}/${participantIds.size} participants.")
                                    // Continue if at least AI participants are found, otherwise skip
                                    if (usersInThisChat.none { it.uid.startsWith("ai_") }) return@forEach
                                }

                                // Pick an AI that didn't speak last, if possible
                                val potentialSpeakers = if (lastMessage?.senderId?.startsWith("ai_") == true) {
                                    aiParticipants.filter { it != lastMessage.senderId }
                                } else {
                                    aiParticipants
                                }
                                // If filtering left no options (e.g., only one AI), fall back to the original list
                                val speakingAiUid = potentialSpeakers.ifEmpty { aiParticipants }.random()

                                val speakingAiUser = usersInThisChat.find { it.uid == speakingAiUid }
                                val personality = speakingAiUser?.personality ?: "" // Use found user's personality
                                val history = conversation.messages.values.toList()

                                Log.d("GroupChatService", "AI ${speakingAiUser?.name ?: speakingAiUid} speaking. Personality: '$personality'")

                                // --- MODIFIED: Typing Indicator Logic ---
                                // 1. SET TYPING TO TRUE
                                conversationRepository.setTypingIndicator(conversation.id, speakingAiUid, true)

                                val personalityForCall = speakingAiUser?.personality
                                val response = aiService.generateGroupResponse(
                                    history,
                                    speakingAiUid,
                                    conversation.topic,
                                    usersInThisChat
                                )

                                // 2. SET TYPING TO FALSE (after response is generated)
                                conversationRepository.setTypingIndicator(conversation.id, speakingAiUid, false)
                                // --- End Modification ---

                                // Check response before saving
                                if (response.isNotBlank() && response != "..." && !response.startsWith("Brain freeze") && !response.startsWith("Uh, pass")) {
                                    Log.d("GroupChatService", "AI ${speakingAiUser?.name ?: speakingAiUid} response: $response")
                                    val newMessage = Message(
                                        senderId = speakingAiUid,
                                        content = response, // Already cleaned by AiService
                                        timestamp = System.currentTimeMillis()
                                    )
                                    // Add message to Firebase (will sync back to Room via listener)
                                    conversationRepository.addMessage(conversation.id, newMessage)
                                } else {
                                    Log.d("GroupChatService", "AI ${speakingAiUser?.name ?: speakingAiUid} generated blank/default/error response, skipping.")
                                }
                            }
                        }
                    } // End conversations.forEach

                } catch (e: Exception) {
                    Log.e("GroupChatService", "Error in service loop", e)
                    // Avoid crashing the service, wait before retrying
                }
                delay(20000) // Check conversations every 20 seconds
            }
            Log.d("GroupChatService", "Service loop finished.")
        }
    }

    fun stop() {
        Log.d("GroupChatService", "Stopping service loop job.")
        serviceJob?.cancel() // Cancel the loop job
        serviceJob = null
    }
}