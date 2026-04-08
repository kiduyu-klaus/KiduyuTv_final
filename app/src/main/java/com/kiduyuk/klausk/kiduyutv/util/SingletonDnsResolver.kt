package com.kiduyuk.klausk.kiduyutv.util

import android.util.Log
import okhttp3.Dns
import okhttp3.DnsOverHttps
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.URL

/**
 * Singleton DNS over HTTPS resolver using Cloudflare's 1.1.1.1 service.
 * This single instance is shared across all OkHttpClient instances in the app.
 * 
 * Benefits of Singleton Pattern:
 * - Single HTTPS connection pool for DNS queries
 * - Reduced memory footprint
 * - Consistent DNS resolution across the app
 * - Lazy initialization on first use
 */
object SingletonDnsResolver {
    
    private const val TAG = "SingletonDnsResolver"
    
    // Cloudflare DNS over HTTPS endpoint
    private const val CLOUDFLARE_DOH_URL = "https://1.1.1.1/dns-query"
    
    // Bootstrap DNS servers for initial resolution of the DoH endpoint itself
    private val BOOTSTRAP_DNS_HOSTS = listOf(
        "1.1.1.1",
        "1.0.0.1",
        "2606:4700:4700::1111",
        "2606:4700:4700::1001"
    )
    
    // The actual Dns resolver to be used by OkHttpClients
    private val dnsResolver: Dns by lazy {
        createDnsOverHttpsResolver()
    }
    
    /**
     * Returns the singleton Dns resolver instance.
     * Thread-safe lazy initialization ensures single instance.
     */
    fun getDns(): Dns = dnsResolver
    
    /**
     * Creates the DnsOverHttps resolver with Cloudflare endpoint.
     */
    private fun createDnsOverHttpsResolver(): Dns {
        Log.d(TAG, "Initializing Cloudflare DNS over HTTPS resolver")
        
        // Bootstrap client - uses system DNS only for the initial DoH server lookup
        val bootstrapClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .build()
        
        // Resolve bootstrap addresses
        val bootstrapAddresses = BOOTSTRAP_DNS_HOSTS.map { host ->
            try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve bootstrap DNS: $host")
                null
            }
        }.filterNotNull()
        
        Log.d(TAG, "Bootstrap DNS resolved: ${bootstrapAddresses.size} addresses")
        
        // Build DnsOverHttps resolver
        val dnsOverHttps = DnsOverHttps.Builder(bootstrapClient)
            .url(URL(CLOUDFLARE_DOH_URL))
            .bootstrapDnsHosts(bootstrapAddresses)
            .build()
        
        Log.d(TAG, "Cloudflare DoH resolver initialized successfully")
        
        return dnsOverHttps
    }
    
    /**
     * Returns the DNS provider name for logging purposes.
     */
    fun getProviderName(): String = "Cloudflare (1.1.1.1) via DNS over HTTPS"
    
    /**
     * Tests DNS resolution for a given hostname.
     * Useful for debugging and verification.
     */
    fun testResolution(hostname: String): List<InetAddress> {
        Log.d(TAG, "Testing DNS resolution for: $hostname")
        val result = dnsResolver.lookup(hostname)
        Log.d(TAG, "Resolved $hostname -> ${result.joinToString()}")
        return result
    }
}
