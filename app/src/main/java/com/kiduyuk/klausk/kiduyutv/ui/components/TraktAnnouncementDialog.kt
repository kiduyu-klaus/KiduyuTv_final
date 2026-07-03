package com.kiduyuk.klausk.kiduyutv.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import androidx.core.content.edit
import kotlinx.coroutines.tasks.await

@Composable
fun TraktAnnouncementDialog() {
    val context = LocalContext.current
    val dialogPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (dialogPrefs.getBoolean("trakt_announcement_shown", false)) {
            return@LaunchedEffect
        }

        runCatching {
            FirebaseManager.getFirebaseDatabaseInstance()
                .getReference("app_config/home_dialog/dialog_message")
                .get()
                .await()
                .getValue(String::class.java)
                ?.trim()
        }.onSuccess { message ->
            if (!message.isNullOrBlank()) {
                dialogMessage = message
                showDialog = true
            }
        }.onFailure { throwable ->
            Log.w("TraktAnnouncementDialog", "Failed to fetch home dialog message", throwable)
        }
    }

    fun dismissDialog() {
        showDialog = false
        dialogPrefs.edit { putBoolean("trakt_announcement_shown", true) }
    }

    if (showDialog && !dialogMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { dismissDialog() },
            containerColor = CardDark,
            title = {
                Text(
                    text = "Kiduyu TV",
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = dialogMessage.orEmpty(),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { dismissDialog() }
                ) {
                    Text("OK", color = TextSecondary)
                }
            }
        )
    }
}
