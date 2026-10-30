package com.tanush.bouncer.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.models.RecentInnings
import com.tanush.bouncer.ui.components.v2.BouncerRowGroup
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale

/**
 * The player's last innings, each with who it was against, in what format and when, under one
 * line of what they add up to. It replaces a glass "performance analytics" card built for
 * comparing several players, which drew this one player's runs as a sparkline with a Bowlers tab
 * that was always empty, and dropped the opponent, format, date and balls the profile carries.
 */
internal fun LazyListScope.playerRecentFormItems(profile: PlayerProfile) {
    val form = buildBatterForm(profile) ?: return
    item(key = "form_header") { SectionHeaderV2(title = stringResource(R.string.player_recent_form)) }
    item(key = "form_summary") {
        Text(
            text = stringResource(
                R.string.player_recent_form_summary,
                String.format(Locale.US, "%.1f", form.average),
                form.recentScores.size,
                form.trend,
            ),
            style = BouncerThemeV2.typography.bodyMedium,
            color = BouncerThemeV2.colors.textSecondary,
            modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.xs),
        )
    }
    item(key = "form_rows") { BouncerRowGroup(profile.recentForm) { InningsRow(it) } }
}

@Composable
private fun InningsRow(innings: RecentInnings) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val opponent = innings.opponent.takeIf { it.isNotBlank() }?.let { stringResource(R.string.player_versus, it) }
    val detail = listOfNotNull(opponent, innings.format.ifBlank { null }, innings.date.ifBlank { null }).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (innings.balls > 0) "${innings.runs} (${innings.balls})" else innings.runs.toString(),
            style = typography.numeric,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f)) {
            if (detail.isNotBlank()) Text(text = detail, style = typography.bodySmall, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/** The profile's own words, when it has any. */
internal fun LazyListScope.playerBioItems(profile: PlayerProfile) {
    val bio = profile.bio.trim().takeIf { it.isNotEmpty() } ?: return
    item(key = "bio_header") { SectionHeaderV2(title = stringResource(R.string.player_about)) }
    item(key = "bio") {
        Text(
            text = bio,
            style = BouncerThemeV2.typography.bodyMedium,
            color = BouncerThemeV2.colors.onSurface,
            modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.xs),
        )
    }
}
