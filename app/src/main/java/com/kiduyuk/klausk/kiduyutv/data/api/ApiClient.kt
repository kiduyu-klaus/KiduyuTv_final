package com.kiduyuk.klausk.kiduyutv.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {

    // Bearer token used for Authorization header for all API requests.
    private const val BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w"

    // Cache configuration
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB
    private const val CACHE_MAX_AGE = 5 // 5 minutes when online
    private const val CACHE_MAX_STALE = 7 // 7 days when offline

    // Interceptor that appends authentication and content-type headers to each request.
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request() // save the original outgoing request
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $BEARER_TOKEN") // attach auth token
            .header("Content-Type", "application/json") // send JSON payload
            .build()
        chain.proceed(newRequest) // continue with the modified request
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
     * Creates OkHttpClient with caching enabled.
     * Should be called with application context to initialize cache directory.
     */
    fun createOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, CACHE_SIZE)

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(authInterceptor)
            .addNetworkInterceptor(cacheInterceptor) // For online requests
            .addInterceptor(forceCacheInterceptor) // For offline requests
            .addInterceptor(loggingInterceptor)
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
        // Default client without cache (will be replaced when context is available)
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Singleton access point for the API service interface.
    val tmdbApiService: TmdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TmdbApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApiService::class.java)
    }
}
