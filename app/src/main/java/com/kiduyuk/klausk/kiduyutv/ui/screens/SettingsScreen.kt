package com.kiduyuk.klausk.kiduyutv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.SettingsViewModel

/**
 * Settings screen composable that displays app settings, information, and version details.
 * Features a left sidebar navigation with three sections and a main content area.
 *
 * @param onBackClick Callback when the back button is clicked.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.APP_SETTINGS) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Load cache size when the screen is first shown
    LaunchedEffect(Unit) {
        viewModel.loadCacheSize(context)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left sidebar with navigation options
        SettingsSidebar(
            selectedSection = selectedSection,
            onSectionSelect = { selectedSection = it },
            onBackClick = onBackClick,
            modifier = Modifier.width(280.dp)
        )

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            when (selectedSection) {
                SettingsSection.APP_SETTINGS -> {
                    AppSettingsContent(
                        isClearingCache = uiState.isClearingCache,
                        cacheClearSuccess = uiState.cacheClearSuccess,
                        cacheSize = uiState.cacheSize,
                        onClearCacheClick = { viewModel.clearCache(context) }
                    )
                }
                SettingsSection.APP_INFORMATION -> {
                    AppInformationContent(
                        appName = "KiduyuTV",
                        appDescription = "KiduyuTV is a streaming application that allows you to watch movies and TV shows. " +
                                "The app features a modern, user-friendly interface with support for a wide range of content. " +
                                "Enjoy seamless navigation, high-quality streaming, and a vast library of entertainment.",
                        websiteUrl = "https://kiduyutv.app",
                        onWebsiteClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kiduyutv.app"))
                            context.startActivity(intent)
                        }
                    )
                }
                SettingsSection.APP_VERSION -> {
                    AppVersionContent(
                        currentVersion = BuildConfig.VERSION_NAME,
                        whatsNew = "Fixed some minor bugs and improved performance."
                    )
                }
            }
        }
    }
}

/**
 * Sidebar component with navigation options for settings sections.
 */
@Composable
private fun SettingsSidebar(
    selectedSection: SettingsSection,
    onSectionSelect: (SettingsSection) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with back button and settings label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = CardDark,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Settings label - pill-shaped
            Box(
                modifier = Modifier
                    .background(
                        color = TextTertiary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section title
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Navigation items
        SettingsSection.entries.forEach { section ->
            SettingsNavItem(
                title = section.title,
                isSelected = selectedSection == section,
                onClick = { onSectionSelect(section) }
            )
        }
    }
}

/**
 * Navigation item for settings sections.
 */
@Composable
private fun SettingsNavItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> CardDark
                    isFocused -> CardDark.copy(alpha = 0.6f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isFocused && !isSelected) {
                    Modifier.border(2.dp, DarkRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected || isFocused) TextPrimary else TextSecondary,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Content for App Settings section.
 */
@Composable
private fun AppSettingsContent(
    isClearingCache: Boolean,
    cacheClearSuccess: Boolean,
    cacheSize: String,
    onClearCacheClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "App Settings",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Cache Management Section
        Text(
            text = "Storage & Cache",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Clear temporary files and database cache to free up space. Your My List and Watch History will not be affected.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Clear Cache Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isClearingCache -> PrimaryRed.copy(alpha = 0.5f)
                                    isFocused -> PrimaryRed.copy(alpha = 0.8f)
                                    else -> PrimaryRed
                                }
                            )
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = !isClearingCache,
                                onClick = onClearCacheClick
                            )
                            .focusable(interactionSource = interactionSource)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isClearingCache) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = if (isClearingCache) "Clearing..." else "Clear Cache ($cacheSize)",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Success Feedback
                    if (cacheClearSuccess) {
                        Text(
                            text = "Cache cleared successfully!",
                            color = Color.Green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content for App Information section.
 */
@Composable
private fun AppInformationContent(
    appName: String,
    appDescription: String,
    websiteUrl: String,
    onWebsiteClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "App Information",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )

        // App icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkRed),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.first().toString(),
                color = TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App name
        Text(
            text = appName,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // App description
        Text(
            text = appDescription,
            color = TextSecondary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Website link
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isFocused) DarkRed.copy(alpha = 0.3f) else CardDark
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) DarkRed else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onWebsiteClick
                )
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = websiteUrl,
                color = if (isFocused) TextPrimary else DarkRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Content for App Version section.
 */
@Composable
private fun AppVersionContent(
    currentVersion: String,
    whatsNew: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "App Version",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Version info card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Current version: $currentVersion",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )

                HorizontalDivider(
                    color = TextTertiary.copy(alpha = 0.2f),
                    thickness = 1.dp
                )

                // What's new section
                Text(
                    text = "What's new?",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "• $whatsNew",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * Enum representing different settings sections.
 */
private enum class SettingsSection(val title: String) {
    APP_SETTINGS("App Settings"),
    APP_INFORMATION("App Information"),
    APP_VERSION("App Version")
}
