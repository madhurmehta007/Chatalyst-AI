package com.android.bakchodai.data.repository

import android.util.Log
import android.widget.Toast
import com.android.bakchodai.data.local.ConversationDao
import com.android.bakchodai.data.local.ConversationEntity
import com.android.bakchodai.data.local.UserDao
import com.android.bakchodai.data.local.UserEntity
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi // Import
import kotlinx.coroutines.channels.awaitClose // Import
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow // Import
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged // Import
import kotlinx.coroutines.flow.flatMapLatest // Import
import kotlinx.coroutines.flow.flowOn // Import
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart // Import
import kotlinx.coroutines.launch
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
    private val scope = CoroutineScope(Dispatchers.IO) // Use IO dispatcher

    private var userListener: ValueEventListener? = null

    // --- Syncing Logic ---
    fun startSyncing() {
        Log.d("Repo", "Starting Firebase sync...")
        syncUsers()
    }

    fun stopSyncing() {
        Log.d("Repo", "Stopping Firebase sync...")
        userListener?.let { database.child("users").removeEventListener(it) }
    }

    // syncUsers remains the same
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


    // --- Flow-based methods ---
    override fun getConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
            .map { entities -> entities.map { it.toModel() } }
            .onStart { // Fetch initial list when flow starts
                fetchAndUpdateUserConversations()
            }
            .flowOn(Dispatchers.IO)
    }

    // Helper to fetch user's conversation list and update Room
    private suspend fun fetchAndUpdateUserConversations() {
        val userId = Firebase.auth.currentUser?.uid ?: return
        try {
            Log.d("Repo", "Fetching initial conversation list for user $userId")
            val userConvoIdsSnapshot = database.child("user-conversations").child(userId).get().await()
            val convoIds = userConvoIdsSnapshot.children.mapNotNull { it.key }
            Log.d("Repo", "Found ${convoIds.size} conversation IDs")

            val remoteConversations = convoIds.mapNotNull { id ->
                database.child("conversations").child(id).get().await().getValue(Conversation::class.java)
            }
            val conversationEntities = remoteConversations.map { it.toEntity() }
            conversationDao.insertAll(conversationEntities)
            Log.d("Repo", "Inserted initial ${conversationEntities.size} conversations into Room.")
        } catch (e: Exception) {
            Log.e("Repo", "Error fetching initial conversation list", e)
        }
    }


    override fun getUsersFlow(): Flow<List<User>> {
        return userDao.getAllUsers()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getConversationFlow(id: String): Flow<Conversation?> {
        // Combine Room Flow with a direct Firebase Listener
        val firebaseFlow = callbackFlow<Conversation?> {
            val conversationRef = database.child("conversations").child(id)
            val listener = conversationRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val conversation = snapshot.getValue(Conversation::class.java)
                    Log.d("Repo", "Firebase listener update for conv $id (Exists: ${conversation != null})")
                    conversation?.let {
                        // Update Room in the background
                        scope.launch { conversationDao.insertAll(listOf(it.toEntity())) }
                    }
                    // Try sending the latest value (conversation or null if deleted)
                    trySend(conversation)
                }

                override fun onCancelled(error: DatabaseError) {
                    // *** THE FIX: Handle cancellation gracefully ***
                    Log.e("Repo", "Firebase listener cancelled for conv $id: ${error.message}")
                    // Don't close with error. Instead, emit null to signal data is gone/inaccessible.
                    trySend(null)
                    // Optionally, you could close without an error if appropriate,
                    // but emitting null explicitly handles the deleted state for the UI.
                    close()
                }
            })
            // Remove listener when the flow collector cancels
            awaitClose {
                Log.d("Repo", "Removing Firebase listener for conv $id")
                conversationRef.removeEventListener(listener)
            }
        }.flowOn(Dispatchers.IO) // Perform Firebase operations off the main thread
            .catch { e -> // Catch potential exceptions during flow creation/emission
                Log.e("Repo", "Error in firebaseFlow for conv $id", e)
                emit(null) // Emit null on error
            }


        // Use flatMapLatest to switch to the Room flow.
        // The Room flow will reflect updates made by the firebaseFlow listener.
        return firebaseFlow.flatMapLatest { firebaseConversation ->
            // If firebaseConversation is null (deleted or permission denied),
            // flatMapLatest will switch to a Room flow that should also emit null.
            conversationDao.getConversationById(id)
                .map { entity ->
                    // Log what Room is providing
                    Log.v("Repo", "Room emitting for conv $id (Exists: ${entity != null})")
                    entity?.toModel()
                }
        }.distinctUntilChanged() // Prevent emitting the same state consecutively
            .flowOn(Dispatchers.IO) // Ensure Room access is off main thread
    }

    // --- Suspend functions (Read from Room) ---
    override suspend fun getConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        conversationDao.getAllConversationsSuspend().map { it.toModel() }
    }

    override suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        conversationDao.getConversationByIdSuspend(id)?.toModel()
    }

    override suspend fun getUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getAllUsersSuspend().map { it.toModel() }
    }

    // --- Write/Update methods (Write ONLY to Firebase) ---
    override suspend fun addMessage(conversationId: String, message: Message) {
        val messageId = database.child("conversations/$conversationId/messages").push().key ?: return
        try {
            database.child("conversations/$conversationId/messages/$messageId")
                .setValue(message.copy(id = messageId))
                .await()
            Log.d("Repo", "Message added to Firebase: ${message.content}")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to add message to Firebase", e)
        }
    }

    override suspend fun createGroup(name: String, participantIds: List<String>, topic: String): String {
        // ... (createGroup implementation remains the same) ...
        val conversationId = database.child("conversations").push().key!!
        val participants = participantIds.associateWith { true }
        val isGroup = participantIds.size > 2 || topic.isNotBlank()

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

    override suspend fun deleteMessage(conversationId: String, messageId: String) {
        try {
            database.child("conversations/$conversationId/messages/$messageId").removeValue().await()
            Log.d("Repo", "Message deleted in Firebase: $messageId")
        } catch (e: Exception) {
            Log.e("Repo", "Failed to delete message in Firebase", e)
        }
    }

    override suspend fun deleteGroup(conversationId: String) {
        val currentUserId = Firebase.auth.currentUser?.uid
        var firebaseUserLinkSuccess = false
        var firebaseMainNodeSuccess = false

        // 1. Attempt to delete the user's link first
        if (currentUserId != null) {
            try {
                database.child("user-conversations")
                    .child(currentUserId)
                    .child(conversationId)
                    .removeValue() // Use removeValue for single path
                    .await()
                Log.d("Repo", "User link deleted in Firebase for group $conversationId, user $currentUserId")
                firebaseUserLinkSuccess = true
            } catch (e: Exception) {
                Log.e("Repo", "Failed to delete user link for group $conversationId in Firebase", e)
                // Log specific Firebase error if possible
                if (e is com.google.firebase.database.DatabaseException) {
                    Log.e("Repo", "Firebase DatabaseException on user link delete: ${e.message}")
                }
                // Continue to attempt deleting the main node and local data
            }
        } else {
            Log.e("Repo", "Cannot delete user-conversation link for group $conversationId: User not logged in.")
        }

        // 2. Attempt to delete the main conversation node
        try {
            database.child("conversations")
                .child(conversationId)
                .removeValue() // Use removeValue for single path
                .await()
            Log.d("Repo", "Main conversation node deleted in Firebase: $conversationId")
            firebaseMainNodeSuccess = true
        } catch (e: Exception) {
            Log.e("Repo", "Failed to delete main conversation node $conversationId in Firebase", e)
            if (e is com.google.firebase.database.DatabaseException) {
                Log.e("Repo", "Firebase DatabaseException on main node delete: ${e.message}")
            }
            // Continue to attempt local deletion
        }

        // 3. Always attempt local deletion for immediate UI feedback
        try {
            scope.launch { // Ensure this runs off the main thread
                conversationDao.deleteConversationById(conversationId)
                Log.d("Repo", "Group deleted locally from Room: $conversationId (Firebase link success: $firebaseUserLinkSuccess, main node success: $firebaseMainNodeSuccess)")
            }.join() // Consider if waiting is necessary
        } catch (roomError: Exception) {
            Log.e("Repo", "Failed to delete group $conversationId locally from Room", roomError)
        }

        // Optional: Handle overall failure state if needed
        if (!firebaseUserLinkSuccess || !firebaseMainNodeSuccess) {
            Log.w("Repo", "Firebase deletion may have been incomplete for group $conversationId.")
            // Inform user or schedule retry if necessary
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
            throw e // Re-throw to inform ViewModel of failure
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

    private fun User.toEntity() = UserEntity(
        uid, name, avatarUrl, personality,
        // Add new fields
        backgroundStory, interests, speakingStyle,
        isOnline, lastSeen
    )
    private fun UserEntity.toModel() = User(
        uid, name, avatarUrl, personality,
        // Add new fields
        backgroundStory, interests, speakingStyle,
        isOnline, lastSeen
    )
    private fun Conversation.toEntity() = ConversationEntity(id, name, participants, messages, group, topic, typing)
    private fun ConversationEntity.toModel() = Conversation(id, name, participants, messages, isGroup, topic, typing)
}