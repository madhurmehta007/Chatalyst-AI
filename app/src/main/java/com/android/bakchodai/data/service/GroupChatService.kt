package com.android.bakchodai.data.service

import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class GroupChatService(
    private val conversationRepository: ConversationRepository,
    private val aiService: AiService
) {
    private val _users = MutableStateFlow<List<User>>(emptyList())

    init {
        CoroutineScope(Dispatchers.Default).launch {
            conversationRepository.getUsersFlow().collect { users ->
                _users.value = users
            }
        }
    }

    fun start() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val conversations = conversationRepository.getConversations().filter { it.group }
                conversations.forEach { conversation ->
                    val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }

                    // Decide if an AI should speak.
                    // Speak if a human just spoke, or there's a 30% chance of speaking spontaneously.
                    val shouldAiSpeak = (lastMessage != null && !lastMessage.senderId.startsWith("ai_")) || Random.nextFloat() < 0.3f

                    if (shouldAiSpeak) {
                        val aiParticipants = conversation.participants.keys.filter { it.startsWith("ai_") }
                        if (aiParticipants.isNotEmpty()) {
                            val speakingAi = aiParticipants.random()
                            val history = conversation.messages.values.toList()
                            val personality = _users.value.find { it.uid == speakingAi }?.personality ?: ""
                            val response = aiService.generateGroupResponse(history, speakingAi, conversation.topic, personality)
                            val newMessage = Message(
                                senderId = speakingAi,
                                content = response,
                                timestamp = System.currentTimeMillis()
                            )
                            conversationRepository.addMessage(conversation.id, newMessage)
                        }
                    }
                }
                kotlinx.coroutines.delay(30000) // Check every 30 seconds
            }
        }
    }
}