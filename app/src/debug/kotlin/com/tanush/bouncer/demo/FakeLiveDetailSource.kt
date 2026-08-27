package com.tanush.bouncer.demo

import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.WinProbabilityPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Zero-I/O [LiveMatchDetailSource] for the showcase video. Emits scripted
 * detail snapshots and commentary lines on timers so the real MatchDetailViewModel
 * updates live on camera exactly as it does with Appwrite Realtime.
 */
internal class FakeLiveDetailSource(private val elapsed: () -> Long) : LiveMatchDetailSource {

    override suspend fun getForMatch(matchId: String): MatchLiveDetailPayload =
        ShowcaseScript.detailPayloadAt(elapsed())

    // No server verdict on purpose: the scripted lines carry Cricbuzz's own "FOUR!"/"SIX!", so the
    // showcase exercises the same text-classification fallback a scraped line takes.
    override suspend fun getCommentaryForMatch(matchId: String): List<CommentaryEntry> =
        ShowcaseScript.commentaryLines().map { CommentaryEntry(it, event = null) }

    // The script is the whole feed; there is nothing behind it to page back to.
    override suspend fun getOlderCommentary(matchId: String, olderThan: Double): List<CommentaryEntry> =
        emptyList()

    // The scripted payload already carries the scorecard, so the split table adds nothing here.
    override suspend fun getScorecardForMatch(matchId: String): com.tanush.bouncer.models.Scorecard? =
        ShowcaseScript.detailPayloadAt(elapsed()).scorecard

    override fun observeScorecard(matchId: String): Flow<com.tanush.bouncer.models.Scorecard> = flow {
        while (true) {
            delay(DETAIL_TICK_MS)
            ShowcaseScript.detailPayloadAt(elapsed()).scorecard?.let { emit(it) }
        }
    }

    override fun observeDetail(matchId: String): Flow<MatchLiveDetailPayload> = flow {
        while (true) {
            delay(DETAIL_TICK_MS)
            emit(ShowcaseScript.detailPayloadAt(elapsed()))
        }
    }

    override fun observeCommentary(matchId: String): Flow<CommentaryEvent> = flow {
        ShowcaseScript.streamingCommentary().forEachIndexed { index, line ->
            delay(COMMENTARY_TICK_MS)
            emit(CommentaryEvent(ShowcaseScript.MATCH_ID, line, index.toDouble(), event = null))
        }
    }


    private companion object {
        const val DETAIL_TICK_MS = 4_000L
        const val COMMENTARY_TICK_MS = 5_000L
    }
}
