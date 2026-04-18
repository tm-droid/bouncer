package com.tanush.bouncer.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Type-safe navigation routes for the Bouncer app.
 *
 * This sealed class hierarchy provides compile-time safety for navigation,
 * proper argument handling, and deep linking support structure.
 */
sealed class BouncerScreen(val route: String) {

    // ═══════════════════════════════════════════════════════════════════════════
    // Primary Tab Destinations (Bottom Navigation)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Home tab - Live matches, featured content */
    data object Home : BouncerScreen("home")

    /** All Matches tab - Complete match listings */
    data object Matches : BouncerScreen("all_matches")

    /** Favourites tab - Saved teams and matches */
    data object Favorites : BouncerScreen("favourites")

    /** Stats/Trophy Room tab - User achievements and statistics */
    data object Stats : BouncerScreen("stats")

    /** More tab - Settings, About, News, DevTools access */
    data object More : BouncerScreen("more")

    // ═══════════════════════════════════════════════════════════════════════════
    // Secondary Screens (Navigated from tabs)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Settings screen - App preferences and configuration */
    data object Settings : BouncerScreen("settings")

    /** Appearance customization screen */
    data object Appearance : BouncerScreen("appearance")

    /** About screen - App info, licenses, credits */
    data object About : BouncerScreen("about")

    /** Developer Tools screen - API diagnostics and debug */
    data object DevTools : BouncerScreen("devtools")

    /** News listing screen */
    data object News : BouncerScreen("news")

    /** Achievements gallery screen */
    data object Achievements : BouncerScreen("achievements")

    /** Trophy room (legacy access) */
    data object Trophy : BouncerScreen("trophy-room")

    /** Extended stats overview screen (analytics details) */
    data object StatsOverview : BouncerScreen("stats_overview")

    /** AR Ball Tracking screen */
    data object ArTracking : BouncerScreen("ar-tracking")

    /** Intelligence/Activity Feed screen */
    data object ActivityFeed : BouncerScreen("activity-feed")

    // ═══════════════════════════════════════════════════════════════════════════
    // Parameterized Screens (With Arguments)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Match detail screen with match ID argument.
     *
     * @property matchId The unique identifier for the match
     */
    data class MatchDetail(val matchId: String) : BouncerScreen("match/$matchId") {
        companion object {
            const val ROUTE_PATTERN = "match/{matchId}"
            const val ARG_MATCH_ID = "matchId"

            fun createRoute(matchId: String): String = "match/${matchId.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_MATCH_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        }
    }

    /**
     * News article detail screen.
     *
     * @property articleId The unique identifier for the news article
     */
    data class NewsDetail(val articleId: String) : BouncerScreen("news/$articleId") {
        companion object {
            const val ROUTE_PATTERN = "news/{articleId}"
            const val ARG_ARTICLE_ID = "articleId"

            fun createRoute(articleId: String): String = "news/${articleId.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_ARTICLE_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        }
    }

    /**
     * Player detail screen.
     *
     * @property playerId The unique identifier for the player
     */
    data class PlayerDetail(val playerId: String) : BouncerScreen("player/$playerId") {
        companion object {
            const val ROUTE_PATTERN = "player/{playerId}"
            const val ARG_PLAYER_ID = "playerId"

            fun createRoute(playerId: String): String = "player/${playerId.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_PLAYER_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        }
    }

    /**
     * Team squad screen.
     *
     * @property teamId The unique identifier for the team
     * @property matchId Optional match ID for match-specific squad
     */
    data class Squad(val teamId: String, val matchId: String? = null) : BouncerScreen(
        if (matchId != null) "squad/$teamId/$matchId" else "squad/$teamId"
    ) {
        companion object {
            const val ROUTE_PATTERN = "squad/{teamId}"
            const val ROUTE_PATTERN_WITH_MATCH = "squad/{teamId}/{matchId}"
            const val ARG_TEAM_ID = "teamId"
            const val ARG_MATCH_ID = "matchId"

            fun createRoute(teamId: String, matchId: String? = null): String =
                if (matchId != null) {
                    "squad/${teamId.encodeUrlSafe()}/${matchId.encodeUrlSafe()}"
                } else {
                    "squad/${teamId.encodeUrlSafe()}"
                }

            val arguments = listOf(
                navArgument(ARG_TEAM_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )

            val argumentsWithMatch = listOf(
                navArgument(ARG_TEAM_ID) {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument(ARG_MATCH_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        }
    }

    /**
     * Match info/scorecard screen.
     *
     * @property matchId The unique identifier for the match
     */
    data class MatchInfo(val matchId: String) : BouncerScreen("match-info/$matchId") {
        companion object {
            const val ROUTE_PATTERN = "match-info/{matchId}"
            const val ARG_MATCH_ID = "matchId"

            fun createRoute(matchId: String): String = "match-info/${matchId.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_MATCH_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Companion Object - Navigation Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    companion object {
        /**
         * All primary tab routes for bottom navigation.
         */
        val primaryTabs = listOf(Home, Matches, News, More)

        /**
         * Routes that should highlight the "More" tab when active.
         */
        val moreChildRoutes = setOf(
            Stats.route,
            Trophy.route,
            StatsOverview.route,
            Settings.route,
            Appearance.route,
            About.route,
            DevTools.route,
            Achievements.route,
            ArTracking.route,
            ActivityFeed.route
        )

        /**
         * Check if a route is a child of the "More" tab.
         */
        fun isMoreChildRoute(route: String?): Boolean {
            return route in moreChildRoutes
        }

        /**
         * Get the parent tab route for a given screen route.
         * Returns the route itself if it's a primary tab.
         */
        fun getParentTab(route: String?): String {
            return when {
                route == null -> Home.route
                route in primaryTabs.map { it.route } -> route
                route.startsWith("news/") -> News.route
                isMoreChildRoute(route) -> More.route
                route.startsWith("match/") || route.startsWith("match-info/") -> Matches.route
                route.startsWith("player/") -> More.route
                route.startsWith("squad/") -> Matches.route
                else -> Home.route
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Navigation argument keys (for SavedStateHandle access)
// ═══════════════════════════════════════════════════════════════════════════════

object NavArgs {
    const val MATCH_ID = "matchId"
    const val ARTICLE_ID = "articleId"
    const val PLAYER_ID = "playerId"
    const val TEAM_ID = "teamId"
}

// ═══════════════════════════════════════════════════════════════════════════════
// URL Encoding Utilities
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * URL-encode a string for safe use in navigation routes.
 */
fun String.encodeUrlSafe(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

/**
 * URL-decode a navigation argument.
 */
fun String.decodeUrlSafe(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8.toString())
