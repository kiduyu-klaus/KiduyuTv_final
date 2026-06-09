package com.kiduyuk.klausk.kiduyutv.data.remote

import com.kiduyuk.klausk.kiduyutv.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * TraktApiClient - Retrofit client for Trakt.tv API
 * 
 * Base URL: https://api.trakt.tv/
 */
object TraktApiClient {

    private const val BASE_URL = "https://api.trakt.tv/"
    
    // Version header required by Trakt API
    private const val TRAKT_API_VERSION = "2"
    
    @Volatile
    private var instance: Retrofit? = null

    @Volatile
    private var apiServiceInstance: TraktApiService? = null

    /**
     * Get the API service instance
     */
    val apiService: TraktApiService
        get() = getInstance()

    /**
     * Get the TraktApiService singleton instance
     */
    fun getInstance(): TraktApiService {
        return apiServiceInstance ?: synchronized(this) {
            apiServiceInstance ?: createApiService().also { apiServiceInstance = it }
        }
    }

    private fun createApiService(): TraktApiService {
        return createRetrofit().create(TraktApiService::class.java)
    }
    
    /**
     * Create OkHttpClient with Trakt authentication headers
     */
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            
            // Get client ID from TraktAuthManager
            val clientId = com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.TRAKT_CLIENT_ID
            
            val newRequest = originalRequest.newBuilder()
                .header("trakt-api-version", TRAKT_API_VERSION)
                .header("trakt-api-key", clientId)
                .build()
            
            chain.proceed(newRequest)
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Create Retrofit instance
     */
    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
