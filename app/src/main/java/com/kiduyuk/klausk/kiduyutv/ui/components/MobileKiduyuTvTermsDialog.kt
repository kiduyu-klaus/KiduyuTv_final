package com.kiduyuk.klausk.kiduyutv.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private val MobileDialogBackground = Color(0xFF101010)
private val MobileDialogSurface = Color(0xFF171717)
private val MobileDialogBorder = Color(0xFF383838)
private val MobileAccentPink = Color(0xFFE91E63)
private val MobileMutedText = Color(0xFF9A9A9A)
private const val MOBILE_POLICY_AND_TERMS_URL = "https://sflatransport.com/api/"

@Composable
fun MobileKiduyuTvTermsDialog(
    onAccepted: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var accepted by remember { mutableStateOf(false) }

    fun openPolicyAndTerms() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(MOBILE_POLICY_AND_TERMS_URL))
        )
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = true
        ),
        containerColor = MobileDialogBackground,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD0D0D0),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MobileAccentPink.copy(alpha = 0.12f))
                        .border(1.dp, MobileAccentPink.copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MobileAccentPink,
                        modifier = Modifier.size(27.dp)
                    )
                }
                Column {
                    Text(
                        text = "Welcome to kiduyuTv",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Privacy-first media streaming",
                        color = MobileMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Please review and accept our Terms of Service and Privacy Policy before continuing. kiduyuTv is designed with zero third-party tracking, ensuring your media library and streaming history remain strictly private to your server instance.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MobileDialogSurface)
                        .border(1.dp, Color(0xFF2D2D2D), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MobileFeatureRow(
                        icon = Icons.Outlined.Lock,
                        title = "Self-Hosted & Private",
                        description = "Streaming history and account data stay on your instance. No cloud telemetry or third-party ads."
                    )
                    MobileFeatureRow(
                        icon = Icons.Outlined.Description,
                        title = "Personal Media Archiving",
                        description = "Designed strictly for personal, household media collections."
                    )
                }

                MobilePolicyButton(
                    icon = Icons.Outlined.Shield,
                    text = "Read Full Privacy Policy",
                    onClick = ::openPolicyAndTerms
                )
                MobilePolicyButton(
                    icon = Icons.Outlined.Description,
                    text = "Read Full Terms of Service",
                    onClick = ::openPolicyAndTerms
                )

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
                            checkedColor = MobileAccentPink,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = buildAnnotatedString {
                            append("I agree to the ")
                            pushStyle(SpanStyle(color = MobileAccentPink, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold))
                            append("Terms of Service")
                            pop()
                            append(" and acknowledge the ")
                            pushStyle(SpanStyle(color = MobileAccentPink, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold))
                            append("Privacy Policy")
                            pop()
                            append(".")
                        },
                        color = MobileMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = onAccepted,
                    enabled = accepted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MobileAccentPink,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF6B1839),
                        disabledContentColor = Color(0xFF9F8B91)
                    )
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept & Continue", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun MobileFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MobileAccentPink,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color(0xFFE6E6E6), fontWeight = FontWeight.Bold))
                append("$title: ")
                pop()
                append(description)
            },
            color = Color(0xFFD0D0D0),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MobilePolicyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MobileDialogSurface,
            contentColor = Color.White
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}
