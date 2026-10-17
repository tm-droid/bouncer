package com.tanush.bouncer.ui.widgets

import com.tanush.bouncer.ui.theme.TeamColorRegistry
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.glance.LocalContext
import com.tanush.bouncer.R
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import io.sentry.Sentry

private fun cp(c: Color): GlanceColorProvider = ColorProvider(day = c, night = c)

/**
 * Day/night pair. Glance resolves this against the *system* setting, not the in-app theme picker —
 * correct for a home-screen surface, which sits on the launcher rather than inside the app.
 */
private fun cp(light: Color, dark: Color): GlanceColorProvider = ColorProvider(day = light, night = dark)

private inline fun <T> sentryGuard(block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

object WidgetBrandPalette {
    val background = Color(0xFF0C1015)
    val surface = Color(0xFF161C26)
    val surfaceElevated = Color(0xFF202834)
    val surfaceHighest = Color(0xFF2B3442)
    val primary = Color(0xFFE0B74F)
    val primaryDim = Color(0xFFAF8327)
    val primaryContainer = Color(0xFF34270D)
    val textPrimary = Color(0xFFFCF7ED)
    val textSecondary = Color(0xFFC1B08D)
    val textTertiary = Color(0xFF8D7E61)
    val textOnPrimary = Color(0xFF130F08)
    val success = Color(0xFF34C759)
    val error = Color(0xFFFF5A4E)
    val warning = Color(0xFFF4B740)
    val live = Color(0xFFFF453A)
    val wicket = Color(0xFFFF5A4E)
    val six = Color(0xFF9B7BFF)
    val four = Color(0xFF34C759)
    val dotBall = Color(0xFF3B4452)
    val border = Color(0x1FF3DB93)
    val borderAccent = primary.copy(alpha = 0.28f)
    val actionChip = Color(0xFF1B2330)
    val liveBadgeBackground = Color(0xFF401614)
    val progressTrack = Color(0xFF1A202A)
}

/**
 * Light counterpart of [WidgetBrandPalette]. Widgets used to be fixed-dark: every colour went
 * through `cp(c)`, which hands the same value to day and night, so a charcoal card with near-white
 * text sat on a light wallpaper.
 *
 * Golds here are the ink-safe family (#8A6516), not the #E0B74F fill gold — that measures 1.74:1 on
 * white. Mirrors res/values/colors.xml, which backs the RemoteViews widget for the same reason.
 */
object WidgetLightPalette {
    val background = Color(0xFFFAFAFA)
    val surface = Color(0xFFFFFFFF)
    val surfaceElevated = Color(0xFFF5F5F5)
    val surfaceHighest = Color(0xFFE5E7EB)
    val primary = Color(0xFF8A6516)
    val primaryDim = Color(0xFF6B4E11)
    val primaryContainer = Color(0xFFFDF3C0)
    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF4B5563)
    val textTertiary = Color(0xFF6B7280)
    val textOnPrimary = Color(0xFFFFFFFF)
    val success = Color(0xFF15803D)
    val error = Color(0xFFDC2626)
    val warning = Color(0xFFB45309)
    val live = Color(0xFFDC2626)
    val wicket = Color(0xFFDC2626)
    val six = Color(0xFF6D28D9)
    val four = Color(0xFF15803D)
    val dotBall = Color(0xFF9CA3AF)
    val border = Color(0x1F1A1A1A)
    val borderAccent = primary.copy(alpha = 0.28f)
    val actionChip = Color(0xFFF5F5F5)
    val liveBadgeBackground = Color(0xFFFEE2E2)
    val progressTrack = Color(0xFFE5E7EB)
}

