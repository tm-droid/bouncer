package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.network.PlayerImageRegistry
import com.tanush.bouncer.network.PlayerImages
import com.tanush.bouncer.ui.components.v2.PersonAvatar
import com.tanush.bouncer.ui.navigation.BouncerScreen
import com.tanush.bouncer.ui.navigation.LocalOpenRoute
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

// What a scorecard row links to and adds under its figures: the player's profile, a bowler's
// wides and no-balls, and the rest of the eleven.

/** "2 wd · 1 nb", or null when the bowler gave neither. */
@Composable
internal fun bowlingExtras(bowler: BowlerStats): String? {
    val wides = bowler.wides.toIntOrNull()?.takeIf { it > 0 }
    val noBalls = bowler.noBalls.toIntOrNull()?.takeIf { it > 0 }
    return listOfNotNull(
        wides?.let { stringResource(R.string.scorecard_wides, it) },
        noBalls?.let { stringResource(R.string.scorecard_no_balls, it) },
    ).joinToString(" · ").ifEmpty { null }
}

/**
 * Opens the player's profile, where something can navigate (the match overlay provides it). The
 * Cricbuzz id from the row's profile link goes with the name, so two players sharing a name open
 * the right one.
 */
@Composable
internal fun Modifier.playerLink(name: String, profileUrl: String): Modifier {
    val open = LocalOpenRoute.current ?: return this
    if (name.isBlank()) return this
    val label = stringResource(R.string.cd_open_player, name)
    val plain = PlayerImageRegistry.withoutRoleTags(name)
    val id = CricBuzzUrls.profileIdOf(profileUrl) ?: PlayerImages.playerId(plain)
    return clickable(onClickLabel = label) {
        open(BouncerScreen.PlayerDetail.createRoute(plain, id))
    }
}

/** A row's leading photo. Decorative: the row already reads the name, so it would say it twice. */
@Composable
internal fun RowScope.RowAvatar(name: String, profileUrl: String, size: Dp) {
    PersonAvatar(
        name = name,
        id = CricBuzzUrls.profileIdOf(profileUrl),
        size = size,
        modifier = Modifier.clearAndSetSemantics {},
    )
    Spacer(Modifier.width(AVATAR_GAP))
}

/** "Yet to bat" while the innings is on, "Did not bat" once it is over: the rest of the eleven. */
@Composable
internal fun YetToBatRow(names: List<String>, battingNow: Boolean) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = YET_TO_BAT_PADDING_H, vertical = YET_TO_BAT_PADDING_V)) {
        Text(
            text = stringResource(if (battingNow) R.string.scorecard_yet_to_bat else R.string.scorecard_did_not_bat),
            style = typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(text = names.joinToString(", "), style = typography.bodySmall, color = colors.onSurface)
    }
    HorizontalDivider(color = colors.divider)
}

private val YET_TO_BAT_PADDING_H = 16.dp
private val YET_TO_BAT_PADDING_V = 10.dp
