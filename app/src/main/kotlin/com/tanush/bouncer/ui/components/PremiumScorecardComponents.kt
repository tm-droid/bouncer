package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

data class BatsmanScoreData(
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val dismissal: String,
    val isNotOut: Boolean = false,
    val isOnStrike: Boolean = false
)

data class BowlerScoreData(
    val name: String,
    val overs: Float,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Float,
    val dots: Int = 0,
    val isBowling: Boolean = false
)

data class FallOfWicketData(
    val wicketNumber: Int,
    val score: Int,
    val batsmanName: String,
    val overNumber: Float
)

@Composable
private fun GlassCard(
    modifier: Modifier,
    elevation: Dp,
    spotColor: Color,
    cornerRadius: Dp,
    ambientColor: Color = Color.Black.copy(alpha = 0.3f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = RoundedCornerShape(cornerRadius),
            spotColor = spotColor,
            ambientColor = ambientColor
        ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(0.5.dp, GlassBorder),
        content = content
    )
}

@Composable
private fun StatCell(
    text: String,
    width: Dp,
    color: Color,
    fontSize: Int = 13,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun StrikeIndicator() {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary)
                ),
                CircleShape
            )
    )
}

@Composable
private fun CircleIconBadge(
    icon: ImageVector,
    tint: Color,
    boxSize: Dp,
    iconSize: Dp,
    borderAlpha: Float = 0.5f,
    fillStartAlpha: Float = 0.3f,
    fillEndAlpha: Float = 0.1f
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = fillStartAlpha),
                        tint.copy(alpha = fillEndAlpha)
                    )
                ),
                CircleShape
            )
            .border(1.dp, tint.copy(alpha = borderAlpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun TeamHeaderRow(
    teamName: String,
    teamColor: Color,
    total: String,
    overs: String
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconBadge(
                icon = Icons.Default.SportsCricket,
                tint = teamColor,
                boxSize = 36.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(teamName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("Batting", fontSize = 12.sp, color = colors.textSecondary)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(total, fontSize = 22.sp, fontWeight = FontWeight.Black, color = teamColor)
            Text("($overs ov)", fontSize = 12.sp, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ExtrasRow(extras: Int) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackgroundHover, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Extras", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
        Text("$extras", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}

@Composable
private fun TotalRow(total: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TOTAL",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = hierarchy.secondary.primary,
            letterSpacing = 2.sp
        )
        Text(total, fontSize = 18.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
    }
}

@Composable
private fun staggeredAlpha(index: Int, isVisible: Boolean, label: String): Float {
    val value by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 50 * index, easing = FastOutSlowInEasing),
        label = label
    )
    return value
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = GlassBorderLight,
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun BatsmenList(
    batsmen: List<BatsmanScoreData>,
    teamColor: Color,
    isVisible: Boolean
) {
    batsmen.forEachIndexed { index, batsman ->
        val progress = staggeredAlpha(index, isVisible, "batsman_$index")
        BatsmanRow(batsman = batsman, teamColor = teamColor, animationProgress = progress)
        if (index < batsmen.lastIndex) RowDivider()
    }
}

@Composable
fun PremiumBattingScorecard(
    teamName: String,
    teamColor: Color,
    batsmen: List<BatsmanScoreData>,
    extras: Int,
    total: String,
    overs: String,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    GlassCard(
        modifier = modifier,
        elevation = 20.dp,
        spotColor = teamColor.copy(alpha = 0.15f),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            TeamHeaderRow(teamName = teamName, teamColor = teamColor, total = total, overs = overs)

            Spacer(modifier = Modifier.height(24.dp))
            ScorecardHeader()
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = hierarchy.secondary.primary.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )

            BatsmenList(batsmen = batsmen, teamColor = teamColor, isVisible = isVisible)

            Spacer(modifier = Modifier.height(16.dp))
            ExtrasRow(extras = extras)
            Spacer(modifier = Modifier.height(8.dp))
            TotalRow(total = total)
            Spacer(modifier = Modifier.height(16.dp))
            GoldGradientDivider()
        }
    }
}

@Composable
fun GoldGradientDivider(modifier: Modifier = Modifier) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        hierarchy.secondary.primary.copy(alpha = 0.30f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun RowScope.HeaderCell(
    label: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.5.sp
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = hierarchy.secondary.primary.copy(alpha = 0.6f),
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        modifier = modifier
    )
}

private data class HeaderColumn(val label: String, val width: Dp, val align: TextAlign = TextAlign.Center)

private val BATTING_HEADER_COLUMNS = listOf(
    HeaderColumn("R", 32.dp),
    HeaderColumn("B", 32.dp),
    HeaderColumn("4s", 28.dp),
    HeaderColumn("6s", 28.dp),
    HeaderColumn("S/R", 48.dp, TextAlign.End)
)

private val BOWLING_HEADER_COLUMNS = listOf(
    HeaderColumn("O", 32.dp),
    HeaderColumn("M", 28.dp),
    HeaderColumn("R", 32.dp),
    HeaderColumn("W", 28.dp),
    HeaderColumn("ECON", 44.dp, TextAlign.End)
)

@Composable
private fun StatHeaderRow(firstLabel: String, columns: List<HeaderColumn>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(firstLabel, Modifier.weight(1f), TextAlign.Start, letterSpacing = 1.sp)
        columns.forEach { col ->
            HeaderCell(col.label, Modifier.width(col.width), col.align)
        }
    }
}

@Composable
private fun ScorecardHeader() {
    StatHeaderRow("BATTER", BATTING_HEADER_COLUMNS)
}

@Composable
private fun strikeRowBackgroundModifier(
    isNotOut: Boolean,
    teamColor: Color,
    goldPrimaryColor: Color
): Modifier {
    if (!isNotOut) return Modifier.fillMaxWidth()
    return Modifier
        .fillMaxWidth()
        .background(
            Brush.horizontalGradient(
                colors = listOf(
                    teamColor.copy(alpha = 0.10f),
                    teamColor.copy(alpha = 0.02f)
                )
            ),
            RoundedCornerShape(4.dp)
        )
        .drawBehind {
            drawRect(
                color = goldPrimaryColor,
                topLeft = Offset.Zero,
                size = Size(3.dp.toPx(), size.height)
            )
        }
}

@Composable
private fun MilestoneBadgeWithSpacer(text: String, color: Color) {
    Spacer(modifier = Modifier.width(6.dp))
    MilestoneBadge(text, color)
}

@Composable
private fun PlayerMilestone(majorMilestone: Boolean, minorMilestone: Boolean, majorText: String, minorText: String) {
    val hierarchy = BouncerDesignSystem.hierarchy
    when {
        majorMilestone -> MilestoneBadgeWithSpacer(majorText, NeonSuccess)
        minorMilestone -> MilestoneBadgeWithSpacer(minorText, hierarchy.secondary.primary)
    }
}

@Composable
private fun BatsmanNameRow(
    batsman: BatsmanScoreData,
    isHighScore: Boolean,
    isCentury: Boolean
) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            batsman.name,
            fontSize = 14.sp,
            fontWeight = if (batsman.isNotOut) FontWeight.Bold else FontWeight.Medium,
            color = if (batsman.isNotOut) colors.textPrimary else colors.textPrimary.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (batsman.isOnStrike) {
            Spacer(modifier = Modifier.width(6.dp))
            StrikeIndicator()
        }

        PlayerMilestone(
            majorMilestone = isCentury,
            minorMilestone = isHighScore,
            majorText = "100",
            minorText = "50"
        )
    }
}

