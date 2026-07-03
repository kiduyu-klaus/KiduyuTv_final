package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Mirrors Google's App Open sample: track the current foreground Activity
 * and show an app open ad when the process returns to the foreground.
 */
class AppOpenAdObserver private constructor(private val application: Application) :
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdObserver"

        @Volatile
        private var installed = false

        fun install(application: Application) {
            if (installed) return
            installed = true
            AppOpenAdObserver(application)
        }
    }

    private var currentActivity: Activity? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        currentActivity?.let { activity ->
            Log.i(TAG, "App moved to foreground, checking app open ad")
            AdManager.showAppOpenIfAvailable(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!AdManager.isAppOpenShowing) {
            currentActivity = activity
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
