package com.android.bakchodai.ui.auth

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.android.bakchodai.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
fun AuthScreen(authViewModel: AuthViewModel, isLoading: Boolean) {
    var showLogin by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val credentialManager = remember { CredentialManager.create(context) }


    val onGoogleSignInClick: () -> Unit = {
        scope.launch {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            try {
                val result = credentialManager.getCredential(
                    context = context,
                    request = request
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    authViewModel.signInWithGoogle(idToken)
                }
            } catch (e: Exception) {
                Log.e("AuthScreen", "Google Sign-In failed", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.errorEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showLogin) {
        LoginScreen(
            isLoading = isLoading,
            onLoginClick = { email, password -> authViewModel.login(email, password) },
            onSignUpClick = { showLogin = false },
            onGoogleSignInClick = onGoogleSignInClick
        )
    } else {
        SignUpScreen(
            isLoading = isLoading,
            onSignUpClick = { name, email, password -> authViewModel.signUp(name, email, password)
            },
            onBackToLoginClick = { showLogin = true },
            onGoogleSignInClick = onGoogleSignInClick
        )
    }
}