// file: app/src/main/java/com/android/bakchodai/ui/auth/AuthViewModel.kt

package com.android.bakchodai.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Represents the authentication states of the user.
 */
enum class AuthState {
    /** User is currently logged out. */
    LOGGED_OUT,

    /** Authentication state is being determined (e.g., on app start). */
    INITIALIZING,

    /** User is logged in. */
    LOGGED_IN
}

/**
 * ViewModel responsible for handling user authentication logic.
 * It interacts with Firebase Authentication and the ConversationRepository for user data.
 *
 * @param repository The repository for managing conversation and user data.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: ConversationRepository) : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth

    // StateFlow to represent the current authentication state.
    private val _authState = MutableStateFlow(AuthState.INITIALIZING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // StateFlow to hold the current FirebaseUser, or null if logged out.
    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    // StateFlow for loading state during login/signup
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    // Flag to prevent the AuthStateListener from causing a race condition during login/signup.
    private var isProcessingAuthAction = false

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            _user.value = firebaseUser

            // If an explicit login/signup is in progress, let that function control the state
            // to prevent the race condition.
            if (isProcessingAuthAction) {
                return@addAuthStateListener
            }

            if (firebaseUser == null) {
                _authState.value = AuthState.LOGGED_OUT
            } else {
                _authState.value = AuthState.LOGGED_IN
            }
        }
    }

    /**
     * Logs in a user with the provided email and password.
     *
     * @param email The user's email.
     * @param password The user's password.
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            isProcessingAuthAction = true
            _isLoading.value = true
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { firebaseUser ->
                    _user.value = firebaseUser
                    _authState.value = AuthState.LOGGED_IN
                }
            } catch (e: Exception) {
                // ... (error handling remains the same) ...
                when (e) {
                    is FirebaseAuthInvalidUserException,
                    is FirebaseAuthInvalidCredentialsException -> {
                        _errorEvent.emit("Invalid email or password")
                    }
                    else -> {
                        _errorEvent.emit("Login failed. Please try again.")
                    }
                }
                _authState.value = AuthState.LOGGED_OUT
            } finally {
                _isLoading.value = false
                isProcessingAuthAction = false
            }
        }
    }

    /**
     * Creates a new user with the given name, email, and password.
     *
     * @param name The desired display name for the new user.
     * @param email The user's email.
     * @param password The user's password.
     */
    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            isProcessingAuthAction = true
            _isLoading.value = true
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let { firebaseUser ->

                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    firebaseUser.updateProfile(profileUpdates).await()

                    firebaseUser.reload().await()

                    val encodedName = try {
                        URLEncoder.encode(name, "UTF-8")
                    } catch (e: Exception) {
                        name
                    }
                    val avatarUrl = "https://api.dicebear.com/7.x/avataaars/avif?seed=${encodedName}"

                    val newUser = User(
                        uid = firebaseUser.uid,
                        name = name,
                        avatarUrl = avatarUrl,
                        isOnline = true, // Set to online on creation
                        lastSeen = System.currentTimeMillis() // Set timestamp
                    )
                    repository.addUser(newUser)

                    _user.value = firebaseUser // Set the reloaded user
                    _authState.value = AuthState.LOGGED_IN
                }
            } catch (e: Exception) {
                // ... (error handling remains the same) ...
                when (e) {
                    is FirebaseAuthUserCollisionException -> {
                        _errorEvent.emit("User account already exists")
                    }
                    is FirebaseAuthWeakPasswordException -> {
                        _errorEvent.emit("Password is too weak. Please use at least 6 characters.")
                    }
                    else -> {
                        _errorEvent.emit("Sign up failed. Please try again.")
                    }
                }
            } finally {
                _isLoading.value = false
                isProcessingAuthAction = false
            }
        }
    }

    /**
     * Logs out the current user from Firebase Authentication.
     */
    fun logout() {
        viewModelScope.launch {
            repository.clearAllLocalData()
            auth.signOut()
        }
    }

    /**
     * Updates the display name of the current authenticated user.
     *
     * @param newName The new display name for the user.
     */
    fun updateUserName(newName: String) {
        viewModelScope.launch {
            val firebaseUser = auth.currentUser ?: return@launch // Ensure a user is logged in.
            try {
                val profileUpdates = userProfileChangeRequest {
                    displayName = newName
                }
                firebaseUser.updateProfile(profileUpdates).await() // Update in Firebase Auth.
                repository.updateUserName(firebaseUser.uid, newName) // Update in application database.
            } catch (e: Exception) {
                _errorEvent.emit("Failed to update name. Please try again.")
            }
        }
    }
}