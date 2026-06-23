package com.tanush.bouncer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
object BouncerTypographyTokens {
    val displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp
    )

    val displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    )

    val headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    )

    val headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    )

    val headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    val titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )

    val titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )

    val bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )

    val bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )

    val bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    )

    val labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp
    )

    val labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.1.sp
    )

    val labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.8.sp
    )
}

@Immutable
data class BouncerTypography(
    val displayLarge: TextStyle = BouncerTypographyTokens.displayLarge,
    val displayMedium: TextStyle = BouncerTypographyTokens.displayMedium,
    val headlineLarge: TextStyle = BouncerTypographyTokens.headlineLarge,
    val headlineMedium: TextStyle = BouncerTypographyTokens.headlineMedium,
    val headlineSmall: TextStyle = BouncerTypographyTokens.headlineSmall,
    val titleLarge: TextStyle = BouncerTypographyTokens.titleLarge,
    val titleMedium: TextStyle = BouncerTypographyTokens.titleMedium,
    val bodyLarge: TextStyle = BouncerTypographyTokens.bodyLarge,
    val bodyMedium: TextStyle = BouncerTypographyTokens.bodyMedium,
    val bodySmall: TextStyle = BouncerTypographyTokens.bodySmall,
    val labelLarge: TextStyle = BouncerTypographyTokens.labelLarge,
    val labelMedium: TextStyle = BouncerTypographyTokens.labelMedium,
    val labelSmall: TextStyle = BouncerTypographyTokens.labelSmall
) {
    val headingLarge: TextStyle get() = headlineLarge
    val headingMedium: TextStyle get() = headlineMedium
    val headingSmall: TextStyle get() = headlineSmall

    val scoreDisplay: TextStyle
        get() = displayLarge.copy(fontFeatureSettings = "tnum")
    val scoreSecondary: TextStyle
        get() = displayMedium.copy(fontFeatureSettings = "tnum")
    val scoreCompact: TextStyle
        get() = headlineLarge.copy(fontFeatureSettings = "tnum")

    val statValue: TextStyle
        get() = titleLarge.copy(fontFeatureSettings = "tnum")
    val matchStatus: TextStyle
        get() = labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    val overCount: TextStyle
        get() = bodySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val playerName: TextStyle
        get() = bodyMedium
    val teamName: TextStyle
        get() = bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val chip: TextStyle
        get() = bodySmall.copy(fontWeight = FontWeight.Medium)
    val button: TextStyle
        get() = bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val buttonCompact: TextStyle
        get() = bodySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val tabularNumbers: TextStyle
        get() = bodyMedium.copy(fontFeatureSettings = "tnum")

    /** Convenience: monospace numbers at any body size */
    val numberLarge: TextStyle
        get() = headlineLarge.copy(fontFeatureSettings = "tnum")
    val numberMedium: TextStyle
        get() = bodyMedium.copy(fontFeatureSettings = "tnum")
    val numberSmall: TextStyle
        get() = bodySmall.copy(fontFeatureSettings = "tnum")

    /** Run-rate / strike-rate display — semibold tabular figures */
    val runRate: TextStyle
        get() = bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

    /** Bowling/batting figures (e.g. "3/24", "56(42)") */
    val figureStat: TextStyle
        get() = titleMedium.copy(fontFeatureSettings = "tnum")

    companion object {
        val tabularBody: TextStyle = BouncerTypographyTokens.bodyMedium.copy(fontFeatureSettings = "tnum")
        val overDisplay: TextStyle = BouncerTypographyTokens.bodySmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum"
        )
        val labelAllCaps: TextStyle = BouncerTypographyTokens.labelMedium
        val statLabel: TextStyle = BouncerTypographyTokens.labelSmall
        val statValue: TextStyle = BouncerTypography().statValue
        val playerName: TextStyle = BouncerTypographyTokens.bodyMedium
        val scoreDisplay: TextStyle = BouncerTypography().scoreDisplay
        val heroGradient: TextStyle = BouncerTypographyTokens.displayLarge
        val surfaceGradient: TextStyle = BouncerTypographyTokens.bodyLarge
        val numberLarge: TextStyle = BouncerTypographyTokens.headlineLarge.copy(fontFeatureSettings = "tnum")
        val numberMedium: TextStyle = BouncerTypographyTokens.bodyMedium.copy(fontFeatureSettings = "tnum")
        val numberSmall: TextStyle = BouncerTypographyTokens.bodySmall.copy(fontFeatureSettings = "tnum")
        val runRate: TextStyle = BouncerTypographyTokens.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum"
        )
        val figureStat: TextStyle = BouncerTypographyTokens.titleMedium.copy(fontFeatureSettings = "tnum")
    }
}

val LocalBouncerTypography = staticCompositionLocalOf { BouncerTypography() }

val BouncerMaterial3Typography = Typography(
    displayLarge = BouncerTypographyTokens.displayLarge,
    displayMedium = BouncerTypographyTokens.displayMedium,
    displaySmall = BouncerTypographyTokens.headlineLarge,
    headlineLarge = BouncerTypographyTokens.headlineLarge,
    headlineMedium = BouncerTypographyTokens.headlineMedium,
    headlineSmall = BouncerTypographyTokens.headlineSmall,
    titleLarge = BouncerTypographyTokens.titleLarge,
    titleMedium = BouncerTypographyTokens.titleMedium,
    titleSmall = BouncerTypographyTokens.bodySmall,
    bodyLarge = BouncerTypographyTokens.bodyLarge,
    bodyMedium = BouncerTypographyTokens.bodyMedium,
    bodySmall = BouncerTypographyTokens.bodySmall,
    labelLarge = BouncerTypographyTokens.labelLarge,
    labelMedium = BouncerTypographyTokens.labelMedium,
    labelSmall = BouncerTypographyTokens.labelSmall
)

fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

val tabularBody: TextStyle = BouncerTypography.tabularBody
val overDisplay: TextStyle = BouncerTypography.overDisplay
val labelAllCaps: TextStyle = BouncerTypography.labelAllCaps
val statLabel: TextStyle = BouncerTypography.statLabel
val statValue: TextStyle = BouncerTypography.statValue
val playerName: TextStyle = BouncerTypography.playerName
val scoreDisplay: TextStyle = BouncerTypography.scoreDisplay
val heroGradient: TextStyle = BouncerTypography.heroGradient
val surfaceGradient: TextStyle = BouncerTypography.surfaceGradient

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
