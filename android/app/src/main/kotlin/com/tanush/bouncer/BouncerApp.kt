package com.tanush.bouncer

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.managers.TeamColorsLoader
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.data.prefs.PreferenceStore
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.PlayedMatchIndex
import com.tanush.bouncer.data.remote.AppwriteAICache
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.AppwriteHighlightsClient
import com.tanush.bouncer.data.remote.AppwriteUsageTracker
import com.tanush.bouncer.data.remote.RealtimeLifecycleController
import com.tanush.bouncer.data.remote.AppwriteMatchCommentaryClient
import com.tanush.bouncer.data.remote.AppwriteMatchDetailClient
import com.tanush.bouncer.data.remote.AppwriteMatchScorecardClient
import com.tanush.bouncer.data.remote.AppwritePlayerProfilesClient
import com.tanush.bouncer.data.remote.AppwriteReadGuard
import com.tanush.bouncer.data.repository.HighlightsRepository
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.network.PlayerImages
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.tanush.bouncer.data.repository.NewsDiskCache
import com.tanush.bouncer.utils.RemindersManager

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

        /** Captive-portal networks accept the connection and never answer; this bounds that. */
        private const val APPWRITE_PING_TIMEOUT_MS = 10_000L
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // ── Initialize Sentry (manual init) ──────────────────────────────
        // Auto-init is disabled in the manifest: the SDK has no manifest key to register a
        // custom OptionsConfiguration, so SentryOptionsConfig (beforeSend noise filter,
        // transport timeouts, replay/trace refinements) only runs via manual init. init still
        // reads all manifest meta-data (DSN, environment, replay masking), then applies our
        // configure() on top. Must run first so everything below reports through the filter.
        // Unless the user has switched diagnostics off in Settings.
        Diagnostics.start(this)

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

        // ── Initialize managers ──────────────────────────────────────────
        HttpClientProvider.initialize(cacheDir)
        // Before anything that reads a preference — AppwriteReadGuard's latch restore, a few lines
        // down, is the first. warmUp() blocks on the initial store read, and on the first launch
        // after upgrade also on the one-time SharedPreferences migration.
        initializeSafely("PreferenceStore") {
            PreferenceStore.initialize(this)
            PreferenceStore.warmUp()
        }
        // TeamLogoManager: Gson cold-init + JSON parsing is heavy — run off main thread.
        // The manager returns null/emptyMap gracefully until initialized, so callers are safe.
        applicationScope.launch(Dispatchers.IO) {
            initializeSafely("TeamLogoManager") { TeamLogoManager.initialize(applicationContext) }
            initializeSafely("TeamColorsLoader") { TeamColorsLoader.initialize(applicationContext) }
        }
        initializeSafely("TeamAcronymManager") { TeamAcronymManager.initialize(this) }
        initializeSafely("FavouriteTeamsManager") { FavouriteTeamsManager.initialize(this) }
        applicationScope.launch(Dispatchers.IO) {
            initializeSafely("PlayerImages") { PlayerImages.initialize(applicationContext, applicationScope) }
        }
        applicationScope.launch(Dispatchers.IO) {
            initializeSafely("MatchSubscriptionManager") { MatchSubscriptionManager.initialize(this@BouncerApp) }
            // Alarms do not survive a restore onto a new phone, an app update on some devices, or
            // a force stop. Setting one that is already set changes nothing.
            initializeSafely("Reminders") { RemindersManager.rescheduleAll(this@BouncerApp) }
        }
        initializeSafely("NotificationChannels") {
            RichNotificationBuilder.CricketNotificationChannel.createNotificationChannels(this)
        }
        initializeSafely("MatchCacheService") { MatchCacheService.initDiskCache(this) }
        initializeSafely("NewsDiskCache") { NewsDiskCache.initialize(cacheDir) }
        // Restore the persisted 402 breaker latch before any Appwrite read can fire, so a
        // relaunch after a budget-exhausted 402 keeps short-circuiting instead of re-probing.
        initializeSafely("AppwriteReadGuard") {
            AppwriteReadGuard.initPersistence(AppwriteReadGuard.PrefsBreakerStore(this))
        }
        initializeSafely("AppwriteClient") { AppwriteClient.initialize(this) }
        probeAppwriteReachabilityOnFirstScreen()
        initializeSafely("AppwriteAICache") { AppwriteAICache.initialize() }
        initializeSafely("AppwriteHighlightsClient") { AppwriteHighlightsClient.initialize() }
        initializeSafely("AppwritePlayerProfilesClient") { AppwritePlayerProfilesClient.initialize() }
        initializeSafely("AppwriteMatchCommentaryClient") { AppwriteMatchCommentaryClient.initialize() }
        initializeSafely("AppwriteMatchDetailClient") { AppwriteMatchDetailClient.initialize() }
        initializeSafely("AppwriteMatchScorecardClient") { AppwriteMatchScorecardClient.initialize() }
        initializeSafely("NotificationInboxManager") { NotificationInboxManager.initialize(this, applicationScope) }
        // AICacheService is not started. Nothing reads or writes that cache — the device generates
        // no commentary — and starting it ran a Room delete and query on every launch and could
        // still push old rows to Appwrite. The class stays until an R8 usage pass removes it.
        // Start Appwrite Realtime subscriptions (app-lifetime)
        initializeSafely("AppwriteRealtime") {
            val database = BouncerDatabase.getInstance(this)
            val highlightsRepo = HighlightsRepository(database.highlightDao(), database.matchDao())
            PlayedMatchIndex.initialize(highlightsRepo, applicationScope)
            val startHighlights = {
                AppwriteHighlightsClient.startRealtimeSubscription(
                    scope = applicationScope,
                    onNewHighlight = { row ->
                        // Fuzzy-match + enrich before persisting so the row gets a
                        // matchedBouncerMatchId and actually appears on its match's Highlights tab.
                        applicationScope.launch(Dispatchers.IO) {
                            runCatching { highlightsRepo.ingestRows(listOf(row)) }
                                .onFailure { Sentry.captureException(it) }
                        }
                    }
                )
            }
            // Not started here. attach() holds every managed subscription until the first activity
            // starts: onCreate also runs for WorkManager and FCM wakes, which never show a screen
            // and never reach onActivityStopped, so a socket opened here stayed open for the whole
            // life of a background process.
            //
            // The ai-commentary-cache table channel that used to be registered next to this one is
            // gone: it fed AICacheService.ingestFromRealtime, whose rows nothing reads (the device
            // generates no commentary), so every device paid Realtime bandwidth for a dead cache.
            RealtimeLifecycleController.register(
                "highlights", startHighlights, AppwriteHighlightsClient::stop
            )
            RealtimeLifecycleController.attach(this, applicationScope)

            // Match detail and commentary are deliberately NOT subscribed here. They are
            // demand-scoped: MatchDetailViewModel subscribes while a match screen is open and
            // releases on onCleared(), so a device no longer receives every match that anyone,
            // anywhere, currently has open.
        }

        // Publishes what this device actually consumes from Appwrite, by quota, every 5 minutes.
        // The point is to see a plan cap coming instead of learning about it from a 402.
        initializeSafely("AppwriteUsageTracker") {
            AppwriteUsageTracker.startPeriodicFlush(applicationScope)
        }

        // ── Configure Coil ImageLoader ───────────────────────────────────
        initializeSafely("CoilImageLoader") {
            // The lambda overload: the shared client is built on Coil's first request, off the
            // main thread, instead of here in onCreate.
            val imageLoader = ImageLoader.Builder(this)
                .okHttpClient { HttpClientProvider.sharedClient }
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

        // ── Off the main thread: FCM, startup metrics, periodic workers ─────
        // `FirebaseMessaging.getInstance()` initialises the messaging component (disk, IPC to
        // Play services) and the first `Sentry.metrics()` call starts the metrics batch
        // processor's thread. Both ran here on the main thread inside `onCreate`, and both are
        // the top frame of a startup ANR (ANDROID-4F, ANDROID-4V). Nothing in launch waits on
        // either.
        applicationScope.launch(Dispatchers.IO) {
            Sentry.metrics().count("app_start", 1.0)
            Sentry.metrics().gauge("app_init_components", 4.0)
            FirebaseMessaging.getInstance().subscribeToTopic("cricket-live-updates")
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "FCM topic subscription failed: ${task.exception?.message}")
                        Sentry.captureException(task.exception ?: Exception("FCM subscribe failed"))
                    }
                }
            // Workers after first frame — not needed for launch.
            kotlinx.coroutines.delay(500L)
            applicationScope.launch(Dispatchers.IO) {
                // initialize() is idempotent; it is called here so the first value is the saved
                // set, not the empty one before the load, which would cancel a needed schedule.
                initializeSafely("MatchSubscriptionManager") { MatchSubscriptionManager.initialize(this@BouncerApp) }
                MatchSubscriptionManager.subscriptions
                    .map { it.isNotEmpty() }
                    .distinctUntilChanged()
                    .collect { followed ->
                        initializeSafely("ScorePollingWorker") {
                            ScorePollingWorker.syncPeriodicWork(this@BouncerApp, followed)
                        }
                    }
            }
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
        val failures = AppHealth.anyFailures()
        if (failures.isNotEmpty()) {
            Log.w(TAG, "Application initialized with degraded components: $failures")
        } else {
            Log.i(TAG, "Application initialized successfully")
        }
    }

    // ── Helper: wrap any init step so a failure is reported but never kills startup ──
    /**
     * Records whether Appwrite answers, so the UI can show a degraded state.
     *
     * The timeout guards against an indefinite hang on a captive-portal network, where the
     * connection is up and nothing resolves.
     */
    /**
     * The ping answers "can the screens reach Appwrite", so it runs when the first screen starts.
     * onCreate also runs for WorkManager and FCM wakes that never show one, and each of those paid
     * a network round trip for an answer nothing would read.
     */
    private fun probeAppwriteReachabilityOnFirstScreen() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                unregisterActivityLifecycleCallbacks(this)
                probeAppwriteReachability()
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }

    private fun probeAppwriteReachability() {
        applicationScope.launch(Dispatchers.IO) {
            val reachable = try {
                kotlinx.coroutines.withTimeoutOrNull(APPWRITE_PING_TIMEOUT_MS) {
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
                // "Cannot reach Appwrite" is the answer this probe exists to produce, and it is
                // recorded in AppHealth below. A no-route failure is therefore not an error: it
                // fires on every cold start behind a captive portal, in flight mode or on a dead
                // cell, and reporting it drowns the ping failures that do mean something — a
                // wrong endpoint, a revoked project, an SDK that threw. SentryOptionsConfig
                // already drops UnknownHostException globally; ConnectException reaches Sentry
                // unless it names sentry.io, which is why this was noisy for Appwrite alone.
                if (!HttpClientProvider.isConnectivityException(e)) Sentry.captureException(e)
                false
            }
            AppHealth.setAppwriteReachable(reachable)
        }
    }

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
