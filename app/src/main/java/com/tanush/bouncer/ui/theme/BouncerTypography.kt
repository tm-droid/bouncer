package com.tanush.bouncer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R

/**
 * Bouncer Typography System
 *
 * Premium futuristic typography using Google Fonts (Inter).
 * Designed for optimal readability on dark glass-morphism surfaces.
 */

// =============================================================================
// Google Fonts Provider
// =============================================================================

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * Primary font family for the app: Inter.
 * Loaded via Google Fonts for consistency across devices.
 */
val InterFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.ExtraBold),
)

// =============================================================================
// Custom Typography Tokens
// =============================================================================

@Immutable
object BouncerTypographyTokens {
    /** 56sp Extra Bold — Main score display (e.g., "256/4") */
    val ScoreDisplay = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum"
    )

    /** 40sp Bold — Hero display text */
    val DisplayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp
    )
    
    /** 28sp Bold — Secondary score display (e.g., target, required rate) */
    val ScoreSecondary = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    )
    
    /** 24sp Bold — Compact score display */
    val ScoreCompact = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    )

    /** 22sp SemiBold — Large headings (e.g., screen titles) */
    val HeadingLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    )
    
    /** 18sp SemiBold — Medium headings (e.g., section titles) */
    val HeadingMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    )
    
    /** 15sp SemiBold — Small headings (e.g., card titles) */
    val HeadingSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )
    
    /** 16sp Regular — Large body text */
    val BodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )
    
    /** 14sp Regular — Medium body text */
    val BodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
    
    /** 12sp Regular — Small body text */
    val BodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    )
    
    /** 13sp SemiBold Uppercase — Large labels */
    val LabelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    )
    
    /** 11sp Regular Uppercase — Medium labels */
    val LabelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
    
    /** 10sp Regular — Small labels */
    val LabelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.4.sp
    )

    /** 20sp SemiBold — Stat values (runs, wickets, averages) */
    val StatValue = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum"
    )

    /** 11sp Bold Uppercase — Match status badges */
    val MatchStatus = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    )
}

@Immutable
data class BouncerTypography(
    val scoreDisplay: TextStyle = BouncerTypographyTokens.ScoreDisplay,
    val displayLarge: TextStyle = BouncerTypographyTokens.DisplayLarge,
    val scoreSecondary: TextStyle = BouncerTypographyTokens.ScoreSecondary,
    val scoreCompact: TextStyle = BouncerTypographyTokens.ScoreCompact,
    val headingLarge: TextStyle = BouncerTypographyTokens.HeadingLarge,
    val headingMedium: TextStyle = BouncerTypographyTokens.HeadingMedium,
    val headingSmall: TextStyle = BouncerTypographyTokens.HeadingSmall,
    val bodyLarge: TextStyle = BouncerTypographyTokens.BodyLarge,
    val bodyMedium: TextStyle = BouncerTypographyTokens.BodyMedium,
    val bodySmall: TextStyle = BouncerTypographyTokens.BodySmall,
    val labelLarge: TextStyle = BouncerTypographyTokens.LabelLarge,
    val labelMedium: TextStyle = BouncerTypographyTokens.LabelMedium,
    val labelSmall: TextStyle = BouncerTypographyTokens.LabelSmall,
    val statValue: TextStyle = BouncerTypographyTokens.StatValue,
    val matchStatus: TextStyle = BouncerTypographyTokens.MatchStatus,
    
    // Additional domain-specific styles
    val overCount: TextStyle = BouncerTypographyTokens.BodySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum"
    ),
    val playerName: TextStyle = BouncerTypographyTokens.BodyMedium,
    val teamName: TextStyle = BouncerTypographyTokens.BodyLarge.copy(
        fontWeight = FontWeight.SemiBold
    ),
    val chip: TextStyle = BouncerTypographyTokens.BodySmall.copy(
        fontWeight = FontWeight.Medium
    ),
    val button: TextStyle = BouncerTypographyTokens.BodyMedium.copy(
        fontWeight = FontWeight.SemiBold
    ),
    val tabularNumbers: TextStyle = BouncerTypographyTokens.BodyMedium.copy(
        fontFeatureSettings = "tnum"
    )
) {
    /**
     * Legacy static style accessors for backward compatibility.
     * Access via BouncerTypography.tabularBody, BouncerTypography.scoreDisplay, etc.
     */
    companion object {
        val tabularBody: TextStyle = BouncerTypographyTokens.BodyMedium.copy(fontFeatureSettings = "tnum")
        val overDisplay: TextStyle = BouncerTypographyTokens.BodySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
        val labelAllCaps: TextStyle = BouncerTypographyTokens.LabelMedium
        val statLabel: TextStyle = BouncerTypographyTokens.LabelSmall
        val statValue: TextStyle = BouncerTypographyTokens.StatValue
        val playerName: TextStyle = BouncerTypographyTokens.BodyMedium
        val scoreDisplay: TextStyle = BouncerTypographyTokens.ScoreDisplay
        val heroGradient: TextStyle = BouncerTypographyTokens.DisplayLarge
        val surfaceGradient: TextStyle = BouncerTypographyTokens.BodyLarge
    }
}

