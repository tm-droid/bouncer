package com.tanush.bouncer.data.remote

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.utils.sentryBreadcrumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drops the always-on Appwrite Realtime subscriptions while the app is in the background, and
 * restores them when it returns.
 *
 * These sockets used to be opened once in `BouncerApp.onCreate` and never closed — `stop()` had no
 * callers at all — so every install held them open for the entire process lifetime, receiving (and
 * paying bandwidth for) events it could not possibly show. Realtime traffic shares the same
 * plan-level bandwidth pool as API and Storage.
 *
 * Uses activity-start/stop counting rather than `ProcessLifecycleOwner`, which would mean pulling in
 * `androidx.lifecycle:lifecycle-process` for one callback. A configuration change momentarily drops
 * the count to zero, which the grace period below absorbs.
 *
 * Only for genuinely app-lifetime subscriptions. Demand-scoped ones (match detail, commentary)
 * manage themselves through the base class refcount and need nothing here.
 */
object RealtimeLifecycleController {

    private const val TAG = "RealtimeLifecycle"

    private class Managed(val name: String, val start: () -> Unit, val stop: () -> Unit)

    private val managed = mutableListOf<Managed>()
    private val lock = Any()

    private var startedActivities = 0
    private var running = true
    private var pauseJob: Job? = null

    private val _foreground = MutableStateFlow(true)

    /**
     * False once the app has been in the background past the grace period, true again on return.
     * The same boundary as the socket pause, for loops that bill per tick (the match-detail presence
     * heartbeat) and should not keep firing for a screen nobody can see. True until [attach] has
     * observed a background, so code running without an Application (unit tests) is unaffected.
     */
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    /**
     * Register an app-lifetime subscription. [start] must be idempotent — the clients guard with
     * `subscriptionStarted`, which [AppwriteTableClient.stop] resets so a restart takes effect.
     */
    fun register(name: String, start: () -> Unit, stop: () -> Unit) = synchronized(lock) {
        managed += Managed(name, start, stop)
    }

    fun attach(application: Application, scope: CoroutineScope) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = onForeground()
            override fun onActivityStopped(activity: Activity) = onBackground(scope)

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun onForeground() = synchronized(lock) {
        startedActivities++
        // Cancels a pending pause, so a rotation or a quick app-switch never tears the sockets down.
        pauseJob?.cancel()
        pauseJob = null
        _foreground.value = true
        if (running) return
        running = true
        managed.forEach { entry ->
            runCatching { entry.start() }.onFailure {
                Log.w(TAG, "Realtime restart failed for ${entry.name}: ${it.message}")
            }
        }
        sentryBreadcrumb("appwrite.realtime", "resumed ${managed.size} subscriptions")
    }

    private fun onBackground(scope: CoroutineScope) = synchronized(lock) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities > 0) return
        pauseJob?.cancel()
        pauseJob = scope.launch {
            delay(CacheConfig.REALTIME_BACKGROUND_GRACE_MS)
            synchronized(lock) {
                // Re-check: the app may have come back during the grace period.
                if (startedActivities > 0 || !running) return@synchronized
                running = false
                _foreground.value = false
                managed.forEach { entry ->
                    runCatching { entry.stop() }.onFailure {
                        Log.w(TAG, "Realtime stop failed for ${entry.name}: ${it.message}")
                    }
                }
                sentryBreadcrumb("appwrite.realtime", "paused ${managed.size} subscriptions (backgrounded)")
            }
        }
    }
}
