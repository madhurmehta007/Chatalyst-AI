package com.android.chatalystai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.chatalystai.data.repository.FirebaseConversationRepository
import com.android.chatalystai.ui.add_ai.AddAiCharacterScreen
import com.android.chatalystai.ui.add_ai.AddAiCharacterViewModel
import com.android.chatalystai.ui.auth.AuthScreen
import com.android.chatalystai.ui.auth.AuthState
import com.android.chatalystai.ui.auth.AuthViewModel
import com.android.chatalystai.ui.chat.ChatScreen
import com.android.chatalystai.ui.chat.ChatViewModel
import com.android.chatalystai.ui.common.FullScreenImageViewer // *** ADDED IMPORT ***
import com.android.chatalystai.ui.creategroup.CreateGroupScreen
import com.android.chatalystai.ui.creategroup.CreateGroupViewModel
import com.android.chatalystai.ui.edit_group.EditGroupScreen
import com.android.chatalystai.ui.main.MainScreen
import com.android.chatalystai.ui.main.MainViewModel
import com.android.chatalystai.ui.newchat.NewChatScreen
import com.android.chatalystai.ui.newchat.NewChatViewModel
import com.android.chatalystai.ui.premium.PremiumScreen
import com.android.chatalystai.ui.profile.AiProfileScreen
import com.android.chatalystai.ui.profile.ProfileScreen
import com.android.chatalystai.ui.splash.SplashScreen
import kotlinx.coroutines.delay
import java.net.URLEncoder // *** ADDED IMPORT ***

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val conversationRepository = remember { FirebaseConversationRepository() }
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    val authIsLoading by authViewModel.isLoading.collectAsState()

    var isSplashing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000) // Show splash for 2 seconds
        isSplashing = false
    }

    if (isSplashing) {
        SplashScreen()
    } else {
        when (authState) {
            AuthState.LOGGED_OUT -> AuthScreen(authViewModel, authIsLoading)
            AuthState.INITIALIZING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            AuthState.LOGGED_IN -> {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        val mainViewModel: MainViewModel = hiltViewModel()
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
                        val mainViewModel: MainViewModel = hiltViewModel()
                        val user by mainViewModel.currentUser.collectAsState()

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
                                user = user,
                                onSaveName = { newName -> authViewModel.updateUserName(newName) },
                                onSaveBio = { newBio -> authViewModel.updateUserBio(newBio) },
                                onLogoutClick = { authViewModel.logout() },
                                onUpgradeClick = { navController.navigate("premium") },
                                onUpdateAvatar = { uri -> authViewModel.updateUserAvatar(uri) },
                                onUpdateAvatarUrl = { url -> authViewModel.updateUserAvatarFromUrl(url) },
                                // *** MODIFICATION: Pass lambda for avatar click ***
                                onViewAvatar = { url ->
                                    val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                    navController.navigate("full_screen_image?url=$encodedUrl")
                                },
                                modifier = Modifier.padding(paddingValues)
                            )
                        }
                    }

                    composable("ai_profile/{userId}") {
                        AiProfileScreen(
                            onBack = { navController.popBackStack() },
                            // *** MODIFICATION: Pass lambda for avatar click ***
                            onViewAvatar = { url ->
                                val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                navController.navigate("full_screen_image?url=$encodedUrl")
                            }
                        )
                    }

                    // *** ADDED: New Route for Full Screen Image Viewer ***
                    composable(
                        "full_screen_image?url={url}",
                        arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true })
                    ) { backStackEntry ->
                        val imageUrl = backStackEntry.arguments?.getString("url")
                        FullScreenImageViewer(
                            imageUrl = imageUrl,
                            onBack = { navController.popBackStack() }
                        )
                    }


                    composable("new_chat") {
                        val newChatViewModel: NewChatViewModel = hiltViewModel()
                        val premadeAiCharacters by newChatViewModel.premadeAiCharacters.collectAsState()
                        val customAiCharacters by newChatViewModel.customAiCharacters.collectAsState()
                        val humanContacts by newChatViewModel.humanContacts.collectAsState()
                        val isLoading by newChatViewModel.isLoading.collectAsState()
                        val conversationId by newChatViewModel.navigateToConversation.collectAsState()
                        val isUserPremium by newChatViewModel.isUserPremium.collectAsState()

                        NewChatScreen(
                            premadeAiCharacters = premadeAiCharacters,
                            customAiCharacters = customAiCharacters,
                            humanContacts = humanContacts,
                            isLoading = isLoading,
                            isUserPremium = isUserPremium,
                            onUserClick = { user ->
                                newChatViewModel.findOrCreateConversation(user)
                            },
                            onAddAiClick = {
                                navController.navigate("add_ai_character")
                            },
                            onNavigateToPremium = {
                                navController.navigate("premium")
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
                        val addAiViewModel: AddAiCharacterViewModel = hiltViewModel()
                        AddAiCharacterScreen(
                            viewModel = addAiViewModel,
                            onBack = { navController.popBackStack() },
                            onAddSuccess = { navController.popBackStack() }
                        )
                    }

                    composable("create_group") {
                        val createGroupViewModel: CreateGroupViewModel = hiltViewModel()
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

                    composable("chat/{conversationId}") { backStackEntry ->
                        val conversationId = backStackEntry.arguments?.getString("conversationId")!!
                        val chatViewModel: ChatViewModel = hiltViewModel()
                        LaunchedEffect(conversationId) {
                            chatViewModel.loadConversation(conversationId)
                        }

                        val conversation by chatViewModel.conversation.collectAsState()
                        val users by chatViewModel.users.collectAsState()
                        val typingUsers by chatViewModel.typingUsers.collectAsState()
                        val isLoading by chatViewModel.isLoading.collectAsState()
                        val isUploading by chatViewModel.isUploading.collectAsState()
                        val replyToMessage by chatViewModel.replyToMessage.collectAsState()
                        val searchQuery by chatViewModel.searchQuery.collectAsState()
                        val filteredMessages by chatViewModel.filteredMessages.collectAsState()
                        val isRecording by chatViewModel.isRecording.collectAsState()
                        val nowPlayingMessageId by chatViewModel.nowPlayingMessageId.collectAsState()
                        val playbackState by chatViewModel.playbackState.collectAsState()

                        val firstUnreadMessageId by chatViewModel.firstUnreadMessageId.collectAsState()

                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (conversation == null) {
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        } else {
                            ChatScreen(
                                conversation = conversation!!,
                                users = users,
                                isUploading = isUploading,
                                isAiTyping = false,
                                typingUsers = typingUsers,
                                onSendMessage = { message ->
                                    chatViewModel.sendMessage(conversationId, message)
                                },
                                onSendMedia = { uris ->
                                    chatViewModel.sendMedia(conversationId, uris)
                                },
                                onEmojiReact = { messageId, emoji ->
                                    chatViewModel.addEmojiReaction(conversationId, messageId, emoji)
                                },
                                onEditMessage = { messageId, newText ->
                                    chatViewModel.editMessage(conversationId, messageId, newText)
                                },

                                onDeleteMessages = { messageIds ->
                                    chatViewModel.deleteMessages(conversationId, messageIds)
                                },
                                onClearChat = {
                                    chatViewModel.clearChat(conversationId)
                                },

                                onMuteConversation = { duration ->
                                    chatViewModel.muteConversation(conversationId, duration)
                                },

                                onNavigateToEditGroup = {
                                    navController.navigate("edit_group/$conversationId")
                                },
                                onNavigateToAiProfile = { userId ->
                                    navController.navigate("ai_profile/$userId")
                                },
                                onDeleteGroup = {
                                    chatViewModel.deleteGroup(conversationId)
                                },
                                onBack = { navController.popBackStack() },
                                replyToMessage = replyToMessage,
                                onSetReplyToMessage = { message ->
                                    chatViewModel.setReplyToMessage(message)
                                },
                                filteredMessages = filteredMessages,
                                searchQuery = searchQuery,
                                onSearchQueryChanged = { query ->
                                    chatViewModel.onSearchQueryChanged(query)
                                },

                                firstUnreadMessageId = firstUnreadMessageId,

                                isRecording = isRecording,
                                nowPlayingMessageId = nowPlayingMessageId,
                                playbackState = playbackState,
                                onStartRecording = { chatViewModel.startRecording() },
                                onStopRecording = { chatViewModel.stopAndSendRecording(conversationId) },
                                onPlayAudio = { chatViewModel.playAudio(it) },
                                onSeekAudio = { message, progress ->
                                    chatViewModel.onSeekAudio(message, progress)
                                },
                                onStopAudio = { chatViewModel.stopAudioOnDispose() }
                            )
                        }
                    }

                    composable("edit_group/{conversationId}") { backStackEntry ->
                        EditGroupScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("premium") {
                        PremiumScreen(
                            onBack = { navController.popBackStack() },
                            onSubscribeClick = {
                                // TODO: Implement payment logic
                            }
                        )
                    }
                }
            }
        }
    }
}