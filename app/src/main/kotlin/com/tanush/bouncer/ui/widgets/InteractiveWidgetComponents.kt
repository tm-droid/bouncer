package com.tanush.bouncer.ui.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tanush.bouncer.BouncerActivity
import java.util.Locale

/**
 * InteractiveWidgetComponents - Tappable and Interactive Widget Elements
 * 
 * Provides interactive components for Glance widgets including:
 * - Tappable score areas with navigation
 * - Quick action buttons (mute, favorite, share)
 * - Overs progress bar visualization
 * - Team logo display optimization
 * - Live indicator with animation simulation
 * - Manual refresh functionality
 */

// ===========================================
// ACTION PARAMETERS
// ===========================================

object WidgetActionKeys {
    val matchIdKey = ActionParameters.Key<String>("match_id")
    val actionTypeKey = ActionParameters.Key<String>("action_type")
    val teamIdKey = ActionParameters.Key<String>("team_id")
}

// ===========================================
// ACTION CALLBACKS
// ===========================================

/**
 * Callback for refreshing widget data
 */
class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, LiveMatchWidgetProvider::class.java).apply {
            action = LiveMatchWidgetProvider.ACTION_REFRESH
        }
        context.sendBroadcast(intent)
    }
}

/**
 * Callback for toggling favorite status
 */
class ToggleFavoriteCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val matchId = parameters[WidgetActionKeys.matchIdKey] ?: return
        val prefs = context.getSharedPreferences("widget_favorites", Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet("favorites", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (!favorites.add(matchId)) favorites.remove(matchId)
        prefs.edit().putStringSet("favorites", favorites).apply()
    }
}

/**
 * Callback for toggling mute status
 */
class ToggleMuteCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val matchId = parameters[WidgetActionKeys.matchIdKey] ?: return
        val prefs = context.getSharedPreferences("widget_muted", Context.MODE_PRIVATE)
        val mutedMatches = prefs.getStringSet("muted", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (!mutedMatches.add(matchId)) mutedMatches.remove(matchId)
        prefs.edit().putStringSet("muted", mutedMatches).apply()
    }
}

/**
 * Callback for switching to next match
 */
class NextMatchCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, LiveMatchWidgetProvider::class.java).apply {
            action = LiveMatchWidgetProvider.ACTION_NEXT_MATCH
        }
        context.sendBroadcast(intent)
    }
}

// ===========================================
// TAPPABLE SCORE AREA
// ===========================================

/**
 * Tappable score area that navigates to match details
 */
@Composable
fun TappableScoreArea(
    modifier: GlanceModifier = GlanceModifier,
    team1Name: String,
    team2Name: String,
    team1Score: String,
    team2Score: String?,
    matchId: String,
    onTap: Action = actionStartActivity<BouncerActivity>()
) {
    Box(
        modifier = modifier
            .widgetCard()
            .clickable(onTap)
            .padding(WidgetTheme.paddingMedium)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            TeamScoreRow(
                teamName = team1Name,
                score = team1Score,
                isBatting = true
            )

            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(40.dp)
                        .height(1.dp)
                        .background(WidgetColorScheme.border)
                ) {}
                Text(
                    text = "  vs  ",
                    style = WidgetTypography.labelMedium
                )
                Box(
                    modifier = GlanceModifier
                        .width(40.dp)
                        .height(1.dp)
                        .background(WidgetColorScheme.border)
                ) {}
            }

            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))

            TeamScoreRow(
                teamName = team2Name,
                score = team2Score ?: "Yet to bat",
                isBatting = team2Score != null && team2Score != "-"
            )
        }
    }
}

/**
 * Individual team score row
 */
@Composable
private fun TeamScoreRow(
    teamName: String,
    score: String,
    isBatting: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.Start,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = teamName,
            style = WidgetTypography.teamName,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = score,
            style = if (isBatting) WidgetTypography.scoreMedium else WidgetTypography.bodyMedium
        )
    }
}

// ===========================================
// QUICK ACTION BUTTONS
// ===========================================

/**
 * Row of quick action buttons (mute, favorite, share, refresh)
 */
