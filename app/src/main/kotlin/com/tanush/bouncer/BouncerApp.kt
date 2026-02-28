package com.tanush.bouncer

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.CoilUtils
import okhttp3.OkHttpClient
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.managers.TeamAcronymManager
import java.io.File

class BouncerApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize managers
        TeamLogoManager.initialize(this)
        TeamAcronymManager.initialize(this)
        FavouriteTeamsManager.initialize(this)

        // Configure Coil ImageLoader with sensible memory/disk caches and OkHttp integration
        try {
            val okHttpClient = OkHttpClient.Builder()
                .build()

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
            // If Coil initialization fails, swallow but keep app functional
            e.printStackTrace()
        }
    }
}

