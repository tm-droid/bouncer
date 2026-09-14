package com.tanush.bouncer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
import java.time.Instant
import java.time.ZoneOffset

@Entity(
    tableName = "matches",
    indices = [
        Index(value = ["isLive"], name = "idx_matches_is_live"),
        Index(value = ["lastUpdated"], name = "idx_matches_last_updated"),
    ]
)
data class MatchEntity(
    @PrimaryKey
    val matchId: String,
    val seriesId: String,
    val matchTitle: String,
    val matchType: String,
    val status: String,
    val venue: String,
    val date: String,
    val matchTime: String,
    val currentStatus: String,
    val info: String,
    val team1Name: String,
    val team1Score: String?,
    val team2Name: String,
    val team2Score: String?,
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isLive: Boolean = computeIsLive(status, currentStatus)
) {
    companion object {
        /**
         * Must agree with [com.tanush.bouncer.models.CricketMatch.isLive] — MatchDao.getLiveMatches()
         * filters on this column while the UI filters on the model property, and the previous
         * two-substring implementation here made those two sets differ.
         */
        fun computeIsLive(status: String, currentStatus: String): Boolean =
            MatchStatusClassifier.classify("$status $currentStatus") == StatusSignal.IN_PROGRESS
    }
}

/**
 * The row [com.tanush.bouncer.services.HighlightFuzzyMatcher] scores a highlight against.
 *
 * Shaped for that matcher rather than for display. [MatchEntity.date] is the UTC `yyyy-MM-dd` of
 * the start, because `daysBetween` reads an ISO prefix and Highlightly's `startDate` is ISO — the
 * list's own "Today" parses as nothing, so date agreement never counted and an old India vs
 * Australia looked as good as today's. [MatchEntity.matchTitle] carries the series name, because the
 * league signal compares Highlightly's league against the title and "A vs B" names no league.
 */
fun CricketMatch.toMatchEntity(nowMs: Long = System.currentTimeMillis()): MatchEntity = MatchEntity(
    matchId = id,
    seriesId = seriesId,
    matchTitle = if (seriesName.isBlank()) matchTitle else "$matchTitle, $seriesName",
    matchType = matchType,
    status = status,
    venue = venue,
    date = startDateMs?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() } ?: "",
    matchTime = matchTime,
    currentStatus = currentStatus,
    info = info,
    team1Name = teams.getOrNull(0)?.name.orEmpty(),
    team1Score = teams.getOrNull(0)?.score?.takeIf { it.isNotBlank() },
    team2Name = teams.getOrNull(1)?.name.orEmpty(),
    team2Score = teams.getOrNull(1)?.score?.takeIf { it.isNotBlank() },
    lastUpdated = nowMs,
)
