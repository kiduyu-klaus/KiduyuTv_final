package com.kiduyuk.klausk.kiduyutv.ui.screens.trakt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.R

// ---------------------------------------------------------------------------
// Token definitions
// ---------------------------------------------------------------------------
private val AuthBackground    = Color(0xFF1A1A1A)
private val AuthCardBg       = Color(0xFF2A2A2A)
private val AuthTextPrimary  = Color(0xFFFFFFFF)
private val AuthTextSecondary = Color(0xFFB0B0B0)
private val AuthTextMuted    = Color(0xFF757575)
private val TraktOrange      = Color(0xFFFF6B00)  // Orange for activation code

// ---------------------------------------------------------------------------
// Sealed class for UI states
// ---------------------------------------------------------------------------
sealed interface TraktAuthTvUiState {
    data class Loading(val message: String = "Connecting to Trakt.tv…") : TraktAuthTvUiState

    data class Code(
        val deviceCode: String = "",
        val userCode: String = "",
        val verificationUrl: String = "https://trakt.tv/activate",
        val pollMessage: String = "Waiting for authorization…"
    ) : TraktAuthTvUiState

    data class Authorized(val message: String = "Authorized! Completing sign in…") : TraktAuthTvUiState

    data class Error(val message: String) : TraktAuthTvUiState
}

/**
 * Stateless Compose screen for Trakt OAuth on Android TV / Fire TV.
 *
 * Uses Device Code Flow - TV shows a code, user enters it at trakt.tv/activate
 * on another device. The app polls until authorization completes.
 *
 * @param uiState Current UI state (loading / code / authorized / error).
 * @param onBack Called when the user activates the back button.
 * @param onRetry Called when "Retry" is tapped in the error state.
 * @param qrCodeContent Optional composable slot for the QR code image.
 */
@Composable
fun TraktAuthTvScreen(
    uiState: TraktAuthTvUiState,
    onBack: () -> Unit,
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
                onBack = onBack,
                onRetry = onRetry,
            )

            is TraktAuthTvUiState.Authorized -> AuthorizedOverlay(uiState.message)

            is TraktAuthTvUiState.Code -> CodeLayout(
                state = uiState,
                onBack = onBack,
                qrCodeContent = qrCodeContent,
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
    onBack: () -> Unit,
    qrCodeContent: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side - Instructions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AuthTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                //Spacer(Modifier.width(12.dp))
                Text(
                    text = "Trakt",
                    color = AuthTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Sign in with Trakt",
                color = AuthTextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Use one of the options below to connect your Trakt account:",
                color = AuthTextSecondary,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(10.dp))

            // Options
            InstructionOption(
                title = "Option 1 - Activate on Your Phone",
                description = "Go to https://trakt.tv/activate and enter the code shown on this screen."
            )
            Spacer(Modifier.height(10.dp))
            InstructionOption(
                title = "Option 2 - Scan to Sign In",
                description = "Scan the QR code with your phone, sign in to Trakt, then scan again if prompted."
            )

            Spacer(Modifier.height(20.dp))

            // Activation Code Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Activation Code: ",
                    color = AuthTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = state.userCode.uppercase(),
                    color = TraktOrange,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "This screen will close automatically once your device is authorized.",
                color = AuthTextSecondary,
                fontSize = 10.sp
            )
        }

        // Right side - QR Code
        Column(
            modifier = Modifier.weight(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrCodeContent != null) {
                    qrCodeContent()
                } else {
                    // Placeholder - QR code will be provided via qrCodeContent
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .background(AuthCardBg, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "QR Code",
                            color = AuthTextMuted,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "https://trakt.tv/activate",
                color = AuthTextPrimary,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun InstructionOption(title: String, description: String) {
    Column {
        Text(
            text = title,
            color = AuthTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = description,
            color = AuthTextSecondary,
            fontSize = 15.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Loading overlay
// ---------------------------------------------------------------------------

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = TraktOrange,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = message,
                color = AuthTextSecondary,
                fontSize = 20.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Authorized overlay
// ---------------------------------------------------------------------------

@Composable
private fun AuthorizedOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = AuthTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(AuthCardBg, RoundedCornerShape(16.dp))
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = "Error",
                tint = AuthTextMuted,
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = message,
                color = AuthTextPrimary,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Back button
                androidx.compose.material3.OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .width(120.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Back",
                        color = AuthTextPrimary,
                        fontSize = 16.sp,
                    )
                }

                // Retry button
                androidx.compose.material3.Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = TraktOrange
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .width(120.dp),
                ) {
                    Text(
                        text = "Retry",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
