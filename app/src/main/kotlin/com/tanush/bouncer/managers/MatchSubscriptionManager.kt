package com.tanush.bouncer.managers

import com.tanush.bouncer.data.AppJson
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import com.tanush.bouncer.services.LiveUpdateService
import com.tanush.bouncer.utils.PrefsRegistry
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * What the user follows, per match, in two independent parts:
 *
 * - [subscriptions] — matches whose Live Update card is on (the ongoing score notification).
 * - [alertPrefs] — which pushes a match sends (wicket, boundary, milestone, start/result), each
 *   delivered through the match's FCM topic.
 *
 * The bell shows filled when either is on; tapping it opens the sheet that sets both.
 */
object MatchSubscriptionManager {
    private const val TAG = "MatchSubscriptionManager"
    private val PREFS_NAME = PrefsRegistry.PREFS_MATCH_SUBSCRIPTIONS
    private val KEY_SUBSCRIBED = PrefsRegistry.MatchSubscriptions.SUBSCRIBED_IDS
    private val KEY_ALERTS = PrefsRegistry.MatchSubscriptions.ALERT_PREFS

    /** Replaced in tests. */
    internal var topics: MatchTopicSubscriber = FirebaseTopics

    private lateinit var prefs: SharedPreferences
    private var appContext: Context? = null
    @Volatile private var initialized = false

    // The activity asked for the Live Update before the off-thread initialize() had finished.
    private var resumePending = false

    private val _subscriptions = MutableStateFlow<Set<String>>(emptySet())
    val subscriptions: StateFlow<Set<String>> = _subscriptions.asStateFlow()

    private val _alertPrefs = MutableStateFlow<Map<String, MatchAlertPrefs>>(emptyMap())
    val alertPrefs: StateFlow<Map<String, MatchAlertPrefs>> = _alertPrefs.asStateFlow()

