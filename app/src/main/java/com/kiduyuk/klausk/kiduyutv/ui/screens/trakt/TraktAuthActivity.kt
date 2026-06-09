package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import kotlinx.coroutines.launch

/**
 * Activity that handles Trakt.tv OAuth 2.0 authentication using the authorization code flow.
 * Opens Trakt's authorization page and lets the user paste the resulting code back into the app.
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

    /** Drives the TV [TraktAuthTvScreen] composable. */
    private var tvUiState: TraktAuthTvUiState by mutableStateOf(
        TraktAuthTvUiState.Code()
    )

    /** Authorization code field value — owned here so Compose stays stateless. */
    private var tvAuthCode: String by mutableStateOf("")

    // ── Shared ───────────────────────────────────────────────────────────────

    private lateinit var traktAuthManager: TraktAuthManager
    private var isTvDevice: Boolean = false

    companion object {
        const val EXTRA_RESULT   = "result"
        const val RESULT_SUCCESS  = "success"
        const val RESULT_CANCELLED = "cancelled"
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

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

        startAuthorizationFlow()
    }

    // ── TV init ──────────────────────────────────────────────────────────────

    private fun initTv() {
        setContent {
            TraktAuthTvScreen(
                uiState          = tvUiState,
                authCode         = tvAuthCode,
                onAuthCodeChange = { tvAuthCode = it },
                onBack           = ::cancelAndFinish,
                onOpenBrowser    = ::openAuthorizationPage,
                onConnect        = ::submitAuthorizationCode,
                onRetry          = ::startAuthorizationFlow,
            )
        }
    }

    // ── Phone init ───────────────────────────────────────────────────────────

    private fun initPhone() {
        setContentView(R.layout.activity_trakt_auth_phone)

        loadingContainer   = findViewById(R.id.loadingContainer)
        errorContainer     = findViewById(R.id.errorContainer)
        codeContainer      = findViewById(R.id.codeContainer)
        tvLoadingMessage   = findViewById(R.id.tvLoadingMessage)
        tvErrorMessage     = findViewById(R.id.tvErrorMessage)
        tvVerificationUrl  = findViewById(R.id.tvVerificationUrl)
        tvPollingStatus    = findViewById(R.id.tvPollingStatus)
        etAuthorizationCode = findViewById(R.id.etAuthorizationCode)
        btnOpenBrowser     = findViewById(R.id.btnOpenBrowser)
        btnConnect         = findViewById(R.id.btnConnect)
        btnBack            = findViewById(R.id.btnBack)
        btnRetry           = findViewById(R.id.btnRetry)
        btnCopyCode        = findViewById(R.id.btnCopyCode)

        btnBack?.setOnClickListener { cancelAndFinish() }
        btnRetry?.setOnClickListener { startAuthorizationFlow() }
        btnOpenBrowser?.setOnClickListener { openAuthorizationPage() }
        btnConnect?.setOnClickListener { submitAuthorizationCode() }

        btnCopyCode?.setOnClickListener {
            val url = tvVerificationUrl?.text?.toString().orEmpty()
            if (url.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Trakt URL", url))
                Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Auth flow ────────────────────────────────────────────────────────────

    private fun startAuthorizationFlow() {
        val authUrl = TraktAuthManager.getAuthorizationUrl()

        if (isTvDevice) {
            tvUiState  = TraktAuthTvUiState.Code(
                verificationUrl = authUrl,
                activationCode  = "•••",  // Replace with real device code if you adopt the Device Code flow
                pollingStatus   = "Open the link in your browser, sign in, then paste the code here.",
            )
            tvAuthCode = ""
        } else {
            phoneShowCodeContainer()
            tvVerificationUrl?.text = authUrl
            tvPollingStatus?.text   = "Open the link in your browser, sign in, then paste the code here."
            etAuthorizationCode?.text?.clear()
        }

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
        val raw  = if (isTvDevice) tvAuthCode else etAuthorizationCode?.text?.toString().orEmpty()
        val code = normalizeAuthorizationCode(raw.trim())

        if (code.isNullOrBlank()) {
            showError("Paste the authorization code from Trakt.tv first.")
            return
        }

        showLoading("Verifying your Trakt.tv code…")

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
        val uri = runCatching { Uri.parse(input) }.getOrNull()
        return if (uri?.host != null) {
            TraktAuthManager.extractCodeFromUrl(input) ?: input
        } else {
            input
        }
    }

    private fun onAuthSuccess() {
        Toast.makeText(this, "Successfully connected to Trakt.tv!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, RESULT_SUCCESS))
        finish()
    }

    // ── UI state helpers ─────────────────────────────────────────────────────

    private fun showLoading(message: String) {
        if (isTvDevice) {
            tvUiState = TraktAuthTvUiState.Loading(message)
        } else {
            loadingContainer?.visibility = View.VISIBLE
            codeContainer?.visibility    = View.GONE
            errorContainer?.visibility   = View.GONE
            tvLoadingMessage?.text       = message
        }
    }

    private fun phoneShowCodeContainer() {
        loadingContainer?.visibility = View.GONE
        codeContainer?.visibility    = View.VISIBLE
        errorContainer?.visibility   = View.GONE
    }

    private fun showError(message: String) {
        if (isTvDevice) {
            tvUiState = TraktAuthTvUiState.Error(message)
        } else {
            loadingContainer?.visibility = View.GONE
            codeContainer?.visibility    = View.GONE
            errorContainer?.visibility   = View.VISIBLE
            tvErrorMessage?.text         = message
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_RESULT, RESULT_CANCELLED))
        finish()
    }
}