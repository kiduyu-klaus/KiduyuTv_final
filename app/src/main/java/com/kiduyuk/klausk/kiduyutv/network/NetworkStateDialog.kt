package com.kiduyuk.klausk.kiduyutv.network


import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
     * Shows a dialog when custom DNS or VPN is detected.
     * Provides options to exit the app or open relevant settings.
     * 
     * @param context Context for creating dialog
     * @param diagnostics Network diagnostics containing DNS/VPN info
     */
    fun showDnsVpnDetectedDialog(
        context: Context,
        diagnostics: NetworkConnectivityChecker.NetworkDiagnostics
    ) {
        // Don't recreate if already showing
        if (currentDialog?.isShowing == true) {
            return
        }
        
        currentDialog?.dismiss()
        
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)
        
        // Build message based on what's detected
        val issues = mutableListOf<String>()
        
        if (diagnostics.isVpnActive) {
            diagnostics.vpnInterfaceName?.let {
                issues.add("• VPN is active ($it)")
            } ?: issues.add("• VPN is active")
        }
        
        if (diagnostics.isUsingCustomDns) {
            issues.add("• Custom DNS detected: ${diagnostics.dnsServers.joinToString(", ")}")
        }
        
        if (diagnostics.isBehindProxy) {
            issues.add("• Proxy detected: ${diagnostics.proxyHost}")
        }
        
        // Set title and message
        val title = when {
            diagnostics.isVpnActive && diagnostics.isUsingCustomDns -> "VPN & Custom DNS Detected"
            diagnostics.isVpnActive -> "VPN Detected"
            diagnostics.isUsingCustomDns -> "Custom DNS Detected"
            diagnostics.isBehindProxy -> "Proxy Detected"
            else -> "Network Issue Detected"
        }
        
        builder.setTitle(title)
        builder.setMessage(
            "We detected the following network configuration that may affect streaming:\n\n" +
            issues.joinToString("\n") +
            "\n\nThis may cause issues with video streaming services.\n\n" +
            "Would you like to:\n" +
            "• Disable VPN or change DNS settings\n" +
            "• Exit the app\n" +
            "• Continue anyway"
        )
        
        // Add buttons based on what's detected
        val buttonActions = mutableListOf<Pair<String, () -> Unit>>()
        
        // VPN settings option
        if (diagnostics.isVpnActive) {
            buttonActions.add(Pair("Open VPN Settings") {
                openVpnSettings(context)
            })
        }
        
        // DNS settings option
        if (diagnostics.isUsingCustomDns) {
            buttonActions.add(Pair("Open DNS Settings") {
                openDnsSettings(context)
            })
        }
        
        // Add "Continue Anyway" button
        buttonActions.add(Pair("Continue Anyway") {
            dismiss()
        })
        
        // Add "Exit App" button
        buttonActions.add(Pair("Exit App") {
            closeApp(context)
        })
        
        // Set buttons based on count (AlertDialog only supports 3 buttons max)
        // Prioritize: Settings options > Continue > Exit
        when (buttonActions.size) {
            4 -> {
                // VPN + DNS + Proxy detected - use only positive and negative buttons
                builder.setPositiveButton(buttonActions[0].first) { _, _ -> buttonActions[0].second() }
                builder.setNegativeButton(buttonActions[2].first) { _, _ -> buttonActions[2].second() }
                // Add custom buttons via custom view or use neutral for third option
                builder.setNeutralButton(buttonActions[3].first) { _, _ -> buttonActions[3].second() }
            }
            3 -> {
                builder.setPositiveButton(buttonActions[0].first) { _, _ -> buttonActions[0].second() }
                builder.setNegativeButton(buttonActions[1].first) { _, _ -> buttonActions[1].second() }
                builder.setNeutralButton(buttonActions[2].first) { _, _ -> buttonActions[2].second() }
            }
            2 -> {
                builder.setPositiveButton(buttonActions[0].first) { _, _ -> buttonActions[0].second() }
                builder.setNegativeButton(buttonActions[1].first) { _, _ -> buttonActions[1].second() }
            }
            else -> {
                builder.setPositiveButton("Exit App") { _, _ -> closeApp(context) }
                builder.setNegativeButton("Continue Anyway") { _, _ -> dismiss() }
            }
        }
        
        currentDialog = builder.show()
    }
    
    /**
     * Opens VPN settings.
     */
    private fun openVpnSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VPN_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general VPN settings
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open VPN settings: ${e2.message}")
            }
        }
        dismiss()
    }
    
    /**
     * Opens DNS settings (Wireless settings as fallback).
     */
    private fun openDnsSettings(context: Context) {
        try {
            // Try to open private DNS settings on Android 9+
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open DNS settings: ${e2.message}")
            }
        }
        dismiss()
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
     * Shows a custom dialog when an ad-blocking DNS service is detected.
     * Matches the FreeReels-style layout: dark rounded card, centered lock+warning
     * emoji, body text about ad-blocking DNS, single full-width "Recheck" button.
     * Non-cancelable. Re-runs the DNS detection on Recheck.
     *
     * @param context Context for dialog creation (use Activity for theming)
     */
    fun showAdBlockingDnsDialog(context: Context) {
        // Don't recreate if already showing
        if (currentDialog?.isShowing == true) {
            return
        }
        currentDialog?.dismiss()

        // Convert dp to pixels
        fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        val rootBg = Color.parseColor("#2A2A2A")

        // Root vertical layout
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(rootBg)
            setPadding(dp(28), dp(32), dp(28), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Title
        val title = TextView(context).apply {
            text = "Unable to Use KiduyuTV"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(title)

        // Lock + warning icon (emoji)
        val icon = TextView(context).apply {
            text = "\uD83D\uDD12\u26A0\uFE0F"   // 🔒 + ⚠️
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            setPadding(0, dp(8), 0, dp(20))
        }
        root.addView(icon)

        // Body message
        val message = TextView(context).apply {
            text = "An ad-blocking DNS service (e.g., AdGuard DNS, NextDNS, Pi-hole) " +
                    "is enabled on your device and is preventing the app from working " +
                    "properly. Ads support our free content. Please disable the service " +
                    "and tap \u201CRecheck.\u201D"
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(0f, 1.2f)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(28))
        }
        root.addView(message)

        // Recheck button — full width, red background, white text, rounded
        val buttonColor = Color.parseColor("#E50914") // PrimaryRed
        val button = Button(context).apply {
            text = "Recheck"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(buttonColor)
                cornerRadius = dp(10).toFloat()
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(16), dp(18), dp(16), dp(18))
            stateListAnimator = null  // remove default elevation animation
            elevation = 0f
        }
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(button, buttonParams)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setCancelable(false)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        button.setOnClickListener {
            Log.i(TAG, "Recheck tapped — re-running DNS detection")
            dialog.dismiss()
            currentDialog = null
            try {
                NetworkConnectivityChecker.forceRefresh(context)
            } catch (e: Exception) {
                Log.w(TAG, "forceRefresh on recheck failed: ${e.message}")
            }
        }

        currentDialog = dialog
        dialog.show()
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

/**
 * Preview layout for the Ad-Blocking DNS warning dialog.
 * Replicates the visual design of [NetworkStateDialog.showAdBlockingDnsDialog].
 */
@Composable
fun AdBlockingDnsDialogContent(
    modifier: Modifier = Modifier,
    onRecheckClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF2A2A2A)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 32.dp, end = 28.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Unable to Use KiduyuTV",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Lock + warning icon (emoji)
            Text(
                text = "\uD83D\uDD12\u26A0\uFE0F", // 🔒 + ⚠️
                fontSize = 72.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            // Body message
            Text(
                text = "An ad-blocking DNS service (e.g., AdGuard DNS, NextDNS, Pi-hole) is enabled on your device and is preventing the app from working properly. Ads support our free content. Please disable the service and tap \u201CRecheck.\u201D",
                color = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 28.dp)
            )

            // Recheck button — full width, red background, white text, rounded
            Button(
                onClick = onRecheckClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFE50914) // PrimaryRed
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = "Recheck",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun AdBlockingDnsDialogPreview() {
    AdBlockingDnsDialogContent()
}
