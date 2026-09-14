package com.tanush.bouncer.network

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A fixture's Cricbuzz `startDate` (epoch ms) as the strings a match row displays, in the device's
 * zone.
 *
 * [statusLine] must stay classifiable: "Match starts at" is a PRE_MATCH marker in
 * [com.tanush.bouncer.models.MatchStatusClassifier], and it is the only thing that makes a schedule
 * fixture — which carries no status of its own — read as upcoming.
 */
internal data class MatchStartTime(val date: String, val time: String, val statusLine: String) {
    companion object {
        fun of(
            epochMs: Long,
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
        ): MatchStartTime {
            val at = Instant.ofEpochMilli(epochMs).atZone(zone)
            val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(at)
            return MatchStartTime(
                date = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale).format(at),
                time = time,
                statusLine = "Match starts at ${DateTimeFormatter.ofPattern("EEE d MMM", locale).format(at)}, $time",
            )
        }
    }
}
