package com.tanush.bouncer.analysis

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tanush.bouncer.models.ProbabilityEvent
import java.util.Date
import io.sentry.Sentry

data class TrackerMatchSituation(
    val requiredRunRate: Double,
    val oversRemaining: Int,
    val wicketsInHand: Int,
    val currentRunRate: Double,
    val target: Int
)

class ProbabilityTracker(
    private val matchId: String,
    private val calculator: WinProbabilityCalculator,
    private val context: Context
) {
    private val events: MutableList<ProbabilityEvent> = mutableListOf()
    private var currentProbability: Double = 50.0
    private val prefs: SharedPreferences = context.getSharedPreferences("probability_cache", Context.MODE_PRIVATE)
    
    suspend fun trackEvent(
        ball: Int,
        over: Int,
        runs: Int,
        isWicket: Boolean,
        wicketType: String?,
        extras: Int,
        extraType: String?,
        matchSituation: TrackerMatchSituation
    ) {
        val oldProbability = currentProbability

        // Calculate new probability — suspend call dispatches to Dispatchers.Default internally
        val runsNeeded = (matchSituation.requiredRunRate * matchSituation.oversRemaining).toInt()
        currentProbability = calculator.calculateWinProbability(
            runsNeeded = runsNeeded,
            ballsRemaining = matchSituation.oversRemaining * 6,
            wicketsLost = 10 - matchSituation.wicketsInHand,
            currentRunRate = matchSituation.currentRunRate,
            requiredRunRate = matchSituation.requiredRunRate
        )
        
        // Determine event type and description
        val (eventType, description) = determineEventType(
            runs, isWicket, wicketType, extras, extraType, ball
        )
        
        // Calculate impact
        val impact = currentProbability - oldProbability
        
        // Create and store event
        val event = ProbabilityEvent(
            timestamp = Date(),
            ball = ball,
            over = over,
            probability = currentProbability,
            eventType = eventType,
            description = description,
            impact = impact
        )
        
        events.add(event)
    }
    
    private fun determineEventType(
        runs: Int,
        isWicket: Boolean,
        wicketType: String?,
        extras: Int,
        extraType: String?,
        ball: Int
    ): Pair<ProbabilityEvent.EventType, String> {
        return when {
            isWicket -> ProbabilityEvent.EventType.WICKET to "Wicket: ${wicketType ?: "Unknown"}"
            runs >= 4 -> ProbabilityEvent.EventType.BOUNDARY to "$runs runs"
            runs == 0 -> ProbabilityEvent.EventType.DOT_BALL to "Dot ball"
            ball <= 36 -> ProbabilityEvent.EventType.POWER_PLAY to "Power play ball"
            ball >= 240 -> ProbabilityEvent.EventType.DEATH_OVERS to "Death overs ball"
            runs == 50 || runs == 100 -> ProbabilityEvent.EventType.MILESTONE to "$runs runs milestone"
            else -> ProbabilityEvent.EventType.OTHER to "$runs runs"
        }
    }
    
    // MARK: - Analysis Methods
    
    fun getProbabilityGraph(): List<ProbabilityEvent> = events
    
    fun getKeyMoments(): List<ProbabilityEvent> {
        try {
            // Return events that had significant impact on the probability
            return events.filter { kotlin.math.abs(it.impact) > 5.0 }
                .sortedByDescending { kotlin.math.abs(it.impact) }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getPhaseAnalysis(): String {
        try {
            var powerPlayImpact = 0.0
            var middleOversImpact = 0.0
            var deathOversImpact = 0.0
            for (event in events) {
                when {
                    event.ball <= 36  -> powerPlayImpact += event.impact
                    event.ball < 240  -> middleOversImpact += event.impact
                    else              -> deathOversImpact += event.impact
                }
            }
            return "Power Play Impact: ${"%.1f".format(powerPlayImpact)}%\n" +
                   "Middle Overs Impact: ${"%.1f".format(middleOversImpact)}%\n" +
                   "Death Overs Impact: ${"%.1f".format(deathOversImpact)}%\n"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getEventAnalysis(): String {
        try {
            var boundaryImpact = 0.0
            var wicketImpact = 0.0
            var dotBallImpact = 0.0
            for (event in events) {
                when (event.eventType) {
                    ProbabilityEvent.EventType.BOUNDARY -> boundaryImpact += event.impact
                    ProbabilityEvent.EventType.WICKET   -> wicketImpact += event.impact
                    ProbabilityEvent.EventType.DOT_BALL -> dotBallImpact += event.impact
                    else -> {}
                }
            }
            return "Boundaries Impact: ${"%.1f".format(boundaryImpact)}%\n" +
                   "Wickets Impact: ${"%.1f".format(wicketImpact)}%\n" +
                   "Dot Balls Impact: ${"%.1f".format(dotBallImpact)}%\n"
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun getMatchSummary(): String {
        try {
            var summary = "Match Probability Analysis\n\n"
        
            // Overall probability change
            if (events.isNotEmpty()) {
                val totalChange = events.last().probability - events.first().probability
                summary += "Overall Probability Change: ${"%.1f".format(totalChange)}%\n\n"
            }
        
            // Add phase analysis
            summary += "Phase Analysis:\n"
            summary += getPhaseAnalysis()
            summary += "\n"
        
            // Add event analysis
            summary += "Event Analysis:\n"
            summary += getEventAnalysis()
            summary += "\n"
        
            // Add key moments
            summary += "Key Moments:\n"
            val keyMoments = getKeyMoments().take(5)
            for (event in keyMoments) {
                summary += "• ${event.description} (Impact: ${"%.1f".format(event.impact)}%)\n"
            }
        
            return summary
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    // MARK: - Data Caching
    
    fun saveToCache() {
        try {
            val cache = ProbabilityCache(
                events = events,
                timestamp = Date(),
                currentProbability = currentProbability
            )
        
            val json = Gson().toJson(cache)
            prefs.edit().putString("probability_cache_$matchId", json).apply()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun loadFromCache() {
        try {
            val json = prefs.getString("probability_cache_$matchId", null)
            if (json != null) {
                val type = object : TypeToken<ProbabilityCache>() {}.type
                val cache = Gson().fromJson<ProbabilityCache>(json, type)
                events.clear()
                events.addAll(cache.events)
                currentProbability = cache.currentProbability
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

data class ProbabilityCache(
    val events: List<ProbabilityEvent>,
    val timestamp: Date,
    val currentProbability: Double
)
