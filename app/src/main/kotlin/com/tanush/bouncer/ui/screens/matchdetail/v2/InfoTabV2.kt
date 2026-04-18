package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.StatRowV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * InfoTabV2 — Match information: series, venue, date, toss, etc.
 */
@Composable
fun InfoTabV2(match: CricketMatch) {
    val spacing = BouncerThemeV2.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "info_header") {
            SectionHeaderV2(title = "Match Information")
        }
        item(key = "info_card") {
            BouncerCard {
                val pad = Modifier.padding(horizontal = spacing.md)
                Spacer(Modifier.height(spacing.sm))
                StatRowV2(label = "Series", value = match.matchTitle, modifier = pad)
                StatRowV2(label = "Match", value = match.matchType, modifier = pad)
                StatRowV2(label = "Date", value = match.date, modifier = pad)
                if (match.matchTime.isNotBlank()) {
                    StatRowV2(label = "Time", value = match.matchTime, modifier = pad)
                }
                StatRowV2(label = "Venue", value = match.venue, modifier = pad, showDivider = false)
                Spacer(Modifier.height(spacing.sm))
            }
        }

        if (match.info.isNotBlank()) {
            item(key = "extra_info_header") {
                SectionHeaderV2(title = "Additional Info")
            }
            item(key = "extra_info") {
                BouncerCard {
                    Spacer(Modifier.height(spacing.sm))
                    StatRowV2(
                        label = "Info",
                        value = match.info,
                        modifier = Modifier.padding(horizontal = spacing.md),
                        showDivider = false,
                    )
                    Spacer(Modifier.height(spacing.sm))
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(spacing.xxl)) }
    }
}