@Composable
fun QuickActionButtons(
    modifier: GlanceModifier = GlanceModifier,
    matchId: String,
    isFavorite: Boolean = false,
    isMuted: Boolean = false,
    showShare: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        QuickActionButton(
            icon = if (isMuted) "🔇" else "🔔",
            contentDescription = if (isMuted) "Unmute" else "Mute",
            isActive = isMuted,
            onClick = actionRunCallback<ToggleMuteCallback>(
                actionParametersOf(WidgetActionKeys.matchIdKey to matchId)
            )
        )
        QuickActionButton(
            icon = if (isFavorite) "★" else "☆",
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            isActive = isFavorite,
            activeColor = WidgetColorScheme.primary,
            onClick = actionRunCallback<ToggleFavoriteCallback>(
                actionParametersOf(WidgetActionKeys.matchIdKey to matchId)
            )
        )
        if (showShare) {
            QuickActionButton(
                icon = "↗",
                contentDescription = "Share",
                onClick = actionStartActivity<BouncerActivity>()
            )
        }
        WidgetRefreshButton()
    }
}

/**
 * Individual quick action button
 */
@Composable
fun QuickActionButton(
    icon: String,
    contentDescription: String,
    isActive: Boolean = false,
    activeColor: ColorProvider = WidgetColorScheme.primary,
    inactiveColor: ColorProvider = WidgetColorScheme.textTertiary,
    onClick: Action
) {
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(if (isActive) WidgetColorScheme.primaryContainer else WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusSmall)
            .clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            style = TextStyle(
                fontSize = 16.sp,
                color = if (isActive) activeColor else inactiveColor
            )
        )
    }
}

// ===========================================
// OVERS PROGRESS BAR
// ===========================================

/**
 * Progress bar showing match overs progress
 */
@Composable
fun OversProgressBar(
    modifier: GlanceModifier = GlanceModifier,
    currentOvers: Float,
    totalOvers: Float,
    teamColor: ColorProvider = WidgetColorScheme.primary
) {
    val progress = if (totalOvers > 0) (currentOvers / totalOvers).coerceIn(0f, 1f) else 0f
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.Start
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            Text(
                text = "Overs: ",
                style = WidgetTypography.labelMedium
            )
            Text(
                text = "${String.format(Locale.US, "%.1f", currentOvers)}/${totalOvers.toInt()}",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WidgetColorScheme.textPrimary
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(WidgetTheme.progressBarHeightMedium)
                .background(WidgetColorScheme.surfaceHighest)
                .cornerRadius(WidgetTheme.radiusFull)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width((progress * 200).dp.coerceAtMost(200.dp))
                    .background(teamColor)
                    .cornerRadius(WidgetTheme.radiusFull)
            ) {}
        }
    }
}

/**
 * Enhanced overs progress with innings breakdown
 */
@Composable
fun OversProgressBarEnhanced(
    modifier: GlanceModifier = GlanceModifier,
    currentOvers: Float,
    totalOvers: Float,
    team1Overs: Float,
    team2Overs: Float?,
    team1Color: ColorProvider = WidgetColorScheme.primary,
    team2Color: ColorProvider = WidgetColorScheme.textSecondary
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        InningsProgressRow(
            teamLabel = "1st",
            overs = team1Overs,
            totalOvers = totalOvers,
            color = team1Color
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        team2Overs?.let { overs ->
            InningsProgressRow(
                teamLabel = "2nd",
                overs = overs,
                totalOvers = totalOvers,
                color = team2Color
            )
        }
    }
}

/**
 * Individual innings progress row
 */
@Composable
private fun InningsProgressRow(
    teamLabel: String,
    overs: Float,
    totalOvers: Float,
    color: ColorProvider
) {
    val progress = if (totalOvers > 0) (overs / totalOvers).coerceIn(0f, 1f) else 0f
    
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = teamLabel,
            style = WidgetTypography.labelSmall,
            modifier = GlanceModifier.width(24.dp)
        )
        
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(4.dp)
                .background(WidgetColorScheme.surfaceHighest)
                .cornerRadius(2.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width((progress * 150).dp.coerceAtMost(150.dp))
                    .background(color)
                    .cornerRadius(2.dp)
            ) {}
        }
        
        Text(
            text = String.format(Locale.US, "%.1f", overs),
            style = WidgetTypography.labelSmall,
            modifier = GlanceModifier.width(32.dp)
        )
    }
}

