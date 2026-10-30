package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object RecommendationScheduler {
    private const val WORK_NAME = "kiduyutv_recommendation_refresh"
    private const val PERIODIC_WORK_NAME = "kiduyutv_recommendation_refresh_daily"

    fun enqueueRefresh(context: Context) {
        if (!TvRecommendationPublisher.isTvLikeDevice(context)) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RecommendationRefreshWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RecommendationRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
        )
    }
}

class RecommendationRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = runCatching {
        if (TvRecommendationPublisher.refresh(applicationContext)) Result.success()
        else Result.retry()
    }.getOrElse { Result.retry() }
}
