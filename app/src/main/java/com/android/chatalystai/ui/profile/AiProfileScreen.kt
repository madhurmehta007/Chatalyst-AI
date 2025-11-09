package com.android.chatalystai.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.android.chatalystai.data.model.User
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProfileScreen(
    viewModel: AiProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onViewAvatar: (String) -> Unit // *** ADDED ***
) {
    val user by viewModel.user.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorEvent.collectAsState()
    val context = LocalContext.current

    val avatarUrl by remember(user?.avatarUrl, user?.avatarUploadTimestamp) {
        mutableStateOf(user?.resolveAvatarUrl() ?: "https://ui-avatars.com/api/?name=?")
    }

    val scope = rememberCoroutineScope()
    var showAvatarOptions by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.updateUserAvatar(uri)
                scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                    if (!bottomSheetState.isVisible) showAvatarOptions = false
                }
            }
        }
    )

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.name ?: "AI Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (avatarUrl.isNotBlank()) {
                                    onViewAvatar(avatarUrl)
                                }
                            }, // *** MODIFICATION: Click to view ***
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Filled.Person),
                        error = rememberVectorPainter(Icons.Filled.Person)
                    )
                    // *** MODIFICATION: Click edit icon to show options ***
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                            .clickable { showAvatarOptions = true }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Avatar",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                ProfileListItem(
                    icon = Icons.Default.Person,
                    title = "Name",
                    subtitle = user!!.name,
                    onClick = { }
                )
                Spacer(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileListItem(
                    icon = Icons.Default.Badge,
                    title = "Personality",
                    subtitle = user!!.personality,
                    onClick = { }
                )
                Spacer(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileListItem(
                    icon = Icons.Default.Info,
                    title = "Background",
                    subtitle = user!!.backgroundStory,
                    onClick = { }
                )
                Spacer(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileListItem(
                    icon = Icons.Default.Interests,
                    title = "Interests",
                    subtitle = user!!.interests,
                    onClick = { }
                )
                Spacer(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileListItem(
                    icon = Icons.Default.Style,
                    title = "Speaking Style",
                    subtitle = user!!.speakingStyle,
                    onClick = { }
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .wrapContentSize(Alignment.Center)
            )
        }
    }

    if (showAvatarOptions) {
        ProfilePictureOptionsSheet(
            onDismiss = { showAvatarOptions = false },
            onPickFromGallery = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSelectSuggestion = { url ->
                viewModel.updateUserAvatarFromUrl(url)
                scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                    if (!bottomSheetState.isVisible) showAvatarOptions = false
                }
            }
        )
    }
}

@Composable
private fun ProfileListItem(
    icon: ImageVector,
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
            tint = MaterialTheme.colorScheme.primary,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePictureOptionsSheet(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSelectSuggestion: (String) -> Unit
) {
    val suggestions = listOf(
        "adventurer" to "https://api.dicebear.com/7.x/adventurer/avif?seed=Felix",
        "bottts" to "https://api.dicebear.com/7.x/bottts/avif?seed=Gizmo",
        "pixel-art" to "https://api.dicebear.com/7.x/pixel-art/avif?seed=Mario",
        "fun-emoji" to "https://api.dicebear.com/7.x/fun-emoji/avif?seed=Joy",
        "lorelei" to "https://api.dicebear.com/7.x/lorelei/avif?seed=Luna",
        "miniavs" to "https://api.dicebear.com/7.x/miniavs/avif?seed=Alex"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Update Profile Picture", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            ProfileListItem(
                icon = Icons.Default.Image,
                title = "Choose from Gallery",
                subtitle = "Upload your own photo",
                onClick = onPickFromGallery
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                "Or pick a style",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(suggestions) { (name, url) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = url,
                            contentDescription = name,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelectSuggestion(url) }
                        )
                        Text(name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}