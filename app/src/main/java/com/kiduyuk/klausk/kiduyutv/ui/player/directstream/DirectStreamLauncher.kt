package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.util.AdManager
import com.kiduyuk.klausk.kiduyutv.util.UnityAdManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single launch boundary for native direct playback.
 *
 * A preloaded interstitial is shown before the player opens. If no ad is
 * ready, playback opens immediately while the next ad continues loading in
 * the background.
 */
object DirectStreamLauncher {

    private const val TAG = "DirectStreamLauncher"

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
        } else if (AdManager.isInterstitialReady) {
            AdManager.showInterstitial(activity) { openPlayer() }
        } else if (UnityAdManager.isInterstitialReady) {
            UnityAdManager.showInterstitial(activity) { openPlayer() }
        } else {
            // Do not wait for an ad network to load while the user is opening
            // playback. Start a background preload for a future launch instead.
            Log.i(TAG, "No ready interstitial — opening DirectStreamActivity immediately")
            AdManager.preloadInterstitial(activity)
            UnityAdManager.preloadAds(activity)
            openPlayer()
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