@Composable
private fun BatsmanStatusText(batsman: BatsmanScoreData) {
    val colors = BouncerDesignSystem.colors
    when {
        !batsman.isNotOut && batsman.dismissal.isNotEmpty() -> {
            Text(
                batsman.dismissal,
                fontSize = 11.sp,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        batsman.isNotOut -> {
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                color = NeonSuccess.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(0.5.dp, NeonSuccess.copy(alpha = 0.25f))
            ) {
                Text(
                    "not out",
                    fontSize = 9.sp,
                    color = NeonSuccess.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun strikeRateColor(
    strikeRate: Float,
    primaryText: Color,
    secondaryText: Color
): Color = when {
    strikeRate >= 150 -> NeonSuccess
    strikeRate >= 100 -> primaryText
    else -> secondaryText
}

@Composable
private fun BatsmanStatCells(
    batsman: BatsmanScoreData,
    teamColor: Color,
    isHighScore: Boolean
) {
    val colors = BouncerDesignSystem.colors
    StatCell(
        text = "${batsman.runs}",
        width = 32.dp,
        color = if (isHighScore) teamColor else colors.textPrimary,
        fontSize = 14,
        fontWeight = FontWeight.Bold
    )
    StatCell(text = "${batsman.balls}", width = 32.dp, color = colors.textSecondary)
    StatCell(
        text = "${batsman.fours}",
        width = 28.dp,
        color = if (batsman.fours > 0) BoundaryBlue else colors.textTertiary,
        fontWeight = if (batsman.fours > 0) FontWeight.Medium else FontWeight.Normal
    )
    StatCell(
        text = "${batsman.sixes}",
        width = 28.dp,
        color = if (batsman.sixes > 0) SixerPurple else colors.textTertiary,
        fontWeight = if (batsman.sixes > 0) FontWeight.Medium else FontWeight.Normal
    )
    StatCell(
        text = String.format("%.1f", batsman.strikeRate),
        width = 48.dp,
        color = strikeRateColor(batsman.strikeRate, colors.textPrimary, colors.textSecondary),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End
    )
}

@Composable
private fun BatsmanRow(
    batsman: BatsmanScoreData,
    teamColor: Color,
    animationProgress: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val isHighScore = batsman.runs >= 50
    val isCentury = batsman.runs >= 100
    val slideOffset = (1f - animationProgress) * 20f

    val strikeRowModifier = strikeRowBackgroundModifier(
        isNotOut = batsman.isNotOut,
        teamColor = teamColor,
        goldPrimaryColor = hierarchy.secondary.primary
    )

    Row(
        modifier = strikeRowModifier
            .graphicsLayer {
                alpha = animationProgress
                translationX = slideOffset
            }
            .padding(
                start = if (batsman.isNotOut) 12.dp else 0.dp,
                end = 0.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BatsmanNameRow(batsman = batsman, isHighScore = isHighScore, isCentury = isCentury)
            BatsmanStatusText(batsman = batsman)
        }

        BatsmanStatCells(batsman = batsman, teamColor = teamColor, isHighScore = isHighScore)
    }
}

@Composable
private fun MilestoneBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun BowlingTeamHeader(teamColor: Color) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(teamColor.copy(alpha = 0.15f), CircleShape)
                .border(0.5.dp, teamColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Adjust,
                contentDescription = null,
                tint = teamColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("Bowling", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}

@Composable
private fun BowlersList(
    bowlers: List<BowlerScoreData>,
    teamColor: Color,
    isVisible: Boolean
) {
    bowlers.forEachIndexed { index, bowler ->
        val progress = staggeredAlpha(index, isVisible, "bowler_$index")
        BowlerRow(bowler = bowler, teamColor = teamColor, animationProgress = progress)
        if (index < bowlers.lastIndex) RowDivider()
    }
}

@Composable
fun PremiumBowlingScorecard(
    teamName: String,
    teamColor: Color,
    bowlers: List<BowlerScoreData>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    GlassCard(
        modifier = modifier,
        elevation = 16.dp,
        spotColor = teamColor.copy(alpha = 0.12f),
        cornerRadius = 24.dp,
        ambientColor = Color.Black.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            BowlingTeamHeader(teamColor = teamColor)

            Spacer(modifier = Modifier.height(16.dp))
            BowlingHeader()
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = hierarchy.secondary.primary.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )

            BowlersList(bowlers = bowlers, teamColor = teamColor, isVisible = isVisible)
        }
    }
}

@Composable
private fun BowlingHeader() {
    StatHeaderRow("BOWLER", BOWLING_HEADER_COLUMNS)
}

@Composable
private fun RowScope.BowlerNameRow(
    bowler: BowlerScoreData,
    isGoodFigures: Boolean,
    isFiveWicket: Boolean
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            bowler.name,
            fontSize = 14.sp,
            fontWeight = if (bowler.isBowling) FontWeight.Bold else FontWeight.Medium,
            color = if (bowler.isBowling) colors.textPrimary else colors.textPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (bowler.isBowling) {
            Spacer(modifier = Modifier.width(6.dp))
            StrikeIndicator()
        }

        PlayerMilestone(
            majorMilestone = isFiveWicket,
            minorMilestone = isGoodFigures,
            majorText = "5W",
            minorText = "3W"
        )
    }
}

private fun bowlerEconomyColor(
    economy: Float,
    primaryText: Color,
    goldPrimary: Color
): Color = when {
    economy <= 6 -> NeonSuccess
    economy <= 8 -> primaryText
    economy <= 10 -> goldPrimary
    else -> NeonError
}

private fun bowlerWicketColor(
    wickets: Int,
    isFiveWicket: Boolean,
    goldPrimary: Color,
    tertiaryText: Color
): Color = when {
    isFiveWicket -> NeonSuccess
    wickets > 0 -> goldPrimary
    else -> tertiaryText
}

@Composable
private fun BowlerStatCells(
    bowler: BowlerScoreData,
    isFiveWicket: Boolean
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    StatCell(text = String.format("%.1f", bowler.overs), width = 32.dp, color = colors.textSecondary)
    StatCell(
        text = "${bowler.maidens}",
        width = 28.dp,
        color = if (bowler.maidens > 0) NeonSuccess else colors.textTertiary
    )
    StatCell(text = "${bowler.runs}", width = 32.dp, color = colors.textSecondary)
    StatCell(
        text = "${bowler.wickets}",
        width = 28.dp,
        color = bowlerWicketColor(
            wickets = bowler.wickets,
            isFiveWicket = isFiveWicket,
            goldPrimary = hierarchy.secondary.primary,
            tertiaryText = colors.textTertiary
        ),
        fontSize = 14,
        fontWeight = FontWeight.Bold
    )
    StatCell(
        text = String.format("%.1f", bowler.economy),
        width = 44.dp,
        color = bowlerEconomyColor(
            economy = bowler.economy,
            primaryText = colors.textPrimary,
            goldPrimary = hierarchy.secondary.primary
        ),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End
    )
}

@Composable
private fun BowlerRow(
    bowler: BowlerScoreData,
    @Suppress("UNUSED_PARAMETER") teamColor: Color,
    animationProgress: Float
) {
    val isGoodFigures = bowler.wickets >= 3
    val isFiveWicket = bowler.wickets >= 5

    val rowBackground = if (bowler.isBowling) {
        Modifier.background(GlassBackgroundHover, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowBackground)
            .graphicsLayer {
                alpha = animationProgress
                translationX = (1f - animationProgress) * 20f
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BowlerNameRow(bowler = bowler, isGoodFigures = isGoodFigures, isFiveWicket = isFiveWicket)
        BowlerStatCells(bowler = bowler, isFiveWicket = isFiveWicket)
    }
}

@Composable
private fun FallOfWicketsHeader() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Timeline,
            contentDescription = null,
            tint = hierarchy.secondary.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Fall of Wickets", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}

@Composable
private fun FallOfWicketsCanvas(
    teamColor: Color,
    glassBorderColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineY = size.height * 0.5f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    glassBorderColor.copy(alpha = 0.2f),
                    teamColor.copy(alpha = 0.5f),
                    glassBorderColor.copy(alpha = 0.2f)
                )
            ),
            start = Offset(0f, lineY),
            end = Offset(size.width, lineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun WicketMarkersRow(
    fallOfWickets: List<FallOfWicketData>,
    teamColor: Color,
    isVisible: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        fallOfWickets.forEachIndexed { index, fow ->
            val animatedScale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(500, delayMillis = 80 * index, easing = FastOutSlowInEasing),
                label = "wicket_$index"
            )
            WicketMarker(fow = fow, teamColor = teamColor, scale = animatedScale)
        }
    }
}

@Composable
private fun FallOfWicketsTimelineBody(
    fallOfWickets: List<FallOfWicketData>,
    teamColor: Color,
    glassBorderColor: Color,
    isVisible: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        FallOfWicketsHeader()
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            FallOfWicketsCanvas(teamColor = teamColor, glassBorderColor = glassBorderColor)
            WicketMarkersRow(
                fallOfWickets = fallOfWickets,
                teamColor = teamColor,
                isVisible = isVisible
            )
        }
    }
}

@Composable
fun FallOfWicketsTimeline(
    fallOfWickets: List<FallOfWicketData>,
    teamColor: Color,
    modifier: Modifier = Modifier
) {
    val glassBorderColor = BouncerDesignSystem.colors.glassBorder
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    GlassCard(
        modifier = modifier,
        elevation = 12.dp,
        spotColor = teamColor.copy(alpha = 0.1f),
        cornerRadius = 20.dp
    ) {
        FallOfWicketsTimelineBody(
            fallOfWickets = fallOfWickets,
            teamColor = teamColor,
            glassBorderColor = glassBorderColor,
            isVisible = isVisible
        )
    }
}

@Composable
private fun WicketMarker(
    fow: FallOfWicketData,
    teamColor: Color,
    scale: Float
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = scale
        }
    ) {
        Text("${fow.score}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)

        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            teamColor.copy(alpha = 0.4f),
                            teamColor.copy(alpha = 0.1f)
                        )
                    ),
                    CircleShape
                )
                .border(1.5.dp, teamColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${fow.wicketNumber}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = teamColor)
        }

        Text("${fow.overNumber}", fontSize = 10.sp, color = colors.textTertiary)
    }
}