    // Mutators below carry @Synchronized. They are read-modify-write across the StateFlow and
    // prefs together, so the disk write has to sit inside the same critical section —
    // `MutableStateFlow.update {}` alone would let two concurrent callers persist in the opposite
    // order to the one they published.

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        try {
            appContext = context.applicationContext
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _subscriptions.value = load()
            _alertPrefs.value = loadAlerts()
            initialized = true
            Log.d(TAG, "Initialized with ${_subscriptions.value.size} subscriptions")
            joinTopicsAfterRestore(context, _alertPrefs.value, topics)
            if (resumePending) {
                resumePending = false
                resumeLiveUpdates(context)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    @Synchronized
    fun subscribe(matchId: String) {
        if (!initialized) return
        try {
            val current = _subscriptions.value.toMutableSet()
            if (current.add(matchId)) {
                save(current)
                Log.d(TAG, "Subscribed to $matchId (total: ${current.size})")
                // The service decides for itself which subscriptions are actually live and stops
                // when none are, so this only nudges it. A no-op in the background, where the
                // platform refuses a foreground-service start; the next foreground entry starts it.
                appContext?.let { LiveUpdateService.start(it) }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    @Synchronized
    fun unsubscribe(matchId: String) {
        if (!initialized) return
        try {
            val current = _subscriptions.value.toMutableSet()
            if (current.remove(matchId)) {
                save(current)
                Log.d(TAG, "Unsubscribed from $matchId (total: ${current.size})")
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Sets which pushes [matchId] sends, subscribing to or leaving its topic as that changes. */
    @Synchronized
    fun setAlerts(matchId: String, prefs: MatchAlertPrefs) {
        if (!initialized) return
        try {
            val before = alertsFor(matchId)
            val next = _alertPrefs.value.toMutableMap()
            if (prefs.anyOn) next[matchId] = prefs else next.remove(matchId)
            saveAlerts(next)
            if (prefs.anyOn && !before.anyOn) topics.subscribe(matchTopic(matchId))
            if (!prefs.anyOn && before.anyOn) topics.unsubscribe(matchTopic(matchId))
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    fun isSubscribed(matchId: String): Boolean {
        if (!initialized) return false
        return _subscriptions.value.contains(matchId)
    }

    private fun save(set: Set<String>) {
        // Persist before publishing so the two never disagree in the order callers observe.
        // `apply()` is asynchronous, so this is an ordering guarantee, not a durability one — a
        // process kill in the next few milliseconds can still lose the write. DataStore is the
        // real fix; commit() would only trade the loss for a main-thread disk write.
        prefs.edit { putString(KEY_SUBSCRIBED, AppJson.encodeToString(set.toList())) }
        _subscriptions.value = set
    }

    /** Start tracking whatever is already subscribed. Called once the app is in the foreground. */
    @Synchronized
    fun resumeLiveUpdates(context: Context) {
        if (!initialized) {
            // BouncerApp initialises this on Dispatchers.IO, so the activity's onCreate can get
            // here first. Returning then skipped the followed-match card for the whole launch.
            resumePending = true
            return
        }
        if (_subscriptions.value.isEmpty()) return
        LiveUpdateService.start(context.applicationContext)
    }

    private fun saveAlerts(map: Map<String, MatchAlertPrefs>) {
        prefs.edit { putString(KEY_ALERTS, AppJson.encodeToString(map)) }
        _alertPrefs.value = map
    }

    /**
     * Before alerts existed, following a match meant only the Live Update card. The first launch
     * after they shipped has no alert key at all, and gives every match already followed the
     * default alerts — following a match is what the user did, and the alerts are what they
     * expected following to mean. The key's presence is the "migrated" marker.
     */
    private fun loadAlerts(): Map<String, MatchAlertPrefs> {
        val json = prefs.getString(KEY_ALERTS, null)
        if (json == null) {
            val migrated = _subscriptions.value.associateWith { MatchAlertPrefs.DEFAULT }
            prefs.edit { putString(KEY_ALERTS, AppJson.encodeToString(migrated)) }
            migrated.keys.forEach { topics.subscribe(matchTopic(it)) }
            return migrated
        }
        return try {
            AppJson.decodeFromString<Map<String, MatchAlertPrefs>>(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse alert prefs JSON", e)
            emptyMap()
        }
    }

    private fun load(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        val json = prefs.getString(KEY_SUBSCRIBED, null) ?: return emptySet()
        return try {
            AppJson.decodeFromString<List<String>>(json).toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscriptions JSON", e)
            emptySet()
        }
    }
}

fun MatchSubscriptionManager.alertsFor(matchId: String): MatchAlertPrefs =
    alertPrefs.value[matchId] ?: MatchAlertPrefs.NONE

private object FirebaseTopics : MatchTopicSubscriber {
    private const val TAG = "MatchTopics"

    // FCM queues topic operations while offline and retries them itself, so a failure here is
    // one that will not heal — worth a Sentry event, since it silently means "no alerts".
    override fun subscribe(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic).addOnFailureListener {
            Log.w(TAG, "subscribe $topic failed", it)
            Sentry.captureException(it)
        }
    }

    override fun unsubscribe(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).addOnFailureListener {
            Log.w(TAG, "unsubscribe $topic failed", it)
        }
    }
}

private const val TOPICS_JOINED_MARKER = "match_topics_joined"

/**
 * Joins the topic of every match whose alerts are on, once per install.
 *
 * The alert choices are in a preferences file that Android backup restores onto a new phone; the
 * topic memberships belong to the old phone's FCM registration and do not come with it. So after a
 * restore the Follow sheet showed alerts as on and none arrived until the user switched them off
 * and on. The marker is in the no-backup directory, which is exactly the state that should not
 * travel. Subscribing to a topic already joined does nothing.
 */
private fun joinTopicsAfterRestore(context: Context, alerts: Map<String, MatchAlertPrefs>, topics: MatchTopicSubscriber) {
    try {
        val marker = File(context.noBackupFilesDir, TOPICS_JOINED_MARKER)
        if (marker.exists()) return
        alerts.filterValues { it.anyOn }.keys.forEach { topics.subscribe(matchTopic(it)) }
        marker.createNewFile()
    } catch (e: Exception) {
        // The alerts still read as on; this is reported so a failure here is not silent.
        Sentry.captureException(e)
    }
}
