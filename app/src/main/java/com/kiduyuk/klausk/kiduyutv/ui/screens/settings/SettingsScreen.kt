package com.kiduyuk.klausk.kiduyutv.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.SettingsViewModel
import androidx.compose.foundation.Image
import com.kiduyuk.klausk.kiduyutv.R
import androidx.core.net.toUri

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

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

    // Load settings data when the screen is first shown
    LaunchedEffect(Unit) {
        viewModel.loadSettingsData(context)
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
                        // Cache
                        isClearingCache = uiState.isClearingCache,
                        cacheClearSuccess = uiState.cacheClearSuccess,
                        cacheSize = uiState.cacheSize,
                        onClearCacheClick = { viewModel.clearCache(context) },
                        // My List
                        isClearingMyList = uiState.isClearingMyList,
                        myListClearSuccess = uiState.myListClearSuccess,
                        onClearMyListClick = { viewModel.clearMyList() },
                        // Companies
                        isClearingCompanies = uiState.isClearingCompanies,
                        companiesClearSuccess = uiState.companiesClearSuccess,
                        onClearCompaniesClick = { viewModel.clearCompanies() },
                        // Networks
                        isClearingNetworks = uiState.isClearingNetworks,
                        networksClearSuccess = uiState.networksClearSuccess,
                        onClearNetworksClick = { viewModel.clearNetworks() },
                        // Casts
                        isClearingCasts = uiState.isClearingCasts,
                        castsClearSuccess = uiState.castsClearSuccess,
                        onClearCastsClick = { viewModel.clearCasts() },
                        // Watch History
                        isClearingWatchHistory = uiState.isClearingWatchHistory,
                        watchHistoryClearSuccess = uiState.watchHistoryClearSuccess,
                        onClearWatchHistoryClick = { viewModel.clearWatchHistory() }
                    )
                }

                SettingsSection.PLAYBACK -> {
                    PlaybackContent(
                        defaultProvider = uiState.defaultProvider,
                        onProviderSelect = { viewModel.setDefaultProvider(context, it) }
                    )
                }

                SettingsSection.APP_INFORMATION -> {
                    AppInformationContent(
                        appName = "KiduyuTV",
                        appDescription = "KiduyuTV is a streaming application that allows you to watch movies and TV shows. " +
                                "The app features a modern, user-friendly interface with support for a wide range of content. " +
                                "Enjoy seamless navigation, high-quality streaming, and a vast library of entertainment.",
                        websiteUrl = "https://kiduyu-klaus.github.io/KiduyuTv_final/",
                        onWebsiteClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://kiduyu-klaus.github.io/KiduyuTv_final/".toUri())
                            context.startActivity(intent)
                        }
                    )
                }

                SettingsSection.APP_VERSION -> {
                    AppVersionContent(
                        currentVersion = BuildConfig.VERSION_NAME,
                        whatsNew = uiState.releaseTitle ?: "SplashActivity: Check for existing APK before downloading to avoid redundancy — v1.1.81",
                        // Update check states
                        isCheckingForUpdates = uiState.isCheckingForUpdates,
                        updateCheckResult = uiState.updateCheckResult,
                        updateAvailable = uiState.updateAvailable,
                        isDownloadingUpdate = uiState.isDownloadingUpdate,
                        downloadProgress = uiState.downloadProgress,
                        onCheckForUpdatesClick = { viewModel.checkForUpdates(context) },
                        onDownloadUpdateClick = { viewModel.downloadAndInstallUpdate(context) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sidebar
// ─────────────────────────────────────────────────────────────────────────────

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
        // Header — back button + "Settings" pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(color = CardDark, shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

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

        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Nav items
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
 * Individual navigation item in the sidebar.
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
                if (isFocused && !isSelected)
                    Modifier.border(2.dp, DarkRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else
                    Modifier
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

// ─────────────────────────────────────────────────────────────────────────────
// App Settings — scrollable with three action cards
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Scrollable content for the App Settings section.
 * Contains three independently managed action cards:
 *  1. Storage & Cache — clear Coil/OkHttp/Room cache
 *  2. My List         — wipe saved media
 *  3. Watch History   — wipe watch history
 */
@Composable
private fun AppSettingsContent(
    // Cache
    isClearingCache: Boolean,
    cacheClearSuccess: Boolean,
    cacheSize: String,
    onClearCacheClick: () -> Unit,
    // My List
    isClearingMyList: Boolean,
    myListClearSuccess: Boolean,
    onClearMyListClick: () -> Unit,
    // Companies
    isClearingCompanies: Boolean,
    companiesClearSuccess: Boolean,
    onClearCompaniesClick: () -> Unit,
    // Networks
    isClearingNetworks: Boolean,
    networksClearSuccess: Boolean,
    onClearNetworksClick: () -> Unit,
    // Casts
    isClearingCasts: Boolean,
    castsClearSuccess: Boolean,
    onClearCastsClick: () -> Unit,
    // Watch History
    isClearingWatchHistory: Boolean,
    watchHistoryClearSuccess: Boolean,
    onClearWatchHistoryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "App Settings",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ── 1. Storage & Cache ────────────────────────────────────────
        SettingsSectionLabel(text = "Storage & Cache")

        SettingsActionCard(
            description = "Clear temporary files and database cache to free up space. " +
                    "Your My List and Watch History will not be affected.",
            buttonLabel = "Clear Cache ($cacheSize)",
            isLoading = isClearingCache,
            loadingLabel = "Clearing...",
            successMessage = "Cache cleared successfully!",
            showSuccess = cacheClearSuccess,
            icon = Icons.Default.Delete,
            onClick = onClearCacheClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── 2. My List ────────────────────────────────────────────────
        SettingsSectionLabel(text = "My List")

        SettingsActionCard(
            description = "Remove all titles you have saved to My List. " +
                    "This action cannot be undone.",
            buttonLabel = "Clear My List",
            isLoading = isClearingMyList,
            loadingLabel = "Clearing...",
            successMessage = "My List cleared!",
            showSuccess = myListClearSuccess,
            icon = Icons.Default.PlaylistRemove,
            onClick = onClearMyListClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── 2a. Companies ─────────────────────────────────────────────
        SettingsSectionLabel(text = "Companies")

        SettingsActionCard(
            description = "Remove all production companies you have saved to My List.",
            buttonLabel = "Clear Saved Companies",
            isLoading = isClearingCompanies,
            loadingLabel = "Clearing...",
            successMessage = "Companies cleared!",
            showSuccess = companiesClearSuccess,
            icon = Icons.Default.PlaylistRemove,
            onClick = onClearCompaniesClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── 2b. Networks ──────────────────────────────────────────────
        SettingsSectionLabel(text = "Networks")

        SettingsActionCard(
            description = "Remove all TV networks you have saved to My List.",
            buttonLabel = "Clear Saved Networks",
            isLoading = isClearingNetworks,
            loadingLabel = "Clearing...",
            successMessage = "Networks cleared!",
            showSuccess = networksClearSuccess,
            icon = Icons.Default.PlaylistRemove,
            onClick = onClearNetworksClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── 2c. Casts ─────────────────────────────────────────────────
        SettingsSectionLabel(text = "Casts")

        SettingsActionCard(
            description = "Remove all cast members you have saved to My List.",
            buttonLabel = "Clear Saved Casts",
            isLoading = isClearingCasts,
            loadingLabel = "Clearing...",
            successMessage = "Casts cleared!",
            showSuccess = castsClearSuccess,
            icon = Icons.Default.PlaylistRemove,
            onClick = onClearCastsClick
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── 3. Watch History ──────────────────────────────────────────
        SettingsSectionLabel(text = "Watch History")

        SettingsActionCard(
            description = "Delete your entire watch history. " +
                    "Your My List will not be affected.",
            buttonLabel = "Delete Watch History",
            isLoading = isClearingWatchHistory,
            loadingLabel = "Deleting...",
            successMessage = "Watch History deleted!",
            showSuccess = watchHistoryClearSuccess,
            icon = Icons.Default.History,
            onClick = onClearWatchHistoryClick
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App Information
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Content for the App Information section.
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

        // App icon placeholder
        // After — replace with this
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher11),
            contentDescription = "$appName icon",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = appName,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appDescription,
            color = TextSecondary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Website link button
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isFocused) DarkRed.copy(alpha = 0.3f) else CardDark)
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

// ─────────────────────────────────────────────────────────────────────────────
// App Version
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Content for the App Version section.
 */
@Composable
private fun AppVersionContent(
    currentVersion: String,
    whatsNew: String,
    isCheckingForUpdates: Boolean,
    updateCheckResult: String?,
    updateAvailable: Boolean,
    isDownloadingUpdate: Boolean,
    downloadProgress: Int,
    onCheckForUpdatesClick: () -> Unit,
    onDownloadUpdateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "App Version",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
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
                    text = "Current version: $currentVersion",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )

                HorizontalDivider(
                    color = TextTertiary.copy(alpha = 0.2f),
                    thickness = 1.dp
                )

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

        Spacer(modifier = Modifier.height(24.dp))

        // ── Check for Updates Section ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Updates",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Check for the latest updates to ensure you have the best experience with KiduyuTV.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // Check for Updates Button
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isCheckingForUpdates || isDownloadingUpdate -> PrimaryRed.copy(alpha = 0.5f)
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
                            enabled = !isCheckingForUpdates && !isDownloadingUpdate,
                            onClick = onCheckForUpdatesClick
                        )
                        .focusable(interactionSource = interactionSource)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCheckingForUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Checking...",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Check for Updates",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Update Result Message
                if (updateCheckResult != null) {
                    val resultColor = if (updateAvailable) Color(0xFF4CAF50) else TextSecondary
                    Text(
                        text = updateCheckResult,
                        color = resultColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Download Progress
                if (isDownloadingUpdate) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Downloading update... $downloadProgress%",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PrimaryRed,
                            trackColor = TextTertiary.copy(alpha = 0.3f),
                        )
                    }
                }

                // Download Update Button (shown when update is available)
                if (updateAvailable && !isDownloadingUpdate) {
                    val downloadInteractionSource = remember { MutableInteractionSource() }
                    val downloadFocused by downloadInteractionSource.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    downloadFocused -> Color(0xFF4CAF50).copy(alpha = 0.8f)
                                    else -> Color(0xFF4CAF50)
                                }
                            )
                            .border(
                                width = if (downloadFocused) 2.dp else 0.dp,
                                color = if (downloadFocused) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = downloadInteractionSource,
                                indication = null,
                                onClick = onDownloadUpdateClick
                            )
                            .focusable(interactionSource = downloadInteractionSource)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Download Update",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playback — default provider selector
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Content for the Playback section.
 * Shows a D-pad-navigable list of provider options; the active selection is
 * highlighted with a red border and a checkmark icon.
 */
@Composable
private fun PlaybackContent(
    defaultProvider: String,
    onProviderSelect: (String) -> Unit
) {
    val options = listOf(SettingsManager.AUTO) + SettingsManager.PROVIDERS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Playback",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionLabel(text = "Default Provider")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose which provider opens automatically when you press Play. " +
                            "Set to \"Auto\" to always see the full provider list.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                options.forEach { option ->
                    val isSelected = option == defaultProvider
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> PrimaryRed.copy(alpha = 0.12f)
                                    isFocused -> SurfaceDark
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                width = if (isSelected || isFocused) 2.dp else 1.dp,
                                color = when {
                                    isSelected -> PrimaryRed
                                    isFocused -> DarkRed.copy(alpha = 0.6f)
                                    else -> TextTertiary.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onProviderSelect(option) }
                            )
                            .focusable(interactionSource = interactionSource)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = if (isSelected) PrimaryRed else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = option,
                                    color = if (isSelected || isFocused) TextPrimary else TextSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (option == SettingsManager.AUTO) {
                                    Text(
                                        text = "Show provider list each time",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Section heading label used inside AppSettingsContent.
 */
@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/**
 * Reusable card that shows a description, an action button with loading/success states,
 * and an optional success confirmation message.
 *
 * @param description   Helper text shown above the button.
 * @param buttonLabel   Label shown on the button when idle.
 * @param isLoading     When true, shows a spinner and disables the button.
 * @param loadingLabel  Label shown on the button while loading.
 * @param successMessage Text shown after a successful action.
 * @param showSuccess   Whether to display the success message.
 * @param icon          Icon shown to the left of the button label when idle.
 * @param onClick       Triggered when the button is pressed.
 */
@Composable
private fun SettingsActionCard(
    description: String,
    buttonLabel: String,
    isLoading: Boolean,
    loadingLabel: String,
    successMessage: String,
    showSuccess: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Action button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isLoading -> PrimaryRed.copy(alpha = 0.5f)
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
                            enabled = !isLoading,
                            onClick = onClick
                        )
                        .focusable(interactionSource = interactionSource)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isLoading) loadingLabel else buttonLabel,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Success feedback
                if (showSuccess) {
                    Text(
                        text = successMessage,
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Enum
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Enum representing the three top-level settings sections.
 */
private enum class SettingsSection(val title: String) {
    APP_SETTINGS("App Settings"),
    PLAYBACK("Playback"),
    APP_INFORMATION("App Information"),
    APP_VERSION("App Version")
}

// ─────────────────────────────────────────────────────────────────────────────
// Compose Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "App Settings — idle",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 900,
    heightDp = 600
)
@Composable
private fun PreviewAppSettingsIdle() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(32.dp)
        ) {
            AppSettingsContent(
                isClearingCache = false,
                cacheClearSuccess = false,
                cacheSize = "24.6 MB",
                onClearCacheClick = {},
                isClearingMyList = false,
                myListClearSuccess = false,
                onClearMyListClick = {},
                isClearingCompanies = false,
                companiesClearSuccess = false,
                onClearCompaniesClick = {},
                isClearingNetworks = false,
                networksClearSuccess = false,
                onClearNetworksClick = {},
                isClearingCasts = false,
                castsClearSuccess = false,
                onClearCastsClick = {},
                isClearingWatchHistory = false,
                watchHistoryClearSuccess = false,
                onClearWatchHistoryClick = {}
            )
        }
    }
}

@Preview(
    name = "App Settings — cache clearing",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 900,
    heightDp = 600
)
@Composable
private fun PreviewAppSettingsCacheClearing() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(32.dp)
        ) {
            AppSettingsContent(
                isClearingCache = true,
                cacheClearSuccess = false,
                cacheSize = "24.6 MB",
                onClearCacheClick = {},
                isClearingMyList = false,
                myListClearSuccess = false,
                onClearMyListClick = {},
                isClearingCompanies = false,
                companiesClearSuccess = false,
                onClearCompaniesClick = {},
                isClearingNetworks = false,
                networksClearSuccess = false,
                onClearNetworksClick = {},
                isClearingCasts = false,
                castsClearSuccess = false,
                onClearCastsClick = {},
                isClearingWatchHistory = false,
                watchHistoryClearSuccess = false,
                onClearWatchHistoryClick = {}
            )
        }
    }
}

@Preview(
    name = "App Settings — all success states",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 900,
    heightDp = 600
)
@Composable
private fun PreviewAppSettingsAllSuccess() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(32.dp)
        ) {
            AppSettingsContent(
                isClearingCache = false,
                cacheClearSuccess = true,
                cacheSize = "0 B",
                onClearCacheClick = {},
                isClearingMyList = false,
                myListClearSuccess = true,
                onClearMyListClick = {},
                isClearingCompanies = false,
                companiesClearSuccess = true,
                onClearCompaniesClick = {},
                isClearingNetworks = false,
                networksClearSuccess = true,
                onClearNetworksClick = {},
                isClearingCasts = false,
                castsClearSuccess = true,
                onClearCastsClick = {},
                isClearingWatchHistory = false,
                watchHistoryClearSuccess = true,
                onClearWatchHistoryClick = {}
            )
        }
    }
}

