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
    // Primary Tab Destinations (Bottom Navigation: Home / Series / News / More)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Home tab - Live matches, featured content */
    data object Home : BouncerScreen("home")

    /** Favourites: reorder, add and remove, pushed from the More tab's Your teams. */
    data object Favorites : BouncerScreen("favourites")

    /** More tab: your teams, the settings, appearance, about. */
    data object More : BouncerScreen("more")

    // ═══════════════════════════════════════════════════════════════════════════
    // Secondary Screens (Navigated from tabs)
    // ═══════════════════════════════════════════════════════════════════════════



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
     * Player comparison screen.
     */
    data object PlayerComparison : BouncerScreen("compare-players") {
        /** The player whose page Compare was tapped on, already in the first slot. */
        const val ARG_FIRST = "first"
        const val ROUTE_PATTERN = "compare-players?first={first}"

        fun createRoute(first: String?): String =
            if (first.isNullOrBlank()) route else "$route?first=${first.encodeUrlSafe()}"

        val arguments = listOf(
            navArgument(ARG_FIRST) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )
    }

    /** In-app notification inbox */
    data object Notifications : BouncerScreen("notifications")

    /** Match reminders set from match pages */
    data object Reminders : BouncerScreen("reminders")

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
     * A player's Cricsheet career (`s/p/<registryId>.json`), for people Cricbuzz does not list.
     * [name] is only the heading until the record loads.
     */
    data class HistoryPlayer(val id: String) : BouncerScreen("history-player/$id") {
        companion object {
            const val ROUTE_PATTERN = "history-player/{id}?name={name}"
            const val ARG_ID = "id"
            const val ARG_NAME = "name"

            fun createRoute(id: String, name: String? = null): String {
                val base = "history-player/${id.encodeUrlSafe()}"
                return if (name.isNullOrBlank()) base else "$base?name=${name.encodeUrlSafe()}"
            }

            val arguments = listOf(
                navArgument(ARG_ID) { type = NavType.StringType; nullable = false },
                navArgument(ARG_NAME) { type = NavType.StringType; nullable = true; defaultValue = null },
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
         *
         * Lazy, like [moreChildRoutes]: these are static fields of BouncerScreen, so they are
         * built while BouncerScreen initialises. When the first class touched is one of the
         * objects named here (`BouncerScreen.Home.route` in a top-level val), that object's
         * own initialiser is still waiting on its superclass and its INSTANCE reads as null —
         * the list held a null tab and the first recomposition threw (ANDROID-5F).
         */
        val primaryTabs by lazy { listOf(Home, SeriesList, News, More) }

        /**
         * Routes that should highlight the "More" tab when active.
         */
        val moreChildRoutes by lazy {
            setOf(
                Favorites.route,
                About.route,
                DevTools.route,
            )
        }

        /**
         * Pages opened from any tab — a player, a team, search, the comparison, Alerts. They belong
         * to no tab, so the bar keeps the one they were opened from: a player opened from a series
         * used to light up More, and Alerts behind Home's bell did too.
         */
        val sharedRoutes by lazy {
            setOf(Search.route, PlayerComparison.route, Notifications.route, Reminders.route)
        }

        fun isSharedRoute(route: String): Boolean =
            route.substringBefore('?') in sharedRoutes || route.startsWith("player/") || route.startsWith("history-player/") ||
                route.startsWith("team/")

        /**
         * Check if a route is a child of the "More" tab.
         */
        fun isMoreChildRoute(route: String?): Boolean {
            // Compared without the query string: "search?tab={tab}" is Search.
            return route?.substringBefore('?') in moreChildRoutes
        }

        /**
         * Get the parent tab route for a given screen route.
         * Returns the route itself if it's a primary tab; Home for a page no tab owns.
         */
        fun getParentTab(route: String?): String = ownerTab(route) ?: Home.route

        /** The tab that owns [route], or null for a [isSharedRoute] page, which keeps whatever tab was lit. */
        fun ownerTab(route: String?): String? = when {
            route == null -> Home.route
            route in primaryTabs.map { it.route } -> route
            isSharedRoute(route) -> null
            route.startsWith("news/") -> News.route
            isMoreChildRoute(route) -> More.route
            route.startsWith("match/") -> Home.route
            route.startsWith("series/") -> SeriesList.route
            else -> Home.route
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
