package com.kiduyuk.klausk.kiduyutv.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** Waits briefly for an asynchronous ad preload without blocking the UI thread. */
internal object AdInventoryWaiter {
    private const val POLL_INTERVAL_MS = 50L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun awaitReady(
        timeoutMs: Long,
        isReady: () -> Boolean,
        requestLoad: () -> Unit,
        shouldContinue: () -> Boolean,
        onReady: () -> Unit,
        onTimeout: () -> Unit
    ) {
        val completed = AtomicBoolean(false)
        val begin = Runnable {
            if (completed.get()) return@Runnable
            if (!shouldContinue()) {
                if (completed.compareAndSet(false, true)) onTimeout()
                return@Runnable
            }
            if (isReady()) {
                if (completed.compareAndSet(false, true)) onReady()
                return@Runnable
            }

            val deadline = SystemClock.uptimeMillis() + timeoutMs
            try {
                requestLoad()
            } catch (_: Exception) {
                if (completed.compareAndSet(false, true)) onTimeout()
                return@Runnable
            }

            val poll = object : Runnable {
                override fun run() {
                    if (completed.get()) return
                    if (!shouldContinue()) {
                        if (completed.compareAndSet(false, true)) onTimeout()
                        return
                    }
                    if (isReady()) {
                        if (completed.compareAndSet(false, true)) onReady()
                        return
                    }
                    if (SystemClock.uptimeMillis() >= deadline) {
                        if (completed.compareAndSet(false, true)) onTimeout()
                        return
                    }
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
            mainHandler.postDelayed(poll, POLL_INTERVAL_MS)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) begin.run() else mainHandler.post(begin)
    }
}
