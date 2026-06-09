package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.launch

/**
 * Activity that handles Trakt.tv OAuth 2.0 authentication using the authorization code flow.
 * Opens Trakt's authorization page and lets the user paste the resulting code back into the app.
 */
class TraktAuthActivity : AppCompatActivity() {

    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var codeContainer: LinearLayout
    private lateinit var tvLoadingMessage: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var tvVerificationUrl: TextView
    private lateinit var tvPollingStatus: TextView
    private lateinit var etAuthorizationCode: EditText
    private lateinit var btnOpenBrowser: Button
    private lateinit var btnConnect: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnRetry: Button
    private lateinit var traktAuthManager: TraktAuthManager

    companion object {
        const val EXTRA_RESULT = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trakt_auth)

        // Initialize views
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

        traktAuthManager = TraktAuthManager.getInstance(this)

        setupViews()
        startAuthorizationFlow()
    }

    private fun setupViews() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Retry button
        btnRetry.setOnClickListener {
            startAuthorizationFlow()
        }

        btnOpenBrowser.setOnClickListener {
            openAuthorizationPage()
        }

        btnConnect.setOnClickListener {
            submitAuthorizationCode()
        }
    }

    private fun startAuthorizationFlow() {
        showCodeContainer()
        tvVerificationUrl.text = TraktAuthManager.getAuthorizationUrl()
        tvPollingStatus.text = "Open the link in your browser, sign in, then paste the code here."
        etAuthorizationCode.text?.clear()
        openAuthorizationPage()
    }

    private fun openAuthorizationPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TraktAuthManager.getAuthorizationUrl()))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showError("No browser app found to open Trakt.tv.")
        } catch (e: Exception) {
            showError("Could not open Trakt.tv login: ${e.message}")
        }
    }

    private fun submitAuthorizationCode() {
        val enteredCode = etAuthorizationCode.text?.toString()?.trim().orEmpty()
        val code = normalizeAuthorizationCode(enteredCode)

        if (code.isNullOrBlank()) {
            showError("Paste the authorization code from Trakt.tv first.")
            return
        }

        showLoading("Verifying your Trakt.tv code...")

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

    private fun normalizeAuthorizationCode(input: String): String? {
        if (input.isBlank()) return null
        if (input.contains("code=") || input.startsWith("http")) {
            return TraktAuthManager.extractCodeFromUrl(input) ?: input
        }
        return input
    }

    private fun onAuthSuccess() {
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
        loadingContainer.visibility = View.GONE
        codeContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        tvErrorMessage.text = message

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(RESULT_CANCELED, Intent().apply {
            putExtra(EXTRA_RESULT, RESULT_CANCELLED)
        })
        super.onBackPressed()
    }
}
