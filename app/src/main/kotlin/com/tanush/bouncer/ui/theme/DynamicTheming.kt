package com.tanush.bouncer.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.pow
import io.sentry.Sentry

// ==========================================
// BOUNCER - Dynamic Theming System (v1.0)
// Team-Based Color Extraction & Time-Aware Themes
// ==========================================

/**
 * Team color scheme data class containing brand colors
 */
@Immutable
data class TeamColorScheme(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color = BlackSurface,
    val onPrimary: Color = Color.White,
    val onSecondary: Color = Color.White,
    val onTertiary: Color = Color.White,
    val onSurface: Color = TextPrimary
)

/**
 * Predefined team color schemes
 */
object LegacyTeamSchemes {
    val India = TeamColorScheme(
        primary = Color(0xFF138808),
        secondary = Color(0xFFFF9933),
        tertiary = Color(0xFF000080)
    )
    
    val Australia = TeamColorScheme(
        primary = Color(0xFFFFCD00),
        secondary = Color(0xFF006F3D),
        tertiary = Color.Black,
        onPrimary = Color.Black
    )
    
    val England = TeamColorScheme(
        primary = Color(0xFFCE1126),
        secondary = Color(0xFF00247D),
        tertiary = Color.White,
        onTertiary = Color.Black
    )
    
    val Pakistan = TeamColorScheme(
        primary = Color(0xFF01411C),
        secondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF00A651),
        onSecondary = Color.Black
    )
    
    val SouthAfrica = TeamColorScheme(
        primary = Color(0xFF007A4D),
        secondary = Color(0xFFFFB612),
        tertiary = Color(0xFFDE3831)
    )
    
    val NewZealand = TeamColorScheme(
        primary = Color(0xFF000000),
        secondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFFC8102E),
        onSecondary = Color.Black
    )
    
    val WestIndies = TeamColorScheme(
        primary = Color(0xFF7B0031),
        secondary = Color(0xFFFFC72C),
        tertiary = Color(0xFF00A651)
    )
    
    val SriLanka = TeamColorScheme(
        primary = Color(0xFF264348),
        secondary = Color(0xFFFFBE00),
        tertiary = Color(0xFF8A1538)
    )
    
    val Bangladesh = TeamColorScheme(
        primary = Color(0xFF006A4E),
        secondary = Color(0xFFF42A41),
        tertiary = Color(0xFFFFFFFF),
        onTertiary = Color.Black
    )
    
    val Afghanistan = TeamColorScheme(
        primary = Color(0xFF000000),
        secondary = Color(0xFFD32011),
        tertiary = Color(0xFF007A36)
    )
    
    val Default = TeamColorScheme(
        primary = GoldDark,
        secondary = GoldPrimary,
        tertiary = ChartCyan
    )
}

/**
 * Time-based theme adjustments
 */