// ===========================================
// TEAM LOGO WIDGET
// ===========================================

/**
 * Optimized team logo display for widgets
 * Uses placeholder with team initial when image not available
 */
@Composable
fun TeamLogoWidget(
    modifier: GlanceModifier = GlanceModifier,
    teamName: String,
    teamAcronym: String = teamName.take(3).uppercase(),
    teamColor: ColorProvider = WidgetColorScheme.primary,
    size: androidx.compose.ui.unit.Dp = WidgetTheme.teamLogoMedium
) {
    Box(
        modifier = modifier
            .size(size)
            .background(teamColor)
            .cornerRadius(size / 2),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = teamAcronym.take(2),
            style = TextStyle(
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.textOnPrimary
            )
        )
    }
}

/**
 * Team logo with fallback to acronym
 */
@Composable
fun TeamLogoWithFallback(
    modifier: GlanceModifier = GlanceModifier,
    teamName: String,
    teamAcronym: String,
    teamColor: ColorProvider = WidgetColorScheme.primary,
    size: androidx.compose.ui.unit.Dp = WidgetTheme.teamLogoMedium,
    isBatting: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        TeamLogoWidget(
            teamName = teamName,
            teamAcronym = teamAcronym,
            teamColor = teamColor,
            size = size
        )
        if (isBatting) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(WidgetColorScheme.success)
                    .cornerRadius(4.dp)
            ) {}
        }
    }
}

// ===========================================
// LIVE INDICATOR WIDGET
// ===========================================

/**
 * Live indicator badge with pulsing simulation
 * Note: Glance doesn't support true animations, so this uses static styling
 */
@Composable
fun LiveIndicatorWidget(
    modifier: GlanceModifier = GlanceModifier,
    isLive: Boolean = true,
    style: LiveIndicatorStyle = LiveIndicatorStyle.BADGE
) {
    if (!isLive) return
    
    when (style) {
        LiveIndicatorStyle.BADGE -> LiveBadge(modifier)
        LiveIndicatorStyle.DOT -> LiveDot(modifier)
        LiveIndicatorStyle.TEXT -> LiveText(modifier)
        LiveIndicatorStyle.COMPACT -> LiveCompact(modifier)
    }
}

enum class LiveIndicatorStyle {
    BADGE,  // Red pill with "LIVE" text
    DOT,    // Red pulsing dot
    TEXT,   // Just "LIVE" text
    COMPACT // Minimal dot
}

@Composable
private fun LiveBadge(modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .background(WidgetColorScheme.live)
            .cornerRadius(WidgetTheme.radiusFull)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "LIVE",
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.White)
            )
        )
    }
}

@Composable
private fun LiveDot(modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .background(ColorProvider(Color(0x33FF3B30)))
            .cornerRadius(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .size(10.dp)
                .background(WidgetColorScheme.live)
                .cornerRadius(5.dp)
        ) {}
    }
}

@Composable
private fun LiveText(modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = "● LIVE",
        style = WidgetTypography.statusLive,
        modifier = modifier
    )
}

@Composable
private fun LiveCompact(modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(WidgetColorScheme.live)
            .cornerRadius(4.dp)
    ) {}
}

// ===========================================
// WIDGET REFRESH BUTTON
// ===========================================

/**
 * Manual refresh button for widget
 */
