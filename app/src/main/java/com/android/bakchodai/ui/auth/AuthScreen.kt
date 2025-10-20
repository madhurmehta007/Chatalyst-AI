package com.android.bakchodai.ui.auth

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuthScreen(authViewModel: AuthViewModel, isLoading: Boolean) {
    var showLogin by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        authViewModel.errorEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showLogin) {
        LoginScreen(
            isLoading = isLoading,
            onLoginClick = { email, password -> authViewModel.login(email, password) },
            onSignUpClick = { showLogin = false }
        )
    } else {
        SignUpScreen(
            isLoading = isLoading,
            onSignUpClick = { name, email, password -> authViewModel.signUp(name, email, password)
            },
            onBackToLoginClick = { showLogin = true }
        )
    }
}