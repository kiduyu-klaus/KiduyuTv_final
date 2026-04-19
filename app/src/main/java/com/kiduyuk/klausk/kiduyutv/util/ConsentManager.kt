package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Manages GDPR consent using Google's User Messaging Platform (UMP).
 * Required for EEA users before showing ads.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    /**
     * Requests the latest consent information and shows a consent form if required.
     * Call from SplashActivity before AdManager.init().
     *
     * @param activity The calling Activity (needed to show the form).
     * @param onComplete Fires when consent has been handled — proceed to call AdManager.init() here.
     */
    fun requestConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated successfully
                if (consentInfo.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, consentInfo, onComplete)
                } else {
                    Log.i(TAG, "Consent form not available")
                    onComplete()
                }
            },
            { formError ->
                Log.w(TAG, "Consent info update failed: ${formError.message}")
                // Proceed even on failure — non-EEA users won't see a form
onComplete()
            }
        )
    }

    private fun loadAndShowConsentForm(
        activity: Activity,
        consentInfo: ConsentInformation,
        onComplete: () -> Unit
    ) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Consent form error: ${formError.message}")
            }
            onComplete()
        }
    }

    /**
     * Checks if ads can be requested based on consent status.
     */
    fun canRequestAds(context: Context): Boolean {
        return try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.canRequestAds()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking consent status: ${e.message}")
            true // Assume true if error occurs
        }
    }

    /**
     * Resets consent status (for testing purposes).
     */
    fun resetConsent(context: Context) {
        try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.reset()
            Log.i(TAG, "Consent reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting consent: ${e.message}")
        }
    }
}
