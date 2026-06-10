package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Premium trophy, celebration, and milestone components
 */

@Composable
private fun repeatingFloat(
    label: String,
    from: Float,
    to: Float,
    durationMs: Int,
    easing: Easing = FastOutSlowInEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse,
    transition: InfiniteTransition
): State<Float> = transition.animateFloat(
    initialValue = from,
    targetValue = to,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMs, easing = easing),
        repeatMode = repeatMode
    ),
    label = label
)

private fun shimmerBrush(color: Color, shimmer: Float): Brush = Brush.linearGradient(
    colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.1f)),
    start = Offset(shimmer * 100, 0f),
    end = Offset((shimmer + 1) * 100, 100f)
)

private fun trophyColorFor(type: TrophyType, secondaryPrimary: Color): Color = when (type) {
    TrophyType.GOLD -> secondaryPrimary
    TrophyType.SILVER -> Color(0xFFC0C0C0)
    TrophyType.BRONZE -> Color(0xFFCD7F32)
    TrophyType.PLATINUM -> Color(0xFFE5E4E2)
    TrophyType.DIAMOND -> Color(0xFFB9F2FF)
}

@Composable
fun TrophyDisplay(
    trophyType: TrophyType,
    title: String,
    subtitle: String = "",
    isUnlocked: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "trophy")
    val glowAlpha by repeatingFloat("glow", 0.3f, 0.8f, 2000, transition = infiniteTransition)
    val rotation by repeatingFloat("rotation", -2f, 2f, 3000, transition = infiniteTransition)
    val shimmer by repeatingFloat(
        "shimmer", -1f, 2f, 2500,
        easing = LinearEasing, repeatMode = RepeatMode.Restart, transition = infiniteTransition
    )

    val hierarchy = BouncerDesignSystem.hierarchy
    val trophyColor = trophyColorFor(trophyType, hierarchy.secondary.primary)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TrophyIcon(
            isUnlocked = isUnlocked,
            trophyColor = trophyColor,
            glowAlpha = glowAlpha,
            rotation = rotation,
            shimmer = shimmer,
            title = title
        )
        TrophyText(
            title = title,
            subtitle = subtitle,
            isUnlocked = isUnlocked,
            trophyColor = trophyColor
        )
    }
}

