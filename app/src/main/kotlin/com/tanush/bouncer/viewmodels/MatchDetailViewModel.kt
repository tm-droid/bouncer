package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.models.StatusSignal
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.WinProbFactor
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CricBuzzMatchRepository
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.history.HistoryRepository
import com.tanush.bouncer.data.history.PastFeedSource
import com.tanush.bouncer.data.history.PastMatchSource
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveDetailSnapshot
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.ProductionLiveDetailSource
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.data.remote.H2HRepository
import com.tanush.bouncer.data.remote.RealtimeLifecycleController
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.data.remote.toEvent
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.network.isReportableScrapeFailure
import com.tanush.bouncer.services.MatchPresenceReporter
import com.tanush.bouncer.ui.components.BatsmanMilestoneInfo
import com.tanush.bouncer.ui.components.MilestoneType
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.utils.firstAnswer
import com.tanush.bouncer.utils.sentryBreadcrumb
import com.tanush.bouncer.utils.sentryCaptureThrottled
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MatchDetailViewModel(
    private var match: CricketMatch,
    private val matchRepository: MatchRepository = CricBuzzMatchRepository(),
    private val liveSource: LiveMatchDetailSource = ProductionLiveDetailSource,
    private val pastSource: PastMatchSource = HistoryRepository,
) : ViewModel() {

    data class UiState(
        val batsmen: List<ScrapedBatsman> = emptyList(),
        val bowler: ScrapedBowler? = null,
        val recentBalls: List<String> = emptyList(),
        /** The over [recentBalls] describes; see [MatchLiveDetailPayload.recentBallsOver]. */
        val recentBallsOver: Int? = null,
        val scorecard: Scorecard? = null,
        val scorecardLoading: Boolean = true,
        val commentary: List<CommentaryEntry> = emptyList(),
        val commentaryLoading: Boolean = true,
        /** True while [loadOlderCommentary] is fetching the next page back. */
        val olderCommentaryLoading: Boolean = false,
        /**
         * False once a page back comes up empty, so the tab stops offering to fetch nothing.
         *
         * Starts true even for a match with no commentary at all: the first page is the initial
         * load, and one empty page is what settles it.
         */
        val hasOlderCommentary: Boolean = true,
        /**
         * Set when [commentary] is a finished match's whole feed from a site that keeps it
         * (`HistoryRepository.pastFeed`) rather than the table: a match past the 14-day prune, or
         * one match-sync never stored. The tab filters it locally, since the table holds no events
         * for it, and labels Cricsheet's. Null for the table's feed.
         */
        val pastFeedSource: PastFeedSource? = null,
        val liveTeam1Score: String = "",
        val liveTeam2Score: String = "",
        val liveTeam1Overs: String = "",
        val liveTeam2Overs: String = "",
        val liveCurrentRunRate: String? = null,
        val liveRequiredRunRate: String? = null,
        /** Latest server status line ("... won by ...", innings break, etc.); drives the header. */
        val liveStatus: String? = null,
        /**
         * Win probability for teams[0] as a percentage, computed by bouncer-match-sync. Null when
         * the server did not score this match.
         *
         * Two sources, both from the same run: the detail payload, and the aggregate `live-winprob`
         * file via [WinProbabilityStore]. The payload wins when it carries a value; the file covers
         * the window before this match earns a fast-tier detail row, which is up to 45 minutes on a
         * screen reached by deep link or a cold start.
         */
        val liveWinProbability: Double? = null,
        /**
         * Draw probability as a percentage, for the one format that has one.
         *
         * Null for every limited-overs match, and null is not zero: a T20 cannot be drawn, while a
         * Test on 8% draw is a real and different statement. The renderer branches on null to
         * choose between a two- and a three-segment bar.
         */
        val liveDrawProbability: Double? = null,
        /** True when [liveWinProbability] is a pre-match prior rather than a live reading. */
        val liveWinProbabilityIsPrematch: Boolean = false,
        val matchFlow: MatchFlowData? = null,
        val preMatchData: PreMatchData? = null,
        val probabilityHistory: List<WinProbabilityPoint> = emptyList(),
        val batsmanMilestone: BatsmanMilestoneInfo? = null,
        val headToHead: H2HPair? = null,
        /** Non-null when the initial load produced no live data at all (network-level failure). */
        val loadError: String? = null,
        /** Non-null when the commentary feed failed and no lines are available. */
        val commentaryError: String? = null,
    ) {
        /**
         * Fold one live-detail payload into the screen's state.
         *
         * [matchIsOver] is the whole point of this function. `match-live-detail` is written only while a
         * match sits in `live-matches.json`, and only on match-sync's detail tier — once per
         * `SLOW_TIER_INTERVAL_MS` (15 minutes) for a match nobody has open. The moment the match ends it
         * leaves that list and **nothing writes, finalises or prunes the row again**, so what is left is a
         * snapshot from up to a quarter of an hour before the last ball, kept for good. The payload carries
         * no timestamp and the client never reads `updated_at`, so the staleness is not visible field by
         * field — while the matches list, re-scraped from Cricbuzz every 60s, has the real result.
         *
         * That is the defect this exists to close: the Recent row and the detail header of the same match
         * showed different scores, and the header was the wrong one, because every non-null payload field
         * overwrote the list-derived seed through [pick] with no status gate.
         *
         * So for a finished match the payload contributes nothing — not the score, the overs, the status,
         * the run rates, the batting pair, the bowler, the recent balls or the probability — with one
         * exception: a scorecard, and only when the state has none. Rows written before the scorecard moved
         * to its own table still carry one, and an old card beats an empty tab. `loadError` is deliberately
         * NOT cleared on that branch: a stale payload is not evidence that anything loaded.
         */
        internal fun merge(p: MatchLiveDetailPayload, matchIsOver: Boolean): UiState {
            if (matchIsOver) {
                val legacyCard = p.scorecard?.takeIf { it.innings.isNotEmpty() && scorecard == null }
                    ?: return this
                return this.copy(scorecard = legacyCard)
            }
            return this.copy(
                loadError = null,
                batsmen = p.batsmen.ifEmpty { this.batsmen },
                bowler = pick(p.bowler, this.bowler),
                recentBalls = p.recentBalls.ifEmpty { this.recentBalls },
                // Moves with the glyphs or not at all: a number left over from the previous payload
                // would label this one's over wrongly, which is the defect the field exists to fix.
                recentBallsOver = if (p.recentBalls.isEmpty()) this.recentBallsOver else p.recentBallsOver,
                scorecard = pick(p.scorecard?.takeIf { it.innings.isNotEmpty() }, this.scorecard),
                liveTeam1Score = pick(p.team1Score, this.liveTeam1Score),
                liveTeam2Score = pick(p.team2Score, this.liveTeam2Score),
                liveTeam1Overs = pick(p.team1Overs, this.liveTeam1Overs),
                liveTeam2Overs = pick(p.team2Overs, this.liveTeam2Overs),
                liveCurrentRunRate = pick(p.currentRunRate, this.liveCurrentRunRate),
                liveRequiredRunRate = pick(p.requiredRunRate, this.liveRequiredRunRate),
                liveStatus = pick(p.status?.takeIf { it.isNotBlank() }, this.liveStatus),
                // Server sends 0..1; the UI works in percent. Keep the previous value when this
                // update carries none, so the bar does not blink out on a payload that omits it.
                // Routed through `pick` rather than its own elvis for the same reason every other
                // field here is: two more branches in this function trips detekt's complexity cap,
                // and the file is already at the TooManyFunctions threshold so a helper cannot pay
                // for itself.
                liveWinProbability = pick(p.winProbabilityTeam1?.times(PERCENT_SCALE), this.liveWinProbability),
                liveDrawProbability = pick(p.winProbabilityDraw?.times(PERCENT_SCALE), this.liveDrawProbability),
                // A detail payload exists only for a match that is being synced live, so its
                // arrival is itself the evidence that any earlier pre-match prior is superseded.
                liveWinProbabilityIsPrematch =
                    if (p.winProbabilityTeam1 != null) false else this.liveWinProbabilityIsPrematch,
                // The swing chart's series, accumulated on the device. The server keeps only the
                // current probability — `live-winprob` and the detail payload are both snapshots,
                // and nothing stores a history — so a point is recorded each time the probability
                // moves. Session-scoped by design: it is the swing since this screen opened, not
                // the match's whole arc.
                probabilityHistory = appendProbabilityPoint(this, p),
            )
        }

    }

    private val firedMilestones = mutableSetOf<String>()
    private var espnMatchUrl: String? = null

    /** `updated_at` of the newest detail row applied, or null when one arrived without a usable age. */
    private var detailUpdatedAtMs: Long? = null

    /** Whether any detail payload has ever been applied. Before the first one the list is all there is. */
    private var hasAppliedDetail = false

    private val _uiState = MutableStateFlow(
        UiState(
            liveTeam1Score = match.teams.getOrNull(0)?.score ?: "",
            liveTeam2Score = match.teams.getOrNull(1)?.score ?: "",
            liveTeam1Overs = match.teams.getOrNull(0)?.overs ?: "",
            liveTeam2Overs = match.teams.getOrNull(1)?.overs ?: "",
            liveCurrentRunRate = match.score?.currentRunRate,
            liveRequiredRunRate = match.score?.requiredRunRate,
            liveStatus = match.currentStatus.ifBlank { null },
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Client-side typewriter reveal of the newest server commentary line. Preserves the
    // cinematic "streaming" feel now that generation happens server-side (no token stream).
    private val _streamingCommentary = MutableStateFlow("")
    val streamingCommentary: StateFlow<String> = _streamingCommentary.asStateFlow()
    private var typewriterJob: Job? = null

    /**
     * How many feed rows the reader has explicitly paid for, i.e. the feed size after the last
     * [loadOlderCommentary]. Realtime truncation never goes below it — see [MAX_FEED_ENTRIES].
     */
    private var pagedFeedFloor = 0

    /** The whole match's wickets and boundaries, fetched when the commentary tab's chip is tapped. */
    val commentaryEvents = CommentaryEventPager(match.id, liveSource, viewModelScope)

    init {
        viewModelScope.launch(bouncerExceptionHandler) { loadInitial() }
        // Per-match subscriptions: collection IS the subscription lifetime, and viewModelScope's
        // cancellation in onCleared() is the release boundary — so leaving the screen drops the
        // Realtime claim without any Compose-side plumbing. No client-side filter any more; the
        // detail stream is scoped to this match's row server-side.
        //
        // Skipped outright for a match the list already calls finished: nothing will ever write
        // that row again (match-sync only syncs matches in `live-matches.json`), so the claim is
        // realtime bandwidth — billed from the same pool as API and Storage — spent on silence.
        // `match.isCompleted` and not `liveStatus`: terminal is sticky, a match never un-completes,
        // and a match that ends while this screen is open was constructed with it false, so it
        // keeps the subscription and receives the payload that reports the result.
        //
        // All three streams are held only while the app is in the foreground (`whileForeground`):
        // this ViewModel outlives a backgrounded activity, and the sockets used to stay open until
        // the process died. On return, detail catches up with one getRow; commentary asks only for
        // rows newer than the newest on screen; the scorecard waits for its next push.
        val foreground = RealtimeLifecycleController.foreground
        if (!match.isCompleted) {
            viewModelScope.launch(bouncerExceptionHandler) {
                whileForeground(
                    foreground,
                    catchUp = { listOfNotNull(liveSource.getForMatch(match.id)) },
                ) { liveSource.observeDetail(match.id) }.collect { applyDetail(it) }
            }
            // Gated like the other two: a table channel carries every commentary write for every
            // live match, and a finished match's feed will never gain another row.
            viewModelScope.launch(bouncerExceptionHandler) {
                whileForeground(
                    foreground,
                    catchUp = {
                        _uiState.value.commentary.mapNotNull { it.sortKey }.maxOrNull()
                            ?.let { newest -> liveSource.getNewerCommentary(match.id, newest) }
                            .orEmpty()
                            .mapNotNull { it.toEvent(match.id) }
                    },
                ) { liveSource.observeCommentary(match.id) }.collect { onNewCommentaryEvent(it) }
            }
        }
        // The scorecard has its own row and its own channel — it was 85-93% of the detail payload
        // and was pushed to every subscriber of that row every minute. Subscribed for the whole
        // screen, not just the Scorecard tab: the Overview's bowler figures, fall-of-wickets phases
        // and innings summary read the same object. Skipped for a finished match for the same
        // reason the detail stream above is: that row froze when the match left the live list.
        if (!match.isCompleted) {
            viewModelScope.launch(bouncerExceptionHandler) {
                whileForeground(foreground) { liveSource.observeScorecard(match.id) }.collect { sc ->
                    val card = sc.scorecard
                    if (card.innings.isNotEmpty()) _uiState.update { it.copy(scorecard = card) }
                }
            }
        }
        // Presence heartbeat: while this match is open and not over, tell the backend a viewer is
        // watching so bouncer-match-sync generates commentary for it. Stops when the ViewModel
        // is cleared (screen left) — viewModelScope cancels the loop and the server-side TTL lapses.
        // Inlined (not its own method) to keep the class under detekt's TooManyFunctions threshold.
        //
        // The gate is deliberately NOT `match.isLive`. That is a frozen snapshot taken from the
        // matches list at construction (the same staleness 5b86664 fixed for the polling loop): a
        // match opened just before the first ball, or opened from a list refreshed while it was
        // still upcoming, reads false forever and never pings — so match-sync logs "0 with viewers",
        // only the 45-minute slow tier writes, and the screen falls back to raw Cricbuzz text.
        // Same rule as the polling loop instead: completed is definitive, anything else pings, and
        // the server's own status line ends it the moment the match is actually over.
        //
        // The win probability is pulled on the same tick, and from the aggregate file rather than
        // the detail payload, because that file is the fresher of the two sources. match-sync
        // writes a detail row only for a match someone is already viewing, or on its 45-minute slow
        // tier — so a screen reached by deep link, search or a cold start can sit on a value up to
        // 45 minutes old while the ping above is still earning this match a place in the fast tier.
        // The aggregate file is rewritten every run for every live match and is a Storage read: at
        // most 60s stale, and no billed DB reads (the quota a 402 takes out app-wide).
        //
        // Riding this loop rather than owning a timer keeps the cadence at 90s, and the store's own
        // 60s TTL no-ops the surplus calls. A payload that carries a probability still wins, since
        // applyDetail overwrites it — and the two cannot disagree, both come from the same run. A
        // null leaves state alone, so an absent number never blanks a bar applyDetail already
        // filled. Inlined for the same reason as the loop itself: detekt's TooManyFunctions.
        viewModelScope.launch(bouncerExceptionHandler) {
            while (isActive && !isMatchOver(match, _uiState.value.liveStatus)) {
                // Suspends while backgrounded: a ping is a billed execution and a refresh a Storage
                // download, and neither can reach a screen nobody is looking at. The ViewModel
                // outlives a backgrounded activity, so without this the loop ran until the process died.
                RealtimeLifecycleController.foreground.first { it }
                // A fixture more than PRESENCE_LEAD_MS away is not synced by match-sync yet, so a
                // ping and a win-probability download would buy nothing.
                if (!shouldPingPresence(match, System.currentTimeMillis())) {
                    delay(PRESENCE_HEARTBEAT_MS)
                    continue
                }
                MatchPresenceReporter.ping(match.id)
                WinProbabilityStore.refresh()
                WinProbabilityStore.probabilityFor(match.id)?.alignedTo(match.teams)?.let { probability ->
                    _uiState.update {
                        it.copy(
                            liveWinProbability = probability.team1Percent,
                            liveDrawProbability = probability.drawPercent ?: it.liveDrawProbability,
                            liveWinProbabilityIsPrematch = probability.isPrematch,
                        )
                    }
                }
                delay(PRESENCE_HEARTBEAT_MS)
            }
        }
    }

    private suspend fun loadInitial() {
        try {
            // Live snapshot (batsmen/bowler/score/scorecard) + commentary feed from Appwrite.
            //
            // Not fetched at all for a finished match: the row froze when the match left the live
            // list, [UiState.merge] would discard every field of it, and the read is billed per row
            // out of the quota a 402 takes out app-wide.
            if (!match.isCompleted) {
                val detail = withTimeoutOrNull(10_000L) { liveSource.getForMatch(match.id) }
                if (detail == null) {
                    sentryBreadcrumb(
                        "match_detail_v2.scorecard",
                        "Appwrite live-detail fetch timed out or returned null",
                        SentryLevel.WARNING,
                    )
                }
                applyDetail(detail)
            }
            // Scorecard: one billed read for first paint, because the detail payload no longer
            // carries it and the row's realtime channel only speaks when match-sync next writes --
            // up to a minute of a blank Overview otherwise. Every update after this arrives free
            // over observeScorecard. The on-device CricBuzz scrape below stays as the last resort
            // for matches match-sync has not written (no live viewer, or not yet started).
            resolveScorecard(
                current = _uiState.value.scorecard,
                matchId = match.id,
                liveSource = liveSource,
                repository = matchRepository,
                matchIsOver = isMatchOver(match, _uiState.value.liveStatus),
                archived = { pastSource.scorecard(match.id) },
            ) { e ->
                sentryBreadcrumb(
                    "match_detail_v2.scorecard_fallback",
                    "On-device scorecard fetch failed: ${e.message}",
                    SentryLevel.WARNING,
                )
                // Offline and a Cricbuzz 5xx (ANDROID-5R: a 504) fall through to the stored card.
                if (isReportableScrapeFailure(e)) Sentry.captureException(e)
            }?.let { sc -> _uiState.update { it.copy(scorecard = sc) } }
            loadCommentaryFeed()
        } finally {
            _uiState.update { st ->
                st.copy(
                    commentaryLoading = false,
                    scorecardLoading = false,
                    // Nothing usable arrived from any source: surface it instead of a blank screen.
                    // Realtime events or a retry clear this.
                    loadError = noLiveDataError(st),
                )
            }
        }

        loadCricinfoData()

        // Head-to-head (Appwrite Storage) + win-probability history.
        AppResult.runCatchingSuspend {
            val t1 = match.teams.getOrNull(0)?.name ?: return@runCatchingSuspend
            val t2 = match.teams.getOrNull(1)?.name ?: return@runCatchingSuspend
            // The file is the T20 record only. On a one-day match or a Test it would be a true
            // record of the wrong format; those read their own from the match's context instead.
            if (!match.matchType.contains("T20", ignoreCase = true)) return@runCatchingSuspend
            H2HRepository.getH2H(t1, t2)?.let { h2h -> _uiState.update { it.copy(headToHead = h2h) } }
        }.onError { sentryBreadcrumb("match_detail_v2.h2h", "H2H fetch failed: ${it.message}", SentryLevel.WARNING) }
    }

    private suspend fun loadCommentaryFeed() {
        // True only when the store answered and holds no rows for this match. A timeout, an
        // offline device or a blocked guard is not that, and reporting them as "the server wrote
        // nothing" is most of what ANDROID-37 was.
        var storeAnsweredEmpty = false
        AppResult.runCatchingSuspend {
            val lines = withTimeoutOrNull(10_000L) { liveSource.firstCommentaryPage(match) }
            storeAnsweredEmpty = lines != null && lines.isEmpty()
            if (!lines.isNullOrEmpty()) _uiState.update { it.copy(commentary = lines, commentaryError = null) }
        }.onError {
            sentryBreadcrumb("match_detail_v2.commentary_load", "Commentary feed load failed: ${it.message}", SentryLevel.WARNING)
            _uiState.update { st ->
                if (st.commentary.isEmpty()) {
                    st.copy(commentaryError = "Commentary feed unreachable — check your connection and retry.")
                } else {
                    st
                }
            }
        }
        if (_uiState.value.commentary.isNotEmpty()) return
        // Before the scrape: the scrape is one page of the match's end and cannot page back, while
        // the archive and Cricsheet have every ball.
        pastSource.feed(match)?.let { past ->
            _uiState.update {
                it.copy(commentary = past.entries, commentaryError = null, pastFeedSource = past.source, hasOlderCommentary = false)
            }
            return
        }
        val scraped = scrapeCommentary(matchRepository, match.id)
        if (scraped.isEmpty()) return
        // This is the raw-Cricbuzz path: unrewritten scraper output rendered verbatim,
        // visually indistinguishable from stored commentary. It is a legitimate last resort
        // but, when the store answered with nothing, it means match-sync stored no rows
        // for a match a user is looking at right now, so that case is reported as
        // degradation. It says nothing about AI coverage: the device never reads
        // `ai_generated`, and a stored raw-text row is a row. A store that could not be
        // read is the device's state and is already reported where the read failed.
        if (storeAnsweredEmpty) {
            sentryCaptureThrottled(
                key = "commentary.raw-fallback",
                message = "Commentary tab fell back to scraping — match-commentary has no rows for this match",
                data = mapOf(
                    "matchId" to match.id,
                    "scrapedLines" to scraped.size
                )
            )
        }
        // No server verdict on this path — the scraper's own words are what the device
        // classifies from, and they still carry Cricbuzz's uppercase FOUR/SIX/OUT.
        _uiState.update { st ->
            st.copy(
                commentary = scraped.map { CommentaryEntry(it, event = null) },
                commentaryError = null,
            )
        }
    }


    // CricInfo one-shots: match flow (DRS/powerplay/impact subs) + pre-match (pitch + team news).
    private suspend fun loadCricinfoData() {
        val url = espnMatchUrl
            ?: matchRepository.findCricinfoMatchUrlAndId(match.matchTitle)?.first?.also { espnMatchUrl = it }
            ?: return
        AppResult.runCatchingSuspend {
            val flow = matchRepository.fetchMatchFlow(url)
            if (flow.drsReviews.isNotEmpty() || flow.powerplays.isNotEmpty() || flow.impactSubs.isNotEmpty()) {
                _uiState.update { it.copy(matchFlow = flow) }
            }
        }.onError { sentryBreadcrumb("match_detail_v2.matchflow", "Match flow fetch failed: ${it.message}", SentryLevel.WARNING) }
        AppResult.runCatchingSuspend {
            val preMatch = matchRepository.fetchPreMatchData(url) ?: return@runCatchingSuspend
            _uiState.update { it.copy(preMatchData = preMatch) }
        }.onError { sentryBreadcrumb("match_detail_v2.prematch", "Pre-match data fetch failed: ${it.message}", SentryLevel.WARNING) }
    }

    /**
     * A newer entry for this match from the matches list.
     *
     * The list is re-scraped roughly every six seconds and is correct at the final ball. The detail
     * row is not: `match-sync` writes it only for a match inside `MAX_MATCHES = 12` of
     * `live-matches.json`, and the state seeded from the list at construction is never blank, so
     * [ui.MatchDetailViewV2]'s `ifBlank` fallback to the list can never fire. A match the server is
     * not writing therefore sat on its open-time score while the row behind it advanced — the
     * inverse of the staleness `eb91dcd` fixed, and on a LIVE match rather than a finished one.
     *
     * Called from composition on every list emission, so it must stay cheap and idempotent:
     * [listLeadsDetail] decides, and when it says no this is one comparison and a field write.
     */
    fun onMatchUpdated(updated: CricketMatch) {
        if (updated.id != match.id) return
        match = updated
        if (!listLeadsDetail(updated, hasAppliedDetail, detailUpdatedAtMs)) return
        _uiState.update { it.seededFrom(updated) }
    }

    /** Clears errors and re-runs the initial load (retry from the error state). */
    fun retryInitialLoad() {
        // The retry re-fetches the first page, so nothing paged-in survives it.
        pagedFeedFloor = 0
        _uiState.update { it.copy(
            loadError = null,
            commentaryError = null,
            scorecardLoading = true,
            commentaryLoading = true,
        )}
        viewModelScope.launch(bouncerExceptionHandler) { loadInitial() }
    }

    // Live snapshot updates (one-shot + Realtime) flow through here. Null fields keep prior values.
    private fun applyDetail(snapshot: LiveDetailSnapshot?) {
        // The server orients both team slots to the first-innings batting side; this screen is in
        // Cricbuzz's listing order. See MatchLiveDetailAlignment.
        val p = snapshot?.payload?.alignedTo(match.teams) ?: return
        // Remembered so [onMatchUpdated] can tell a row match-sync is still writing from one it
        // abandoned. Null (an unreadable `updated_at`) is not treated as old — see
        // [LiveDetailSnapshot] — so it is recorded as "seen, age unknown".
        detailUpdatedAtMs = snapshot.updatedAtMs
        hasAppliedDetail = true
        // Judged from the state BEFORE this payload, so the update that finishes the match still
        // lands and only the re-reads of that frozen row afterwards are ignored. See [UiState.merge].
        val alreadyOver = isMatchOver(match, _uiState.value.liveStatus)
        _uiState.update { st -> st.merge(p, alreadyOver) }
        if (alreadyOver) return
        p.batsmen.forEach { handleBatsmanMilestone(it) }
    }

    // New server commentary line: prepend to the feed (dedup) and typewriter-reveal it.
    private fun onNewCommentaryEvent(ev: CommentaryEvent) {
        var isNew = false
        _uiState.update { st ->
            // Deduped on `sort_key` — the row's identity — and NOT on the prose. Two deliveries
            // reading "Jadeja to Kumara, no run" are an ordinary pair of dot balls, and
            // `match-sync` now stores every one of them rather than a 45-minute sample, so a
            // text-equality test drops real balls out of the feed for looking like each other.
            if (st.commentary.any { it.sortKey != null && it.sortKey == ev.sortKey }) st
            else {
                isNew = true
                val entry = CommentaryEntry(
                    ev.commentary, ev.event, ev.ball, ev.sortKey, ev.runs, ev.extra, ev.innings,
                )
                st.copy(
                    // The ceiling sits ABOVE what the reader explicitly paged in: those rows cost
                    // billed reads, and `hasOlderCommentary` would cheerfully buy them again.
                    commentary = insertBySortKey(st.commentary, entry)
                        .take(pagedFeedFloor + MAX_FEED_ENTRIES),
                    commentaryError = null,
                )
            }
        }
        if (!isNew) return
        typewriterJob?.cancel()
        typewriterJob = viewModelScope.launch(bouncerExceptionHandler) {
            _streamingCommentary.value = ""
            // Word by word, not character by character: an 18 ms per-character emit recomposed the
            // Overview ~55 times a second for the length of every line.
            val words = ev.commentary.split(' ')
            val sb = StringBuilder(ev.commentary.length)
            words.forEachIndexed { index, word ->
                if (index > 0) sb.append(' ')
                sb.append(word)
                _streamingCommentary.value = sb.toString()
                delay(TYPEWRITER_WORD_DELAY_MS)
            }
            delay(TYPEWRITER_HOLD_MS)
            _streamingCommentary.value = ""
        }
    }

    /**
     * Fetch the page of commentary below the oldest line on screen.
     *
     * Reader-driven rather than eager: reads bill per row returned, so a full Test innings is only
     * paid for by someone who scrolls back through it. A page that comes back empty clears
     * [UiState.hasOlderCommentary] and the affordance goes away.
     */
    fun loadOlderCommentary() {
        val st = _uiState.value
        if (st.olderCommentaryLoading || !st.hasOlderCommentary) return
        val oldest = st.commentary.lastOrNull()?.sortKey ?: return
        _uiState.update { it.copy(olderCommentaryLoading = true) }
        viewModelScope.launch(bouncerExceptionHandler) {
            val page = withTimeoutOrNull(COMMENTARY_FETCH_TIMEOUT_MS) {
                liveSource.getOlderCommentary(match.id, oldest)
            }.orEmpty()
            _uiState.update { state ->
                val known = state.commentary.mapNotNull { it.sortKey }.toSet()
                val fresh = page.filter { it.sortKey != null && it.sortKey !in known }
                val grown = state.commentary + fresh
                pagedFeedFloor = grown.size
                state.copy(
                    commentary = grown,
                    olderCommentaryLoading = false,
                    // Judged on the page, not on `fresh`: a page that was entirely already-known
                    // rows still proves there is more behind it.
                    hasOlderCommentary = page.isNotEmpty(),
                )
            }
        }
    }

    private fun handleBatsmanMilestone(b: ScrapedBatsman) {
        val runs = b.runs.toIntOrNull() ?: 0
        val milestoneType = milestoneTypeFor(runs) ?: return
        if (firedMilestones.add("${b.name}_${milestoneType.runs}")) emitBatsmanMilestone(b, runs, milestoneType)
    }

    private fun emitBatsmanMilestone(b: ScrapedBatsman, runs: Int, milestoneType: MilestoneType) {
        _uiState.update { it.copy(batsmanMilestone = BatsmanMilestoneInfo(
            playerName = b.name,
            teamName = match.teams.firstOrNull { t -> t.isBatting }?.name ?: "",
            currentRuns = runs,
            balls = b.balls.toIntOrNull() ?: 0,
            fours = b.fours.toIntOrNull() ?: 0,
            sixes = b.sixes.toIntOrNull() ?: 0,
            strikeRate = b.strikeRate.toFloatOrNull() ?: 0f,
            milestone = milestoneType,
            isNewMilestone = true,
        ))}
        val playerName = b.name
        viewModelScope.launch {
            delay(MILESTONE_BANNER_MS)
            _uiState.update { s ->
                if (s.batsmanMilestone?.playerName == playerName) s.copy(batsmanMilestone = null) else s
            }
        }
    }

    companion object {
        private const val TYPEWRITER_WORD_DELAY_MS = 70L
        private const val TYPEWRITER_HOLD_MS = 1500L
        private const val MILESTONE_BANNER_MS = 15_000L
        // Re-ping well within match-sync's 4-min VIEWER_TTL_MS so ~2 dropped pings still count.
        private const val PRESENCE_HEARTBEAT_MS = 90_000L

        /** Per-type store key — see [HighlightsViewModel.storeKey] for why sharing one collides. */
        fun storeKey(matchId: String): String = "match_detail_$matchId"

        fun factory(
            match: CricketMatch,
            matchRepository: MatchRepository = CricBuzzMatchRepository(),
            liveSource: LiveMatchDetailSource = ProductionLiveDetailSource,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MatchDetailViewModel(match, matchRepository, liveSource) as T
            }
    }
}

// File-level helpers keep the ViewModel's member-function count and per-function
// complexity inside detekt thresholds.
private fun <T> pick(new: T?, old: T): T = new ?: old

private fun milestoneTypeFor(runs: Int): MilestoneType? = when {
    runs >= 200 -> MilestoneType.DOUBLE_HUNDRED
    runs >= 100 -> MilestoneType.HUNDRED
    runs >= 50 -> MilestoneType.FIFTY
    else -> null
}

private fun noLiveDataError(st: MatchDetailViewModel.UiState): String? =
    if (st.scorecard == null && st.batsmen.isEmpty() && st.commentary.isEmpty()) {
        "Rain delay — couldn't fetch live match data. Check your connection and retry."
    } else {
        null
    }

/**
 * Commentary scraped on-device, for matches Appwrite has none for. Empty on any failure.
 *
 * `bouncer-match-sync` writes commentary only for a live match with a viewer heartbeat, and a
 * finished match leaves the live list for good — so a match nobody had open while it was played
 * has no rows, ever, and no scheduled path can backfill it afterwards. Both Hundred games of
 * 2026-07-30 are in exactly that state. Raw Cricbuzz text beats a blank tab.
 *
 * Two bounds, both measured on 2026-07-31 rather than assumed:
 *  - **Depth, not age.** Cricbuzz still served commentary for a match 36 days old, so this does
 *    not expire in any practical window. But its page carries only the newest slice — 6 to 20
 *    items — and no paginated or archive source exists (`/cricket-full-commentary/{id}` 404s), so
 *    this shows the tail of an innings, never all of it.
 *  - **No AI rewrite, no write-back.** The lines are Cricbuzz's own and they stay on the device;
 *    reaching for the LLM here would spend a provider call per delivery on a finished match.
 *
 * Returning empty is not an error. `CommentaryTabV2` already renders "No more commentary
 * available" for a completed match, which is the honest message — `commentaryError` would claim a
 * connectivity problem that does not exist.
 */
/**
 * Whether the match is definitively over, so the presence heartbeat should stop.
 *
 * [liveStatus] is the server's own status line, which arrives via Realtime and is authoritative;
 * `match.isCompleted` only covers what the matches list already knew at construction. Anything
 * unclassifiable counts as not-over on purpose — over-pinging costs one function execution per
 * 90 seconds, under-pinging costs the match its entire generated commentary feed.
 *
 * Top-level rather than a method so the ViewModel stays under detekt's TooManyFunctions
 * threshold, which is also why the heartbeat loop itself is inlined in `init`.
 */
private fun isMatchOver(match: CricketMatch, liveStatus: String?): Boolean =
    match.isCompleted || MatchStatusClassifier.classify(liveStatus) == StatusSignal.TERMINAL

/**
 * Scorecard for first paint: Appwrite's row, then the on-device scrape as a last resort.
 *
 * Returns null when neither source has one, and **never returns an empty `Scorecard()`**. Both
 * callers gate on `scorecard == null`, so storing an empty one closes the retry gates while the tab
 * still renders "No scorecard available" — the on-device fallback used to do exactly that.
 */
internal suspend fun resolveScorecard(
    current: Scorecard?,
    matchId: String,
    liveSource: LiveMatchDetailSource,
    repository: MatchRepository,
    matchIsOver: Boolean,
    archived: suspend () -> Scorecard? = { null },
    onScrapeFailure: (Throwable) -> Unit,
): Scorecard? {
    if (current != null) return null
    suspend fun stored() = liveSource.getScorecardForMatch(matchId)?.takeIf { it.scorecard.innings.isNotEmpty() }
    // runCatchingSuspend rethrows cancellation, which stdlib runCatching reported as a failed scrape.
    suspend fun scraped() = AppResult.runCatchingSuspend { repository.fetchScorecard(matchId) }
        .onError { onScrapeFailure(it.cause ?: it) }
        .getOrNull()
        ?.takeIf { it.innings.isNotEmpty() }
    // `match-scorecard` freezes when the match leaves the live list, at the same instant and for
    // the same reason `match-live-detail` does — so for a finished match the stored row is the
    // truncated one and Cricbuzz's own page has the completed card. The scrape goes first there,
    // which also spends no billed read unless it fails.
    // The archive is last: it holds the card as the row froze, and is only reached once the row
    // itself has been pruned.
    if (matchIsOver) return firstAnswer({ scraped() }, { stored()?.scorecard }, archived)
    // Live: one billed read, and the row wins while the server is still writing it. A row that has
    // gone quiet is not discarded — it is merely outranked by a scrape, and still serves if that
    // scrape comes back empty. Losing a stale card to no card at all would be a worse answer.
    val row = stored()
    if (row != null && row.isFresh()) return row.scorecard
    return scraped() ?: row?.scorecard
}

/**
 * The over the chart plots against: the batting side's, which is the one still advancing.
 *
 * Both figures arrive as Cricbuzz's `"66.5"` notation, where the fraction is a ball count and not a
 * decimal — but only their ordering matters here, and `66.5 < 67.1` holds either way.
 */
internal fun battingOverOf(team1Overs: String, team2Overs: String): Float {
    val first = team1Overs.trim().toFloatOrNull() ?: 0f
    val second = team2Overs.trim().toFloatOrNull() ?: 0f
    return maxOf(first, second)
}

/**
 * Adds one point to the swing series, or returns [history] unchanged.
 *
 * A point is worth keeping only when the probability actually moved: match-sync rewrites the detail
 * row every minute whether or not a ball was bowled, and recording every rewrite would fill the
 * series with duplicates and flatten the chart. Bounded because a Test runs for five days.
 */
private fun appendProbabilityPoint(
    state: MatchDetailViewModel.UiState,
    payload: MatchLiveDetailPayload,
): List<WinProbabilityPoint> = appendProbabilityPoint(
    history = state.probabilityHistory,
    over = battingOverOf(
        payload.team1Overs ?: state.liveTeam1Overs,
        payload.team2Overs ?: state.liveTeam2Overs,
    ),
    team1Percent = payload.winProbabilityTeam1?.times(PERCENT_SCALE) ?: state.liveWinProbability,
    factors = payload.winProbabilityFactors,
)

internal fun appendProbabilityPoint(
    history: List<WinProbabilityPoint>,
    over: Float,
    team1Percent: Double?,
    factors: List<WinProbFactor> = emptyList(),
): List<WinProbabilityPoint> {
    if (team1Percent == null) return history
    val team1 = (team1Percent / PERCENT_SCALE).toFloat()
    val last = history.lastOrNull()
    if (last != null && kotlin.math.abs(last.team1Probability - team1) < PROBABILITY_EPSILON &&
        over <= last.over
    ) {
        return history
    }
    val point = WinProbabilityPoint(
        over = over,
        team1Probability = team1,
        team2Probability = 1f - team1,
        factors = factors,
    )
    return (history + point).takeLast(PROBABILITY_HISTORY_MAX)
}

private const val PERCENT_SCALE = 100.0
private const val PROBABILITY_EPSILON = 0.005f
private const val PROBABILITY_HISTORY_MAX = 240

/**
 * Places a realtime line in the newest-first feed by its `sort_key`.
 *
 * Realtime delivery is not ordered. Prepending unconditionally — which is what this did — put a
 * late-arriving ball above the deliveries that actually followed it, and the tab's over grouping
 * then rendered that as the wrong over leading the feed. The key travels on the event and on every
 * stored row, so the insert point is known exactly; an entry without one (the scrape fallback)
 * sorts as newest, which is what a locally scraped line is.
 */
/**
 * How far the feed may grow ON TOP of what was fetched. Fetches are capped by the client's own page
 * limits, but realtime inserts were not capped at all: a Test left open for a session grew the list
 * without bound, and every insert copies it.
 *
 * It is a headroom, not a total. As a total it deleted the reader's own pages: paging is 48 + 150·n,
 * so three taps put 498 rows on screen and the next delivery dropped 99 of them — rows that cost
 * billed reads, and which `hasOlderCommentary` would then offer to buy again. `pagedFeedFloor`
 * holds what the reader asked for, and 400 realtime deliveries — 66 overs of new play, by which
 * point that page really is history — is what may sit above it.
 */
private const val MAX_FEED_ENTRIES = 400

internal fun insertBySortKey(
    feed: List<CommentaryEntry>,
    entry: CommentaryEntry,
): List<CommentaryEntry> {
    val key = entry.sortKey ?: return listOf(entry) + feed
    val at = feed.indexOfFirst { (it.sortKey ?: Double.MAX_VALUE) < key }
    return if (at < 0) feed + entry else feed.subList(0, at) + entry + feed.subList(at, feed.size)
}

private suspend fun scrapeCommentary(repository: MatchRepository, matchId: String): List<String> =
    try {
        withTimeoutOrNull(COMMENTARY_FETCH_TIMEOUT_MS) {
            repository.fetchLiveCommentary(CricBuzzUrls.liveScoresUrl(matchId))
        }.orEmpty()
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Leaving the screen cancels the fetch; that is not a failed fetch (ANDROID-53).
        throw e
    } catch (e: Exception) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "commentary_fallback"
                message = "On-device commentary fetch failed: ${e.message}"
                level = SentryLevel.WARNING
            }
        )
        if (isReportableScrapeFailure(e)) Sentry.captureException(e)
        emptyList()
    }

private const val COMMENTARY_FETCH_TIMEOUT_MS = 10_000L
