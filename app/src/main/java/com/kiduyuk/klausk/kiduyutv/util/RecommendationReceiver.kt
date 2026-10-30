package com.kiduyuk.klausk.kiduyutv.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RecommendationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INITIALIZE_PROGRAMS -> RecommendationScheduler.enqueueRefresh(context)
            ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED -> {
                val programId = intent.getLongExtra(EXTRA_PREVIEW_PROGRAM_ID, -1L)
                TvRecommendationPublisher.handleProgramDisabled(context, programId)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> RecommendationScheduler.enqueueRefresh(context)
        }
    }

    companion object {
        const val ACTION_INITIALIZE_PROGRAMS = "android.media.tv.action.INITIALIZE_PROGRAMS"
        const val ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED =
            "android.media.tv.action.PREVIEW_PROGRAM_BROWSABLE_DISABLED"
        const val EXTRA_PREVIEW_PROGRAM_ID = "android.media.tv.extra.PREVIEW_PROGRAM_ID"
    }
}
