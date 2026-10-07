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
    val teams: List<Side> = emptyList(),
    val h2h: Record? = null,
    val cricinfo: Officials? = null,
) {
    /**
     * Who is standing in the match, who is on debut and how many reviews each side has left, as
     * ESPNcricinfo's match page states them. Absent for a match Cricinfo does not cover, which is
     * most of them. [reviews] is its own short line ("WI - 2 of 2, IND - 1 of 2") and is as old as
     * the file: it changes a few times a match, not every ball.
     */
    @Serializable
    data class Officials(
        val umpires: List<String> = emptyList(),
        val tvUmpires: List<String> = emptyList(),
        val referees: List<String> = emptyList(),
        val debutants: List<Debutant> = emptyList(),
        val reviews: String? = null,
    )

    /**
     * A player in their first match of this kind. [team] is the player's COUNTRY code as Cricinfo
     * gives it — "AUS" for a Victoria debutant — so it does not say which side they play for here.
     */
    @Serializable
    data class Debutant(val team: String = "", val name: String = "")

    /** One of the two sides, in the order [Record] counts them. */
    @Serializable
    data class Side(val name: String = "")

    /**
     * The two sides' meetings in the archive, in THIS match's format and for these sides' gender —
     * which is what the T20-only `h2h-stats` file cannot say about a one-day match or a Test.
     * [firstWins] belongs to `teams[0]`; the remainder of [games] were ties, draws or no result.
     */
    @Serializable
    data class Record(val games: Int = 0, val firstWins: Int = 0, val secondWins: Int = 0)

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

/** Fewer meetings than this is an anecdote, not a record. */
const val MIN_RECORDED_MEETINGS = 3

/** The record with the names it belongs to, or null when there is too little of one to show. */
fun MatchContext.recordedMeetings(): Triple<Int, Pair<String, Int>, Pair<String, Int>>? {
    val record = h2h?.takeIf { it.games >= MIN_RECORDED_MEETINGS } ?: return null
    val first = teams.getOrNull(0)?.name?.takeIf { it.isNotBlank() } ?: return null
    val second = teams.getOrNull(1)?.name?.takeIf { it.isNotBlank() } ?: return null
    return Triple(record.games, first to record.firstWins, second to record.secondWins)
}

/** How far this ground's first-innings scoring sits from its format's, in whole per cent. */
fun MatchContext.Venue.scoringPercent(): Int = Math.round((scoring - 1.0) * PERCENT).toInt()

private const val PERCENT = 100
