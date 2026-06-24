package com.kiduyuk.klausk.kiduyutv.network

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
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
import java.net.NetworkInterface

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
    
    // StateFlow to emit network diagnostic information
    private val _networkDiagnostics = MutableStateFlow(NetworkDiagnostics())
    val networkDiagnostics: StateFlow<NetworkDiagnostics> = _networkDiagnostics.asStateFlow()
    
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
     * Data class to hold network diagnostic information.
     */
    data class NetworkDiagnostics(
        val isUsingCustomDns: Boolean = false,
        val dnsServers: List<String> = emptyList(),
        val isVpnActive: Boolean = false,
        val vpnInterfaceName: String? = null,
        val isBehindProxy: Boolean = false,
        val proxyHost: String? = null,
        val networkType: String = "Unknown",
        val isMetered: Boolean = false,
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    /**
     * Returns the current network diagnostics information.
     */
    fun getNetworkDiagnostics(): NetworkDiagnostics = _networkDiagnostics.value
    
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
                    //Log.i(TAG, "Network has no INTERNET capability")
                    return@withContext NetworkState.ConnectedNoInternet
                }
                
                // Verify actual internet reachability
                val isReachable = testInternetReachability()
                
                if (isReachable) {
                    //Log.i(TAG, "Internet is reachable")
                    NetworkState.Connected
                } else {
                    //Log.i(TAG, "Network active but internet not reachable")
                    NetworkState.ConnectedNoInternet
                }
            } catch (e: Exception) {
                //Log.e(TAG, "Error checking connectivity: ${e.message}")
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
            //Log.i(TAG, "Host $host reachable: $reachable")
            reachable
        } catch (e: Exception) {
            Log.w(TAG, "Error checking host $host: ${e.message}")
            false
        }
    }
    
    /**
     * Detects custom DNS servers being used on the device.
     * Compares against known public DNS servers to determine if custom DNS is in use.
     */
    private fun detectCustomDnsServers(): Pair<Boolean, List<String>> {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
                
            val dnsServers = mutableListOf<String>()
            val knownPublicDns = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222")
            
            // Get active network link properties
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val activeNetwork = connectivityManager.activeNetwork
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                
                if (linkProperties != null) {
                    dnsServers.addAll(linkProperties.dnsServers.map { it.hostAddress })
                }
            } else {
                // Fallback for older Android versions using NetworkInterface
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val networkInterface = interfaces.nextElement()
                        val inetAddresses = networkInterface.inetAddresses
                        while (inetAddresses.hasMoreElements()) {
                            val address = inetAddresses.nextElement()
                            if (!address.isLoopbackAddress && address.hostAddress != null) {
                                // Check if this interface has DNS servers
                                try {
                                    val props = networkInterface.javaClass.getMethod("getLinkProperties")
                                        .invoke(networkInterface) as? LinkProperties
                                    props?.dnsServers?.forEach { dns ->
                                        if (!dnsServers.contains(dns.hostAddress)) {
                                            dnsServers.add(dns.hostAddress)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Fallback: check system DNS properties
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting network interfaces: ${e.message}")
                }
            }
            
            val isCustomDns = if (dnsServers.isNotEmpty()) {
                // Check if any DNS server is NOT a known public DNS
                dnsServers.any { dns -> 
                    knownPublicDns.none { it.equals(dns, ignoreCase = true) }
                }
            } else {
                false
            }
            
            //Log.i(TAG, "DNS servers detected: $dnsServers, Is custom: $isCustomDns")
            Pair(isCustomDns, dnsServers)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting DNS servers: ${e.message}")
            Pair(false, emptyList())
        }
    }
    
    /**
     * Detects if a VPN connection is active.
     * Checks for VPN interfaces in the network configuration.
     */
    private fun detectVpnConnection(): Pair<Boolean, String?> {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
                
            var vpnActive = false
            var vpnInterface: String? = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        vpnActive = true
                        // Try to get interface name
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val linkProperties = connectivityManager.getLinkProperties(network)
                            vpnInterface = linkProperties?.interfaceName
                        }
                        break
                    }
                }
            } else {
                // Fallback for older Android versions
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val networkInterface = interfaces.nextElement()
                        val name = networkInterface.name.lowercase()
                        if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("tap") ||
                            name.contains("vpn") || name.startsWith("wg")) {
                            vpnActive = true
                            vpnInterface = networkInterface.name
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking VPN interfaces: ${e.message}")
                }
            }
            
            //Log.i(TAG, "VPN active: $vpnActive, interface: $vpnInterface")
            Pair(vpnActive, vpnInterface)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting VPN: ${e.message}")
            Pair(false, null)
        }
    }
    
    /**
     * Detects if the device is behind a proxy server.
     */
    private fun detectProxySettings(): Pair<Boolean, String?> {
        return try {
            val context = AndroidApp.instance
            val proxyHost = android.net.Proxy.getHost(context)
            val proxyPort = android.net.Proxy.getPort(context)
            
            val isBehindProxy = !proxyHost.isNullOrEmpty() && proxyPort != null && proxyPort > 0
            
            if (isBehindProxy) {
                //Log.i(TAG, "Proxy detected: $proxyHost:$proxyPort")
                Pair(true, "$proxyHost:$proxyPort")
            } else {
                //Log.i(TAG, "No proxy detected")
                Pair(false, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting proxy: ${e.message}")
            Pair(false, null)
        }
    }
    
    /**
     * Gets the current network type (WiFi, Cellular, Ethernet, etc.).
     */
    private fun getNetworkType(): String {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
                
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            when {
                capabilities == null -> "Unknown"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Other"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Checks if the network is metered (metered connection like cellular).
     */
    private fun isMeteredNetwork(): Boolean {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
            connectivityManager.isActiveNetworkMetered
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Performs a comprehensive network diagnostic check.
     * Detects DNS servers, VPN, proxy, network type, and metered status.
     * Updates the networkDiagnostics StateFlow with the results.
     */
    private fun performNetworkDiagnostics() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val (isCustomDns, dnsServers) = detectCustomDnsServers()
                val (isVpn, vpnInterface) = detectVpnConnection()
                val (isProxy, proxyInfo) = detectProxySettings()
                val networkType = getNetworkType()
                val isMetered = isMeteredNetwork()
                
                val diagnostics = NetworkDiagnostics(
                    isUsingCustomDns = isCustomDns,
                    dnsServers = dnsServers,
                    isVpnActive = isVpn,
                    vpnInterfaceName = vpnInterface,
                    isBehindProxy = isProxy,
                    proxyHost = proxyInfo,
                    networkType = networkType,
                    isMetered = isMetered,
                    lastUpdated = System.currentTimeMillis()
                )
                
                _networkDiagnostics.value = diagnostics
                
//                Log.i(TAG, "Network Diagnostics:")
//                Log.i(TAG, "  - Network Type: $networkType")
//                Log.i(TAG, "  - Custom DNS: $isCustomDns (Servers: $dnsServers)")
//                Log.i(TAG, "  - VPN Active: $isVpn (Interface: $vpnInterface)")
//                Log.i(TAG, "  - Proxy: $isProxy (Info: $proxyInfo)")
//                Log.i(TAG, "  - Metered: $isMetered")
                
                // Check if DNS server is detected and show the FreeReels-style
                // "Unable to Use KiduyuTV" dialog. DNS check only — VPN/proxy
                // are intentionally ignored per current product requirements.
                if (isCustomDns) {
                    withContext(Dispatchers.Main) {
                        // Try to get Activity context for proper dialog display
                        // Fall back to Application context if no Activity is available
                        val activityContext = AndroidApp.getCurrentActivity() ?: AndroidApp.instance
                        
                        // Only show dialog if we have a valid context
                        if (activityContext != null) {
                            try {
                                Log.i(TAG, "Ad-blocking DNS detected, showing DNS dialog")
                                NetworkStateDialog.showAdBlockingDnsDialog(activityContext)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error showing DNS dialog: ${e.message}")
                            }
                        } else {
                            Log.i(TAG, "No Activity context available, skipping DNS dialog")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Check if app is in foreground
     */
    private fun isAppInForeground(): Boolean {
        return try {
            val activityManager = AndroidApp.instance.getSystemService(Context.ACTIVITY_SERVICE) 
                as android.app.ActivityManager
            val processes = activityManager.runningAppProcesses
            val appProcess = processes?.find { 
                it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE ||
                it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
            appProcess != null
        } catch (e: Exception) {
            // Default to true if we can't determine, let dialog handle the error
            true
        }
    }
    
    /**
     * Updates the current state and emits to observers if changed.
     */
    private fun updateState(state: NetworkState) {
        val currentState = _networkState.value
        if (currentState != state) {
            //Log.i(TAG, "State changed: $currentState → $state")
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
            
            // Also perform network diagnostics to detect DNS and VPN
            performNetworkDiagnostics()
        }
    }
}

/**
 * Reference to the Application class for singleton access.
 * Replace AndroidApp with your actual Application class name.
 */
object AndroidApp {
    lateinit var instance: Application
    
    // Track the current foreground Activity for dialog display
    @Volatile
    private var currentActivity: android.app.Activity? = null
    
    /**
     * Gets the current foreground Activity context.
     * Returns null if no Activity is available.
     */
    fun getCurrentActivity(): android.app.Activity? = currentActivity
    
    /**
     * Sets the current foreground Activity.
     * Should be called when an Activity resumes.
     */
    fun setCurrentActivity(activity: android.app.Activity?) {
        currentActivity = activity
    }
}