@Preview(
    name = "App Information",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 600,
    heightDp = 600
)
@Composable
private fun PreviewAppInformation() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            AppInformationContent(
                appName = "KiduyuTV",
                appDescription = "KiduyuTV is a streaming application that allows you to watch movies and TV shows. " +
                        "Enjoy seamless navigation, high-quality streaming, and a vast library of entertainment.",
                websiteUrl = "https://kiduyutv.app",
                onWebsiteClick = {}
            )
        }
    }
}

@Preview(
    name = "App Version",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 600,
    heightDp = 600
)
@Composable
private fun PreviewAppVersion() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            AppVersionContent(
                currentVersion = "1.4.2",
                whatsNew = "Fixed some minor bugs and improved performance.",
                isCheckingForUpdates = false,
                updateCheckResult = null,
                updateAvailable = false,
                isDownloadingUpdate = false,
                downloadProgress = 0,
                onCheckForUpdatesClick = {},
                onDownloadUpdateClick = {}
            )
        }
    }
}

@Preview(
    name = "App Version — Update Available",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 600,
    heightDp = 600
)
@Composable
private fun PreviewAppVersionUpdateAvailable() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            AppVersionContent(
                currentVersion = "1.4.2",
                whatsNew = "Fixed some minor bugs and improved performance.",
                isCheckingForUpdates = false,
                updateCheckResult = "Update available: v1.5.0 (current: v1.4.2)",
                updateAvailable = true,
                isDownloadingUpdate = false,
                downloadProgress = 0,
                onCheckForUpdatesClick = {},
                onDownloadUpdateClick = {}
            )
        }
    }
}

