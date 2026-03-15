package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChipState {
    NORMAL,
    ACTIVE,
    LOADING,
    DISABLED,
    STALE
}

private val ChipShape = RoundedCornerShape(8.dp)
private val GoldAccent = Color(0xFFD4AF37)

@Composable
private fun chipShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "chip_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chip_shimmer_translate"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E1E1E),
            GoldAccent.copy(alpha = 0.12f),
            Color(0xFF1E1E1E)
        ),
        start = Offset(translate - 200f, 0f),
        end = Offset(translate, 0f)
    )
}

private fun formatRelativeTime(timestampMillis: Long): String {
    val elapsed = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 10 -> "${minutes}m"
        else -> "10m+"
    }
}

@Composable
fun PremiumChip(
    label: String,
    state: ChipState = ChipState.NORMAL,
    icon: ImageVector? = null,
    staleTimestamp: Long = 0L,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color
    val borderColor: Color
    val textColor: Color
    val isClickable: Boolean

    when (state) {
        ChipState.NORMAL -> {
            backgroundColor = Color(0xFF1E1E1E)
            borderColor = Color.White.copy(alpha = 0.1f)
            textColor = Color.White.copy(alpha = 0.8f)
            isClickable = true
        }
        ChipState.ACTIVE -> {
            backgroundColor = Color(0xFF2A2205)
            borderColor = GoldAccent
            textColor = GoldAccent
            isClickable = true
        }
        ChipState.LOADING -> {
            backgroundColor = Color(0xFF1E1E1E)
            borderColor = Color.White.copy(alpha = 0.1f)
            textColor = Color.White.copy(alpha = 0.8f)
            isClickable = false
        }
        ChipState.DISABLED -> {
            backgroundColor = Color(0xFF161616)
            borderColor = Color.Transparent
            textColor = Color.White.copy(alpha = 0.3f)
            isClickable = false
        }
        ChipState.STALE -> {
            backgroundColor = Color(0xFF1E1E1E)
            borderColor = Color.White.copy(alpha = 0.1f)
            textColor = Color.White.copy(alpha = 0.5f)
            isClickable = true
        }
    }

    val description = when (state) {
        ChipState.NORMAL -> label
        ChipState.ACTIVE -> "$label, selected"
        ChipState.LOADING -> "$label, loading"
        ChipState.DISABLED -> "$label, disabled"
        ChipState.STALE -> "$label, stale data ${formatRelativeTime(staleTimestamp)} ago"
    }

    val chipModifier = modifier
        .height(32.dp)
        .clip(ChipShape)
        .then(
            if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, ChipShape)
            else Modifier
        )
        .background(backgroundColor, ChipShape)
        .then(
            if (isClickable && onClick != null) Modifier.clickable(onClick = onClick)
            else Modifier
        )
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .semantics { contentDescription = description }

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
        },
        label = "chip_state"
    ) { currentState ->
        Row(
            modifier = chipModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (currentState) {
                ChipState.LOADING -> {
                    val shimmer = chipShimmerBrush()
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmer)
                    )
                }
                ChipState.ACTIVE -> {
                    val leadingIcon = icon ?: Icons.Filled.Check
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                ChipState.STALE -> {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = formatRelativeTime(staleTimestamp),
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
                else -> {
                    // NORMAL and DISABLED
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun PremiumChipPreview() {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PremiumChip(label = "Normal", state = ChipState.NORMAL)
        PremiumChip(label = "Active", state = ChipState.ACTIVE)
        PremiumChip(label = "Loading", state = ChipState.LOADING)
        PremiumChip(label = "Disabled", state = ChipState.DISABLED)
        PremiumChip(
            label = "Stale",
            state = ChipState.STALE,
            staleTimestamp = System.currentTimeMillis() - 300_000L
        )
    }
}
