package com.tanush.bouncer.models

import android.content.Context
import io.sentry.Sentry

enum class AchievementKind(val id: String) {
    // Batting
    RUNS_30("runs30"), RUNS_50("runs50"), RUNS_75("runs75"), RUNS_100("runs100"),
    RUNS_150("runs150"), RUNS_200("runs200"), RUNS_250("runs250"), RUNS_300("runs300"),
    FASTEST_FIFTY("fastestFifty"), FASTEST_HUNDRED("fastestHundred"), MOST_SIXES("mostSixes"),
    // Bowling
    WICKETS_3("wickets3"), WICKETS_4("wickets4"), WICKETS_5("wickets5"), WICKETS_10("wickets10"),
    MAIDEN_OVER("maidenOver"), HAT_TRICK("hatTrick"), BEST_ECONOMY("bestEconomy"),
    // Team
    TEAM_200("team200"), TEAM_10_WKT_WIN("team10WktWin"), SUCCESSFUL_CHASE("successfulChase"),
    // Special
    PLAYER_OF_MATCH("playerOfMatch"), RECORD_PARTNERSHIP("recordPartnership");

    companion object {
        private const val PREFS_NAME = "bouncer_achievements"
        private const val KEY_UNLOCKED = "unlocked_achievements"
        
        fun fromString(value: String): AchievementKind? {
            try {
                return entries.find { it.id == value }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        fun loadUnlocked(context: Context): Set<AchievementKind> {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedIds = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
                return savedIds.mapNotNull { fromString(it) }.toSet()
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        fun saveUnlocked(context: Context, achievements: Set<AchievementKind>) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putStringSet(KEY_UNLOCKED, achievements.map { it.id }.toSet()).apply()
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        fun unlock(context: Context, kind: AchievementKind): Boolean {
            try {
                val current = loadUnlocked(context).toMutableSet()
                if (current.contains(kind)) return false
                current.add(kind)
                saveUnlocked(context, current)
                return true
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}

data class Achievement(
    val id: AchievementKind,
    val title: String,
    val description: String,
    val icon: String,
    var unlocked: Boolean = false
) {
    companion object {
        val all: List<Achievement> = listOf(
            // Batting
            Achievement(AchievementKind.RUNS_30, "30 Runs", "Score 30 runs in an innings", "3️⃣0️⃣"),
            Achievement(AchievementKind.RUNS_50, "Fifty", "Score 50 runs in an innings", "🏅"),
            Achievement(AchievementKind.RUNS_75, "75 Runs", "Score 75 runs in an innings", "7️⃣5️⃣"),
            Achievement(AchievementKind.RUNS_100, "Century", "Score 100 runs in an innings", "🎖️"),
            Achievement(AchievementKind.RUNS_150, "150 Runs", "Score 150 runs in an innings", "1️⃣5️⃣0️⃣"),
            Achievement(AchievementKind.RUNS_200, "Double Century", "Score 200 runs in an innings", "💯💯"),
            Achievement(AchievementKind.RUNS_250, "250 Runs", "Score 250 runs in an innings", "2️⃣5️⃣0️⃣"),
            Achievement(AchievementKind.RUNS_300, "Triple Century", "Score 300 runs in an innings", "3️⃣0️⃣0️⃣"),
            Achievement(AchievementKind.FASTEST_FIFTY, "Fastest Fifty", "Score 50 runs in record balls", "⚡️5️⃣0️⃣"),
            Achievement(AchievementKind.FASTEST_HUNDRED, "Fastest Hundred", "Score 100 runs in record balls", "⚡️💯"),
            Achievement(AchievementKind.MOST_SIXES, "Most Sixes", "Hit the most sixes in a match", "6️⃣"),
            // Bowling
            Achievement(AchievementKind.WICKETS_3, "Three-for", "Take 3 wickets in an innings", "🎯"),
            Achievement(AchievementKind.WICKETS_4, "Four-for", "Take 4 wickets in an innings", "4️⃣"),
            Achievement(AchievementKind.WICKETS_5, "Five-for", "Take 5 wickets in an innings", "5️⃣"),
            Achievement(AchievementKind.WICKETS_10, "10 Wickets", "Take 10 wickets in a match", "🔟"),
            Achievement(AchievementKind.MAIDEN_OVER, "Maiden Over", "Bowl a maiden over", "🦢"),
            Achievement(AchievementKind.HAT_TRICK, "Hat-trick", "Take 3 wickets in 3 balls", "🎩"),
            Achievement(AchievementKind.BEST_ECONOMY, "Best Economy", "Best economy rate in a match", "💸"),
            // Team
            Achievement(AchievementKind.TEAM_200, "200+ Team Total", "Team scores 200+ runs", "2️⃣0️⃣0️⃣"),
            Achievement(AchievementKind.TEAM_10_WKT_WIN, "10 Wicket Win", "Win by 10 wickets", "🔟🏏"),
            Achievement(AchievementKind.SUCCESSFUL_CHASE, "Successful Chase", "Chase down a target", "🏃‍♂️"),
            // Special
            Achievement(AchievementKind.PLAYER_OF_MATCH, "Player of the Match", "Awarded Player of the Match", "⭐️"),
            Achievement(AchievementKind.RECORD_PARTNERSHIP, "Record Partnership", "Record partnership in a match", "🤝")
        )
    }
}

