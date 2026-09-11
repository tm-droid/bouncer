package com.tanush.bouncer.ui.theme.v2

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerFontFamily

/**
 * Bouncer V2 typography scale.
 * Uses [BouncerFontFamily] (IBM Plex Sans) — bold for scores, semibold for headers.
 *
 * Hierarchy is carried by size and weight, never by colour or by wrapping something in a box.
 *
 * Anything that re-renders with new digits in place — scores, overs, run rates, stats — must use
 * a [TABULAR] style. Proportional digits are different widths, so a live score repolling every
 * ~6s reflows its whole row on each tick (a `1` narrowing to a `4` visibly shifts the overs and
 * the win-probability labels beside it). Tabular figures pin every digit to one advance width.
 */

/**
 * OpenType `tnum`: fixed-advance digits. Required on any numeral that updates in place.
 */
private const val TABULAR = "tnum"

@Immutable
data class BouncerTypographyV2(
    /** 32sp ExtraBold — large hero scores */
    val displayLarge: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    /** 28sp Bold — section headers, screen titles */
    val displayMedium: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp,
    ),
    /** 24sp Bold — card titles, match scores in detail */
    val headlineLarge: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    /** 20sp SemiBold — section headers in-screen */
    val headlineMedium: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    /** 18sp SemiBold — match card series header */
    val titleLarge: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    /** 16sp Medium — team names, player names */
    val titleMedium: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    /** 14sp Medium — card sub-titles, list items */
    val titleSmall: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    /** 16sp Normal — paragraph body */
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    /** 14sp Normal — secondary body, description */
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    /** 12sp Normal — captions, timestamps */
    val bodySmall: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    /** 14sp Medium — buttons, chips, badges */
    val labelLarge: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    /** 12sp Medium — small labels, tab bar labels */
    val labelMedium: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    /** 11sp Medium — micro labels, badges */
    val labelSmall: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    /** 11sp Medium, wide-tracked — uppercase section headers ("MATCHES", "THIS OVER") */
    val overline: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    /** 22sp ExtraBold, tabular — compact match card scores */
    val scoreLarge: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR,
    ),
    /** 16sp Bold, tabular — secondary scores, overs */
    val scoreMedium: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR,
    ),
    /** 14sp Medium, tabular — stat figures, overs, run rates in tables and rows */
    val numeric: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR,
    ),
    /** 12sp Normal, tabular — the small `(14.2)` overs figure beside a score */
    val numericSmall: TextStyle = TextStyle(
        fontFamily = BouncerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TABULAR,
    ),
)

/** Convert to Material 3 Typography for MaterialTheme interop */
fun BouncerTypographyV2.toMaterial3(): Typography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)

val LocalBouncerTypographyV2 = staticCompositionLocalOf { BouncerTypographyV2() }
