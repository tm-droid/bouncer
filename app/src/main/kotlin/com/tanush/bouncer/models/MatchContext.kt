package com.tanush.bouncer.models

import kotlinx.serialization.Serializable

/**
 * What `bouncer-match-sync` knows about a match beside the score — the ground, the toss and a
 * weather forecast — read from the `mc-{matchId}` file in the `cricket-stats` bucket.
 *
 * Every field has a default: the file is written by a server that changes independently of the
 * installed app, and a field this build does not know must not cost it the ones it does.
 */
@Serializable
data class MatchContext(
    val rev: Int = 0,
    val venue: Venue? = null,
    val forecast: Forecast? = null,
    val preview: Surface? = null,
    val summary: Surface? = null,
) {
    /**
     * A few sentences about the match as a whole: the [preview] before it, the [summary] after.
     * Written server-side from the match's own records and checked against them; when no written
     * version passes that check the server stores the same facts as plain sentences instead, so
     * [text] is never unchecked prose.
     */
    @Serializable
    data class Surface(
        val text: String = "",
    )

    /**
     * The ground against its format. [scoring] is first-innings scoring here over first-innings
     * scoring everywhere in the format, shrunk toward 1.0 while the ground has few matches — so it
     * is an estimate, and it is shown only once [known] says the archive has the ground at all.
     */
    @Serializable
    data class Venue(
        val name: String = "",
        val known: Boolean = false,
        val matches: Int = 0,
        val scoring: Double = 1.0,
    )

    /** A forecast, never an observation: [fetchedAt] is when it was asked for. */
    @Serializable
    data class Forecast(
        val source: String = "",
        val fetchedAt: Long = 0,
        val hours: List<Hour> = emptyList(),
    )

    /** One forecast hour. A null field is one the source did not give, not a zero. */
    @Serializable
    data class Hour(
        val t: Long = 0,
        val tempC: Double? = null,
        val humidity: Double? = null,
        val rainChance: Double? = null,
        val windKph: Double? = null,
        val cloud: Double? = null,
    )
}

/** The hours the conditions card summarises: about a limited-overs match from now. */
const val FORECAST_WINDOW_HOURS = 8

/** What the conditions card shows. A null field is left off the card. */
data class ForecastSummary(
    val tempC: Int?,
    val humidity: Int?,
    val maxRainChance: Int?,
    val windKph: Int?,
    val cloud: Int?,
    val hours: Int,
    val fetchedAt: Long,
)

/**
 * The forecast from [nowMs] onward, or null when it has nothing left to say.
 *
 * A forecast fetched yesterday for hours that have now passed is not shown as today's weather:
 * only hours at or after the current one are read, and with none left the card is not drawn.
 */
fun MatchContext.Forecast.summary(nowMs: Long): ForecastSummary? {
    val ahead = hours.filter { it.t >= nowMs - HOUR_MS }.take(FORECAST_WINDOW_HOURS)
    val first = ahead.firstOrNull() ?: return null
    return ForecastSummary(
        tempC = first.tempC?.let(Math::round)?.toInt(),
        humidity = first.humidity?.let(Math::round)?.toInt(),
        maxRainChance = ahead.mapNotNull { it.rainChance }.maxOrNull()?.let(Math::round)?.toInt(),
        windKph = first.windKph?.let(Math::round)?.toInt(),
        cloud = first.cloud?.let(Math::round)?.toInt(),
        hours = ahead.size,
        fetchedAt = fetchedAt,
    )
}

private const val HOUR_MS = 3_600_000L

/** How far this ground's first-innings scoring sits from its format's, in whole per cent. */
fun MatchContext.Venue.scoringPercent(): Int = Math.round((scoring - 1.0) * PERCENT).toInt()

private const val PERCENT = 100
