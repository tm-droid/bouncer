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
import com.tanush.bouncer.data.TeamDirectory
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.remote.AppwriteAICache
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.AppwriteHighlightsClient
import com.tanush.bouncer.data.remote.AppwriteMatchCommentaryClient
import com.tanush.bouncer.data.remote.AppwriteMatchDetailClient
import com.tanush.bouncer.data.remote.AppwritePlayerFormsClient
import com.tanush.bouncer.data.remote.AppwritePlayerProfilesClient
import com.tanush.bouncer.data.repository.toEntity
import com.tanush.bouncer.network.HttpClientProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.tanush.bouncer.services.AICacheService
import com.tanush.bouncer.services.DatabaseCleanupWorker
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.services.ScorePollingWorker
import com.tanush.bouncer.services.SeriesSyncWorker
import com.tanush.bouncer.ui.notifications.RichNotificationBuilder
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Global health snapshot of init steps. Other code can query whether a specific
 * component is healthy and degrade gracefully when not.
 */
object AppHealth {
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val _appwriteReachable = MutableStateFlow(false)
    val appwriteReachable: StateFlow<Boolean> = _appwriteReachable.asStateFlow()

    fun markFailed(component: String) { failed += component }
    fun isHealthy(component: String): Boolean = component !in failed
    fun anyFailures(): Set<String> = failed.toSet()
    internal fun setAppwriteReachable(reachable: Boolean) { _appwriteReachable.value = reachable }
}

class BouncerApp : Application() {

    companion object {
        private const val TAG = "BouncerApp"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        HttpClientProvider.initialize(cacheDir)
        // TeamLogoManager: Gson cold-init + JSON parsing is heavy — run off main thread.
        // The manager returns null/emptyMap gracefully until initialized, so callers are safe.
        applicationScope.launch(Dispatchers.IO) {
            initializeSafely("TeamLogoManager") { TeamLogoManager.initialize(applicationContext) }
        }
        initializeSafely("TeamAcronymManager") { TeamAcronymManager.initialize(this) }
        initializeSafely("FavouriteTeamsManager") { FavouriteTeamsManager.initialize(this) }
        applicationScope.launch(Dispatchers.IO) {
            initializeSafely("MatchSubscriptionManager") { MatchSubscriptionManager.initialize(this@BouncerApp) }
        }
        initializeSafely("NotificationChannels") {
            RichNotificationBuilder.CricketNotificationChannel.createNotificationChannels(this)
        }
        initializeSafely("MatchCacheService") { MatchCacheService.initDiskCache(this) }
        initializeSafely("AppwriteClient") { AppwriteClient.initialize(this) }
        // Verify Appwrite reachability; reflect in AppHealth so UI can show degraded state.
        // Timeout guards against indefinite hang on slow/captive-portal networks.
        applicationScope.launch(Dispatchers.IO) {
            val reachable = try {
                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    AppwriteClient.client?.ping()
                } != null
            } catch (e: Exception) {
                Sentry.addBreadcrumb(
                    Breadcrumb().apply {
                        category = "app.init"
                        message = "Appwrite ping failed: ${e.message}"
                        level = SentryLevel.WARNING
                    }
                )
                Sentry.captureException(e)
                false
            }
            AppHealth.setAppwriteReachable(reachable)
        }
        initializeSafely("AppwritePlayerFormsClient") { AppwritePlayerFormsClient.initialize() }
        initializeSafely("AppwriteAICache") { AppwriteAICache.initialize() }
        initializeSafely("AppwriteHighlightsClient") { AppwriteHighlightsClient.initialize() }
        initializeSafely("AppwritePlayerProfilesClient") { AppwritePlayerProfilesClient.initialize() }
        initializeSafely("AppwriteMatchCommentaryClient") { AppwriteMatchCommentaryClient.initialize() }
        initializeSafely("AppwriteMatchDetailClient") { AppwriteMatchDetailClient.initialize() }
        initializeSafely("NotificationInboxManager") { NotificationInboxManager.initialize(this, applicationScope) }
        initializeSafely("AICacheService") {
            AICacheService.initialize(BouncerDatabase.getInstance(this).aiCacheDao())
        }
        // Warm team directory so first screen access doesn't block / silently fail.
        applicationScope.launch(Dispatchers.IO) {
            try { TeamDirectory.fetchTeams() } catch (e: Exception) { Sentry.captureException(e) }
        }

        // Start Appwrite Realtime subscriptions (app-lifetime)
        initializeSafely("AppwriteRealtime") {
            val highlightDao = BouncerDatabase.getInstance(this).highlightDao()
            AppwriteHighlightsClient.startRealtimeSubscription(
                scope = applicationScope,
                onNewHighlight = { row ->
                    applicationScope.launch(Dispatchers.IO) {
                        highlightDao.upsertAll(listOf(row.toEntity()))
                    }
                }
            )
            AppwriteAICache.startRealtimeSubscription(
                scope = applicationScope,
                onNewEntry = { key, commentary ->
                    // Dispatchers.IO required — ingestFromRealtime acquires a mutex and writes to
                    // Room. Running on Main risks jank if the mutex is contested or Room suspends.
                    applicationScope.launch(Dispatchers.IO) {
                        AICacheService.ingestFromRealtime(key, commentary)
                    }
                }
            )
            // Table-wide subscriptions; MatchDetailViewModel filters the shared flows by matchId.
            AppwriteMatchCommentaryClient.startRealtime()
            AppwriteMatchDetailClient.startRealtime()
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

        // ── Subscribe to FCM topic for live match state changes ─────────────
        FirebaseMessaging.getInstance().subscribeToTopic("cricket-live-updates")
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "FCM topic subscription failed: ${task.exception?.message}")
                    Sentry.captureException(task.exception ?: Exception("FCM subscribe failed"))
                }
            }

        // ── Enqueue periodic workers after first frame — not needed for launch ──
        applicationScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500L)
            initializeSafely("ScorePollingWorker") { ScorePollingWorker.enqueuePeriodicWork(this@BouncerApp) }
            initializeSafely("SeriesSyncWorker") { SeriesSyncWorker.enqueuePeriodicWork(this@BouncerApp) }
            initializeSafely("DatabaseCleanupWorker") { DatabaseCleanupWorker.enqueueWeekly(this@BouncerApp) }
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
        val failures = AppHealth.anyFailures()
        if (failures.isNotEmpty()) {
            Log.w(TAG, "Application initialized with degraded components: $failures")
        } else {
            Log.i(TAG, "Application initialized successfully")
        }
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
            AppHealth.markFailed(componentName)
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
