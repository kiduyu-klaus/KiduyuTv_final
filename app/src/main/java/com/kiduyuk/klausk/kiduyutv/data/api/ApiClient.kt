package com.kiduyuk.klausk.kiduyutv.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    
    // Bearer token used for Authorization header for all API requests.
    private const val BEARER_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w"

    // Interceptor that appends authentication and content-type headers to each request.
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request() // save the original outgoing request
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $BEARER_TOKEN") // attach auth token
            .header("Content-Type", "application/json") // send JSON payload
            .build()
        chain.proceed(newRequest) // continue with the modified request
    }

    // Logging interceptor to print request/response bodies during debug.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // OkHttp client with interceptors and custom timeouts.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Retrofit client with base URL and Gson converter.
    private val retrofit = Retrofit.Builder()
        .baseUrl(TmdbApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Singleton access point for the API service interface.
    val tmdbApiService: TmdbApiService = retrofit.create(TmdbApiService::class.java)
}