@Preview(
    name = "App Version — Checking",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 600,
    heightDp = 600
)
@Composable
private fun PreviewAppVersionChecking() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            AppVersionContent(
                currentVersion = "1.4.2",
                whatsNew = "Fixed some minor bugs and improved performance.",
                isCheckingForUpdates = true,
                updateCheckResult = null,
                updateAvailable = false,
                isDownloadingUpdate = false,
                downloadProgress = 0,
                onCheckForUpdatesClick = {},
                onDownloadUpdateClick = {}
            )
        }
    }
}

@Preview(
    name = "App Version — Downloading",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 600,
    heightDp = 600
)
@Composable
private fun PreviewAppVersionDownloading() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            AppVersionContent(
                currentVersion = "1.4.2",
                whatsNew = "Fixed some minor bugs and improved performance.",
                isCheckingForUpdates = false,
                updateCheckResult = "Update available: v1.5.0 (current: v1.4.2)",
                updateAvailable = true,
                isDownloadingUpdate = true,
                downloadProgress = 45,
                onCheckForUpdatesClick = {},
                onDownloadUpdateClick = {}
            )
        }
    }
}

@Preview(
    name = "Settings Sidebar",
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 320,
    heightDp = 500
)
@Composable
private fun PreviewSettingsSidebar() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(24.dp)
        ) {
            SettingsSidebar(
                selectedSection = SettingsSection.APP_SETTINGS,
                onSectionSelect = {},
                onBackClick = {},
                modifier = Modifier.width(280.dp)
            )
        }
    }
}

