// file: app/src/main/java/com/android/bakchodai/ui/auth/AuthViewModel.kt

package com.android.bakchodai.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
                // Await the completion of the sign-in task.
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { firebaseUser ->
                    // Authentication token is now ready and propagated.
                    _user.value = firebaseUser
                    // Explicitly set the state here, now that the auth token is ready.
                    _authState.value = AuthState.LOGGED_IN
                }
            } catch (e: Exception) {
                // TODO: Handle login error (e.g., show a toast message).
                // Log.e("AuthViewModel", "Login failed", e)
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
                // Await the creation of the user in Firebase Auth.
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let { firebaseUser ->
                    // Update Firebase Auth user profile with display name.
                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    firebaseUser.updateProfile(profileUpdates).await()

                    // Reload the user to get the updated displayName.
                    firebaseUser.reload().await()

                    val encodedName = try {
                        URLEncoder.encode(name, "UTF-8")
                    } catch (e: Exception) {
                        name
                    }
                    val avatarUrl = "https://api.dicebear.com/7.x/avataaars/avif?seed=${encodedName}"

                    // Add the user to our application's database.
                    val newUser = User(
                        uid = firebaseUser.uid,
                        name = name,
                        avatarUrl = avatarUrl // Save the URL
                    )
                    repository.addUser(newUser) // Suspend function called safely in coroutine.

                    // Authentication token and user data are now ready.
                    _user.value = firebaseUser
                    // Explicitly set the state here.
                    _authState.value = AuthState.LOGGED_IN
                }
            } catch (e: Exception) {
                // ... (catch block)
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
        auth.signOut()
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
                // TODO: Handle error during username update.
                // Log.e("AuthViewModel", "Failed to update user name", e)
            }
        }
    }
}