package com.android.bakchodai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.bakchodai.data.repository.FirebaseConversationRepository
import com.android.bakchodai.ui.auth.AuthScreen
import com.android.bakchodai.ui.auth.AuthState
import com.android.bakchodai.ui.auth.AuthViewModel
import com.android.bakchodai.ui.auth.AuthViewModelFactory
import com.android.bakchodai.ui.chat.ChatScreen
import com.android.bakchodai.ui.chat.ChatViewModel
import com.android.bakchodai.ui.chat.ChatViewModelFactory
import com.android.bakchodai.ui.creategroup.CreateGroupScreen
import com.android.bakchodai.ui.creategroup.CreateGroupViewModel
import com.android.bakchodai.ui.creategroup.CreateGroupViewModelFactory
import com.android.bakchodai.ui.main.MainScreen
import com.android.bakchodai.ui.main.MainViewModel
import com.android.bakchodai.ui.main.MainViewModelFactory
import com.android.bakchodai.ui.newchat.NewChatScreen
import com.android.bakchodai.ui.newchat.NewChatViewModel
import com.android.bakchodai.ui.newchat.NewChatViewModelFactory

@Composable
fun AppNavigation() {
    // --- SINGLE SOURCE OF TRUTH FOR DEPENDENCIES ---
    val conversationRepository = remember { FirebaseConversationRepository() }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(conversationRepository))
    val authState by authViewModel.authState.collectAsState()

    when (authState) {
        AuthState.LOGGED_OUT -> AuthScreen(authViewModel)
        AuthState.INITIALIZING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthState.LOGGED_IN -> {
            // Only show the main app when the user is fully logged in and initialized
            val navController = rememberNavController()
            val mainViewModelFactory = remember { MainViewModelFactory(conversationRepository) }
            val chatViewModelFactory = remember { ChatViewModelFactory(conversationRepository) }
            val createGroupViewModelFactory = remember { CreateGroupViewModelFactory(conversationRepository) }
            val newChatViewModelFactory = remember { NewChatViewModelFactory(conversationRepository) }

            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    val mainViewModel: MainViewModel = viewModel(factory = mainViewModelFactory)
                    MainScreen(
                        mainViewModel = mainViewModel,
                        authViewModel = authViewModel,
                        onConversationClick = { conversationId ->
                            navController.navigate("chat/$conversationId")
                        },
                        onNewChatClick = { navController.navigate("new_chat") },
                        onNewGroupClick = { navController.navigate("create_group") },
                        onSaveProfile = { newName -> authViewModel.updateUserName(newName) },
                        onLogout = { authViewModel.logout() }
                    )
                }
                composable("new_chat") {
                    val newChatViewModel: NewChatViewModel = viewModel(factory = newChatViewModelFactory)
                    val users by newChatViewModel.users.collectAsState()
                    val conversationId by newChatViewModel.navigateToConversation.collectAsState()

                    NewChatScreen(users = users) { user ->
                        newChatViewModel.findOrCreateConversation(user)
                    }

                    LaunchedEffect(conversationId) {
                        conversationId?.let {
                            navController.navigate("chat/$it") {
                                popUpTo("new_chat") { inclusive = true }
                            }
                        }
                    }
                }
                composable("create_group") {
                    val createGroupViewModel: CreateGroupViewModel = viewModel(factory = createGroupViewModelFactory)
                    val users by createGroupViewModel.users.collectAsState()
                    val groupCreated by createGroupViewModel.groupCreated.collectAsState()

                    CreateGroupScreen(users = users) { name, participantIds, topic ->
                        createGroupViewModel.createGroup(name, participantIds, topic)
                    }

                    LaunchedEffect(groupCreated) {
                        groupCreated?.let {
                            navController.navigate("chat/$it") {
                                popUpTo("create_group") { inclusive = true }
                            }
                        }
                    }
                }
                composable("chat/{conversationId}") { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId")!!
                    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)

                    LaunchedEffect(conversationId) {
                        chatViewModel.loadConversation(conversationId)
                    }

                    val conversation by chatViewModel.conversation.collectAsState()
                    val users by chatViewModel.users.collectAsState()

                    conversation?.let { conv ->
                        ChatScreen(
                            conversation = conv,
                            users = users,
                            onSendMessage = { message ->
                                chatViewModel.sendMessage(conversationId, message)
                            },
                            onEmojiReact = { messageId, emoji ->
                                chatViewModel.addEmojiReaction(conversationId, messageId, emoji)
                            }
                        )
                    }
                }
            }
        }
    }
}