@Immutable
data class TimeBasedTheme(
    val warmthFactor: Float,
    val brightnessAdjustment: Float,
    val ambientColor: Color
) {
    companion object {
        fun forCurrentTime(): TimeBasedTheme {
            try {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            
                return when (hour) {
                    in 5..8 -> Morning
                    in 9..16 -> Day
                    in 17..20 -> Evening
                    else -> Night
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        val Morning = TimeBasedTheme(
            warmthFactor = 0.15f,
            brightnessAdjustment = 0.05f,
            ambientColor = Color(0xFFFFE4B5) // Warm sunrise
        )
        
        val Day = TimeBasedTheme(
            warmthFactor = 0f,
            brightnessAdjustment = 0f,
            ambientColor = Color.Transparent
        )
        
        val Evening = TimeBasedTheme(
            warmthFactor = -0.1f,
            brightnessAdjustment = -0.05f,
            ambientColor = Color(0xFFE8D4F4) // Cool sunset
        )
        
        val Night = TimeBasedTheme(
            warmthFactor = -0.15f,
            brightnessAdjustment = -0.1f,
            ambientColor = Color(0xFF1A1A2E) // Deep blue
        )
    }
}

/**
 * High contrast mode for accessibility
 */
@Immutable
data class HighContrastMode(
    val isEnabled: Boolean,
    val contrastMultiplier: Float = 1.5f
) {
    fun adjustColor(color: Color): Color {
        try {
            if (!isEnabled) return color
        
            val r = (color.red * contrastMultiplier).coerceIn(0f, 1f)
            val g = (color.green * contrastMultiplier).coerceIn(0f, 1f)
            val b = (color.blue * contrastMultiplier).coerceIn(0f, 1f)
        
            return Color(r, g, b, color.alpha)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    companion object {
        val Normal = HighContrastMode(isEnabled = false)
        val Enhanced = HighContrastMode(isEnabled = true, contrastMultiplier = 1.3f)
        val Maximum = HighContrastMode(isEnabled = true, contrastMultiplier = 1.6f)
    }
}

val LocalTeamColorScheme = staticCompositionLocalOf { LegacyTeamSchemes.Default }
val LocalTimeBasedTheme = staticCompositionLocalOf { TimeBasedTheme.forCurrentTime() }
val LocalHighContrastMode = staticCompositionLocalOf { HighContrastMode.Normal }

/**
 * Team theme provider that extracts dominant colors from team logos using Palette
 */
object TeamThemeProvider {
    
    /**
     * Extract dominant colors from a bitmap using Palette
     */
    suspend fun extractColorsFromBitmap(bitmap: Bitmap): TeamColorScheme {
        try {
            return withContext(Dispatchers.Default) {
                try {
                    val palette = Palette.from(bitmap).generate()
                
                    val primary = palette.dominantSwatch?.rgb?.let { Color(it) }
                        ?: palette.vibrantSwatch?.rgb?.let { Color(it) }
                        ?: LegacyTeamSchemes.Default.primary
                
                    val secondary = palette.vibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette.mutedSwatch?.rgb?.let { Color(it) }
                        ?: LegacyTeamSchemes.Default.secondary
                
                    val tertiary = palette.mutedSwatch?.rgb?.let { Color(it) }
                        ?: palette.lightMutedSwatch?.rgb?.let { Color(it) }
                        ?: LegacyTeamSchemes.Default.tertiary
                
                    val surface = palette.darkMutedSwatch?.rgb?.let { Color(it) }
                        ?: BlackSurface
                
                    // Calculate appropriate text colors based on luminance
                    val onPrimary = if (calculateLuminance(primary) > 0.5f) Color.Black else Color.White
                    val onSecondary = if (calculateLuminance(secondary) > 0.5f) Color.Black else Color.White
                
                    TeamColorScheme(
                        primary = primary,
                        secondary = secondary,
                        tertiary = tertiary,
                        surface = surface,
                        onPrimary = onPrimary,
                        onSecondary = onSecondary
                    )
                } catch (e: Exception) {
                    LegacyTeamSchemes.Default
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get predefined colors for a team by name
     */
    fun getTeamColors(teamName: String): TeamColorScheme {
        try {
            val normalizedName = teamName.lowercase().trim()
        
            return when {
                normalizedName.contains("india") -> LegacyTeamSchemes.India
                normalizedName.contains("australia") -> LegacyTeamSchemes.Australia
                normalizedName.contains("england") -> LegacyTeamSchemes.England
                normalizedName.contains("pakistan") -> LegacyTeamSchemes.Pakistan
                normalizedName.contains("south africa") || normalizedName.contains("proteas") -> LegacyTeamSchemes.SouthAfrica
                normalizedName.contains("new zealand") || normalizedName.contains("black caps") -> LegacyTeamSchemes.NewZealand
                normalizedName.contains("west indies") || normalizedName.contains("windies") -> LegacyTeamSchemes.WestIndies
                normalizedName.contains("sri lanka") -> LegacyTeamSchemes.SriLanka
                normalizedName.contains("bangladesh") -> LegacyTeamSchemes.Bangladesh
                normalizedName.contains("afghanistan") -> LegacyTeamSchemes.Afghanistan
                else -> LegacyTeamSchemes.Default
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    private fun calculateLuminance(color: Color): Float {
        try {
            val r = if (color.red <= 0.03928f) color.red / 12.92f else ((color.red + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
            val g = if (color.green <= 0.03928f) color.green / 12.92f else ((color.green + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
            val b = if (color.blue <= 0.03928f) color.blue / 12.92f else ((color.blue + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Remember and cache extracted team colors from a bitmap
 */
@Composable
fun rememberLegacyTeamColorScheme(
    bitmap: Bitmap?,
    fallbackTeamName: String = ""
): TeamColorScheme {
    var teamColors by remember { mutableStateOf(TeamThemeProvider.getTeamColors(fallbackTeamName)) }
    
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            teamColors = TeamThemeProvider.extractColorsFromBitmap(bitmap)
        }
    }
    
    return teamColors
}

/**
 * Remember team colors by team name only
 */
@Composable
fun rememberLegacyTeamColorScheme(teamName: String): TeamColorScheme {
    return remember(teamName) {
        TeamThemeProvider.getTeamColors(teamName)
    }
}

/**
 * Apply time-based adjustments to a color
 */
fun Color.adjustForTimeOfDay(timeTheme: TimeBasedTheme): Color {
    try {
        if (timeTheme.warmthFactor == 0f && timeTheme.brightnessAdjustment == 0f) return this
    
        var r = this.red
        var g = this.green
        var b = this.blue
    
        // Apply warmth (positive = warmer, negative = cooler)
        if (timeTheme.warmthFactor > 0) {
            r = (r + timeTheme.warmthFactor).coerceIn(0f, 1f)
            b = (b - timeTheme.warmthFactor * 0.5f).coerceIn(0f, 1f)
        } else {
            r = (r + timeTheme.warmthFactor * 0.5f).coerceIn(0f, 1f)
            b = (b - timeTheme.warmthFactor).coerceIn(0f, 1f)
        }
    
        // Apply brightness
        r = (r + timeTheme.brightnessAdjustment).coerceIn(0f, 1f)
        g = (g + timeTheme.brightnessAdjustment).coerceIn(0f, 1f)
        b = (b + timeTheme.brightnessAdjustment).coerceIn(0f, 1f)
    
        return Color(r, g, b, this.alpha)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Create a Material 3 ColorScheme from TeamColorScheme
 */
fun TeamColorScheme.toMaterialColorScheme(): ColorScheme {
    try {
        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.3f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondary.copy(alpha = 0.3f),
            onSecondaryContainer = secondary,
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = tertiary.copy(alpha = 0.3f),
            onTertiaryContainer = tertiary,
            background = BlackBackground,
            onBackground = TextPrimary,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = BlackElevated,
            onSurfaceVariant = TextSecondary,
            outline = GlassBorder,
            outlineVariant = GlassBorderLight
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Dynamic theme wrapper that provides team-based color scheme
 */
@Composable
fun DynamicThemeWrapper(
    teamColorScheme: TeamColorScheme = LegacyTeamSchemes.Default,
    timeBasedTheme: TimeBasedTheme = TimeBasedTheme.forCurrentTime(),
    highContrastMode: HighContrastMode = HighContrastMode.Normal,
    content: @Composable () -> Unit
) {
    // Animate color transitions
    val animatedPrimary by animateColorAsState(
        targetValue = highContrastMode.adjustColor(
            teamColorScheme.primary.adjustForTimeOfDay(timeBasedTheme)
        ),
        animationSpec = tween(durationMillis = AnimationDuration.moderate),
        label = "primaryColorAnimation"
    )
    
    val animatedSecondary by animateColorAsState(
        targetValue = highContrastMode.adjustColor(
            teamColorScheme.secondary.adjustForTimeOfDay(timeBasedTheme)
        ),
        animationSpec = tween(durationMillis = AnimationDuration.moderate),
        label = "secondaryColorAnimation"
    )
    
    val animatedTertiary by animateColorAsState(
        targetValue = highContrastMode.adjustColor(teamColorScheme.tertiary),
        animationSpec = tween(durationMillis = AnimationDuration.moderate),
        label = "tertiaryColorAnimation"
    )
    
    val dynamicTeamColors = teamColorScheme.copy(
        primary = animatedPrimary,
        secondary = animatedSecondary,
        tertiary = animatedTertiary
    )
    
    CompositionLocalProvider(
        LocalTeamColorScheme provides dynamicTeamColors,
        LocalTimeBasedTheme provides timeBasedTheme,
        LocalHighContrastMode provides highContrastMode
    ) {
        MaterialTheme(
            colorScheme = dynamicTeamColors.toMaterialColorScheme(),
            typography = BouncerMaterial3Typography,
            content = content
        )
    }
}

/**
 * Access current team colors anywhere in the composition
 */
object DynamicColors {
    val teamColors: TeamColorScheme
        @Composable
        get() = LocalTeamColorScheme.current
    
    val timeTheme: TimeBasedTheme
        @Composable
        get() = LocalTimeBasedTheme.current
    
    val highContrast: HighContrastMode
        @Composable
        get() = LocalHighContrastMode.current
}

/**
 * Returns the primary team color as an ARGB int for use in Android notification APIs
 * (NotificationCompat.Builder.setColor, RemoteViews.setInt).
 * Fuzzy-matches on team name; falls back to gold if unrecognized.
 */
fun getTeamNotificationColor(teamName: String): Int {
    val lower = teamName.lowercase()
    val scheme = when {
        lower.contains("india") -> LegacyTeamSchemes.India
        lower.contains("australia") -> LegacyTeamSchemes.Australia
        lower.contains("england") -> LegacyTeamSchemes.England
        lower.contains("pakistan") -> LegacyTeamSchemes.Pakistan
        lower.contains("south africa") || lower.contains("southafrica") -> LegacyTeamSchemes.SouthAfrica
        lower.contains("new zealand") || lower.contains("newzealand") -> LegacyTeamSchemes.NewZealand
        lower.contains("west indies") || lower.contains("westindies") -> LegacyTeamSchemes.WestIndies
        lower.contains("sri lanka") || lower.contains("srilanka") -> LegacyTeamSchemes.SriLanka
        lower.contains("bangladesh") -> LegacyTeamSchemes.Bangladesh
        lower.contains("afghanistan") -> LegacyTeamSchemes.Afghanistan
        else -> LegacyTeamSchemes.Default
    }
    return scheme.primary.toArgb()
}
