// FirebaseConversationRepository.kt
package com.android.bakchodai.data.repository

import android.util.Log
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.MessageStatus
import com.android.bakchodai.data.model.MessageType
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.remote.AiService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseConversationRepository : ConversationRepository {

    private val database = Firebase.database.reference

    private val aiService = AiService()

    private val _usersFlow = MutableStateFlow<List<User>>(emptyList())
    private val usersFlow: Flow<List<User>> = _usersFlow

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedAiCharactersIfNeeded()
        }
        CoroutineScope(Dispatchers.Default).launch {
            getUsersFlow().collect { users ->
                _usersFlow.value = users
            }
        }
    }

    suspend fun seedAiCharactersIfNeeded() {
        val aiUsers = listOf(
            User(uid = "ai_rahul", name = "Rahul", personality = "The funny guy who always cracks jokes and roasts everyone, loves IPL and cricket banter."),
            User(uid = "ai_priya", name = "Priya", personality = "Sassy gossip queen, always sharing juicy life updates and planning wild trips."),
            User(uid = "ai_amit", name = "Amit", personality = "Tech geek dropping random facts, but gets hilariously roasted for being too nerdy."),
            User(uid = "ai_sneha", name = "Sneha", personality = "The chaotic planner who suggests epic trip ideas but turns everything into a comedy of errors."),
            User(uid = "ai_vikram", name = "Vikram", personality = "Quiet observer who suddenly drops deep thoughts, memes, or savage one-liners out of nowhere.")
        )

        for (aiUser in aiUsers) {
            val userNode = database.child("users").child(aiUser.uid)
            val snapshot = userNode.get().await()
            if (!snapshot.exists()) {
                userNode.setValue(aiUser).await()
            }
        }
    }

    override suspend fun getConversations(): List<Conversation> {
        val currentUser = Firebase.auth.currentUser ?: return emptyList()
        val userConvoIdsSnapshot = database.child("user-conversations").child(currentUser.uid).get().await()
        val convoIds = userConvoIdsSnapshot.children.mapNotNull { it.key }
        return convoIds.mapNotNull { id ->
            database.child("conversations").child(id).get().await().getValue(Conversation::class.java)
        }
    }

    override suspend fun addMessage(conversationId: String, message: Message) {
        // 1. Write the new message to the database
        val messageId = database.child("conversations/$conversationId/messages").push().key!!
        database.child("conversations/$conversationId/messages/$messageId").setValue(message.copy(id = messageId)).await()

        // 2. Check if this is a 1-to-1 chat
        val conversation = getConversation(conversationId)

        // 3. *** LOOP FIX ***
        //    ONLY generate a response if the message is from a HUMAN and it's NOT a group chat.
        //    Group chats are handled by GroupChatService.
        if (conversation != null && !message.senderId.startsWith("ai_") && !conversation.group) {
            generateAndAddAiResponse(conversationId, message.senderId)
        }
    }

    // This function is now ONLY for 1-to-1 AI responses
    private suspend fun generateAndAddAiResponse(conversationId: String, lastSenderId: String) {
        withContext(Dispatchers.IO) {
            try {
                val conversation = getConversation(conversationId) ?: return@withContext
                val aiParticipantIds = conversation.participants.keys.filter { it.startsWith("ai_") }
                val history = conversation.messages.values.toList()

                if (aiParticipantIds.isNotEmpty()) {
                    val speakingAi = aiParticipantIds.first() // In 1-to-1, there's only one

                    // Use the simple getResponse for 1-to-1 chats
                    val response = aiService.getResponse(history)

                    val newMessage = Message(
                        id = "",
                        senderId = speakingAi,
                        content = response,
                        timestamp = System.currentTimeMillis(),
                        type = MessageType.TEXT,
                        status = MessageStatus.SENT
                    )

                    // *** CRITICAL LOOP FIX ***
                    // Write directly to DB instead of calling addMessage() again.
                    val newMsgId = database.child("conversations/$conversationId/messages").push().key!!
                    database.child("conversations/$conversationId/messages/$newMsgId").setValue(newMessage.copy(id = newMsgId)).await()
                }
            } catch (e: Exception) {
                Log.e("FirebaseRepo", "Error generating AI response", e)
            }
        }
    }


    override suspend fun getConversation(id: String): Conversation? {
        val data = database.child("conversations").child(id).get().await()
        return data.getValue(Conversation::class.java)
    }

    override fun getConversationFlow(id: String): Flow<Conversation?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Conversation::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = database.child("conversations").child(id)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun getUsersFlow(): Flow<List<User>> = callbackFlow {
        val listener = database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                trySend(users)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { database.child("users").removeEventListener(listener) }
    }

    override suspend fun getUsers(): List<User> {
        return database.child("users").get().await().children.mapNotNull {
            it.getValue(User::class.java)
        }
    }

    override suspend fun addUser(user: User) {
        database.child("users").child(user.uid).setValue(user).await()
    }

    override suspend fun updateUserName(uid: String, newName: String) {
        database.child("users").child(uid).child("name").setValue(newName).await()
    }

    override suspend fun createGroup(name: String, participantIds: List<String>, topic: String): String {
        val conversationId = database.child("conversations").push().key!!
        val participants = participantIds.associateWith { true }
        val isGroup = participantIds.size > 2 || topic.isNotBlank()

        val conversationName = if (!isGroup) {
            val currentUserId = Firebase.auth.currentUser?.uid ?: ""
            val otherUserId = participantIds.firstOrNull { it != currentUserId }
            val otherUser = otherUserId?.let { database.child("users").child(it).get().await().getValue<User>() }
            otherUser?.name ?: name
        } else {
            name
        }

        val conversation = Conversation(
            id = conversationId,
            name = conversationName,
            participants = participants,
            group = isGroup,
            topic = topic
        )

        val childUpdates = mutableMapOf<String, Any?>()
        childUpdates["/conversations/$conversationId"] = conversation
        participantIds.forEach { participantId ->
            childUpdates["/user-conversations/$participantId/$conversationId"] = true
        }

        database.updateChildren(childUpdates).await()

        // Don't auto-message on group creation, let GroupChatService handle it.
        // if (isGroup && topic.isNotBlank()) {
        //     generateAndAddAiResponse(conversationId, "")
        // }
        return conversationId
    }

    override fun getConversationsFlow(): Flow<List<Conversation>> {
        val currentUserId = Firebase.auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val convoIds = snapshot.children.mapNotNull { it.key }
                    launch(Dispatchers.IO) {
                        val convos = convoIds.mapNotNull { id ->
                            database.child("conversations").child(id).get().await().getValue(Conversation::class.java)
                        }
                        trySend(convos)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            val ref = database.child("user-conversations").child(currentUserId)
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }
    }
}