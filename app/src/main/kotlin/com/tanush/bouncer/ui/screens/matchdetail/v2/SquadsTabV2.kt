package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ImpactSubEvent
import com.tanush.bouncer.network.MatchSquads
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.BouncerRowGroup
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Who is playing: the elevens (or the squads before the toss), any impact substitute brought on,
 * and Cricinfo's team news as written. The team sheet used to sit in the middle of Info, between
 * the officials and the weather.
 */
@Composable
fun SquadsTabV2(
    match: CricketMatch,
    squads: MatchSquads?,
    impactSubs: List<ImpactSubEvent>,
    teamNews: String?,
) {
    val spacing = BouncerThemeV2.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        teamSheetItems(match, squads)
        impactSubItems(impactSubs)
        teamNews?.takeIf { it.isNotBlank() }?.let { news ->
            item(key = "team_news_header") { SectionHeaderV2(title = stringResource(R.string.squads_team_news)) }
            item(key = "team_news") {
                BouncerCard {
                    Text(
                        text = news,
                        style = BouncerThemeV2.typography.bodyMedium,
                        color = BouncerThemeV2.colors.onSurface,
                        modifier = Modifier.padding(spacing.md),
                    )
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}

private fun LazyListScope.impactSubItems(impactSubs: List<ImpactSubEvent>) {
    if (impactSubs.isEmpty()) return
    item(key = "impact_header") { SectionHeaderV2(title = stringResource(R.string.squads_impact_subs)) }
    item(key = "impact_subs") {
        BouncerRowGroup(impactSubs) { sub ->
            val colors = BouncerThemeV2.colors
            val typography = BouncerThemeV2.typography
            Column(Modifier.padding(BouncerThemeV2.spacing.md)) {
                Text(
                    text = stringResource(R.string.squads_impact_sub_line, sub.playerIn, sub.playerOut),
                    style = typography.bodyMedium,
                    color = colors.onSurface,
                )
                val over = sub.over.takeIf { it.isNotBlank() }?.let { stringResource(R.string.squads_impact_sub_over, it) }
                val detail = listOf(sub.team, over)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(text = detail, style = typography.labelMedium, color = colors.textSecondary)
                }
            }
        }
    }
}
