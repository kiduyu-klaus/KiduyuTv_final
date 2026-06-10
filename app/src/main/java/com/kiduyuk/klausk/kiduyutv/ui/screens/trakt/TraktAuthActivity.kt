package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Activity that handles Trakt.tv OAuth 2.0 authentication using the Device Code Flow.
 *
 * This flow is designed for devices like Smart TVs where opening a browser is not practical.
 * The user enters a code shown on the TV at trakt.tv/activate on another device.
 *
 * Layout selection:
 * - TV  ([Configuration.UI_MODE_TYPE_TELEVISION]) → Jetpack Compose via [TraktAuthTvScreen]
 * - Phone / tablet                                → `activity_trakt_auth_phone.xml`
 */
class TraktAuthActivity : AppCompatActivity() {

    // ── Phone-only XML view references ───────────────────────────────────────
    private var loadingContainer: LinearLayout? = null
    private var errorContainer: LinearLayout? = null
    private var codeContainer: LinearLayout? = null
    private var tvLoadingMessage: TextView? = null
    private var tvErrorMessage: TextView? = null
    private var tvVerificationUrl: TextView? = null
    private var tvPollingStatus: TextView? = null
    private var etAuthorizationCode: EditText? = null
    private var btnOpenBrowser: Button? = null
    private var btnConnect: Button? = null
    private var btnBack: ImageButton? = null
    private var btnRetry: Button? = null
    private var btnCopyCode: TextView? = null

    // ── TV Compose state ─────────────────────────────────────────────────────
    private var tvUiState: TraktAuthTvUiState by mutableStateOf(
        TraktAuthTvUiState.Loading("Initializing…")
    )

    // ── Shared ───────────────────────────────────────────────────────────────
    private lateinit var traktAuthManager: TraktAuthManager
    private var isTvDevice: Boolean = false

    // Device Code Flow state
    private var deviceCode: String = ""
    private var userCode: String = ""
    private var pollingJob: Job? = null
    private val pollingIntervalMs = 5000L // Poll every 5 seconds
    private val httpClient = OkHttpClient()

