// file: bakchodai/ui/profile/ProfileScreen.kt
package com.android.bakchodai.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.theme.ThemeHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User?,
    onSaveClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(user) { mutableStateOf(user?.name ?: "") }
    val avatarUrl by remember(user) {
        mutableStateOf(user?.resolveAvatarUrl() ?: "https://ui-avatars.com/api/?name=?")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme by ThemeHelper.isDarkTheme(context).collectAsState(initial = false)

    // State for showing the edit name dialog
    var showEditNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background), // Use theme background
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.Person),
            error = rememberVectorPainter(Icons.Filled.Person)
        )
        Spacer(modifier = Modifier.height(32.dp))

        // --- Settings List ---
        // User Name
        ProfileListItem(
            icon = Icons.Default.Person,
            title = "Name",
            subtitle = user?.name ?: "Loading...",
            onClick = { showEditNameDialog = true },
            trailingContent = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Name",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        // Dark Mode Toggle
        ProfileListItem(
            icon = Icons.Default.Brightness6,
            title = "Dark Mode",
            subtitle = if (isDarkTheme) "Enabled" else "Disabled",
            onClick = {
                scope.launch {
                    ThemeHelper.setDarkTheme(context, !isDarkTheme)
                }
            },
            trailingContent = {
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { isChecked ->
                        scope.launch {
                            ThemeHelper.setDarkTheme(context, isChecked)
                        }
                    }
                )
            }
        )

        Spacer(modifier = Modifier.weight(1f)) // Pushes logout to bottom

        // Logout Button
        ProfileListItem(
            icon = Icons.Default.ExitToApp,
            title = "Logout",
            subtitle = "",
            onClick = onLogoutClick,
            titleColor = MaterialTheme.colorScheme.error // Make logout red
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    // --- Edit Name Dialog ---
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Name") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveClick(name)
                        Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show()
                        showEditNameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    name = user?.name ?: "" // Reset name if cancelled
                    showEditNameDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary, // Use primary color for icons
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontSize = 17.sp
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}