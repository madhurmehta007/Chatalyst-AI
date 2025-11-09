package com.android.chatalystai.data.repository

import android.util.Log
import com.android.chatalystai.data.local.ConversationDao
import com.android.chatalystai.data.local.ConversationEntity
import com.android.chatalystai.data.local.UserDao
import com.android.chatalystai.data.local.UserEntity
import com.android.chatalystai.data.model.Conversation
import com.android.chatalystai.data.model.Message
import com.android.chatalystai.data.model.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstConversationRepository @Inject constructor(
    private val userDao: UserDao,
    private val conversationDao: ConversationDao
) : ConversationRepository {

    private val database = Firebase.database.reference
    private val scope = CoroutineScope(Dispatchers.IO)

    private var userListener: ValueEventListener? = null

    private var currentSyncUserId: String? = null
    private var userConversationsListener: ValueEventListener? = null
    private val activeConversationListeners = mutableMapOf<String, ValueEventListener>()

    fun startSyncing() {
        val userId = Firebase.auth.currentUser?.uid ?: return
        currentSyncUserId = userId
        Log.d("Repo", "Starting Firebase sync for user $userId")

        syncUsers()
        syncUserConversations(userId)
    }

    fun stopSyncing() {
        Log.d("Repo", "Stopping Firebase sync...")
        userListener?.let { database.child("users").removeEventListener(it) }
        userListener = null

        currentSyncUserId?.let { userId ->
            userConversationsListener?.let {
                database.child("user-conversations").child(userId).removeEventListener(it)
            }
        }
        userConversationsListener = null

        activeConversationListeners.forEach { (convoId, listener) ->
            database.child("conversations").child(convoId).removeEventListener(listener)
        }
        activeConversationListeners.clear()
        currentSyncUserId = null
        Log.d("Repo", "All Firebase sync listeners stopped.")
    }

    private fun syncUsers() {
        userListener = database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteUsers = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                scope.launch {
                    val userEntities = remoteUsers.map { it.toEntity() }
                    userDao.insertAll(userEntities)
                    Log.d("Repo", "Synced ${userEntities.size} users to Room.")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Repo", "User sync cancelled", error.toException())
            }
        })
    }

    private fun syncUserConversations(userId: String) {
        val userConversationsRef = database.child("user-conversations").child(userId)

        userConversationsListener?.let { userConversationsRef.removeEventListener(it) }

        userConversationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteConvoIds = snapshot.children.mapNotNull { it.key }.toSet()
                Log.d("Repo", "User conversation list updated. Found ${remoteConvoIds.size} convos.")

                val listenersToRemove = activeConversationListeners.keys.filterNot { it in remoteConvoIds }
                listenersToRemove.forEach { convoId ->
                    val listener = activeConversationListeners.remove(convoId)
                    listener?.let { database.child("conversations").child(convoId).removeEventListener(it) }
                    Log.d("Repo", "Removed listener for deleted/left convo: $convoId")
                }

                val listenersToAdd = remoteConvoIds.filterNot { it in activeConversationListeners.keys }
                listenersToAdd.forEach { convoId ->
                    Log.d("Repo", "Adding new listener for convo: $convoId")
                    val convoRef = database.child("conversations").child(convoId)

                    val listener = object : ValueEventListener {
                        override fun onDataChange(convoSnapshot: DataSnapshot) {
                            val conversation = convoSnapshot.getValue(Conversation::class.java)
                            if (conversation != null) {
                                scope.launch {
                                    conversationDao.insertAll(listOf(conversation.toEntity()))
                                    Log.v("Repo", "Updated convo $convoId in Room (e.g., new message)")
                                }
                            } else {
                                scope.launch {
                                    conversationDao.deleteConversationById(convoId)
                                    Log.d("Repo", "Deleted convo $convoId from Room")
                                }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Repo", "Listener for convo $convoId cancelled", error.toException())
                        }
                    }
                    convoRef.addValueEventListener(listener)
                    activeConversationListeners[convoId] = listener
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Repo", "User conversations listener cancelled", error.toException())
            }
        }
        userConversationsRef.addValueEventListener(userConversationsListener!!)
    }


    override fun getConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getUsersFlow(): Flow<List<User>> {
        return userDao.getAllUsers()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getConversationFlow(id: String): Flow<Conversation?> {
        val firebaseFlow = callbackFlow<Conversation?> {
            val conversationRef = database.child("conversations").child(id)
            val listener = conversationRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val conversation = snapshot.getValue(Conversation::class.java)
                    Log.d("Repo", "Firebase listener update for conv $id (Exists: ${conversation != null})")
                    conversation?.let {
                        scope.launch { conversationDao.insertAll(listOf(it.toEntity())) }
                    }
                    trySend(conversation)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Repo", "Firebase listener cancelled for conv $id: ${error.message}")
                    trySend(null)
                    close()
                }
            })
            awaitClose {
                Log.d("Repo", "Removing Firebase listener for conv $id")
                conversationRef.removeEventListener(listener)
            }
        }.flowOn(Dispatchers.IO)
            .catch { e ->
                Log.e("Repo", "Error in firebaseFlow for conv $id", e)
                emit(null)
            }


        return firebaseFlow.flatMapLatest { firebaseConversation ->
            conversationDao.getConversationById(id)
                .map { entity ->
                    Log.v("Repo", "Room emitting for conv $id (Exists: ${entity != null})")
                    entity?.toModel()
                }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        conversationDao.getAllConversationsSuspend().map { it.toModel() }
    }

    override suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        conversationDao.getConversationByIdSuspend(id)?.toModel()
    }

    override suspend fun getUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getAllUsersSuspend().map { it.toModel() }
    }

    override suspend fun addMessage(conversationId: String, message: Message) {
        scope.launch {
            try {
                val conversation = conversationDao.getConversationByIdSuspend(conversationId)
                if (conversation != null) {
                    val updatedMessages = conversation.messages.toMutableMap()
                    updatedMessages[message.id] = message

                    conversationDao.insertAll(listOf(
                        conversation.copy(messages = updatedMessages)
                    ))
                    Log.d("Repo", "Message added to Room locally (pending): ${message.id}")
                }
            } catch (e: Exception) {
                Log.e("Repo", "Failed to add pending message to Room", e)
            }

            val firebaseMessage = message.copy(id = "", isSent = true)

            try {
                val newMessageRef = database.child("conversations/$conversationId/messages").push()
                val messageId = newMessageRef.key!!
                newMessageRef.setValue(firebaseMessage.copy(id = messageId)).await()
                Log.d("Repo", "Message sent to Firebase: ${message.content}")

                val conversation = conversationDao.getConversationByIdSuspend(conversationId)
                if (conversation != null) {
                    val updatedMessages = conversation.messages.toMutableMap()
                    updatedMessages.remove(message.id)
                    conversationDao.insertAll(listOf(
                        conversation.copy(messages = updatedMessages)
                    ))
                }

            } catch (e: Exception) {
                Log.e("Repo", "Failed to add message to Firebase, it will remain pending", e)
            }
        }
    }

    override suspend fun createGroup(name: String, participantIds: List<String>, topic: String, isGroup: Boolean): String {
        val conversationId = database.child("conversations").push().key!!
        val participants = participantIds.associateWith { true }

        val conversationName = if (!isGroup) {
            val currentUserId = Firebase.auth.currentUser?.uid ?: ""
            val otherUserId = participantIds.firstOrNull { it != currentUserId }
            otherUserId?.let { uid ->
                database.child("users").child(uid).child("name").get().await().getValue<String>()
            } ?: name
        } else {
            name
        }

        val conversation = Conversation(
            id = conversationId, name = conversationName, participants = participants, group = isGroup, topic = topic
        )

        val childUpdates = mutableMapOf<String, Any?>()
        childUpdates["/conversations/$conversationId"] = conversation
        participantIds.forEach { participantId ->
            childUpdates["/user-conversations/$participantId/$conversationId"] = true
        }

        database.updateChildren(childUpdates).await()
        Log.d("Repo", "Group created in Firebase: $conversationId")
        return conversationId
    }

    override suspend fun addUser(user: User) {
        try {
            database.child("users").child(user.uid).setValue(user).await()
            Log.d("Repo", "User added/updated in Firebase: ${user.uid}")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to add user to Firebase", e)
        }
    }

    // *** ADDED: Implementation for single user delete ***
    override suspend fun deleteUser(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                userDao.deleteUserById(userId)
                Log.d("Repo", "User $userId deleted from Room")
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete user $userId from Room", e)
            }
            try {
                database.child("users").child(userId).removeValue().await()
                Log.d("Repo", "User $userId deleted from Firebase")
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete user $userId from Firebase", e)
            }
        }
    }

    // *** ADDED: Implementation for multiple user delete ***
    override suspend fun deleteUsers(userIds: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                userDao.deleteUsersByIds(userIds)
                Log.d("Repo", "Deleted ${userIds.size} users from Room")
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete users from Room", e)
            }

            val updates = mutableMapOf<String, Any?>()
            userIds.forEach { userId ->
                updates["/users/$userId"] = null
            }

            try {
                database.updateChildren(updates).await()
                Log.d("Repo", "Deleted ${userIds.size} users from Firebase")
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete users from Firebase", e)
            }
        }
    }

    override suspend fun updateUserName(uid: String, newName: String) {
        try {
            database.child("users").child(uid).child("name").setValue(newName).await()
            Log.d("Repo", "Username updated in Firebase for: $uid")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update username in Firebase", e)
        }
    }

    override suspend fun updateMessage(conversationId: String, messageId: String, newText: String) {
        val updates = mapOf(
            "content" to newText,
            "isEdited" to true
        )
        try {
            database.child("conversations/$conversationId/messages/$messageId").updateChildren(updates).await()
            Log.d("Repo", "Message updated in Firebase: $messageId")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update message in Firebase", e)
        }
    }

    override suspend fun deleteMessages(conversationId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return

        val updates = mutableMapOf<String, Any?>()
        messageIds.forEach { msgId ->
            updates["/conversations/$conversationId/messages/$msgId"] = null
        }

        try {
            database.updateChildren(updates).await()
            Log.d("Repo", "Deleted ${messageIds.size} messages in Firebase")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to delete messages in Firebase", e)
        }
    }

    override suspend fun clearChat(conversationId: String) {
        try {
            database.child("conversations/$conversationId/messages").removeValue().await()
            Log.d("Repo", "Chat history cleared in Firebase for: $conversationId")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to clear chat in Firebase", e)
        }
    }

    override suspend fun deleteGroup(conversationId: String) {
        val currentUserId = Firebase.auth.currentUser?.uid
        var firebaseUserLinkSuccess = false
        var firebaseMainNodeSuccess = false

        if (currentUserId != null) {
            try {
                database.child("user-conversations")
                    .child(currentUserId)
                    .child(conversationId)
                    .removeValue()
                    .await()
                Log.d("Repo", "User link deleted in Firebase for group $conversationId, user $currentUserId")
                firebaseUserLinkSuccess = true
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete user link for group $conversationId in Firebase", e)
            }
        } else {
            Log.e("Repo", "Cannot delete user-conversation link for group $conversationId: User not logged in.")
        }

        val conversation = getConversation(conversationId)
        if (conversation?.group == true) {
            try {
                database.child("conversations")
                    .child(conversationId)
                    .removeValue()
                    .await()
                Log.d("Repo", "Main conversation node deleted in Firebase: $conversationId")
                firebaseMainNodeSuccess = true
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete main conversation node $conversationId in Firebase", e)
            }
        } else {
            Log.d("Repo", "Skipping main node deletion for 1-on-1 chat: $conversationId")
            firebaseMainNodeSuccess = true
        }


        try {
            scope.launch {
                conversationDao.deleteConversationById(conversationId)
                Log.d("Repo", "Group deleted locally from Room: $conversationId (Firebase link success: $firebaseUserLinkSuccess, main node success: $firebaseMainNodeSuccess)")
            }.join()
        } catch (roomError: Exception) {
            Log.e("Repo", "Failed to delete group $conversationId locally from Room", roomError)
        }

        if (!firebaseUserLinkSuccess || !firebaseMainNodeSuccess) {
            Log.w("Repo", "Firebase deletion may have been incomplete for group $conversationId.")
        }
    }

    override suspend fun updateGroupDetails(conversationId: String, newName: String, newTopic: String) {
        val updates = mapOf(
            "name" to newName,
            "topic" to newTopic
        )
        try {
            database.child("conversations/$conversationId").updateChildren(updates).await()
            Log.d("Repo", "Group details updated in Firebase: $conversationId")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update group details in Firebase", e)
            throw e
        }
    }

    override suspend fun clearAllLocalData() {
        scope.launch {
            try {
                userDao.clearAll()
                conversationDao.clearAll()
                Log.d("Repo", "Cleared all local user and conversation data from Room.")
            } catch (e: Exception) {
                Log.e("Repo", "Error clearing local data", e)
            }
        }.join()
    }

    override suspend fun setTypingIndicator(conversationId: String, userId: String, isTyping: Boolean) {
        val typingRef = database.child("conversations/$conversationId/typing/$userId")
        try {
            if (isTyping) {
                typingRef.setValue(true).await()
            } else {
                typingRef.removeValue().await()
            }
        } catch (e: Exception) {
            Log.e("Repo", "Failed to set typing indicator in Firebase", e)
        }
    }

    override suspend fun updateUserPresence(uid: String, isOnline: Boolean) {
        val presenceUpdates = mutableMapOf<String, Any>(
            "isOnline" to isOnline
        )
        if (!isOnline) {
            presenceUpdates["lastSeen"] = System.currentTimeMillis()
        }

        try {
            database.child("users").child(uid).updateChildren(presenceUpdates).await()
            Log.d("Repo", "User presence updated for $uid: isOnline=$isOnline")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update user presence in Firebase", e)
        }
    }

    override fun setOfflineOnDisconnect(uid: String) {
        val presenceRef = database.child("users").child(uid)
        presenceRef.child("isOnline").onDisconnect().setValue(false)
        presenceRef.child("lastSeen").onDisconnect().setValue(System.currentTimeMillis())
    }

    override suspend fun updateUserFcmToken(uid: String, token: String) {
        try {
            database.child("users").child(uid).child("fcmToken").setValue(token).await()
            Log.d("Repo", "FCM Token updated in Firebase for: $uid")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update FCM token in Firebase", e)
        }
    }

    override suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>, userId: String) {
        if (messageIds.isEmpty()) return
        val readTimestamp = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            try {
                val conversationEntity = conversationDao.getConversationByIdSuspend(conversationId)
                if (conversationEntity != null) {
                    val updatedMessages = conversationEntity.messages.toMutableMap()
                    var changed = false
                    messageIds.forEach { msgId ->
                        val message = updatedMessages[msgId]
                        if (message != null && !message.readBy.containsKey(userId)) {
                            val updatedReadBy = message.readBy + (userId to readTimestamp)
                            updatedMessages[msgId] = message.copy(readBy = updatedReadBy)
                            changed = true
                        }
                    }
                    if (changed) {
                        conversationDao.insertAll(listOf(conversationEntity.copy(messages = updatedMessages)))
                        Log.d("Repo", "Marked ${messageIds.size} messages as read in Room for $userId")
                    }
                }
            } catch (e: Exception) {
                Log.e("Repo", "Failed to mark messages as read in Room", e)
            }
        }

        val updates = mutableMapOf<String, Any?>()
        messageIds.forEach { msgId ->
            updates["/conversations/$conversationId/messages/$msgId/readBy/$userId"] = readTimestamp
        }

        try {
            database.updateChildren(updates).await()
            Log.d("Repo", "Marked ${messageIds.size} messages as read in Firebase for $userId")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to mark messages as read in Firebase", e)
        }
    }

    override suspend fun updateUserBio(uid: String, newBio: String) {
        try {
            database.child("users").child(uid).child("bio").setValue(newBio).await()
            Log.d("Repo", "User bio updated in Firebase for: $uid")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update user bio in Firebase", e)
        }
    }

    override suspend fun updateUserPremiumStatus(uid: String, isPremium: Boolean) {
        try {
            database.child("users").child(uid).child("isPremium").setValue(isPremium).await()
            Log.d("Repo", "User premium status updated in Firebase for: $uid")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update user premium status in Firebase", e)
        }
    }

    override suspend fun setConversationMuted(conversationId: String, mutedUntil: Long) {
        withContext(Dispatchers.IO) {
            conversationDao.updateMuteStatus(conversationId, mutedUntil)
            Log.d("Repo", "Set mute status for $conversationId until $mutedUntil")
        }
    }

    override suspend fun updateUserAvatarUrl(uid: String, newUrl: String, timestamp: Long) {
        try {
            database.child("users").child(uid).child("avatarUrl").setValue(newUrl).await()
            database.child("users").child(uid).child("avatarUploadTimestamp").setValue(timestamp).await()
            Log.d("Repo", "User avatar updated in Firebase for: $uid")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to update user avatar in Firebase", e)
        }
    }

    private fun User.toEntity() = UserEntity(
        uid, name, avatarUrl, personality,
        backgroundStory, interests, speakingStyle,
        isOnline, lastSeen,fcmToken,
        bio,premium,
        avatarUploadTimestamp
    )

    private fun UserEntity.toModel() = User(
        uid, name, avatarUrl, personality,
        backgroundStory, interests, speakingStyle,
        isOnline, lastSeen,fcmToken,
        bio,isPremium,
        avatarUploadTimestamp
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id, name, participants, messages, group, topic, typing,
        runBlocking { conversationDao.getConversationByIdSuspend(id)?.mutedUntil ?: 0L }
    )
    private fun ConversationEntity.toModel() = Conversation(
        id, name, participants, messages, isGroup, topic, typing,mutedUntil
    )
}