// =============================================================================
// CompositionLocal
// =============================================================================

val LocalBouncerTypography = staticCompositionLocalOf { BouncerTypography() }

// =============================================================================
// Material 3 Typography Mapping
// =============================================================================

val BouncerMaterial3Typography = Typography(
    displayLarge = BouncerTypographyTokens.DisplayLarge,
    displayMedium = BouncerTypographyTokens.ScoreSecondary,
    displaySmall = BouncerTypographyTokens.ScoreCompact,
    headlineLarge = BouncerTypographyTokens.HeadingLarge,
    headlineMedium = BouncerTypographyTokens.HeadingMedium,
    headlineSmall = BouncerTypographyTokens.HeadingSmall,
    titleLarge = BouncerTypographyTokens.HeadingLarge,
    titleMedium = BouncerTypographyTokens.HeadingMedium,
    titleSmall = BouncerTypographyTokens.HeadingSmall,
    bodyLarge = BouncerTypographyTokens.BodyLarge,
    bodyMedium = BouncerTypographyTokens.BodyMedium,
    bodySmall = BouncerTypographyTokens.BodySmall,
    labelLarge = BouncerTypographyTokens.LabelLarge,
    labelMedium = BouncerTypographyTokens.LabelMedium,
    labelSmall = BouncerTypographyTokens.LabelSmall
)

// =============================================================================
// Helper Extension
// =============================================================================

/**
 * Applies tabular number formatting to any text style for aligned digits.
 */
fun TextStyle.withTabularNumbers(): TextStyle {
    return this.copy(fontFeatureSettings = "tnum")
}

// =============================================================================
// Legacy Typography Aliases — Backward compatibility
// =============================================================================

val tabularBody: TextStyle = BouncerTypographyTokens.BodyMedium.copy(fontFeatureSettings = "tnum")
val overDisplay: TextStyle = BouncerTypographyTokens.BodySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
val labelAllCaps: TextStyle = BouncerTypographyTokens.LabelMedium
val statLabel: TextStyle = BouncerTypographyTokens.LabelSmall
val statValue: TextStyle = BouncerTypographyTokens.StatValue
val playerName: TextStyle = BouncerTypographyTokens.BodyMedium
val scoreDisplay: TextStyle = BouncerTypographyTokens.ScoreDisplay
val heroGradient: TextStyle = BouncerTypographyTokens.DisplayLarge
val surfaceGradient: TextStyle = BouncerTypographyTokens.BodyLarge

// =============================================================================
// Typography Extension Properties for MaterialTheme.typography access
// =============================================================================

val Typography.tabularBody: TextStyle
    get() = com.tanush.bouncer.ui.theme.tabularBody

val Typography.overDisplay: TextStyle
    get() = com.tanush.bouncer.ui.theme.overDisplay

val Typography.labelAllCaps: TextStyle
    get() = com.tanush.bouncer.ui.theme.labelAllCaps

val Typography.statLabel: TextStyle
    get() = com.tanush.bouncer.ui.theme.statLabel

val Typography.statValue: TextStyle
    get() = com.tanush.bouncer.ui.theme.statValue

val Typography.playerName: TextStyle
    get() = com.tanush.bouncer.ui.theme.playerName

val Typography.scoreDisplay: TextStyle
    get() = com.tanush.bouncer.ui.theme.scoreDisplay

val Typography.heroGradient: TextStyle
    get() = com.tanush.bouncer.ui.theme.heroGradient

val Typography.surfaceGradient: TextStyle
    get() = com.tanush.bouncer.ui.theme.surfaceGradient