@Composable
fun WidgetRefreshButton(
    modifier: GlanceModifier = GlanceModifier,
    style: RefreshButtonStyle = RefreshButtonStyle.ICON
) {
    val refreshAction = actionRunCallback<RefreshWidgetCallback>()
    
    when (style) {
        RefreshButtonStyle.ICON -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .background(WidgetColorScheme.surfaceElevated)
                    .cornerRadius(WidgetTheme.radiusSmall)
                    .clickable(refreshAction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = WidgetColorScheme.textSecondary
                    )
                )
            }
        }
        RefreshButtonStyle.TEXT -> {
            Box(
                modifier = modifier
                    .background(WidgetColorScheme.surfaceElevated)
                    .cornerRadius(WidgetTheme.radiusSmall)
                    .clickable(refreshAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Refresh",
                    style = WidgetTypography.labelMedium
                )
            }
        }
        RefreshButtonStyle.ICON_TEXT -> {
            Row(
                modifier = modifier
                    .background(WidgetColorScheme.surfaceElevated)
                    .cornerRadius(WidgetTheme.radiusSmall)
                    .clickable(refreshAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = WidgetColorScheme.textSecondary
                    )
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Refresh",
                    style = WidgetTypography.labelMedium
                )
            }
        }
    }
}

enum class RefreshButtonStyle {
    ICON,
    TEXT,
    ICON_TEXT
}

// ===========================================
// RECENT BALLS DISPLAY
// ===========================================

/**
 * Recent balls strip showing last 6 deliveries
 */
@Composable
fun RecentBallsStrip(
    modifier: GlanceModifier = GlanceModifier,
    balls: List<String>,
    maxBalls: Int = 6
) {
    Row(
        modifier = modifier
            .background(WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        val displayBalls = balls.takeLast(maxBalls)
        
        displayBalls.forEachIndexed { index, ball ->
            BallIndicator(ball = ball)
            if (index < displayBalls.size - 1) {
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
        }
        repeat(maxBalls - displayBalls.size) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            EmptyBallSlot()
        }
    }
}

/**
 * Individual ball indicator
 */
@Composable
fun BallIndicator(
    ball: String,
    size: androidx.compose.ui.unit.Dp = WidgetTheme.ballIndicatorSize
) {
    val (bgColor, textColor) = getBallColors(ball)
    
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(bgColor)
            .cornerRadius(size / 2),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatBallText(ball),
            style = TextStyle(
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}

/**
 * Empty ball slot placeholder
 */
@Composable
private fun EmptyBallSlot(
    size: androidx.compose.ui.unit.Dp = WidgetTheme.ballIndicatorSize
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(WidgetColorScheme.surfaceHighest)
            .cornerRadius(size / 2)
    ) {}
}

/**
 * Get colors for ball type
 */
private fun getBallColors(ball: String): Pair<ColorProvider, ColorProvider> =
    when (ball.uppercase().trim()) {
        "W" -> WidgetColorScheme.wicket to ColorProvider(Color.White)
        "6" -> WidgetColorScheme.six to ColorProvider(Color.White)
        "4" -> WidgetColorScheme.four to ColorProvider(Color.White)
        "0", "•" -> WidgetColorScheme.dotBall to WidgetColorScheme.textTertiary
        "NB", "WD", "LB", "B" -> WidgetColorScheme.warning to ColorProvider(Color.Black)
        else -> WidgetColorScheme.surfaceHighest to WidgetColorScheme.textPrimary
    }

/**
 * Format ball text for display
 */
private fun formatBallText(ball: String): String =
    when (ball.uppercase().trim()) {
        "0" -> "•"
        "NB" -> "nb"
        "WD" -> "wd"
        "LB" -> "lb"
        else -> ball
    }

// ===========================================
// WIN PROBABILITY DISPLAY
// ===========================================

/**
 * Win probability indicator with team colors
 */
@Composable
fun WinProbabilityIndicator(
    modifier: GlanceModifier = GlanceModifier,
    team1Name: String,
    team2Name: String,
    team1Probability: Float,
    team1Color: ColorProvider = WidgetColorScheme.primary,
    team2Color: ColorProvider = WidgetColorScheme.textSecondary
) {
    val team1Percent = (team1Probability * 100).toInt()
    val team2Percent = 100 - team1Percent
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "$team1Name $team1Percent%",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = team1Color
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "$team2Percent% $team2Name",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = team2Color
                )
            )
        }
        
        Spacer(modifier = GlanceModifier.height(4.dp))

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(8.dp)
                .cornerRadius(4.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width((team1Probability * 200).dp.coerceAtMost(200.dp))
                    .background(team1Color)
                    .cornerRadius(4.dp)
            ) {}
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .background(team2Color)
                    .cornerRadius(4.dp)
            ) {}
        }
    }
}

