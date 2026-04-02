package com.kiduyuk.klausk.kiduyutv.ui.screens.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistRemove
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.SettingsViewModel

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
                        // Cache
                        isClearingCache = uiState.isClearingCache,
                        cacheClearSuccess = uiState.cacheClearSuccess,
                        cacheSize = uiState.cacheSize,
                        onClearCacheClick = { viewModel.clearCache(context) },
                        // My List
                        isClearingMyList = uiState.isClearingMyList,
                        myListClearSuccess = uiState.myListClearSuccess,
                        onClearMyListClick = { viewModel.clearMyList() },
                        // Watch History
                        isClearingWatchHistory = uiState.isClearingWatchHistory,
                        watchHistoryClearSuccess = uiState.watchHistoryClearSuccess,
                        onClearWatchHistoryClick = { viewModel.clearWatchHistory() }
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
                    isFocused  -> CardDark.copy(alpha = 0.6f)
                    else       -> Color.Transparent
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
                                else      -> PrimaryRed
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
    heightDp = 400
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
                whatsNew = "Fixed some minor bugs and improved performance."
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