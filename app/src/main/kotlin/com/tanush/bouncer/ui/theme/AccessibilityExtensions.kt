package com.tanush.bouncer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import io.sentry.Sentry

/**
 * Accessibility Extensions for Native Android Experience
 * 
 * Provides semantic labels and descriptions for improved accessibility
 */

/**
 * Add content description for TalkBack
 */
fun Modifier.contentDescription(description: String): Modifier = semantics {
    try {
        contentDescription = description
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Add state description for dynamic content
 */
fun Modifier.stateDescription(description: String): Modifier = semantics {
    try {
        stateDescription = description
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Mark region for live updates (live scores, etc)
 */
fun Modifier.liveRegion(mode: LiveRegionMode = LiveRegionMode.Polite): Modifier = semantics {
    try {
        liveRegion = mode
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Common content descriptions for cricket app
 */
object CricketAccessibility {
    fun matchCard(team1: String, score1: String, team2: String, score2: String, status: String): String {
        try {
            return "Match: $team1 $score1 versus $team2 $score2. Status: $status"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun liveScore(runs: Int, wickets: Int, overs: String): String {
        try {
            return "Current score: $runs for $wickets in $overs overs"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun over(ballNumber: Int, runs: Int, isWicket: Boolean): String {
        try {
            return if (isWicket) {
                "Ball $ballNumber: Wicket!"
            } else {
                "Ball $ballNumber: $runs runs"
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun winProbability(team: String, probability: Int): String {
        try {
            return "$team has $probability percent win probability"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun playerStats(name: String, stat: String, value: String): String {
        try {
            return "$name: $stat is $value"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
