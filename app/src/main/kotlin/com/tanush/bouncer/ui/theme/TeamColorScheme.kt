package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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

    val default: TeamColors = buildTeamColors(GoldDark, GoldPrimary)

    fun forTeam(teamName: String?): TeamColors {
        if (teamName.isNullOrBlank()) return default

        val normalized = normalize(teamName)
        val key = acronyms[normalized.uppercase()] ?: normalized

        knownTeams[key]?.let { return buildTeamColors(it.first, it.second) }

        knownTeams.entries.firstOrNull { (name, _) ->
            name.contains(key) || key.contains(name)
        }?.let { return buildTeamColors(it.value.first, it.value.second) }

        return hashFallback(key)
    }

    fun forMatch(homeTeam: String?, awayTeam: String?): MatchTeamPalette {
        return MatchTeamPalette(
            home = forTeam(homeTeam),
            away = forTeam(awayTeam)
        )
    }

    private fun buildTeamColors(primaryRaw: Color, secondaryRaw: Color): TeamColors {
        val primary = primaryRaw.ensureContrast(background = BlackBackground, minimumRatio = 3.0f)
        val secondary = secondaryRaw.ensureContrast(background = BlackBackground, minimumRatio = 2.8f)
        val surface = primary.darken(0.72f).blend(BlackSurface, 0.62f)
        val onSurface = TextPrimary.ensureContrast(surface, minimumRatio = 4.5f)
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

    private fun hashFallback(teamName: String): TeamColors {
        val hash = teamName.hashCode().absoluteValue
        val hue = (hash % 360).toFloat()
        val saturation = 0.58f + ((hash / 360) % 20) / 100f
        val value = 0.84f

        val primary = Color.hsv(hue, saturation.coerceIn(0.52f, 0.8f), value)
        val secondary = Color.hsv((hue + 42f) % 360f, (saturation - 0.08f).coerceIn(0.45f, 0.75f), 0.88f)
        return buildTeamColors(primary, secondary)
    }

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

@Composable
fun rememberTeamColors(teamName: String?): TeamColors {
    return remember(teamName) { TeamColorRegistry.forTeam(teamName) }
}

@Composable
fun rememberMatchTeamPalette(homeTeam: String?, awayTeam: String?): MatchTeamPalette {
    return remember(homeTeam, awayTeam) { TeamColorRegistry.forMatch(homeTeam, awayTeam) }
}

@Composable
fun rememberTeamColors(homeTeam: String?, awayTeam: String?): Pair<TeamColors, TeamColors> {
    val palette = rememberMatchTeamPalette(homeTeam, awayTeam)
    return remember(palette) { palette.home to palette.away }
}
