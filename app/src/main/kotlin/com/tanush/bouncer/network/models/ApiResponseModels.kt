package com.tanush.bouncer.network.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val status: String,
    val response: List<SeriesData>
)

@Serializable
data class UpcomingSchedulesApiResponse(
    val status: String,
    val response: UpcomingResponseData
)

@Serializable
data class UpcomingSeriesApiResponse(
    val status: String,
    val response: List<SeriesData>
)

@Serializable
data class UpcomingResponseData(
    val schedules: List<ScheduleData>
)

@Serializable
data class ScheduleData(
    val date: String,
    val matchList: List<SeriesMatchData>
)

@Serializable
data class SeriesMatchData(
    val date: String,
    val seriesName: String,
    val seriesId: String,
    val matchId: String,
    val matchTitle: String,
    val matchDesc: String,
    val matchFormat: String,
    val venue: String,
    val startDate: String
)

@Serializable
data class MatchDetails(
    val date: String,
    val seriesName: String,
    val seriesId: String,
    val matchId: String,
    val matchTitle: String,
    val matchDesc: String,
    val matchFormat: String,
    val venue: String,
    val startDate: String
)

@Serializable
data class SeriesData(
    val seriesName: String,
    val matchList: List<MatchData>
)

@Serializable
data class MatchData(
    val seriesId: String?,
    val matchId: String,
    val matchTitle: String,
    val matchFormat: String?,
    val matchVenue: String?,
    val matchDate: String?,
    val matchTime: String?,
    val matchStatus: String?,
    val currentStatus: String?,
    // Live matches only
    val teamOne: TeamData?,
    val teamTwo: TeamData?
)

@Serializable
data class TeamData(
    val name: String,
    val score: String?,
    val status: String?
)

@Serializable
data class SanwebMatchResponse(
    val title: String,
    val update: String,
    val livescore: String,
    val runrate: String,
    val batterone: String,
    val batsmanonerun: String,
    val batsmanoneball: String,
    val batsmanonesr: String,
    val battertwo: String,
    val batsmantworun: String,
    val batsmantwoball: String,
    val batsmantwosr: String,
    val bowlerone: String,
    val bowleroneover: String,
    val bowleronerun: String,
    val bowleronewickers: String,
    val bowleroneeconomy: String,
    val bowlertwo: String,
    val bowlertwoover: String,
    val bowlertworun: String,
    val bowlertwowickers: String,
    val bowlertwoeconomy: String
)

@Serializable
data class SanwebMatchesListResponse(
    val matches: List<SanwebMatchSummary>,
    val count: Int
)

/**
 * One match in a `{type}-matches` Storage file, written by `bouncer-poll-live-matches`.
 *
 * Everything below [status] was added when that function stopped reading a separately-hosted
 * scraper and began parsing Cricbuzz itself. The old shape was `{id, title, status}` and nothing
 * else, which is why every server-fed surface was a degraded copy of the phone UI: the widget
 * split [title] on " vs " to name the sides, Android Auto had no score to show, and nothing
 * carried Cricbuzz's team ids — the only key a live payload can be aligned on.
 *
 * Every added field has a default, so a file written by an older deployment still parses. An
 * absent [teams] is "this file predates the change", not "this match has no teams", and a caller
 * must degrade rather than render blanks.
 */
@Serializable
data class SanwebMatchSummary(
    val id: String,
    val title: String,
    /** Cricbuzz's result-bearing line: "India won by 5 wkts", "Innings Break". */
    val status: String,
    /** Cricbuzz's coarse `state`: "Complete", "In Progress", "Preview". Blank on an older file. */
    val state: String = "",
    /** "TEST", "ODI", "T20", "HUNDRED". Blank when the upstream page did not name it. */
    val matchFormat: String = "",
    val seriesId: String = "",
    val seriesName: String = "",
    val matchDesc: String = "",
    /** Epoch millis as a string, matching Cricbuzz's own field. Blank when absent. */
    val startDate: String = "",
    val teams: List<SanwebMatchTeam> = emptyList(),
)

/** One side of a [SanwebMatchSummary]. [teamId] is Cricbuzz's own id, blank when unknown. */
@Serializable
data class SanwebMatchTeam(
    val teamId: String = "",
    val name: String = "",
    /** Every innings this side has batted, joined: "266 & 83/2". Blank before it has batted. */
    val score: String = "",
    val overs: String = "",
    val isBatting: Boolean = false,
)
