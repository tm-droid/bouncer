package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The fills for a two-sided proportional bar, team-coloured only when the registry knows **both**
 * sides.
 *
 * `TeamColorRegistry.forTeam` falls back to hashing a name into a hue. That is colour which
 * identifies nobody, and on a bar it reads as a brand: it painted Amsterdam Flames magenta against
 * Dublin Guardians blue on the match-detail win-probability card. The rule — team colour only where
 * it identifies a team, never as decoration — lived inline in `HeroMatchCard` alone, so Home obeyed
 * it and the match detail did not. It lives here now so a third bar cannot miss it again.
 *
 * [team1Key] and [team2Key] are full team names, not the acronyms a card displays: `isKnown` needs
 * `MIN_FUZZY_KEY` = 4 characters before it will fuzzy-match, so a three-letter code is never known
 * unless the alias table lists it verbatim.
 *
 * Known colours are lifted against [fill] rather than the page background, because a colour that
 * clears its contrast floor on the app background can still read as an empty segment on a card.
 */
@Composable
fun rememberSegmentColors(
    team1Key: String?,
    team2Key: String?,
    fill: Color,
    minContrast: Float,
): Pair<Color, Color> {
    val colors = BouncerThemeV2.colors
    if (!TeamColorRegistry.isKnown(team1Key) || !TeamColorRegistry.isKnown(team2Key)) {
        return colors.onSurface.copy(alpha = NEUTRAL_LEAD_ALPHA) to
            colors.textSecondary.copy(alpha = NEUTRAL_TRAIL_ALPHA)
    }
    val palette = rememberMatchTeamPalette(team1Key, team2Key)
    return palette.home.primary.liftForContrast(fill, minContrast) to
        palette.away.primary.liftForContrast(fill, minContrast)
}

/** A strong and a faint neutral: still two distinguishable shares, with no claim to be a team. */
private const val NEUTRAL_LEAD_ALPHA = 0.8f
private const val NEUTRAL_TRAIL_ALPHA = 0.25f