// ===========================================
// MATCH STATUS CHIP
// ===========================================

/**
 * Match status chip (Live, Completed, Upcoming)
 */
@Composable
fun MatchStatusChip(
    modifier: GlanceModifier = GlanceModifier,
    status: MatchStatus,
    customText: String? = null
) {
    val (bgColor, textColor, text) = when (status) {
        MatchStatus.LIVE -> Triple(
            WidgetColorScheme.live,
            ColorProvider(Color.White),
            customText ?: "LIVE"
        )
        MatchStatus.COMPLETED -> Triple(
            WidgetColorScheme.surfaceHighest,
            WidgetColorScheme.textSecondary,
            customText ?: "Completed"
        )
        MatchStatus.UPCOMING -> Triple(
            WidgetColorScheme.primaryContainer,
            WidgetColorScheme.primary,
            customText ?: "Upcoming"
        )
        MatchStatus.STUMPS -> Triple(
            WidgetColorScheme.warning,
            ColorProvider(Color.Black),
            customText ?: "Stumps"
        )
        MatchStatus.TEA -> Triple(
            WidgetColorScheme.surfaceHighest,
            WidgetColorScheme.textSecondary,
            customText ?: "Tea"
        )
        MatchStatus.LUNCH -> Triple(
            WidgetColorScheme.surfaceHighest,
            WidgetColorScheme.textSecondary,
            customText ?: "Lunch"
        )
        MatchStatus.RAIN_DELAY -> Triple(
            ColorProvider(Color(0xFF60A5FA)),
            ColorProvider(Color.White),
            customText ?: "Rain Delay"
        )
    }
    
    Box(
        modifier = modifier
            .background(bgColor)
            .cornerRadius(WidgetTheme.radiusFull)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        )
    }
}

enum class MatchStatus {
    LIVE,
    COMPLETED,
    UPCOMING,
    STUMPS,
    TEA,
    LUNCH,
    RAIN_DELAY
}

// ===========================================
// NAV DOTS (FOR MULTIPLE MATCHES)
// ===========================================

/**
 * Navigation dots for multiple matches
 */
@Composable
fun MatchNavigationDots(
    modifier: GlanceModifier = GlanceModifier,
    totalMatches: Int,
    currentIndex: Int,
    onNextMatch: Action = actionRunCallback<NextMatchCallback>()
) {
    Row(
        modifier = modifier
            .clickable(onNextMatch),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        val activeIndex = currentIndex % totalMatches
        repeat(totalMatches.coerceAtMost(5)) { index ->
            val isActive = index == activeIndex
            Box(
                modifier = GlanceModifier
                    .size(if (isActive) 8.dp else 6.dp)
                    .background(if (isActive) WidgetColorScheme.primary else WidgetColorScheme.textTertiary)
                    .cornerRadius(4.dp)
            ) {}
            if (index < totalMatches - 1) {
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
        }
    }
}

// ===========================================
// BATTING PAIR DISPLAY
// ===========================================

/**
 * Displays current batting pair with their scores
 */
@Composable
fun BattingPairWidget(
    modifier: GlanceModifier = GlanceModifier,
    striker: BatsmanInfo,
    nonStriker: BatsmanInfo
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(WidgetTheme.paddingSmall)
    ) {
        BatsmanRow(
            batsman = striker,
            isStriker = true
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        BatsmanRow(
            batsman = nonStriker,
            isStriker = false
        )
    }
}

@Composable
private fun BatsmanRow(
    batsman: BatsmanInfo,
    isStriker: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (isStriker) {
            Text(
                text = "●",
                style = TextStyle(
                    fontSize = 8.sp,
                    color = WidgetColorScheme.success
                ),
                modifier = GlanceModifier.width(12.dp)
            )
        } else {
            Spacer(modifier = GlanceModifier.width(12.dp))
        }
        Text(
            text = batsman.name,
            style = WidgetTypography.bodySmall,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = "${batsman.runs}",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.textPrimary
            )
        )
        Text(
            text = "(${batsman.balls})",
            style = WidgetTypography.labelSmall,
            modifier = GlanceModifier.padding(start = 2.dp)
        )
    }
}

