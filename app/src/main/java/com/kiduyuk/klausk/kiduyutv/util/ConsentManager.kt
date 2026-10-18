package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * Manages GDPR consent using Google's User Messaging Platform (UMP).
 * Required for EEA users before showing ads.
 *
 * After UMP resolves, consent is propagated to all secondary ad networks
 * (StartApp, Unity Ads, Wortise) so they also respect the user's choice.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"
    @Volatile private var consentResolved = false

    fun interface OnConsentGatheringCompleteListener {
        fun consentGatheringComplete(error: FormError?)
    }

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
        gatherConsent(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Consent gathering completed with error: ${formError.message}")
            }
            if (consentResolved && canRequestAds(activity)) {
                propagateConsentToAllNetworks(activity)
            }
            onComplete()
        }
    }

    /**
     * Requests the latest UMP consent information on every app launch, then
     * loads and shows the consent form if Google UMP says it is required.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener
    ) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                val consentStatusBeforeForm = consentInfo.consentStatus
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    // A completed UMP flow may permit personalized or
                    // non-personalized requests. A form/load error is not
                    // permission to initialize an advertising SDK.
                    consentResolved = formError == null
                    Log.i(
                        TAG,
                        "Consent form flow completed: resolved=$consentResolved " +
                            "canRequestAds=${canRequestAds(activity)}"
                    )
                    if (formError == null &&
                        consentStatusBeforeForm == ConsentInformation.ConsentStatus.REQUIRED
                    ) {
                        StartAppAdManager.recordConsentUpdate(activity)
                    }
                    onConsentGatheringCompleteListener.consentGatheringComplete(formError)
                }
            },
            { formError ->
                // UMP can report a transient update error even when its
                // persisted consent state already permits ad requests (for
                // example, outside the EEA or while the consent server is
                // temporarily unreachable). Trust UMP's authoritative
                // canRequestAds() result instead of permanently suppressing
                // all ads for the rest of this process.
                val requestsAllowed = canRequestAds(activity)
                consentResolved = requestsAllowed
                Log.w(TAG, "Consent info update failed: ${formError.message}")
                Log.i(TAG, "Consent fallback: resolved=$consentResolved canRequestAds=$requestsAllowed")
                onConsentGatheringCompleteListener.consentGatheringComplete(formError)
            }
        )
    }

    fun hasResolvedConsent(): Boolean = consentResolved

    /**
     * Forwards the UMP consent decision to supported secondary networks.
     * This should be called **after** UMP has resolved so each SDK knows
     * whether it may use personal data for ad targeting.
     *
     * Note on SDK API compatibility:
     * - StartApp receives consent during SDK initialization in StartAppAdManager.
     * - Unity Ads receives the applicable GDPR opt-in signal through its Privacy API.
     * - Wortise's SDK consent flow is requested after SDK initialization and
     *   before its ad units are loaded.
     */
    private fun propagateConsentToAllNetworks(context: Context) {
        val unityOptInConsent = unityGdprOptInConsent(context)
        Log.i(TAG, "Propagating consent to all networks: unityGdprOptIn=$unityOptInConsent")

        // ── StartApp ──────────────────────────────────────────────────────
        // Applied explicitly rather than left to SDK initialisation, so a consent
        // change reaches a Start.io SDK that is already running.
        StartAppAdManager.applyConsent(context)

        // ── Unity Ads ─────────────────────────────────────────────────────
        UnityAdManager.updateUserConsent(unityOptInConsent)

        // ── Wortise ───────────────────────────────────────────────────────
        // Wortise runs its own consent CMP after SDK initialization.
        Log.i(TAG, "Wortise consent is requested after SDK initialization.")
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
            false
        }
    }

    /**
     * Returns true when UMP requires the app to expose a privacy options entry point.
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (e: Exception) {
            Log.w(TAG, "Error checking privacy options status: ${e.message}")
            false
        }
    }

    /**
     * Shows the Google UMP privacy options form.
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: ConsentForm.OnConsentFormDismissedListener? = null
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
            } else {
                StartAppAdManager.recordConsentUpdate(activity)
            }
            propagateConsentToAllNetworks(activity)
            // The viewer just resolved consent in-app, so initialise the ad SDKs
            // now rather than waiting for the next process foreground.
            AdManager.retryInitIfEligible(activity)
            StartAppAdManager.retryInitIfEligible(activity)
            if (canRequestAds(activity)) {
                UnityAdManager.preloadAds(activity)
                // Wortise retry is temporarily disabled.
            }
            onConsentFormDismissedListener?.onConsentFormDismissed(formError)
        }
    }

    /**
     * Checks if personalized ads are allowed based on UMP consent.
     */
    fun canShowPersonalizedAds(context: Context): Boolean =
        unityGdprOptInConsent(context) == true

    private fun unityGdprOptInConsent(context: Context): Boolean? {
        return try {
            val preferences = context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE
            )
            if (preferences.all["IABTCF_gdprApplies"]?.toString() != "1") {
                return null
            }
            val purposeConsents = preferences.all["IABTCF_PurposeConsents"]?.toString()
            // Purposes 1, 3, and 4 cover storage/access and personalized ad creation/selection.
            val consentGranted = purposeConsents?.let {
                it.getOrNull(0) == '1' && it.getOrNull(2) == '1' && it.getOrNull(3) == '1'
            } ?: false
            if (!consentGranted) {
                Log.i(TAG, "Unity GDPR opt-in not present in UMP TCF purpose consents")
            }
            consentGranted
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Unity GDPR opt-in from UMP consent state", e)
            false
        }
    }

    /**
     * Resets consent status (for testing purposes).
     */
    fun resetConsent(context: Context) {
        try {
            val info = UserMessagingPlatform.getConsentInformation(context)
            info.reset()
            consentResolved = false
            Log.i(TAG, "Consent reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting consent: ${e.message}")
        }
    }

}
