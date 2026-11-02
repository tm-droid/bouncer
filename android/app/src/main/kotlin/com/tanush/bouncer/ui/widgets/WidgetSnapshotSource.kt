package com.tanush.bouncer.ui.widgets

import android.content.Context
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.data.remote.AppwriteMatchDetailClient
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.MatchStatus
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.utils.sentryBreadcrumb
import com.tanush.bouncer.viewmodels.alignedTo
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException

/**
 * Builds the one [WidgetSnapshot] every widget renders.
 *
 * Source order is deliberate. The scraped matches list is first: it is what the phone UI shows, it
 * is re-read every few seconds, and unlike the server's `{type}-matches` file it carries both
 * sides, their acronyms and their Cricbuzz team ids. `match-live-detail` is merged on top for the
 * things only it has — the pair at the crease, the bowler, the over so far — and only while it is
 * still being written. The server list is the last resort, and it can name the sides but not score
 * them.
 */
object WidgetSnapshotSource {

    /**
     * Rebuild the snapshot and push it to every widget store.
     *
     * Returns null when there is no live match to show, having cleared the stores — which is the
     * only circumstance in which anything clears them.
     */
    suspend fun refresh(context: Context): WidgetSnapshot? {
        val match = pickMatch(context)
        if (match == null) {
            val fallback = serverListSnapshot()
            if (fallback == null) {
                WidgetSnapshotStore.clear(context)
                return null
            }
            WidgetSnapshotStore.write(context, fallback)
            return fallback
        }
        val snapshot = withLiveDetail(snapshotOf(match), match)
        WidgetSnapshotStore.write(context, snapshot)
        return snapshot
    }

    /**
     * The match the widgets are about: a followed one if any is live, else the first live match.
     *
     * Followed first because a user who subscribed to a match has said which one they care about,
     * and the widget has room for exactly one.
     */
    private suspend fun pickMatch(context: Context): CricketMatch? {
        val live = MatchCacheService.getMatches(MatchCacheService.LIVE)
            ?: MatchCacheService.getOfflineMatches(MatchCacheService.LIVE)
            ?: return null
        MatchSubscriptionManager.initialize(context)
        val followed = MatchSubscriptionManager.subscriptions.value
        return live.firstOrNull { it.id in followed && it.isLive }
            ?: live.firstOrNull { it.isLive }
            ?: live.firstOrNull()
    }

