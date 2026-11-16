package com.tanush.bouncer.ui.widgets

import android.content.Context
import androidx.core.content.edit
import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.serialization.Serializable

/**
 * Everything the widgets render, as one value.
 *
 * Three stores used to hold this fact — `widget_cache` for the RemoteViews widget, and one
 * `SharedPreferences` file each for the Glance home and lock-screen widgets — and three writers
 * disagreed about what went in them. `ScorePollingWorker` wrote the innings *being batted* into
 * `team1_score` regardless of which side was batting, never wrote `team2_score` at all, took the
 * team names from a free-text title and hardcoded `is_live = true`. `LiveMatchWidgetProvider`
 * wrote both sides properly from a `CricketMatch`, but its only caller passed `match = null`,
 * which took the clear-everything branch — so an FCM `score_update`, the freshest signal the app
 * has, **blanked** every widget instead of updating it.
 *
 * One snapshot, one writer. [WidgetSnapshotStore] is the only thing that writes any widget store,
 * and it projects this value into the legacy keys each renderer already reads, so the rendering
 * code is untouched and cannot drift from the snapshot.
 *
 * Both sides are always present. A blank score is written as a blank, never as the other side's —
 * "no number yet" and "this is the number" must stay distinguishable.
 */
@Serializable
data class WidgetSnapshot(
    val matchId: String,
    val team1Name: String,
    val team1Score: String,
    val team1Overs: String,
    val team2Name: String,
    val team2Score: String,
    val team2Overs: String,
    /** Short forms for the lock screen, which has room for three characters and no more. */
    val team1Abbr: String,
    val team2Abbr: String,
    val statusText: String,
    val matchType: String,
    /**
     * From [com.tanush.bouncer.models.MatchStatusClassifier], never a literal.
     *
     * `ScorePollingWorker` wrote `true` unconditionally, so a widget showing a finished match still
     * carried the live treatment until something else happened to overwrite it.
     */
    val isLive: Boolean,
    val isComplete: Boolean,
    /** Team 1's chance, 0..1, or null when the server has no number. Null is not 0.5. */
    val winProbability: Float?,
    /** Comma-separated glyphs, in the shape `BouncerGlanceWidget` already parses. */
    val recentBalls: String?,
    /** `"name|runs|balls;name|runs|balls"` — both halves or nothing, as the parser returns null on one. */
    val battingPair: String?,
    /** `"name|wickets|runs|overs"`. */
    val currentBowler: String?,
    val updatedAtMs: Long,
)

/**
 * The one writer of every widget store, and the only reader of the snapshot.
 *
 * Holds the snapshot as JSON under a single key so a partial update ([applyPush]) is a
 * read-modify-write of one value rather than a scattering of independent `putString`s, then
 * projects it onto the legacy keys the three renderers read.
 */
object WidgetSnapshotStore {

    /** The RemoteViews widget's store, and where the snapshot itself lives. */
    internal const val PREFS = "widget_cache"
    private const val LOCK_ABBR_CHARS = 3
    private const val KEY_SNAPSHOT = "snapshot_json"

