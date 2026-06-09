package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.R

// ---------------------------------------------------------------------------
// Token definitions — mirror the XML color resources used in the TV layout.
// ---------------------------------------------------------------------------
private val AuthBackground   = Color(0xFF1A1A1A)
private val AuthCardBg       = Color(0xFF2A2A2A)
private val AuthTextPrimary  = Color(0xFFFFFFFF)
private val AuthTextSecondary= Color(0xFFB0B0B0)
private val AuthTextMuted    = Color(0xFF757575)
private val AuthTextDim      = Color(0xFF9E9E9E)
private val TraktRed         = Color(0xFFED1C24)

/**
 * Sealed class representing the three UI states of the TV auth screen.
 */
sealed interface TraktAuthTvUiState {
    /** Verifying a pasted code with Trakt. [message] is shown under the spinner. */
    data class Loading(val message: String = "Connecting to Trakt.tv…") : TraktAuthTvUiState

    /** The main interaction state — user needs to paste a code and press Connect. */
    data class Code(
        val verificationUrl: String = "",
        val activationCode: String  = "•••",
        val authorizationCode: String = "",
        val pollingStatus: String   = "Open the link in your browser, sign in, then paste the code here.",
    ) : TraktAuthTvUiState

    /** Something went wrong. [message] describes what and how to recover. */
    data class Error(val message: String) : TraktAuthTvUiState
}

/**
 * Stateless Compose screen for Trakt OAuth on Android TV / Fire TV.
 *
 * The layout mirrors [activity_trakt_auth_tv.xml]:
 * - Left column (60 %): scrollable instructions + activation code
 * - Right column (40 %): QR placeholder + URL
 * - Bottom action row (in left column): code input + Open Browser + Connect
 *
 * All state changes are communicated back to the host [TraktAuthActivity]
 * through the lambda parameters — the composable itself holds no mutable state.
 *
 * @param uiState          Current UI state (loading / code / error).
 * @param authCode         Current value of the authorization-code text field.
 * @param onAuthCodeChange Called whenever the user edits the code field.
 * @param onBack           Called when the user activates the back button.
 * @param onOpenBrowser    Called when "Open Browser" is tapped.
 * @param onConnect        Called when "Connect" is tapped (or IME action fired).
 * @param onRetry          Called when "Retry" is tapped in the error state.
 * @param qrCodeContent    Optional composable slot for the QR code image.
 *                         Defaults to a placeholder icon when `null`.
 */
