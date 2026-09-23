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
            !SettingsManager(context).isAdsDisabled() &&
                ConsentManager.hasResolvedConsent() &&
                ConsentManager.canRequestAds(context)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to evaluate ad eligibility", error)
            false
        }
        if (!allowed) Log.i(TAG, "Ad request suppressed")
        return allowed
    }
}
