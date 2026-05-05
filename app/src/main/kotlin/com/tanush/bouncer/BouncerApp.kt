package com.tanush.bouncer

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.services.AICacheService
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.services.ScorePollingWorker
import com.tanush.bouncer.services.SeriesSyncWorker
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import java.io.File

class BouncerApp : Application() {

    companion object {
        private const val TAG = "BouncerApp"
    }

    override fun onCreate() {
        super.onCreate()

        // ── Install global uncaught-exception handler ────────────────────
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Sentry.captureException(throwable)
                Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            } catch (_: Exception) { /* Sentry itself failed, don't recurse */ }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // ── Leave a startup breadcrumb ───────────────────────────────────
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "app.lifecycle"
                message = "BouncerApp.onCreate started"
                level = SentryLevel.INFO
            }
        )
        Sentry.logger().info("BouncerApp startup begun")
        Sentry.metrics().count("app_start", 1.0)

        // ── Initialize managers ──────────────────────────────────────────
        initializeSafely("TeamLogoManager") { TeamLogoManager.initialize(this) }
        initializeSafely("TeamAcronymManager") { TeamAcronymManager.initialize(this) }
        initializeSafely("FavouriteTeamsManager") { FavouriteTeamsManager.initialize(this) }
        initializeSafely("MatchSubscriptionManager") { MatchSubscriptionManager.initialize(this) }
        initializeSafely("MatchCacheService") { MatchCacheService.initDiskCache(this) }
        initializeSafely("NotificationInboxManager") { NotificationInboxManager.initialize(this) }
        initializeSafely("AICacheService") {
            AICacheService.initialize(BouncerDatabase.getInstance(this).aiCacheDao())
        }

        // ── Configure Coil ImageLoader ───────────────────────────────────
        initializeSafely("CoilImageLoader") {
            val okHttpClient = com.tanush.bouncer.network.HttpClientProvider.sharedClient

            val imageLoader = ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(cacheDir, "image_cache"))
                        .maxSizeBytes(40L * 1024 * 1024) // 40 MB
                        .build()
                }
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15) // 15 % of app memory
                        .build()
                }
                .respectCacheHeaders(false)
                .build()

            coil.Coil.setImageLoader(imageLoader)
        }

        // ── Enqueue periodic score polling via WorkManager ───────────────
        initializeSafely("ScorePollingWorker") {
            ScorePollingWorker.enqueuePeriodicWork(this)
        }
        initializeSafely("SeriesSyncWorker") {
            SeriesSyncWorker.enqueuePeriodicWork(this)
        }

        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "app.lifecycle"
                message = "BouncerApp.onCreate completed"
                level = SentryLevel.INFO
            }
        )
        Sentry.logger().log(SentryLogLevel.INFO, "BouncerApp startup completed")
        Sentry.metrics().gauge("app_init_components", 4.0)
        Log.i(TAG, "Application initialized successfully")
    }

    // ── Helper: wrap any init step so a failure is reported but never kills startup ──
    private fun initializeSafely(componentName: String, block: () -> Unit) {
        try {
            block()
            Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    category = "app.init"
                    message = "$componentName initialized"
                    level = SentryLevel.INFO
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize $componentName", e)
            Sentry.captureException(e)
            Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    category = "app.init"
                    message = "$componentName FAILED: ${e.message}"
                    level = SentryLevel.ERROR
                }
            )
        }
    }
}
