package com.tanush.bouncer.ui.accessibility

import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.CustomAccessibilityAction as ComposeCustomAccessibilityAction

/**
 * TalkBack Support for Bouncer Cricket App
 * 
 * Comprehensive accessibility utilities for screen reader support,
 * including cricket-specific semantic descriptions and announcements.
 */

/**
 * Cricket-specific semantic descriptions for TalkBack
 */
object CricketSemantics {
    
    /**
     * Generate a comprehensive match description for screen readers
     */
    fun matchDescription(
        team1: String,
        team1Score: String,
        team2: String,
        team2Score: String,
        matchStatus: String,
        format: String? = null,
        venue: String? = null
    ): String {
        val formatText = format?.let { "$it match. " } ?: ""
        val venueText = venue?.let { " at $it" } ?: ""
        return "${formatText}$team1 $team1Score versus $team2 $team2Score. Status: $matchStatus$venueText"
    }
    
    /**
     * Generate a live score description
     */
    fun scoreDescription(
        runs: Int,
        wickets: Int,
        overs: String,
        battingTeam: String? = null,
        runRate: Float? = null,
        requiredRunRate: Float? = null
    ): String {
        val teamText = battingTeam?.let { "$it: " } ?: ""
        val scoreText = "$runs for $wickets in $overs overs"
        val runRateText = runRate?.let { ". Run rate: %.2f".format(it) } ?: ""
        val requiredText = requiredRunRate?.let { ". Required rate: %.2f".format(it) } ?: ""
        return "$teamText$scoreText$runRateText$requiredText"
    }
    
    /**
     * Generate player batting description
     */
    fun batterDescription(
        name: String,
        runs: Int,
        balls: Int,
        fours: Int,
        sixes: Int,
        isOnStrike: Boolean = false
    ): String {
        val strikeText = if (isOnStrike) " on strike" else ""
        val strikeRate = if (balls > 0) (runs.toFloat() / balls * 100) else 0f
        return "$name$strikeText: $runs runs from $balls balls. Strike rate: %.1f. $fours fours and $sixes sixes".format(strikeRate)
    }
    
    /**
     * Generate player bowling description
     */
    fun bowlerDescription(
        name: String,
        overs: String,
        maidens: Int,
        runs: Int,
        wickets: Int,
        economy: Float? = null
    ): String {
        val economyText = economy?.let { ". Economy: %.2f".format(it) } ?: ""
        return "$name: $wickets wickets for $runs runs in $overs overs. $maidens maidens$economyText"
    }
    
    /**
     * Generate ball-by-ball description
     */
    fun ballDescription(
        ballNumber: Int,
        outcome: BallOutcome,
        bowler: String? = null,
        batter: String? = null
    ): String {
        val deliveryText = bowler?.let { "$it to ${batter ?: "batter"}" } ?: "Ball $ballNumber"
        return when (outcome) {
            is BallOutcome.Runs -> "$deliveryText: ${outcome.runs} run${if (outcome.runs != 1) "s" else ""}"
            is BallOutcome.Boundary -> "$deliveryText: ${if (outcome.isSix) "SIX!" else "FOUR!"}"
            is BallOutcome.Wicket -> "$deliveryText: WICKET! ${outcome.dismissalType}"
            is BallOutcome.Wide -> "$deliveryText: Wide ball"
            is BallOutcome.NoBall -> "$deliveryText: No ball"
            is BallOutcome.LegBye -> "$deliveryText: ${outcome.runs} leg bye${if (outcome.runs != 1) "s" else ""}"
            is BallOutcome.Bye -> "$deliveryText: ${outcome.runs} bye${if (outcome.runs != 1) "s" else ""}"
            is BallOutcome.DotBall -> "$deliveryText: Dot ball"
        }
    }
    
    /**
     * Generate win probability description
     */
    fun winProbabilityDescription(
        team1: String,
        probability1: Int,
        team2: String,
        probability2: Int
    ): String {
        return when {
            probability1 > probability2 -> "$team1 leads with $probability1 percent win probability. $team2 at $probability2 percent"
            probability2 > probability1 -> "$team2 leads with $probability2 percent win probability. $team1 at $probability1 percent"
            else -> "Even match: Both teams at $probability1 percent win probability"
        }
    }
    
    /**
     * Generate partnership description
     */
    fun partnershipDescription(
        runs: Int,
        balls: Int,
        batter1: String,
        batter2: String
    ): String {
        return "Partnership of $runs runs from $balls balls between $batter1 and $batter2"
    }
    
    /**
     * Generate over summary description
     */
    fun overSummaryDescription(
        overNumber: Int,
        runs: Int,
        wickets: Int,
        extras: Int = 0
    ): String {
        val wicketText = if (wickets > 0) " and $wickets wicket${if (wickets != 1) "s" else ""}" else ""
        val extrasText = if (extras > 0) " including $extras extra${if (extras != 1) "s" else ""}" else ""
        return "Over $overNumber: $runs runs$wicketText$extrasText"
    }
}

/**
 * Sealed class representing possible ball outcomes
 */
sealed class BallOutcome {
    data class Runs(val runs: Int) : BallOutcome()
    data class Boundary(val isSix: Boolean) : BallOutcome()
    data class Wicket(val dismissalType: String) : BallOutcome()
    data object Wide : BallOutcome()
    data object NoBall : BallOutcome()
    data class LegBye(val runs: Int) : BallOutcome()
    data class Bye(val runs: Int) : BallOutcome()
    data object DotBall : BallOutcome()
}

/**
 * Custom accessibility action data class
 */
data class BouncerAccessibilityAction(
    val label: String,
    val action: () -> Boolean
)

/**
 * Modifier for live score announcements that auto-announce when score changes
 */
