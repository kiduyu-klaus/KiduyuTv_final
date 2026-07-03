package com.kiduyuk.klausk.kiduyutv.ui.components.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher

@Composable
fun rememberPhoneInterstitialBackClick(onBackClick: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    return remember(activity, onBackClick) {
        {
            if (BuildConfig.FLAVOR == "phone" && activity != null) {
                AdFallbackDispatcher.showInterstitial(activity) {
                    onBackClick()
                }
            } else {
                onBackClick()
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
