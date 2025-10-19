package com.android.bakchodai.ui.edit_group

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    onBack: () -> Unit,
    viewModel: EditGroupViewModel = hiltViewModel() // Get ViewModel via Hilt
) {
    val conversation by viewModel.conversation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    // Local state for text fields, initialized when conversation loads
    var groupName by remember(conversation) { mutableStateOf(conversation?.name ?: "") }
    var topic by remember(conversation) { mutableStateOf(conversation?.topic ?: "") }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            Toast.makeText(context, "Group details saved!", Toast.LENGTH_SHORT).show()
            viewModel.resetSuccess()
            onBack() // Navigate back after saving
        }
    }

     LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Group") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                     IconButton(
                         onClick = { viewModel.saveGroupDetails(groupName, topic) },
                         enabled = !isLoading && groupName.isNotBlank() && conversation != null // Enable only if name is not blank and convo loaded
                     ) {
                         Icon(Icons.Default.Check, contentDescription = "Save")
                     }
                }
            )
        }
    ) { paddingValues ->
         Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (conversation == null && !isLoading) { // Show loading until conversation is available
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                 Column(
                     modifier = Modifier
                         .fillMaxSize()
                         .padding(16.dp),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     OutlinedTextField(
                         value = groupName,
                         onValueChange = { groupName = it },
                         label = { Text("Group Name") },
                         modifier = Modifier.fillMaxWidth(),
                         singleLine = true,
                         enabled = !isLoading
                     )
                     Spacer(modifier = Modifier.height(16.dp))
                     OutlinedTextField(
                         value = topic,
                         onValueChange = { topic = it },
                         label = { Text("Group Topic (Optional)") },
                         modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                         enabled = !isLoading,
                         maxLines = 5
                     )
                 }
            }
             if (isLoading && conversation != null) { // Show loading overlay during save
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
             }
         }
    }
}