@Composable
fun TraktAuthTvScreen(
    uiState: TraktAuthTvUiState,
    authCode: String,
    onAuthCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    qrCodeContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackground)
            .padding(48.dp),
    ) {
        when (uiState) {
            is TraktAuthTvUiState.Loading -> LoadingOverlay(uiState.message)

            is TraktAuthTvUiState.Error -> ErrorOverlay(
                message = uiState.message,
                onRetry  = onRetry,
            )

            is TraktAuthTvUiState.Code -> CodeLayout(
                state            = uiState,
                authCode         = authCode,
                onAuthCodeChange = onAuthCodeChange,
                onBack           = onBack,
                onOpenBrowser    = onOpenBrowser,
                onConnect        = onConnect,
                qrCodeContent    = qrCodeContent,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Code layout — the main interaction state
// ---------------------------------------------------------------------------

@Composable
private fun CodeLayout(
    state: TraktAuthTvUiState.Code,
    authCode: String,
    onAuthCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onConnect: () -> Unit,
    qrCodeContent: (@Composable () -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ──────────────────────────────────────────────────────────
        TopBar(onBack = onBack)

        Spacer(Modifier.height(48.dp))

        // ── Two-column body ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Left column: scrollable instructions + action row at the bottom
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
            ) {
                // Scrollable instructions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    LeftColumnInstructions(
                        activationCode = state.activationCode,
                        pollingStatus  = state.pollingStatus,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Fixed action row
                ActionRow(
                    authCode         = authCode,
                    onAuthCodeChange = onAuthCodeChange,
                    onOpenBrowser    = onOpenBrowser,
                    onConnect        = onConnect,
                )
            }

            Spacer(Modifier.width(48.dp))

            // Right column: QR code + URL (not scrollable)
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement   = Arrangement.Center,
                horizontalAlignment   = Alignment.CenterHorizontally,
            ) {
                RightColumnQr(
                    verificationUrl = state.verificationUrl,
                    qrCodeContent   = qrCodeContent,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint              = AuthTextPrimary,
                modifier          = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(24.dp))
        Text(
            text       = "Trakt",
            color      = AuthTextPrimary,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LeftColumnInstructions(
    activationCode: String,
    pollingStatus: String,
) {
    Text(
        text       = "Sign in with Trakt",
        color      = AuthTextPrimary,
        fontSize   = 44.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.SansSerif,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text     = "Use one of the options below to connect your Trakt account:",
        color    = AuthTextSecondary,
        fontSize = 18.sp,
    )

    Spacer(Modifier.height(32.dp))

    Text(
        text       = "Option 1 – Open in Browser",
        color      = AuthTextPrimary,
        fontSize   = 24.sp,
        fontWeight = FontWeight.Medium,
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text     = "Press 'Open Browser 'below, sign in on Trakt.tv, then copy the authorization code and paste it into the field.",
        color    = AuthTextSecondary,
        fontSize = 16.sp,
    )

    Spacer(Modifier.height(24.dp))

    Text(
        text       = "Option 2 – Scan to Sign In",
        color      = AuthTextPrimary,
        fontSize   = 24.sp,
        fontWeight = FontWeight.Medium,
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text     = "Scan the QR code with your phone and sign in to Trakt.",
        color    = AuthTextSecondary,
        fontSize = 16.sp,
    )

    Spacer(Modifier.height(40.dp))

    // Activation code row
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text       = "Activation Code: ",
            color      = AuthTextPrimary,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text       = activationCode,
            color      = TraktRed,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Black,
        )
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text     = pollingStatus,
        color    = AuthTextSecondary,
        fontSize = 16.sp,
        fontStyle = FontStyle.Italic,
    )
}

@Composable
private fun ActionRow(
    authCode: String,
    onAuthCodeChange: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    onConnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value         = authCode,
            onValueChange = onAuthCodeChange,
            placeholder   = {
                Text(
                    "Paste authorization code here",
                    color    = AuthTextDim,
                    fontSize = 18.sp,
                )
            },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType   = KeyboardType.Text,
                autoCorrect    = false,
                imeAction      = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedTextColor    = AuthTextPrimary,
                unfocusedTextColor  = AuthTextPrimary,
                focusedBorderColor  = TraktRed,
                unfocusedBorderColor= AuthTextMuted,
                cursorColor         = TraktRed,
            ),
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
        )

        Spacer(Modifier.width(16.dp))

        TextButton(
            onClick  = onOpenBrowser,
            modifier = Modifier.height(64.dp),
        ) {
            Text(
                text     = "Open Browser",
                color    = AuthTextPrimary,
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.width(16.dp))

        Button(
            onClick  = onConnect,
            shape    = RoundedCornerShape(8.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TraktRed),
            modifier = Modifier
                .height(64.dp)
                .width(180.dp),
        ) {
            Text(
                text       = "Connect",
                color      = AuthTextPrimary,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RightColumnQr(
    verificationUrl: String,
    qrCodeContent: (@Composable () -> Unit)?,
) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (qrCodeContent != null) {
            qrCodeContent()
        } else {
            // Placeholder until a real QR bitmap is supplied
            Icon(
                painter           = painterResource(R.drawable.ic_trakt_qr_placeholder),
                contentDescription = "Trakt QR code",
                tint              = Color.Unspecified,
                modifier          = Modifier.fillMaxSize(),
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text           = verificationUrl.ifBlank { "https://trakt.tv/activate" },
        color          = AuthTextPrimary,
        fontSize       = 18.sp,
        fontWeight     = FontWeight.Medium,
        textDecoration = TextDecoration.Underline,
    )
}

// ---------------------------------------------------------------------------
// Loading overlay
// ---------------------------------------------------------------------------

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color    = TraktRed,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text     = message,
                color    = AuthTextSecondary,
                fontSize = 20.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Error overlay
// ---------------------------------------------------------------------------

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier              = Modifier
                .background(AuthCardBg, RoundedCornerShape(16.dp))
                .padding(48.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_error),
                contentDescription = "Error",
                tint              = AuthTextMuted,
                modifier          = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text     = message,
                color    = AuthTextPrimary,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick  = onRetry,
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TraktRed),
                modifier = Modifier
                    .height(64.dp)
                    .width(180.dp),
            ) {
                Text(
                    text     = "Retry",
                    color    = AuthTextPrimary,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
