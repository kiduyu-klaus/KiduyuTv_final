package com.kiduyuk.klausk.kiduyutv.network

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dialog manager for displaying network connectivity status.
 * Shows appropriate message and actions based on the current state.
 * Includes retry functionality that checks network status after retry
 * and shows the dialog again if still unavailable.
 */
object NetworkStateDialog {

    private const val TAG = "NetworkStateDialog"

    // Reference to current dialog
    private var currentDialog: AlertDialog? = null

    // Callback for retry action
    private var retryCallback: (() -> Unit)? = null

    // Coroutine scope for retry operations
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Job for ongoing retry operation
    private var retryJob: Job? = null

    // Store the last known bad state for retry re-display
    private var lastBadState: NetworkState? = null

    // Delay before checking network after retry (milliseconds)
    private val RETRY_CHECK_DELAY = 1500L

    /**
     * Shows or updates the dialog based on network state.
     * Automatically dismisses if connected.
     *
     * @param context Context for creating dialog
     * @param state Current network state
     * @param onRetry Optional callback for retry action
     */
    fun showIfNeeded(
        context: Context,
        state: NetworkState,
        onRetry: (() -> Unit)? = null
    ) {
        // Update callback
        retryCallback = onRetry

        when (state) {
            is NetworkState.Connected -> {
                dismiss()
                return
            }

            is NetworkState.NotConnected -> {
                // Store the bad state for potential retry re-display
                lastBadState = state
                showNoConnectionDialog(context)
            }

            is NetworkState.ConnectedNoInternet -> {
                // Store the bad state for potential retry re-display
                lastBadState = state
                showNoInternetDialog(context)
            }

            is NetworkState.Unknown -> {
                // Don't show dialog for unknown state
                dismiss()
            }
        }
    }

    /**
     * Shows the dialog with the current network state.
     * Creates appropriate dialog based on the state.
     *
     * @param context Context for creating dialog
     * @param state Current network state to display
     */
    fun show(
        context: Context,
        state: NetworkState
    ) {
        showIfNeeded(context, state, null)
    }

    /**
     * Dismisses the current dialog if visible.
     */
    fun dismiss() {
        try {
            currentDialog?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing dialog: ${e.message}")
        }
        currentDialog = null
        retryCallback = null
        // Don't clear lastBadState on dismiss - we might need it for retry
    }

    /**
     * Checks if a dialog is currently showing.
     */
    fun isShowing(): Boolean {
        return currentDialog?.isShowing == true
    }

    /**
     * Shows dialog for no network connection state.
     */
    private fun showNoConnectionDialog(context: Context) {
        // Don't recreate if already showing
        if (currentDialog?.isShowing == true) {
            return
        }

        currentDialog?.dismiss()

        // Use standard AlertDialog instead of MaterialAlertDialogBuilder to avoid theme dependency issues
        currentDialog = AlertDialog.Builder(context).apply {
            setTitle("No Network Connection")
            setMessage(
                "Your device is not connected to any network.\n\n" +
                        "Please check your WiFi or mobile data settings and try again."
            )
            setCancelable(false)  // User must take action

            setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                performRetry(context)
            }

            setNegativeButton("Settings") { dialog, _ ->
                dialog.dismiss()
                openNetworkSettings(context)
            }

            setNeutralButton("Close App") { dialog, _ ->
                dialog.dismiss()
                closeApp(context)
            }
        }.show()
    }

    /**
     * Shows dialog for network without internet access state.
     */
    private fun showNoInternetDialog(context: Context) {
        // Don't recreate if already showing
        if (currentDialog?.isShowing == true) {
            return
        }

        currentDialog?.dismiss()

        // Use standard AlertDialog instead of MaterialAlertDialogBuilder to avoid theme dependency issues
        currentDialog = AlertDialog.Builder(context).apply {
            setTitle("No Internet Access")
            setMessage(
                "Your device is connected to a network but cannot reach the internet.\n\n" +
                        "This may be due to:\n" +
                        "• A captive portal (hotel, airport, café WiFi)\n" +
                        "• Network restrictions or firewall\n" +
                        "• Service outage\n\n" +
                        "Please check your network settings or try again later."
            )
            setCancelable(false)

            setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                performRetry(context)
            }

            setNegativeButton("Settings") { dialog, _ ->
                dialog.dismiss()
                openNetworkSettings(context)
            }

            setNeutralButton("Close App") { dialog, _ ->
                dialog.dismiss()
                closeApp(context)
            }
        }.show()
    }

    /**
     * Performs the retry action with proper network state checking.
     * After refreshing network, checks if still disconnected and shows dialog again.
     *
     * @param context Context for network operations
     */
    private fun performRetry(context: Context) {
        // Cancel any ongoing retry operation
        retryJob?.cancel()

        retryJob = dialogScope.launch {
            Log.i(TAG, "Performing retry...")

            // First, invoke the callback if provided
            retryCallback?.invoke()

            // Default retry: force refresh connectivity check
            try {
                NetworkConnectivityChecker.forceRefresh(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error performing retry: ${e.message}")
            }

            // Wait for network state to update
            delay(RETRY_CHECK_DELAY)

            // Check the current network state
            val currentState = NetworkConnectivityChecker.networkState.first()
            Log.i(TAG, "Network state after retry: $currentState")

            when (currentState) {
                is NetworkState.Connected -> {
                    // Network is back, dismiss the dialog
                    Log.i(TAG, "Network restored, dismissing dialog")
                    dismiss()
                    lastBadState = null
                }

                is NetworkState.NotConnected,
                is NetworkState.ConnectedNoInternet -> {
                    // Still no network, show the dialog again if needed
                    Log.i(TAG, "Network still unavailable, showing dialog again")
                    // Store the current bad state
                    lastBadState = currentState
                    // Show the appropriate dialog
                    showIfNeeded(context, currentState, null)
                }

                is NetworkState.Unknown -> {
                    // Unknown state, try showing the last known bad state if available
                    Log.i(TAG, "Network state unknown after retry")
                    lastBadState?.let { showIfNeeded(context, it, null) }
                }
            }
        }
    }

    /**
     * Opens the network settings screen.
     */
    private fun openNetworkSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open settings: ${e2.message}")
            }
        }
    }

    /**
     * Closes the application.
     */
    private fun closeApp(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
            Log.e(TAG, "Error closing app: ${e.message}")
        }
    }
}