object WidgetColorScheme {
    val background = cp(WidgetLightPalette.background, WidgetBrandPalette.background)
    val surface = cp(WidgetLightPalette.surface, WidgetBrandPalette.surface)
    val surfaceElevated = cp(WidgetLightPalette.surfaceElevated, WidgetBrandPalette.surfaceElevated)
    val surfaceHighest = cp(WidgetLightPalette.surfaceHighest, WidgetBrandPalette.surfaceHighest)
    val primary = cp(WidgetLightPalette.primary, WidgetBrandPalette.primary)
    val primaryDim = cp(WidgetLightPalette.primaryDim, WidgetBrandPalette.primaryDim)
    val primaryContainer = cp(WidgetLightPalette.primaryContainer, WidgetBrandPalette.primaryContainer)
    val textPrimary = cp(WidgetLightPalette.textPrimary, WidgetBrandPalette.textPrimary)
    val textSecondary = cp(WidgetLightPalette.textSecondary, WidgetBrandPalette.textSecondary)
    val textTertiary = cp(WidgetLightPalette.textTertiary, WidgetBrandPalette.textTertiary)
    val textOnPrimary = cp(WidgetLightPalette.textOnPrimary, WidgetBrandPalette.textOnPrimary)
    val success = cp(WidgetLightPalette.success, WidgetBrandPalette.success)
    val error = cp(WidgetLightPalette.error, WidgetBrandPalette.error)
    val warning = cp(WidgetLightPalette.warning, WidgetBrandPalette.warning)
    val live = cp(WidgetLightPalette.live, WidgetBrandPalette.live)
    val wicket = cp(WidgetLightPalette.wicket, WidgetBrandPalette.wicket)
    val six = cp(WidgetLightPalette.six, WidgetBrandPalette.six)
    val four = cp(WidgetLightPalette.four, WidgetBrandPalette.four)
    val dotBall = cp(WidgetLightPalette.dotBall, WidgetBrandPalette.dotBall)
    val border = cp(WidgetLightPalette.border, WidgetBrandPalette.border)
    val borderAccent = cp(WidgetLightPalette.borderAccent, WidgetBrandPalette.borderAccent)
}

/**
 * RemoteViews colours, resolved from resources against [context].
 *
 * These cannot be a `ColorProvider`: `RemoteViews.setTextColor` takes a plain ARGB int, so the
 * day/night choice has to be made when the views are built. Reading through `R.color.*` lets the
 * resource system make it — `values-night/colors.xml` carries the dark set — which is why this is a
 * per-Context instance rather than the singleton of constants it used to be. A singleton would also
 * have cached the wrong mode across a theme change.
 */
class WidgetRemoteViewsColors private constructor(ctx: Context) {
    val background = ContextCompat.getColor(ctx, R.color.black_background)
    val surface = ContextCompat.getColor(ctx, R.color.card_surface)
    val surfaceElevated = ContextCompat.getColor(ctx, R.color.card_surface_light)
    val primary = ContextCompat.getColor(ctx, R.color.shiny_gold_deep)
    val primaryDim = ContextCompat.getColor(ctx, R.color.shiny_gold_dark)
    val primaryLight = ContextCompat.getColor(ctx, R.color.shiny_gold_light)
    val primaryContainer = ContextCompat.getColor(ctx, R.color.widget_gold_container)
    val textPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
    val textSecondary = ContextCompat.getColor(ctx, R.color.text_secondary)
    val textTertiary = ContextCompat.getColor(ctx, R.color.text_tertiary)
    val textOnPrimary = ContextCompat.getColor(ctx, R.color.widget_text_on_primary)
    val success = ContextCompat.getColor(ctx, R.color.success_green)
    val error = ContextCompat.getColor(ctx, R.color.error_red)
    val warning = ContextCompat.getColor(ctx, R.color.warning_orange)
    val live = ContextCompat.getColor(ctx, R.color.widget_live)
    val wicket = ContextCompat.getColor(ctx, R.color.widget_ball_wicket)
    val six = ContextCompat.getColor(ctx, R.color.widget_ball_six)
    val four = ContextCompat.getColor(ctx, R.color.widget_ball_four)
    val dotBall = ContextCompat.getColor(ctx, R.color.widget_dot_ball)
    val progressTrack = ContextCompat.getColor(ctx, R.color.widget_probability_track)

    companion object {
        fun of(context: Context): WidgetRemoteViewsColors = WidgetRemoteViewsColors(context)
    }
}


object WidgetTypography {
    private fun style(weight: FontWeight, size: TextUnit, color: GlanceColorProvider) =
        TextStyle(fontWeight = weight, fontSize = size, color = color)

