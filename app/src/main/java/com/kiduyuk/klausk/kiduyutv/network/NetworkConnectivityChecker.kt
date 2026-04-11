package com.kiduyuk.klausk.kiduyutv.network

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Singleton service that continuously monitors network connectivity.
 * Performs both passive monitoring (via BroadcastReceiver) and
 * active reachability tests to ensure real internet access.
 */
object NetworkConnectivityChecker {
    
    private const val TAG = "NetworkConnectivityChecker"
    
    // Timing constants
    private const val INITIAL_CHECK_DELAY = 1000L  // 1 second delay before first check
    private const val PERIODIC_CHECK_INTERVAL = 5000L  // Check every 5 seconds
    private const val REACHABILITY_TIMEOUT = 3000L  // 3 second timeout for reachability test
    
    // DNS servers to test reachability
    private val TEST_HOSTS = listOf(
        "1.1.1.1",      // Cloudflare DNS
        "8.8.8.8",      // Google DNS
        "www.google.com"
    )
    
    // StateFlow to emit connectivity state changes
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Unknown)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    // Whether continuous monitoring is active
    @Volatile
    private var isMonitoring = false
    
    // Handler for periodic checks
    private val handler = Handler(Looper.getMainLooper())
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            performReachabilityCheck()
            handler.postDelayed(this, PERIODIC_CHECK_INTERVAL)
        }
    }
    
    /**
     * Starts continuous network connectivity monitoring.
     * Call this in your Application class or MainActivity.
     * 
     * @param context Application context for registering receiver
     */
    fun startMonitoring(context: Context) {
        if (isMonitoring) {
            Log.i(TAG, "Monitoring already active")
            return
        }
        
        isMonitoring = true
        Log.i(TAG, "Starting network connectivity monitoring")
        
        // Register for connectivity change broadcasts
        val intentFilter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        
        try {
            context.registerReceiver(
                connectivityReceiver,
                intentFilter
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
        }
        
        // Perform immediate synchronous check for initial state
        val initialState = checkNetworkSync(context)
        updateState(initialState)
        
        // Start periodic reachability checks
        handler.postDelayed(
            periodicCheckRunnable,
            INITIAL_CHECK_DELAY
        )
    }

    /**
     * Synchronous network interface check (no internet reachability test).
     * Used for immediate initial state detection.
     */
    private fun checkNetworkSync(context: Context): NetworkState {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
            
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (network == null || networkCapabilities == null) {
                NetworkState.NotConnected
            } else if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                NetworkState.ConnectedNoInternet
            } else {
                // Assume connected until reachability test completes
                NetworkState.Connected
            }
        } catch (e: Exception) {
            NetworkState.Unknown
        }
    }
    
    /**
     * Stops network connectivity monitoring.
     * Call this when your app is being destroyed.
     * 
     * @param context Application context for unregistering receiver
     */
    fun stopMonitoring(context: Context) {
        if (!isMonitoring) return
        
        isMonitoring = false
        Log.i(TAG, "Stopping network connectivity monitoring")
        
        // Unregister receiver
        try {
            context.unregisterReceiver(connectivityReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver not registered: ${e.message}")
        }
        
        // Stop periodic checks
        handler.removeCallbacks(periodicCheckRunnable)
    }
    
    /**
     * Performs a one-time connectivity check.
     * Returns the current network state.
     * 
     * @param context Context for connectivity manager access
     * @return NetworkState representing current connectivity
     */
    suspend fun checkConnectivity(context: Context): NetworkState {
        return withContext(Dispatchers.IO) {
            checkNetworkAndInternet(context)
        }
    }
    
    /**
     * Forces a refresh of the connectivity state.
     * Useful for manual retry operations.
     * 
     * @param context Context for connectivity manager access
     */
    fun forceRefresh(context: Context) {
        scope.launch {
            val state = checkConnectivity(context)
            updateState(state)
        }
    }
    
    /**
     * BroadcastReceiver for passive connectivity monitoring.
     */
    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) return
            
            Log.i(TAG, "Connectivity change detected")
            
            // Perform immediate reachability check
            scope.launch {
                val state = checkNetworkAndInternet(context ?: return@launch)
                updateState(state)
            }
        }
    }
    
    /**
     * Performs a comprehensive connectivity check.
     * Combines network interface check with actual reachability test.
     */
    private suspend fun checkNetworkAndInternet(context: Context): NetworkState {
        return withContext(Dispatchers.IO) {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                    as ConnectivityManager
                
                // Check if any network is active
                val network = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                
                if (network == null || networkCapabilities == null) {
                    Log.i(TAG, "No active network")
                    return@withContext NetworkState.NotConnected
                }
                
                // Check for internet capability
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                
                if (!hasInternet) {
                    Log.i(TAG, "Network has no INTERNET capability")
                    return@withContext NetworkState.ConnectedNoInternet
                }
                
                // Verify actual internet reachability
                val isReachable = testInternetReachability()
                
                if (isReachable) {
                    Log.i(TAG, "Internet is reachable")
                    NetworkState.Connected
                } else {
                    Log.i(TAG, "Network active but internet not reachable")
                    NetworkState.ConnectedNoInternet
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connectivity: ${e.message}")
                NetworkState.Unknown
            }
        }
    }
    
    /**
     * Tests actual internet reachability by attempting connections.
     * Uses multiple hosts to avoid false negatives from single-host issues.
     */
    private suspend fun testInternetReachability(): Boolean {
        return withContext(Dispatchers.IO) {
            for (host in TEST_HOSTS) {
                try {
                    if (canConnectToHost(host)) {
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reachability test failed for $host: ${e.message}")
                }
            }
            false
        }
    }
    
    /**
     * Attempts to reach a host using InetAddress.isReachable.
     * This is a simple but effective way to test connectivity.
     */
    private fun canConnectToHost(host: String): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(REACHABILITY_TIMEOUT.toInt())
            Log.i(TAG, "Host $host reachable: $reachable")
            reachable
        } catch (e: Exception) {
            Log.w(TAG, "Error checking host $host: ${e.message}")
            false
        }
    }
    
    /**
     * Updates the current state and emits to observers if changed.
     */
    private fun updateState(state: NetworkState) {
        val currentState = _networkState.value
        if (currentState != state) {
            Log.i(TAG, "State changed: $currentState → $state")
            _networkState.value = state
        }
    }
    
    /**
     * Performs a reachability check and updates state.
     */
    private fun performReachabilityCheck() {
        if (!isMonitoring) return
        
        scope.launch {
            val context = AndroidApp.instance
            val state = checkNetworkAndInternet(context)
            updateState(state)
        }
    }
}

/**
 * Reference to the Application class for singleton access.
 * Replace AndroidApp with your actual Application class name.
 */
object AndroidApp {
    lateinit var instance: Application
}