    fun read(context: Context): WidgetSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { AppJson.decodeFromString<WidgetSnapshot>(raw) }.getOrNull()
    }

    /** Persist [snapshot] and push it into every widget store. */
    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_SNAPSHOT, AppJson.encodeToString(WidgetSnapshot.serializer(), snapshot))
            putString("team1_name", snapshot.team1Name)
            putString("team2_name", snapshot.team2Name)
            putString("team1_score", snapshot.team1Score)
            putString("team2_score", snapshot.team2Score)
            putString("match_status", snapshot.statusText)
            putString("match_type", snapshot.matchType)
            putBoolean("is_live", snapshot.isLive)
            putString("recent_balls", snapshot.recentBalls)
            putString("batting_pair", snapshot.battingPair)
            putString("current_bowler", snapshot.currentBowler)
            // 0.5 is the renderers' own "no bar to draw" default and the one value they cannot
            // distinguish from a real even match; the snapshot keeps the null.
            putFloat("win_probability", snapshot.winProbability ?: 0.5f)
            putLong("last_update", snapshot.updatedAtMs)
            apply()
        }
        context.getSharedPreferences(BouncerGlanceWidget.PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("match_id", snapshot.matchId)
            putString("team1_name", snapshot.team1Abbr)
            putString("team1_score", snapshot.team1Score)
            putString("team2_name", snapshot.team2Abbr)
            putString("team2_score", snapshot.team2Score)
            putString("match_status", snapshot.statusText)
            putBoolean("is_live", snapshot.isLive)
            putFloat("win_prob", snapshot.winProbability ?: 0.5f)
            putString("recent_balls", snapshot.recentBalls)
            putString("batting_pair", snapshot.battingPair)
            putString("current_bowler", snapshot.currentBowler)
            putLong("last_update", snapshot.updatedAtMs)
            apply()
        }
        context.getSharedPreferences(BouncerLockScreenWidget.PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            // The lock screen has room for three characters; a curated acronym is already that
            // short, and a full name is cut to the same width the old writer used.
            putString("team1_abbr", snapshot.team1Abbr.take(LOCK_ABBR_CHARS).uppercase())
            putString("team1_score", snapshot.team1Score)
            putString("team2_abbr", snapshot.team2Abbr.take(LOCK_ABBR_CHARS).uppercase())
            putString("team2_score", snapshot.team2Score)
            putBoolean("is_live", snapshot.isLive)
            putLong("last_update", snapshot.updatedAtMs)
            apply()
        }
    }

    /**
     * Fold an FCM `score_update` into the snapshot.
     *
     * The push carries `team1`, `team2` and `score` and used to reach only the tray notification;
     * the widget half of the handler called `updateAllWidgets(match = null)`, which cleared every
     * store. A push is the fastest signal the app receives, so it updates what it can and leaves
     * the rest of the snapshot alone.
     *
     * [score] is one side's line, and the push does not say whose. It is applied only when it can
     * be attributed — the push names both sides, so the side whose name matches and whose score
     * differs is the one that just changed. When that is ambiguous the scores are left as they
     * are: a number against the wrong team is worse than a number a minute old.
     */
    fun applyPush(context: Context, matchId: String, team1: String, team2: String, score: String) {
        val current = read(context) ?: return
        val updated = applyPushTo(current, matchId, team1, team2, score)
        if (updated == null) {
            sentryBreadcrumb(
                "widget",
                "score_update push not applied to the snapshot",
                SentryLevel.INFO,
                mapOf("matchId" to matchId),
            )
            return
        }
        write(context, updated)
    }

    /**
     * Drop every widget store. Only for "there is genuinely no live match".
     *
     * Written out one file at a time rather than looped: `SharedPreferencesFileInventoryTest`
     * resolves the file-name argument of every `getSharedPreferences` call, and a loop variable
     * makes this call site invisible to it. That guard exists because the same key living in two
     * preference files is silent at runtime, which is half of what went wrong here.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
        context.getSharedPreferences(BouncerGlanceWidget.PREF_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
        context.getSharedPreferences(BouncerLockScreenWidget.PREF_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }
}

/**
 * [WidgetSnapshotStore.applyPush]'s rule, without the storage, so it can be tested.
 *
 * Returns null when the push cannot be attributed, and null means "change nothing". A push about
 * another match, with no score, or naming neither cached side is ignored outright: a number
 * against the wrong team reads as a real score and is worse than a number a minute old.
 */
internal fun applyPushTo(
    current: WidgetSnapshot,
    matchId: String,
    team1: String,
    team2: String,
    score: String,
    nowMs: Long = System.currentTimeMillis(),
): WidgetSnapshot? {
    if (current.matchId != matchId) return null
    val line = score.trim()
    if (line.isEmpty()) return null
    return when {
        team1.isNotBlank() && team1.equals(current.team1Name, ignoreCase = true) ->
            current.copy(team1Score = line, updatedAtMs = nowMs)
        team2.isNotBlank() && team2.equals(current.team2Name, ignoreCase = true) ->
            current.copy(team2Score = line, updatedAtMs = nowMs)
        else -> null
    }
}