    val displayLarge = style(FontWeight.Bold, 32.sp, WidgetColorScheme.textPrimary)
    val displayMedium = style(FontWeight.Bold, 28.sp, WidgetColorScheme.textPrimary)
    val displaySmall = style(FontWeight.Bold, 24.sp, WidgetColorScheme.textPrimary)
    val headlineLarge = style(FontWeight.Bold, 20.sp, WidgetColorScheme.textPrimary)
    val headlineMedium = style(FontWeight.Medium, 18.sp, WidgetColorScheme.textPrimary)
    val headlineSmall = style(FontWeight.Medium, 16.sp, WidgetColorScheme.textPrimary)
    val titleLarge = style(FontWeight.Medium, 16.sp, WidgetColorScheme.textPrimary)
    val titleMedium = style(FontWeight.Medium, 14.sp, WidgetColorScheme.textPrimary)
    val titleSmall = style(FontWeight.Medium, 12.sp, WidgetColorScheme.textPrimary)
    val bodyLarge = style(FontWeight.Normal, 14.sp, WidgetColorScheme.textSecondary)
    val bodyMedium = style(FontWeight.Normal, 13.sp, WidgetColorScheme.textSecondary)
    val bodySmall = style(FontWeight.Normal, 12.sp, WidgetColorScheme.textSecondary)
    val labelLarge = style(FontWeight.Medium, 12.sp, WidgetColorScheme.textTertiary)
    val labelMedium = style(FontWeight.Medium, 11.sp, WidgetColorScheme.textTertiary)
    val labelSmall = style(FontWeight.Medium, 10.sp, WidgetColorScheme.textTertiary)
    val scoreLarge = style(FontWeight.Bold, 28.sp, WidgetColorScheme.textPrimary)
    val scoreMedium = style(FontWeight.Bold, 22.sp, WidgetColorScheme.textPrimary)
    val scoreSmall = style(FontWeight.Bold, 18.sp, WidgetColorScheme.textPrimary)
    val teamName = style(FontWeight.Medium, 14.sp, WidgetColorScheme.textPrimary)
    val teamAcronym = style(FontWeight.Bold, 12.sp, WidgetColorScheme.textSecondary)
    val statusLive = style(FontWeight.Bold, 10.sp, WidgetColorScheme.live)
    val statusNormal = style(FontWeight.Normal, 11.sp, WidgetColorScheme.textTertiary)
    val winProbability = style(FontWeight.Bold, 16.sp, WidgetColorScheme.primary)
}

object WidgetTheme {
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val radiusXLarge = 20.dp
    val radiusFull = 100.dp

    val spacingXSmall = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 12.dp
    val spacingLarge = 16.dp
    val spacingXLarge = 20.dp
    val spacingXXLarge = 24.dp

    val paddingSmall = 8.dp
    val paddingMedium = 12.dp
    val paddingLarge = 16.dp

    val iconSizeSmall = 16.dp
    val iconSizeMedium = 20.dp
    val iconSizeLarge = 24.dp
    val iconSizeXLarge = 32.dp

    val teamLogoSmall = 24.dp
    val teamLogoMedium = 32.dp
    val teamLogoLarge = 40.dp
    val teamLogoXLarge = 48.dp

    val ballIndicatorSize = 22.dp

    val progressBarHeightSmall = 4.dp
    val progressBarHeightMedium = 6.dp
    val progressBarHeightLarge = 8.dp

    val smallWidgetMinWidth = 110.dp
    val smallWidgetMinHeight = 110.dp
    val mediumWidgetMinWidth = 180.dp
    val mediumWidgetMinHeight = 110.dp
    val largeWidgetMinWidth = 250.dp
    val largeWidgetMinHeight = 180.dp
}

fun GlanceModifier.widgetCornerRadius(radius: Dp = WidgetTheme.radiusMedium): GlanceModifier =
    sentryGuard { this.cornerRadius(radius) }

fun GlanceModifier.widgetCard(
    backgroundColor: GlanceColorProvider = WidgetColorScheme.surface,
    cornerRadius: Dp = WidgetTheme.radiusMedium
): GlanceModifier = this.background(backgroundColor).cornerRadius(cornerRadius)

fun GlanceModifier.widgetCardElevated(
    backgroundColor: GlanceColorProvider = WidgetColorScheme.surfaceElevated,
    cornerRadius: Dp = WidgetTheme.radiusMedium
): GlanceModifier = this.background(backgroundColor).cornerRadius(cornerRadius)

fun GlanceModifier.widgetCardAccent(
    backgroundColor: GlanceColorProvider = WidgetColorScheme.primaryContainer,
    cornerRadius: Dp = WidgetTheme.radiusMedium
): GlanceModifier = this.background(backgroundColor).cornerRadius(cornerRadius)

object WidgetShadow {
    val shadowColorLight = Color(0x1A000000)
    val shadowColorMedium = Color(0x26000000)
    val shadowColorDark = Color(0x33000000)