@Composable
private fun ExtrasBreakdownRow(
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    total: Int
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExtraItem(label = "Wd", value = wides, color = hierarchy.secondary.primary)
        ExtraItem(label = "Nb", value = noBalls, color = NeonError)
        ExtraItem(label = "B", value = byes, color = colors.textSecondary)
        ExtraItem(label = "Lb", value = legByes, color = colors.textSecondary)

        Box(
            modifier = Modifier
                .height(32.dp)
                .width(1.dp)
                .background(colors.glassBorder.copy(alpha = 0.3f))
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", fontSize = 18.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
            Text("Total", fontSize = 10.sp, color = colors.textTertiary)
        }
    }
}

@Composable
fun ExtrasBreakdown(
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    penalty: Int = 0,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val total = wides + noBalls + byes + legByes + penalty

    GlassCard(
        modifier = modifier,
        elevation = 8.dp,
        spotColor = hierarchy.secondary.primary.copy(alpha = 0.08f),
        cornerRadius = 16.dp
    ) {
        ExtrasBreakdownRow(
            wides = wides,
            noBalls = noBalls,
            byes = byes,
            legByes = legByes,
            total = total
        )
    }
}

@Composable
private fun ExtraItem(label: String, value: Int, color: Color) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) color else colors.textTertiary
        )
        Text(label, fontSize = 10.sp, color = colors.textTertiary)
    }
}
