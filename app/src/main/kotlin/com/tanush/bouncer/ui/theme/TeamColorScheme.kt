package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.Gray900

@Immutable
data class TeamColors(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val onSurface: Color,
    val onPrimary: Color,
    val border: Color = primary.copy(alpha = 0.45f),
    val accent: Color = secondary.copy(alpha = 0.24f),
    /** [primary] pushed to AA against the ground, for team names and scores drawn as text. */
    val ink: Color = primary,
)

@Immutable
data class MatchTeamPalette(
    val home: TeamColors,
    val away: TeamColors
) {
    val gradientStops: List<Color>
        get() = listOf(home.primary.copy(alpha = 0.22f), away.primary.copy(alpha = 0.22f))
}

object TeamColorRegistry {
    /**
     * Crest-derived colours from `assets/TeamColors.json`, keyed by normalized name.
     *
     * Generated offline by `tools/team-colors/extract.mjs` and loaded once at startup. Empty until
     * then, and empty for good if the asset is missing: every lookup falls through to curated or
     * neutral, so a missing file costs colour, never correctness.
     */
    @Volatile private var extracted: Map<String, Pair<Color, Color>> = emptyMap()

    /** Install the crest-derived table. Keys are normalized here so the asset can carry raw names. */
    fun loadExtracted(source: Map<String, Pair<Color, Color>>) {
        extracted = source.entries.associate { (name, pair) -> normalize(name) to pair }
    }

    /**
     * The ground a team's brand colour is adjusted against.
     *
     * Brand colours are fixed, so they have to be nudged to clear contrast against whatever they are
     * drawn on — and that ground is not a constant. The registry baked every team against
     * [BlackBackground] regardless of theme, so in light mode a bar or label came out tuned for a
     * surface it was never drawn on.
     */
    @Immutable
    data class Ground(val background: Color, val surface: Color, val ink: Color) {
        val isLight: Boolean get() = background.relativeLuminance() >= LIGHT_GROUND_LUMINANCE
    }

    val DarkGround = Ground(BlackBackground, BlackSurface, TextPrimaryDark)

    val default: TeamColors = buildTeamColors(GoldDark, GoldPrimaryDark, DarkGround)

    /**
     * The team's colours on [ground]: curated kit colours first, crest-derived second, neutral grey
     * when neither knows the side.
     *
     * The neutral used to be a hue hashed from the name, which painted associate fixtures magenta
     * against lime — colour that identified nobody. Callers that only want colour when it means
     * something ask [isKnown] first; [rememberTeamInk] does that for text.
     */
    fun forTeam(teamName: String?, ground: Ground = DarkGround): TeamColors {
        if (teamName.isNullOrBlank()) return if (ground == DarkGround) default else buildTeamColors(GoldDark, GoldPrimaryDark, ground)
        val pair = resolve(teamName) ?: (NEUTRAL_PRIMARY to NEUTRAL_SECONDARY)
        return buildTeamColors(pair.first, pair.second, ground)
    }

    /**
     * True when [teamName] has a real colour here — curated or crest-derived — rather than the
     * neutral fallback.
     *
     * Fuzzy matching is by whole words and needs a key of [MIN_FUZZY_KEY] characters: substring
     * matching let "oman" claim "romania", and a two-letter code like "LL" claim Royal Challengers.
     */
    fun isKnown(teamName: String?): Boolean = !teamName.isNullOrBlank() && resolve(teamName) != null

    fun forMatch(homeTeam: String?, awayTeam: String?, ground: Ground = DarkGround): MatchTeamPalette {
        return MatchTeamPalette(
            home = forTeam(homeTeam, ground),
            away = forTeam(awayTeam, ground)
        )
    }

    private fun resolve(teamName: String): Pair<Color, Color>? {
        val normalized = normalize(teamName)
        val key = TEAM_COLOR_ALIASES[normalized.uppercase()] ?: normalized
        CURATED_TEAM_COLORS[key]?.let { return it }
        extracted[key]?.let { return it }
        if (key.length < MIN_FUZZY_KEY) return null
        return fuzzy(CURATED_TEAM_COLORS, key) ?: fuzzy(extracted, key)
    }

