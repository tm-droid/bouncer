package com.tanush.bouncer.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.tanush.bouncer.liveactivity.LiveActivityManager
import com.tanush.bouncer.liveactivity.LiveActivityNotificationBuilder
import com.tanush.bouncer.liveactivity.LiveActivityRepository
import com.tanush.bouncer.liveactivity.LiveActivityStyle
import com.tanush.bouncer.liveactivity.MatchState
import com.tanush.bouncer.liveactivity.NoopMatchUpdateDataSource
import com.tanush.bouncer.liveactivity.UpdateFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LiveActivityService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repository by lazy {
        LiveActivityRepository(
            context = applicationContext,
            dataSource = NoopMatchUpdateDataSource()
        )
    }
    private val notificationBuilder by lazy { LiveActivityNotificationBuilder(applicationContext) }
    private val manager by lazy { LiveActivityManager(applicationContext, notificationBuilder) }

    private var activeMatchId: String? = null
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: return START_NOT_STICKY
                val style = intent.getStringExtra(EXTRA_STYLE)
                    ?.let { runCatching { LiveActivityStyle.valueOf(it) }.getOrNull() }
                    ?: LiveActivityStyle.Compact
                val frequency = intent.getStringExtra(EXTRA_FREQUENCY)
                    ?.let { runCatching { UpdateFrequency.valueOf(it) }.getOrNull() }
                    ?: UpdateFrequency.EVERY_OVER
                startTracking(matchId, style, frequency)
            }

            ACTION_STYLE -> {
                val style = intent.getStringExtra(EXTRA_STYLE)
                    ?.let { runCatching { LiveActivityStyle.valueOf(it) }.getOrNull() }
                    ?: LiveActivityStyle.Compact
                serviceScope.launch { manager.setStyle(style) }
            }

            ACTION_FREQUENCY -> {
                val frequency = intent.getStringExtra(EXTRA_FREQUENCY)
                    ?.let { runCatching { UpdateFrequency.valueOf(it) }.getOrNull() }
                    ?: UpdateFrequency.EVERY_OVER
                serviceScope.launch { manager.updateFrequency(frequency) }
            }

            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTracking(matchId: String, style: LiveActivityStyle, frequency: UpdateFrequency) {
        activeMatchId = matchId
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            manager.startActivity(matchId, style)
            manager.updateFrequency(frequency)

            val warmState = repository.refreshState(matchId) ?: fallbackState(matchId)
            val warmNotification = notificationBuilder.buildNotification(
                matchId = matchId,
                style = style,
                state = warmState,
                relativeTime = getString(com.tanush.bouncer.R.string.live_activity_last_update_just_now),
                highPriority = false
            )
            startForeground(matchId.hashCode(), warmNotification)

            repository.subscribeToMatch(matchId, intervalMs = 7_000L).collectLatest { (state, update) ->
                manager.postUpdate(state, update)
                if (state.isCompleted) {
                    stopTracking()
                }
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        serviceScope.launch { manager.endActivity() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fallbackState(matchId: String): MatchState {
        val now = System.currentTimeMillis()
        return MatchState(
            matchId = matchId,
            leagueName = "Live Match",
            team1Short = "T1",
            team2Short = "T2",
            team1Score = "0/0",
            team2Score = "0/0",
            currentScore = "0/0",
            overs = "0.0",
            wickets = 0,
            runs = 0,
            currentBatter = "--",
            nonStriker = "--",
            currentBowler = "--",
            lastBall = "-",
            liveSinceEpochMs = now,
            lastUpdateEpochMs = now,
            currentRunRate = 0f,
            requiredRunRate = 0f,
            wicketsRemaining = 10,
            winProbabilityPercent = null,
            statusText = "Live • Fetching latest score",
            isLive = true,
            isCompleted = false
        )
    }

    inner class LocalBinder : Binder() {
        fun service(): LiveActivityService = this@LiveActivityService
    }

    companion object {
        const val ACTION_START = "com.tanush.bouncer.liveactivity.START"
        const val ACTION_STOP = "com.tanush.bouncer.liveactivity.STOP"
        const val ACTION_STYLE = "com.tanush.bouncer.liveactivity.STYLE"
        const val ACTION_FREQUENCY = "com.tanush.bouncer.liveactivity.FREQUENCY"

        const val EXTRA_MATCH_ID = "extra_match_id"
        const val EXTRA_STYLE = "extra_style"
        const val EXTRA_FREQUENCY = "extra_frequency"

        fun start(
            context: Context,
            matchId: String,
            style: LiveActivityStyle,
            frequency: UpdateFrequency
        ) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MATCH_ID, matchId)
                putExtra(EXTRA_STYLE, style.name)
                putExtra(EXTRA_FREQUENCY, frequency.name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateStyle(context: Context, style: LiveActivityStyle) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_STYLE
                putExtra(EXTRA_STYLE, style.name)
            }
            context.startService(intent)
        }

        fun updateFrequency(context: Context, frequency: UpdateFrequency) {
            val intent = Intent(context, LiveActivityService::class.java).apply {
                action = ACTION_FREQUENCY
                putExtra(EXTRA_FREQUENCY, frequency.name)
            }
            context.startService(intent)
        }

    }
}
