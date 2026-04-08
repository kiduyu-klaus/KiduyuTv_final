package com.kiduyuk.klausk.kiduyutv.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Dialog manager for displaying network connectivity status.
 * Shows appropriate message and actions based on the current state.
 */
object NetworkStateDialog {

    private const val TAG = "NetworkStateDialog"

    // Reference to current dialog
    private var currentDialog: AlertDialog? = null

    // Callback for retry action
    private var retryCallback: (() -> Unit)? = null

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
                showNoConnectionDialog(context)
            }

            is NetworkState.ConnectedNoInternet -> {
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
                performRetry()
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
                performRetry()
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
     * Performs the retry action.
     */
    private fun performRetry() {
        retryCallback?.invoke() ?: run {
            // Default retry: force refresh connectivity check
            try {
                NetworkConnectivityChecker.forceRefresh(AndroidApp.instance)
            } catch (e: Exception) {
                Log.e(TAG, "Error performing retry: ${e.message}")
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
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Exception) {
            Log.e(TAG, "Error closing app: ${e.message}")
        }
    }
}