    companion object {
        const val EXTRA_RESULT = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        isTvDevice = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        traktAuthManager = TraktAuthManager.getInstance(this)

        if (isTvDevice) {
            initTv()
        } else {
            initPhone()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelAndFinish()
                }
            }
        )

        startDeviceCodeFlow()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }

    // ── TV init ──────────────────────────────────────────────────────────────

    private fun initTv() {
        setContent {
            TraktAuthTvScreen(
                uiState = tvUiState,
                onBack = ::cancelAndFinish,
                onRetry = ::startDeviceCodeFlow,
                qrCodeContent = {
                    if (tvUiState is TraktAuthTvUiState.Code) {
                        val url = (tvUiState as TraktAuthTvUiState.Code).verificationUrl
                        val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${Uri.encode(url)}"
                        AsyncImage(
                            model = qrUrl,
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            )
        }
    }

    // ── Phone init ───────────────────────────────────────────────────────────

    private fun initPhone() {
        setContentView(R.layout.activity_trakt_auth_phone)

        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        codeContainer = findViewById(R.id.codeContainer)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        tvVerificationUrl = findViewById(R.id.tvVerificationUrl)
        tvPollingStatus = findViewById(R.id.tvPollingStatus)
        etAuthorizationCode = findViewById(R.id.etAuthorizationCode)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
        btnConnect = findViewById(R.id.btnConnect)
        btnBack = findViewById(R.id.btnBack)
        btnRetry = findViewById(R.id.btnRetry)
        btnCopyCode = findViewById(R.id.btnCopyCode)

        btnBack?.setOnClickListener { cancelAndFinish() }
        btnRetry?.setOnClickListener { startDeviceCodeFlow() }
        btnOpenBrowser?.setOnClickListener { openAuthorizationPage() }
        btnConnect?.setOnClickListener { submitAuthorizationCode() }

        btnCopyCode?.setOnClickListener {
            val url = tvVerificationUrl?.text?.toString().orEmpty()
            if (url.isNotBlank()) {
                val service = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Trakt URL", url)
                service.setPrimaryClip(clip)
                Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Device Code Flow ─────────────────────────────────────────────────────

    /**
     * Start the OAuth 2.0 Device Authorization Grant flow.
     *
     * 1. Request device code from Trakt
     * 2. Display user code for user to enter at trakt.tv/activate
     * 3. Poll token endpoint until user authorizes
     */
    private fun startDeviceCodeFlow() {
        pollingJob?.cancel()

        if (isTvDevice) {
            tvUiState = TraktAuthTvUiState.Loading("Connecting to Trakt.tv…")
        } else {
            showPhoneLoading("Connecting to Trakt.tv…")
        }

        lifecycleScope.launch {
            try {
                val result = requestDeviceCode()
                if (result != null) {
                    deviceCode = result.deviceCode
                    userCode = result.userCode
                    val verificationUrl = result.verificationUrl
                    val interval = result.interval

                    if (isTvDevice) {
                        tvUiState = TraktAuthTvUiState.Code(
                            deviceCode = deviceCode,
                            userCode = userCode,
                            verificationUrl = verificationUrl,
                            pollMessage = "Waiting for authorization…"
                        )
                        startPolling(interval)
                    } else {
                        phoneShowCodeContainer()
                        tvVerificationUrl?.text = verificationUrl
                        tvPollingStatus?.text = "User Code: $userCode\nWaiting for authorization…"
                        startPolling(interval)
                    }
                } else {
                    showError("Failed to get device code from Trakt.tv")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    /**
     * Request device code from Trakt.tv
     */
    private suspend fun requestDeviceCode(): DeviceCodeResult? {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("client_id", TraktAuthManager.TRAKT_CLIENT_ID)
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://api.trakt.tv/oauth/device/code")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    DeviceCodeResult(
                        deviceCode = json.getString("device_code"),
                        userCode = json.getString("user_code"),
                        verificationUrl = json.getString("verification_url"),
                        interval = json.getInt("interval"),
                        expiresIn = json.getInt("expires_in")
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Poll the token endpoint to check if the user has authorized the device.
     */
    private fun startPolling(intervalSeconds: Int) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            val interval = (intervalSeconds * 1000L).coerceAtLeast(1000L)

            while (isActive) {
                delay(interval)

                val result = checkDeviceToken()
                when (result) {
                    is PollingResult.Authorized -> {
                        if (isTvDevice) {
                            tvUiState = TraktAuthTvUiState.Authorized("Authorized! Completing sign in…")
                        } else {
                            showPhoneLoading("Authorized! Completing sign in…")
                        }
                        // Exchange device code tokens for OAuth tokens
                        exchangeDeviceCodeForTokens(result.json)
                        return@launch
                    }
                    is PollingResult.Pending -> {
                        // Still waiting, update message
                        if (isTvDevice) {
                            tvUiState = TraktAuthTvUiState.Code(
                                deviceCode = deviceCode,
                                userCode = userCode,
                                verificationUrl = "https://trakt.tv/activate",
                                pollMessage = result.message
                            )
                        } else {
                            tvPollingStatus?.text = "User Code: $userCode\n${result.message}"
                        }
                    }
                    is PollingResult.Error -> {
                        showError(result.message)
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * Check if the device has been authorized.
     */
    private suspend fun checkDeviceToken(): PollingResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("code", deviceCode)
                    put("client_id", TraktAuthManager.TRAKT_CLIENT_ID)
                    put("client_secret", TraktAuthManager.TRAKT_CLIENT_SECRET)
                    put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://api.trakt.tv/oauth/device/token")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    // Authorization successful - tokens returned
                    PollingResult.Authorized(json)
                } else {
                    val errorJson = responseBody?.let { runCatching { JSONObject(it) }.getOrNull() }
                    val error = errorJson?.optString("error") ?: "authorization_pending"

                    when (error) {
                        "authorization_pending" -> {
                            PollingResult.Pending("Waiting for authorization…")
                        }
                        "expired_token" -> {
                            PollingResult.Error("Code expired. Please try again.")
                        }
                        else -> {
                            PollingResult.Error("Authorization failed: $error")
                        }
                    }
                }
            } catch (e: Exception) {
                PollingResult.Error("Error checking authorization: ${e.message}")
            }
        }
    }

    /**
     * Exchange device code tokens for OAuth tokens and save them.
     */
    private suspend fun exchangeDeviceCodeForTokens(json: JSONObject) {
        // The tokens were already returned in the polling response
        withContext(Dispatchers.Main) {
            try {
                traktAuthManager.saveTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn = json.getInt("expires_in"),
                    userName = json.optJSONObject("user")?.optString("username")
                )
                
                delay(1500) // Brief delay to show success state
                onAuthSuccess()
            } catch (e: Exception) {
                showError("Failed to save tokens: ${e.message}")
            }
        }
    }

    // ── Legacy phone flow (not used for TV) ─────────────────────────────────

    private fun openAuthorizationPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TraktAuthManager.getAuthorizationUrl()))
            startActivity(intent)
        } catch (e: Exception) {
            showError("No browser app found to open Trakt.tv.")
        }
    }

    private fun submitAuthorizationCode() {
        val code = etAuthorizationCode?.text?.toString().orEmpty().trim()

        if (code.isBlank()) {
            showError("Enter the authorization code from Trakt.tv first.")
            return
        }

        showPhoneLoading("Verifying your Trakt.tv code…")

        lifecycleScope.launch {
            try {
                val success = traktAuthManager.exchangeCodeForTokens(code)
                if (success) {
                    onAuthSuccess()
                } else {
                    showError("Trakt.tv rejected the code. Please open Trakt again and try once more.")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    // ── Auth success ─────────────────────────────────────────────────────────

    private fun onAuthSuccess() {
        pollingJob?.cancel()
        Toast.makeText(this, "Successfully connected to Trakt.tv!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, RESULT_SUCCESS))
        finish()
    }

    // ── UI state helpers ─────────────────────────────────────────────────────

    private fun showPhoneLoading(message: String) {
        loadingContainer?.visibility = View.VISIBLE
        codeContainer?.visibility = View.GONE
        errorContainer?.visibility = View.GONE
        tvLoadingMessage?.text = message
    }

    private fun phoneShowCodeContainer() {
        loadingContainer?.visibility = View.GONE
        codeContainer?.visibility = View.VISIBLE
        errorContainer?.visibility = View.GONE
    }

    private fun showError(message: String) {
        pollingJob?.cancel()
        if (isTvDevice) {
            tvUiState = TraktAuthTvUiState.Error(message)
        } else {
            loadingContainer?.visibility = View.GONE
            codeContainer?.visibility = View.GONE
            errorContainer?.visibility = View.VISIBLE
            tvErrorMessage?.text = message
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun cancelAndFinish() {
        pollingJob?.cancel()
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_RESULT, RESULT_CANCELLED))
        finish()
    }
}

// ── Data classes for Device Code Flow ───────────────────────────────────────

private data class DeviceCodeResult(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val interval: Int,
    val expiresIn: Int
)

private sealed class PollingResult {
    data class Authorized(val json: JSONObject) : PollingResult()
    data class Pending(val message: String) : PollingResult()
    data class Error(val message: String) : PollingResult()
}