@Composable
fun Modifier.liveScoreAnnouncement(
    runs: Int,
    wickets: Int,
    overs: String,
    team: String? = null
): Modifier {
    val view = LocalView.current
    var previousScore by remember { mutableStateOf("$runs/$wickets") }
    val currentScore = "$runs/$wickets"
    
    LaunchedEffect(currentScore) {
        if (currentScore != previousScore) {
            val announcement = CricketSemantics.scoreDescription(runs, wickets, overs, team)
            view.announceForAccessibility(announcement)
            previousScore = currentScore
        }
    }
    
    return this.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = CricketSemantics.scoreDescription(runs, wickets, overs, team)
    }
}

/**
 * Modifier for match card accessibility semantics
 */
fun Modifier.matchStatusSemantics(
    team1: String,
    team1Score: String,
    team2: String,
    team2Score: String,
    matchStatus: String,
    isLive: Boolean = false,
    format: String? = null,
    venue: String? = null,
    accessibilityActions: List<BouncerAccessibilityAction> = emptyList()
): Modifier = this.semantics(mergeDescendants = true) {
    contentDescription = CricketSemantics.matchDescription(
        team1, team1Score, team2, team2Score, matchStatus, format, venue
    )
    
    if (isLive) {
        liveRegion = LiveRegionMode.Polite
        stateDescription = "Live match in progress"
    } else {
        stateDescription = matchStatus
    }
    
    if (accessibilityActions.isNotEmpty()) {
        customActions = accessibilityActions.map { action ->
            ComposeCustomAccessibilityAction(action.label) {
                action.action()
            }
        }
    }
}

/**
 * Modifier for player stats accessibility
 */
fun Modifier.playerStatsSemantics(
    playerName: String,
    role: String,
    stats: Map<String, String>,
    isOnStrike: Boolean = false
): Modifier = this.semantics(mergeDescendants = true) {
    val statsDescription = stats.entries.joinToString(". ") { "${it.key}: ${it.value}" }
    val strikeText = if (isOnStrike) " Currently on strike." else ""
    contentDescription = "$playerName, $role.$strikeText $statsDescription"
    
    if (isOnStrike) {
        stateDescription = "On strike"
    }
}

/**
 * Modifier for section headers to improve navigation
 */
fun Modifier.semanticHeading(
    level: Int = 1,
    text: String? = null
): Modifier = this.semantics {
    heading()
    text?.let { contentDescription = it }
}

/**
 * Utilities for controlling TalkBack traversal order
 */
object TraversalOrder {
    
    /**
     * Modifier to set traversal index for logical navigation order
     */
    fun Modifier.traversalIndex(index: Float): Modifier = this.semantics {
        traversalIndex = index
    }
    
    /**
     * Modifier to mark element as invisible to accessibility services
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun Modifier.invisibleToAccessibility(): Modifier = this.semantics {
        invisibleToUser()
    }
    
    /**
     * Modifier to clear and merge semantics
     */
    fun Modifier.clearSemantics(): Modifier = this.clearAndSetSemantics { }
    
    /**
     * Modifier for grouping related elements
     */
    fun Modifier.groupedSemantics(
        contentDescription: String? = null
    ): Modifier = this.semantics(mergeDescendants = true) {
        contentDescription?.let { this.contentDescription = it }
    }
}

/**
 * Composable to manage focus for accessibility
 */
@Composable
fun rememberAccessibilityFocusRequester(): FocusRequester {
    return remember { FocusRequester() }
}

/**
 * Modifier extension for making elements focusable with proper accessibility
 */
fun Modifier.accessibleFocusable(
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null
): Modifier {
    var modifier = this.focusable()
    focusRequester?.let { modifier = modifier.focusRequester(it) }
    contentDescription?.let { 
        modifier = modifier.semantics { this.contentDescription = it }
    }
    return modifier
}

/**
 * Announce text to TalkBack users
 */
@Composable
fun AccessibilityAnnouncement(
    message: String,
    priority: LiveRegionMode = LiveRegionMode.Polite
) {
    val view = LocalView.current
    
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            view.announceForAccessibility(message)
        }
    }
}

/**
 * Modifier for wicket announcements with high priority
 */
@Composable
fun Modifier.wicketAnnouncement(
    hasWicket: Boolean,
    dismissedBatter: String,
    dismissalType: String,
    bowler: String
): Modifier {
    val view = LocalView.current
    var announcedWicket by remember { mutableStateOf(false) }
    
    LaunchedEffect(hasWicket, dismissedBatter) {
        if (hasWicket && !announcedWicket) {
            val announcement = "WICKET! $dismissedBatter dismissed, $dismissalType by $bowler"
            view.announceForAccessibility(announcement)
            announcedWicket = true
        }
        if (!hasWicket) {
            announcedWicket = false
        }
    }
    
    return this.semantics {
        liveRegion = LiveRegionMode.Assertive
    }
}

/**
 * Modifier for boundary announcements
 */
@Composable
fun Modifier.boundaryAnnouncement(
    hasBoundary: Boolean,
    isSix: Boolean,
    batter: String
): Modifier {
    val view = LocalView.current
    var announcedBoundary by remember { mutableStateOf(false) }
    
    LaunchedEffect(hasBoundary, isSix, batter) {
        if (hasBoundary && !announcedBoundary) {
            val boundaryType = if (isSix) "SIX" else "FOUR"
            val announcement = "$boundaryType! Hit by $batter"
            view.announceForAccessibility(announcement)
            announcedBoundary = true
        }
        if (!hasBoundary) {
            announcedBoundary = false
        }
    }
    
    return this.semantics {
        liveRegion = LiveRegionMode.Polite
    }
}
