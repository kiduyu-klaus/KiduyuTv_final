package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.launch

/**
 * Activity that handles Trakt.tv OAuth 2.0 authentication.
 * Uses a WebView to load the Trakt authorization page and captures the OAuth callback.
 */
class TraktAuthActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var tvLoadingMessage: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnBack: Button
    private lateinit var btnRetry: Button
    private lateinit var traktAuthManager: TraktAuthManager

    companion object {
        const val EXTRA_RESULT = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

        // OAuth callback URL - must match the one registered on Trakt.tv API
        private const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trakt_auth)

        // Initialize views manually
        webView = findViewById(R.id.webView)
        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnBack = findViewById(R.id.btnBack)
        btnRetry = findViewById(R.id.btnRetry)

        traktAuthManager = TraktAuthManager.getInstance(this)

        setupViews()
        startAuthentication()
    }

    private fun setupViews() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Retry button
        btnRetry.setOnClickListener {
            startAuthentication()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startAuthentication() {
        showLoading("Preparing authentication...")

        // Build the authorization URL
        val authUrl = traktAuthManager.getAuthorizationUrl(REDIRECT_URI)

        // Configure WebView
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString = "KiduyuTV Android"

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Check if this is our callback URL
                    if (url.startsWith(REDIRECT_URI) || url.contains("code=")) {
                        // Extract the authorization code
                        val code = extractCodeFromUrl(url)
                        if (code != null) {
                            handleAuthorizationCode(code)
                        } else {
                            // User might have denied access
                            handleCancellation()
                        }
                        return true
                    }

                    // Allow navigation to Trakt.tv
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    showWebView()
                }
            }
        }

        // Load the authorization URL
        webView.loadUrl(authUrl)
    }

    private fun extractCodeFromUrl(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("code")
        } catch (e: Exception) {
            null
        }
    }

    private fun handleAuthorizationCode(code: String) {
        showLoading("Exchanging code for tokens...")

        lifecycleScope.launch {
            try {
                val success = traktAuthManager.exchangeCodeForTokens(code)

                if (success) {
                    Toast.makeText(
                        this@TraktAuthActivity,
                        "Successfully connected to Trakt.tv!",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(RESULT_OK, Intent().apply {
                        putExtra(EXTRA_RESULT, RESULT_SUCCESS)
                    })
                } else {
                    showError("Failed to exchange authorization code for tokens.")
                }
            } catch (e: Exception) {
                showError("Authentication error: ${e.message}")
            } finally {
                finish()
            }
        }
    }

    private fun handleCancellation() {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_RESULT, RESULT_CANCELLED)
        })
        finish()
    }

    private fun showLoading(message: String) {
        loadingContainer.visibility = View.VISIBLE
        webView.visibility = View.GONE
        errorContainer.visibility = View.GONE
        tvLoadingMessage.text = message
    }

    private fun showWebView() {
        loadingContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        webView.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        tvErrorMessage.text = message

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If WebView can go back, go back; otherwise cancel the auth
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            handleCancellation()
        }
    }
}