    /** "India A" and "England Lions" take their parent's colours; "Romania" does not take Oman's. */
    private fun fuzzy(table: Map<String, Pair<Color, Color>>, key: String): Pair<Color, Color>? {
        val padded = " $key "
        return table.entries.firstOrNull { (name, _) ->
            padded.contains(" $name ") || " $name ".contains(padded)
        }?.value
    }

    private fun buildTeamColors(primaryRaw: Color, secondaryRaw: Color, ground: Ground): TeamColors {
        val primary = primaryRaw.ensureContrast(background = ground.background, minimumRatio = 3.0f)
        val secondary = secondaryRaw.ensureContrast(background = ground.background, minimumRatio = 2.8f)
        // A team-tinted card: a wash of brand colour over the ground, in the direction the ground
        // goes. Darkening toward a dark surface reads as tinted charcoal; the same recipe on white
        // reads as mud.
        val surface = if (ground.isLight) {
            primary.blend(ground.surface, LIGHT_SURFACE_TINT)
        } else {
            primary.darken(0.72f).blend(ground.surface, 0.62f)
        }
        val onSurface = ground.ink.ensureContrast(surface, minimumRatio = 4.5f)
        val onPrimary = primary.contrastingTextColor().ensureContrast(primary, minimumRatio = 4.5f)
        // Text in team colour has to clear AA on both grounds it is drawn on: the card and the page.
        val ink = primaryRaw
            .ensureContrast(background = ground.surface, minimumRatio = INK_RATIO)
            .ensureContrast(background = ground.background, minimumRatio = INK_RATIO)

        return TeamColors(
            primary = primary,
            secondary = secondary,
            surface = surface,
            onSurface = onSurface,
            onPrimary = onPrimary,
            border = primary.copy(alpha = 0.52f),
            accent = secondary.copy(alpha = 0.22f),
            ink = ink,
        )
    }

    private const val LIGHT_GROUND_LUMINANCE = 0.5f
    private const val MIN_FUZZY_KEY = 4
    private const val LIGHT_SURFACE_TINT = 0.88f
    private const val INK_RATIO = 4.6f
    private val NEUTRAL_PRIMARY = Color(0xFF8E959E)
    private val NEUTRAL_SECONDARY = Color(0xFF5C636B)

    internal fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("\\bwomen\\b"), "")
            .replace(Regex("\\bw\\b"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/** The active theme's ground — what team colours are actually drawn on. */
@Composable
fun rememberTeamGround(): TeamColorRegistry.Ground {
    val colors = BouncerThemeV2.colors
    return remember(colors) {
        TeamColorRegistry.Ground(
            background = colors.background,
            surface = colors.surface,
            ink = if (colors.isDark) TextPrimaryDark else Gray900,
        )
    }
}

@Composable
fun rememberTeamColors(teamName: String?): TeamColors {
    val ground = rememberTeamGround()
    return remember(teamName, ground) { TeamColorRegistry.forTeam(teamName, ground) }
}

@Composable
fun rememberMatchTeamPalette(homeTeam: String?, awayTeam: String?): MatchTeamPalette {
    val ground = rememberTeamGround()
    return remember(homeTeam, awayTeam, ground) { TeamColorRegistry.forMatch(homeTeam, awayTeam, ground) }
}

@Composable
fun rememberTeamColors(homeTeam: String?, awayTeam: String?): Pair<TeamColors, TeamColors> {
    val palette = rememberMatchTeamPalette(homeTeam, awayTeam)
    return remember(palette) { palette.home to palette.away }
}

/**
 * The colour to draw [teamName] in as text: its team ink when the registry knows the side, the
 * theme's `onSurface` otherwise. Every team-coloured label goes through here so an
 * unknown side never inherits a colour that means nothing.
 */
@Composable
fun rememberTeamInk(teamName: String?): Color {
    val fallback = BouncerThemeV2.colors.onSurface
    if (!TeamColorRegistry.isKnown(teamName)) return fallback
    return rememberTeamColors(teamName).ink
}
