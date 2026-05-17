package com.kiduyuk.klausk.kiduyutv.network

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kiduyuk.klausk.kiduyutv.network.NetworkStateDialog

/**
 * Composable that observes network connectivity state.
 * Shows dialog when network is unavailable or when VPN/custom DNS is detected.
 * 
 * Usage:
 * 
 * @Composable
 * fun MainScreen() {
 *     NetworkConnectivityObserver()
 *     
 *     // Your UI code here
 * }
 */
@Composable
fun NetworkConnectivityObserver(
    onNetworkStateChange: ((NetworkState) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Collect network state
    val networkState by NetworkConnectivityChecker.networkState.collectAsState()
    
    // Collect network diagnostics (includes DNS/VPN info)
    val networkDiagnostics by NetworkConnectivityChecker.networkDiagnostics.collectAsState()
    
    // Track if we've shown the DNS/VPN dialog to avoid re-showing it
    var hasShownDnsVpnDialog by remember { mutableStateOf(false) }
    
    // Start monitoring when composable enters composition
    LaunchedEffect(Unit) {
        NetworkConnectivityChecker.startMonitoring(context)
    }
    
    // Observe state changes and show/hide dialog
    LaunchedEffect(networkState) {
        onNetworkStateChange?.invoke(networkState)
        
        // Show dialog for non-connected states
        when (networkState) {
            is NetworkState.NotConnected -> {
                NetworkStateDialog.showIfNeeded(context, networkState)
            }
            is NetworkState.ConnectedNoInternet -> {
                NetworkStateDialog.showIfNeeded(context, networkState)
            }
            else -> {
                NetworkStateDialog.dismiss()
            }
        }
    }
    
    // Observe network diagnostics for DNS/VPN detection
    LaunchedEffect(networkDiagnostics) {
        val isCustomDns = networkDiagnostics.isUsingCustomDns
        val isVpnActive = networkDiagnostics.isVpnActive
        
        if ((isCustomDns || isVpnActive) && !hasShownDnsVpnDialog) {
            // Show the DNS/VPN warning dialog
            Log.i("NetworkConnectivityObserver", "DNS/VPN detected, showing warning dialog")
            NetworkStateDialog.showDnsVpnDetectedDialog(context, networkDiagnostics)
            hasShownDnsVpnDialog = true
        } else if (!isCustomDns && !isVpnActive) {
            // Reset the flag when DNS/VPN is no longer active
            hasShownDnsVpnDialog = false
        }
    }
    
    // Stop monitoring when leaving composition or lifecycle destroyed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_DESTROY -> {
                    NetworkConnectivityChecker.stopMonitoring(context)
                    NetworkStateDialog.dismiss()
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Hook to get current network state.
 * 
 * Usage:
 * 
 * @Composable
 * fun MyScreen() {
 *     val networkState = rememberNetworkState()
 *     
 *     if (!networkState.isConnected()) {
 *         // Show offline message
 *     }
 * }
 */
@Composable
fun rememberNetworkState(): NetworkState {
    return NetworkConnectivityChecker.networkState.collectAsState().value
}