data class BatsmanInfo(
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int = 0,
    val sixes: Int = 0
)

// ===========================================
// CURRENT BOWLER DISPLAY
// ===========================================

/**
 * Displays current bowler with their figures
 */
@Composable
fun CurrentBowlerWidget(
    modifier: GlanceModifier = GlanceModifier,
    bowler: BowlerInfo
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(horizontal = WidgetTheme.paddingSmall, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "⚾",
            style = TextStyle(fontSize = 12.sp),
            modifier = GlanceModifier.padding(end = 6.dp)
        )
        Text(
            text = bowler.name,
            style = WidgetTypography.bodySmall,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = "${bowler.wickets}/${bowler.runs}",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.textPrimary
            )
        )
        Text(
            text = " (${bowler.overs})",
            style = WidgetTypography.labelSmall
        )
    }
}

data class BowlerInfo(
    val name: String,
    val overs: String,
    val runs: Int,
    val wickets: Int,
    val maidens: Int = 0
)

// ===========================================
// RUN RATE COMPARISON
// ===========================================

/**
 * Displays current run rate vs required run rate
 */
@Composable
fun RunRateComparison(
    modifier: GlanceModifier = GlanceModifier,
    currentRunRate: Float,
    requiredRunRate: Float? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(WidgetTheme.paddingSmall),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            modifier = GlanceModifier.defaultWeight()
        ) {
            Text(
                text = "CRR",
                style = WidgetTypography.labelSmall
            )
            Text(
                text = String.format(Locale.US, "%.2f", currentRunRate),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WidgetColorScheme.textPrimary
                )
            )
        }

        requiredRunRate?.let { rrr ->
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(WidgetColorScheme.border)
            ) {}
            
            Column(
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = "RRR",
                    style = WidgetTypography.labelSmall
                )
                val rrrColor = if (rrr > currentRunRate) WidgetColorScheme.error else WidgetColorScheme.success
                Text(
                    text = String.format(Locale.US, "%.2f", rrr),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = rrrColor
                    )
                )
            }
        }
    }
}

// ===========================================
// COUNTDOWN TIMER DISPLAY
// ===========================================

/**
 * Countdown timer for upcoming matches
 */
@Composable
fun MatchCountdown(
    modifier: GlanceModifier = GlanceModifier,
    hoursRemaining: Int,
    minutesRemaining: Int,
    matchTitle: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetColorScheme.primaryContainer)
            .cornerRadius(WidgetTheme.radiusMedium)
            .padding(WidgetTheme.paddingMedium),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = matchTitle,
            style = WidgetTypography.titleSmall
        )
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        Row(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.Bottom
        ) {
            TimeUnit(value = hoursRemaining, label = "h")
            Text(
                text = ":",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WidgetColorScheme.primary
                ),
                modifier = GlanceModifier.padding(horizontal = 4.dp)
            )
            TimeUnit(value = minutesRemaining, label = "m")
        }
        
        Spacer(modifier = GlanceModifier.height(4.dp))
        
        Text(
            text = "until match starts",
            style = WidgetTypography.labelSmall
        )
    }
}

@Composable
private fun TimeUnit(
    value: Int,
    label: String
) {
    Row(
        verticalAlignment = Alignment.Vertical.Bottom
    ) {
        Text(
            text = String.format("%02d", value),
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.primary
            )
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                color = WidgetColorScheme.textSecondary
            ),
            modifier = GlanceModifier.padding(bottom = 2.dp)
        )
    }
}

// ===========================================
// SCORE COMPARISON WIDGET
// ===========================================

/**
 * Side-by-side score comparison for chasing team
 */
