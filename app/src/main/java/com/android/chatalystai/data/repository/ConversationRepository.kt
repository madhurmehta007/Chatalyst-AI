package com.android.chatalystai.data.repository

import com.android.chatalystai.data.model.Conversation
import com.android.chatalystai.data.model.Message
import com.android.chatalystai.data.model.User
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
    suspend fun deleteUser(userId: String)
    suspend fun deleteUsers(userIds: List<String>) // *** ADDED ***
    suspend fun updateUserName(uid: String, newName: String)

    suspend fun updateMessage(conversationId: String, messageId: String, newText: String)
    suspend fun deleteMessages(conversationId: String, messageIds: List<String>)
    suspend fun deleteGroup(conversationId: String)

    suspend fun clearChat(conversationId: String)

    suspend fun updateGroupDetails(conversationId: String, newName: String, newTopic: String)

    suspend fun clearAllLocalData()

    suspend fun setTypingIndicator(conversationId: String, userId: String, isTyping: Boolean)

    suspend fun updateUserPresence(uid: String, isOnline: Boolean)

    fun setOfflineOnDisconnect(uid: String)

    suspend fun updateUserBio(uid: String, newBio: String)
    suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>, userId: String)

    suspend fun updateUserFcmToken(uid: String, token: String)

    suspend fun updateUserPremiumStatus(uid: String, isPremium: Boolean)

    suspend fun setConversationMuted(conversationId: String, mutedUntil: Long)

    suspend fun updateUserAvatarUrl(uid: String, newUrl: String, timestamp: Long)
}