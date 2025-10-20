package com.android.bakchodai.ui.creategroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    users: List<User>,
    isLoading: Boolean,
    onCreateGroup: (String, List<String>, String) -> Unit,
    onBack: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    val selectedUsers = remember { mutableStateListOf<String>() }

    // Create a map for quick lookup of User objects by ID
    val usersById = remember(users) { users.associateBy { it.uid } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        // NEW: Floating Action Button for creation
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onCreateGroup(groupName, selectedUsers.toList(), topic) },
                // Enable only when a name and at least one user is selected
                containerColor = if (selectedUsers.isNotEmpty() && groupName.isNotBlank()) MaterialTheme.colorScheme.secondary else Color.Gray
            ) {
                Icon(Icons.Default.Check, contentDescription = "Create Group", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // --- Group Details Inputs ---
            TextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )
            TextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )

            // --- Selected Users Chip List ---
            if (selectedUsers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(selectedUsers) { userId ->
                        usersById[userId]?.let { user ->
                            SelectedUserChip(user = user, onRemove = { selectedUsers.remove(userId) })
                        }
                    }
                }
                Divider()
            }

            // --- User Selection List ---
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            "Select AI Characters",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(users, key = { it.uid }) { user ->
                        val isSelected = user.uid in selectedUsers
                        SelectableUserListItem(
                            user = user,
                            isSelected = isSelected,
                            onUserClick = {
                                if (isSelected) {
                                    selectedUsers.remove(user.uid)
                                } else {
                                    selectedUsers.add(user.uid)
                                }
                            }
                        )
                        Divider(modifier = Modifier.padding(start = 88.dp))
                    }
                }
            }
        }
    }
}

/**
 * A composable chip to show a selected user.
 */
@Composable
private fun SelectedUserChip(
    user: User,
    onRemove: () -> Unit
) {
    Card(
        shape = CircleShape,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            AsyncImage(
                model = user.resolveAvatarUrl(),
                contentDescription = user.name,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentScale = ContentScale.Crop
            )
            Text(
                text = user.name,
                modifier = Modifier.padding(horizontal = 8.dp),
                fontSize = 14.sp
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.Gray
                )
            }
        }
    }
}

/**
 * A composable row item for displaying a user in a selectable list.
 */
@Composable
private fun SelectableUserListItem(
    user: User,
    isSelected: Boolean,
    onUserClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() }
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
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
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(2.dp)
                )
            }
        }


        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = user.personality.ifBlank { "AI Character" },
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}