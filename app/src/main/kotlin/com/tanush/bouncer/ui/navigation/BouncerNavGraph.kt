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
    // Primary Tab Destinations (Bottom Navigation: Home / Matches / News / More)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Home tab - Live matches, featured content */
    data object Home : BouncerScreen("home")

    /** All Matches - complete match listings, pushed from Home's "All matches" link (not a tab) */
    data object Matches : BouncerScreen("all_matches")

    /** Favourites - saved teams and matches (deep-link route, not a bottom tab) */
    data object Favorites : BouncerScreen("favourites")

    /** More tab - Settings, Appearance, About, DevTools access */
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

    /** News tab - cricket headlines (bottom-nav destination) */
    data object News : BouncerScreen("news")

    /**
     * Search screen. The optional `tab` opens it on one result tab — the Series and News tabs
     * open it on Tournaments and News.
     */
    data object Search : BouncerScreen("search") {
        const val ROUTE_PATTERN = "search?tab={tab}"
        const val ARG_TAB = "tab"

        fun createRoute(tab: String? = null): String =
            if (tab == null) route else "search?tab=${tab.encodeUrlSafe()}"

        val arguments = listOf(
            navArgument(ARG_TAB) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    }

    /** Series list screen */
    data object SeriesList : BouncerScreen("series")

    /**
     * Series detail screen with series ID argument.
     */
    data class SeriesDetail(val seriesId: String) : BouncerScreen("series/$seriesId") {
        companion object {
            const val ROUTE_PATTERN = "series/{seriesId}"
            const val ARG_SERIES_ID = "seriesId"

            fun createRoute(seriesId: String): String = "series/${seriesId.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_SERIES_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        }
    }

    /**
     * Points table screen with series ID argument.
     */
    data class PointsTable(val seriesId: String) : BouncerScreen("points-table/$seriesId") {
        companion object {
            const val ROUTE_PATTERN = "points-table/{seriesId}"
            const val ARG_SERIES_ID = "seriesId"

            fun createRoute(seriesId: String): String = "points-table/${seriesId.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_SERIES_ID) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        }
    }

    /**
     * Player comparison screen.
     */
    data object PlayerComparison : BouncerScreen("compare-players")

    /** In-app notification inbox */
    data object Notifications : BouncerScreen("notifications")

    /** First-launch onboarding flow */
    data object Onboarding : BouncerScreen("onboarding")

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
            const val ROUTE_PATTERN = "player/{playerId}?cbId={cbId}"
            const val ARG_PLAYER_ID = "playerId"

            /** Cricbuzz profile id, when the caller has one. Opens that exact player. */
            const val ARG_CRICBUZZ_ID = "cbId"

            fun createRoute(playerId: String, cricbuzzId: String? = null): String {
                val base = "player/${playerId.encodeUrlSafe()}"
                return if (cricbuzzId.isNullOrBlank()) base else "$base?cbId=${cricbuzzId.encodeUrlSafe()}"
            }

            val arguments = listOf(
                navArgument(ARG_PLAYER_ID) {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument(ARG_CRICBUZZ_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            )
        }
    }

    /**
     * Team detail screen with team name argument.
     */
    data class TeamDetail(val teamName: String) : BouncerScreen("team/$teamName") {
        companion object {
            const val ROUTE_PATTERN = "team/{teamName}"
            const val ARG_TEAM_NAME = "teamName"

            fun createRoute(teamName: String): String = "team/${teamName.encodeUrlSafe()}"

            val arguments = listOf(
                navArgument(ARG_TEAM_NAME) {
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
        val primaryTabs = listOf(Home, SeriesList, News, More)

        /**
         * Routes that should highlight the "More" tab when active.
         */
        val moreChildRoutes = setOf(
            Settings.route,
            Appearance.route,
            About.route,
            DevTools.route,
            Search.route,
            PlayerComparison.route,
            Notifications.route,
        )

        /**
         * Check if a route is a child of the "More" tab.
         */
        fun isMoreChildRoute(route: String?): Boolean {
            // Compared without the query string: "search?tab={tab}" is Search.
            return route?.substringBefore('?') in moreChildRoutes
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
                route == Matches.route -> Home.route
                route.startsWith("match/") -> Home.route
                route.startsWith("player/") -> More.route
                route.startsWith("team/") -> More.route
                route.startsWith("series/") -> SeriesList.route
                route.startsWith("points-table/") -> SeriesList.route
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
    const val SERIES_ID = "seriesId"
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
