package com.android.bakchodai.ui.add_ai

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAiCharacterScreen(
    viewModel: AddAiCharacterViewModel,
    onBack: () -> Unit,
    onAddSuccess: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()
    val addSuccess by viewModel.addSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    var background by remember { mutableStateOf("") } // New state
    var interests by remember { mutableStateOf("") } // New state
    var style by remember { mutableStateOf("") } // New state

    LaunchedEffect(addSuccess) {
        if (addSuccess) {
            Toast.makeText(context, "AI Character Added!", Toast.LENGTH_SHORT).show()
            viewModel.resetSuccess() // Reset state
            onAddSuccess() // Navigate back
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError() // Clear error after showing
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New AI Character") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("AI Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text("Personality Summary") },
                    placeholder = { Text("e.g., The funny guy, always cracks jokes")},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    enabled = !isLoading,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = background,
                    onValueChange = { background = it },
                    label = { Text("Background Story / Role") },
                    placeholder = { Text("e.g., Works as a software dev, lives in Bangalore, recently bought a bike...")},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    enabled = !isLoading,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = interests,
                    onValueChange = { interests = it },
                    label = { Text("Interests / Likes / Dislikes (Optional)") },
                    placeholder = { Text("e.g., Cricket (RCB fan), hates pineapple pizza, trekking, Bollywood movies")},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    enabled = !isLoading,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Speaking Style
                OutlinedTextField(
                    value = style,
                    onValueChange = { style = it },
                    label = { Text("Speaking Style") },
                    placeholder = { Text("e.g., Sarcastic, uses lots of emojis and Hinglish, short sentences")},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    enabled = !isLoading,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.addAiCharacter(name, personality, background, interests, style) },
                    // Update validation if needed
                    enabled = !isLoading && name.isNotBlank() && personality.isNotBlank() && background.isNotBlank() && style.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add AI Character")
                }
                Spacer(modifier = Modifier.height(16.dp)) // Add space at bottom
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}