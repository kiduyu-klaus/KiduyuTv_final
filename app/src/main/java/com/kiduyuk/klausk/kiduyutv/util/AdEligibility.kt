package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log

/**
 * The single eligibility decision used before any advertising SDK loads or
 * displays inventory. This deliberately fails closed: an unresolved UMP
 * request is not the same thing as permission to request ads.
 */
object AdEligibility {
    private const val TAG = "AdEligibility"

    fun canRequestAds(context: Context): Boolean {
        val allowed = try {
            val adsDisabled = SettingsManager(context).isAdsDisabled()
            val consentAllowsRequests = ConsentManager.canRequestAds(context)
            val eligible = !adsDisabled && consentAllowsRequests
            if (!eligible) {
                Log.i(
                    TAG,
                    "Ad request suppressed: adsDisabled=$adsDisabled, " +
                        "consentAllowsRequests=$consentAllowsRequests"
                )
            }
            eligible
        } catch (error: Exception) {
            Log.w(TAG, "Unable to evaluate ad eligibility", error)
            false
        }
        return allowed
    }

    /**
     * Determines whether non-AdMob networks may request ads.
     *
     * Fire OS may not provide Google Play Services, so a completed UMP flow
     * can exist without Google Mobile Ads reporting canRequestAds(). Secondary
     * networks use their non-personalized mode in that case. AdMob continues
     * to use [canRequestAds] and is never enabled by this method.
     */
    fun canRequestSecondaryAds(context: Context): Boolean {
        return try {
            if (SettingsManager(context).isAdsDisabled()) return false
            canRequestAds(context) ||
                (UpdateUtil.isFireTvDevice(context) && ConsentManager.hasCompletedConsentFlow())
        } catch (error: Exception) {
            Log.w(TAG, "Unable to evaluate secondary ad eligibility", error)
            false
        }
    }
}
