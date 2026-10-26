package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.util.InterstitialFallbackDispatcher
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
            val openAfterInterstitial = {
                InterstitialFallbackDispatcher.show(
                    activity = activity,
                    placement = InterstitialFallbackDispatcher.Placement.PLAYER_LAUNCH,
                    onDismissed = openPlayer
                )
            }
            if (shouldConfirmAllProviders(activity, intent)) {
                showAllProvidersWarning(activity, openAfterInterstitial)
            } else {
                openAfterInterstitial()
            }
        }
    }

    /**
     * "All Providers" is currently represented by AUTO/an empty provider key.
     * Do not warn when this launch already supplies an explicit provider, since
     * that request will not fan out to every compatible provider.
     */
    private fun shouldConfirmAllProviders(activity: Activity, intent: Intent): Boolean {
        val configured = SettingsManager(activity).getDefaultDirectStreamProvider()
        val requested = intent.getStringExtra(DirectStreamActivity.EXTRA_PROVIDER)
        return isAllProviders(configured) && isAllProviders(requested)
    }

    private fun isAllProviders(provider: String?): Boolean {
        val value = provider?.trim().orEmpty()
        return value.isBlank() ||
            value.equals(SettingsManager.AUTO, ignoreCase = true) ||
            value.equals("All Providers", ignoreCase = true)
    }

    private fun showAllProvidersWarning(activity: Activity, onContinue: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Set a default provider")
            .setMessage(
                "Fetching from All Providers can take longer and this option will be " +
                    "removed in a future update. Choose a default direct stream provider " +
                    "in Playback settings."
            )
            .setNegativeButton("Open settings") { _, _ ->
                activity.startActivity(MainActivity.createSettingsIntent(activity))
            }
            .setPositiveButton("Continue with all providers") { _, _ ->
                onContinue()
            }
            .show()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
