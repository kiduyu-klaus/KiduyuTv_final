package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.clearTokens
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.exchangeCodeForTokens
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.getAuthorizationUrl
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.getValidAccessToken
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.init
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.prefs
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.refreshMutex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TraktAuthManager — handles Trakt.tv OAuth 2.0 (authorization code / OOB flow).
 *
 * Documentation: https://trakt.docs.apiary.io/#reference/authentication
 *
 * Usage:
 *   1. Call [init] once in `Application.onCreate` (or before any other call).
 *   2. Obtain the auth URL via [getAuthorizationUrl], open it in a browser.
 *   3. Let the user paste the returned code, then call [exchangeCodeForTokens].
 *   4. Use [getValidAccessToken] (suspending) wherever an API token is needed.
 */
object TraktAuthManager {

    private const val TAG = "TraktAuthManager"

    // ── Credentials ──────────────────────────────────────────────────────────
    // TODO: Move to BuildConfig (generated from local.properties / CI secrets)
    //       to keep them out of source control.
    const val TRAKT_CLIENT_ID     = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
    const val TRAKT_CLIENT_SECRET = "12c597436f61997d8fcb31d246af7400359533d0411374f456af6df2bf7313d9"

    // ── OAuth endpoints ───────────────────────────────────────────────────────
    const val TRAKT_AUTHORIZE_URL = "https://trakt.tv/oauth/authorize"
    const val TRAKT_TOKEN_URL     = "https://api.trakt.tv/oauth/token"
    const val REDIRECT_URI        = "urn:ietf:wg:oauth:2.0:oob"

    // ── Public state ──────────────────────────────────────────────────────────
    private val _isTraktAuthenticated = MutableStateFlow(false)
    val isTraktAuthenticated: StateFlow<Boolean> = _isTraktAuthenticated

    private val _accessToken  = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName

    private val _userAvatarUrl = MutableStateFlow<String?>(null)
    val userAvatarUrl: StateFlow<String?> = _userAvatarUrl

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Dedicated scope for background token operations (refresh on startup, etc.).
     * SupervisorJob ensures one failed child does not cancel siblings.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serialises concurrent calls to [getValidAccessToken] so only one refresh
     * request is ever in-flight at a time.
     */
    private val refreshMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── SharedPreferences ────────────────────────────────────────────────────

    private const val PREFS_NAME        = "trakt_prefs"
    private const val PREF_ACCESS_TOKEN  = "access_token"
    private const val PREF_REFRESH_TOKEN = "refresh_token"
    private const val PREF_EXPIRES_AT    = "expires_at"
    private const val PREF_USER_NAME     = "user_name"
    private const val PREF_USER_AVATAR   = "user_avatar"

    /**
     * Non-nullable after [init] is called. Accessing any token method before
     * [init] will throw [IllegalStateException] via the [prefs] property.
     */
    private var _prefs: SharedPreferences? = null
    private val prefs: SharedPreferences
        get() = _prefs
            ?: error("TraktAuthManager.init(context) must be called before using this object.")

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Must be called once — ideally in `Application.onCreate` — before any
     * other method on this object.  Subsequent calls are no-ops.
     */
    fun init(context: Context) {
        if (_prefs != null) return

        _prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadStoredTokens()
    }

    /**
     * Convenience accessor kept for call-site compatibility.
     * Prefer calling [init] directly in Application; do not pass `null`.
     */
    fun getInstance(context: Context?): TraktAuthManager {
        if (context != null) init(context)
        return this
    }

    // ── Token persistence ─────────────────────────────────────────────────────

