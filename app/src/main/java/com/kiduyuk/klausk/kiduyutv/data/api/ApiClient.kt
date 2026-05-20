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
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"
    
    // Debug mode flag - set to true to see detailed logs
    private const val DEBUG_MODE = true

    // Bearer token used for Authorization header for all API requests.
    private const val BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI1NmE4YjFmMGFhYzQ4NTA0ODI2ZTgwNjY4NjViMDc0MCIsIm5iZiI6MTYyNjIwNDQ3NS4wOTksInN1YiI6IjYwZWRlOTNiMGYwZGE1MDA1ZmQzMzk0YSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.cpc2FUt6ENj6p-16b4ER0Sq5x34NTMHE4HErcSlb13o"

    // Interceptor that appends authentication and content-type headers to each request.
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        if (DEBUG_MODE) {
            Log.d(TAG, "=== AUTH INTERCEPTOR ===")
            Log.d(TAG, "Original URL: ${originalRequest.url}")
            Log.d(TAG, "Original Headers: ${originalRequest.headers}")
        }
        
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $BEARER_TOKEN")
            .header("Content-Type", "application/json")
            .build()
        
        if (DEBUG_MODE) {
            Log.d(TAG, "Auth Header: Bearer [TOKEN_LENGTH=${BEARER_TOKEN.length}]")
            Log.d(TAG, "New URL: ${newRequest.url}")
            Log.d(TAG, "New Headers: ${newRequest.headers}")
        }
        
        val response = chain.proceed(newRequest)
        
        if (DEBUG_MODE) {
            Log.d(TAG, "Response Code: ${response.code}")
            Log.d(TAG, "Response Message: ${response.message}")
            Log.d(TAG, "Response Headers: ${response.headers}")
        }
        
        return@Interceptor response
    }

    // Global Retry Interceptor: Retries 3 times with delay on timeout or IO error
    private val retryInterceptor = Interceptor { chain ->
        var attempt = 0
        val maxRetry = 3
        val retryDelayMs = 3000L
        var response: Response? = null
        var exception: Exception? = null

        if (DEBUG_MODE) {
            Log.d(TAG, "=== RETRY INTERCEPTOR ===")
            Log.d(TAG, "Request URL: ${chain.request().url}")
        }

        while (attempt <= maxRetry) {
            try {
                if (attempt > 0) {
                    response?.close()
                    if (DEBUG_MODE) {
                        Log.w(TAG, "RETRY attempt $attempt of $maxRetry after ${retryDelayMs / 1000}s")
                    }
                    Thread.sleep(retryDelayMs)
                }
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "Attempt $attempt: Proceeding with request to ${chain.request().url}")
                }
                
                response = chain.proceed(chain.request())
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "Attempt $attempt: Response code = ${response.code}")
                }
                
                if (response.isSuccessful) {
                    if (DEBUG_MODE) {
                        Log.d(TAG, "Attempt $attempt: SUCCESS - ${response.code}")
                    }
                    return@Interceptor response
                }

                val code = response.code
                response.close()

                if (code == 503 || code == 504 || code == 429) {
                    if (DEBUG_MODE) {
                        Log.w(TAG, "Attempt $attempt: Retryable error $code")
                    }
                } else {
                    if (DEBUG_MODE) {
                        Log.e(TAG, "Attempt $attempt: Non-retryable error $code")
                    }
                    throw IOException("Request failed with non-retryable code: $code")
                }
            } catch (e: Exception) {
                exception = e
                if (e is IOException && e.message?.contains("Canceled", ignoreCase = true) == true) {
                    if (DEBUG_MODE) {
                        Log.i(TAG, "Request was canceled, stopping retries")
                    }
                    throw e
                }

                if (e is SocketTimeoutException || e is IOException) {
                    if (DEBUG_MODE) {
                        Log.w(TAG, "Attempt $attempt: Network error - ${e.message}")
                    }
                } else {
                    if (DEBUG_MODE) {
                        Log.e(TAG, "Attempt $attempt: Non-retryable exception - ${e.message}")
                    }
                    throw e
                }
            }
            attempt++
        }

        response?.close()
        if (DEBUG_MODE) {
            Log.e(TAG, "All $maxRetry retries failed!")
        }
        throw exception ?: IOException("Request failed after $maxRetry retries")
    }

    // Logging interceptor to print request/response bodies during debug.
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        if (DEBUG_MODE) {
            Log.d(TAG, "[HTTP] $message")
        }
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * Creates OkHttpClient with retry logic enabled (no caching).
     */
    fun createOkHttpClient(context: Context): OkHttpClient {
        if (DEBUG_MODE) {
            Log.d(TAG, "=== createOkHttpClient() ===")
            Log.d(TAG, "Context: $context")
            Log.d(TAG, "Cache dir: ${context.cacheDir}")
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            
        if (DEBUG_MODE) {
            Log.d(TAG, "OkHttpClient created successfully")
            Log.d(TAG, "Connection timeout: 30s")
            Log.d(TAG, "Read timeout: 30s")
            Log.d(TAG, "Write timeout: 30s")
        }
        
        return client
    }

    /**
     * Creates Retrofit client with provided OkHttpClient.
     */
    fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
        if (DEBUG_MODE) {
            Log.d(TAG, "=== createRetrofit() ===")
            Log.d(TAG, "Base URL: ${TmdbApiService.BASE_URL}")
        }
        
        val retrofit = Retrofit.Builder()
            .baseUrl(TmdbApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        if (DEBUG_MODE) {
            Log.d(TAG, "Retrofit client created successfully")
        }
        
        return retrofit
    }

    // Lazy initialization for backward compatibility
    private val okHttpClient: OkHttpClient by lazy {
        if (DEBUG_MODE) {
            Log.d(TAG, "=== Lazy OkHttpClient Initialization ===")
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            
        if (DEBUG_MODE) {
            Log.d(TAG, "Lazy OkHttpClient created successfully")
        }
        
        client
    }

    // Singleton access point for the API service interface.
    val tmdbApiService: TmdbApiService by lazy {
        if (DEBUG_MODE) {
            Log.d(TAG, "=== Creating TmdbApiService ===")
            Log.d(TAG, "Base URL: ${TmdbApiService.BASE_URL}")
        }
        
        val service = createRetrofit(okHttpClient).create(TmdbApiService::class.java)
        
        if (DEBUG_MODE) {
            Log.d(TAG, "TmdbApiService created successfully")
        }
        
        service
    }
    
    /**
     * Utility method to check if the API token is valid (not expired).
     */
    fun isTokenValid(): Boolean {
        return try {
            val parts = BEARER_TOKEN.split(".")
            if (parts.size != 3) {
                Log.w(TAG, "Token format invalid - not 3 parts")
                return false
            }
            // Decode payload (second part) to check expiration
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
            val expMatch = Regex("\"exp\":(\\d+)").find(payload)
            if (expMatch != null) {
                val exp = expMatch.groupValues[1].toLong()
                val currentTime = System.currentTimeMillis() / 1000
                val isValid = exp > currentTime
                Log.d(TAG, "Token exp=$exp, current=$currentTime, valid=$isValid")
                return isValid
            }
            Log.w(TAG, "Could not parse token expiration")
            true // Assume valid if can't parse
        } catch (e: Exception) {
            Log.e(TAG, "Error validating token: ${e.message}")
            true // Assume valid on error
        }
    }
}