    @Composable
    fun ShadowedContainer(
        modifier: GlanceModifier = GlanceModifier,
        shadowColor: GlanceColorProvider = ColorProvider(
            day = shadowColorLight,
            night = Color(0x00000000)
        ),
        contentBackgroundColor: GlanceColorProvider = WidgetColorScheme.surface,
        cornerRadius: Dp = WidgetTheme.radiusMedium,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = modifier
                .background(shadowColor)
                .cornerRadius(cornerRadius + 2.dp)
                .padding(bottom = 2.dp, end = 2.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(contentBackgroundColor)
                    .cornerRadius(cornerRadius)
            ) {
                content()
            }
        }
    }

    @Composable
    fun GlowContainer(
        modifier: GlanceModifier = GlanceModifier,
        glowColor: GlanceColorProvider = WidgetColorScheme.primaryContainer,
        contentBackgroundColor: GlanceColorProvider = WidgetColorScheme.surface,
        cornerRadius: Dp = WidgetTheme.radiusMedium,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = modifier
                .background(glowColor)
                .cornerRadius(cornerRadius + 1.dp)
                .padding(1.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(contentBackgroundColor)
                    .cornerRadius(cornerRadius)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SmallWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingSmall)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            header?.let {
                Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingXSmall)) {
                    it()
                }
            }
            Box(modifier = GlanceModifier.defaultWeight()) {
                content()
            }
            footer?.let {
                Box(modifier = GlanceModifier.padding(top = WidgetTheme.spacingXSmall)) {
                    it()
                }
            }
        }
    }
}

@Composable
fun MediumWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable (() -> Unit)? = null,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            header?.let {
                Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    leftContent()
                }
                Box(
                    modifier = GlanceModifier.padding(horizontal = WidgetTheme.spacingSmall),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.glance.text.Text(
                        text = "vs",
                        style = WidgetTypography.labelMedium
                    )
                }
                Box(modifier = GlanceModifier.defaultWeight()) {
                    rightContent()
                }
            }
            footer?.let {
                Box(modifier = GlanceModifier.padding(top = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
        }
    }
}

@Composable
fun LargeWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable () -> Unit,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable (() -> Unit)? = null,
    footer: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingLarge)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingMedium)) {
                header()
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                primaryContent()
            }
            secondaryContent?.let {
                Box(modifier = GlanceModifier.padding(vertical = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
            Box(modifier = GlanceModifier.padding(top = WidgetTheme.spacingMedium)) {
                footer()
            }
        }
    }
}

object WidgetGradients {
    val goldGradient = WidgetColorScheme.primary
    val successGradient = WidgetColorScheme.success
    val errorGradient = WidgetColorScheme.error
    val surfaceGradient = WidgetColorScheme.surfaceElevated
    val glassEffect = ColorProvider(
        day = Color(0xE6FFFFFF),
        night = Color(0x99141414)
    )
}

object WidgetDimensions {
    fun teamLogoSize(isSmallWidget: Boolean): Dp = sentryGuard {
        if (isSmallWidget) WidgetTheme.teamLogoSmall else WidgetTheme.teamLogoMedium
    }

    fun scoreTextStyle(isSmallWidget: Boolean): TextStyle = sentryGuard {
        if (isSmallWidget) WidgetTypography.scoreSmall else WidgetTypography.scoreMedium
    }

    fun contentPadding(isSmallWidget: Boolean): Dp = sentryGuard {
        if (isSmallWidget) WidgetTheme.paddingSmall else WidgetTheme.paddingMedium
    }

    fun contentSpacing(isSmallWidget: Boolean): Dp = sentryGuard {
        if (isSmallWidget) WidgetTheme.spacingXSmall else WidgetTheme.spacingSmall
    }
}

object WidgetGlassmorphism {
    val glassBackground = ColorProvider(
        day = Color(0xCCFFFFFF),
        night = Color(0x80141414)
    )
    val frostedGlass = ColorProvider(
        day = Color(0xE6F8F9FA),
        night = Color(0xB30F0F0F)
    )
    val glassBorder = ColorProvider(
        day = Color(0x1F1A1A1A),
        night = Color(0x1AFFFFFF)
    )

    fun GlanceModifier.glassCard(cornerRadius: Dp = WidgetTheme.radiusMedium): GlanceModifier =
        this.background(glassBackground).cornerRadius(cornerRadius)

    fun GlanceModifier.frostedCard(cornerRadius: Dp = WidgetTheme.radiusMedium): GlanceModifier =
        this.background(frostedGlass).cornerRadius(cornerRadius)
}

