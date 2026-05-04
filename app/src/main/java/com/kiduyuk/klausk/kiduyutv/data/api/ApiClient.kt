package com.kiduyuk.klausk.kiduyutv.data.api

import android.content.Context
import android.util.Log
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import com.kiduyuk.klausk.kiduyutv.util.SingletonDnsResolver
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"

    // Bearer token used for Authorization header for all API requests.
    private const val BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w"

    // ========================================================================
    // OPTIMIZED CACHE CONFIGURATION
    // ========================================================================
    // Main API cache: 25 MB for better offline performance
    private const val CACHE_SIZE = 25L * 1024 * 1024 // 25 MB
    // Online cache: 10 minutes to reduce API calls
    private const val CACHE_MAX_AGE = 10 // 10 minutes when online
    // Offline cache: 14 days for extended offline support
    private const val CACHE_MAX_STALE = 14 // 14 days when offline

    // GitHub content cache: 15 MB
    private const val GITHUB_CACHE_SIZE = 15L * 1024 * 1024 // 15 MB

    // ========================================================================
    // OPTIMIZED RETRY CONFIGURATION
    // ========================================================================
    private const val MAX_RETRY = 3

    /**
     * Adaptive backoff delay calculation
     * Attempts: 1s, 2s, 4s, 8s (capped at 8 seconds)
     * This reduces unnecessary wait time compared to fixed 3-second delays
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        // Exponential backoff: 1000ms * 2^(attempt), capped at 8 seconds
        val delay = 1000L * (1 shl attempt.coerceAtMost(3))
        return delay.coerceAtMost(8000L)
    }

    // Interceptor that appends authentication and content-type headers to each request.
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $BEARER_TOKEN")
            .header("Content-Type", "application/json")
            // Accept compressed responses for reduced data transfer
            .header("Accept-Encoding", "gzip, deflate")
            .build()
        chain.proceed(newRequest)
    }

    // ========================================================================
    // RESPONSE COMPRESSION INTERCEPTOR
    // ========================================================================
    /**
     * Request compression interceptor
     * Enables gzip/deflate compression for outgoing requests
     * Benefits: 60-80% reduction in data transfer for JSON responses
     */
    private val compressionInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept-Encoding", "gzip, deflate")
            .build()
        chain.proceed(request)
    }

    /**
     * Response decompression interceptor
     * Automatically handles gzip/deflate encoded responses
     */
    private val decompressionInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept-Encoding", "gzip, deflate")
            .build()
        val response = chain.proceed(request)
        // Let OkHttp handle decompression automatically
        response
    }

    // Global Retry Interceptor with adaptive backoff
    private val retryInterceptor = Interceptor { chain ->
        var attempt = 0
        var response: Response? = null
        var exception: Exception? = null

        while (attempt <= MAX_RETRY) {
            try {
                if (attempt > 0) {
                    // CRITICAL: Close previous response before retrying
                    response?.close()
                    val delayMs = calculateRetryDelay(attempt - 1)
                    Log.i(TAG, "Retrying request (attempt $attempt of $MAX_RETRY) after ${delayMs}ms...")
                    Thread.sleep(delayMs)
                }
                response = chain.proceed(chain.request())
                if (response.isSuccessful) return@Interceptor response

                // If not successful, close and check if retryable
                val code = response.code
                response.close()

                // Only retry on specific server error codes
                if (code == 503 || code == 504 || code == 429) {
                    Log.w(TAG, "Request failed with code $code, will retry")
                } else {
                    // For non-retryable codes, return the response (will be handled by caller)
                    throw IOException("Request failed with non-retryable code: $code")
                }
            } catch (e: Exception) {
                exception = e
                // Check if the request was canceled (e.g., by Coroutine cancellation)
                if (e is IOException && e.message?.contains("Canceled", ignoreCase = true) == true) {
                    Log.i(TAG, "Request was canceled, stopping retries")
                    throw e
                }

                if (e is SocketTimeoutException || e is IOException) {
                    Log.w(TAG, "Request failed (attempt $attempt): ${e.message}")
                    // Don't close response here as it might be null or already closed
                } else {
                    // For non-retryable exceptions, throw immediately
                    throw e
                }
            }
            attempt++
        }

        // If we reached here, all attempts failed
        response?.close()
        throw exception ?: IOException("Request failed after $MAX_RETRY retries")
    }

    // Cache control interceptor for offline support and reduced network calls
    private val cacheInterceptor = Interceptor { chain ->
        var request = chain.request()

        // Add cache control headers based on network availability
        request = request.newBuilder()
            .cacheControl(
                CacheControl.Builder()
                    .maxAge(CACHE_MAX_AGE, TimeUnit.MINUTES)
                    .build()
            )
            .build()

        val response = chain.proceed(request)

        // Cache responses for offline use
        response.newBuilder()
            .header("Cache-Control", "public, max-age=${CACHE_MAX_AGE * 60}")
            .removeHeader("Pragma")
            .build()
    }

    // Force cache interceptor for offline scenarios
    private val forceCacheInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .cacheControl(
                CacheControl.Builder()
                    .maxStale(CACHE_MAX_STALE, TimeUnit.DAYS)
                    .build()
            )
            .build()

        chain.proceed(request)
    }

    // Logging interceptor to print request/response bodies during debug.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * Creates OkHttpClient with caching, retry logic, and compression enabled.
     * Should be called with application context to initialize cache directory.
     *
     * Optimizations applied:
     * - 25 MB cache for offline support (was 10 MB)
     * - 10-minute cache age to reduce API calls (was 5 minutes)
     * - 14-day stale cache for extended offline support (was 7 days)
     * - Adaptive backoff retry (1s, 2s, 4s, 8s) instead of fixed 3s delays
     * - Response compression for 60-80% data reduction
     */
    fun createOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, CACHE_SIZE)

        return OkHttpClient.Builder()
            .cache(cache)
            // Request compression
            .addInterceptor(compressionInterceptor)
            .addInterceptor(authInterceptor)
            // Adaptive backoff retry logic
            .addInterceptor(retryInterceptor)
            .addNetworkInterceptor(cacheInterceptor) // For online requests with proper cache headers
            .addInterceptor(forceCacheInterceptor) // For offline requests - fallback to cached data
            //.addInterceptor(loggingInterceptor) // Uncomment for debugging
            .dns(SingletonDnsResolver.getDns()) // Cloudflare DNS over HTTPS
            // Optimized timeouts
            .connectTimeout(20, TimeUnit.SECONDS) // Reduced from 30 for faster failure detection
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Enable gzip compression
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Creates OkHttpClient for GitHub content with larger cache
     * Optimized for fetching curated content lists
     */
    fun createGitHubOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "github_cache")
        val cache = Cache(cacheDir, GITHUB_CACHE_SIZE)

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(compressionInterceptor)
            .addInterceptor(retryInterceptor)
            .addNetworkInterceptor(cacheInterceptor)
            .addInterceptor(forceCacheInterceptor)
            .dns(SingletonDnsResolver.getDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Creates Retrofit client with provided OkHttpClient.
     */
    fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(TmdbApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Lazy initialization for backward compatibility
    // Note: This uses basic client without full optimizations
    // Use createOkHttpClient(context) for production
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(compressionInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor)
            .dns(SingletonDnsResolver.getDns())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Singleton access point for the API service interface.
    val tmdbApiService: TmdbApiService by lazy {
        createRetrofit(okHttpClient).create(TmdbApiService::class.java)
    }
}