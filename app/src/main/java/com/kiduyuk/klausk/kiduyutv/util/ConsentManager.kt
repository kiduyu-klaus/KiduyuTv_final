package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.unity3d.ads.metadata.MetaData

/**
 * Manages GDPR consent using Google's User Messaging Platform (UMP).
 * Required for EEA users before showing ads.
 *
 * After UMP resolves, consent is propagated to all secondary ad networks
 * (StartApp, Unity Ads, Wortise) so they also respect the user's choice.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    /**
     * True when the last `requestConsentInfoUpdate` call failed with a
     * publisher-side misconfiguration (typically "no forms configured for
     * the input app ID" in the AdMob console). When this is true,
     * `ConsentInformation.canRequestAds()` will permanently return false
     * until consent is reset, so callers must not treat that false as a
     * user opt-out — they should still try to serve non-personalized ads.
     */
    @Volatile
    private var publisherMisconfigured = false

    /**
     * Requests the latest consent information and shows a consent form if required.
     * Call from SplashActivity before initializing any ad SDK.
     *
     * After the user dismisses the form (or if no form is required), consent
     * flags are forwarded to StartApp, Unity Ads, and Wortise automatically.
     *
     * @param activity The calling Activity (needed to show the form).
     * @param onComplete Fires when consent has been handled — initialize ad SDKs here.
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
                // Consent info updated successfully — clear any prior misconfig flag.
                publisherMisconfigured = false
                if (consentInfo.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, consentInfo, onComplete)
                } else {
                    Log.i(TAG, "Consent form not available")
                    propagateConsentToAllNetworks(activity)
                    onComplete()
                }
            },
            { formError ->
                // Most common cause here is the AdMob app ID not having a
                // privacy message configured in the AdMob console. We still
                // proceed so the app can serve (non-personalized) ads, but
                // flag it so [canRequestAds] consumers can distinguish this
                // from a real user opt-out.
                val msg = formError.message.orEmpty()
                publisherMisconfigured = msg.contains("Publisher misconfiguration", ignoreCase = true) ||
                    msg.contains("no form", ignoreCase = true)
                Log.w(TAG, "Consent info update failed: $msg")
                Log.w(
                    TAG,
                    if (publisherMisconfigured)
                        "AdMob consent form is not configured for this app ID. " +
                            "Create one at https://admob.google.com (Privacy & messaging). " +
                            "Falling back to non-personalized ads."
                    else
                        "Proceeding with non-personalized ads."
                )
                propagateConsentToAllNetworks(activity)
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
            propagateConsentToAllNetworks(activity)
            onComplete()
        }
    }

    /**
     * Forwards the UMP consent decision to supported secondary networks.
     * This should be called **after** UMP has resolved so each SDK knows
     * whether it may use personal data for ad targeting.
     *
     * Note on SDK API compatibility:
     * - StartApp receives consent during SDK initialization in StartAppAdManager.
     * - Unity Ads uses the `MetaData` "gdpr.consent" / "privacy.consent" keys.
     * - Wortise SDK 1.7.2 uses the UMP integration implicitly — no manual
     *   call required. We log only.
     */
    private fun propagateConsentToAllNetworks(context: Context) {
        val canPersonalize = canShowPersonalizedAds(context)
        Log.i(TAG, "Propagating consent to all networks: personalize=$canPersonalize")

        // ── StartApp ──────────────────────────────────────────────────────
        Log.i(TAG, "StartApp: consent will be applied during SDK initialization.")

        // ── Unity Ads ─────────────────────────────────────────────────────
        try {
            MetaData(context).apply {
                set("gdpr.consent", if (canPersonalize) "true" else "false")
                set("privacy.consent", if (canPersonalize) "true" else "false")
                commit()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward consent to Unity: ${e.message}")
        }

        // ── Wortise ───────────────────────────────────────────────────────
        // Wortise SDK 1.7.2 reads Google UMP consent automatically once UMP
        // is initialised. No manual setUserConsent() call is required.
        Log.i(TAG, "Wortise: consent handled automatically via UMP integration.")
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
     * Checks if personalized ads are allowed based on UMP consent.
     */
    fun canShowPersonalizedAds(context: Context): Boolean {
        return try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            // canRequestAds returns true when either:
            // 1. User consented to personalized ads
            // 2. User is not in EEA (no consent required)
            // 3. User consented to non-personalized ads
            // For a stricter check, inspect the consent string directly.
            info.canRequestAds()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking personalized consent: ${e.message}")
            true
        }
    }

    /**
     * Resets consent status (for testing purposes).
     */
    fun resetConsent(context: Context) {
        try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.reset()
            publisherMisconfigured = false
            Log.i(TAG, "Consent reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting consent: ${e.message}")
        }
    }

    /**
     * True when the most recent UMP request failed because the AdMob app ID
     * has no consent form configured in the publisher console. When true,
     * `canRequestAds()` returns false but that is *not* a user opt-out —
     * callers should still initialize the ad SDK and serve non-personalized
     * ads rather than blocking the app entirely.
     */
    fun isPublisherMisconfigured(): Boolean = publisherMisconfigured
}
