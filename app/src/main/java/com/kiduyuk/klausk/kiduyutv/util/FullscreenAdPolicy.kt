package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Applies one persisted cooldown to every fullscreen ad format.
 *
 * Standard interstitial, app-open, rewarded, and rewarded-interstitial ads all
 * share this policy so that one fullscreen impression cannot be followed by
 * another fullscreen impression immediately afterward.
 */
object FullscreenAdPolicy {
    private const val PREFS = "ad_policy"
    private const val KEY_LAST_FULLSCREEN_WALL_MS = "last_fullscreen_wall_ms"

    // Legacy keys are read once as a compatibility fallback after upgrading.
    private const val KEY_LAST_INTERSTITIAL_WALL_MS = "last_interstitial_wall_ms"
    private const val KEY_LAST_APP_OPEN_WALL_MS = "last_app_open_wall_ms"

    /** The minimum time between two successfully displayed fullscreen ads. */
    const val FULLSCREEN_COOLDOWN_MS = 2 * 60 * 1000L
    private const val CONTENT_TRANSITION_DEBOUNCE_MS = 15_000L

    // Prevents two independent navigation/foreground events from starting
    // competing fullscreen fallback chains before either one is displayed.
    private val fullscreenRequestInProgress = AtomicBoolean(false)
    private var lastShownElapsedMs = 0L
    private var lastContentTransitionRequestElapsedMs = 0L

    /** Returns true when no fullscreen ad has been shown during the cooldown. */
    fun canShowFullscreen(context: Context): Boolean {
        val preferences = preferences(context)
        val elapsedReference = synchronized(this) { lastShownElapsedMs }
        if (elapsedReference > 0L) {
            return SystemClock.elapsedRealtime() - elapsedReference >= FULLSCREEN_COOLDOWN_MS
        }
        val lastShown = preferences.getLong(KEY_LAST_FULLSCREEN_WALL_MS, 0L)
            .takeIf { it > 0L }
            ?: maxOf(
                preferences.getLong(KEY_LAST_INTERSTITIAL_WALL_MS, 0L),
                preferences.getLong(KEY_LAST_APP_OPEN_WALL_MS, 0L)
            )
        return cooldownElapsed(System.currentTimeMillis(), lastShown)
    }

    /** Records a fullscreen impression after the SDK confirms that it was shown. */
    fun recordFullscreenShown(context: Context) {
        val elapsedNow = SystemClock.elapsedRealtime()
        synchronized(this) { lastShownElapsedMs = elapsedNow }
        preferences(context).edit()
            .putLong(KEY_LAST_FULLSCREEN_WALL_MS, System.currentTimeMillis())
            .apply()
    }

    /** Claims the in-process fullscreen request slot for a fallback dispatcher. */
    fun tryBeginRequest(): Boolean = fullscreenRequestInProgress.compareAndSet(false, true)

    /** Releases the in-process fullscreen request slot after a request completes. */
    fun endRequest() {
        fullscreenRequestInProgress.set(false)
    }

    /** Prevents rapid navigation events from repeatedly starting ad requests. */
    fun shouldRequestContentTransition(): Boolean = synchronized(this) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastContentTransitionRequestElapsedMs < CONTENT_TRANSITION_DEBOUNCE_MS) {
            false
        } else {
            lastContentTransitionRequestElapsedMs = now
            true
        }
    }

    /** Compatibility alias for standard interstitial call sites. */
    fun canShowInterstitial(context: Context): Boolean = canShowFullscreen(context)

    /** Compatibility alias for app-open call sites. */
    fun canShowAppOpen(context: Context): Boolean = canShowFullscreen(context)

    /** Compatibility alias for standard interstitial call sites. */
    fun recordInterstitialShown(context: Context) = recordFullscreenShown(context)

    /** Compatibility alias for app-open call sites. */
    fun recordAppOpenShown(context: Context) = recordFullscreenShown(context)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A backward clock adjustment fails open instead of suppressing ads indefinitely. */
    private fun cooldownElapsed(now: Long, previous: Long): Boolean =
        previous <= 0L || now < previous || now - previous >= FULLSCREEN_COOLDOWN_MS
}
