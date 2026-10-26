package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * Warns before a direct-stream request fans out to every provider.
 *
 * This check belongs immediately before provider resolution, rather than only
 * at the launch boundary, because activities can also be opened directly and
 * can reload streams without going through [DirectStreamLauncher].
 */
object DirectStreamProviderWarning {
    private const val ALL_PROVIDERS_LABEL = "All Providers"

    /**
     * Shows the warning when the saved default direct-stream provider is AUTO.
     * Returns true when a dialog was shown; the caller must then wait for the
     * user's choice before starting the fetch.
     */
    fun showIfNeeded(
        activity: Activity,
        intent: Intent,
        onContinue: () -> Unit
    ): Boolean {
        val requestedProvider = intent.getStringExtra(DirectStreamActivity.EXTRA_PROVIDER)
        if (activity.isFinishing || activity.isDestroyed || !isAllProviders(
                SettingsManager(activity).getDefaultDirectStreamProvider()
            ) || !isAllProviders(requestedProvider)
        ) {
            return false
        }

        // Use the platform builder here. MobileDirectStreamActivity is a
        // ComponentActivity using a Compose theme, which is not guaranteed to
        // inherit Theme.AppCompat as required by MaterialAlertDialogBuilder.
        val dialog = AlertDialog.Builder(activity)
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
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.requestFocus()
        }
        dialog.show()
        return true
    }

    fun isAllProviders(provider: String?): Boolean {
        val value = provider?.trim().orEmpty()
        return value.isBlank() ||
            value.equals(SettingsManager.AUTO, ignoreCase = true) ||
            value.equals(ALL_PROVIDERS_LABEL, ignoreCase = true)
    }
}