@Composable
fun ScoreComparisonWidget(
    modifier: GlanceModifier = GlanceModifier,
    team1Name: String,
    team1Score: Int,
    team1Overs: Float,
    team2Name: String,
    team2Score: Int,
    team2Overs: Float,
    targetScore: Int? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusMedium)
            .padding(WidgetTheme.paddingMedium)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = team1Name,
                style = WidgetTypography.labelMedium,
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "vs",
                style = WidgetTypography.labelSmall
            )
            Text(
                text = team2Name,
                style = WidgetTypography.labelMedium,
                modifier = GlanceModifier.defaultWeight()
            )
        }
        
        Spacer(modifier = GlanceModifier.height(8.dp))

        val maxScore = maxOf(team1Score, team2Score, targetScore ?: 0)
        val team1Progress = if (maxScore > 0) team1Score.toFloat() / maxScore else 0f
        val team2Progress = if (maxScore > 0) team2Score.toFloat() / maxScore else 0f

        ScoreProgressBar(
            score = team1Score,
            overs = team1Overs,
            progress = team1Progress,
            color = WidgetColorScheme.primary
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        ScoreProgressBar(
            score = team2Score,
            overs = team2Overs,
            progress = team2Progress,
            color = WidgetColorScheme.textSecondary
        )

        targetScore?.let { target ->
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Target: $target",
                style = WidgetTypography.labelSmall
            )
        }
    }
}

@Composable
private fun ScoreProgressBar(
    score: Int,
    overs: Float,
    progress: Float,
    color: ColorProvider
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .height(6.dp)
                .width((progress * 120).dp.coerceIn(4.dp, 120.dp))
                .background(color)
                .cornerRadius(3.dp)
        ) {}
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        Text(
            text = "$score (${String.format(Locale.US, "%.1f", overs)})",
            style = WidgetTypography.labelSmall
        )
    }
}

// ===========================================
// PARTNERSHIP DISPLAY
// ===========================================

/**
 * Current partnership display
 */
@Composable
fun PartnershipWidget(
    modifier: GlanceModifier = GlanceModifier,
    runs: Int,
    balls: Int,
    batsman1: String,
    batsman2: String
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WidgetColorScheme.surfaceElevated)
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(horizontal = WidgetTheme.paddingSmall, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "Partnership:",
            style = WidgetTypography.labelSmall,
            modifier = GlanceModifier.padding(end = 4.dp)
        )
        
        Text(
            text = "$runs",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.primary
            )
        )
        
        Text(
            text = "($balls)",
            style = WidgetTypography.labelSmall,
            modifier = GlanceModifier.padding(start = 2.dp)
        )
        
        Spacer(modifier = GlanceModifier.defaultWeight())
        
        Text(
            text = "$batsman1 & $batsman2",
            style = WidgetTypography.labelSmall
        )
    }
}

// ===========================================
// LAST WICKET DISPLAY
// ===========================================

/**
 * Last wicket fallen display
 */
@Composable
fun LastWicketWidget(
    modifier: GlanceModifier = GlanceModifier,
    batsmanName: String,
    runs: Int,
    balls: Int,
    dismissalType: String,
    teamScoreAtWicket: String
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorProvider(Color(0x1AFF3B30)))
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(horizontal = WidgetTheme.paddingSmall, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "W",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.wicket
            ),
            modifier = GlanceModifier
                .background(ColorProvider(Color(0x33FF3B30)))
                .cornerRadius(2.dp)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        
        Spacer(modifier = GlanceModifier.width(6.dp))
        
        Text(
            text = "$batsmanName $runs($balls)",
            style = WidgetTypography.labelMedium,
            modifier = GlanceModifier.defaultWeight()
        )
        
        Text(
            text = teamScoreAtWicket,
            style = WidgetTypography.labelSmall
        )
    }
}

// ===========================================
// EXPANDED MATCH DETAILS
// ===========================================

/**
 * Expandable match details card
 */
