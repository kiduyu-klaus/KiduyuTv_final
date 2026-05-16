package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Activity that handles Trakt.tv OAuth 2.0 authentication using device code flow.
 * Displays a user code and polling interval, then waits for user to authorize at https://trakt.tv/activate
 */
class TraktAuthActivity : AppCompatActivity() {

    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var codeContainer: LinearLayout
    private lateinit var tvLoadingMessage: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var tvUserCode: TextView
    private lateinit var tvVerificationUrl: TextView
    private lateinit var tvPollingStatus: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnRetry: Button
    private lateinit var traktAuthManager: TraktAuthManager

    companion object {
        const val EXTRA_RESULT = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

        // Polling interval in milliseconds
        private const val POLL_INTERVAL_MS = 5000L
    }

    private var isPolling = false
    private val handler = Handler(Looper.getMainLooper())
    private var deviceCode: String? = null
    private var intervalMs: Int = POLL_INTERVAL_MS.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trakt_auth)

        // Initialize views
        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        codeContainer = findViewById(R.id.codeContainer)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        tvUserCode = findViewById(R.id.tvUserCode)
        tvVerificationUrl = findViewById(R.id.tvVerificationUrl)
        tvPollingStatus = findViewById(R.id.tvPollingStatus)
        btnBack = findViewById(R.id.btnBack)
        btnRetry = findViewById(R.id.btnRetry)

        traktAuthManager = TraktAuthManager.getInstance(this)

        setupViews()
        startDeviceCodeFlow()
    }

    private fun setupViews() {
        // Back button
        btnBack.setOnClickListener {
            stopPolling()
            finish()
        }

        // Retry button
        btnRetry.setOnClickListener {
            startDeviceCodeFlow()
        }
    }

    private fun startDeviceCodeFlow() {
        showLoading("Requesting device code...")

        lifecycleScope.launch {
            try {
                val result = requestDeviceCode()
                if (result) {
                    showCodeContainer()
                    startPollingForAuth()
                } else {
                    showError("Failed to get device code. Please try again.")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private suspend fun requestDeviceCode(): Boolean {
        return try {
            showLoading("Connecting to Trakt.tv...")

            val body = okhttp3.FormBody.Builder()
                .add("client_id", TraktAuthManager.TRAKT_CLIENT_ID)
                .build()

            val request = okhttp3.Request.Builder()
                .url(TraktAuthManager.TRAKT_DEVICE_CODE_URL)
                .post(body)
                .build()

            val response = TraktAuthManager.getHttpClient().newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                deviceCode = json.getString("device_code")
                val userCode = json.getString("user_code")
                val verificationUrl = json.getString("verification_url")
                intervalMs = json.optInt("interval", 5) * 1000

                // Update UI with codes
                runOnUiThread {
                    tvUserCode.text = formatUserCode(userCode)
                    tvVerificationUrl.text = "https://trakt.tv/activate"
                    tvPollingStatus.text = "Waiting for authorization..."
                }

                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("TraktAuthActivity", "Device code error: ${e.message}")
            false
        }
    }

    private fun formatUserCode(code: String): String {
        // Format as XXX-XXX for better readability
        return if (code.length == 6) {
            "${code.substring(0, 3)}-${code.substring(3)}"
        } else {
            code
        }
    }

    private fun startPollingForAuth() {
        if (isPolling) return
        isPolling = true

        runOnUiThread {
            tvPollingStatus.text = "Checking for authorization..."
        }

        // Start polling using a runnable
        val pollRunnable = object : Runnable {
            override fun run() {
                if (!isPolling) return

                lifecycleScope.launch {
                    val success = pollForToken()
                    if (success) {
                        onAuthSuccess()
                    } else {
                        // Schedule next poll
                        handler.postDelayed(this@Runnable, intervalMs.toLong())
                    }
                }
            }
        }

        // First poll happens immediately
        handler.post(pollRunnable)
    }

    private suspend fun pollForToken(): Boolean {
        val code = deviceCode ?: return false

        return try {
            val body = okhttp3.FormBody.Builder()
                .add("code", code)
                .add("client_id", TraktAuthManager.TRAKT_CLIENT_ID)
                .add("client_secret", TraktAuthManager.TRAKT_CLIENT_SECRET)
                .add("grant_type", "device_code")
                .build()

            val request = okhttp3.Request.Builder()
                .url(TraktAuthManager.TRAKT_TOKEN_URL)
                .post(body)
                .build()

            val response = TraktAuthManager.getHttpClient().newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                if (json.has("access_token")) {
                    // Authorization successful
                    return true
                }
            } else {
                // Check error code - 404 means still pending, other errors may be terminal
                val errorJson = responseBody?.let { JSONObject(it) }
                val error = errorJson?.optString("error", "")
                if (error == "authorization_pending") {
                    // User hasn't authorized yet, continue polling
                    return false
                } else if (error == "expired_token") {
                    // Device code expired, need to restart
                    runOnUiThread {
                        showError("Authorization expired. Please try again.")
                    }
                    return true // Stop polling with error
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("TraktAuthActivity", "Poll error: ${e.message}")
            false
        }
    }

    private fun onAuthSuccess() {
        stopPolling()
        isPolling = false

        Toast.makeText(
            this,
            "Successfully connected to Trakt.tv!",
            Toast.LENGTH_SHORT
        ).show()

        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_RESULT, RESULT_SUCCESS)
        })
        finish()
    }

    private fun stopPolling() {
        isPolling = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun showLoading(message: String) {
        loadingContainer.visibility = View.VISIBLE
        codeContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        tvLoadingMessage.text = message
    }

    private fun showCodeContainer() {
        loadingContainer.visibility = View.GONE
        codeContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        stopPolling()
        loadingContainer.visibility = View.GONE
        codeContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        tvErrorMessage.text = message

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        stopPolling()
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_RESULT, RESULT_CANCELLED)
        })
        super.onBackPressed()
    }
}