    /** The list entry alone — correct for both sides, and complete except for the crease. */
    internal fun snapshotOf(match: CricketMatch): WidgetSnapshot {
        val team1 = match.teams.getOrNull(0)
        val team2 = match.teams.getOrNull(1)
        return WidgetSnapshot(
            matchId = match.id,
            team1Name = team1?.name.orEmpty(),
            team1Score = team1?.score.orEmpty(),
            team1Overs = team1?.overs.orEmpty(),
            team2Name = team2?.name.orEmpty(),
            team2Score = team2?.score.orEmpty(),
            team2Overs = team2?.overs.orEmpty(),
            team1Abbr = abbr(team1),
            team2Abbr = abbr(team2),
            statusText = match.currentStatus.takeIf { it.isNotBlank() } ?: match.status,
            matchType = match.matchType,
            // Classified, never asserted: the worker this replaces wrote `true` for whatever match
            // happened to be first in the server's live list, including a finished one.
            isLive = match.isLive,
            isComplete = match.isCompleted,
            winProbability = match.winProbabilityFraction,
            recentBalls = match.recentBalls?.joinToString(",")?.takeIf { it.isNotBlank() },
            battingPair = pairOf(match.scrapedBatsmen.orEmpty()),
            currentBowler = bowlerOf(match.currentBowler),
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    /**
     * Merge the live-detail row over the list entry.
     *
     * Skipped for a finished match and for a row that has gone quiet: that row stops being written
     * the instant its match leaves `live-matches.json`, so merging it over a list that has the
     * result is the staleness `eb91dcd` fixed on the match-detail screen, on a different surface.
     *
     * Aligned by team id through [alignedTo] before anything is read out of it. The payload's
     * `team1` slot is the side that batted FIRST, which is not `teams[0]`, and copying its score
     * into slot one — which is what `ScorePollingWorker` did — attributes it to whichever side the
     * list happened to list first.
     */
    private suspend fun withLiveDetail(base: WidgetSnapshot, match: CricketMatch): WidgetSnapshot {
        if (match.isCompleted) return base
        val snapshot = try {
            AppwriteMatchDetailClient.getForMatch(match.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sentryBreadcrumb("widget", "Live detail fetch failed: ${e.message}", SentryLevel.WARNING)
            null
        } ?: return base
        if (!snapshot.isFresh()) return base
        return withDetail(base, snapshot.payload, match.teams)
    }

    /**
     * A snapshot from the server's `live-matches` file, which names the sides and nothing else.
     *
     * Reached only when the scraped cache has never been filled — a widget added before the app
     * was first opened, or a cold process after the cache expired. It carries no scores, and
     * writing blanks is the honest answer: the previous worker filled `team1_score` with the
     * innings being batted and left `team2_score` at whatever was there before.
     */
    private suspend fun serverListSnapshot(): WidgetSnapshot? {
        val summary = CricketRepository.getMatches("live").getOrNull()?.matches?.firstOrNull() ?: return null
        val team1 = summary.teams.getOrNull(0)
        val team2 = summary.teams.getOrNull(1)
        // The file used to carry `{id, title, status}` only, so the names had to be cut out of the
        // title and there were no scores at all. Files written by an older deployment still arrive
        // in that shape, which is what the title split is for now — a fallback, not the path.
        val (fromTitle1, fromTitle2) = splitTitle(summary.title)
        val name1 = team1?.name?.takeIf { it.isNotBlank() } ?: fromTitle1
        val name2 = team2?.name?.takeIf { it.isNotBlank() } ?: fromTitle2
        val classified = MatchStatus.from("${summary.state} ${summary.status}")
        return WidgetSnapshot(
            matchId = summary.id,
            team1Name = name1,
            team1Score = team1?.score.orEmpty(),
            team1Overs = team1?.overs.orEmpty(),
            team2Name = name2,
            team2Score = team2?.score.orEmpty(),
            team2Overs = team2?.overs.orEmpty(),
            team1Abbr = name1,
            team2Abbr = name2,
            statusText = summary.status,
            matchType = summary.matchFormat,
            isLive = classified == MatchStatus.Live,
            isComplete = classified == MatchStatus.Completed,
            winProbability = null,
            recentBalls = null,
            battingPair = null,
            currentBowler = null,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    private fun abbr(team: CricketMatch.Team?): String =
        team?.acronym?.takeIf { it.isNotBlank() } ?: team?.name.orEmpty()

    /** `"India vs Australia, 3rd ODI"` -> `"India"` / `"Australia"`. */
    internal fun splitTitle(title: String): Pair<String, String> {
        val separator = TITLE_SEPARATORS.firstOrNull { title.contains(it, ignoreCase = true) }
            ?: return Pair(title.trim(), "")
        val before = title.substringBefore(separator).trim()
        val after = title.substringAfter(separator)
        return Pair(before, after.substringBefore(",").trim())
    }

    private val TITLE_SEPARATORS = listOf(" vs ", " v ")
}

/**
 * Merge a live-detail payload over a list-derived snapshot.
 *
 * Aligned by team id through [alignedTo] before anything is read out of it. The payload's `team1`
 * slot is the side that batted FIRST, which is not `teams[0]`, and copying its score into slot one
 * — which is what `ScorePollingWorker` did — attributes it to whichever side the list happened to
 * name first. Every field falls back to the snapshot's own value: the payload adds what only it
 * has and takes nothing away.
 *
 * The caller decides whether the payload is worth merging at all — a finished match and a row that
 * has stopped being written both skip this entirely, because that row freezes the moment its match
 * leaves `live-matches.json`.
 */
internal fun withDetail(
    base: WidgetSnapshot,
    payload: MatchLiveDetailPayload,
    teams: List<CricketMatch.Team>,
    nowMs: Long = System.currentTimeMillis(),
): WidgetSnapshot {
    val p = payload.alignedTo(teams)
    return base.copy(
        team1Score = orKeep(p.team1Score, base.team1Score),
        team2Score = orKeep(p.team2Score, base.team2Score),
        team1Overs = orKeep(p.team1Overs, base.team1Overs),
        team2Overs = orKeep(p.team2Overs, base.team2Overs),
        statusText = orKeep(p.status, base.statusText),
        winProbability = p.winProbabilityTeam1?.toFloat() ?: base.winProbability,
        recentBalls = orKeep(p.recentBalls.joinToString(","), base.recentBalls.orEmpty()).ifBlank { null },
        battingPair = pairOf(p.batsmen) ?: base.battingPair,
        currentBowler = bowlerOf(p.bowler) ?: base.currentBowler,
        updatedAtMs = nowMs,
    )
}

/** Both halves or nothing: `BouncerGlanceWidget.parseBattingPair` returns null on one. */
internal fun pairOf(batsmen: List<ScrapedBatsman>): String? {
    val striker = batsmen.firstOrNull { it.isStriker } ?: batsmen.getOrNull(0) ?: return null
    val nonStriker = batsmen.firstOrNull { !it.isStriker } ?: batsmen.getOrNull(1) ?: return null
    if (striker === nonStriker) return null
    return "${striker.name}|${striker.runs}|${striker.balls};${nonStriker.name}|${nonStriker.runs}|${nonStriker.balls}"
}

/** The payload's value when it says something, else the list's. Blank is "no value". */
private fun orKeep(candidate: String?, fallback: String): String =
    candidate?.takeIf { it.isNotBlank() } ?: fallback

/** `"name|wickets|runs|overs"` — the field order `BouncerGlanceWidget.parseBowler` expects. */
private fun bowlerOf(bowler: com.tanush.bouncer.models.ScrapedBowler?): String? =
    bowler?.let { "${it.name}|${it.wickets}|${it.runs}|${it.overs}" }
