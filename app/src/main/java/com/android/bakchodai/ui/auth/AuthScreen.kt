package com.android.bakchodai.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AuthScreen(authViewModel: AuthViewModel) {
    var showLogin by remember { mutableStateOf(true) }

    if (showLogin) {
        LoginScreen(
            onLoginClick = { email, password -> authViewModel.login(email, password) },
            onSignUpClick = { showLogin = false }
        )
    } else {
        SignUpScreen {
            name, email, password -> authViewModel.signUp(name, email, password)
        }
    }
}
