package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
 * - Persisting login across cache clears via SharedPreferences
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
    
    // SharedPreferences constants for persistent login
    private const val PREFS_NAME = "auth_prefs"
    private const val PREF_UID = "uid"
    private const val PREF_DISPLAY_NAME = "display_name"
    private const val PREF_EMAIL = "email"
    private const val PREF_PHOTO_URL = "photo_url"
    private const val PREF_IS_SIGNED_IN = "is_signed_in"
    private const val PREF_AUTH_TYPE = "auth_type" // "google", "custom", or legacy "phone"
    
    private var firebaseAuth: FirebaseAuth? = null
    private var googleSignInClient: GoogleSignInClient? = null
    private var applicationContext: Context? = null
    private var sharedPreferences: SharedPreferences? = null
    
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
    
    // User UID (for TV phone authorization)
    private val _userUid = MutableStateFlow<String?>(null)
    val userUid: StateFlow<String?> = _userUid.asStateFlow()
    
    // Is user signed in
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Initialize AuthManager.
     * Call this once in your Application class.
     * Loads persisted login state from SharedPreferences if available.
     */
    fun init(context: Context, webClientId: String = DEFAULT_WEB_CLIENT_ID) {
        applicationContext = context.applicationContext
        
        // Initialize SharedPreferences for persistent login
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
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
        
        // Initialize with current user (Firebase auth)
        val firebaseUser = firebaseAuth?.currentUser
        if (firebaseUser != null) {
            // User is signed in via Google on mobile
            updateAuthState(firebaseUser)
        } else {
            // Check for persisted TV phone authorization login
            loadPersistedLogin()
        }
    }
    
    /**
     * Load persisted login state from SharedPreferences.
     * This restores TV phone authorization login after cache clear.
     */
    private fun loadPersistedLogin() {
        val prefs = sharedPreferences ?: return
        
        val isSignedIn = prefs.getBoolean(PREF_IS_SIGNED_IN, false)
        if (!isSignedIn) {
            Log.i(TAG, "No persisted login found in SharedPreferences")
            return
        }
        
        val uid = prefs.getString(PREF_UID, null)
        val displayName = prefs.getString(PREF_DISPLAY_NAME, null)
        val email = prefs.getString(PREF_EMAIL, null)
        val photoUrl = prefs.getString(PREF_PHOTO_URL, null)
        val authType = prefs.getString(PREF_AUTH_TYPE, null)
        
        if (uid != null) {
            Log.i(TAG, "Restoring persisted login from SharedPreferences: uid=$uid, authType=$authType")
            
            // CRITICAL: Update auth state FIRST, then Firebase components
            // This ensures the StateFlow emits the new value before FirebaseSyncManager's collector runs
            _isSignedIn.value = true
            _userDisplayName.value = displayName
            _userEmail.value = email
            _userPhotoUrl.value = photoUrl
            _userUid.value = uid
            
            // Update FirebaseManager with the persisted UID
            // This MUST be called after updating StateFlows to ensure consistency
            FirebaseManager.init(uid)
            Log.i(TAG, "FirebaseManager updated with persisted UID: $uid")
            
            // Try to update FirebaseSyncManager to restart sync with the correct UID
            // Note: This is a best-effort update since FirebaseSyncManager may not be initialized yet
            try {
                FirebaseSyncManager.updateFirebaseManagerUserId(uid)
            } catch (e: Exception) {
                Log.i(TAG, "FirebaseSyncManager not yet initialized, FirebaseManager already updated with UID: $uid")
            }
            
            Log.i(TAG, "Persisted login restored successfully: displayName=$displayName, isSignedIn=$_isSignedIn.value")
        } else {
            Log.w(TAG, "Persisted login found but UID is null, clearing invalid state")
            clearPersistedLogin()
        }
    }
    
    /**
     * Save login state to SharedPreferences for persistence across cache clears.
     */
    private fun savePersistedLogin(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?,
        authType: String
    ) {
        val prefs = sharedPreferences ?: run {
            Log.e(TAG, "SharedPreferences not initialized, cannot save login")
            return
        }
        
        prefs.edit().apply {
            putString(PREF_UID, uid)
            putString(PREF_DISPLAY_NAME, displayName)
            putString(PREF_EMAIL, email)
            putString(PREF_PHOTO_URL, photoUrl)
            putBoolean(PREF_IS_SIGNED_IN, true)
            putString(PREF_AUTH_TYPE, authType)
            apply()
        }
        
        Log.i(TAG, "Login saved to SharedPreferences: uid=$uid, authType=$authType")
    }
    
    /**
     * Clear persisted login state from SharedPreferences.
     */
    private fun clearPersistedLogin() {
        val prefs = sharedPreferences ?: run {
            Log.e(TAG, "SharedPreferences not initialized, cannot clear login")
            return
        }
        
        prefs.edit().apply {
            remove(PREF_UID)
            remove(PREF_DISPLAY_NAME)
            remove(PREF_EMAIL)
            remove(PREF_PHOTO_URL)
            remove(PREF_IS_SIGNED_IN)
            remove(PREF_AUTH_TYPE)
            apply()
        }
        
        Log.i(TAG, "Persisted login cleared from SharedPreferences")
    }
    
    /**
     * Update authentication state from Firebase user.
     */
    private fun updateAuthState(user: FirebaseUser?) {
        // Ignore anonymous users for UI signed-in state.
        // TV uses anonymous auth only as a temporary step before custom-token sign-in.
        if (user == null || user.isAnonymous) {
            _authStateFlow.value = null
            _isSignedIn.value = false
            _userDisplayName.value = null
            _userEmail.value = null
            _userPhotoUrl.value = null
            _userUid.value = null
            return
        }

        _authStateFlow.value = user
        _isSignedIn.value = true
        _userDisplayName.value = user.displayName
        _userEmail.value = user.email
        _userPhotoUrl.value = user.photoUrl?.toString()
        _userUid.value = user.uid

        // Keep all authenticated sessions (Google + custom token) on the same UID path.
        FirebaseManager.init(user.uid)
        try {
            FirebaseSyncManager.updateFirebaseManagerUserId(user.uid)
        } catch (e: Exception) {
            Log.i(TAG, "FirebaseSyncManager not yet initialized, FirebaseManager updated with UID: ${user.uid}")
        }

        savePersistedLogin(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString(),
            authType = "custom"
        )
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
     * Get the persisted UID (for TV phone authorization).
     */
    val currentUid: String?
        get() = _userUid.value
    
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

        firebaseAuth?.signOut()
        googleSignInClient?.signOut()
        clearPersistedLogin()

        if (applicationContext != null) {
            val deviceId = SettingsManager(applicationContext!!).getDeviceId()
            FirebaseManager.init(deviceId)
        }

        _isLoading.value = false
        onComplete?.invoke()
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
                    
                    // Clear persisted login
                    clearPersistedLogin()
                    
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
            "uid" to (currentUser?.uid ?: _userUid.value),
            "email" to (currentUser?.email ?: _userEmail.value),
            "displayName" to (currentUser?.displayName ?: _userDisplayName.value),
            "photoUrl" to (currentUser?.photoUrl?.toString() ?: _userPhotoUrl.value),
            "isSignedIn" to _isSignedIn.value
        )
    }
    
    /**
     * Handle phone authorization on TV.
     * When a user authorizes the TV from their phone, this method is called
     * to update the AuthManager state to reflect the signed-in status.
     * 
     * The phone app sends the user's profile data (displayName, email, photoUrl)
     * along with the UID, so the TV can display the same account information.
     * 
     * CRITICAL: This method ensures that both the TV and mobile app use the SAME
     * Firebase path (users/{uid}/) for data operations, enabling data sharing.
     * 
     * The login is persisted to SharedPreferences so it survives cache clears.
     * 
     * @param uid The Firebase Auth UID received from phone authorization
     * @param displayName User's display name from phone's Google account
     * @param email User's email from phone's Google account
     * @param photoUrl User's profile photo URL from phone's Google account
     */
    fun onPhoneAuthorized(
        uid: String,
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null
    ) {
        // CRITICAL: Update FirebaseManager with the authorized UID
        // This ensures TV and mobile save to the SAME Firebase path
        // Both apps will now use: users/{uid}/myList, users/{uid}/savedCompanies, etc.
        FirebaseManager.init(uid)
        
        // Also update FirebaseSyncManager to restart sync with the correct user ID
        // This ensures listeners are set up for the correct user path
        // Note: We need to check if FirebaseSyncManager is initialized before calling this
        try {
            FirebaseSyncManager.updateFirebaseManagerUserId(uid)
        } catch (e: Exception) {
            // FirebaseSyncManager might not be initialized yet, FirebaseManager.init() is sufficient
            Log.i(TAG, "FirebaseSyncManager not yet initialized, FirebaseManager updated with UID: $uid")
        }
        
        // Update auth state to signed in
        // Note: We don't have a real FirebaseUser on TV since TV doesn't use Firebase Auth.
        // Instead, we update the StateFlows directly to reflect the signed-in state.
        // The UI observes these StateFlows to display account information.
        _isSignedIn.value = true
        _userDisplayName.value = displayName ?: "TV User"
        _userEmail.value = email
        _userPhotoUrl.value = photoUrl
        _userUid.value = uid
        
        // Save to SharedPreferences for persistence across cache clears
        savePersistedLogin(
            uid = uid,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl,
            authType = "phone"
        )
        
        Log.i(TAG, "Phone authorization successful for UID: $uid, displayName: $displayName")
        Log.i(TAG, "Both TV and mobile will now use Firebase path: users/$uid/")
        Log.i(TAG, "Login persisted to SharedPreferences for cache clear survival")
    }
    
    /**
     * Sign out from phone authorization.
     * Reverts to anonymous device ID and clears persisted login.
     */
    fun signOutFromPhone(onComplete: (() -> Unit)? = null) {
        signOut(onComplete)
    }
}
