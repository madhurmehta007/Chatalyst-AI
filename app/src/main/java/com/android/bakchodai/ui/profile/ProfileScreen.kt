package com.android.bakchodai.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.theme.ThemeHelper
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    user: User?,
    onSaveClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    val avatarUrl by remember(user) {
        mutableStateOf(user?.avatarUrl ?: "https://ui-avatars.com/api/?name=?")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Read dark mode state from ThemeHelper
    val isDarkTheme by ThemeHelper.isDarkTheme(context).collectAsState(initial = false)

    LaunchedEffect(user) {
        if (user != null) {
            name = user.name
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Align to top for profile view
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Profile Picture
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant), // Background for placeholder/error
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.Person), // Placeholder
            error = rememberVectorPainter(Icons.Filled.Person) // Error fallback
        )
        Spacer(modifier = Modifier.height(16.dp))

        // User's name (non-editable, from auth)
        Text(
            text = user?.name ?: "Loading...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Card for actions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSaveClick(name)
                        Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // *** Card for Settings (Dark Mode) ***
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { isChecked ->
                        scope.launch {
                            ThemeHelper.setDarkTheme(context, isChecked)
                        }
                    }
                )
            }
        }


        Spacer(modifier = Modifier.weight(1f)) // Pushes logout button to bottom

        Button(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}