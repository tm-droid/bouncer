package com.tanush.bouncer.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random
import io.sentry.Sentry

@Composable
fun AnimatedScoreDisplay(
    score: String,
    modifier: Modifier = Modifier,
    color: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    fontSize: Int = 28
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val shape = RoundedCornerShape(8.dp)
    AnimatedContent(
        targetState = score,
        transitionSpec = {
            (slideInVertically(initialOffsetY = { it }) + fadeIn())
                .togetherWith(slideOutVertically(targetOffsetY = { -it }) + fadeOut())
        },
        modifier = modifier,
        label = "scoreAnimation"
    ) { targetScore ->
        Box(
            modifier = Modifier
                .background(GlassSurface, shape)
                .border(0.5.dp, color.copy(alpha = 0.15f), shape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = targetScore,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

@Composable
fun RunRateIndicator(
    currentRunRate: Float,
    requiredRunRate: Float?,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val isPositive = requiredRunRate == null || currentRunRate >= requiredRunRate
    val color = if (isPositive) colors.statusSuccess else colors.statusLive

    GlassSurface(
        modifier = modifier.height(56.dp),
        cornerRadius = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RUN RATE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = String.format("%.2f", currentRunRate),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
            }
            requiredRunRate?.let { rrr ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "REQUIRED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textTertiary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = String.format("%.2f", rrr),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassSurface)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerTranslate, 0f),
                        end = Offset(shimmerTranslate + 200f, size.height)
                    )
                )
            }
    )
}
