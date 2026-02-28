package com.tanush.bouncer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

/**
 * Accessibility Extensions for Native Android Experience
 * 
 * Provides semantic labels and descriptions for improved accessibility
 */

/**
 * Add content description for TalkBack
 */
fun Modifier.contentDescription(description: String): Modifier = semantics {
    contentDescription = description
}

/**
 * Add state description for dynamic content
 */
fun Modifier.stateDescription(description: String): Modifier = semantics {
    stateDescription = description
}

/**
 * Mark region for live updates (live scores, etc)
 */
fun Modifier.liveRegion(mode: LiveRegionMode = LiveRegionMode.Polite): Modifier = semantics {
    liveRegion = mode
}

/**
 * Common content descriptions for cricket app
 */
object CricketAccessibility {
    fun matchCard(team1: String, score1: String, team2: String, score2: String, status: String): String {
        return "Match: $team1 $score1 versus $team2 $score2. Status: $status"
    }
    
    fun liveScore(runs: Int, wickets: Int, overs: String): String {
        return "Current score: $runs for $wickets in $overs overs"
    }
    
    fun over(ballNumber: Int, runs: Int, isWicket: Boolean): String {
        return if (isWicket) {
            "Ball $ballNumber: Wicket!"
        } else {
            "Ball $ballNumber: $runs runs"
        }
    }
    
    fun winProbability(team: String, probability: Int): String {
        return "$team has $probability percent win probability"
    }
    
    fun playerStats(name: String, stat: String, value: String): String {
        return "$name: $stat is $value"
    }
}
