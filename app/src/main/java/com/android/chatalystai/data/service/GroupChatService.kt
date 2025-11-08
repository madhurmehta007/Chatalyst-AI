package com.android.chatalystai.data.service

import android.util.Log
import com.android.chatalystai.data.model.Message
import com.android.chatalystai.data.model.MessageType
import com.android.chatalystai.data.model.User
import com.android.chatalystai.data.remote.AiService
import com.android.chatalystai.data.remote.GiphyService
import com.android.chatalystai.data.remote.GoogleImageService
import com.android.chatalystai.data.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.regex.Pattern
import kotlin.random.Random

class GroupChatService(
    private val conversationRepository: ConversationRepository,
    private val aiService: AiService,
    private val googleImageService: GoogleImageService,
    private val giphyService: GiphyService
) {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serviceJob: Job? = null

    private val imagePattern: Pattern = Pattern.compile("\\[IMAGE:(.*?)]")

    init {
        serviceScope.launch {
            Log.d("GroupChatService", "Starting user collection flow")
            conversationRepository.getUsersFlow().collectLatest { users ->
                Log.d("GroupChatService", "Received ${users.size} users (incl. human)")
                _users.value = users
            }
        }
    }

    fun start() {
        if (serviceJob?.isActive == true) {
            Log.d("GroupChatService", "Service loop already running.")
            return
        }
        serviceJob = serviceScope.launch {
            Log.d("GroupChatService", "Service loop started")
            while (isActive) {
                try {
                    val conversations =
                        conversationRepository.getConversations().filter { it.group }
                    Log.d("GroupChatService", "Checking ${conversations.size} group conversations")

                    val currentUsers = _users.value
                    if (currentUsers.isEmpty()) {
                        Log.w("GroupChatService", "User list is empty, delaying check.")
                        delay(10000)
                        continue
                    }

                    conversations.forEach { conversation ->
                        if (!isActive) return@forEach

                        val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }
                        val lastTimestamp = lastMessage?.timestamp ?: 0L
                        val currentTime = System.currentTimeMillis()

                        val timeSinceLastMessage = currentTime - lastTimestamp
                        val isChatInactive = timeSinceLastMessage > 150_000 && lastTimestamp != 0L

                        if (isChatInactive) {
                            Log.d(
                                "GroupChatService",
                                "Conv ${conversation.id}: Skipping AI response, chat inactive for ${timeSinceLastMessage / 1000}s."
                            )
                            return@forEach
                        }

                        val aiParticipantsInChat = currentUsers.filter {
                            it.uid.startsWith("ai_") && conversation.participants.containsKey(it.uid)
                        }
                        if (aiParticipantsInChat.isEmpty()) return@forEach

                        var mentionedAi: User? = null
                        if (lastMessage != null && !lastMessage.senderId.startsWith("ai_")) {
                            mentionedAi = aiParticipantsInChat.firstOrNull { aiUser ->
                                lastMessage.content.contains(aiUser.name, ignoreCase = true)
                            }
                        }


                        val humanSpokeRecently =
                            lastMessage != null && !lastMessage.senderId.startsWith("ai_")
                        val aiWasMentioned = mentionedAi != null
                        val randomChance = Random.nextFloat() < 0.5f

                        val shouldAiSpeak =
                            (aiWasMentioned && lastMessage?.senderId != mentionedAi?.uid) ||
                                    humanSpokeRecently ||
                                    randomChance

                        Log.v(
                            "GroupChatService",
                            "Conv ${conversation.id}: Should AI speak? $shouldAiSpeak (Mentioned: $aiWasMentioned, Human spoke: $humanSpokeRecently, Random: $randomChance)"
                        )

                        if (shouldAiSpeak) {
                            val participantIds = conversation.participants.keys
                            val usersInThisChat = currentUsers.filter { it.uid in participantIds }

                            if (usersInThisChat.isEmpty()) {
                                Log.w(
                                    "GroupChatService",
                                    "User data is incomplete for conv ${conversation.id}. Skipping."
                                )
                                return@forEach
                            }

                            val speakingAiUid: String
                            if (aiWasMentioned) {
                                speakingAiUid = mentionedAi.uid
                                Log.d(
                                    "GroupChatService",
                                    "Forcing response from mentioned AI: ${mentionedAi.name}"
                                )
                            } else {
                                val aiParticipantIds = aiParticipantsInChat.map { it.uid }
                                val potentialSpeakers =
                                    if (lastMessage?.senderId?.startsWith("ai_") == true) {
                                        aiParticipantIds.filter { it != lastMessage.senderId }
                                    } else {
                                        aiParticipantIds
                                    }
                                speakingAiUid =
                                    potentialSpeakers.ifEmpty { aiParticipantIds }.random()
                            }

                            val speakingAiUser = usersInThisChat.find { it.uid == speakingAiUid }
                            val history = conversation.messages.values.toList()

                            Log.d(
                                "GroupChatService",
                                "AI ${speakingAiUser?.name ?: speakingAiUid} speaking."
                            )

                            conversationRepository.setTypingIndicator(
                                conversation.id,
                                speakingAiUid,
                                true
                            )

                            val rawResponse = aiService.generateCharacterResponse(
                                history,
                                speakingAiUid,
                                conversation.topic,
                                usersInThisChat
                            )

                            conversationRepository.setTypingIndicator(
                                conversation.id,
                                speakingAiUid,
                                false
                            )

                            val matcher = imagePattern.matcher(rawResponse)

                            if (matcher.find()) {
                                val searchQuery = matcher.group(1)?.trim()
                                val textPart = rawResponse.replace(matcher.group(0)!!, "").trim()

                                if (textPart.isNotBlank() && !textPart.startsWith("Brain freeze")) {
                                    val textMessage = Message(
                                        id = UUID.randomUUID().toString(),
                                        senderId = speakingAiUid,
                                        content = textPart.replace("**", "").trim(),
                                        timestamp = System.currentTimeMillis(),
                                        isSent = false
                                    )
                                    conversationRepository.addMessage(conversation.id, textMessage)
                                    delay(1000)
                                }

                                if (!searchQuery.isNullOrBlank()) {
                                    Log.d(
                                        "GroupChatService",
                                        "AI requested image for query: '$searchQuery'"
                                    )
                                    var imageUrl = googleImageService.searchImage(searchQuery)
                                    if (imageUrl.isNullOrBlank()) {
                                        Log.w("GroupChatService", "Google Image Search failed for '$searchQuery'. Trying Giphy fallback.")
                                        imageUrl = giphyService.searchGif(searchQuery)
                                    }

                                    if (imageUrl != null) {
                                        val imageMessage = Message(
                                            id = UUID.randomUUID().toString(),
                                            senderId = speakingAiUid,
                                            content = imageUrl,
                                            type = MessageType.IMAGE,
                                            timestamp = System.currentTimeMillis(),
                                            isSent = false
                                        )
                                        conversationRepository.addMessage(
                                            conversation.id,
                                            imageMessage
                                        )
                                    } else {
                                        Log.e( // *** MODIFIED: Changed from 'w' to 'e' ***
                                            "GroupChatService",
                                            "All image services (Google, Giphy) failed for '$searchQuery'"
                                        )
                                    }
                                }
                            } else {
                                val cleanedResponse = rawResponse.replace("**", "").trim()
                                if (cleanedResponse.isNotBlank() && cleanedResponse != "..." && !cleanedResponse.startsWith(
                                        "Brain freeze"
                                    )
                                ) {
                                    Log.d(
                                        "GroupChatService",
                                        "AI ${speakingAiUser?.name} response: $cleanedResponse"
                                    )
                                    val newMessage = Message(
                                        senderId = speakingAiUid,
                                        content = cleanedResponse,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    conversationRepository.addMessage(conversation.id, newMessage)
                                } else {
                                    Log.d(
                                        "GroupChatService",
                                        "AI ${speakingAiUser?.name} generated blank response, skipping."
                                    )
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e("GroupChatService", "Error in service loop", e)
                }
                delay(20000)
            }
            Log.d("GroupChatService", "Service loop finished.")
        }
    }

    fun stop() {
        Log.d("GroupChatService", "Stopping service loop job.")
        serviceJob?.cancel()
        serviceJob = null
    }
}