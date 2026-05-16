package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TraktAuthManager - Handles Trakt.tv OAuth2 authentication
 * 
 * Documentation: https://trakt.docs.apiary.io/#reference/authentication
 */
object TraktAuthManager {

    private const val TAG = "TraktAuthManager"
    
    // Trakt credentials (provided by user)
    const val TRAKT_CLIENT_ID = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
    const val TRAKT_CLIENT_SECRET = "12c597436f61997d8fcb31d246af7400359533d0411374f456af6df2bf7313d9"
    
    // OAuth endpoints (public for use by other components)
    const val TRAKT_AUTH_URL = "https://trakt.tv/oauth/device"
    const val TRAKT_TOKEN_URL = "https://api.trakt.tv/oauth/device/token"
    const val TRAKT_DEVICE_CODE_URL = "https://api.trakt.tv/oauth/device/code"
    
    // Redirect URI for Device authentication (Out-of-band)
    const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    
    // Scopes
    const val SCOPE = "SCROBBLE,SYNC,SETTINGS"
    
    // StateFlow for auth state
    private val _isTraktAuthenticated = MutableStateFlow(false)
    val isTraktAuthenticated: StateFlow<Boolean> = _isTraktAuthenticated
    
    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken
    
    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken
    
    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // OkHttpClient for network requests
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Device code flow state
    private var deviceCode: String? = null
    private var userCode: String? = null
    private var intervalMs: Int = 5000
    
    // SharedPreferences for token storage
    private const val PREFS_NAME = "trakt_auth_prefs"
    private const val PREF_ACCESS_TOKEN = "access_token"
    private const val PREF_REFRESH_TOKEN = "refresh_token"
    private const val PREF_EXPIRES_AT = "expires_at"
    private const val PREF_USER_NAME = "user_name"
    
    private var sharedPreferences: SharedPreferences? = null
    
    /**
     * Get singleton instance (for compatibility with existing code)
     */
    fun getInstance(context: Context?): TraktAuthManager {
        if (context != null && sharedPreferences == null) {
            init(context)
        }
        return this
    }
    
