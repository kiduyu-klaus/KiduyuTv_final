package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberPhoneInterstitialBackClick(onBackClick: () -> Unit): () -> Unit {
    // Leaving a screen is not an ad placement. Preserve immediate navigation.
    return remember(onBackClick) { { onBackClick() } }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