object WidgetAnimationStyles {
    val pulseColors = listOf(
        WidgetColorScheme.live,
        ColorProvider(day = Color(0xFFFF6B6B), night = Color(0xFFFF5252)),
        ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFFF3B30))
    )

    fun getPulseColor(cycleIndex: Int): GlanceColorProvider = sentryGuard {
        pulseColors[cycleIndex % pulseColors.size]
    }

    val shimmerBase = ColorProvider(day = Color(0xFFE5E7EB), night = Color(0xFF2A2A2A))
    val shimmerHighlight = ColorProvider(day = Color(0xFFF3F4F6), night = Color(0xFF3A3A3A))

    fun GlanceModifier.shimmerPlaceholder(cornerRadius: Dp = WidgetTheme.radiusSmall): GlanceModifier =
        this.background(shimmerBase).cornerRadius(cornerRadius)
}

object CompactWidgetStyles {
    val compactScoreStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = WidgetColorScheme.textPrimary
    )
    val compactTeamStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = WidgetColorScheme.textSecondary
    )
    val compactLabelStyle = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 8.sp,
        color = WidgetColorScheme.textTertiary
    )
    val ultraCompactSpacing = 2.dp
    val compactPadding = 4.dp
    val compactCornerRadius = 6.dp
}

@Composable
fun ExtraLargeWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable () -> Unit,
    scoreboard: @Composable () -> Unit,
    battingCard: @Composable (() -> Unit)? = null,
    bowlingCard: @Composable (() -> Unit)? = null,
    statsRow: @Composable (() -> Unit)? = null,
    footer: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusXLarge)
            .padding(WidgetTheme.spacingXXLarge)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingMedium)) {
                header()
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .widgetCard()
                    .padding(WidgetTheme.paddingMedium)
            ) {
                scoreboard()
            }
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = WidgetTheme.spacingMedium),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                battingCard?.let {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .widgetCardElevated()
                            .padding(WidgetTheme.paddingSmall)
                    ) {
                        it()
                    }
                }
                Spacer(modifier = GlanceModifier.width(WidgetTheme.spacingSmall))
                bowlingCard?.let {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .widgetCardElevated()
                            .padding(WidgetTheme.paddingSmall)
                    ) {
                        it()
                    }
                }
            }
            statsRow?.let {
                Box(modifier = GlanceModifier.padding(vertical = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = WidgetTheme.spacingMedium)
            ) {
                footer()
            }
        }
    }
}

object ResponsiveSizing {
    enum class WidgetSizeClass { SMALL, MEDIUM, LARGE, EXTRA_LARGE }

    fun getScoreStyle(sizeClass: WidgetSizeClass): TextStyle = sentryGuard {
        when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTypography.scoreSmall
            WidgetSizeClass.MEDIUM -> WidgetTypography.scoreMedium
            WidgetSizeClass.LARGE -> WidgetTypography.scoreLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTypography.displaySmall
        }
    }

    fun getTeamNameStyle(sizeClass: WidgetSizeClass): TextStyle = sentryGuard {
        when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTypography.labelMedium
            WidgetSizeClass.MEDIUM -> WidgetTypography.titleSmall
            WidgetSizeClass.LARGE -> WidgetTypography.titleMedium
            WidgetSizeClass.EXTRA_LARGE -> WidgetTypography.titleLarge
        }
    }

    fun getPadding(sizeClass: WidgetSizeClass): Dp = sentryGuard {
        when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTheme.paddingSmall
            WidgetSizeClass.MEDIUM -> WidgetTheme.paddingMedium
            WidgetSizeClass.LARGE -> WidgetTheme.paddingLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTheme.spacingXXLarge
        }
    }

    fun getLogoSize(sizeClass: WidgetSizeClass): Dp = sentryGuard {
        when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTheme.teamLogoSmall
            WidgetSizeClass.MEDIUM -> WidgetTheme.teamLogoMedium
            WidgetSizeClass.LARGE -> WidgetTheme.teamLogoLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTheme.teamLogoXLarge
        }
    }

    fun getCornerRadius(sizeClass: WidgetSizeClass): Dp = sentryGuard {
        when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTheme.radiusMedium
            WidgetSizeClass.MEDIUM -> WidgetTheme.radiusLarge
            WidgetSizeClass.LARGE -> WidgetTheme.radiusLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTheme.radiusXLarge
        }
    }

    fun getMaxBalls(sizeClass: WidgetSizeClass): Int = sentryGuard {
        when (sizeClass) {
            WidgetSizeClass.SMALL -> 3
            WidgetSizeClass.MEDIUM -> 6
            WidgetSizeClass.LARGE -> 6
            WidgetSizeClass.EXTRA_LARGE -> 12
        }
    }

    fun showExtendedInfo(sizeClass: WidgetSizeClass): Boolean = sentryGuard {
        sizeClass == WidgetSizeClass.LARGE || sizeClass == WidgetSizeClass.EXTRA_LARGE
    }
}

