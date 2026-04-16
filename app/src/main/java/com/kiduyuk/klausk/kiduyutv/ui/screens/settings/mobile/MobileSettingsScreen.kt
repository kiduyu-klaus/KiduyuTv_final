package com.kiduyuk.klausk.kiduyutv.ui.screens.settings.mobile

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.transform.CircleCropTransformation
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.AuthManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBackClick: () -> Unit,
    onMyListClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val myList by MyListManager.myList.collectAsState()
    var showProviderPicker by remember { mutableStateOf(false) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSignInError by remember { mutableStateOf<String?>(null) }

    // Auth state from AuthManager
    val isSignedIn by AuthManager.isSignedIn.collectAsState()
    val userDisplayName by AuthManager.userDisplayName.collectAsState()
    val userEmail by AuthManager.userEmail.collectAsState()
    val userPhotoUrl by AuthManager.userPhotoUrl.collectAsState()
    val isAuthLoading by AuthManager.isLoading.collectAsState()
    val currentUid by AuthManager.authStateFlow.collectAsState()

    // TV Login state
    var tvCodeInput by remember { mutableStateOf("") }
    var isAuthorizingTv by remember { mutableStateOf(false) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
        
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account.idToken?.let { token ->
                AuthManager.signInWithGoogle(
                    idToken = token,
                    onSuccess = { user ->
                        Toast.makeText(context, "Signed in as ${user.displayName}", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        showSignInError = exception.message ?: "Sign-in failed"
                    }
                )
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            showSignInError = "Sign-in failed (code ${e.statusCode}): ${e.localizedMessage}"
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSettingsData(context)
    }

    // Show sign-in error toast
    LaunchedEffect(showSignInError) {
        showSignInError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            showSignInError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // ── Account Section ────────────────────────────────────────────────
            SettingsGroup(title = "Account") {
                if (isSignedIn) {
                    // Signed in state - show user info
                    AccountSignedInCard(
                        displayName = userDisplayName ?: "User",
                        email = userEmail ?: "",
                        photoUrl = userPhotoUrl,
                        onSignOutClick = {
                            QuitDialog(
                                context = context,
                                title = "Sign Out?",
                                message = "Are you sure you want to sign out of your account?",
                                positiveButtonText = "Sign Out",
                                negativeButtonText = "Cancel",
                                lottieAnimRes = R.raw.exit,
                                onNo = {},
                                onYes = {
                                    AuthManager.signOut {
                                        Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ).show()
                        },
                        onDeleteAccountClick = { showDeleteAccountDialog = true }
                    )
                } else {
                    // Signed out state - show sign in button
                    AccountSignInCard(
                        onSignInClick = {
                            val signInClient = AuthManager.getGoogleSignInClient()
                            if (signInClient != null) {
                                googleSignInLauncher.launch(signInClient.signInIntent)
                            } else {
                                Toast.makeText(context, "Unable to start sign-in", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isLoading = isAuthLoading
                    )
                }
            }

            // ── TV Login Section (Only if signed in) ───────────────────────────
            if (isSignedIn) {
                Spacer(modifier = Modifier.height(24.dp))
                SettingsGroup(title = "TV Login") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Authorize Android TV",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Enter the 6-digit code shown on your TV to sync your account.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = tvCodeInput,
                            onValueChange = { if (it.length <= 6) tvCodeInput = it.uppercase() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("e.g. A7B29X") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryRed,
                                unfocusedBorderColor = TextTertiary.copy(alpha = 0.3f)
                            ),
                            trailingIcon = {
                                if (isAuthorizingTv) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else if (tvCodeInput.length == 6) {
                                    IconButton(onClick = {
                                        val code = tvCodeInput.trim()
                                        if (code.length == 6) {
                                            isAuthorizingTv = true
                                            val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                                            val codeRef = database.getReference("tv_codes/$code")
                                            
                                            codeRef.get().addOnSuccessListener { snapshot ->
                                                if (snapshot.exists()) {
                                                    val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                                                    val now = System.currentTimeMillis()
                                                    val fiveMinutes = 5 * 60 * 1000
                                                    
                                                    if (now - createdAt < fiveMinutes) {
                                                        // Code is valid and not expired, write user data
                                                        val uid = currentUid?.uid
                                                        if (uid != null) {
                                                            // Write complete user profile data for TV to fetch
                                                            val authorizedUser = mapOf(
                                                                "uid" to uid,
                                                                "displayName" to (userDisplayName ?: ""),
                                                                "email" to (userEmail ?: ""),
                                                                "photoUrl" to (userPhotoUrl ?: "")
                                                            )
                                                            codeRef.child("authorizedUser").setValue(authorizedUser)
                                                                .addOnSuccessListener {
                                                                    Toast.makeText(context, "TV Authorized Successfully!", Toast.LENGTH_SHORT).show()
                                                                    tvCodeInput = ""
                                                                    isAuthorizingTv = false
                                                                    
                                                                    // Clean up after a short delay
                                                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                                        codeRef.removeValue()
                                                                    }, 2000)
                                                                }
                                                                .addOnFailureListener {
                                                                    Toast.makeText(context, "Failed to authorize TV", Toast.LENGTH_SHORT).show()
                                                                    isAuthorizingTv = false
                                                                }
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Code has expired. Generate a new one on TV.", Toast.LENGTH_LONG).show()
                                                        isAuthorizingTv = false
                                                        codeRef.removeValue() // Cleanup expired code
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Invalid code. Please check and try again.", Toast.LENGTH_SHORT).show()
                                                    isAuthorizingTv = false
                                                }
                                            }.addOnFailureListener {
                                                Toast.makeText(context, "Error connecting to Firebase", Toast.LENGTH_SHORT).show()
                                                isAuthorizingTv = false
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Authorize", tint = Color.Green)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── My List Section ──────────────────────────────────────────────────
            SettingsGroup(title = "My List") {
                SettingsItem(
                    icon = Icons.Default.Bookmark,
                    title = "My List",
                    subtitle = "${myList.size} items saved",
                    onClick = onMyListClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Playback Section ────────────────────────────────────────────────
            SettingsGroup(title = "Playback") {
                SettingsItem(
                    icon = Icons.Default.PlayCircle,
                    title = "Default Provider",
                    subtitle = if (uiState.defaultProvider == SettingsManager.AUTO)
                        "Ask each time"
                    else
                        uiState.defaultProvider,
                    onClick = { showProviderPicker = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── App Settings Section ───────────────────────────────────────────
            SettingsGroup(title = "App Settings") {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear Cache",
                    subtitle = "Current cache: ${uiState.cacheSize}",
                    onClick = {
                        QuitDialog(
                            context = context,
                            title = "Clear Cache?",
                            message = "Are you sure you want to clear the app cache? This will free up space but may slow down initial loading.",
                            positiveButtonText = "Clear",
                            negativeButtonText = "Cancel",
                            lottieAnimRes = R.raw.exit,
                            onYes = { viewModel.clearCache(context) }
                        ).show()
                    },
                    isLoading = uiState.isClearingCache,
                    isSuccess = uiState.cacheClearSuccess
                )
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Clear Watch History",
                    subtitle = "Remove all previously watched content",
                    onClick = {
                        QuitDialog(
                            context = context,
                            title = "Clear History?",
                            message = "Are you sure you want to clear your entire watch history? This action cannot be undone.",
                            positiveButtonText = "Clear",
                            negativeButtonText = "Cancel",
                            lottieAnimRes = R.raw.exit,
                            onYes = { viewModel.clearWatchHistory() }
                        ).show()
                    },
                    isLoading = uiState.isClearingWatchHistory,
                    isSuccess = uiState.watchHistoryClearSuccess
                )
                SettingsItem(
                    icon = Icons.Default.PlaylistRemove,
                    title = "Clear My List",
                    subtitle = "Remove all items from your favorites",
                    onClick = {
                        QuitDialog(
                            context = context,
                            title = "Clear My List?",
                            message = "Are you sure you want to remove all items from your list? This action cannot be undone.",
                            positiveButtonText = "Clear",
                            negativeButtonText = "Cancel",
                            lottieAnimRes = R.raw.exit,
                            onYes = { viewModel.clearMyList() }
                        ).show()
                    },
                    isLoading = uiState.isClearingMyList,
                    isSuccess = uiState.myListClearSuccess
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── App Information Section ────────────────────────────────────────
            SettingsGroup(title = "App Information") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About KiduyuTV",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}",
                    onClick = { /* Could show a dialog */ }
                )
                SettingsItem(
                    icon = Icons.Default.Public,
                    title = "Visit Website",
                    subtitle = "https://kiduyu-klaus.github.io/KiduyuTv_final/",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://kiduyu-klaus.github.io/KiduyuTv_final/".toUri())
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Updates Section ────────────────────────────────────────────────
            SettingsGroup(title = "Updates") {
                if (uiState.releaseTitle != null) {
                    SettingsItem(
                        icon = Icons.Default.NewReleases,
                        title = "What's New",
                        subtitle = uiState.releaseTitle!!,
                        onClick = { showWhatsNewDialog = true }
                    )
                }
                SettingsItem(
                    icon = Icons.Default.Update,
                    title = "Check for Updates",
                    subtitle = uiState.updateCheckResult ?: "Stay on the latest version",
                    onClick = { viewModel.checkForUpdates(context) },
                    isLoading = uiState.isCheckingForUpdates
                )
                if (uiState.updateAvailable) {
                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = if (uiState.isDownloadingUpdate) "Downloading Update..." else "Download Update",
                        subtitle = if (uiState.isDownloadingUpdate) "Progress: ${uiState.downloadProgress}%" else "New version is available",
                        onClick = { viewModel.downloadAndInstallUpdate(context) },
                        isLoading = uiState.isDownloadingUpdate,
                        progress = if (uiState.isDownloadingUpdate) uiState.downloadProgress / 100f else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "KiduyuTV v${BuildConfig.VERSION_NAME}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Default Provider Picker Dialog ────────────────────────────────────────
    if (showProviderPicker) {
        val options = listOf(SettingsManager.AUTO) + SettingsManager.PROVIDERS
        AlertDialog(
            onDismissRequest = { showProviderPicker = false },
            containerColor = CardDark,
            title = {
                Text(
                    "Default Provider",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Choose which provider opens automatically when you tap Play. " +
                                "Select \"Auto\" to always see the full list.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    options.forEach { option ->
                        val isSelected = option == uiState.defaultProvider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setDefaultProvider(context, option)
                                    showProviderPicker = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setDefaultProvider(context, option)
                                    showProviderPicker = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PrimaryRed,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (option == SettingsManager.AUTO) {
                                    Text(
                                        "Show provider list each time",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderPicker = false }) {
                    Text("Done", color = PrimaryRed)
                }
            }
        )
    }

    // ── What's New Dialog ──────────────────────────────────────────────────────
    if (showWhatsNewDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsNewDialog = false },
            containerColor = CardDark,
            title = {
                Text(
                    text = uiState.releaseTitle ?: "What's New",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (uiState.releaseNotes != null) {
                        Text(
                            text = uiState.releaseNotes!!,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        Text(
                            text = "Loading release notes...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhatsNewDialog = false }) {
                    Text("Close", color = PrimaryRed)
                }
            }
        )
    }

    // ── Delete Account Dialog ───────────────────────────────────────────────────
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = CardDark,
            title = {
                Text(
                    "Delete Account?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will permanently delete your account and all associated data. This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        AuthManager.deleteAccount(
                            onSuccess = {
                                Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { exception ->
                                Toast.makeText(context, "Failed to delete account: ${exception.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = PrimaryRed)
                }
            }
        )
    }
}

@Composable
private fun AccountSignInCard(
    onSignInClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Google Icon placeholder
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Not signed in",
            color = TextSecondary,
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Sign in to sync your data across devices",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sign in with Google button
        Button(
            onClick = onSignInClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
private fun AccountSignedInCard(
    displayName: String,
    email: String,
    photoUrl: String?,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo or placeholder
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PrimaryRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "U",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = email,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sign out button
        OutlinedButton(
            onClick = onSignOutClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextPrimary
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Delete account button
        TextButton(
            onClick = onDeleteAccountClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.Red
            )
        ) {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Account")
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = PrimaryRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    progress: Float? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            if (isLoading && progress == null) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryRed, strokeWidth = 2.dp)
            } else if (isSuccess) {
                Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.Green, modifier = Modifier.size(20.dp))
            } else if (progress == null) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                color = PrimaryRed,
                trackColor = SurfaceDark,
            )
        }
    }
}