    private fun loadStoredTokens() {
        val storedAccess  = prefs.getString(PREF_ACCESS_TOKEN,  null)
        val storedRefresh = prefs.getString(PREF_REFRESH_TOKEN, null)
        val expiresAt     = prefs.getLong(PREF_EXPIRES_AT, 0L)
        val storedUser    = prefs.getString(PREF_USER_NAME,     null)
        val storedAvatar  = prefs.getString(PREF_USER_AVATAR,   null)

        when {
            storedAccess != null && System.currentTimeMillis() < expiresAt -> {
                _accessToken.value          = storedAccess
                _refreshToken.value         = storedRefresh
                _userName.value             = storedUser
                _userAvatarUrl.value        = storedAvatar
                _isTraktAuthenticated.value = true
                Log.i(TAG, "Trakt tokens loaded from storage")
            }
            storedRefresh != null -> {
                // Token exists but has expired — refresh in the background.
                _refreshToken.value = storedRefresh
                _userName.value     = storedUser
                _userAvatarUrl.value = storedAvatar
                Log.i(TAG, "Access token expired; refreshing in background")
                scope.launch { refreshAccessToken() }
            }
            else -> {
                Log.i(TAG, "No Trakt tokens found — user not authenticated")
            }
        }
    }

    /**
     * Publicly exposes token saving for flows that handle the network response
     * themselves (e.g., Device Code Flow in TraktAuthActivity).
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Int,
        userName: String?,
        userAvatar: String? = null,
    ) {
        prefs.edit()
            .putString(PREF_ACCESS_TOKEN,  accessToken)
            .putString(PREF_REFRESH_TOKEN, refreshToken)
            .putLong(PREF_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1_000L)
            .putString(PREF_USER_NAME, userName)
            .putString(PREF_USER_AVATAR, userAvatar)
            .apply()

        _accessToken.value          = accessToken
        _refreshToken.value         = refreshToken
        _userName.value             = userName
        _userAvatarUrl.value        = userAvatar
        _isTraktAuthenticated.value = true

        // If we don't have an avatar, try to fetch it
        if (userAvatar == null && accessToken.isNotEmpty()) {
            scope.launch { fetchUserSettings() }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds the browser URL the user must visit to authorise the app. */
    fun getAuthorizationUrl(redirectUri: String = REDIRECT_URI): String =
        "$TRAKT_AUTHORIZE_URL?" +
                "client_id=$TRAKT_CLIENT_ID" +
                "&redirect_uri=${Uri.encode(redirectUri)}" +
                "&response_type=code"

