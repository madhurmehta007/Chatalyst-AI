package com.android.bakchodai.ui.add_ai

import android.widget.Toast
import androidx.compose.foundation.layout.*
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
                    label = { Text("AI Personality / Instructions") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), // Make personality field taller
                    enabled = !isLoading,
                    maxLines = 5 // Allow multiple lines
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.addAiCharacter(name, personality) },
                    enabled = !isLoading && name.isNotBlank() && personality.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add AI Character")
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}