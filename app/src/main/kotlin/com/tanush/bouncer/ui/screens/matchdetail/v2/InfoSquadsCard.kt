package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.data.history.ArchivedInfo
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The squad lists a backfilled card kept. Cricbuzz's list can include players who did not play, so
 * the card says so rather than calling it the eleven.
 */
internal fun LazyListScope.infoSquadsItems(info: ArchivedInfo?) {
    if (info == null || info.squads.isEmpty()) return
    item(key = "archived_squads_header") { SectionHeaderV2(title = stringResource(R.string.match_info_squad_lists)) }
    item(key = "archived_squads_card") {
        BouncerCard {
            Column(
                Modifier.padding(BouncerThemeV2.spacing.md),
                verticalArrangement = Arrangement.spacedBy(BouncerThemeV2.spacing.sm),
            ) {
                info.squads.forEach { (team, players) ->
                    Text(team, style = BouncerThemeV2.typography.titleSmall, color = BouncerThemeV2.colors.onSurface)
                    Text(
                        players.joinToString(", "),
                        style = BouncerThemeV2.typography.bodySmall,
                        color = BouncerThemeV2.colors.textSecondary,
                    )
                }
                Text(
                    stringResource(R.string.match_info_squad_lists_note),
                    style = BouncerThemeV2.typography.labelMedium,
                    color = BouncerThemeV2.colors.textSecondary,
                )
            }
        }
    }
}
