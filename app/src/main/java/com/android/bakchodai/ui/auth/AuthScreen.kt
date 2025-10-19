package com.android.bakchodai.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AuthScreen(authViewModel: AuthViewModel, isLoading: Boolean) {
    var showLogin by remember { mutableStateOf(true) }

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
            }
        )
    }
}