@Composable
fun ExpandedMatchDetails(
    modifier: GlanceModifier = GlanceModifier,
    team1Name: String,
    team1Score: String,
    team2Name: String,
    team2Score: String?,
    currentOvers: Float,
    totalOvers: Float,
    status: String,
    venue: String,
    matchType: String,
    striker: BatsmanInfo? = null,
    nonStriker: BatsmanInfo? = null,
    currentBowler: BowlerInfo? = null,
    recentBalls: List<String> = emptyList(),
    winProbability: Float? = null,
    matchId: String,
    onTap: Action = actionStartActivity<BouncerActivity>()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widgetCard()
            .clickable(onTap)
            .padding(WidgetTheme.paddingMedium)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            MatchStatusChip(status = MatchStatus.LIVE)
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = matchType,
                style = WidgetTypography.labelMedium,
                modifier = GlanceModifier.defaultWeight()
            )
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        TeamScoreRow(
            teamName = team1Name,
            score = team1Score,
            isBatting = team2Score == null || team2Score == "-"
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        TeamScoreRow(
            teamName = team2Name,
            score = team2Score ?: "Yet to bat",
            isBatting = team2Score != null && team2Score != "-"
        )

        Spacer(modifier = GlanceModifier.height(12.dp))

        OversProgressBar(
            currentOvers = currentOvers,
            totalOvers = totalOvers
        )

        if (striker != null && nonStriker != null) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            BattingPairWidget(
                striker = striker,
                nonStriker = nonStriker
            )
        }
        currentBowler?.let {
            Spacer(modifier = GlanceModifier.height(4.dp))
            CurrentBowlerWidget(bowler = it)
        }
        if (recentBalls.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            RecentBallsStrip(balls = recentBalls)
        }
        winProbability?.let { prob ->
            Spacer(modifier = GlanceModifier.height(8.dp))
            WinProbabilityIndicator(
                team1Name = team1Name,
                team2Name = team2Name,
                team1Probability = prob
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = status,
            style = WidgetTypography.bodySmall
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = venue,
            style = WidgetTypography.labelSmall
        )
    }
}

// ===========================================
// POWERPLAY INDICATOR
// ===========================================

/**
 * Powerplay status indicator
 */
@Composable
fun PowerplayIndicator(
    modifier: GlanceModifier = GlanceModifier,
    currentPowerplay: PowerplayPhase,
    oversInPhase: Float,
    totalOversInPhase: Float
) {
    val bgColor = when (currentPowerplay) {
        PowerplayPhase.POWERPLAY_1 -> WidgetColorScheme.primaryContainer
        PowerplayPhase.POWERPLAY_2 -> WidgetColorScheme.surfaceElevated
        PowerplayPhase.POWERPLAY_3 -> WidgetColorScheme.surfaceHighest
        PowerplayPhase.NONE -> WidgetColorScheme.surface
    }
    val phaseLabel = when (currentPowerplay) {
        PowerplayPhase.POWERPLAY_1 -> "PP1"
        PowerplayPhase.POWERPLAY_2 -> "PP2"
        PowerplayPhase.POWERPLAY_3 -> "PP3"
        PowerplayPhase.NONE -> ""
    }
    Row(
        modifier = modifier
            .background(bgColor)
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = phaseLabel,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColorScheme.primary
            )
        )
        
        if (currentPowerplay != PowerplayPhase.NONE) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            
            Text(
                text = "${String.format(Locale.US, "%.1f", oversInPhase)}/${totalOversInPhase.toInt()}",
                style = WidgetTypography.labelSmall
            )
        }
    }
}

enum class PowerplayPhase {
    POWERPLAY_1,
    POWERPLAY_2,
    POWERPLAY_3,
    NONE
}

// ===========================================
// DLS TARGET DISPLAY
// ===========================================

/**
 * DLS adjusted target display
 */
@Composable
fun DLSTargetWidget(
    modifier: GlanceModifier = GlanceModifier,
    originalTarget: Int,
    dlsTarget: Int,
    oversReduced: Float,
    parScore: Int
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorProvider(Color(0x1A60A5FA)))
            .cornerRadius(WidgetTheme.radiusSmall)
            .padding(WidgetTheme.paddingSmall)
    ) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "DLS",
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color(0xFF3B82F6))
                ),
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0x3360A5FA)))
                    .cornerRadius(2.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            
            Spacer(modifier = GlanceModifier.width(8.dp))
            
            Text(
                text = "Target: $dlsTarget",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WidgetColorScheme.textPrimary
                )
            )
            
            Text(
                text = " (${oversReduced.toInt()} overs)",
                style = WidgetTypography.labelSmall
            )
        }
        
        Spacer(modifier = GlanceModifier.height(2.dp))
        
        Text(
            text = "Par: $parScore",
            style = WidgetTypography.labelSmall
        )
    }
}
