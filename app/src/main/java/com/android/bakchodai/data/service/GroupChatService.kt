package com.android.bakchodai.data.service

import android.util.Log
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.MessageType
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.remote.GiphyService
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
import java.util.regex.Pattern
import kotlin.random.Random

// MODIFIED: Update constructor
class GroupChatService(
    private val conversationRepository: ConversationRepository,
    private val aiService: AiService,
    private val giphyService: GiphyService // NEW: Add GiphyService
) {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    // Use SupervisorJob so one failure doesn't cancel the whole scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serviceJob: Job? = null // To manage the main loop job

    // NEW: Regex pattern to find the image tag
    private val imagePattern: Pattern = Pattern.compile("\\[IMAGE:(.*?)]")

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

                        // --- START @MENTION LOGIC ---
                        val aiParticipantsInChat = currentUsers.filter {
                            it.uid.startsWith("ai_") && conversation.participants.containsKey(it.uid)
                        }
                        if (aiParticipantsInChat.isEmpty()) return@forEach // No AIs in this chat

                        var mentionedAi: User? = null
                        if (lastMessage != null && !lastMessage.senderId.startsWith("ai_")) {
                            mentionedAi = aiParticipantsInChat.firstOrNull { aiUser ->
                                lastMessage.content.contains(aiUser.name, ignoreCase = true)
                            }
                        }
                        // --- END @MENTION LOGIC ---


                        // --- DECIDE IF AI SHOULD SPEAK ---
                        val humanSpokeRecently = lastMessage != null && !lastMessage.senderId.startsWith("ai_")
                        val aiWasMentioned = mentionedAi != null
                        val randomChance = Random.nextFloat() < 0.5f // 50% chance if no human spoke

                        val shouldAiSpeak = (aiWasMentioned && lastMessage?.senderId != mentionedAi?.uid) ||
                                humanSpokeRecently ||
                                randomChance

                        Log.v("GroupChatService", "Conv ${conversation.id}: Should AI speak? $shouldAiSpeak (Mentioned: $aiWasMentioned, Human spoke: $humanSpokeRecently, Random: $randomChance)")

                        if (shouldAiSpeak) {
                            val participantIds = conversation.participants.keys
                            val usersInThisChat = currentUsers.filter { it.uid in participantIds }

                            if (usersInThisChat.isEmpty()) {
                                Log.w("GroupChatService", "User data is incomplete for conv ${conversation.id}. Skipping.")
                                return@forEach
                            }

                            val speakingAiUid: String
                            if (aiWasMentioned) {
                                speakingAiUid = mentionedAi!!.uid
                                Log.d("GroupChatService", "Forcing response from mentioned AI: ${mentionedAi.name}")
                            } else {
                                val aiParticipantIds = aiParticipantsInChat.map { it.uid }
                                val potentialSpeakers = if (lastMessage?.senderId?.startsWith("ai_") == true) {
                                    aiParticipantIds.filter { it != lastMessage.senderId }
                                } else {
                                    aiParticipantIds
                                }
                                speakingAiUid = potentialSpeakers.ifEmpty { aiParticipantIds }.random()
                            }

                            val speakingAiUser = usersInThisChat.find { it.uid == speakingAiUid }
                            val history = conversation.messages.values.toList()

                            Log.d("GroupChatService", "AI ${speakingAiUser?.name ?: speakingAiUid} speaking.")

                            conversationRepository.setTypingIndicator(conversation.id, speakingAiUid, true)

                            // Get the raw response from AiService
                            val rawResponse = aiService.generateGroupResponse(
                                history,
                                speakingAiUid,
                                conversation.topic,
                                usersInThisChat
                            )

                            conversationRepository.setTypingIndicator(conversation.id, speakingAiUid, false)

                            // --- NEW IMAGE HANDLING LOGIC ---
                            val matcher = imagePattern.matcher(rawResponse)

                            if (matcher.find()) {
                                // An image tag was found!
                                val searchQuery = matcher.group(1)?.trim()
                                // Get the text part, if any, by removing the image tag
                                val textPart = rawResponse.replace(matcher.group(0)!!, "").trim()

                                // 1. If there's any text besides the image tag, send it first
                                if (textPart.isNotBlank() && !textPart.startsWith("Brain freeze")) {
                                    val textMessage = Message(
                                        senderId = speakingAiUid,
                                        content = textPart.replace("**", "").trim(),
                                        timestamp = System.currentTimeMillis()
                                    )
                                    conversationRepository.addMessage(conversation.id, textMessage)
                                    delay(1000) // Small delay to make it look like two actions
                                }

                                // 2. Now search for the GIF and send it
                                if (!searchQuery.isNullOrBlank()) {
                                    Log.d("GroupChatService", "AI requested image for query: '$searchQuery'")
                                    val imageUrl = giphyService.searchGif(searchQuery)
                                    if (imageUrl != null) {
                                        val imageMessage = Message(
                                            senderId = speakingAiUid,
                                            content = imageUrl, // The content is the URL
                                            type = MessageType.IMAGE, // Set the type to IMAGE
                                            timestamp = System.currentTimeMillis()
                                        )
                                        conversationRepository.addMessage(conversation.id, imageMessage)
                                    } else {
                                        Log.w("GroupChatService", "Giphy returned no URL for '$searchQuery'")
                                    }
                                }
                            } else {
                                // --- THIS IS THE OLD LOGIC FOR TEXT-ONLY MESSAGES ---
                                val cleanedResponse = rawResponse.replace("**", "").trim()
                                if (cleanedResponse.isNotBlank() && cleanedResponse != "..." && !cleanedResponse.startsWith("Brain freeze")) {
                                    Log.d("GroupChatService", "AI ${speakingAiUser?.name} response: $cleanedResponse")
                                    val newMessage = Message(
                                        senderId = speakingAiUid,
                                        content = cleanedResponse,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    conversationRepository.addMessage(conversation.id, newMessage)
                                } else {
                                    Log.d("GroupChatService", "AI ${speakingAiUser?.name} generated blank response, skipping.")
                                }
                            }
                            // --- END NEW LOGIC ---
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