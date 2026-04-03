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
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"

    // Bearer token used for Authorization header for all API requests.
    private const val BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w"

    // Cache configuration
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB
    private const val CACHE_MAX_AGE = 5 // 5 minutes when online
    private const val CACHE_MAX_STALE = 7 // 7 days when offline

    // Interceptor that appends authentication and content-type headers to each request.
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request( ) // save the original outgoing request
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $BEARER_TOKEN") // attach auth token
            .header("Content-Type", "application/json") // send JSON payload
            .build()
        chain.proceed(newRequest) // continue with the modified request
    }

    // Global Retry Interceptor: Retries 3 times with delay on timeout or IO error
    private val retryInterceptor = Interceptor { chain ->
        var attempt = 0
        val maxRetry = 3
        val retryDelayMs = 3000L // 3 seconds (reduced from 30s)
        var response: Response? = null
        var exception: Exception? = null

        while (attempt <= maxRetry) {
            try {
                if (attempt > 0) {
                    // CRITICAL: Close previous response before retrying
                    response?.close()
                    Log.i(TAG, "Retrying request (attempt $attempt of $maxRetry) after ${retryDelayMs / 1000}s...")
                    Thread.sleep(retryDelayMs)
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
        throw exception ?: IOException("Request failed after $maxRetry retries")
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
     * Creates OkHttpClient with caching and retry logic enabled.
     * Should be called with application context to initialize cache directory.
     */
    fun createOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache" )
        val cache = Cache(cacheDir, CACHE_SIZE)

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(authInterceptor)
            //.addInterceptor(retryInterceptor) // Added global retry logic
            .addNetworkInterceptor(cacheInterceptor) // For online requests with proper cache headers
            .addInterceptor(forceCacheInterceptor) // For offline requests - fallback to cached data
            //.addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Updated from 60 to 30
            .readTimeout(30, TimeUnit.SECONDS)    // Updated from 60 to 30
            .writeTimeout(30, TimeUnit.SECONDS)   // Updated from 60 to 30
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
    private val okHttpClient: OkHttpClient by lazy {
        // Default client without cache (will be replaced when context is available)
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor) // Added global retry logic
            //.addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Updated from 60 to 30
            .readTimeout(30, TimeUnit.SECONDS)    // Updated from 60 to 30
            .writeTimeout(30, TimeUnit.SECONDS)   // Updated from 60 to 30
            .build()
    }

    // Singleton access point for the API service interface.
    val tmdbApiService: TmdbApiService by lazy {
        createRetrofit(okHttpClient).create(TmdbApiService::class.java)
    }
}