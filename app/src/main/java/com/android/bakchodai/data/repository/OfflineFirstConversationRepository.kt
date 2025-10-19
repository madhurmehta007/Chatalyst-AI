package com.android.bakchodai.data.repository

import android.util.Log
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
    // Removed conversationListener as specific listeners will be used in getConversationFlow

    // --- Syncing Logic ---
    fun startSyncing() {
        Log.d("Repo", "Starting Firebase sync...")
        syncUsers()
        // Removed syncConversations() call from here, handled differently now
    }

    fun stopSyncing() {
        Log.d("Repo", "Stopping Firebase sync...")
        userListener?.let { database.child("users").removeEventListener(it) }
        // Specific conversation listeners are removed in awaitClose of getConversationFlow
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

    // Removed the old syncConversations() function

    // --- Flow-based methods ---
    override fun getConversationsFlow(): Flow<List<Conversation>> {
        // This flow now relies on a separate mechanism to populate Room initially
        // (e.g., fetch on login or use Firebase RTDB offline cache + listener)
        // For simplicity, let's trigger a one-time fetch and update when this is first collected.
        return conversationDao.getAllConversations()
            .map { entities -> entities.map { it.toModel() } }
            .onStart { // Fetch initial list when flow starts
                fetchAndUpdateUserConversations()
            }
            .flowOn(Dispatchers.IO) // Ensure Room access is off the main thread
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
                    // Update Room whenever Firebase changes
                    conversation?.let {
                        scope.launch { conversationDao.insertAll(listOf(it.toEntity())) }
                    }
                    // Try sending the update through the flow
                    trySend(conversation)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Repo", "Firebase listener cancelled for conv $id", error.toException())
                    close(error.toException()) // Close the flow on error
                }
            })
            // Remove listener when the flow collector cancels
            awaitClose {
                Log.d("Repo", "Removing Firebase listener for conv $id")
                conversationRef.removeEventListener(listener)
            }
        }.flowOn(Dispatchers.IO) // Perform Firebase operations off the main thread

        // Use flatMapLatest to switch to the Room flow after the Firebase flow provides an initial value
        // or whenever Firebase emits a new value (which updates Room)
        // DistinctUntilChanged prevents unnecessary updates if the data hasn't changed
        return firebaseFlow.flatMapLatest {
            // This now reads directly from Room, which is updated by the firebaseFlow listener
            conversationDao.getConversationById(id).map { it?.toModel() }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
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
        // Fetch participants directly from Firebase before deleting
        val conversationSnapshot = database.child("conversations/$conversationId").get().await()
        val conversation = conversationSnapshot.getValue(Conversation::class.java)
        val participants = conversation?.participants?.keys ?: emptySet()

        val childUpdates = mutableMapOf<String, Any?>()
        childUpdates["/conversations/$conversationId"] = null
        participants.forEach { userId ->
            childUpdates["/user-conversations/$userId/$conversationId"] = null
        }
        try {
            database.updateChildren(childUpdates).await()
            Log.d("Repo", "Group deleted in Firebase: $conversationId")
            // Also delete locally
            scope.launch { conversationDao.deleteConversationById(conversationId) }
        } catch (e: Exception) {
            Log.e("Repo", "Failed to delete group in Firebase", e)
        }
    }

    // --- Mapper functions ---
    private fun User.toEntity() = UserEntity(uid, name, avatarUrl, personality)
    private fun UserEntity.toModel() = User(uid, name, avatarUrl, personality)
    private fun Conversation.toEntity() = ConversationEntity(id, name, participants, messages, group, topic)
    private fun ConversationEntity.toModel() = Conversation(id, name, participants, messages, isGroup, topic)
}