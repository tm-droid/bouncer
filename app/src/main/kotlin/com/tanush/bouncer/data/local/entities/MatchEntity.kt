package com.tanush.bouncer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal

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