@Preview(
    name = "Single Action Card — idle",
    showBackground = true,
    backgroundColor = 0xFF1A1A1A,
    widthDp = 600,
    heightDp = 160
)
@Composable
private fun PreviewSettingsActionCardIdle() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(24.dp)
        ) {
            SettingsActionCard(
                description = "Clear temporary files and database cache to free up space.",
                buttonLabel = "Clear Cache (24.6 MB)",
                isLoading = false,
                loadingLabel = "Clearing...",
                successMessage = "Cache cleared successfully!",
                showSuccess = false,
                icon = Icons.Default.Delete,
                onClick = {}
            )
        }
    }
}

@Preview(
    name = "Single Action Card — loading",
    showBackground = true,
    backgroundColor = 0xFF1A1A1A,
    widthDp = 600,
    heightDp = 160
)
@Composable
private fun PreviewSettingsActionCardLoading() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(24.dp)
        ) {
            SettingsActionCard(
                description = "Clear temporary files and database cache to free up space.",
                buttonLabel = "Clear Cache (24.6 MB)",
                isLoading = true,
                loadingLabel = "Clearing...",
                successMessage = "Cache cleared successfully!",
                showSuccess = false,
                icon = Icons.Default.Delete,
                onClick = {}
            )
        }
    }
}

@Preview(
    name = "Single Action Card — success",
    showBackground = true,
    backgroundColor = 0xFF1A1A1A,
    widthDp = 600,
    heightDp = 160
)
@Composable
private fun PreviewSettingsActionCardSuccess() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .padding(24.dp)
        ) {
            SettingsActionCard(
                description = "Clear temporary files and database cache to free up space.",
                buttonLabel = "Clear Cache (0 B)",
                isLoading = false,
                loadingLabel = "Clearing...",
                successMessage = "Cache cleared successfully!",
                showSuccess = true,
                icon = Icons.Default.Delete,
                onClick = {}
            )
        }
    }
}
