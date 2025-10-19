package com.android.bakchodai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
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
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.FirebaseConversationRepository
import com.android.bakchodai.ui.add_ai.AddAiCharacterScreen
import com.android.bakchodai.ui.add_ai.AddAiCharacterViewModel
import com.android.bakchodai.ui.add_ai.AddAiCharacterViewModelFactory
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
import com.android.bakchodai.ui.profile.ProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val conversationRepository = remember { FirebaseConversationRepository() }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(conversationRepository))
    val authState by authViewModel.authState.collectAsState()
    val authIsLoading by authViewModel.isLoading.collectAsState()

    when (authState) {
        AuthState.LOGGED_OUT -> AuthScreen(authViewModel, authIsLoading)
        AuthState.INITIALIZING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthState.LOGGED_IN -> {
            val navController = rememberNavController()
            val mainViewModelFactory = remember { MainViewModelFactory(conversationRepository) }
            val chatViewModelFactory = remember { ChatViewModelFactory(conversationRepository) }
            val createGroupViewModelFactory = remember { CreateGroupViewModelFactory(conversationRepository) }
            val newChatViewModelFactory = remember { NewChatViewModelFactory(conversationRepository) }
            val addAiViewModelFactory = remember {
                AddAiCharacterViewModelFactory(
                    conversationRepository
                )
            }

            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    val mainViewModel: MainViewModel = viewModel(factory = mainViewModelFactory)
                    MainScreen(
                        mainViewModel = mainViewModel,
                        authViewModel = authViewModel,
                        onConversationClick = { conversationId ->
                            navController.navigate("chat/$conversationId")
                        },
                        onProfileClick = {
                            navController.navigate("profile")
                        },
                        onNewChatClick = { navController.navigate("new_chat") },
                        onNewGroupClick = { navController.navigate("create_group") }
                    )
                }

                composable("profile") {
                    val currentUser by authViewModel.user.collectAsState()
                    val users by viewModel<MainViewModel>(factory = mainViewModelFactory).users.collectAsState()
                    val user = users.find { it.uid == currentUser?.uid }
                        ?: User(uid = currentUser?.uid ?: "", name = currentUser?.displayName ?: "")

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Profile") },
                                navigationIcon = {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        ProfileScreen(
                            user = user, // Pass the full user object
                            onSaveClick = { newName -> authViewModel.updateUserName(newName) },
                            onLogoutClick = { authViewModel.logout() },
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }

                composable("new_chat") {
                    val newChatViewModel: NewChatViewModel = viewModel(factory = newChatViewModelFactory)
                    val users by newChatViewModel.users.collectAsState()
                    val isLoading by newChatViewModel.isLoading.collectAsState()
                    val conversationId by newChatViewModel.navigateToConversation.collectAsState()

                    NewChatScreen(
                        users = users,
                        isLoading = isLoading,
                        onUserClick = { user ->
                            newChatViewModel.findOrCreateConversation(user)
                        },
                        // *** ADDED: Navigate to new screen ***
                        onAddAiClick = {
                            navController.navigate("add_ai_character")
                        },
                        onBack = { navController.popBackStack() }
                    )

                    LaunchedEffect(conversationId) {
                        conversationId?.let {
                            navController.navigate("chat/$it") {
                                popUpTo("new_chat") { inclusive = true }
                            }
                        }
                    }
                }

                composable("add_ai_character") {
                    val addAiViewModel: AddAiCharacterViewModel = viewModel(factory = addAiViewModelFactory)
                    AddAiCharacterScreen(
                        viewModel = addAiViewModel,
                        onBack = { navController.popBackStack() },
                        onAddSuccess = { navController.popBackStack() } // Go back after adding
                    )
                }

                composable("create_group") {
                    val createGroupViewModel: CreateGroupViewModel = viewModel(factory = createGroupViewModelFactory)
                    val users by createGroupViewModel.users.collectAsState()
                    val isLoading by createGroupViewModel.isLoading.collectAsState()
                    val groupCreated by createGroupViewModel.groupCreated.collectAsState()

                    CreateGroupScreen(
                        users = users,
                        isLoading = isLoading,
                        onCreateGroup = { name, participantIds, topic ->
                            createGroupViewModel.createGroup(name, participantIds, topic)
                        },
                        onBack = { navController.popBackStack() }
                    )

                    LaunchedEffect(groupCreated) {
                        groupCreated?.let {
                            navController.navigate("chat/$it") {
                                popUpTo("create_group") { inclusive = true }
                            }
                        }
                    }
                }

                // *** THIS IS THE CORRECTED LOGIC ***
                composable("chat/{conversationId}") { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId")!!
                    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)

                    LaunchedEffect(conversationId) {
                        chatViewModel.loadConversation(conversationId)
                    }

                    val conversation by chatViewModel.conversation.collectAsState()
                    val users by chatViewModel.users.collectAsState()
                    val isAiTyping by chatViewModel.isAiTyping.collectAsState()
                    val isLoading by chatViewModel.isLoading.collectAsState() // Get loading state

                    if (isLoading) {
                        // State 1: We are actively loading the conversation
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (conversation == null) {
                        // State 2: Loading is finished, but conversation is still null.
                        // This means it was deleted or invalid. Pop back to the main screen.
                        LaunchedEffect(Unit) {
                            navController.popBackStack()
                        }
                    } else {
                        // State 3: Loading is finished and we have a conversation to show
                        ChatScreen(
                            conversation = conversation!!,
                            users = users,
                            isAiTyping = isAiTyping,
                            onSendMessage = { message ->
                                chatViewModel.sendMessage(conversationId, message)
                            },
                            onEmojiReact = { messageId, emoji ->
                                chatViewModel.addEmojiReaction(conversationId, messageId, emoji)
                            },
                            onEditMessage = { messageId, newText ->
                                chatViewModel.editMessage(conversationId, messageId, newText)
                            },
                            onDeleteMessage = { messageId ->
                                chatViewModel.deleteMessage(conversationId, messageId)
                            },
                            onDeleteGroup = {
                                chatViewModel.deleteGroup(conversationId)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}