object DynamicWidgetTheme {
    fun teamColorProvider(
        teamColor: Color,
        fallbackDay: Color = Color(0xFFB39800),
        fallbackNight: Color = Color(0xFFFFD700)
    ): GlanceColorProvider {
        return ColorProvider(
            day = teamColor.takeIf { it != Color.Transparent } ?: fallbackDay,
            night = teamColor.takeIf { it != Color.Transparent } ?: fallbackNight
        )
    }

    fun accentSurface(accentColor: Color, alpha: Float = 0.1f): GlanceColorProvider = sentryGuard {
        ColorProvider(
            day = accentColor.copy(alpha = alpha),
            night = accentColor.copy(alpha = alpha * 0.5f)
        )
    }

    object MatchFormatColors {
        val test = ColorProvider(day = Color(0xFF8B4513), night = Color(0xFFCD853F))
        val odi = ColorProvider(day = Color(0xFF1E3A8A), night = Color(0xFF3B82F6))
        val t20 = ColorProvider(day = Color(0xFF7C3AED), night = Color(0xFF8B5CF6))
        val t10 = ColorProvider(day = Color(0xFFEC4899), night = Color(0xFFF472B6))
        val theHundred = ColorProvider(day = Color(0xFF059669), night = Color(0xFF10B981))
        val default = WidgetColorScheme.primary

        fun getFormatColor(format: String): GlanceColorProvider = sentryGuard {
            when (format.lowercase()) {
                "test" -> test
                "odi" -> odi
                "t20", "t20i" -> t20
                "t10" -> t10
                "100", "hundred", "the hundred" -> theHundred
                else -> default
            }
        }
    }
}

@Composable
fun WidgetLoadingLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .background(WidgetAnimationStyles.shimmerBase)
                    .cornerRadius(20.dp)
            ) {}
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))
            androidx.glance.text.Text(
                text = LocalContext.current.getString(R.string.widget_loading),
                style = WidgetTypography.bodyMedium
            )
        }
    }
}

@Composable
fun WidgetErrorLayout(
    modifier: GlanceModifier = GlanceModifier,
    message: String = "Unable to load match data",
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            androidx.glance.text.Text(
                text = "⚠️",
                style = TextStyle(fontSize = 24.sp)
            )
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))
            androidx.glance.text.Text(
                text = message,
                style = WidgetTypography.bodyMedium
            )
        }
    }
}

@Composable
fun WidgetEmptyLayout(
    modifier: GlanceModifier = GlanceModifier,
    message: String = "No live matches",
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            androidx.glance.text.Text(
                text = "🏏",
                style = TextStyle(fontSize = 32.sp)
            )
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))
            androidx.glance.text.Text(
                text = message,
                style = WidgetTypography.bodyMedium
            )
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingXSmall))
            androidx.glance.text.Text(
                text = LocalContext.current.getString(R.string.widget_tap_to_open_app),
                style = WidgetTypography.labelSmall
            )
        }
    }
}

/**
 * A team's name colour on a widget: its team ink on each of the two widget grounds when the registry
 * knows the side, the plain text colour otherwise. Day and night are resolved separately because a
 * Glance colour cannot be read at composition time — the launcher picks the mode.
 */
internal fun widgetTeamInk(teamName: String): GlanceColorProvider {
    if (!TeamColorRegistry.isKnown(teamName)) return WidgetColorScheme.textPrimary
    val day = TeamColorRegistry.Ground(WidgetLightPalette.background, WidgetLightPalette.surfaceElevated, WidgetLightPalette.textPrimary)
    val night = TeamColorRegistry.Ground(WidgetBrandPalette.background, WidgetBrandPalette.surfaceElevated, WidgetBrandPalette.textPrimary)
    return cp(TeamColorRegistry.forTeam(teamName, day).ink, TeamColorRegistry.forTeam(teamName, night).ink)
}