    /**
     * Exchanges a one-time authorisation [code] (pasted by the user) for
     * access + refresh tokens.  Returns `true` on success.
     */
    suspend fun exchangeCodeForTokens(code: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true

                val body = JSONObject().run {
                    put("code",          code)
                    put("client_id",     TRAKT_CLIENT_ID)
                    put("client_secret", TRAKT_CLIENT_SECRET)
                    put("redirect_uri",  REDIRECT_URI)
                    put("grant_type",    "authorization_code")
                    toString()
                }.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(TRAKT_TOKEN_URL)
                    .post(body)
                    .build()

                val response     = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    saveTokens(
                        accessToken  = json.getString("access_token"),
                        refreshToken = json.getString("refresh_token"),
                        expiresIn    = json.getInt("expires_in"),
                        userName     = json.optJSONObject("user")
                            ?.optString("username")
                            ?.takeIf { it.isNotBlank() },
                    )
                    Log.i(TAG, "Trakt authentication successful")
                    true
                } else {
                    Log.e(TAG, "Token exchange failed: ${response.code}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange error: ${e.message}")
                false
            } finally {
                _isLoading.value = false
            }
        }

    /**
     * Refreshes the access token using the stored refresh token.
     * Clears all tokens and returns `false` if the server rejects the request.
     */
    suspend fun refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        val currentRefreshToken = _refreshToken.value ?: return@withContext false

        try {
            _isLoading.value = true
            Log.i(TAG, "Refreshing Trakt access token")

            val body = JSONObject().run {
                put("refresh_token", currentRefreshToken)
                put("client_id",     TRAKT_CLIENT_ID)
                put("client_secret", TRAKT_CLIENT_SECRET)
                put("grant_type",    "refresh_token")
                toString()
            }.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(TRAKT_TOKEN_URL)
                .post(body)
                .build()

            val response     = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                saveTokens(
                    accessToken  = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn    = json.getInt("expires_in"),
                    userName     = json.optJSONObject("user")
                        ?.optString("username")
                        ?.takeIf { it.isNotBlank() },
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
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns a valid access token, refreshing it first if it is expired or
     * about to expire (within 5 minutes).
     *
     * Concurrent callers are serialised via [refreshMutex] so only a single
     * refresh request is ever in-flight.
     *
     * Returns `null` if no token is available and refresh fails.
     */
    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        if (_accessToken.value == null) {
            if (_refreshToken.value == null) return@withLock null
            return@withLock if (refreshAccessToken()) _accessToken.value else null
        }

        val expiresAt = prefs.getLong(PREF_EXPIRES_AT, 0L)
        val nearExpiry = System.currentTimeMillis() > expiresAt - 300_000L
        if (nearExpiry && !refreshAccessToken()) return@withLock null

        _accessToken.value
    }

    /**
     * Returns the cached access token without any expiry check or network call.
     *
     * **Warning:** the token may be expired. Prefer [getValidAccessToken] unless
     * you are in a context where suspension is not possible and you handle 401s
     * at the call site.
     */
    fun getValidAccessTokenSync(): String? = _accessToken.value

    /** Convenience accessor for the current username. */
    fun getUsername(): String? = _userName.value

    /** Returns the shared [OkHttpClient] for use in other components. */
    fun getHttpClient(): OkHttpClient = client

    // ── Sign-out ──────────────────────────────────────────────────────────────

    /** Clears all stored tokens and resets authentication state. */
    fun clearTokens() {
        prefs.edit().clear().apply()
        _accessToken.value          = null
        _refreshToken.value         = null
        _userName.value             = null
        _userAvatarUrl.value        = null
        _isTraktAuthenticated.value = false
        Log.i(TAG, "Trakt tokens cleared")
    }

    /**
     * Fetches user settings (including avatar) from Trakt API.
     */
    suspend fun fetchUserSettings() {
        val token = getValidAccessToken() ?: return
        try {
            val request = Request.Builder()
                .url("https://api.trakt.tv/users/settings")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", TRAKT_CLIENT_ID)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val userJson = json.optJSONObject("user")
                val username = userJson?.optString("username")
                val imagesJson = userJson?.optJSONObject("images")
                val avatar = imagesJson?.optJSONObject("avatar")?.optString("full")

                if (username != null || avatar != null) {
                    prefs.edit().apply {
                        if (username != null) {
                            putString(PREF_USER_NAME, username)
                            _userName.value = username
                        }
                        if (avatar != null) {
                            putString(PREF_USER_AVATAR, avatar)
                            _userAvatarUrl.value = avatar
                        }
                    }.apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user settings: ${e.message}")
        }
    }

    /** Alias for [clearTokens] kept for call-site compatibility. */
    fun signOut() = clearTokens()

    // ── URL helpers ───────────────────────────────────────────────────────────

    /**
     * Returns `true` only for URLs that genuinely look like Trakt OAuth
     * callbacks (OOB redirect URI, or the trakt.tv oauth/authorized path).
     *
     * Previously matched any URL containing `code=`, which caused false
     * positives for unrelated URLs with that query parameter.
     */
    fun isCallbackUrl(url: String): Boolean {
        if (url.startsWith(REDIRECT_URI)) return true
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.host == "trakt.tv" && uri.path?.contains("oauth") == true
    }

    /**
     * Extracts the `code` query parameter from a Trakt OAuth callback URL.
     * Returns `null` if the URL cannot be parsed or the parameter is absent.
     */
    fun extractCodeFromUrl(url: String): String? =
        runCatching { Uri.parse(url).getQueryParameter("code") }
            .onFailure { Log.e(TAG, "Error extracting code from URL: ${it.message}") }
            .getOrNull()
}