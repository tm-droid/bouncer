package com.tanush.bouncer.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.ProductionLiveDetailSource
import com.tanush.bouncer.liveactivity.LiveUpdateContent
import com.tanush.bouncer.liveactivity.LiveUpdateDismissals
import com.tanush.bouncer.liveactivity.LiveUpdateLogoLoader
import com.tanush.bouncer.liveactivity.LiveUpdateNotifier
import com.tanush.bouncer.liveactivity.buildLiveUpdateContent
import com.tanush.bouncer.liveactivity.cachedMatchEntry
import com.tanush.bouncer.liveactivity.cachedLiveMatches
import com.tanush.bouncer.liveactivity.liveUpdateNotificationId
import com.tanush.bouncer.liveactivity.placeholderContent
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a promoted Live Update on screen for every live match the user follows.
 *
 * The feed is Appwrite Realtime, not a poll: `bouncer-match-sync` upserts each live match's
 * `match-live-detail` row every minute and `observeDetail` subscribes to that one row, so a score
 * arrives when it changes and costs no billed reads. The single billed read per match is
 * `getForMatch`, for first paint.
 *
 * The foreground service exists to keep the process alive for those websocket frames — **not**
 * because a promoted notification needs one. That distinction is what makes [onTimeout]
 * survivable: when Android's `dataSync` budget runs out the update pump stops, but the cards stay
 * on screen.
 *
 * Replaces `LiveActivityService`, which was wired to a `NoopMatchUpdateDataSource` and had no
 * caller anywhere in the app.
 */
class LiveUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifier by lazy { LiveUpdateNotifier(applicationContext) }
    private val source: LiveMatchDetailSource = ProductionLiveDetailSource

    private val jobs = mutableMapOf<String, Job>()
    private val contents = mutableMapOf<String, LiveUpdateContent>()

    /** Matches whose result is in. They keep their subscription but stop updating. */
    private val finished = mutableSetOf<String>()
    private var subscriptionJob: Job? = null

    /**
     * The match whose notification is the foreground-service notification. Android ties a
     * service's lifetime to exactly one notification id, so one of the cards has to play that
     * role rather than a second permanent notification sitting beside them.
     */
    private var anchorMatchId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        // The previous stack could never be promoted and said nothing about it. Whether the system
        // will honour the request is now the first thing this service records.
        sentryBreadcrumb("live_update", "Live Update service started", data = mapOf("canPromote" to notifier.canPromote()))
    }

    /**
     * A sticky restart redelivers a null intent, so nothing here may depend on one. The service
     * this replaces switched on `intent.action`, fell through every branch in that case, and never
     * called `startForeground` — a latent `ForegroundServiceDidNotStartInTimeException`.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MatchSubscriptionManager.initialize(applicationContext)
        val first = (MatchSubscriptionManager.subscriptions.value - finished).firstOrNull()
        if (first == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Synchronous, before any network work: the platform allows roughly five seconds between
        // startForegroundService and startForeground.
        anchorTo(first)
        if (subscriptionJob == null) {
            subscriptionJob = scope.launch { followSubscriptions() }
            scope.launch { presenceHeartbeat { jobs.keys.toList() } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        jobs.values.forEach(Job::cancel)
        jobs.keys.forEach(notifier::cancel)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * `dataSync` is capped at six cumulative foreground hours a day, which a Test comfortably
     * exceeds. Detaching leaves every card posted — they are ordinary notifications that happen to
     * be promoted — and hands the app's other surfaces back to the existing 5-minute poll chain.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        sentryBreadcrumb(
            "live_update",
            "Foreground dataSync budget exhausted; leaving Live Updates posted",
            SentryLevel.WARNING,
            mapOf("tracked" to jobs.size),
        )
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        ScorePollingWorker.enqueueLiveFollowUp(applicationContext)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /**
     * Start a tracking job per newly subscribed live match, drop the ones that went away.
     *
     * A subscription can name a match that has not started, which has no `match-live-detail` row
     * and would leave a placeholder card up indefinitely — so the set is filtered against the
     * cached live list. A cache with nothing to say is treated as unknown, not as "nothing is
     * live": silently refusing to track is the failure mode this whole feature had.
     */
    private suspend fun followSubscriptions() {
        MatchSubscriptionManager.subscriptions.collect { subscribed ->
            val cached = cachedLiveMatches()
            val trackable = subscribed.asSequence()
                .filter { it !in finished && !LiveUpdateDismissals.isDismissed(it) }
                .filter { id -> cached == null || cached.any { it.id == id } }
                .toSet()

            (jobs.keys - trackable).forEach { matchId ->
                jobs.remove(matchId)?.cancel()
                notifier.cancel(matchId)
                contents.remove(matchId)
                LiveUpdateDismissals.clear(matchId)
            }
            (trackable - jobs.keys).forEach { matchId ->
                jobs[matchId] = scope.launch { track(matchId) }
            }
            if (trackable.isEmpty()) stopSelf() else reanchor(trackable)
        }
    }

    /** First paint from the row, then every Realtime update to it, for the life of the job. */
    private suspend fun track(matchId: String) {
        try {
            source.getForMatch(matchId)?.let { publish(matchId, it) }
            source.observeDetail(matchId).collect { publish(matchId, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // One match's feed failing must not take the others down with it.
            Sentry.captureException(e)
            sentryBreadcrumb("live_update", "Detail feed failed", SentryLevel.ERROR, mapOf("matchId" to matchId))
        }
    }

    /**
     * Render one payload.
     *
     * A finished match is retired rather than unsubscribed: following a match is the user's choice
     * to reverse, not the scoreline's.
     */
    private suspend fun publish(matchId: String, snapshot: LiveDetailSnapshot) {
        val content = buildLiveUpdateContent(matchId, cachedMatchEntry(matchId), snapshot.payload)
        contents[matchId] = content

        // A finished match and a swiped-away one both stop updating; only the first is remembered
        // as finished, because a dismissal is about this notification and not about the match.
        if (content.isComplete || LiveUpdateDismissals.isDismissed(matchId)) {
            if (content.isComplete) finished.add(matchId)
            jobs.remove(matchId)?.cancel()
            notifier.cancel(matchId)
            val remaining = jobs.keys.toSet()
            if (remaining.isEmpty()) stopSelf() else reanchor(remaining)
            return
        }

        // Both crests: the designed card draws the opposition's too, and the loader memoises, so a
        // second lookup costs nothing after the first paint of a match.
        val logo = LiveUpdateLogoLoader.bitmapFor(applicationContext, content.logoTeamName)
        val opponentLogo = LiveUpdateLogoLoader.bitmapFor(applicationContext, content.opponentLogoTeamName)
        if (matchId == anchorMatchId) {
            enterForeground(matchId, notifier.build(content, logo, opponentLogo))
        } else {
            notifier.post(content, logo, opponentLogo)
        }
    }

    /** Move the foreground anchor onto a still-running match when the current one drops out. */
    private fun reanchor(trackable: Set<String>) {
        val current = anchorMatchId
        if (current != null && current in trackable) return
        anchorTo(trackable.first())
    }

    /**
     * Make [matchId]'s card the foreground notification, using its last known content or the
     * placeholder, and clear the card the previous anchor left behind.
     */
    private fun anchorTo(matchId: String) {
        val previous = anchorMatchId
        anchorMatchId = matchId
        val content = contents[matchId]
            ?: placeholderContent(matchId, null, getString(R.string.live_update_placeholder))
        enterForeground(matchId, notifier.build(content, null))
        if (previous != null && previous != matchId) notifier.cancel(previous)
    }

    companion object {
        /**
         * Start tracking, or nudge an already-running service. Safe to call repeatedly: the
         * service reads the live subscription set rather than anything carried on the intent.
         */
        fun start(context: Context) {
            val intent = Intent(context, LiveUpdateService::class.java)
            runCatching { context.startForegroundService(intent) }.onFailure {
                // Starting a foreground service from the background is refused on API 31+. A
                // subscription toggled from a widget or a notification action is a normal way to
                // land here, so this is a breadcrumb rather than a crash.
                sentryBreadcrumb("live_update", "Foreground start refused: ${it.message}", SentryLevel.WARNING)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveUpdateService::class.java))
        }
    }
}

/**
 * Enter the foreground with [matchId]'s card as the service notification.
 *
 * The type is asserted here as well as in the manifest: from API 34 the platform validates it
 * where the service actually enters the foreground, and a mismatch is a runtime failure rather
 * than a build one.
 *
 * The constant is a compile-time int that R8 inlines, so the reference is safe below API 29 where
 * `startForeground` simply ignores the type argument — which is what `ServiceCompat` exists to do.
 */
@SuppressLint("InlinedApi")
private fun Service.enterForeground(matchId: String, notification: Notification) = ServiceCompat.startForeground(
    this,
    liveUpdateNotificationId(matchId),
    notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
)

/**
 * Tells `bouncer-match-sync` someone is following these matches, so their `match-live-detail` rows
 * are written every minute. A followed match that nobody had OPEN used to be on the slow tier — one
 * detail write per 15 minutes, now per hour — and the Live Update card, which subscribes to that
 * row, updated no faster. One batched execution per heartbeat for every card.
 */
private suspend fun presenceHeartbeat(followed: () -> List<String>) {
    while (currentCoroutineContext().isActive) {
        val ids = followed()
        if (ids.isNotEmpty()) MatchPresenceReporter.pingAll(ids)
        delay(MatchPresenceReporter.HEARTBEAT_MS)
    }
}
