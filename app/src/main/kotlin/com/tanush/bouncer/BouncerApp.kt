package com.tanush.bouncer

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.services.MatchCacheService
import io.sentry.Sentry
import java.io.File

class BouncerApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize managers
        TeamLogoManager.initialize(this)
        TeamAcronymManager.initialize(this)
        FavouriteTeamsManager.initialize(this)
        
        // Initialize disk cache for offline support
        MatchCacheService.initDiskCache(this)

        // Configure Coil ImageLoader with sensible memory/disk caches and OkHttp integration
        try {
            val okHttpClient = com.tanush.bouncer.network.HttpClientProvider.sharedClient

            val imageLoader = ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(cacheDir, "image_cache"))
                        .maxSizeBytes(40L * 1024 * 1024) // 40MB
                        .build()
                }
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15) // 15% of app memory
                        .build()
                }
                .respectCacheHeaders(false)
                .build()

            // Set as default for Coil's image loader
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            // If Coil initialization fails, report to Sentry but keep app functional
            Sentry.captureException(e)
        }
    }
}

