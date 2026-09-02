package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import com.tanush.bouncer.ui.extensions.pressScale

private val Gold = Color(0xFFD4AF37)
private val Red = Color(0xFFE53935)
private val CardDark = Color(0xFF1E1E1E)

enum class MatchPhase {
    UPCOMING,
    LIVE,
    INNINGS_BREAK,
    COMPLETED,
    HIGHLIGHTS
}

private data class PhaseStyle(
    val icon: ImageVector,
    val label: String,
    val background: Color,
    val contentColor: Color,
    val borderColor: Color? = null,
    val iconSize: Float = 16f
)

@Composable
private fun phaseStyle(phase: MatchPhase): PhaseStyle = when (phase) {
    MatchPhase.UPCOMING -> PhaseStyle(
        icon = Icons.Outlined.Notifications,
        label = stringResource(R.string.match_follow),
        background = CardDark,
        contentColor = Gold
    )
    MatchPhase.LIVE -> PhaseStyle(
        icon = Icons.Filled.Circle,
        label = "LIVE",
        background = Color(0xFF2D1010),
        contentColor = Red,
        borderColor = Red.copy(alpha = 0.3f),
        iconSize = 8f
    )
    MatchPhase.INNINGS_BREAK -> PhaseStyle(
        icon = Icons.Outlined.Pause,
        label = stringResource(R.string.match_break),
        background = CardDark,
        contentColor = Color.White.copy(alpha = 0.6f)
    )
    MatchPhase.COMPLETED -> PhaseStyle(
        icon = Icons.Outlined.Assessment,
        label = stringResource(R.string.match_scorecard),
        background = CardDark,
        contentColor = Gold
    )
    MatchPhase.HIGHLIGHTS -> PhaseStyle(
        icon = Icons.Outlined.PlayArrow,
        label = stringResource(R.string.match_highlights),
        background = CardDark,
        contentColor = Gold
    )
}

@Composable
fun MatchActionChip(
    phase: MatchPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val slideOffset = with(density) { 4.dp.roundToPx() }

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically(tween(200)) { slideOffset })
                .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -slideOffset })
        },
        label = "match_action_chip_phase"
    ) { currentPhase ->
        val style = phaseStyle(currentPhase)

        val chipModifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (style.borderColor != null) {
                    Modifier.border(1.dp, style.borderColor, RoundedCornerShape(16.dp))
                } else Modifier
            )
            .background(style.background, RoundedCornerShape(16.dp))
            .pressScale(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = "Match action: ${style.label}" }

        Row(
            modifier = chipModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (currentPhase == MatchPhase.LIVE) {
                PulsingDot()
            } else {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.contentColor,
                    modifier = Modifier.size(style.iconSize.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = style.label,
                color = style.contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    val reducedMotion = rememberReducedMotionEnabled()

    if (reducedMotion) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Red, CircleShape)
        )
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live_dot_scale"
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(Red, CircleShape)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MatchActionChipPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MatchPhase.entries.forEach { phase ->
            MatchActionChip(
                phase = phase,
                onClick = {}
            )
        }
    }
}
