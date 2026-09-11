package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.Gray900
import kotlin.math.absoluteValue

@Immutable
data class TeamColors(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val onSurface: Color,
    val onPrimary: Color,
    val border: Color = primary.copy(alpha = 0.45f),
    val accent: Color = secondary.copy(alpha = 0.24f)
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
    private val knownTeams: Map<String, Pair<Color, Color>> = mapOf(
        "india" to (Color(0xFF0057B8) to Color(0xFFFF9933)),
        "australia" to (Color(0xFFFFC72C) to Color(0xFF006F3D)),
        "england" to (Color(0xFF00247D) to Color(0xFFCF142B)),
        "pakistan" to (Color(0xFF01411C) to Color(0xFFFFFFFF)),
        "south africa" to (Color(0xFF007A4D) to Color(0xFFFFB81C)),
        "new zealand" to (Color(0xFF111111) to Color(0xFFC8102E)),
        "west indies" to (Color(0xFF7B0031) to Color(0xFFFFC72C)),
        "sri lanka" to (Color(0xFF1F3A93) to Color(0xFFFFBE00)),
        "bangladesh" to (Color(0xFF006A4E) to Color(0xFFF42A41)),
        "afghanistan" to (Color(0xFF0057B8) to Color(0xFFD32011)),
        "mumbai indians" to (Color(0xFF004BA0) to Color(0xFFD1A23C)),
        "chennai super kings" to (Color(0xFFFDB913) to Color(0xFF0078BC)),
        "royal challengers bangalore" to (Color(0xFFD71920) to Color(0xFF0B0B0B)),
        "kolkata knight riders" to (Color(0xFF3A225D) to Color(0xFFB4975A)),
        "rajasthan royals" to (Color(0xFFE91E63) to Color(0xFF003594)),
        "punjab kings" to (Color(0xFFED1B24) to Color(0xFFFFFFFF)),
        "sunrisers hyderabad" to (Color(0xFFFF822A) to Color(0xFFFFFFFF)),
        "delhi capitals" to (Color(0xFF0078BC) to Color(0xFFEF1C25)),
        "gujarat titans" to (Color(0xFF1C4E9D) to Color(0xFF47C1BF)),
        "lucknow super giants" to (Color(0xFF004C93) to Color(0xFFA5D9F7))
    )

    private val acronyms: Map<String, String> = mapOf(
        "IND" to "india",
        "AUS" to "australia",
        "ENG" to "england",
        "PAK" to "pakistan",
        "SA" to "south africa",
        "RSA" to "south africa",
        "NZ" to "new zealand",
        "WI" to "west indies",
        "SL" to "sri lanka",
        "BAN" to "bangladesh",
        "AFG" to "afghanistan",
        "MI" to "mumbai indians",
        "CSK" to "chennai super kings",
        "RCB" to "royal challengers bangalore",
        "KKR" to "kolkata knight riders",
        "RR" to "rajasthan royals",
        "PBKS" to "punjab kings",
        "KXIP" to "punjab kings",
        "SRH" to "sunrisers hyderabad",
        "DC" to "delhi capitals",
        "DD" to "delhi capitals",
        "GT" to "gujarat titans",
        "LSG" to "lucknow super giants"
    )

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

    fun forTeam(teamName: String?, ground: Ground = DarkGround): TeamColors {
        if (teamName.isNullOrBlank()) return if (ground == DarkGround) default else buildTeamColors(GoldDark, GoldPrimaryDark, ground)

        val normalized = normalize(teamName)
        val key = acronyms[normalized.uppercase()] ?: normalized

        knownTeams[key]?.let { return buildTeamColors(it.first, it.second, ground) }

        knownTeams.entries.firstOrNull { (name, _) ->
            name.contains(key) || key.contains(name)
        }?.let { return buildTeamColors(it.value.first, it.value.second, ground) }

        return hashFallback(key, ground)
    }

    /**
     * True when [teamName] has a real brand colour here rather than one derived from a hash of its name.
     *
     * The hash fallback is fine for telling two teams apart in a detail view, but on a list it painted
     * arbitrary magenta/lime and violet/lime pairs for every associate and franchise side it did not
     * know. Callers that only want colour when it means something ask this first. Fuzzy matching needs
     * a key of [MIN_FUZZY_KEY] characters, or "LL" would claim Royal Challengers' colours.
     */
    fun isKnown(teamName: String?): Boolean {
        if (teamName.isNullOrBlank()) return false
        val normalized = normalize(teamName)
        val key = acronyms[normalized.uppercase()] ?: normalized
        if (key in knownTeams) return true
        return key.length >= MIN_FUZZY_KEY && knownTeams.keys.any { it.contains(key) || key.contains(it) }
    }

    fun forMatch(homeTeam: String?, awayTeam: String?, ground: Ground = DarkGround): MatchTeamPalette {
        return MatchTeamPalette(
            home = forTeam(homeTeam, ground),
            away = forTeam(awayTeam, ground)
        )
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

        return TeamColors(
            primary = primary,
            secondary = secondary,
            surface = surface,
            onSurface = onSurface,
            onPrimary = onPrimary,
            border = primary.copy(alpha = 0.52f),
            accent = secondary.copy(alpha = 0.22f)
        )
    }

    private fun hashFallback(teamName: String, ground: Ground): TeamColors {
        val hash = teamName.hashCode().absoluteValue
        val hue = (hash % 360).toFloat()
        val saturation = 0.58f + ((hash / 360) % 20) / 100f
        val value = 0.84f

        val primary = Color.hsv(hue, saturation.coerceIn(0.52f, 0.8f), value)
        val secondary = Color.hsv((hue + 42f) % 360f, (saturation - 0.08f).coerceIn(0.45f, 0.75f), 0.88f)
        return buildTeamColors(primary, secondary, ground)
    }

    private const val LIGHT_GROUND_LUMINANCE = 0.5f
    private const val MIN_FUZZY_KEY = 4
    private const val LIGHT_SURFACE_TINT = 0.88f

    private fun normalize(value: String): String {
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
