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

@Serializable
data class SanwebMatchSummary(
    val id: String,
    val title: String,
    val status: String
)
