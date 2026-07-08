package com.kiduyuk.klausk.kiduyutv.network

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.network.AndroidApp.setCurrentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.net.UnknownHostException

/**
 * Hardened service that monitors network connectivity and behaviorally detects ad-blocking DNS.
 */
object NetworkConnectivityChecker {

    private const val TAG = "NetworkConnectivityChecker"

    private const val INITIAL_CHECK_DELAY = 1000L
    private const val PERIODIC_CHECK_INTERVAL = 5000L
    private const val REACHABILITY_TIMEOUT = 3000L

    // ==================== THREAD-SAFE CACHE CONTROL ====================
    private val dnsCacheMutex = Mutex()
    private var lastDnsCheckTime = 0L
    private var cachedIsDnsBlocking = false
    private const val DNS_CHECK_CACHE_DURATION = 4 * 60 * 60 * 1000L // 4 hours

    // Tracks if the dialog has already been shown for the current ad-blocking state transition
    @Volatile
    private var isDialogShowingForCurrentBlockState = false

    // Multi-host resilient, lightweight 204 connectivity endpoints
    private val CONNECTIVITY_ENDPOINTS = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
        "https://example.com"
    )

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Unknown)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _networkDiagnostics = MutableStateFlow(NetworkDiagnostics())
    val networkDiagnostics: StateFlow<NetworkDiagnostics> = _networkDiagnostics.asStateFlow()

    @Volatile
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            performReachabilityCheck()
            handler.postDelayed(this, PERIODIC_CHECK_INTERVAL)
        }
    }

    data class NetworkDiagnostics(
        val isUsingCustomDns: Boolean = false, // True if behavioral ad-blocking is active
        val dnsServers: List<String> = emptyList(),
        val isVpnActive: Boolean = false,
        val vpnInterfaceName: String? = null,
        val isBehindProxy: Boolean = false,
        val proxyHost: String? = null,
        val networkType: String = "Unknown",
        val isMetered: Boolean = false,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    fun getNetworkDiagnostics(): NetworkDiagnostics = _networkDiagnostics.value

    // ==================== CACHE INVALIDATION & LIFECYCLE HOOKS ====================
    fun invalidateDnsCache() {
        Log.i(TAG, "Network layout modified. Resetting cache layers safely.")
        scope.launch {
            dnsCacheMutex.withLock {
                lastDnsCheckTime = 0L
            }
        }
        isDialogShowingForCurrentBlockState = false
    }

    /**
     * Resets the dialog tracking state. Call this from your Dialog's onDismissListener
     * to allow the check loop to prompt the user again later if they remain blocked.
     */
    fun onDialogDismissed() {
        isDialogShowingForCurrentBlockState = false
    }

    fun startMonitoring(context: Context) {
        if (isMonitoring) return

        isMonitoring = true
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                invalidateDnsCache()
                scope.launch {
                    val state = checkNetworkAndInternet(context)
                    updateState(state)
                    performNetworkDiagnostics()
                }
            }

            override fun onLost(network: Network) {
                updateState(NetworkState.NotConnected)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                invalidateDnsCache()
                scope.launch {
                    val state = checkNetworkAndInternet(context)
                    updateState(state)
                    performNetworkDiagnostics()
                }
            }

            override fun onUnavailable() {
                updateState(NetworkState.NotConnected)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                invalidateDnsCache()
                scope.launch {
                    performNetworkDiagnostics()
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind NetworkCallback: ${e.message}")
        }

        updateState(checkNetworkSync(context))
        handler.postDelayed(periodicCheckRunnable, INITIAL_CHECK_DELAY)
    }

    private fun checkNetworkSync(context: Context): NetworkState {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                when {
                    network == null || capabilities == null -> NetworkState.NotConnected
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.ConnectedNoInternet
                    else -> NetworkState.Connected
                }
            } else {
                // Fully compatible fallback interface logic for pre-23 operating environments
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                when {
                    activeNetworkInfo == null || !activeNetworkInfo.isConnected -> NetworkState.NotConnected
                    else -> NetworkState.Connected
                }
            }
        } catch (e: Exception) {
            NetworkState.Unknown
        }
    }

    fun stopMonitoring(context: Context) {
        if (!isMonitoring) return
        isMonitoring = false

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
        handler.removeCallbacks(periodicCheckRunnable)
    }

    fun forceRefresh(context: Context) {
        invalidateDnsCache() // Resolves manual "Recheck" loop traps completely
        scope.launch {
            val state = withContext(Dispatchers.IO) { checkNetworkAndInternet(context) }
            updateState(state)
            performNetworkDiagnostics()
        }
    }

    private suspend fun checkNetworkAndInternet(context: Context): NetworkState {
        return withContext(Dispatchers.IO) {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)

                    if (network == null || capabilities == null) return@withContext NetworkState.NotConnected
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@withContext NetworkState.ConnectedNoInternet
                } else {
                    @Suppress("DEPRECATION")
                    val activeNetworkInfo = connectivityManager.activeNetworkInfo
                    @Suppress("DEPRECATION")
                    if (activeNetworkInfo == null || !activeNetworkInfo.isConnected) return@withContext NetworkState.NotConnected
                }

                if (testInternetReachability()) NetworkState.Connected else NetworkState.ConnectedNoInternet
            } catch (e: Exception) {
                NetworkState.Unknown
            }
        }
    }

    // ==================== HARDENED HTTP REACHABILITY CHECK ====================
    private suspend fun testInternetReachability(): Boolean {
        return withContext(Dispatchers.IO) {
            for (endpoint in CONNECTIVITY_ENDPOINTS) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(endpoint)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = REACHABILITY_TIMEOUT.toInt()
                    connection.readTimeout = REACHABILITY_TIMEOUT.toInt()
                    connection.useCaches = false
                    
                    val responseCode = connection.responseCode
                    // Matches standard 200 OK, 204 No Content, etc.
                    if (responseCode in 100..599) {
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "HTTP reachability dropped on checkpoint: $endpoint: ${e.message}")
                } finally {
                    connection?.disconnect()
                }
            }
            false
        }
    }

    // ==================== BEHAVIORAL AD-BLOCKING DETECTOR ====================
    private suspend fun isDnsActivelyBlockingAds(): Boolean {
        val cachedResult = dnsCacheMutex.withLock {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDnsCheckTime < DNS_CHECK_CACHE_DURATION) {
                return@withLock cachedIsDnsBlocking
            }
            null
        }
        if (cachedResult != null) return cachedResult

        return withContext(Dispatchers.IO) {
            if (!testInternetReachability()) {
                return@withContext dnsCacheMutex.withLock { cachedIsDnsBlocking }
            }

            val canaryDomains = listOf(
                "pagead2.googlesyndication.com",
                "googleads.g.doubleclick.net",
                "adservice.google.com"
            )

            var blockedCount = 0

            for (domain in canaryDomains) {
                try {
                    // Reverted back safely to public multi-address verification hooks
                    val addresses = InetAddress.getAllByName(domain).toList()

                    if (addresses.isEmpty()) {
                        blockedCount++
                        continue
                    }

                    for (address in addresses) {
                        val ip = address.hostAddress ?: ""
                        
                        val isNullRouted = ip == "0.0.0.0" || ip == "127.0.0.1" || 
                                           ip == "::" || ip == "::1" || ip.isEmpty()

                        // Leverages native framework flags to comprehensively check IPv4/IPv6 loopbacks & site links
                        val isSinkholeIp = address.isLoopbackAddress || 
                                           address.isSiteLocalAddress || 
                                           address.isLinkLocalAddress || 
                                           address.isAnyLocalAddress ||
                                           ip.startsWith("192.168.") ||
                                           ip.startsWith("10.") ||
                                           (ip.startsWith("172.") && try {
                                               val secondOctet = ip.split(".")[1].toInt()
                                               secondOctet in 16..31
                                           } catch (_: Exception) { false })

                        if (isNullRouted || isSinkholeIp) {
                            blockedCount++
                            break
                        }
                    }
                } catch (e: UnknownHostException) {
                    blockedCount++ // Captures standard localized NXDOMAIN adjustments cleanly
                } catch (_: Exception) {}
            }

            val isBlockingDetected = blockedCount >= 2
            
            dnsCacheMutex.withLock {
                cachedIsDnsBlocking = isBlockingDetected
                lastDnsCheckTime = System.currentTimeMillis()
            }

            isBlockingDetected
        }
    }

    private fun getActiveDnsServers(): List<String> {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork ?: return emptyList()
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return emptyList()
                linkProperties.dnsServers.mapNotNull { it.hostAddress }
            } else {
                emptyList() // Deprecated system-wide fallback hooks omitted for security alignment
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving interface DNS targets: ${e.message}")
            emptyList()
        }
    }

    private fun detectVpnConnection(): Pair<Boolean, String?> {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var vpnActive = false
            var vpnInterface: String? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        vpnActive = true
                        val linkProperties = connectivityManager.getLinkProperties(network)
                        vpnInterface = linkProperties?.interfaceName
                        break
                    }
                }
            } else {
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
            }
            Pair(vpnActive, vpnInterface)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    private fun detectProxySettings(): Pair<Boolean, String?> {
        return try {
            val context = AndroidApp.instance
            @Suppress("DEPRECATION")
            val proxyHost = android.net.Proxy.getHost(context)
            @Suppress("DEPRECATION")
            val proxyPort = android.net.Proxy.getPort(context)

            if (!proxyHost.isNullOrEmpty() && proxyPort != null && proxyPort > 0) {
                Pair(true, "$proxyHost:$proxyPort")
            } else {
                Pair(false, null)
            }
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    private fun getNetworkType(): String {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                when {
                    activeNetworkInfo == null -> "Unknown"
                    activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI -> "WiFi"
                    activeNetworkInfo.type == ConnectivityManager.TYPE_MOBILE -> "Cellular"
                    activeNetworkInfo.type == ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                    else -> "Other"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun isMeteredNetwork(): Boolean {
        return try {
            val connectivityManager = AndroidApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.isActiveNetworkMetered
        } catch (e: Exception) {
            false
        }
    }

    // ==================== WORKFLOW EXECUTIVE EXECUTION ====================
    private fun performNetworkDiagnostics() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val dnsServers = getActiveDnsServers()
                val (isVpn, vpnInterface) = detectVpnConnection()
                val (isProxy, proxyInfo) = detectProxySettings()
                val networkType = getNetworkType()
                val isMetered = isMeteredNetwork()

                val isUsingAdBlockingDns = isDnsActivelyBlockingAds()

                val diagnostics = NetworkDiagnostics(
                    isUsingCustomDns = isUsingAdBlockingDns,
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

                // EDGE-TRIGGERED CONTROL: Evaluates transitioning behavior to prevent recurring loops
                if (isUsingAdBlockingDns) {
                    if (!isDialogShowingForCurrentBlockState) {
                        withContext(Dispatchers.Main) {
                            val activityContext = AndroidApp.getCurrentActivity()
                            
                            // Context Verification Layer (Bypasses WindowManager Token Exceptions)
                            if (activityContext != null && !activityContext.isFinishing && !activityContext.isDestroyed) {
                                try {
                                    Log.i(TAG, "Ad-blocking DNS detected behaviorally. Displaying UI Dialog Frame safely.")
                                    isDialogShowingForCurrentBlockState = true
                                    NetworkStateDialog.showAdBlockingDnsDialog(activityContext)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed initializing interface alert anchor window: ${e.message}")
                                    isDialogShowingForCurrentBlockState = false
                                }
                            }
                        }
                    }
                } else {
                    isDialogShowingForCurrentBlockState = false
                }
            }
        }
    }

    private fun updateState(state: NetworkState) {
        val currentState = _networkState.value
        if (currentState != state) {
            Log.i(TAG, "State changed: $currentState → $state")
            _networkState.value = state
        }
    }

    private fun performReachabilityCheck() {
        if (!isMonitoring) return
        scope.launch {
            val context = AndroidApp.instance
            val state = checkNetworkAndInternet(context)
            updateState(state)
            performNetworkDiagnostics()
        }
    }
}

// ==================== APPLICATION REFERENCE & TRACKING LAYOUT ====================
/**
 * Reference to the Application class for singleton access and activity tracking.
 * Ensure your base Application class updates [setCurrentActivity] within an 
 * ActivityLifecycleCallbacks implementation.
 */
object AndroidApp {
    lateinit var instance: Application

    // Track the current foreground Activity safely across threads for dialog display
    @Volatile
    private var currentActivity: android.app.Activity? = null

    /**
     * Gets the current foreground Activity context.
     * Returns null if no Activity is currently active or in focus.
     */
    fun getCurrentActivity(): android.app.Activity? = currentActivity

    /**
     * Sets the current foreground Activity.
     * Should be bound inside your Application's onActivityResumed / onActivityPaused hooks.
     */
    fun setCurrentActivity(activity: android.app.Activity?) {
        currentActivity = activity
    }
}
