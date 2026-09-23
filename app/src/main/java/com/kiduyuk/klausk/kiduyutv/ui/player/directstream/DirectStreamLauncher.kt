package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single launch boundary for native direct playback.
 *
 * A preloaded interstitial is shown before the player opens. If no ad is
 * ready, playback opens immediately while the next ad continues loading in
 * the background.
 */
object DirectStreamLauncher {

    fun launch(
        context: Context,
        intent: Intent,
        launchIntent: ((Intent) -> Unit)? = null,
        onLaunched: () -> Unit = {}
    ) {
        val activity = context.findActivity()
        val launched = AtomicBoolean(false)
        val openPlayer = {
            if (launched.compareAndSet(false, true)) {
                if (launchIntent != null) {
                    launchIntent(intent)
                } else if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.applicationContext.startActivity(intent)
                }
                onLaunched()
            }
        }

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            openPlayer()
        } else {
            AdFallbackDispatcher.showInterstitial(
                activity = activity,
                placement = AdFallbackDispatcher.AdPlacement.PLAYER_LAUNCH,
                onDismissed = openPlayer
            )
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