@Composable
private fun TrophyIcon(
    isUnlocked: Boolean,
    trophyColor: Color,
    glowAlpha: Float,
    rotation: Float,
    shimmer: Float,
    title: String
) {
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .blur(16.dp)
                    .alpha(glowAlpha)
                    .background(trophyColor, CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .rotate(if (isUnlocked) rotation else 0f)
                .clip(CircleShape)
                .background(brush = trophyBackgroundBrush(isUnlocked, trophyColor, shimmer))
                .border(
                    width = 2.dp,
                    brush = trophyBorderBrush(isUnlocked, trophyColor),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = title,
                tint = if (isUnlocked) trophyColor else TextMuted,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

private fun trophyBackgroundBrush(isUnlocked: Boolean, trophyColor: Color, shimmer: Float): Brush =
    if (isUnlocked) {
        shimmerBrush(trophyColor, shimmer)
    } else {
        Brush.linearGradient(colors = listOf(GlassSurface, GlassSurface))
    }

private fun trophyBorderBrush(isUnlocked: Boolean, trophyColor: Color): Brush =
    Brush.linearGradient(
        colors = if (isUnlocked) {
            listOf(trophyColor, trophyColor.copy(alpha = 0.5f))
        } else {
            listOf(TextMuted, GlassBorder)
        }
    )

@Composable
private fun TrophyText(
    title: String,
    subtitle: String,
    isUnlocked: Boolean,
    trophyColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = if (isUnlocked) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = if (isUnlocked) trophyColor else TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

enum class TrophyType {
    BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
}

private fun buildCelebrationParticles(primary: Color, secondary: Color): List<Particle> {
    val palette = listOf(primary, secondary, primary, Color.White)
    return List(50) {
        Particle(
            x = Random.nextFloat(),
            y = Random.nextFloat() - 0.5f,
            size = Random.nextFloat() * 8f + 4f,
            color = palette.random(),
            speedY = Random.nextFloat() * 0.02f + 0.01f,
            rotation = Random.nextFloat() * 360f
        )
    }
}

@Composable
private fun ParticleCanvas(particles: List<Particle>, time: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val y = ((particle.y + time * particle.speedY * 100) % 1.2f)
            val x = particle.x + sin(time * 10 + particle.x * 10).toFloat() * 0.02f

            rotate(
                degrees = particle.rotation + time * 360,
                pivot = Offset(size.width * x, size.height * y)
            ) {
                drawRect(
                    color = particle.color,
                    topLeft = Offset(
                        size.width * x - particle.size / 2,
                        size.height * y - particle.size / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(particle.size, particle.size * 0.6f)
                )
            }
        }
    }
}

@Composable
private fun CelebrationMessage(message: String, textScale: Float, accent: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.scale(textScale)
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = message,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val speedY: Float,
    val rotation: Float
)

@Composable
fun MilestoneCard(
    milestone: String,
    description: String,
    progress: Float,
    icon: ImageVector = Icons.Default.Star,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "milestone")
    val glowAlpha by repeatingFloat("glow", 0.2f, 0.5f, 1500, transition = infiniteTransition)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(brush = milestoneBackgroundBrush(isCompleted, hierarchy.secondary.primary))
            .border(
                width = if (isCompleted) 2.dp else 1.dp,
                brush = milestoneBorderBrush(
                    isCompleted,
                    hierarchy.secondary.primary,
                    hierarchy.secondary.secondary
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MilestoneIcon(
                icon = icon,
                isCompleted = isCompleted,
                glowAlpha = glowAlpha,
                accent = hierarchy.secondary.primary
            )
            MilestoneDetails(
                modifier = Modifier.weight(1f),
                milestone = milestone,
                description = description,
                animatedProgress = animatedProgress,
                isCompleted = isCompleted,
                primary = hierarchy.secondary.primary
            )
        }
    }
}

private fun milestoneBackgroundBrush(isCompleted: Boolean, primary: Color): Brush =
    if (isCompleted) {
        Brush.linearGradient(
            colors = listOf(
                primary.copy(alpha = 0.2f),
                primary.copy(alpha = 0.1f)
            )
        )
    } else {
        Brush.linearGradient(colors = listOf(GlassSurface, GlassSurface))
    }

private fun milestoneBorderBrush(isCompleted: Boolean, primary: Color, secondary: Color): Brush =
    if (isCompleted) {
        Brush.linearGradient(colors = listOf(primary, primary, secondary))
    } else {
        Brush.linearGradient(
            colors = listOf(
                primary.copy(alpha = 0.3f),
                secondary.copy(alpha = 0.1f)
            )
        )
    }

@Composable
private fun MilestoneIcon(
    icon: ImageVector,
    isCompleted: Boolean,
    glowAlpha: Float,
    accent: Color
) {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .blur(12.dp)
                    .alpha(glowAlpha)
                    .background(accent, CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isCompleted) accent.copy(alpha = 0.2f) else GlassSurface)
                .border(
                    width = 2.dp,
                    color = if (isCompleted) accent else accent.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.Verified else icon,
                contentDescription = null,
                tint = if (isCompleted) accent else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun MilestoneDetails(
    milestone: String,
    description: String,
    animatedProgress: Float,
    isCompleted: Boolean,
    primary: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = milestone,
            color = if (isCompleted) primary else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = description,
            color = TextSecondary,
            fontSize = 12.sp
        )

        ProgressTrack(animatedProgress = animatedProgress, primary = primary)

        Text(
            text = "${(animatedProgress * 100).toInt()}% Complete",
            color = primary.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ProgressTrack(animatedProgress: Float, primary: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(primary.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(
                    brush = Brush.horizontalGradient(colors = listOf(primary, primary))
                )
        )
    }
}

private val StreakOrange = Color(0xFFFF6B00)
private val StreakAmber = Color(0xFFFF9500)
private val StreakGold = Color(0xFFFFD700)
private val StreakRed = Color(0xFFFF4500)

@Composable
fun StreakCounter(
    currentStreak: Int,
    maxStreak: Int,
    unit: String = "days",
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "streak")
    val flame by repeatingFloat("flame", 0.95f, 1.05f, 500, transition = infiniteTransition)
    val glowRadius by repeatingFloat("glowRadius", 8f, 16f, 1000, transition = infiniteTransition)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        StreakOrange.copy(alpha = 0.2f),
                        StreakAmber.copy(alpha = 0.1f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(colors = listOf(StreakOrange, StreakAmber)),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlameIcon(flame = flame, glowRadius = glowRadius)
        StreakValueColumn(value = currentStreak, unit = unit, label = "Current Streak")
        Spacer(modifier = Modifier.weight(1f))
        StreakValueColumn(
            value = maxStreak,
            unit = unit,
            label = "Best Streak",
            valueColor = hierarchy.secondary.primary,
            valueSize = 18,
            alignment = Alignment.End
        )
    }
}

@Composable
private fun FlameIcon(flame: Float, glowRadius: Float) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(StreakOrange.copy(alpha = 0.5f), Color.Transparent)
                ),
                radius = glowRadius.dp.toPx()
            )
        }

        Canvas(
            modifier = Modifier
                .size(32.dp)
                .scale(flame)
        ) {
            drawPath(
                path = buildFlamePath(size.width, size.height),
                brush = Brush.verticalGradient(
                    colors = listOf(StreakGold, StreakOrange, StreakRed)
                )
            )
        }
    }
}

