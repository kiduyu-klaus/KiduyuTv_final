package com.kiduyuk.klausk.kiduyutv.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val DialogBackground = Color(0xFF101010)
private val DialogSurface = Color(0xFF171717)
private val DialogBorder = Color(0xFF383838)
private val AccentPink = Color(0xFFE91E63)
private val MutedText = Color(0xFF9A9A9A)
private const val POLICY_AND_TERMS_URL = "https://sflatransport.com/api/"

@Composable
fun KiduyuTvTermsDialog(
    onAccepted: () -> Unit
) {
    val context = LocalContext.current
    var accepted by remember { mutableStateOf(false) }
    val policyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        policyFocusRequester.requestFocus()
    }

    fun openPolicyAndTerms() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(POLICY_AND_TERMS_URL)))
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 840.dp)
                .border(1.dp, DialogBorder, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = DialogBackground,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AccentPink.copy(alpha = 0.12f))
                            .border(1.dp, AccentPink.copy(alpha = 0.65f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = AccentPink,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Welcome to kiduyuTv",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Privacy-first media streaming",
                            color = MutedText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                HorizontalDividerLine()

                Text(
                    text = "Please review and accept our Terms of Service and Privacy Policy before continuing. kiduyuTv is designed with zero third-party tracking, ensuring your media library and streaming history remain strictly private to your server instance.",
                    color = Color(0xFFD0D0D0),
                    style = MaterialTheme.typography.bodyLarge
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DialogSurface)
                        .border(1.dp, Color(0xFF2D2D2D), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FeatureRow(
                        icon = { Icon(Icons.Outlined.Lock, null, tint = AccentPink) },
                        title = "Self-Hosted & Private",
                        description = "Streaming history and account data stay on your instance. No cloud telemetry or third-party ads."
                    )
                    FeatureRow(
                        icon = { Icon(Icons.Outlined.Description, null, tint = AccentPink) },
                        title = "Personal Media Archiving",
                        description = "Designed strictly for personal, household media collections."
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PolicyButton(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(policyFocusRequester),
                        icon = Icons.Outlined.Shield,
                        text = "Read Full Privacy Policy",
                        onClick = ::openPolicyAndTerms
                    )
                    PolicyButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Description,
                        text = "Read Full Terms of Service",
                        onClick = ::openPolicyAndTerms
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { accepted = !accepted }
                        .focusable(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { accepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentPink,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = buildAnnotatedString {
                            append("I agree to the ")
                            pushStyle(SpanStyle(color = AccentPink, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold))
                            append("Terms of Service")
                            pop()
                            append(" and acknowledge the ")
                            pushStyle(SpanStyle(color = AccentPink, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold))
                            append("Privacy Policy")
                            pop()
                            append(".")
                        },
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDividerLine()

                Button(
                    onClick = onAccepted,
                    enabled = accepted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusable(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPink,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6B1839),
                        disabledContentColor = Color(0xFF9F8B91)
                    )
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Accept & Continue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            text = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color(0xFFE6E6E6), fontWeight = FontWeight.Bold))
                append("$title: ")
                pop()
                append(description)
            },
            color = Color(0xFFD0D0D0),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PolicyButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .onFocusChanged { focused = it.isFocused },
        border = BorderStroke(1.dp, if (focused) AccentPink else DialogBorder),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DialogSurface,
            contentColor = Color.White
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HorizontalDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF2A2A2A))
    )
}
