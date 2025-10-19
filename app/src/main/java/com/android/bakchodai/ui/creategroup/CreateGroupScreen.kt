package com.android.bakchodai.ui.creategroup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
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
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            TextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(users) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = user.uid in selectedUsers,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        selectedUsers.add(user.uid)
                                    } else {
                                        selectedUsers.remove(user.uid)
                                    }
                                }
                            )
                            Text(user.name)
                        }
                    }
                }
            }
            Button(
                onClick = { onCreateGroup(groupName, selectedUsers.toList(), topic) },
                enabled = selectedUsers.isNotEmpty() && !isLoading,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            ) {
                Text("Create Group")
            }
        }
    }
}