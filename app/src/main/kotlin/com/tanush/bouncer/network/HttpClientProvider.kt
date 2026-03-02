package com.tanush.bouncer.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Singleton provider for a shared [OkHttpClient] instance.
 * All network consumers (Retrofit, Coil, raw OkHttp calls) should use
 * [sharedClient] to avoid duplicating connection pools, dispatchers, and
 * thread pools.
 */
object HttpClientProvider {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * Single shared OkHttpClient used across the entire application.
     */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(loggingInterceptor)
            .build()
    }
}