    /**
     * Initialize TraktAuthManager
     */
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadStoredTokens()
    }
    
    /**
     * Load tokens from SharedPreferences
     */
    private fun loadStoredTokens() {
        val accessToken = sharedPreferences?.getString(PREF_ACCESS_TOKEN, null)
        val refreshToken = sharedPreferences?.getString(PREF_REFRESH_TOKEN, null)
        val expiresAt = sharedPreferences?.getLong(PREF_EXPIRES_AT, 0) ?: 0
        val userName = sharedPreferences?.getString(PREF_USER_NAME, null)
        
        if (accessToken != null && System.currentTimeMillis() < expiresAt) {
            _accessToken.value = accessToken
            _refreshToken.value = refreshToken
            _userName.value = userName
            _isTraktAuthenticated.value = true
            Log.i(TAG, "Trakt tokens loaded from storage")
        } else if (refreshToken != null) {
            // Try to refresh token
            Log.i(TAG, "Access token expired, attempting refresh")
            Thread {
                kotlinx.coroutines.runBlocking {
                    refreshAccessToken()
                }
            }.start()
        } else {
            Log.i(TAG, "No Trakt tokens found, user not authenticated")
        }
    }
    
    /**
     * Generate OAuth authorization URL for device flow
     */
    fun getAuthorizationUrl(redirectUri: String = REDIRECT_URI): String {
        return "$TRAKT_AUTH_URL?" +
            "client_id=$TRAKT_CLIENT_ID" +
            "&redirect_uri=${Uri.encode(redirectUri)}" +
            "&scope=${Uri.encode(SCOPE)}"
    }
    
    /**
     * Exchange authorization code for tokens
     */
    suspend fun exchangeCodeForTokens(code: String): Boolean {
        return handleCallback(code)
    }
    
    /**
     * Get username
     */
    fun getUsername(): String? {
        return _userName.value
    }
    
    /**
     * Handle OAuth callback and exchange code for tokens
     */
    suspend fun handleCallback(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Exchanging authorization code for tokens")
            _isLoading.value = true
            
            val body = FormBody.Builder()
                .add("code", code)
                .add("client_id", TRAKT_CLIENT_ID)
                .add("client_secret", TRAKT_CLIENT_SECRET)
                .add("redirect_uri", REDIRECT_URI)
                .add("grant_type", "authorization_code")
                .build()
            
            val request = Request.Builder()
                .url(TRAKT_TOKEN_URL)
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                saveTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn = json.getInt("expires_in"),
                    userName = json.optJSONObject("user")?.optString("username")
                )
                Log.i(TAG, "Trakt authentication successful")
                _isLoading.value = false
                true
            } else {
                Log.e(TAG, "Token exchange failed: ${response.code}")
                _isLoading.value = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error: ${e.message}")
            _isLoading.value = false
            false
        }
    }
    
    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        val currentRefreshToken = _refreshToken.value ?: return@withContext false
        
        try {
            Log.i(TAG, "Refreshing Trakt access token")
            
            val body = FormBody.Builder()
                .add("refresh_token", currentRefreshToken)
                .add("client_id", TRAKT_CLIENT_ID)
                .add("client_secret", TRAKT_CLIENT_SECRET)
                .add("grant_type", "refresh_token")
                .build()
            
            val request = Request.Builder()
                .url(TRAKT_TOKEN_URL)
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                saveTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn = json.getInt("expires_in"),
                    userName = json.optJSONObject("user")?.optString("username")
                )
                Log.i(TAG, "Trakt token refresh successful")
                true
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code}")
                clearTokens()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error: ${e.message}")
            clearTokens()
            false
        }
    }
    
    /**
     * Save tokens to SharedPreferences
     */
    private fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int, userName: String?) {
        sharedPreferences?.edit()?.apply {
            putString(PREF_ACCESS_TOKEN, accessToken)
            putString(PREF_REFRESH_TOKEN, refreshToken)
            putLong(PREF_EXPIRES_AT, System.currentTimeMillis() + (expiresIn * 1000L))
            putString(PREF_USER_NAME, userName)
            apply()
        }
        
        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
        _userName.value = userName
        _isTraktAuthenticated.value = true
    }
    
    /**
     * Clear stored tokens (sign out)
     */
    fun clearTokens() {
        sharedPreferences?.edit()?.clear()?.apply()
        _accessToken.value = null
        _refreshToken.value = null
        _userName.value = null
        _isTraktAuthenticated.value = false
        Log.i(TAG, "Trakt tokens cleared")
    }
    
    /**
     * Get current access token (with auto-refresh if needed)
     */
    suspend fun getValidAccessToken(): String? {
        if (_accessToken.value == null) return null
        
        // Check if token is about to expire (within 5 minutes)
        val expiresAt = sharedPreferences?.getLong(PREF_EXPIRES_AT, 0) ?: 0
        if (System.currentTimeMillis() > expiresAt - 300000) {
            if (!refreshAccessToken()) {
                return null
            }
        }
        
        return _accessToken.value
    }
    
    /**
     * Get current access token synchronously (without auto-refresh)
     */
    fun getValidAccessTokenSync(): String? {
        return _accessToken.value
    }
    
    /**
     * Sign out from Trakt
     */
    fun signOut() {
        clearTokens()
    }
    
    /**
     * Get HTTP client for use in other components
     */
    fun getHttpClient(): OkHttpClient = client

    /**
     * Check if this is a callback URL from Trakt OAuth
     */
    fun isCallbackUrl(url: String): Boolean {
        // Check for OOB redirect URI or code parameter
        return url.startsWith(REDIRECT_URI) || 
               url.contains("code=") || 
               url.contains("trakt.tv/oauth/authorized")
    }
    
    /**
     * Extract code from callback URL
     */
    fun extractCodeFromUrl(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("code")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting code from URL: ${e.message}")
            null
        }
    }
}