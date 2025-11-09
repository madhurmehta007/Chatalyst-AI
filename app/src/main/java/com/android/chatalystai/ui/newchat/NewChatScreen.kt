package com.android.chatalystai.ui.newchat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.chatalystai.data.model.User

private const val FREE_TIER_AI_LIMIT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    premadeAiCharacters: List<User>,
    customAiCharacters: List<User>,
    humanContacts: List<User>,
    isLoading: Boolean,
    isUserPremium: Boolean,
    onUserClick: (User) -> Unit,
    onAddAiClick: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onBack: () -> Unit,
    onDeleteAiCharacters: (List<String>) -> Unit // *** MODIFIED ***
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectedAiIds = remember { mutableStateListOf<String>() }
    val isInSelectionMode = selectedAiIds.isNotEmpty()

    // Clear selection on back press
    BackHandler(enabled = isInSelectionMode) {
        selectedAiIds.clear()
    }

    Scaffold(
        topBar = {
            // *** MODIFICATION: Conditional TopBar ***
            if (isInSelectionMode) {
                ContextualTopBar(
                    selectedCount = selectedAiIds.size,
                    onClose = { selectedAiIds.clear() },
                    onDelete = { showDeleteDialog = true }
                )
            } else {
                NormalTopBar(onBack = onBack)
            }
        },
        floatingActionButton = {
            // *** MODIFICATION: Hide FAB in selection mode ***
            if (!isInSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        if (isUserPremium || customAiCharacters.size < FREE_TIER_AI_LIMIT) {
                            onAddAiClick()
                        } else {
                            onNavigateToPremium()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add AI Character")
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (premadeAiCharacters.isEmpty() && customAiCharacters.isEmpty() && humanContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No contacts found.\nTap '+' to create a new AI!",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {

                if (premadeAiCharacters.isNotEmpty()) {
                    item {
                        Text(
                            text = "Featured AI",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(premadeAiCharacters) { user ->
                                FeaturedAiCard(user = user, onUserClick = onUserClick)
                            }
                        }
                    }
                }

                if (customAiCharacters.isNotEmpty()) {
                    item {
                        Text(
                            text = "Your AI Characters",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(customAiCharacters, key = { it.uid }) { user ->
                        val isSelected = user.uid in selectedAiIds
                        UserListItem(
                            user = user,
                            isSelected = if (isUserPremium) isSelected else false,
                            onUserClick = {
                                if (isInSelectionMode) {
                                    if (isSelected) selectedAiIds.remove(user.uid)
                                    else selectedAiIds.add(user.uid)
                                } else {
                                    onUserClick(user)
                                }
                            },
                            onUserLongPress = {
                                if (isUserPremium) {
                                    if (!isInSelectionMode) {
                                        selectedAiIds.add(user.uid)
                                    }
                                }
                            }
                        )
                        Divider(
                            modifier = Modifier.padding(
                                top = 8.dp,
                                bottom = 8.dp,
                                start = 88.dp
                            )
                        )
                    }
                }

                if (humanContacts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(humanContacts, key = { it.uid }) { user ->
                        UserListItem(
                            user = user,
                            isSelected = false, // Humans can't be selected
                            onUserClick = { onUserClick(user) },
                            onUserLongPress = {} // Humans can't be deleted
                        )
                        Divider(
                            modifier = Modifier.padding(
                                top = 8.dp,
                                bottom = 8.dp,
                                start = 88.dp
                            )
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        val count = selectedAiIds.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $count AI Character${if (count > 1) "s" else ""}?") },
            text = { Text("Are you sure you want to delete ${if (count > 1) "these AI characters" else "this AI character"}? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAiCharacters(selectedAiIds.toList())
                        showDeleteDialog = false
                        selectedAiIds.clear()
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// *** ADDED: Normal Top Bar ***
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("New Chat") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

// *** ADDED: Contextual Top Bar for Selection ***
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Selection")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}


@Composable
private fun FeaturedAiCard(
    user: User,
    onUserClick: (User) -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onUserClick(user) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = user.resolveAvatarUrl(),
            contentDescription = user.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.Person),
            error = rememberVectorPainter(Icons.Filled.Person)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = user.name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = user.personality.ifBlank { "AI" },
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@OptIn(ExperimentalFoundationApi::class) // *** ADDED ***
@Composable
private fun UserListItem(
    user: User,
    isSelected: Boolean, // *** ADDED ***
    onUserClick: () -> Unit, // *** MODIFIED ***
    onUserLongPress: () -> Unit // *** ADDED ***
) {
    val hapticFeedback = LocalHapticFeedback.current
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // *** MODIFICATION: Use combinedClickable ***
            .combinedClickable(
                onClick = onUserClick,
                onLongClick = {
                    onUserLongPress()
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.resolveAvatarUrl(),
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.Person),
            error = rememberVectorPainter(Icons.Filled.Person)
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))

            val subtitle = if (user.uid.startsWith("ai_")) {
                user.personality.ifBlank { "AI Character" }
            } else {
                "Human User"
            }
            Text(
                text = subtitle,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}