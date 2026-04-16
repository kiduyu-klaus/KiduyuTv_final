package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AuthManager - Handles Firebase Authentication with Google Sign-In.
 * 
 * This singleton manages user authentication state and provides methods for:
 * - Signing in with Google
 * - Signing out
 * - Getting current user information
 * - Listening to auth state changes
 * 
 * Usage:
 *   // Initialize once in Application
 *   AuthManager.init(context)
 * 
 *   // Sign in
 *   AuthManager.signInWithGoogle(activity, idToken)
 * 
 *   // Get current user
 *   val user = AuthManager.currentUser
 * 
 *   // Observe auth state
 *   AuthManager.authStateFlow.collect { user -> ... }
 */
object AuthManager {

    private const val TAG = "AuthManager"
    
    // Default web client ID - Replace with your actual Google OAuth client ID
    // This is found in google-services.json or Google Cloud Console
    private const val DEFAULT_WEB_CLIENT_ID = "109926033937-dsl207opc1lsa3fnonim2sfmnc0o9hjk.apps.googleusercontent.com"
    
    private var firebaseAuth: FirebaseAuth? = null
    private var googleSignInClient: GoogleSignInClient? = null
    private var applicationContext: Context? = null
    
    // Auth state as a StateFlow for reactive updates
    private val _authStateFlow = MutableStateFlow<FirebaseUser?>(null)
    val authStateFlow: StateFlow<FirebaseUser?> = _authStateFlow.asStateFlow()
    
    // User display name
    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()
    
    // User email
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()
    
    // User photo URL
    private val _userPhotoUrl = MutableStateFlow<String?>(null)
    val userPhotoUrl: StateFlow<String?> = _userPhotoUrl.asStateFlow()
    
    // Is user signed in
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Initialize AuthManager.
     * Call this once in your Application class.
     */
    fun init(context: Context, webClientId: String = DEFAULT_WEB_CLIENT_ID) {
        applicationContext = context.applicationContext
        
        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()
        
        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        
        // Set up auth state listener
        firebaseAuth?.addAuthStateListener { auth ->
            updateAuthState(auth.currentUser)
        }
        
        // Initialize with current user
        updateAuthState(firebaseAuth?.currentUser)
    }
    
    /**
     * Update authentication state from Firebase user.
     */
    private fun updateAuthState(user: FirebaseUser?) {
        _authStateFlow.value = user
        _isSignedIn.value = user != null
        _userDisplayName.value = user?.displayName
        _userEmail.value = user?.email
        _userPhotoUrl.value = user?.photoUrl?.toString()
    }
    
    /**
     * Get the Google Sign-In client for launching the sign-in intent.
     */
    fun getGoogleSignInClient(): GoogleSignInClient? = googleSignInClient
    
