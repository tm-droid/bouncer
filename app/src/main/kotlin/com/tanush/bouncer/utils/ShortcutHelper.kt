package com.tanush.bouncer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Manages dynamic launcher shortcuts for quick access to live matches.
 */
object ShortcutHelper {

    private const val SHORTCUT_LIVE = "shortcut_live_matches"
    private const val MAX_SHORTCUTS = 4
    private const val DEEP_LINK_SCHEME = "app"
    private const val DEEP_LINK_HOST = "bouncer"

    /**
     * Publishes dynamic shortcuts: a persistent "Live Matches" entry plus
     * up to two per-match shortcuts for the top live matches.
     */
    fun updateDynamicShortcuts(context: Context, liveMatches: List<CricketMatch>) {
        try {
            val shortcuts = mutableListOf<ShortcutInfoCompat>()

            // Always-present "Live Matches" shortcut
            shortcuts += ShortcutInfoCompat.Builder(context, SHORTCUT_LIVE)
                .setShortLabel("Live")
                .setLongLabel("Live Matches")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/live"))
                )
                .setRank(0)
                .build()

            // Per-match shortcuts for the top 2 live matches
            liveMatches.filter { it.isLive }
                .take(2)
                .forEachIndexed { index, match ->
                    val shortLabel = match.teams.joinToString(" vs ") { it.name }
                    shortcuts += ShortcutInfoCompat.Builder(context, "match_${match.id}")
                        .setShortLabel(shortLabel.take(25))
                        .setLongLabel(match.matchTitle)
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
                        .setIntent(
                            Intent(Intent.ACTION_VIEW, Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/match/${match.id}"))
                        )
                        .setRank(index + 1)
                        .build()
                }

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts.take(MAX_SHORTCUTS))

            Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    category = "shortcuts"
                    message = "Dynamic shortcuts updated"
                    level = SentryLevel.INFO
                    setData("count", shortcuts.size.toString())
                }
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Reports a shortcut interaction to the system for ranking purposes. */
    fun reportShortcutUsed(context: Context, shortcutId: String) {
        try {
            ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)

            Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    category = "shortcuts"
                    message = "Shortcut used: $shortcutId"
                    level = SentryLevel.INFO
                }
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}
