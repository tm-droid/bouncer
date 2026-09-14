package com.tanush.bouncer.network

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A match's Cricbuzz `startDate` (epoch ms) as the date a match row displays, in the device's zone.
 * It replaces a hardcoded "Today", which the Info tab showed for matches days old.
 */
internal data class MatchStartTime(val date: String) {
    companion object {
        fun of(
            epochMs: Long,
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
        ): MatchStartTime = MatchStartTime(
            date = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
                .format(Instant.ofEpochMilli(epochMs).atZone(zone)),
        )
    }
}