    /**
     * Get the current Firebase user, if signed in.
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth?.currentUser
    
    /**
     * Sign in with Google ID token.
     * This is called after the Google Sign-In intent returns successfully.
     * 
     * @param idToken The Google ID token from the sign-in result
     * @param onSuccess Callback for successful sign-in
     * @param onFailure Callback for failed sign-in
     */
    fun signInWithGoogle(
        idToken: String,
        onSuccess: ((FirebaseUser) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        _isLoading.value = true
        
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        firebaseAuth?.signInWithCredential(credential)
            ?.addOnCompleteListener { task ->
                _isLoading.value = false
                
                if (task.isSuccessful) {
                    val user = firebaseAuth?.currentUser
                    user?.let {
                        // Update FirebaseManager with user ID for data sync
                        FirebaseManager.init(it.uid)
                        onSuccess?.invoke(it)
                    }
                } else {
                    task.exception?.let { exception ->
                        onFailure?.invoke(exception)
                    }
                }
            }
    }
    
    /**
     * Handle the Google Sign-In activity result.
     * Call this from your Activity's onActivityResult.
     * 
     * @param requestCode The request code from onActivityResult
     * @param resultCode The result code from onActivityResult
     * @param data The intent data from onActivityResult
     * @param onSuccess Callback for successful sign-in
     * @param onFailure Callback for failed sign-in
     * @return true if the result was handled, false otherwise
     */
    fun handleGoogleSignInResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onSuccess: ((FirebaseUser) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): Boolean {
        // Default request code for Google Sign-In
        val GOOGLE_SIGN_IN_RC = 9001
        
        if (requestCode == GOOGLE_SIGN_IN_RC) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { token ->
                    signInWithGoogle(
                        idToken = token,
                        onSuccess = onSuccess,
                        onFailure = onFailure
                    )
                } ?: run {
                    onFailure?.invoke(Exception("Google ID token is null"))
                }
            } catch (e: ApiException) {
                onFailure?.invoke(e)
            }
            return true
        }
        return false
    }
    
    /**
     * Sign out the current user.
     * 
     * @param onComplete Optional callback when sign-out is complete
     */
    fun signOut(onComplete: (() -> Unit)? = null) {
        _isLoading.value = true
        
        // Sign out from Firebase
        firebaseAuth?.signOut()
        
        // Sign out from Google
        googleSignInClient?.signOut()
            ?.addOnCompleteListener { task ->
                _isLoading.value = false
                onComplete?.invoke()
                
                if (!task.isSuccessful) {
                    // Log error if needed
                }
            }
        
        // Revert to anonymous device ID for Firebase Manager
        val deviceId = SettingsManager(applicationContext!!).getDeviceId()
        FirebaseManager.init(deviceId)
    }
    
    /**
     * Delete the user's account.
     * This will permanently delete the Firebase account and all associated data.
     * 
     * @param onSuccess Callback for successful account deletion
     * @param onFailure Callback for failed deletion
     */
    fun deleteAccount(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        _isLoading.value = true
        
        currentUser?.delete()
            ?.addOnCompleteListener { task ->
                _isLoading.value = false
                
                if (task.isSuccessful) {
                    // Sign out from Google as well
                    googleSignInClient?.revokeAccess()
                    
                    // Revert to anonymous
                    val deviceId = SettingsManager(applicationContext!!).getDeviceId()
                    FirebaseManager.init(deviceId)
                    
                    onSuccess?.invoke()
                } else {
                    task.exception?.let { exception ->
                        onFailure?.invoke(exception)
                    }
                }
            }
    }
    
    /**
     * Get user info as a map (useful for displaying user details).
     */
    fun getUserInfo(): Map<String, Any?> {
        return mapOf(
            "uid" to currentUser?.uid,
            "email" to currentUser?.email,
            "displayName" to currentUser?.displayName,
            "photoUrl" to currentUser?.photoUrl?.toString(),
            "isSignedIn" to (currentUser != null)
        )
    }
    
    /**
     * Handle phone authorization on TV.
     * When a user authorizes the TV from their phone, this method is called
     * to update the AuthManager state to reflect the signed-in status.
     * 
     * Since TV doesn't use Firebase Auth directly (it receives UID from phone),
     * we create a mock user object to track the signed-in state.
     * 
     * @param uid The Firebase UID received from phone authorization
     * @param displayName Optional display name (can be fetched from Firebase or passed from phone)
     * @param email Optional email (can be fetched from Firebase or passed from phone)
     * @param photoUrl Optional photo URL (can be fetched from Firebase or passed from phone)
     */
    fun onPhoneAuthorized(
        uid: String,
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null
    ) {
        // Update FirebaseManager with the authorized UID
        FirebaseManager.init(uid)
        
        // Update auth state to signed in
        // Note: We don't have a real FirebaseUser on TV since TV doesn't use Firebase Auth
        // Instead, we update the StateFlows directly to reflect the signed-in state
        _isSignedIn.value = true
        _userDisplayName.value = displayName ?: "TV User"
        _userEmail.value = email ?: ""
        _userPhotoUrl.value = photoUrl
        
        // Also update the auth state flow with a placeholder
        // This helps maintain consistency with the existing UI expectations
        val mockUser = object : FirebaseUser() {
            override fun getUid(): String = uid
            override fun getDisplayName(): String? = displayName ?: "TV User"
            override fun getEmail(): String? = email
            override fun getPhotoUrl(): android.net.Uri? = photoUrl?.let { android.net.Uri.parse(it) }
            override fun isEmailVerified(): Boolean = false
            override fun getProviderData(): MutableList<com.google.firebase.auth.UserInfo> = mutableListOf()
            override fun getTenantId(): String? = null
            override fun getMetadata(): com.google.firebase.auth.UserMetadata? = null
            override fun getMultiFactor(): com.google.firebase.auth.MultiFactor? = null
            override fun plus(codeName: String): com.google.firebase.auth.UserProfileChangeRequest = 
                com.google.firebase.auth.UserProfileChangeRequest.Builder().build()
            override fun updateEmail(newEmail: String): com.google.firebase.auth.Task<Void> = 
                com.google.firebase.auth.Task()
            override fun updatePassword(newPassword: String): com.google.firebase.auth.Task<Void> = 
                com.google.firebase.auth.Task()
            override fun updateProfile(request: com.google.firebase.auth.UserProfileChangeRequest): com.google.firebase.auth.Task<Void> = 
                com.google.firebase.auth.Task()
            override fun delete(): com.google.firebase.auth.Task<Void> = 
                com.google.firebase.auth.Task()
            override fun reauthenticate(credential: com.google.firebase.auth.AuthCredential): com.google.firebase.auth.Task<Void> = 
                com.google.firebase.auth.Task()
            override fun verifyBeforeUpdateEmail(newEmail: String): com.google.firebase.auth.Task<Void> = 
                com.google.firebase.auth.Task()
            override fun linkWithCredential(credential: com.google.firebase.auth.AuthCredential): com.google.firebase.auth.Task<com.google.firebase.auth.AuthResult> = 
                com.google.firebase.auth.Task()
            override fun unlink(provider: String): com.google.firebase.auth.Task<FirebaseUser> = 
                com.google.firebase.auth.Task()
            override fun isLinked(provider: String): Boolean = false
            override fun getProviderId(): String = "firebase"
        }
        _authStateFlow.value = mockUser
        
        Log.i(TAG, "Phone authorization successful for UID: $uid")
    }
    
    /**
     * Sign out from phone authorization.
     * Reverts to anonymous device ID.
     */
    fun signOutFromPhone(onComplete: (() -> Unit)? = null) {
        // Update auth state to signed out
        _isSignedIn.value = false
        _userDisplayName.value = null
        _userEmail.value = null
        _userPhotoUrl.value = null
        _authStateFlow.value = null
        
        // Revert to anonymous device ID for Firebase Manager
        if (applicationContext != null) {
            val deviceId = SettingsManager(applicationContext!!).getDeviceId()
            FirebaseManager.init(deviceId)
        }
        
        onComplete?.invoke()
        Log.i(TAG, "Signed out from phone authorization")
    }
}

