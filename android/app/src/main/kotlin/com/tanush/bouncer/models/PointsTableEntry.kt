package com.tanush.bouncer.models

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

/**
 * Represents a single team row in a tournament points table.
 */
@Serializable
@Immutable
data class PointsTableEntry(
    val position: Int,
    val teamName: String,
    val teamAcronym: String,
    /** Cricbuzz's own team id, so a row can be joined onto a team without comparing names. */
    val teamId: String = "",
    /** The crest from Cricbuzz's `teamImageId`, or null to fall back to the bundled map. */
    val logoUrl: String? = null,
    val matches: Int,
    val won: Int,
    val lost: Int,
    val tied: Int = 0,
    val noResult: Int = 0,
    val netRunRate: Double,
    val points: Int,
    val recentForm: List<String> = emptyList(),
    val qualified: Boolean = false,
) {
    val drawOrNR: Int get() = tied + noResult
}
