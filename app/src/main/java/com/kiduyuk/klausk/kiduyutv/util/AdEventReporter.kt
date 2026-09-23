package com.kiduyuk.klausk.kiduyutv.util

import android.util.Log

/** Privacy-safe, structured ad diagnostics. Do not add identifiers or ad responses here. */
object AdEventReporter {
    private const val TAG = "AdEvent"

    fun report(placement: String, format: String, network: String, outcome: String) {
        Log.i(TAG, "placement=$placement format=$format network=$network outcome=$outcome")
    }
}
