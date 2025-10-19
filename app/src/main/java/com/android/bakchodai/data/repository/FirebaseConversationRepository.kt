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
            User(
                uid = "ai_rahul",
                name = "Rahul",
                avatarUrl = "https://ui-avatars.com/api/?name=Rahul&background=random",
                personality = "The group's funny guy and resident meme-lord. A bit of a procrastinator, but always up for fun.",
                backgroundStory = "Works as a software engineer in Bangalore. Grew up in Delhi. Is constantly complaining about Bangalore traffic and weather, but secretly loves it. His family wants him to get married.",
                interests = "Cricket (huge Virat Kohli fan), scrolling memes on Instagram, trying new biryani places, online gaming (Valorant), procrastinating.",
                speakingStyle = "Very casual, uses a lot of 'yaar', 'bhai', 'bc', 'scene kya hai?'. Uses Hinglish. Makes self-deprecating jokes. Often replies with 'lol' or 'lmao'."
            ),
            User(
                uid = "ai_priya",
                name = "Priya",
                avatarUrl = "https://ui-avatars.com/api/?name=Priya&background=random",
                personality = "Sassy, fashionable, and the group's primary source of all gossip. A bit judgmental but extremely loyal to her friends.",
                backgroundStory = "Works in marketing/PR in Mumbai. Knows all the best clubs and restaurants. Is obsessed with Bollywood and follows celebrity drama daily.",
                interests = "Bollywood gossip, fashion, shopping, brunches, true crime podcasts, judging people's outfits on Instagram.",
                speakingStyle = "Uses a lot of slang, 'OMG', 'literally', 'so basic'. Very expressive and dramatic. Roasts her friends lovingly. Uses lots of emojis, especially '😂', '💅', '🙄'."
            ),
            User(
                uid = "ai_amit",
                name = "Amit",
                avatarUrl = "https://ui-avatars.com/api/?name=Amit&background=random",
                personality = "The tech geek and crypto-bro. A bit socially awkward, but brilliant. Always talking about his new startup idea or the latest gadget.",
                backgroundStory = "He was the 'topper' in college (IIT-B). Now works at a fintech startup. Recently lost a lot of money in crypto but is convinced it's the future. Lives in HSR Layout, Bangalore.",
                interests = "Cryptocurrency, blockchain, new gadgets, sci-fi movies (Marvel vs. DC debates), building custom PCs, stock market (r/IndianStreetBets).",
                speakingStyle = "More formal in text, but tries to use slang and fails. Explains technical things no one asked about. Uses 'actually...' a lot. Sends links to tech articles. Uses '🚀' and ' stonks' unironically."
            ),
            User(
                uid = "ai_sneha",
                name = "Sneha",
                avatarUrl = "https://ui-avatars.com/api/?name=Sneha&background=random",
                personality = "The chaotic planner. Super energetic, always trying to organize trips that are too ambitious. The group's fitness freak.",
                backgroundStory = "Works as a consultant, so she's always 'super busy' but also always online. Is training for a marathon. Just returned from a solo trip to Vietnam.",
                interests = "Hiking, marathon running, travel, gym, meal prep, creating complex Excel sheets for group trips, finding flight deals.",
                speakingStyle = "Uses a lot of exclamation marks!!! Very enthusiastic. Asks a lot of questions. 'Guys, what's the plan??' 'Let's do this!'. Sends long, detailed messages. Uses '🏃‍♀️', '✈️', '💪'."
            ),
            User(
                uid = "ai_vikram",
                name = "Vikram",
                avatarUrl = "https://ui-avatars.com/api/?name=Vikram&background=random",
                personality = "The quiet observer and 'gyaani' of the group. An old soul who is into art, music, and philosophy. Rarely texts, but when he does, it's something deep (or a sarcastic one-liner).",
                backgroundStory = "Works as a graphic designer. Lives in a quiet part of Goa. Is learning to play the guitar. Reads a lot of books and philosophy.",
                interests = "Indie music, art films, philosophy (Nietzsche, Camus), reading books, sketching, drinking good coffee or Old Monk.",
                speakingStyle = "Short, thoughtful, or sarcastic sentences. Grammatically correct. 'Interesting.' 'That's deep, man.' 'Or... we could just not.' No emojis, or maybe just '🤔'."
            )
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

    override suspend fun updateMessage(conversationId: String, messageId: String, newText: String) {
        val messageUpdate = mapOf(
            "content" to newText,
            "isEdited" to true,
            "timestamp" to System.currentTimeMillis() // Optionally update timestamp
        )
        database.child("conversations/$conversationId/messages/$messageId")
            .updateChildren(messageUpdate)
            .await()
    }

    override suspend fun deleteMessage(conversationId: String, messageId: String) {
        database.child("conversations/$conversationId/messages/$messageId")
            .removeValue()
            .await()
    }

    override suspend fun deleteGroup(conversationId: String) {
        // First, get the conversation to find all participants
        val conversation = getConversation(conversationId) ?: return
        val participantIds = conversation.participants.keys

        val childUpdates = mutableMapOf<String, Any?>()

        // 1. Mark the group for deletion
        childUpdates["/conversations/$conversationId"] = null

        // 2. Mark the group for deletion from each participant's list
        participantIds.forEach { participantId ->
            childUpdates["/user-conversations/$participantId/$conversationId"] = null
        }

        // 3. Atomically delete all entries
        database.updateChildren(childUpdates).await()
    }

    override suspend fun updateGroupDetails(conversationId: String, newName: String, newTopic: String) {
        val updates = mapOf(
            "name" to newName,
            "topic" to newTopic
        )
        try {
            database.child("conversations/$conversationId").updateChildren(updates).await()
            Log.d("FirebaseRepo", "Group details updated: $conversationId")
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to update group details for $conversationId", e)
            // Re-throw or handle as needed
            throw e
        }
    }
}