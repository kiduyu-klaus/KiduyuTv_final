package com.kiduyuk.klausk.kiduyutv.data.api

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import com.kiduyuk.klausk.kiduyutv.util.SingletonDnsResolver
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"

    // Bearer token used for Authorization header for all API requests.
    private const val BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI5ZGUzZGU2MjNiNTc5ZjZlMTI3YzZlYzYwM2I5Zjc0ZCIsIm5iZiI6MTY2MDMwMTIxNC4yNCwic3ViIjoiNjJmNjJmOWVjM2JmZmUwMDdhNzJlODVkIiwic2NvcGVzIjpbImFwaV9yZWFkIl0sInZlcnNpb24iOjF9.S10N9Lag4A71FXL-VsErRF71UhADOKTPeK5FDnsEOhk"

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
        throw exception ?: IOException("Request failed after $maxRetry retries")
    }

    // Logging interceptor to print request/response bodies during debug.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * Creates OkHttpClient with retry logic enabled (no caching).
     * Should be called with application context.
     */
    fun createOkHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            //.addInterceptor(retryInterceptor) // Added global retry logic
            //.addInterceptor(loggingInterceptor)
            .dns(SingletonDnsResolver.getDns()) // Cloudflare DNS over HTTPS
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
        // Default client without cache
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor) // Added global retry logic
            //.addInterceptor(loggingInterceptor)
            .dns(SingletonDnsResolver.getDns()) // Cloudflare DNS over HTTPS
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Singleton access point for the API service interface.
    val tmdbApiService: TmdbApiService by lazy {
        createRetrofit(okHttpClient).create(TmdbApiService::class.java)
    }
}