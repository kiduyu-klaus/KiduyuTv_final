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
 * After UMP resolves, Start.io receives its documented `pas` personalization
 * flag. Unity Ads reads UMP's IAB TCF string when Unity is enabled as a vendor
 * in the published UMP message.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"
    // Current IAB GVL v3 entry (vendor-list version 183, 2026-10-29).
    // Keep this aligned with Start.io's registered TCF vendor entry.
    private const val START_IO_TCF_VENDOR_ID = 1216
    @Volatile private var consentResolved = false
    @Volatile private var consentInformation: ConsentInformation? = null

    fun interface OnConsentGatheringCompleteListener {
        fun consentGatheringComplete(error: FormError?)
    }

    /**
     * Requests the latest consent information and shows a consent form if required.
     * Call from SplashActivity before initializing any ad SDK.
     *
     * After the user dismisses the form (or if no form is required), Start.io's
     * personalization flag is refreshed; Unity reads the stored TCF string.
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
     * Requests fresh UMP consent information on every app launch and lets UMP
     * load and show the consent form when required.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener
    ) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                val consentStatusAfterUpdate = consentInfo.consentStatus
                Log.i(
                    TAG,
                    "Consent info updated: status=${consentInfo.consentStatus} " +
                        "formAvailable=${consentInfo.isConsentFormAvailable}"
                )

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "UMP consent form flow completed with error: ${formError.message}")
                    }
                    consentResolved = formError == null || consentInfo.canRequestAds()
                    Log.i(
                        TAG,
                        "Consent form flow completed: resolved=$consentResolved " +
                            "canRequestAds=${consentInfo.canRequestAds()}"
                    )
                    if (formError == null &&
                        consentStatusAfterUpdate == ConsentInformation.ConsentStatus.REQUIRED
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
                val requestsAllowed = consentInfo.canRequestAds()
                consentResolved = requestsAllowed
                Log.w(TAG, "Consent info update failed: ${formError.message}")
                Log.i(TAG, "Consent fallback: resolved=$consentResolved canRequestAds=$requestsAllowed")
                onConsentGatheringCompleteListener.consentGatheringComplete(formError)
            }
        )
    }

    fun hasResolvedConsent(): Boolean = consentResolved

    /**
     * Refreshes Start.io's explicit personalization flag after UMP resolves.
     * Unity Ads consumes the stored IAB TCF string directly.
     *
     * Wortise is inactive in the current app configuration and retains its own
     * consent flow if it is enabled in a future release.
     */
    private fun propagateConsentToAllNetworks(context: Context) {
        Log.i(TAG, "Propagating consent to networks using UMP's stored privacy signals")

        // ── StartApp ──────────────────────────────────────────────────────
        // Applied explicitly rather than left to SDK initialisation, so a consent
        // change reaches a Start.io SDK that is already running.
        StartAppAdManager.applyConsent(context)

        // Unity Ads 4.21 consumes the IAB TCF string from a TCF-compliant CMP.
        // Do not reduce UMP's purpose/vendor choices to a single userConsent bit.

        // ── Wortise ───────────────────────────────────────────────────────
        // Wortise runs its own consent CMP after SDK initialization.
        Log.i(TAG, "Wortise consent is requested after SDK initialization.")
    }

    /**
     * Checks if ads can be requested based on consent status.
     */
    fun canRequestAds(context: Context): Boolean {
        return try {
            val info = getConsentInformation(context)
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
            val info = getConsentInformation(context)
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
     * Start.io's `pas` flag is true only when UMP records both the relevant
     * TCF purposes and Start.io's own vendor consent. Missing consent data
     * fails closed for personalization while ad requests remain eligible.
     */
    fun canShowStartIoPersonalizedAds(context: Context): Boolean {
        return try {
            val preferences = context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE
            )
            when (preferences.all["IABTCF_gdprApplies"]?.toString()) {
                // Start.io's own guidance requires this flag for GDPR-covered users.
                // Outside GDPR, no affirmative consent flag is required by that guidance.
                "0" -> return true
                "1" -> Unit
                else -> return false
            }

            val all = preferences.all
            val purposeConsents = all["IABTCF_PurposeConsents"]?.toString().orEmpty()
            val vendorConsents = all["IABTCF_VendorConsents"]?.toString().orEmpty()
            val purposesAllowed = listOf(1, 3, 4).all { purposeId ->
                purposeConsents.getOrNull(purposeId - 1) == '1'
            }
            val startIoVendorAllowed = vendorConsents.getOrNull(START_IO_TCF_VENDOR_ID - 1) == '1'
            val personalized = purposesAllowed && startIoVendorAllowed
            if (!personalized) {
                Log.i(TAG, "Start.io personalization denied by UMP purpose/vendor consent")
            }
            personalized
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Start.io TCF consent from UMP state", e)
            false
        }
    }

    /**
     * Resets consent status (for testing purposes).
     */
    fun resetConsent(context: Context) {
        try {
            val info = getConsentInformation(context)
            info.reset()
            consentResolved = false
            Log.i(TAG, "Consent reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting consent: ${e.message}")
        }
    }

    private fun getConsentInformation(context: Context): ConsentInformation {
        consentInformation?.let { return it }
        return synchronized(this) {
            consentInformation ?: UserMessagingPlatform.getConsentInformation(
                context.applicationContext
            ).also { consentInformation = it }
        }
    }

}