private fun buildFlamePath(width: Float, height: Float): Path = Path().apply {
    moveTo(width * 0.5f, 0f)
    cubicTo(
        width * 0.7f, height * 0.3f,
        width, height * 0.5f,
        width * 0.5f, height
    )
    cubicTo(
        0f, height * 0.5f,
        width * 0.3f, height * 0.3f,
        width * 0.5f, 0f
    )
}

@Composable
private fun StreakValueColumn(
    value: Int,
    unit: String,
    label: String,
    valueColor: Color = TextPrimary,
    valueSize: Int = 24,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$value $unit",
            color = valueColor,
            fontSize = valueSize.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun BadgeDisplay(
    name: String,
    icon: ImageVector,
    isEarned: Boolean = true,
    tier: Int = 1,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val tierColor = tierColorFor(tier, hierarchy.secondary.primary)

    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val shimmer by repeatingFloat(
        "shimmer", -1f, 2f, 3000,
        easing = LinearEasing, repeatMode = RepeatMode.Restart, transition = infiniteTransition
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BadgeHexagon(
            icon = icon,
            isEarned = isEarned,
            tierColor = tierColor,
            shimmer = shimmer,
            name = name
        )

        Text(
            text = name,
            color = if (isEarned) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

private fun tierColorFor(tier: Int, secondaryPrimary: Color): Color = when (tier) {
    1 -> Color(0xFFCD7F32)
    2 -> Color(0xFFC0C0C0)
    3 -> secondaryPrimary
    else -> Color(0xFFB9F2FF)
}

@Composable
private fun BadgeHexagon(
    icon: ImageVector,
    isEarned: Boolean,
    tierColor: Color,
    shimmer: Float,
    name: String
) {
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(56.dp)
                .alpha(if (isEarned) 1f else 0.4f)
        ) {
            val path = buildHexagonPath(size.width, size.height, size.minDimension / 2)
            drawPath(path = path, brush = shimmerBrush(tierColor, shimmer))
            drawPath(path = path, color = tierColor, style = Stroke(width = 2.dp.toPx()))
        }

        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = if (isEarned) tierColor else TextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun buildHexagonPath(width: Float, height: Float, radius: Float): Path {
    val centerX = width / 2
    val centerY = height / 2
    return Path().apply {
        for (i in 0..5) {
            val angle = Math.toRadians(60.0 * i - 30)
            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

@Composable
fun XPProgressBar(
    currentXP: Int,
    maxXP: Int,
    level: Int,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val progress = (currentXP.toFloat() / maxXP).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "xp"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "xpBar")
    val shimmer by repeatingFloat(
        "shimmer", -1f, 2f, 2000,
        easing = LinearEasing, repeatMode = RepeatMode.Restart, transition = infiniteTransition
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LevelBadge(level = level, accent = hierarchy.secondary.primary)
        XPProgressDetails(
            modifier = Modifier.weight(1f),
            currentXP = currentXP,
            maxXP = maxXP,
            animatedProgress = animatedProgress,
            shimmer = shimmer,
            primary = hierarchy.secondary.primary,
            secondary = hierarchy.secondary.secondary
        )
    }
}

@Composable
private fun LevelBadge(level: Int, accent: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.3f),
                        accent.copy(alpha = 0.1f)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(colors = listOf(accent, accent)),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "LVL",
                color = accent.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$level",
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun XPProgressDetails(
    currentXP: Int,
    maxXP: Int,
    animatedProgress: Float,
    shimmer: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Experience",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "$currentXP / $maxXP XP",
                color = primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        XPProgressTrack(
            animatedProgress = animatedProgress,
            shimmer = shimmer,
            primary = primary,
            secondary = secondary
        )
    }
}

@Composable
private fun XPProgressTrack(
    animatedProgress: Float,
    shimmer: Float,
    primary: Color,
    secondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(primary.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(primary, primary, secondary)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .alpha(0.3f)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White, Color.Transparent),
                        start = Offset(shimmer * 500, 0f),
                        end = Offset((shimmer + 0.3f) * 500, 0f)
                    )
                )
        )
    }
}
