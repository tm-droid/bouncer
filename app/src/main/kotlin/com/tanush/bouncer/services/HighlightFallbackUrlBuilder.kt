package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.dao.HighlightDao
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.Breadcrumb

object HighlightFallbackUrlBuilder {
    private const val TAG = "HighlightFallbackUrlBuilder"

    suspend fun buildFallbackUrl(
        highlight: HighlightEntity,
        dao: HighlightDao
    ): String? {
        if (!highlight.embedUrl.isNullOrBlank()) {
            return highlight.embedUrl
        }

        val leagueLower = highlight.leagueName.lowercase()
        return when {
            isIccEvent(leagueLower) -> {
                buildIccUrl(highlight, dao)
            }
            isIpl(leagueLower) -> {
                buildIplUrl(highlight, dao)
            }
            else -> {
                reportMissingEmbed(highlight)
                null
            }
        }
    }

    private suspend fun buildIccUrl(
        highlight: HighlightEntity,
        dao: HighlightDao
    ): String? {
        val cached = dao.cachedIccMatchId(highlight.matchedBouncerMatchId ?: "")
        if (cached != null) {
            return "https://www.icc-cricket.com/matches/$cached/${slugify(highlight.homeTeam)}-vs-${slugify(highlight.awayTeam)}"
        }
        Log.d(TAG, "ICC match ID not cached for ${highlight.matchedBouncerMatchId}; cannot derive URL")
        reportMissingEmbed(highlight)
        return null
    }

    private suspend fun buildIplUrl(
        highlight: HighlightEntity,
        dao: HighlightDao
    ): String? {
        val cached = dao.cachedIplMatchId(highlight.matchedBouncerMatchId ?: "")
        if (cached != null) {
            return "https://www.iplt20.com/match/$cached"
        }
        Log.d(TAG, "IPL match ID not cached for ${highlight.matchedBouncerMatchId}; cannot derive URL")
        reportMissingEmbed(highlight)
        return null
    }

    private fun reportMissingEmbed(highlight: HighlightEntity) {
        Log.w(TAG, "No embed URL and no fallback derivable for highlight ${highlight.id}")
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "highlight.missing_embed"
            message = "Highlight has no embed URL and no fallback source"
            level = SentryLevel.WARNING
            setData("league", highlight.leagueName)
            setData("home_team", highlight.homeTeam)
            setData("away_team", highlight.awayTeam)
            setData("date", highlight.matchStartDate)
            setData("highlight_id", highlight.id)
        })
    }

    private fun isIccEvent(leagueLower: String): Boolean {
        return leagueLower.contains("icc") ||
                leagueLower.contains("champions trophy") ||
                leagueLower.contains("world cup") ||
                leagueLower.contains("world test championship") ||
                leagueLower.contains("t20i")
    }

    private fun isIpl(leagueLower: String): Boolean {
        return leagueLower.contains("ipl") ||
                leagueLower.contains("indian premier league")
    }

    private fun slugify(team: String): String {
        return team.lowercase()
            .replace(" ", "-")
            .replace("[^a-z0-9-]".toRegex(), "")
            .replace("-+".toRegex(), "-")
            .trim('-')
    }
}
