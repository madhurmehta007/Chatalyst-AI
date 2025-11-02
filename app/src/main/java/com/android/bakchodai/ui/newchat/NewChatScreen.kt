package com.android.bakchodai.ui.newchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    users: List<User>,
    isLoading: Boolean,
    isUserPremium: Boolean, // *** MODIFICATION: Added ***
    onUserClick: (User) -> Unit,
    onAddAiClick: () -> Unit,
    onNavigateToPremium: () -> Unit, // *** MODIFICATION: Added ***
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // *** MODIFICATION: Check premium status here ***
                    if (isUserPremium) {
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
        } else if (users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No AI characters found.\nTap '+' to add one!", modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(users) { user ->
                    UserListItem(
                        user = user,
                        onUserClick = onUserClick
                    )
                    Divider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 88.dp))
                }
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: User,
    onUserClick: (User) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick(user) }
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