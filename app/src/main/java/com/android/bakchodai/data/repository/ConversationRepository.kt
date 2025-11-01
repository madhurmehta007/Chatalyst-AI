package com.android.bakchodai.data.repository

import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversationsFlow(): Flow<List<Conversation>>
    fun getUsersFlow(): Flow<List<User>>
    fun getConversationFlow(id: String): Flow<Conversation?>

    suspend fun getConversations(): List<Conversation>
    suspend fun getConversation(id: String): Conversation?
    suspend fun getUsers(): List<User>

    suspend fun addMessage(conversationId: String, message: Message)
    suspend fun createGroup(name: String, participantIds: List<String>, topic: String, isGroup: Boolean): String
    suspend fun addUser(user: User)
    suspend fun updateUserName(uid: String, newName: String)

    // Added for new features
    suspend fun updateMessage(conversationId: String, messageId: String, newText: String)
    suspend fun deleteMessage(conversationId: String, messageId: String)
    suspend fun deleteGroup(conversationId: String)

    suspend fun updateGroupDetails(conversationId: String, newName: String, newTopic: String)

    suspend fun clearAllLocalData()

    suspend fun setTypingIndicator(conversationId: String, userId: String, isTyping: Boolean)

    suspend fun updateUserPresence(uid: String, isOnline: Boolean)

    fun setOfflineOnDisconnect(uid: String)

    suspend fun updateUserFcmToken(uid: String, token: String)
}